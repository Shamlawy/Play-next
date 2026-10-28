package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.report.FrameRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone frame gate's rules (fix list P): the whole frame is judged, a fold's relayout isn't a stutter. */
class FrameRulesTest {
    private val ms = 1_000_000L
    private val start = 1_000L
    private val later = start + 60_000

    @Test
    fun theWholeFrameIsJudged() {
        // what's passed is the whole frame (UI thread, RenderThread, GPU): 150 ms of it counts, however little was UI
        assertTrue(FrameRules.counts(150 * ms, later, start, 0, "home"))
        assertFalse(FrameRules.counts(99 * ms, later, start, 0, "home"))
    }

    @Test
    fun aFoldOrATurnSettlesFirst() {
        val folded = later - 300
        assertFalse("300 ms after the window changed size", FrameRules.counts(400 * ms, later, start, folded, "home"))
        assertTrue("900 ms after", FrameRules.counts(400 * ms, folded + 900, start, folded, "home"))
    }

    @Test
    fun aColdStartAndTheProbeDontCount() {
        assertFalse(FrameRules.counts(400 * ms, start + 4_000, start, 0, "home"))
        assertFalse(FrameRules.counts(400 * ms, later, start, 0, "probe"))
    }
}
