package io.github.shamlawy.playnext.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeMathTest {
    private fun frames(n: Int, ms: Double) = List(n) { ms }

    @Test
    fun steady120Passes() {
        val v = ProbeMath.judge(frames(1200, 8.33), maxHz = 120f)
        assertTrue(v.pass)
        assertEquals(1.0, v.onTime, 0.0)
    }

    @Test
    fun steady60Fails() {
        assertFalse(ProbeMath.judge(frames(600, 16.67), maxHz = 60f).pass)
        assertFalse("120 Hz frames but the screen said 60", ProbeMath.judge(frames(1200, 8.33), maxHz = 60f).pass)
    }

    @Test
    fun aFewHitchesAreAllowedNotMany() {
        val few = frames(1170, 8.33) + frames(30, 16.67) // 2.5% late
        assertTrue(ProbeMath.judge(few, 120f).pass)
        val many = frames(1100, 8.33) + frames(100, 16.67) // 8.3% late
        assertFalse(ProbeMath.judge(many, 120f).pass)
    }

    @Test
    fun aLongStallFailsOnTheSlowestOnePercent() {
        val stalls = frames(1170, 8.33) + frames(30, 40.0)
        val v = ProbeMath.judge(stalls, 120f)
        assertTrue(v.onTime >= .95)
        assertFalse(v.pass)
    }

    @Test
    fun tooFewFramesNeverPass() {
        assertFalse(ProbeMath.judge(frames(50, 8.33), 120f).pass)
        assertFalse(ProbeMath.judge(emptyList(), 120f).pass)
    }

    @Test
    fun percentiles() {
        val xs = (1..100).map { it.toDouble() }
        assertEquals(51.0, ProbeMath.pct(xs, 50.0), 0.0)
        assertEquals(99.0, ProbeMath.pct(xs, 99.0), 0.0)
        assertEquals(0.0, ProbeMath.pct(emptyList(), 50.0), 0.0)
    }

    @Test
    fun headline() {
        assertEquals("Smooth at 120 Hz: 100.0% of frames on time", ProbeMath.headline(ProbeMath.judge(frames(1200, 8.33), 120f)))
    }
}
