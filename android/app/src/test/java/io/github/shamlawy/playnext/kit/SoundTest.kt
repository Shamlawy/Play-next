package io.github.shamlawy.playnext.kit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

/** The sound recipes as the web app's Web Audio would play them: timelines, waves, lengths, levels and the WAV. */
class SoundTest {
    private fun peak(x: FloatArray, from: Double = 0.0, to: Double = 99.0): Float {
        val a = (from * Recipes.RATE).toInt().coerceIn(0, x.size)
        val b = (to * Recipes.RATE).toInt().coerceIn(0, x.size)
        var m = 0f
        for (i in a until b) m = maxOf(m, abs(x[i]))
        return m
    }

    @Test fun paramRampsLikeWebAudio() {
        val g = Param(1.0).set(0.0, .1).lin(.5, .2).exp(.0001, .5)
        assertEquals("before the first event: the default", 1.0, g.at(.05), 1e-9)
        assertEquals(0.0, g.at(.1), 1e-9)
        assertEquals("half way up the straight line", .25, g.at(.15), 1e-9)
        assertEquals(.5, g.at(.2), 1e-9)
        // exponential: v0 * (v1/v0)^k
        assertEquals(.5 * Math.pow(.0001 / .5, .5), g.at(.35), 1e-9)
        assertEquals("after the last event it holds", .0001, g.at(.9), 1e-12)
    }

    @Test fun wavesAreBandLimitedAndPeakAtOne() {
        val syn = Synth(48000, .1)
        for (w in Wave.entries) {
            val x = syn.track()
            syn.osc(x, w, Param.of(110.0), Param.of(1.0), 0.0, .1)
            val p = x.maxOf { abs(it) }
            assertTrue("$w peaks near 1 (was $p)", p in .9f..1.05f)
        }
        // a sine is a sine: 440 Hz, a quarter cycle in
        val s = syn.track()
        syn.osc(s, Wave.Sine, Param.of(440.0), Param.of(1.0), 0.0, .1)
        val i = 48000 / 440 / 4
        assertEquals(sin(2 * Math.PI * 440 * i / 48000.0), s[i].toDouble(), 1e-3)
    }

    @Test fun everyRecipeFitsItsLengthAndIsHeard() {
        for (s in Sfx.entries) {
            val x = Recipes.render(s)
            assertEquals("$s length", (s.seconds * Recipes.RATE).toInt().toDouble(), x.size.toDouble(), 2.0)
            assertTrue("$s stays within -1..1", x.all { it in -1f..1f })
            assertTrue("$s is heard (peak ${peak(x)})", peak(x) > .01f)
            // the last 10ms are the envelope's .0001 tail or silence: nothing is cut off mid-sound
            assertTrue("$s ends quietly (${peak(x, s.seconds - .01)})", peak(x, s.seconds - .01) < .02f)
        }
    }

    @Test fun ticksAreAsQuietAsTheWebMakesThem() {
        // beep tap: a 520 Hz triangle at .05, straight to the speaker
        assertEquals(.05f, peak(Recipes.render(Sfx.Tap)), .01f)
        // open: its second note starts at 72% of the first (50ms), so there's sound at 60ms
        assertTrue(peak(Recipes.render(Sfx.Open), .055, .065) > .02f)
        // the day card: silent for its first 750ms, then the ticks
        val day = Recipes.render(Sfx.Day)
        assertEquals(0f, peak(day, 0.0, .74), 0f)
        assertTrue(peak(day, .75, .8) > .02f)
    }

    @Test fun theSameSeedMakesTheSameSound() {
        assertArrayEquals(Recipes.render(Sfx.Shatter, 3), Recipes.render(Sfx.Shatter, 3), 0f)
        assertTrue(!Recipes.render(Sfx.Shatter, 3).contentEquals(Recipes.render(Sfx.Shatter, 4)))
    }

    @Test fun wavIsSixteenBitMono() {
        val b = Wav.of(floatArrayOf(0f, 1f, -1f, .5f), 48000)
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals(44 + 8, b.size)
        assertEquals(36 + 8, bb.getInt(4))
        assertEquals(1, bb.getShort(22).toInt()) // mono
        assertEquals(48000, bb.getInt(24))
        assertEquals(16, bb.getShort(34).toInt())
        assertEquals(32767, bb.getShort(46).toInt())
        assertEquals(-32767, bb.getShort(48).toInt())
    }
}
