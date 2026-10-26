package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.net.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The practice copy's write switch (plan "Until switch day"): every helper route is classified, and nothing that writes gets out. */
class RoutesTest {
    private val h = Routes.HELPER_HOST
    private fun ok(m: String, path: String, host: String = h, body: String? = null) = Routes.check(m, host, path, body) == Routes.Allow
    private fun blocked(m: String, path: String, host: String = h, body: String? = null) = Routes.check(m, host, path, body) is Routes.Block

    /** Every path and prefix the helper's dispatcher matches, read from worker/helper.js itself. */
    private val helperRoutes: Set<String> by lazy {
        val js = Repo.text("worker/helper.js")
        val exact = Regex("""url\.pathname === "(/[^"]*)"""").findAll(js).map { it.groupValues[1] }
        val prefix = Regex("""url\.pathname\.startsWith\("(/[^"]*)"\)""").findAll(js).map { it.groupValues[1] }
        // vault() reads its operation off the path (/vault/<op>) and compares op === "put" / "list" / "get" / "del"
        val vault = Regex("""\bop === "(\w+)"""").findAll(js).map { "/vault/" + it.groupValues[1] }
        (exact + prefix + vault).toSet()
    }

    @Test
    fun everyHelperRouteIsClassified() {
        assertTrue("didn't find the helper's routes", helperRoutes.size > 25)
        val missing = helperRoutes - Routes.KNOWN
        assertTrue("worker/helper.js has routes Routes.kt doesn't classify (add each as read or write): $missing", missing.isEmpty())
    }

    @Test
    fun routesListedHereStillExist() {
        val gone = Routes.KNOWN - helperRoutes
        assertTrue("Routes.kt lists routes worker/helper.js no longer has: $gone", gone.isEmpty())
    }

    @Test
    fun writesAreBlocked() {
        for ((m, p) in listOf(
            "POST" to "/vault/put", "POST" to "/vault/del", "POST" to "/vault/anything",
            "POST" to "/cal/put", "GET" to "/nudge/key", "POST" to "/nudge", "POST" to "/nudge/test",
            "POST" to "/announce", "GET" to "/announce", "POST" to "/usage", "GET" to "/usage",
            "POST" to "/book/key", "GET" to "/book/keys", "POST" to "/steam/me", "POST" to "/steam/sync",
            "GET" to "/bugs", "POST" to "/bugs/ack", "POST" to "/never-heard-of-it",
        )) assertTrue("$m $p should be blocked", blocked(m, p))
    }

    @Test
    fun readsAndReportsGetThrough() {
        for ((m, p) in listOf(
            "GET" to "/vault/list", "GET" to "/vault/get", "POST" to "/bug", "GET" to "/version", "GET" to "/fx",
            "POST" to "/reviews", "POST" to "/hours", "POST" to "/art", "POST" to "/news", "POST" to "/ost",
            "GET" to "/ost/clip", "POST" to "/yt", "GET" to "/sgdb/grids/game/1", "GET" to "/cal/abc.ics",
            "POST" to "/why", "POST" to "/chat", "POST" to "/recap", "POST" to "/steam", "POST" to "/similar",
        )) assertTrue("$m $p should be allowed", ok(m, p))
    }

    @Test
    fun priceWatchingWritesButAPriceLookIsARead() {
        assertTrue(ok("POST", "/prices", body = """{"games":[{"t":"x"}]}"""))
        for (b in listOf("""{"id":"abc","watch":[]}""", """{"off": true}""", """{"all" :1}""")) {
            assertTrue("$b should be blocked", blocked("POST", "/prices", body = b))
        }
    }

    @Test
    fun otherHostsAreReadOnly() {
        assertTrue(ok("GET", "/api/games", host = "api.rawg.io"))
        assertTrue(ok("HEAD", "/x.jpg", host = "cdn.example.com"))
        assertTrue(blocked("PUT", "/u/abc", host = Routes.SYNC_HOST)) // friend sync never publishes
        assertTrue(blocked("POST", "/x", host = "example.com"))
    }

    @Test
    fun theHelperGetsTheWebAppsOrigin() {
        assertTrue(Routes.wantsOrigin(Routes.HELPER_HOST))
        assertTrue(Routes.wantsOrigin(Routes.SYNC_HOST))
        assertEquals(false, Routes.wantsOrigin("api.github.com"))
    }
}
