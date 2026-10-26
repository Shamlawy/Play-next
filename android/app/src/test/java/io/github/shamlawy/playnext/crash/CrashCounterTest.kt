package io.github.shamlawy.playnext.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashCounterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun twoEarlyCrashesOpenSafeMode() {
        val c = CrashCounter(tmp.root)
        assertFalse(c.safeMode)
        c.crashed(sinceStartMs = 2_000)
        assertFalse("one crash is not enough", c.safeMode)
        c.crashed(sinceStartMs = 59_000)
        assertTrue(c.safeMode)
        // a new process reads the same file
        assertTrue(CrashCounter(tmp.root).safeMode)
    }

    @Test
    fun aLateCrashDoesNotCount() {
        val c = CrashCounter(tmp.root)
        c.crashed(sinceStartMs = 1_000)
        c.crashed(sinceStartMs = CrashCounter.WINDOW_MS + 1)
        assertEquals(1, c.count)
        assertFalse(c.safeMode)
    }

    @Test
    fun aCalmMinuteClearsTheCount() {
        val c = CrashCounter(tmp.root)
        c.crashed(1_000)
        c.crashed(1_000)
        c.calm()
        assertEquals(0, c.count)
        assertFalse(c.safeMode)
    }

    @Test
    fun aBrokenFileCountsAsNoCrash() {
        tmp.newFile("early.txt").writeText("not a number")
        assertEquals(0, CrashCounter(tmp.root).count)
    }
}
