package io.github.shamlawy.playnext.probe

import kotlin.math.roundToInt

/** The 120 Hz test's numbers (android/parity/0-map/6-critic.md G3), apart from Android so JVM tests can check them. */
object ProbeMath {
    /** One 120 Hz frame is 8.33 ms; 8.9 leaves room for the clock's jitter. */
    const val ON_TIME_MS = 8.9
    const val P99_MAX_MS = 16.7
    const val ON_TIME_SHARE = .95
    const val MIN_FRAMES = 200
    const val MIN_HZ = 119f

    data class Verdict(val pass: Boolean, val frames: Int, val onTime: Double, val p50: Double, val p99: Double, val hz: Float)

    /** The value at percentile [p] (0-100), nearest rank; 0 for no values. */
    fun pct(xs: List<Double>, p: Double): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return s[((p / 100.0) * (s.size - 1)).roundToInt().coerceIn(0, s.size - 1)]
    }

    fun onTime(intervals: List<Double>, limit: Double = ON_TIME_MS): Double =
        if (intervals.isEmpty()) 0.0 else intervals.count { it <= limit }.toDouble() / intervals.size

    /**
     * Pass: at least 95% of the fling's frame-to-frame gaps within one 120 Hz frame, the slowest 1% under one 60 Hz
     * frame, and the screen at 120 Hz while it flings. Too few frames never passes.
     */
    fun judge(intervalsMs: List<Double>, maxHz: Float): Verdict {
        val ot = onTime(intervalsMs)
        val p99 = pct(intervalsMs, 99.0)
        val pass = intervalsMs.size >= MIN_FRAMES && ot >= ON_TIME_SHARE && p99 < P99_MAX_MS && maxHz >= MIN_HZ
        return Verdict(pass, intervalsMs.size, ot, pct(intervalsMs, 50.0), p99, maxHz)
    }

    fun ms(x: Double) = "%.1f".format(java.util.Locale.ROOT, x)
    fun share(x: Double) = "%.1f%%".format(java.util.Locale.ROOT, x * 100)

    /** The one line Home shows and the report's title words. */
    fun headline(v: Verdict): String =
        if (v.pass) "Smooth at ${v.hz.roundToInt()} Hz: ${share(v.onTime)} of frames on time"
        else "Not 120 Hz yet: ${v.hz.roundToInt()} Hz, ${share(v.onTime)} of frames on time"
}
