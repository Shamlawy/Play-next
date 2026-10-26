package io.github.shamlawy.playnext.probe

import kotlin.math.ceil

/**
 * The 120 Hz test's pass rule (android/parity/0-map/6-critic.md G3), kept apart from Android so JVM tests check it:
 * during the fling at least 95% of frames come 8.9 ms or less after the one before, the slowest 1% of frames take under
 * 16.7 ms to make, and the screen ran at 120 Hz.
 */
object ProbeMath {
    const val ON_TIME_MS = 8.9
    const val P99_MS = 16.7
    const val HZ = 119f // 120 Hz panels report 119.9-120.1
    const val MIN_FRAMES = 60

    data class Result(
        val attempt: Int,
        val frames: Int, // frame intervals seen during the fling
        val onTime: Double, // share of intervals <= 8.9 ms
        val p50: Double, // frame time percentiles, ms
        val p90: Double,
        val p99: Double,
        val jankPct: Double, // JankStats' own verdict, % of frames
        val minHz: Float,
        val maxHz: Float,
    ) {
        val pass: Boolean get() = frames >= MIN_FRAMES && onTime >= 0.95 && p99 < P99_MS && maxHz >= HZ

        /** "attempt 1: 97% on time, p50 6.1 p90 7.9 p99 12.4 ms, 120 Hz, jank 0.4%" */
        fun line(): String = "attempt $attempt: ${pct(onTime)}% on time, p50 ${f1(p50)} p90 ${f1(p90)} p99 ${f1(p99)} ms, " +
            (if (minHz == maxHz) "${hz(maxHz)} Hz" else "${hz(minHz)}-${hz(maxHz)} Hz") + ", jank ${f1(jankPct)}%, $frames frames"
    }

    /** The nearest-rank percentile of [sorted] (ascending), 0 when empty. */
    fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0
        val i = (ceil(p / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[i]
    }

    fun result(attempt: Int, intervalsNs: List<Long>, durationsNs: List<Long>, jank: Int, minHz: Float, maxHz: Float): Result {
        val d = durationsNs.sorted()
        val ms = { ns: Long -> ns / 1_000_000.0 }
        val onTime = if (intervalsNs.isEmpty()) 0.0 else intervalsNs.count { ms(it) <= ON_TIME_MS } / intervalsNs.size.toDouble()
        return Result(
            attempt = attempt,
            frames = intervalsNs.size,
            onTime = onTime,
            p50 = ms(percentile(d, 50.0)),
            p90 = ms(percentile(d, 90.0)),
            p99 = ms(percentile(d, 99.0)),
            jankPct = if (d.isEmpty()) 0.0 else jank * 100.0 / d.size,
            minHz = if (minHz > maxHz) maxHz else minHz,
            maxHz = maxHz,
        )
    }

    private fun f1(x: Double) = "%.1f".format(java.util.Locale.ROOT, x)
    private fun pct(x: Double) = Math.round(x * 100).toString()
    private fun hz(x: Float) = Math.round(x).toString()
}
