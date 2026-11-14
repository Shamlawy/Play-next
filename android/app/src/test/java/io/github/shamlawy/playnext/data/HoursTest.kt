package io.github.shamlawy.playnext.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Hours to beat from HowLongToBeat on made-up games (index.html 23339-23510, android/parity/2-map/2-edit.md §7c): whose
 * number the hours are, when a game is due, the request, what an answer, a miss and an error leave, the fill that
 * follows HowLongToBeat and never touches typed numbers, and Keep mine.
 */
class HoursTest {
    private val day = JsDate.DAY
    private val now = Instant.parse("2026-11-14T08:00:00Z").toEpochMilli()
    private val lib = Library(Json.parseToJsonElement(LIB.replace("OLD", (now - 50 * day).toString()).replace("NEW", (now - 2 * day).toString())).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }

    @After fun clean() { Hours.forget() }

    @Test fun whoseNumberTheHoursAre() {
        assertEquals("none", Hours.src(g("a")))
        assertEquals("me", Hours.src(g("b")))
        assertEquals("hltb", Hours.src(g("c")))
        assertEquals("ai", Hours.src(g("d")))
        assertEquals("old", Hours.src(g("e")))
        assertTrue(Hours.auto(g("a")))
        assertFalse("typed by you", Hours.auto(g("b")))
        assertTrue(Hours.auto(g("d")))
        assertFalse("from before v241: can't tell", Hours.auto(g("e")))
    }

    @Test fun whenAGameIsDue() {
        assertTrue("never asked", Hours.due(lib, g("a"), now))
        assertTrue("found 50 days ago, an older game: 45", Hours.due(lib, g("c"), now))
        assertFalse("found 2 days ago", Hours.due(lib, g("e"), now))
        assertFalse("competitive", Hours.due(lib, g("cp"), now))
        val off = Library(Json.parseToJsonElement(LIB.replace("\"games\"", "\"hbOff\":true,\"games\"")).jsonObject)
        assertFalse("switched off", Hours.due(off, off.games.first { it.id == "a" }, now))
    }

    @Test fun theRequestAndTheFormat() {
        val b = Json.parseToJsonElement(Hours.body(listOf(g("a")))).jsonObject
        assertEquals(JsonPrimitive("hltb"), b["src"])
        assertEquals("""[{"k":"a","t":"Moonwell","year":"2024","plat":"PC"}]""", b["items"].toString())
        assertEquals("9½", Hours.fmt(9.4))
        assertEquals("12", Hours.fmt(12.4))
        assertEquals("—", Hours.fmt(0.0))
        assertEquals("https://howlongtobeat.com/game/77", Hours.url(g("c")))
        assertEquals("https://howlongtobeat.com/?q=Moonwell", Hours.url(g("a")))
    }

    @Test fun anAnswerFillsEmptyHoursButNeverTypedOnes() = runBlocking {
        var filled: Map<String, JsonElement>? = null
        Hours.post = { """{"ok":true,"src":"hltb","res":{"a":{"id":5,"n":"Moonwell","m":11.5,"x":18,"c":30,"cnt":420}}}""" }
        Hours.fetch(lib, g("a"), { now }, force = false) { filled = it }
        val h = Hours.of(g("a"))!!
        assertEquals(11.5, h.m, 0.0)
        assertEquals(420L, h.cnt)
        assertEquals(JsonPrimitive(11.5), filled!!["hours"])
        assertEquals(JsonPrimitive(30), filled!!["hours100"])
        assertEquals(JsonPrimitive("hltb"), filled!!["hrsSrc"])
        filled = null
        Hours.post = { """{"ok":true,"src":"hltb","res":{"b":{"id":6,"n":"Glass Road","m":40,"x":50,"c":80,"cnt":10}}}""" }
        Hours.fetch(lib, g("b"), { now }, force = false) { filled = it }
        assertNull("typed by you: never filled", filled)
        assertEquals(40.0, Hours.of(g("b"))!!.m, 0.0)
        // Nexi's guess gives way, and his guess is dropped
        Hours.post = { """{"ok":true,"src":"hltb","res":{"d":{"id":7,"m":20}}}""" }
        Hours.fetch(lib, g("d"), { now }, force = false) { filled = it }
        assertEquals(JsonNull, filled!!["hbV"])
    }

    @Test fun aMissAnErrorAndAnOldHelper() = runBlocking {
        var calls = 0
        Hours.post = { calls++; throw java.io.IOException("offline") }
        Hours.fetch(lib, g("a"), { now }, force = false) {}
        assertNull("no network records nothing", Hours.of(g("a")))
        Hours.post = { calls++; """{"ok":true,"src":"hltb","res":{"a":{"none":1,"sure":0}}}""" }
        Hours.fetch(lib, g("a"), { now }, force = false) {}
        assertTrue(Hours.of(g("a"))!!.none)
        assertFalse("not found: again in two weeks", Hours.due(lib, g("a"), now + 13 * day))
        assertTrue(Hours.due(lib, g("a"), now + 15 * day))
        calls = 0
        Hours.post = { calls++; """{"ok":true,"src":"hltb","res":{"b":{"err":"busy"}}}""" }
        Hours.fetch(lib, g("b"), { now }, force = false) {}
        assertEquals("an error is asked once more", 2, calls)
        assertEquals("busy", Hours.of(g("b"))!!.err)
        assertTrue("an error: again after 6 hours", Hours.due(lib, g("b"), now + 7 * 3_600_000L))
        calls = 0
        Hours.post = { calls++; """{"ok":true,"res":{"e":{"m":3}}}""" }
        Hours.fetch(lib, g("e"), { now }, force = false) {}
        assertEquals("before v17: guesses, not used", 40.0, Hours.of(g("e"))!!.m, 0.0)
        Hours.fetch(lib, g("e"), { now + 60_000 }, force = false) {}
        assertEquals("an hour's wait after an old helper", 1, calls)
        Hours.fetch(lib, g("e"), { now + 60_000 }, force = true) {}
        assertEquals("the sheet asks anyway", 2, calls)
    }

    @Test fun yoursAgainstHowLongToBeats() {
        val e = g("e")
        val h = Hours.of(e)!!
        assertFalse(Hours.same(e, h))
        assertTrue(Hours.canKeep(e, h))
        val k = Hours.keep(h)
        assertEquals(JsonPrimitive(40), k["hlKeep"])
        assertEquals(JsonPrimitive("me"), k["hrsSrc"])
        assertTrue(Hours.same(g("c"), Hours.of(g("c"))!!))
        assertFalse("nothing to change", Hours.moves(g("c"), Hours.of(g("c"))!!))
    }

    companion object {
        const val LIB = """{"games":[
            {"id":"a","title":"Moonwell","state":"backlog","date":"2024-03-01","platforms":"PC"},
            {"id":"b","title":"Glass Road","state":"backlog","date":"2023-05-01","hours":25,"hrsSrc":"me"},
            {"id":"c","title":"Lantern Bay","state":"backlog","date":"2022-01-10","hours":12,"hoursX":20,"hours100":0,"hrsSrc":"hltb","hl":{"id":77,"n":"Lantern Bay","m":12,"x":20,"c":0,"cnt":90,"at":OLD}},
            {"id":"d","title":"Ash Garden","state":"backlog","date":"2021-02-02","hours":14,"hbV":{"m":14}},
            {"id":"e","title":"Tin Crown","state":"backlog","date":"2020-06-06","hours":20,"hl":{"id":9,"m":40,"x":55,"c":90,"cnt":30,"at":NEW}},
            {"id":"cp","title":"Arena Nine","state":"backlog","comp":true}
        ],"played":[]}"""
    }
}
