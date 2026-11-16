package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.net.Routes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Similar's "Picked by Nexi" (`simFind`, index.html 13498; android/parity/2-map/2-edit.md §7e) on made-up games: what's
 * asked, how each name is checked on RAWG (numbers decide), the old helper's series, the backup's 30-day copy and a failure.
 */
class SimilarPicksTest {
    private val now = Instant.parse("2026-11-15T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private fun lib(extra: String = "") = Library(Json.parseToJsonElement(SimilarTest.LIB.replace("\"games\":", "$extra\"games\":")).jsonObject)
    private val lib = lib()
    private fun g(l: Library, id: String) = l.games.first { it.id == id }
    private fun rk(l: Library) = Rank(l, now, utc)
    private val rawgAsked = mutableListOf<String>()

    @After fun clean() { Similar.forget() }

    /** RAWG's answers by what's searched (and the series by RAWG id). */
    private fun rawg(results: Map<String, String>) {
        Similar.rawgGet = { url ->
            val u = url.toHttpUrl()
            rawgAsked += u.encodedPath + "?" + (u.queryParameter("search") ?: "")
            assertEquals("the key goes along", "k1", u.queryParameter("key"))
            val key = u.queryParameter("search") ?: u.encodedPath
            200 to (results[key] ?: """{"results":[]}""")
        }
    }

    private fun hit(id: Int, name: String, extra: String = "") = """{"id":$id,"name":"$name","background_image":"https://media.rawg.io/media/games/$id.jpg","released":"2021-03-04","genres":[{"name":"Action"},{"name":"RPG"},{"name":"Indie"}],"metacritic":84$extra}"""

    @Test fun whatsAskedAndEachNameCheckedOnRawg() = runBlocking {
        var body = ""
        Similar.ask = { b ->
            body = b
            """{"games":[
              {"name":"Star Rift 3","why":"Same tight jumps"},
              {"name":"Glass Road","why":"you have it"},
              {"name":"Moonwell","why":"the game itself"},
              {"name":"Cinder Vale","why":"Moody caves"},
              {"name":"Paper Atlas","why":"Made up by the model"},
              {"name":"Cinder Vale Remastered","why":"the same RAWG game again"}]}"""
        }
        rawg(
            mapOf(
                // RAWG has the first game, its sequel and the third one: numbers decide (III = 3)
                "Star Rift 3" to """{"results":[${hit(1, "Star Rift")},${hit(3, "Star Rift III")},${hit(2, "Star Rift 2")}]}""",
                "Cinder Vale" to """{"results":[${hit(7, "Cinder Vale")}]}""",
                "Paper Atlas" to """{"results":[${hit(9, "Atlas of Rivers")}]}""",
                "Cinder Vale Remastered" to """{"results":[${hit(7, "Cinder Vale")}]}""",
            ),
        )
        Similar.find(lib, rk(lib), g(lib, "a"), "k1", now)
        val b = Json.parseToJsonElement(body).jsonObject
        assertEquals("Moonwell", (b["title"] as JsonPrimitive).content)
        assertEquals("Metroidvania, Action", (b["genres"] as JsonPrimitive).content)
        assertEquals(6, (b["exclude"] as JsonArray).size)
        assertTrue(b.containsKey("favourites") && b.containsKey("year"))
        assertEquals(Routes.Allow, Routes.check("POST", Routes.HELPER_HOST, "/similar", body))
        assertTrue("yours and the game itself are never looked up: $rawgAsked", rawgAsked.none { it.endsWith("Glass Road") || it.endsWith("Moonwell") })
        val f = Similar.found(lib, g(lib, "a"), now)!!
        assertEquals("ai", f.how)
        assertEquals("the number picks the game; a made-up name drops; one per RAWG game", listOf("Star Rift III", "Cinder Vale"), f.l.map { it.name })
        assertEquals(listOf("Same tight jumps", "Moody caves"), f.l.map { it.why })
        val c = Similar.card(f.l[0])
        assertEquals("2021 · Action, RPG · MC 84", c.meta)
        assertEquals("https://media.rawg.io/media/games/3.jpg", c.img)
        // a second open uses the answer
        body = ""
        Similar.find(lib, rk(lib), g(lib, "a"), "k1", now + 60_000)
        assertEquals("", body)
    }

    @Test fun withoutARawgKeyTheNamesComeWithoutPictures() = runBlocking {
        Similar.ask = { """{"games":[${(1..12).joinToString(",") { "{\"name\":\"Made Up $it\",\"why\":\"w$it\"}" }}]}""" }
        Similar.rawgGet = { throw AssertionError("RAWG isn't asked without a key") }
        Similar.find(lib, rk(lib), g(lib, "a"), "", now)
        val f = Similar.found(lib, g(lib, "a"), now)!!
        assertEquals("ten at most", 10, f.l.size)
        assertEquals("", Similar.card(f.l[0]).img)
        assertEquals("", Similar.card(f.l[0]).meta)
    }

    @Test fun anOldHelperGivesTheSameSeries() = runBlocking {
        Similar.ask = { throw Recap.HelperError("helper-old") }
        rawg(
            mapOf(
                "Moonwell" to """{"results":[{"id":40,"name":"Moonwell"}]}""",
                "/api/games/40/game-series" to """{"results":[${hit(41, "Moonwell II")},${hit(42, "Glass Road")}]}""",
            ),
        )
        Similar.find(lib, rk(lib), g(lib, "a"), "k1", now)
        val f = Similar.found(lib, g(lib, "a"), now)!!
        assertEquals("series", f.how)
        assertEquals("yours left out", listOf("Moonwell II"), f.l.map { it.name })
        assertEquals("Same series", f.l[0].why)
        // no network counts the same; without a key the series is empty
        Similar.forget()
        Similar.ask = { throw java.io.IOException("offline") }
        Similar.find(lib, rk(lib), g(lib, "a"), "", now)
        assertEquals("series", Similar.found(lib, g(lib, "a"), now)!!.how)
        assertEquals(0, Similar.found(lib, g(lib, "a"), now)!!.l.size)
    }

    @Test fun anotherFailureIsSaid() = runBlocking {
        Similar.ask = { throw Recap.HelperError("The helper said 500") }
        Similar.find(lib, rk(lib), g(lib, "a"), "k1", now)
        assertNull(Similar.found(lib, g(lib, "a"), now))
        assertEquals("The helper said 500", Similar.findErr["a"])
        // the next open asks again and clears it
        Similar.ask = { """{"games":[]}""" }
        Similar.find(lib, rk(lib), g(lib, "a"), "k1", now)
        assertNull(Similar.findErr["a"])
        assertEquals(0, Similar.found(lib, g(lib, "a"), now)!!.l.size)
    }

    @Test fun theBackupsCopyHolds30Days() = runBlocking {
        val kept = lib("\"simCache3\":{\"a\":{\"t\":${now - 86_400_000L},\"how\":\"ai\",\"l\":[${hit(5, "Old Pick", ",\"why\":\"kept\"")}]}},")
        var asked = 0
        Similar.ask = { asked++; """{"games":[]}""" }
        Similar.find(kept, rk(kept), g(kept, "a"), "k1", now)
        assertEquals("not asked while the copy is fresh", 0, asked)
        assertEquals(listOf("Old Pick"), Similar.found(kept, g(kept, "a"), now)!!.l.map { it.name })
        assertEquals("kept", Similar.found(kept, g(kept, "a"), now)!!.l[0].why)
        val later = now + 30 * 86_400_000L
        assertNull("30 days on, asked again", Similar.found(kept, g(kept, "a"), later))
        Similar.find(kept, rk(kept), g(kept, "a"), "k1", later)
        assertEquals(1, asked)
    }
}
