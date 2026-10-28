package io.github.shamlawy.playnext.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import android.os.SystemClock
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.report.Report
import io.github.shamlawy.playnext.report.Reporter
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Never stuck on a bad version: a crash within a minute of start counts, a minute of calm clears the count, and two in a
 * row open safe mode (a plain screen that can only update, send the reports and try again).
 */
class CrashCounter(private val dir: File) {
    private val file get() = File(dir, "early.txt")

    /** Read from the file once, then kept: a start asks [safeMode] more than once. */
    @Volatile private var known: Int? = null

    val count: Int get() = known ?: runCatching { file.readText().trim().toInt() }.getOrDefault(0).also { known = it }

    fun crashed(sinceStartMs: Long) {
        if (sinceStartMs < WINDOW_MS) write(count + 1)
    }

    /**
     * A freeze (ANR) or a native crash ends a run without the Java handler above; Android's record of it, read at the
     * next start, counts the same when the run that ended had started within [WINDOW_MS] of it.
     */
    fun exitedEarly(startedAt: Long, exitAt: Long, fatal: Boolean) {
        if (fatal && startedAt > 0 && exitAt >= startedAt && exitAt - startedAt < WINDOW_MS) write(count + 1)
    }

    /** Nothing to write when nothing counted (most starts): a full phone can't fail it then. */
    fun calm() {
        if (count != 0) write(0)
    }

    val safeMode: Boolean get() = count >= 2

    private fun write(n: Int) {
        dir.mkdirs()
        val tmp = File(dir, "early.tmp")
        tmp.writeText(n.toString())
        swapIn(tmp, file)
        known = n
    }

    companion object {
        const val WINDOW_MS = 60_000L
    }
}

/**
 * Write a new file, then swap it in, so a crash mid-write never leaves half a file. renameTo doesn't replace an existing
 * file on every JVM (Windows: the PC's unit tests failed on it), so this moves with REPLACE_EXISTING.
 */
fun swapIn(tmp: File, dest: File) {
    Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

object CrashGuard {
    private val json = Json { encodeDefaults = true }
    lateinit var counter: CrashCounter
        private set
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var calming: Job? = null

    /** First thing in Application.onCreate. */
    fun install(ctx: Context) {
        dir = File(ctx.filesDir, "crash").apply { mkdirs() }
        counter = CrashCounter(dir)
        // the last run never had its calm minute on screen: did it end in a freeze or a native crash?
        val armed = Store.prefs.getLong("armedAt", 0L)
        if (armed > 0L) {
            runCatching {
                val x = ctx.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull()
                if (x != null) counter.exitedEarly(armed, x.timestamp, x.reason == ApplicationExitInfo.REASON_ANR || x.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
            }
        }
        Store.prefs.edit().putLong("armedAt", System.currentTimeMillis()).apply()
        val started = Process.getStartElapsedRealtime()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                counter.crashed(SystemClock.elapsedRealtime() - started)
                save(e)
            }
            if (before != null) before.uncaughtException(t, e) else Process.killProcess(Process.myPid())
        }
    }

    /**
     * The app on screen (MainActivity resumed) or not: the calm minute that clears the count only runs while you can
     * see the app, so safe mode left open, or the app started in the background for an update, never clears it.
     */
    fun onScreen(on: Boolean) {
        calming?.cancel()
        calming = if (!on) null else scope.launch {
            delay(CrashCounter.WINDOW_MS)
            runCatching { counter.calm() }
            Store.prefs.edit().remove("armedAt").apply()
        }
    }

    /** The crash as a report file; the next start sends it. Written raw, the reporter scrubs nothing it can't see yet. */
    private fun save(e: Throwable) {
        val top = e.stackTrace.firstOrNull()
        val r = Reporter.build(
            kind = "error",
            msg = (e::class.java.simpleName + ": " + (e.message ?: "")).trim(),
            stack = e.stackTraceToString(),
            at = top?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "",
        ) ?: return
        File(dir, "pending-${System.currentTimeMillis()}.txt").writeText(json.encodeToString(Report.serializer(), r))
    }

    fun pendingFiles(): List<File> = dir.listFiles { f -> f.name.startsWith("pending-") }?.toList().orEmpty()

    /**
     * Android's own record of why earlier runs ended: a freeze (ANR) or a native crash becomes a report, a memory kill
     * is log-only. Java crashes are already caught above, so they're skipped here.
     */
    fun readExits(ctx: Context) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val seen = Store.prefs.getLong("exitSeen", 0L)
        val exits = runCatching { am.getHistoricalProcessExitReasons(ctx.packageName, 0, 10) }.getOrDefault(emptyList())
            .filter { it.timestamp > seen }
        if (exits.isEmpty()) return
        Store.prefs.edit().putLong("exitSeen", exits.maxOf { it.timestamp }).apply()
        for (x in exits) {
            val screen = x.processStateSummary?.let { String(it) } ?: "?"
            when (x.reason) {
                ApplicationExitInfo.REASON_ANR -> Reporter.report(
                    "stuck", "The app froze and Android closed it on $screen",
                    stack = runCatching { x.traceInputStream?.bufferedReader()?.use { r -> mainThread(r.lineSequence()) } }.getOrNull() ?: "",
                )
                ApplicationExitInfo.REASON_CRASH_NATIVE -> Reporter.report("error", "Native crash on $screen", stack = x.description ?: "")
                ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
                    Reporter.report("killed", "Android closed the app for memory on $screen", stack = x.description ?: "")
                else -> Unit
            }
        }
        Screen.set(ctx, Screen.current)
    }

    /**
     * The frozen main thread out of an ANR trace: the trace opens with ~40 lines about the process, and the `"main"`
     * thread's block (its header, then `at …` frames) is what says where it hung. Its `|` detail lines are left out.
     */
    fun mainThread(lines: Sequence<String>): String {
        val block = lines.dropWhile { !it.startsWith("\"main\"") }.takeWhile { it.isNotBlank() }.toList()
        if (block.isEmpty()) return ""
        return (listOf(block.first()) + block.drop(1).map { it.trim() }.filter { it.startsWith("at ") || it.startsWith("native:") }).take(40).joinToString("\n")
    }
}
