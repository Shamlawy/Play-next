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

    @Test
    fun aFreezeWhileOpeningIsTheStartsOwn() {
        // the web's boot check: a frame of 300 ms or more in the first seconds; the very first frame is the start itself
        assertTrue(FrameRules.boot(320 * ms, start + 2_000, start, 0, "home", 1))
        assertFalse("the first frame", FrameRules.boot(900 * ms, start + 400, start, 0, "home", 0))
        assertFalse("under 300 ms", FrameRules.boot(290 * ms, start + 2_000, start, 0, "home", 1))
        assertFalse("after the first 5 s the stutter rule has it", FrameRules.boot(400 * ms, start + 5_000, start, 0, "home", 1))
        assertFalse(FrameRules.boot(400 * ms, start + 2_000, start, 0, "probe", 1))
        // unfolded 300 ms after opening: the relayout isn't a freeze (the review of android-v19)
        assertFalse("just unfolded", FrameRules.boot(400 * ms, start + 1_500, start, start + 1_200, "home", 1))
        assertTrue("settled", FrameRules.boot(400 * ms, start + 2_100, start, start + 1_200, "home", 1))
    }
}
