package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.probe.ProbeMath
import io.github.shamlawy.playnext.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeAndLinksTest {
    private val ms = 1_000_000L
    private fun frames(n: Int, intervalNs: Long) = List(n) { intervalNs }

    @Test
    fun aSmooth120HzFlingPasses() {
        val r = ProbeMath.result(1, frames(1200, 8_333_333), frames(1200, 6 * ms), jank = 2, minHz = 120f, maxHz = 120f)
        assertTrue(r.line(), r.pass)
        assertEquals(1.0, r.onTime, 0.0)
        assertTrue(r.line().startsWith("attempt 1: 100% on time, p50 6.0 p90 6.0 p99 6.0 ms, 120 Hz"))
    }

    @Test
    fun sixtyHzFails() {
        val r = ProbeMath.result(1, frames(600, 16_666_667), frames(600, 6 * ms), 0, 60f, 60f)
        assertFalse(r.pass)
        assertEquals(0.0, r.onTime, 0.0)
    }

    @Test
    fun droppedFramesOrSlowFramesFail() {
        // 6% of frames late: under the 95% bar
        val late = frames(940, 8_333_333) + frames(60, 16_666_667)
        assertFalse(ProbeMath.result(1, late, frames(1000, 6 * ms), 0, 120f, 120f).pass)
        // on time but 2% of frames took 20 ms to make: p99 over 16.7
        val slow = frames(980, 6 * ms) + frames(20, 20 * ms)
        assertFalse(ProbeMath.result(1, frames(1000, 8_333_333), slow, 0, 120f, 120f).pass)
        // too few frames to say anything
        assertFalse(ProbeMath.result(1, frames(10, 8_333_333), frames(10, 6 * ms), 0, 120f, 120f).pass)
    }

    @Test
    fun percentiles() {
        val s = (1L..100L).toList()
        assertEquals(50L, ProbeMath.percentile(s, 50.0))
        assertEquals(99L, ProbeMath.percentile(s, 99.0))
        assertEquals(100L, ProbeMath.percentile(s, 100.0))
        assertEquals(0L, ProbeMath.percentile(emptyList(), 50.0))
    }

    @Test
    fun deepLinks() {
        assertEquals("probe", Links.nudge("playnext://open?nudge=probe"))
        assertEquals(Route.Probe, Links.route("playnext://open?x=1&nudge=probe"))
        assertEquals("What should I play", Links.nudge("playnext://open?nudge=What%20should%20I%20play"))
        assertEquals("a link this app doesn't know opens Home, never crashes", Route.Tab("home"), Links.route("playnext://open?nudge=someday"))
        assertNull(Links.route("playnext://open"))
        assertNull(Links.route("https://open?nudge=probe"))
        assertNull(Links.route("playnext://elsewhere?nudge=probe"))
        assertNull(Links.route("::: not a link"))
        assertNull(Links.route(null))
    }

    /** Every `?nudge=` the web app sends (shortcuts, pings, the calendar feed) opens its screen, or the nearest one built. */
    @Test
    fun everyNudgeOpensItsScreen() {
        fun r(q: String) = Links.route("playnext://open?$q")
        assertEquals(Route.Tab("add"), r("nudge=add"))
        assertEquals(Route.Tab("games", "queue"), r("nudge=queue"))
        assertEquals("What should I play?: On deck's first, whatever g says", Route.Game(null), r("nudge=next&g=x"))
        for (n in listOf("release", "game", "finish", "reviews", "steam")) {
            assertEquals(n, Route.Game("g7"), r("nudge=$n&g=g7"))
            assertEquals("$n without a game", Route.Tab("home"), r("nudge=$n"))
        }
        assertEquals(Route.Tab("taste", "duels"), r("nudge=duel"))
        assertEquals(Route.Tab("games", "played"), r("nudge=badge"))
        assertEquals(Route.Tab("games", "played"), r("nudge=verdict"))
        for (n in listOf("recap&m=2026-09", "deals", "update", "tip")) assertEquals(n, Route.Tab("home"), r("nudge=$n"))
        assertEquals("a game id is decoded", Route.Game("a b"), r("nudge=game&g=a%20b"))
    }
}
