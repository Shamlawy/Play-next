package io.github.shamlawy.playnext

import android.app.ApplicationStartInfo
import io.github.shamlawy.playnext.report.StartRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's cold start (plan gate 10): which starts count, when one is slow, and the per-version measurement. */
class StartRulesTest {
    private val ms = 1_000_000L
    private val launch = 50_000 * ms
    private val cold = ApplicationStartInfo.START_TYPE_COLD
    private val tap = ApplicationStartInfo.START_REASON_LAUNCHER

    @Test
    fun onlyAColdStartYouOpenedCounts() {
        assertEquals(StartRules.Start(420, 510), StartRules.of(cold, tap, launch, launch + 420 * ms, launch + 510 * ms))
        assertEquals(StartRules.Start(420, null), StartRules.of(cold, ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS, launch, launch + 420 * ms, null))
        assertEquals(StartRules.Start(300, 450), StartRules.of(cold, ApplicationStartInfo.START_REASON_START_ACTIVITY, launch, launch + 300 * ms, launch + 450 * ms))
        assertNull("warm", StartRules.of(ApplicationStartInfo.START_TYPE_WARM, tap, launch, launch + 100 * ms, null))
        assertNull("the update check's job: no screen", StartRules.of(cold, ApplicationStartInfo.START_REASON_JOB, launch, launch + 300 * ms, null))
        assertNull("no first frame", StartRules.of(cold, tap, launch, null, null))
        // a games mark from before the first frame can't be the start's: left out, not a negative time
        assertEquals(StartRules.Start(420, null), StartRules.of(cold, tap, launch, launch + 420 * ms, launch + 100 * ms))
    }

    @Test
    fun aSlowStartIsATimingReport() {
        assertNull(StartRules.slow(StartRules.Start(420, 510)))
        assertNull(StartRules.slow(StartRules.Start(990, 1_490)))
        val (msg, note) = StartRules.slow(StartRules.Start(640, 1_536))!!
        assertEquals("Opening the app took 1540ms", msg)
        assertEquals("cold start: first frame 640ms, your games 1536ms", note)
        assertEquals("Opening the app took 1200ms", StartRules.slow(StartRules.Start(1_204, null))!!.first)
        // bugsync's timing-report pattern (HITCH in .github/scripts/bugsync.mjs) must read it
        assertTrue(Regex("^Opening the app (?:froze for|took) [\\d.]+ms").containsMatchIn(msg))
    }

    @Test
    fun fiveColdStartsAreSentOnce() {
        var log = StartRules.Log(19)
        listOf(StartRules.Start(400, 500), StartRules.Start(380, 470), StartRules.Start(900, 1_000), StartRules.Start(420, null)).forEach { log = log.add(it) }
        assertNull("four aren't enough", StartRules.measure(log))
        log = log.add(StartRules.Start(410, 520))
        val m = StartRules.measure(log)!!
        assertEquals("Self-test: 5 cold starts on android-v19: first frame 410ms, your games 510ms (middle values; slowest 1000ms)", m)
        // bugsync reads "%" and "worst Nms" as a problem; the measurement says neither
        assertTrue(!m.contains("%") && !m.contains("worst"))
        // kept in a file: read back the same, and a new version starts over
        val text = StartRules.write(log.copy(sent = true))
        assertEquals(log.copy(sent = true), StartRules.read(text, 19))
        assertEquals(StartRules.Log(20), StartRules.read(text, 20))
        assertEquals(StartRules.Log(19), StartRules.read("rubbish", 19))
    }
}
