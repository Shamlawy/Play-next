package io.github.shamlawy.playnext.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Reviews on made-up games (index.html 20410-20556, android/parity/2-map/2-edit.md §7a): which games are watched and
 * when, RAWG's platform Metacritic, Steam's players through the helper, what no network leaves, the critic fill, and
 * Nexi's guess with its reasons (`rvFor`).
 */
class ReviewsTest {
    private val day = JsDate.DAY
    private val now = Instant.parse("2026-11-14T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private val lib = Library(Json.parseToJsonElement(LIB).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }
    private val rk = Rank(lib, now, utc)

    @After fun clean() { Reviews.forget() }

    @Test fun whichGamesAreWatched() {
        assertEquals(4, Reviews.age(g("new"), now, utc))
        assertTrue("out 4 days ago", Reviews.want(lib, rk, g("new"), now, utc))
        assertTrue("out in 2 days", Reviews.want(lib, rk, g("soon"), now, utc))
        assertFalse("out a year ago", Reviews.want(lib, rk, g("old"), now, utc))
        assertFalse("played", Reviews.want(lib, rk, g("p1"), now, utc))
        assertFalse("TBA", Reviews.want(lib, rk, g("tba"), now, utc))
        assertTrue("PC", Reviews.steamOk(g("new")))
        assertFalse("Switch only", Reviews.steamOk(g("soon")))
        assertTrue("no platforms", Reviews.steamOk(g("old")))
        assertTrue("never checked", Reviews.due(g("new"), now, utc))
        assertEquals("3 days ago", Reviews.ago(now - 3 * day, now))
        assertEquals("2 weeks ago", Reviews.ago(now - 15 * day, now))
        assertEquals("yesterday", Reviews.ago(now - day - 1, now))
    }

    @Test fun rawgPicksYourPlatformsMetacritic() {
        val d = Json.parseToJsonElement("""{"metacritic":80,"metacritic_platforms":[{"metascore":77,"platform":{"name":"Xbox Series S/X"}},{"metascore":84,"platform":{"name":"PC"}}],"rating":4.15,"ratings_count":600}""").jsonObject
        val rv = LinkedHashMap<String, JsonElement>()
        Reviews.rawgInto(g("new"), d, rv)
        assertEquals(JsonPrimitive(84), rv["mc"])
        assertEquals(JsonPrimitive("PC"), rv["mcP"])
        assertEquals(JsonPrimitive(8.3), rv["us"])
        val rv2 = LinkedHashMap<String, JsonElement>()
        Reviews.rawgInto(g("soon"), d, rv2)
        assertEquals("no match: RAWG's own", JsonPrimitive(80), rv2["mc"])
    }

    @Test fun steamsPlayersThroughTheHelper() = runBlocking {
        var filled: Map<String, JsonElement>? = null
        var asked = ""
        Reviews.post = { b -> asked = b; """{"ok":true,"res":{"new":{"st":4410,"d":"Very Positive","p":91,"n":1234}}}""" }
        Reviews.fetch(lib, g("new"), "", "ae", { now }) { filled = it }
        assertTrue(asked.contains("\"cc\":\"ae\"") && asked.contains("\"st\":0"))
        val rv = Reviews.of(g("new"))!!
        assertEquals(now, (rv["at"] as JsonPrimitive).content.toLong())
        assertEquals(4410L, Reviews.stId(g("new")))
        assertNull("no critic score: nothing filled", filled)
        val f = Queue(lib, rk, now, utc).rvFor(g("new"))!!
        assertEquals(91.0, Backup.num(f.st!!["p"], 0.0, 0.0, 100.0), 0.0)
        assertEquals("a rough guess", f.sure)
        assertTrue(f.why.toString(), f.why[0].startsWith("you usually score") || f.why[0] == "you usually agree with the critics")
    }

    @Test fun noNetworkRecordsNothingAndACriticScoreFillsAnEmptyOne() = runBlocking {
        Reviews.post = { throw java.io.IOException("offline") }
        Reviews.get = { throw java.io.IOException("offline") }
        Reviews.fetch(lib, g("new"), "key", "us", { now }) {}
        assertNull(Reviews.of(g("new")))
        var filled: Map<String, JsonElement>? = null
        Reviews.post = { """{"ok":true,"res":{"new":{"none":1}}}""" }
        Reviews.get = { url -> 200 to if (url.contains("search=")) """{"results":[{"id":31,"name":"Moonwell"}]}""" else """{"metacritic":82}""" }
        Reviews.fetch(lib, g("new"), "key", "us", { now }) { filled = it }
        assertEquals(JsonPrimitive(82), filled!!["critic"])
        assertEquals(JsonPrimitive("RAWG"), filled!!["criticFrom"])
        assertFalse("not on Steam now", Reviews.steamOk(g("new")))
    }

    @Test fun nexisGuessSaysWhy() {
        val f = Queue(lib, rk, now, utc).rvFor(g("old"))!!
        assertEquals(70.0, f.mc, 0.0)
        assertEquals("(9-7, 8-7.5, 8-6: 1.5; Action 2) x 3/6", "you usually score 0.9 above the critics on games like this", f.why[0])
        assertTrue(f.why.toString(), f.why.any { it.startsWith("it shares a genre with your #") })
        assertNull("played: none", Queue(lib, rk, now, utc).rvFor(g("p1")))
    }

    companion object {
        const val LIB = """{"games":[
            {"id":"new","title":"Moonwell","state":"backlog","date":"2026-11-10","platforms":"PC","genres":"Puzzle"},
            {"id":"soon","title":"Glass Road","state":"upcoming","date":"2026-11-16","platforms":"Switch"},
            {"id":"old","title":"Lantern Bay","state":"backlog","date":"2025-11-01","critic":70,"genres":"Action"},
            {"id":"tba","title":"Ash Garden","state":"upcoming","date":"2026-11-12","status":"tba"},
            {"id":"p1","title":"Tin Crown","state":"played","date":"2024-01-01","critic":70,"genres":"Action"},
            {"id":"p2","title":"Iron Tide","state":"played","date":"2024-01-01","critic":75,"genres":"RPG"},
            {"id":"p3","title":"Fallen Kite","state":"played","date":"2024-01-01","critic":60,"genres":"Action"}
        ],"played":[{"id":"p1","real":9,"t":"2024-02-01"},{"id":"p2","real":8,"t":"2024-02-01"},{"id":"p3","real":8,"t":"2024-02-01"}]}"""
    }
}
