package io.github.shamlawy.playnext.report

import android.app.Activity
import android.os.Process
import android.os.SystemClock
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import io.github.shamlawy.playnext.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The phone frame gate (plan gate 1): every frame is timed by JankStats, and a frame of 100 ms or more files a "slow"
 * report in the web app's words ("A 180ms stutter on home"), so bugsync's rules for stutters apply as they are (three
 * sightings, or at once from 400 ms). What doesn't count is in [FrameRules].
 */
class FrameGate private constructor(private val stats: JankStats) {
    fun on(yes: Boolean) {
        stats.isTrackingEnabled = yes
    }

    companion object {
        private val later = CoroutineScope(SupervisorJob() + Dispatchers.Default) // never the main thread
        private val lock = Any()
        private var worst = 0L
        private var worstUi = 0L
        private var count = 0
        private var where = ""

        /** When the window last changed size (fold, unfold, turn), on the elapsedRealtime clock. */
        @Volatile private var resized = 0L

        fun track(a: Activity): FrameGate? = runCatching {
            // MainActivity handles size changes itself (no recreate), so the window's own size says when one happened
            a.window.decorView.addOnLayoutChangeListener { _, l, t, r, b, l0, t0, r0, b0 ->
                if (r - l != r0 - l0 || b - t != b0 - t0) resized = SystemClock.elapsedRealtime()
            }
            FrameGate(JankStats.createAndTrack(a.window) { fd -> seen(fd) })
        }.getOrNull()

        private fun seen(fd: FrameData) {
            // the whole frame (UI thread, RenderThread and GPU), as the phone shows it; older data has the UI part only
            val total = (fd as? FrameDataApi31)?.frameDurationTotalNanos ?: fd.frameDurationUiNanos
            val screen = Screen.current
            if (!FrameRules.counts(total, SystemClock.elapsedRealtime(), Process.getStartElapsedRealtime(), resized, screen)) return
            synchronized(lock) {
                if (count == 0) {
                    where = screen
                    // one report per burst: the worst frame of the next 2 s
                    later.launch { delay(2_000); file() }
                }
                count++
                if (total > worst) {
                    worst = total
                    worstUi = fd.frameDurationUiNanos
                }
            }
        }

        private fun file() {
            var ms = 0L
            var ui = 0L
            var n = 0
            var s = ""
            synchronized(lock) {
                ms = worst / 1_000_000
                ui = worstUi / 1_000_000
                n = count
                s = where
                worst = 0
                worstUi = 0
                count = 0
            }
            if (n == 0) return
            val note = "$n slow frame" + (if (n > 1) "s" else "") + " in 2 s; the UI thread's part of the worst: ${ui}ms"
            Reporter.report("slow", "A ${ms}ms stutter on $s", note = note)
        }
    }
}

/** Which frames the gate counts, apart from Android so a JVM test can check it. */
internal object FrameRules {
    const val SLOW_NS = 100_000_000L

    /** A cold start's first frames are slow on any phone. */
    const val WARM_MS = 5_000L

    /** The relayout after a fold, unfold or turn isn't a stutter: the web skips 800 ms after a resize too (v245 #305). */
    const val SETTLE_MS = 800L

    /** [totalNs] is the frame's whole time; the times are elapsedRealtime ms. The 120 Hz test measures itself. */
    fun counts(totalNs: Long, now: Long, start: Long, resized: Long, screen: String): Boolean =
        totalNs >= SLOW_NS && now - start >= WARM_MS && now - resized >= SETTLE_MS && screen != "probe"
}
