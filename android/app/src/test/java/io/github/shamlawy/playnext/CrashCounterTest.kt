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
    fun aCalmStartWritesNothing() {
        val dir = tmp.newFolder("crash")
        CrashCounter(dir).calm()
        assertFalse("a calm minute with nothing counted touches no file (a full phone can't fail it)", dir.resolve("early.txt").exists())
    }

    @Test
    fun anEarlyFreezeOrNativeCrashCounts() {
        val c = CrashCounter(tmp.newFolder("crash"))
        val start = 1_000_000L
        c.exitedEarly(start, start + 8_000, fatal = true)
        assertEquals("a freeze 8 s in counts", 1, c.count)
        c.exitedEarly(start, start + CrashCounter.WINDOW_MS + 1, fatal = true)
        c.exitedEarly(start, start + 2_000, fatal = false)
        c.exitedEarly(0L, start, fatal = true)
        assertEquals("late, not fatal (a memory kill) or no start known: none count", 1, c.count)
        c.exitedEarly(start, start + 3_000, fatal = true)
        assertTrue(c.safeMode)
    }

    @Test
    fun theFrozenMainThreadOutOfAnAnrTrace() {
        val trace = (List(30) { "header line $it" } + listOf(
            "",
            "\"Signal Catcher\" daemon prio=10 tid=7 Runnable",
            "  at somewhere.Else(Else.java:1)",
            "",
            "\"main\" prio=5 tid=1 Sleeping",
            "  | group=\"main\" sCount=1 ucsCount=0 flags=1 obj=0x72 self=0xb4",
            "  | state=S schedstat=( 0 0 0 ) utm=5 stm=1 core=3 HZ=100",
            "  at java.lang.Thread.sleep(Native method)",
            "  - sleeping on <0x0a> (a java.lang.Object)",
            "  at io.github.shamlawy.playnext.ui.Slow.draw(Slow.kt:12)",
            "  native: #00 pc 000 /apex/libc.so (syscall+28)",
            "",
            "\"other\" prio=5 tid=2 Waiting",
            "  at a.B(B.java:3)",
        )).asSequence()
        val main = io.github.shamlawy.playnext.crash.CrashGuard.mainThread(trace)
        assertEquals(
            listOf("\"main\" prio=5 tid=1 Sleeping", "at java.lang.Thread.sleep(Native method)", "at io.github.shamlawy.playnext.ui.Slow.draw(Slow.kt:12)", "native: #00 pc 000 /apex/libc.so (syscall+28)"),
            main.lines(),
        )
        assertEquals("", io.github.shamlawy.playnext.crash.CrashGuard.mainThread(sequenceOf("no threads here")))
    }

    @Test
    fun aBrokenFileCountsAsNoCrash() {
        val dir = tmp.newFolder("crash")
        dir.resolve("early.txt").writeText("not a number")
        assertEquals(0, CrashCounter(dir).count)
    }
}
