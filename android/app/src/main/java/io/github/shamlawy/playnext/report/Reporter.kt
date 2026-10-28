package io.github.shamlawy.playnext.report

import android.content.Context
import io.github.shamlawy.playnext.Device
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.crash.swapIn
import io.github.shamlawy.playnext.net.Net
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.time.LocalDate

/**
 * Problem reports, the web app's way: one signature per problem, once a day, scrubbed, batches of 10 to the
 * helper's /bug. Kept in a file so a crash or no signal never loses one; every start sends what's left.
 */
object Reporter {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context
    private lateinit var queueFile: File
    private val lock = Any()
    private val sending = Mutex() // one flush at a time: two would send a batch twice
    private var wait: Job? = null // the pause before a flush, never the flush itself
    private var queue: MutableList<Report>? = null // the file's copy, held under lock; kept when the phone is full

    /** Names to scrub (game titles, list names) and secrets (RAWG key); filled once the backup is read (Piece 2). */
    @Volatile var names: Collection<String> = emptyList()
    @Volatile var secrets: Collection<String> = emptyList()

    /** The library line for reports ([ReportRules.libLine]), from the copy of your games the app has open. */
    @Volatile var lib: String = ""

    /** How a batch reaches the helper's /bug; a test swaps it. */
    @Volatile internal var send: suspend (String) -> Boolean = { body -> Net.call(Net.postJson(Net.helper("/bug"), body)) { it.isSuccessful } }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        queueFile = File(app.filesDir, "reports/queue.txt").apply { parentFile?.mkdirs() }
        synchronized(lock) {
            wait?.cancel()
            wait = null
            queue = null
        }
    }

    /**
     * Builds the report the way index.html does. Returns null when it was already sent today. [apart] splits one
     * message into several problems (the ran report: one per version, which the digits alone don't tell apart).
     */
    fun build(kind: String, msg: String, stack: String = "", at: String = "", note: String = "", screen: String = Screen.current, now: Long = System.currentTimeMillis(), apart: String = ""): Report? {
        if (Device.emulator) return null
        val m = Scrub.text(msg, names, secrets).trim()
        if (m.isEmpty()) return null
        val a = Scrub.text(at, names, secrets)
        val sig = ReportRules.sig(kind, m, a, screen, now, apart)
        if (!firstToday(sig)) return null
        return Report(
            sig = sig, kind = kind, msg = m,
            stack = Scrub.text(stack, names, secrets).lines().take(8).joinToString("\n"),
            at = a, screen = screen, note = if (note.isNotBlank()) Scrub.text(note, names, secrets) else "",
            w = Device.widthDp(app), h = Device.heightDp(app), dpr = Math.round(Device.density(app) * 100) / 100.0,
            dev = Device.dev(app), v = Device.versionCode.toString(),
            crumbs = Crumbs.last(10), lib = lib,
        )
    }

    /** An automatic report: queued off the main thread (a file write), sent 3 s later with whatever else turns up. */
    fun report(kind: String, msg: String, stack: String = "", at: String = "", note: String = "") {
        val r = build(kind, msg, stack, at, note) ?: return
        scope.launch {
            enqueue(listOf(r))
            flushSoon(3000)
        }
    }

    /** "Report a problem": sent at once. The result says whether it went or waits for a signal. */
    suspend fun user(text: String): Boolean = withContext(Dispatchers.IO) {
        val t = text.trim().take(800)
        if (t.isEmpty()) return@withContext false
        val r = build("user", t.take(200), note = t) ?: return@withContext false
        enqueue(listOf(r))
        flush()
    }

    /** Once per app version: "this version ran", the proof bugsync needs before it closes a fixed stutter. */
    fun ranOnce(code: Int = Device.versionCode) {
        if (Device.emulator || Store.prefs.getInt("ranV", 0) == code) return
        // null here only when this version's report was already queued today
        build("ran", "Android app v$code ran", apart = "v$code")?.let { enqueue(listOf(it)) }
        Store.prefs.edit().putInt("ranV", code).apply()
        flushSoon(5000)
    }

    /**
     * Every start (Application.onCreate): reports written by an earlier process (a crash) join the queue, and whatever
     * waits (an offline "Report a problem", a crash) is sent a few seconds in. Off the main thread.
     */
    fun adopt(files: List<File>) {
        scope.launch {
            val got = files.mapNotNull { f -> runCatching { json.decodeFromString(Report.serializer(), f.readText()) }.getOrNull().also { f.delete() } }
            if (got.isNotEmpty()) enqueue(got)
            flushSoon(3000)
        }
    }

    fun queued(): Int = synchronized(lock) { list().size }

    fun flushSoon(ms: Long) {
        synchronized(lock) {
            wait?.cancel()
            // only the pause can be cancelled: once it's over the flush is its own job, so a newer report never stops a
            // batch already on its way (the helper may have it: sent again, it would count twice)
            wait = scope.launch {
                delay(ms)
                scope.launch { flush() }
            }
        }
    }

    /** Sends everything waiting, 10 at a time. True when the queue is empty afterwards. */
    suspend fun flush(): Boolean = withContext(Dispatchers.IO) {
        sending.withLock {
            var ok = true
            while (ok) {
                val batch = synchronized(lock) { list().take(10) }
                if (batch.isEmpty()) break
                val body = buildJsonObject {
                    putJsonArray("reports") { batch.forEach { add(json.encodeToJsonElement(Report.serializer(), it)) } }
                }.toString()
                ok = try {
                    send(body)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                // the sent ones themselves: reports queued meanwhile (and the oldest the cap let go) don't shift a count
                if (ok) synchronized(lock) { list().let { q -> q.removeAll { r -> batch.any { it === r } }; save(q) } }
            }
            ok
        }
    }

    /** Joins the queue (at most 30, the oldest go first). Blocks on a file write: callers are off the main thread. */
    internal fun enqueue(rs: List<Report>) = synchronized(lock) {
        val q = list()
        q.addAll(rs)
        while (q.size > 30) q.removeAt(0)
        save(q)
    }

    private fun list(): MutableList<Report> = queue ?: (
        if (!queueFile.exists()) mutableListOf()
        else runCatching { json.decodeFromString(ListSerializer(Report.serializer()), queueFile.readText()) }.getOrDefault(emptyList()).toMutableList()
        ).also { queue = it }

    /** A full phone mustn't crash the app: the queue stays in memory and the next change writes it again. */
    private fun save(rs: List<Report>) {
        runCatching {
            val tmp = File(queueFile.parentFile, "queue.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Report.serializer()), rs))
            swapIn(tmp, queueFile)
        }
    }

    private val sentSer = MapSerializer(String.serializer(), String.serializer())
    private val sentLock = Any() // apart from the queue's lock: a report built on the main thread never waits on a file write

    /** The web app's S.bugSent: signature -> the day it was sent, at most 150 kept. */
    private fun firstToday(sig: String): Boolean = synchronized(sentLock) {
        val day = LocalDate.now().toString()
        val sent = runCatching { json.decodeFromString(sentSer, Store.prefs.getString("bugSent", "{}")!!) }.getOrDefault(emptyMap())
        if (sent[sig] == day) return false
        val next = LinkedHashMap(sent).apply { remove(sig); put(sig, day) }
        val kept = next.entries.toList().takeLast(150).associate { it.key to it.value }
        Store.prefs.edit().putString("bugSent", json.encodeToString(sentSer, kept)).apply()
        true
    }
}
