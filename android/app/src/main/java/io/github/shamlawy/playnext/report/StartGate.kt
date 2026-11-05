package io.github.shamlawy.playnext.report

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.SystemClock
import androidx.profileinstaller.ProfileVerifier
import io.github.shamlawy.playnext.Device
import io.github.shamlawy.playnext.crash.swapIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * The phone's cold start (plan gate 10, "Cold start timed in CI and on the phone": the web's open-time freeze, #334,
 * stayed open across many releases). Android keeps its own record of every start ([ApplicationStartInfo]: when the tap
 * launched the app, its first frame, and when the app said it was fully drawn, which the root does once your games show).
 * A cold start from the launcher or Recents that was slow files a "slow" report ("Opening the app took 1240ms"), so
 * bugsync's rules for timing reports apply as they are; a freeze in its first seconds is [FrameGate]'s. The times of the
 * last cold starts are kept per version, and the fifth one sends them once as a measurement (normal ones go only to
 * bugs.yml's log), so the phone's real numbers are known, not only the bad ones.
 */
object StartGate {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** When your games (or the restore screen) first showed in this process, on the elapsedRealtime clock in ns; 0 = not yet. */
    @Volatile private var shownAt = 0L

    /** The root calls this after the first frame that shows your games or the restore screen. */
    fun shown() {
        if (shownAt == 0L) shownAt = SystemClock.elapsedRealtimeNanos()
    }

    /** When [watch] ran: a start whose first frame came before it isn't this watch's (safe mode's "Try again"). */
    @Volatile private var watchedAt = 0L

    /** Once per process (PlayNextApp.start): Android calls back when this start's record is complete. */
    fun watch(ctx: Context) {
        val app = ctx.applicationContext
        watchedAt = SystemClock.elapsedRealtimeNanos()
        runCatching {
            app.getSystemService(ActivityManager::class.java)
                .addApplicationStartInfoCompletionListener(Dispatchers.IO.asExecutor()) { info -> io.launch { seen(app, info) } }
        }
    }

    private suspend fun seen(app: Context, info: ApplicationStartInfo) {
        val stamps = info.startupTimestamps
        val launch = stamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH] ?: return
        val first = stamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]
        // safe mode's "Try again" watches from inside a process whose start (safe mode's own screen) is long over
        if (first == null || first < watchedAt) return
        // the record is complete at the first frame, often before your games show: wait for them a little. `info` is a
        // copy made then, so Android's later "fully drawn" mark (ReportDrawnWhen) is read from a fresh copy afterwards
        var waited = 0
        while (shownAt == 0L && waited < StartRules.WAIT_MS) {
            delay(250)
            waited += 250
        }
        val drawn = runCatching {
            app.getSystemService(ActivityManager::class.java).getHistoricalProcessStartReasons(1).firstOrNull()
                ?.startupTimestamps?.takeIf { it[ApplicationStartInfo.START_TIMESTAMP_LAUNCH] == launch }
                ?.get(ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN)
        }.getOrNull()
        val s = StartRules.of(info.startType, info.reason, launch, first, drawn ?: shownAt.takeIf { it > 0 }) ?: return
        val c = compiled()
        // a start before this version is compiled ahead (a sideloaded update compiles when the phone is idle) is
        // slower on any phone: it's kept for the measurement, but only a compiled start can be reported as slow
        if (c == COMPILED) StartRules.slow(s)?.let { (msg, note) -> Reporter.report("slow", msg, note = "$note; $c", screen = "start") }
        keep(app, s, c == COMPILED)
    }

    private const val COMPILED = "compiled ahead"

    /** Whether this version runs compiled ahead (its Baseline Profile): a start right after an update may not yet. */
    private fun compiled(): String = runCatching {
        val c = ProfileVerifier.getCompilationStatusAsync().get()
        if (c.isCompiledWithProfile) COMPILED else if (c.hasProfileEnqueuedForCompilation()) "compiling after an update" else "not compiled ahead"
    }.getOrDefault("compile state unknown")

    /** The last cold starts of this version, one line each; the [StartRules.SAMPLE]th sends them once. */
    private fun keep(app: Context, s: StartRules.Start, compiled: Boolean) {
        if (Device.emulator) return
        runCatching {
            val f = File(app.filesDir, "reports/starts.txt").apply { parentFile?.mkdirs() }
            val v = Device.versionCode
            val had = if (f.exists()) StartRules.read(f.readText(), v) else StartRules.Log(v)
            if (had.sent) return
            val now = had.add(s.copy(compiled = compiled))
            val msg = StartRules.measure(now)
            // queued first, then marked sent: a process that dies in between sends it again (once a day at most)
            if (msg != null) Reporter.report("slow", msg, screen = "start")
            val tmp = File(f.parentFile, "starts.tmp")
            tmp.writeText(StartRules.write(if (msg != null) now.copy(sent = true) else now))
            swapIn(tmp, f)
        }
    }
}

