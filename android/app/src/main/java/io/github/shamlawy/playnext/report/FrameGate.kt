package io.github.shamlawy.playnext.report

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.metrics.performance.JankStats
import io.github.shamlawy.playnext.Screen

/**
 * The phone frame gate (plan gate 1): every frame is timed by JankStats, and a frame of 100 ms or more files a "slow"
 * report in the web app's words ("A 180ms stutter on home"), so bugsync's rules for stutters apply as they are (three
 * sightings, or at once from 400 ms). The first 5 s after start and the 120 Hz test don't count: the test measures
 * itself, and a cold start's first frames are slow on any phone.
 */
class FrameGate private constructor(private val stats: JankStats) {
    fun on(yes: Boolean) {
        stats.isTrackingEnabled = yes
    }

    companion object {
        private const val SLOW_NS = 100_000_000L
        private const val WARM_MS = 5_000L
        private val main = Handler(Looper.getMainLooper())
        private val lock = Any()
        private var worst = 0L
        private var count = 0
        private var where = ""

        fun track(a: Activity): FrameGate? = runCatching {
            FrameGate(JankStats.createAndTrack(a.window) { fd -> seen(fd.frameDurationUiNanos) })
        }.getOrNull()

        private fun seen(ns: Long) {
            if (ns < SLOW_NS) return
            if (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() < WARM_MS) return
            val screen = Screen.current
            if (screen == "probe") return
            synchronized(lock) {
                if (count == 0) {
                    where = screen
                    // one report per burst: the worst frame of the next 2 s
                    main.postDelayed(::file, 2_000)
                }
                count++
                worst = maxOf(worst, ns)
            }
        }

        private fun file() {
            val (ms, n, s) = synchronized(lock) {
                Triple(worst / 1_000_000, count, where).also { worst = 0; count = 0 }
            }
            if (n == 0) return
            Reporter.report("slow", "A ${ms}ms stutter on $s", note = "$n slow frame" + (if (n > 1) "s" else "") + " in 2 s")
        }
    }
}
