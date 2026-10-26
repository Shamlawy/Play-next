package io.github.shamlawy.playnext.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.report.Report
import io.github.shamlawy.playnext.report.Reporter
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Never stuck on a bad version: a crash within a minute of start counts, a minute of calm clears the count, and two in a
 * row open safe mode (a plain screen that can only update, send the reports and try again).
 */
class CrashCounter(private val dir: File) {
    private val file get() = File(dir, "early.txt")

    val count: Int get() = runCatching { file.readText().trim().toInt() }.getOrDefault(0)

    fun crashed(sinceStartMs: Long) {
        if (sinceStartMs < WINDOW_MS) write(count + 1)
    }

    fun calm() = write(0)

    val safeMode: Boolean get() = count >= 2

    private fun write(n: Int) {
        dir.mkdirs()
        val tmp = File(dir, "early.tmp")
        tmp.writeText(n.toString())
        swapIn(tmp, file)
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

    /** First thing in Application.onCreate. */
    fun install(ctx: Context) {
        dir = File(ctx.filesDir, "crash").apply { mkdirs() }
        counter = CrashCounter(dir)
        val started = Process.getStartElapsedRealtime()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                counter.crashed(SystemClock.elapsedRealtime() - started)
                save(e)
            }
            if (before != null) before.uncaughtException(t, e) else Process.killProcess(Process.myPid())
        }
        Handler(Looper.getMainLooper()).postDelayed({ counter.calm() }, CrashCounter.WINDOW_MS)
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
                    stack = runCatching { x.traceInputStream?.bufferedReader()?.use { r -> r.lineSequence().take(40).joinToString("\n") } }.getOrNull() ?: "",
                )
                ApplicationExitInfo.REASON_CRASH_NATIVE -> Reporter.report("error", "Native crash on $screen", stack = x.description ?: "")
                ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
                    Reporter.report("killed", "Android closed the app for memory on $screen", stack = x.description ?: "")
                else -> Unit
            }
        }
        Screen.set(ctx, Screen.current)
    }
}