/** What counts and what it says, apart from Android so a JVM test can check it. */
internal object StartRules {
    /** A cold start whose first frame takes this long is slow (Android vitals call 5 s excessive; a 2026 flagship with its Baseline Profile opens in well under a second). */
    const val SLOW_FIRST_MS = 1_000L

    /** …or whose games take this long to show. */
    const val SLOW_SHOWN_MS = 1_500L

    /** How long to wait for your games after the first frame before judging the start on the first frame alone. */
    const val WAIT_MS = 10_000

    /** Cold starts kept per version before they're sent as one measurement. */
    const val SAMPLE = 5

    /** [firstMs]: tap to first frame; [shownMs]: tap to your games (null when they never came within [WAIT_MS]). */
    data class Start(val firstMs: Long, val shownMs: Long?, val compiled: Boolean = true)

    /** A cold start you opened (the launcher, Recents, a shortcut or link), not one Android made for the update check. */
    fun of(type: Int, reason: Int, launch: Long, first: Long?, shown: Long?): Start? {
        if (type != ApplicationStartInfo.START_TYPE_COLD || first == null || first < launch) return null
        if (reason != ApplicationStartInfo.START_REASON_LAUNCHER && reason != ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS &&
            reason != ApplicationStartInfo.START_REASON_START_ACTIVITY
        ) return null
        val ms = { t: Long -> (t - launch) / 1_000_000 }
        // your games are often drawn in the very frame Android counts as the first: never earlier than it
        return Start(ms(first), shown?.coerceAtLeast(first)?.let(ms))
    }

    /** The report for a slow start (its message counts as a timing report in bugsync), or null. */
    fun slow(s: Start): Pair<String, String>? {
        val shown = s.shownMs
        if (s.firstMs < SLOW_FIRST_MS && (shown == null || shown < SLOW_SHOWN_MS)) return null
        val ms = maxOf(s.firstMs, shown ?: 0)
        val note = "cold start: first frame ${s.firstMs}ms, " + (if (shown != null) "your games ${shown}ms" else "your games not shown within ${WAIT_MS / 1000}s")
        return "Opening the app took ${round(ms)}ms" to note
    }

    data class Log(val v: Int, val starts: List<Start> = emptyList(), val sent: Boolean = false) {
        fun add(s: Start) = copy(starts = (starts + s).takeLast(SAMPLE))
    }

    /** The measurement once [SAMPLE] cold starts are in (no "%" and no "worst": bugsync reads those as a problem). */
    fun measure(l: Log): String? {
        if (l.starts.size < SAMPLE) return null
        val first = median(l.starts.map { it.firstMs })
        val shown = l.starts.mapNotNull { it.shownMs }
        return "Self-test: $SAMPLE cold starts on android-v${l.v}: first frame ${round(first)}ms" +
            (if (shown.isNotEmpty()) ", your games ${round(median(shown))}ms" else "") +
            " (middle values; slowest ${round(l.starts.maxOf { maxOf(it.firstMs, it.shownMs ?: 0) })}ms; " +
            "${l.starts.count { it.compiled }} of $SAMPLE compiled ahead)"
    }

    fun write(l: Log): String = "v=${l.v} sent=${l.sent}\n" + l.starts.joinToString("") { "${it.firstMs} ${it.shownMs ?: -1} ${if (it.compiled) 1 else 0}\n" }

    /** The kept log, or a fresh one when it belongs to another version or can't be read. */
    fun read(text: String, v: Int): Log {
        val lines = text.lines().filter { it.isNotBlank() }
        val head = Regex("^v=(\\d+) sent=(true|false)$").find(lines.firstOrNull() ?: "") ?: return Log(v)
        if (head.groupValues[1].toInt() != v) return Log(v)
        val starts = lines.drop(1).mapNotNull { line ->
            val p = line.split(" ").mapNotNull { it.toLongOrNull() }
            if (p.size == 3) Start(p[0], p[1].takeIf { it >= 0 }, p[2] == 1L) else null
        }
        return Log(v, starts.takeLast(SAMPLE), head.groupValues[2] == "true")
    }

    private fun median(a: List<Long>): Long = a.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

    /** Tens of ms, as the web's reports say them. */
    private fun round(ms: Long) = Math.round(ms / 10.0) * 10
}
