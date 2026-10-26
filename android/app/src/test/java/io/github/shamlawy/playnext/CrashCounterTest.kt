package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.crash.CrashCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Never stuck on a bad version: two crashes within a minute of start open safe mode, a calm minute clears it. */
class CrashCounterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun twoEarlyCrashesOpenSafeMode() {
        val c = CrashCounter(tmp.newFolder("crash"))
        assertFalse(c.safeMode)
        c.crashed(sinceStartMs = 1_200)
        assertFalse("one crash is not enough", c.safeMode)
        c.crashed(sinceStartMs = 59_000)
        assertTrue(c.safeMode)
        // it survives a restart: a new counter on the same folder reads the file
        assertTrue(CrashCounter(c.let { tmp.root.resolve("crash") }).safeMode)
    }

    @Test
    fun aLateCrashDoesntCount() {
        val c = CrashCounter(tmp.newFolder("crash"))
        c.crashed(sinceStartMs = 2_000)
        c.crashed(sinceStartMs = CrashCounter.WINDOW_MS + 1)
        assertEquals(1, c.count)
        assertFalse(c.safeMode)
    }

    @Test
    fun aCalmMinuteStartsOver() {
        val c = CrashCounter(tmp.newFolder("crash"))
        c.crashed(1_000)
        c.calm()
        c.crashed(1_000)
        assertFalse(c.safeMode)
        c.crashed(1_000)
        assertTrue(c.safeMode)
        c.calm() // "Try again" and a new version both start over
        assertFalse(c.safeMode)
    }

    @Test
    fun aBrokenFileCountsAsNoCrash() {
        val dir = tmp.newFolder("crash")
        dir.resolve("early.txt").writeText("not a number")
        assertEquals(0, CrashCounter(dir).count)
    }
}
