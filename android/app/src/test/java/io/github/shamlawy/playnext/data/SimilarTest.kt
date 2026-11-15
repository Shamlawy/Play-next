package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.net.Routes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln

/**
 * Similar games on made-up games (index.html 13460-13590, 20139-20170, 22239; android/parity/2-map/2-edit.md §7e-7g):
 * which tags count, how alike two games are, the Queue's "Like" tab, whether two names are the same game, and Steam
 * players' "More like this" (what's asked, the cards' words, the backup's copy, a helper from before it).
 */
class SimilarTest {
    private val now = Instant.parse("2026-11-15T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private fun lib(extra: String = "") = Library(Json.parseToJsonElement(LIB.replace("\"games\":", "$extra\"games\":")).jsonObject)
    private val lib = lib()
    private fun g(l: Library, id: String) = l.games.first { it.id == id }
    private fun queue(l: Library) = Queue(l, Rank(l, now, utc), now, utc)

    @After fun clean() { Similar.forget() }

    @Test fun genresWhatGotYouAndSteamsTagsButNotTheGenericOnes() {
        assertEquals(setOf("g:metroidvania", "g:action", "g:souls-like"), Similar.tags(g(lib, "a")))
        assertEquals("a broad Steam tag still counts", setOf("g:racing", "w:story", "g:open world"), Similar.tags(g(lib, "e")))
    }

    @Test fun aRareSharedTagCountsForMoreThanACommonOne() {
        val hits = queue(lib).sim.like(g(lib, "a"), 8)
        // Metroidvania (3 games) and Action (2) shared, Souls-like (1) not: the weights are ln((N+1)/(df+.5))
        val w = { df: Int -> ln(7.0 / (df + .5)) }  // six games
        assertEquals(listOf("b"), hits.map { it.g.id })
        assertEquals((w(3) + w(2)) / (w(3) + w(2) + w(1)), hits[0].s, 1e-9)
        assertEquals(53, Math.round(hits[0].s * 100).toInt())
        assertTrue("sharing only Metroidvania is under .22", hits.none { it.g.id == "c" })
        assertEquals("no tags, no look-alikes", emptyList<String>(), queue(lib).sim.like(g(lib, "x"), 8).map { it.g.id })
    }

    @Test fun theQueuesLikeTab() {
        val q = queue(lib)
        assertEquals(listOf("b"), q.list("S:a").map { it.id })
        assertEquals(1, q.count("S:a"))
        assertEquals("Like Moonwell", q.tabName("S:a"))
        assertEquals("S:a", q.tabNow("S:a"))
        assertEquals("a game that's gone: All", "all", q.tabNow("S:gone"))
    }

    @Test fun numbersDecideWhichGameANameIs() {
        assertEquals(1.0, Similar.same("Star Rift 3", "Star Rift III"), 0.0)
        assertEquals(.3, Similar.same("Star Rift", "Star Rift 3"), 0.0)
        assertEquals("one holds the other", .85, Similar.same("The Quiet Archive", "Quiet Archive"), 0.0)
        assertEquals("a lone I stays a word", 1.0, Similar.same("I Am Lantern", "i am lantern"), 0.0)
        assertEquals("b", Similar.have(lib, "glass road")?.id)
        assertNull(Similar.have(lib, "Glass Road 2"))
        assertTrue("you said not for me on the web", Similar.nope(lib("\"simNo\":{\"northbound\":1},"), "Northbound"))
    }

    @Test fun steamPlayersAlsoLike() = runBlocking {
        var asked = ""
        Similar.post = { b -> asked = b; ANSWER }
        Similar.more(lib, g(lib, "a"), "ae", now)
        val b = Json.parseToJsonElement(asked).jsonObject
        assertEquals("ae", (b["cc"] as JsonPrimitive).content)
        assertEquals(0, (b["items"] as JsonArray).size)
        assertEquals("7001", (b["more"] as JsonPrimitive).content)
        assertEquals(Routes.Allow, Routes.check("POST", Routes.HELPER_HOST, "/steam", asked))
        val l = Similar.cached(lib, g(lib, "a"), now)!!
        assertEquals(listOf("Harbour Lights", "Tactics of the Tide", "Northbound"), l.map { it.name })
        val c = Similar.card(l[1], g(lib, "a"))
        assertEquals("Steam players who like Moonwell love this · both Souls-like", c.why)
        assertEquals("Souls-like, Strategy", c.meta)
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/500001/capsule_616x353.jpg", c.img)
        assertEquals("Steam players also like it", Similar.card(l[2], g(lib, "a")).why)
        // asked once: a second open uses the answer
        asked = ""
        Similar.more(lib, g(lib, "a"), "ae", now)
        assertEquals("", asked)
        // not on Steam: nothing to ask
        Similar.more(lib, g(lib, "b"), "ae", now)
        assertEquals("", asked)
    }

    @Test fun theBackupsCopyAndAnOldHelper() = runBlocking {
        val kept = lib("\"stMore\":{\"a\":{\"id\":7001,\"t\":${now - 86_400_000L},\"l\":[{\"id\":9,\"name\":\"Glasswing\",\"tags\":[\"Metroidvania\"]}]}},")
        assertEquals(listOf("Glasswing"), Similar.cached(kept, g(kept, "a"), now)!!.map { it.name })
        assertNull("14 days on, asked again", Similar.cached(kept, g(kept, "a"), now + 14 * 86_400_000L))
        Similar.post = { throw Recap.HelperError("helper-old") }
        Similar.more(lib, g(lib, "a"), "ae", now)
        assertEquals("helper-old", Similar.err["a"])
        assertNull(Similar.cached(lib, g(lib, "a"), now))
    }

    /** The web keeps only a non-empty list: an empty answer holds for 10 minutes, then Steam is asked again; a new ask clears the last failure. */
    @Test fun anEmptyAnswerIsAskedAgainLater() = runBlocking {
        var calls = 0
        Similar.post = { calls++; throw java.io.IOException("offline") }
        Similar.more(lib, g(lib, "a"), "ae", now)
        assertTrue(Similar.err["a"].orEmpty().isNotEmpty())
        Similar.post = { calls++; """{"ok":true,"more":{"games":[]}}""" }
        Similar.more(lib, g(lib, "a"), "ae", now)
        assertNull("the old failure is gone", Similar.err["a"])
        assertEquals(emptyList<String>(), Similar.cached(lib, g(lib, "a"), now + 60_000)!!.map { it.name })
        Similar.more(lib, g(lib, "a"), "ae", now + 60_000)
        assertEquals("not asked again a minute on", 2, calls)
        assertNull(Similar.cached(lib, g(lib, "a"), now + 10 * 60_000))
        Similar.more(lib, g(lib, "a"), "ae", now + 10 * 60_000)
        assertEquals("asked again ten minutes on", 3, calls)
    }

    companion object {
        val LIB = """{"games":[
            {"id":"a","title":"Moonwell","state":"backlog","genres":["Metroidvania","Action"],"stId":7001,"stTags":["Metroidvania","Souls-like","Singleplayer","2D"]},
            {"id":"b","title":"Glass Road","state":"backlog","genres":"Metroidvania, Action"},
            {"id":"c","title":"Pocket Comet","state":"played","genres":["Metroidvania","Puzzle"]},
            {"id":"d","title":"Lantern Bay","state":"upcoming","genres":["Racing"]},
            {"id":"e","title":"Harbour Lights","state":"backlog","genres":["Racing"],"why":["story"],"stTags":["Open World","Singleplayer"]},
            {"id":"x","title":"Tin Crown","state":"backlog"}
        ],"played":[{"id":"c","real":7,"t":"2024-02-01"}]}"""

        val ANSWER = """{"ok":true,"res":{},"more":{"of":{"name":"Moonwell","tags":[]},"games":[
            {"id":500000,"name":"Harbour Lights","tags":["Metroidvania","Exploration"],"desc":"A lighthouse."},
            {"id":500001,"name":"Tactics of the Tide","tags":["Souls-like","Strategy"]},
            {"id":500002,"name":"Northbound","tags":[]}]}}"""
    }
}
