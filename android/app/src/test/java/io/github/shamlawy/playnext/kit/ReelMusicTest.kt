package io.github.shamlawy.playnext.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The opening reel's soundtrack as the web's `pnReel.audio` makes it: 15 s, on the beat, the drop at the end, never clipping. */
class ReelMusicTest {
    private val x = ReelMusic.render()

    private fun rms(from: Double, to: Double): Double {
        val a = (from * Recipes.RATE).toInt()
        val b = (to * Recipes.RATE).toInt()
        var s = 0.0
        for (i in a until b) s += x[i].toDouble() * x[i]
        return sqrt(s / (b - a))
    }

    @Test fun fifteenSecondsThatNeverClip() {
        assertEquals((Recipes.RATE * 15.0).toInt(), x.size)
        assertTrue("kept inside -1 to 1", x.all { abs(it) <= .981f })
        assertTrue("loud enough to hear", rms(0.0, 14.0) > .05)
    }

    @Test fun aKickOnEveryBeat() {
        // the first 60 ms of each beat are louder than the quiet just before the next beat's hat
        for (b in listOf(1, 5, 9, 17, 25)) {
            val t = b * .5
            assertTrue("beat $b lands", rms(t, t + .06) > rms(t + .16, t + .22) * 1.5)
        }
    }

    @Test fun theRiserClimbsAndTheEndFallsQuiet() {
        assertTrue("the riser grows from 10 to 12 s", rms(11.55, 11.95) > rms(10.55, 10.95) * 1.3)
        assertTrue("silence after the drop", rms(14.95, 15.0) < rms(14.0, 14.3) / 10)
    }
}
