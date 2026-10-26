package io.github.shamlawy.playnext.report

import android.content.Context
import io.github.shamlawy.playnext.Device
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.crash.swapIn
import io.github.shamlawy.playnext.net.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.time.LocalDate

/**
 * Problem reports, the web app's way: one signature per problem, once a day, scrubbed, batches of 10 to the
 * helper's /bug. Kept in a file so a crash or no signal never loses one; the next start sends what's left.
 */
object Reporter {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context
    private lateinit var queueFile: File
    private val lock = Any()
    private var pending: Job? = null

    /** Names to scrub (game titles, list names) and secrets (RAWG key); filled once the backup is read (Piece 2). */
    @Volatile var names: Collection<String> = emptyList()
    @Volatile var secrets: Collection<String> = emptyList()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        queueFile = File(app.filesDir, "reports/queue.txt").apply { parentFile?.mkdirs() }
    }

    /** Builds the report the way index.html does. Returns null when it was already sent today. */
    fun build(kind: String, msg: String, stack: String = "", at: String = "", note: String = "", screen: String = Screen.current, now: Long = System.currentTimeMillis()): Report? {
        if (Device.emulator) return null
        val m = Scrub.text(msg, names, secrets).trim()
        if (m.isEmpty()) return null
        val a = Scrub.text(at, names, secrets)
        val sig = ReportRules.sig(kind, m, a, screen, now)
        if (!firstToday(sig)) return null
        return Report(
            sig = sig, kind = kind, msg = m,
            stack = Scrub.text(stack, names, secrets).lines().take(8).joinToString("\n"),
            at = a, screen = screen, note = if (note.isNotBlank()) Scrub.text(note, names, secrets) else "",
            w = Device.widthDp(app), h = Device.heightDp(app), dpr = Math.round(Device.density(app) * 100) / 100.0,
            dev = Device.dev(app), v = Device.versionCode.toString(),
        )
    }

    /** An automatic report: sent 3 s later with whatever else turns up. */
    fun report(kind: String, msg: String, stack: String = "", at: String = "", note: String = "") {
        val r = build(kind, msg, stack, at, note) ?: return
        enqueue(listOf(r))
        flushSoon(3000)
    }

    /** "Report a problem": sent at once. The result says whether it went or waits for a signal. */
    suspend fun user(text: String): Boolean {
        val t = text.trim().take(800)
        if (t.isEmpty()) return false
        val r = build("user", t.take(200), note = t) ?: return false
        enqueue(listOf(r))
        return flush()
    }

    /** Once per app version: "this version ran", the proof bugsync needs before it closes a fixed stutter. */
    fun ranOnce() {
        val code = Device.versionCode
        if (Store.prefs.getInt("ranV", 0) == code) return
        build("ran", "Android app v$code ran")?.let { enqueue(listOf(it)) }
        Store.prefs.edit().putInt("ranV", code).apply()
        flushSoon(5000)
    }

    /** Reports written by an earlier process (a crash) join the queue. */
    fun adopt(files: List<File>) {
        val got = files.mapNotNull { f -> runCatching { json.decodeFromString(Report.serializer(), f.readText()) }.getOrNull().also { f.delete() } }
        if (got.isNotEmpty()) enqueue(got)
    }

    fun queued(): Int = synchronized(lock) { read().size }

    fun flushSoon(ms: Long) {
        pending?.cancel()
        pending = scope.launch { delay(ms); flush() }
    }

    /** Sends everything waiting, 10 at a time. True when the queue is empty afterwards. */
    suspend fun flush(): Boolean {
        while (true) {
            val batch = synchronized(lock) { read().take(10) }
            if (batch.isEmpty()) return true
            val body = buildJsonObject {
                putJsonArray("reports") { batch.forEach { add(json.encodeToJsonElement(Report.serializer(), it)) } }
            }.toString()
            val ok = runCatching { Net.call(Net.postJson(Net.helper("/bug"), body)) { it.isSuccessful } }.getOrDefault(false)
            if (!ok) return false
            synchronized(lock) { write(read().drop(batch.size)) }
        }
    }

    private fun enqueue(rs: List<Report>) = synchronized(lock) { write((read() + rs).takeLast(30)) }

    private fun read(): List<Report> =
        if (!queueFile.exists()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(Report.serializer()), queueFile.readText()) }.getOrDefault(emptyList())

    private fun write(rs: List<Report>) {
        val tmp = File(queueFile.parentFile, "queue.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(Report.serializer()), rs))
        swapIn(tmp, queueFile)
    }

    private val sentSer = MapSerializer(String.serializer(), String.serializer())

    /** The web app's S.bugSent: signature -> the day it was sent, at most 150 kept. */
    private fun firstToday(sig: String): Boolean = synchronized(lock) {
        val day = LocalDate.now().toString()
        val sent = runCatching { json.decodeFromString(sentSer, Store.prefs.getString("bugSent", "{}")!!) }.getOrDefault(emptyMap())
        if (sent[sig] == day) return false
        val next = LinkedHashMap(sent).apply { remove(sig); put(sig, day) }
        val kept = next.entries.toList().takeLast(150).associate { it.key to it.value }
        Store.prefs.edit().putString("bugSent", json.encodeToString(sentSer, kept)).apply()
        true
    }
}
