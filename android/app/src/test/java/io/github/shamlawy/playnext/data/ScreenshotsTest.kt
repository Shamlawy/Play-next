package io.github.shamlawy.playnext.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * A page's screenshots fetched by themselves (M5, `shotsFetch` v240 + `gpPickShots`, index.html 22393, 22754) on
 * made-up games: which store's shots win, five at most, RAWG's to fill up, when a game is asked again, and a failure
 * that is never taken for "this game has none".
 */
class ScreenshotsTest {
    private val now = Instant.parse("2026-11-15T08:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private fun lib(games: String) = Library(Json.parseToJsonElement("""{"games":[$games]}""").jsonObject)
    private fun one(game: String) = lib(game).let { it to it.games[0] }

    @After fun clean() { Screenshots.forget(); ArtNet.forget() }

    private fun pic(s: String, n: Int, r: String = "shot") = Art.Pic(s, r, "https://$s.example/$n.jpg", "https://$s.example/$n.jpg", 1920, 1080, "Screenshot")

    @Test fun theStoreWithThreeOrMoreWinsFiveAtMost() {
        val pics = (1..2).map { pic("st", it) } + (1..7).map { pic("ps", it) } + (1..4).map { pic("ns", it) } + pic("st", 9, "hero")
        val (s, l) = Screenshots.pick(pics, listOf("PS5", "PC"))
        assertEquals("Steam has only 2: PlayStation's 7, five of them", "ps", s)
        assertEquals(5, l.size)
        assertTrue(l.all { it.s == "ps" })
        // a game only on Nintendo machines asks Nintendo first
        assertEquals("ns", Screenshots.pick(pics, listOf("Switch", "Nintendo Switch 2")).first)
        // none with 3: all of them in the order, five at most, led by the first
        val few = listOf(pic("xb", 1), pic("ns", 1), pic("st", 1), pic("st", 2), pic("ps", 1), pic("ns", 2))
        val (s2, l2) = Screenshots.pick(few, listOf("PC"))
        assertEquals("st", s2)
        assertEquals(listOf("st", "st", "ps", "ns", "ns"), l2.map { it.s })
        assertEquals("", Screenshots.pick(emptyList(), emptyList()).first)
    }

    @Test fun phoneSizedCopies() {
        val ss = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1/ss_ab12.jpg?t=5"
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1/ss_ab12.1920x1080.jpg?t=5", Screenshots.big(ss))
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1/ss_ab12.600x338.jpg?t=5", Screenshots.thumb("", ss))
        assertEquals("https://image.api.playstation.com/x.png?w=1280&h=1280", Screenshots.big("https://image.api.playstation.com/x.png?a=1"))
        assertEquals("a store's own thumbnail wins", "t.jpg", Screenshots.thumb("t.jpg", ss))
    }

    @Test fun whenAGameIsAskedAgain() {
        val (_, has) = one("""{"id":"a","title":"Moonwell","shots":[{"u":"x"}],"shotsAt":${now - 59 * day}}""")
        assertFalse("shots under 60 days old", Screenshots.due(has, now))
        assertTrue("60 days on", Screenshots.due(has, now + 2 * day))
        val (_, none) = one("""{"id":"a","title":"Moonwell","shotsNo":1,"shotsNoV":240,"shotsAt":${now - 2 * day}}""")
        assertFalse("none, said 2 days ago", Screenshots.due(none, now))
        assertTrue("3 days on", Screenshots.due(none, now + day))
        val (_, oldNone) = one("""{"id":"a","title":"Moonwell","shotsNo":1,"shotsAt":${now - day}}""")
        assertTrue("a 'none' from before v240 doesn't count", Screenshots.due(oldNone, now))
        assertTrue("never asked", Screenshots.due(lib("""{"id":"b","title":"Glass Road"}""").games[0], now))
    }

    private fun stores(vararg pics: String, found: String = "") =
        Json.parseToJsonElement("""{${if (found.isNotEmpty()) "\"found\":$found," else ""}"pics":[${pics.joinToString(",")}]}""") as JsonObject

    private fun st(n: Int) = """{"s":"st","r":"shot","u":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1/ss_a$n.jpg","w":1920,"h":1080,"k":"Screenshot"}"""

    @Test fun storesThenRawgToFillUp() = runBlocking {
        val (l, g) = one("""{"id":"a","title":"Moonwell","platforms":"PC","stId":7001}""")
        var asked = ""
        Screenshots.stores = { t, sid, cc, pscc -> asked = "$t|$sid|$cc|$pscc"; stores(st(1), st(2)) }
        val rawg = mutableListOf<String>()
        Screenshots.rawgGet = { url ->
            val u = url.toHttpUrl()
            rawg += u.encodedPath
            if (u.queryParameter("search") != null) 200 to """{"results":[{"id":3,"name":"Moonwell 2"},{"id":4,"name":"Moonwell"}]}"""
            else 200 to """{"results":[{"image":"https://media.rawg.io/media/screenshots/r1.jpg"},{"image":""},{"image":"https://media.rawg.io/media/screenshots/r2.jpg"},{"image":"https://media.rawg.io/media/screenshots/r3.jpg"},{"image":"https://media.rawg.io/media/screenshots/r4.jpg"}]}"""
        }
        Screenshots.fetch(l, g, "k1", "ae", { now })
        assertEquals("Moonwell|7001|ae|ae", asked)
        assertEquals("RAWG's closest title (.85 alike), then its screenshots", listOf("/api/games", "/api/games/4/screenshots"), rawg)
        val shots = Page(Queue(l, Rank(l, now, ZoneId.of("UTC")), now, ZoneId.of("UTC")), now, ZoneId.of("UTC"), Locale.UK).shots(g)
        assertEquals("Steam's 2 and RAWG's 3: five", 5, shots.size)
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1/ss_a1.600x338.jpg" to "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1/ss_a1.1920x1080.jpg", shots[0])
        assertEquals("https://media.rawg.io/media/resize/640/-/screenshots/r1.jpg" to "https://media.rawg.io/media/resize/1280/-/screenshots/r1.jpg", shots[2])
        assertEquals("Steam and RAWG", Screenshots.src(g))
        assertFalse("answered: not asked again", Screenshots.due(g, now + day))
        // the next open, two days on, leaves it alone
        asked = ""
        Screenshots.fetch(l, g, "k1", "ae", { now + 2 * day })
        assertEquals("", asked)
    }

    @Test fun aFailureIsNeverNone() = runBlocking {
        val (l, g) = one("""{"id":"a","title":"Moonwell","platforms":"PC"}""")
        Screenshots.retryWait = 0
        var calls = 0
        Screenshots.stores = { _, _, _, _ -> calls++; stores(found = """{"st":{"err":"timeout"},"ps":{"err":"403"}}""") }
        Screenshots.rawgGet = { throw AssertionError("no RAWG key, no RAWG") }
        Screenshots.fetch(l, g, "", "us", { now })
        assertEquals("the stores are asked again once", 2, calls)
        assertEquals("every store failed", Screenshots.err["a"])
        assertTrue("still due", Screenshots.due(g, now))
        assertNull(Screenshots.of(g))
        // not asked again within the minute, then asked
        Screenshots.fetch(l, g, "", "us", { now + 30_000 })
        assertEquals(2, calls)
        // the stores answer, with no screenshots: "none" for 3 days
        Screenshots.stores = { _, _, _, _ -> calls++; stores(found = """{"st":{"name":"Moonwell"}}""") }
        Screenshots.fetch(l, g, "", "us", { now + 61_000 })
        assertEquals(3, calls)
        assertNull(Screenshots.err["a"])
        assertFalse("none: left alone for 3 days", Screenshots.due(g, now + 2 * day))
        assertTrue(Screenshots.due(g, now + 3 * day + 61_000))
    }
}
