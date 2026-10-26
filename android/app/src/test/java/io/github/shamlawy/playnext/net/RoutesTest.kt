package io.github.shamlawy.playnext.net

import io.github.shamlawy.playnext.Repo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The practice copy's write switch against the real helper: every route in worker/helper.js must be classified. */
class RoutesTest {
    private val helper = Repo.text("worker/helper.js")
    private val H = Routes.HELPER_HOST

    private fun routesInHelper(): Set<String> {
        val exact = Regex("url\\.pathname === \"(/[^\"]*)\"").findAll(helper).map { it.groupValues[1] }
        val prefix = Regex("url\\.pathname\\.startsWith\\(\"(/[^\"]*)\"\\)").findAll(helper).map { it.groupValues[1] }
        val vault = Regex("op === \"(\\w+)\"").findAll(helper).map { "/vault/" + it.groupValues[1] }
        return (exact + prefix + vault).toSet()
    }

    @Test
    fun everyHelperRouteIsListed() {
        val found = routesInHelper()
        assertTrue("found only ${found.size} routes: did helper.js's dispatcher change shape?", found.size >= 35)
        val missing = found - Routes.KNOWN
        assertTrue("worker/helper.js has routes Routes.kt doesn't classify: $missing (add each as a read or a write)", missing.isEmpty())
    }

    @Test
    fun writesAreBlocked() {
        val writes = listOf(
            "POST" to "/vault/put", "POST" to "/vault/del", "POST" to "/cal/put", "GET" to "/nudge/key", "POST" to "/nudge",
            "POST" to "/nudge/test", "POST" to "/announce", "GET" to "/announce", "POST" to "/usage", "GET" to "/usage",
            "POST" to "/book/key", "GET" to "/book/keys", "POST" to "/steam/me", "POST" to "/steam/sync", "GET" to "/bugs",
            "POST" to "/bugs/ack", "POST" to "/vault/anything",
        )
        for ((m, p) in writes) assertTrue("$m $p must be blocked", Routes.check(m, H, p) is Routes.Block)
    }

    @Test
    fun readsAndReportsGoThrough() {
        val reads = listOf(
            "GET" to "/vault/list", "GET" to "/vault/get", "POST" to "/reviews", "POST" to "/hours", "POST" to "/art",
            "POST" to "/news", "POST" to "/ost", "GET" to "/ost/clip", "POST" to "/yt", "GET" to "/fx", "GET" to "/version",
            "GET" to "/sgdb/grids/game/1", "GET" to "/cal/abc.ics", "POST" to "/bug", "POST" to "/chat", "POST" to "/similar",
        )
        for ((m, p) in reads) assertEquals("$m $p must be allowed", Routes.Allow, Routes.check(m, H, p))
    }

    @Test
    fun priceWatchingIsAWrite() {
        assertEquals(Routes.Allow, Routes.check("POST", H, "/prices", """{"items":[{"t":"x"}]}"""))
        for (k in listOf("id", "watch", "off", "all")) {
            assertTrue(k, Routes.check("POST", H, "/prices", """{"$k": 1}""") is Routes.Block)
        }
    }

    @Test
    fun unknownRoutesAndOtherHostsWritesAreBlocked() {
        assertTrue(Routes.check("GET", H, "/something-new") is Routes.Block)
        assertTrue(Routes.check("PUT", Routes.SYNC_HOST, "/u/abc") is Routes.Block)
        assertTrue(Routes.check("POST", "api.rawg.io", "/api/games") is Routes.Block)
        assertEquals(Routes.Allow, Routes.check("GET", "api.rawg.io", "/api/games"))
        assertEquals(Routes.Allow, Routes.check("GET", "itunes.apple.com", "/search"))
    }

    @Test
    fun theHelperGetsTheWebOrigin() {
        assertTrue(Routes.wantsOrigin(H))
        assertTrue(Routes.wantsOrigin(Routes.SYNC_HOST))
        assertTrue(!Routes.wantsOrigin("api.github.com"))
    }

    @Test
    fun theHelperStillChecksThatOrigin() {
        assertTrue("helper.js no longer allows https://shamlawy.github.io", helper.contains("https://shamlawy.github.io"))
    }
}
