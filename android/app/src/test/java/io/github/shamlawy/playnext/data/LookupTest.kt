package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Edit › Critics › Pull score, date, tags and blurb (Phase 2 piece 2b, 2-edit.md §6) as `rawgLookup` / `applyLookup`
 * do it: only a game whose name matches (and isn't an older one for a game you have coming) is used, unless you say
 * use it anyway; tags merge yours first, at most 8; the blurb and backdrop only fill a game that has none; nothing
 * reaches the game until Save. Made-up games.
 */
class LookupTest {
    private val lib = Library(Json.parseToJsonElement(
        """{"games":[
          {"id":"a","title":"Starfall Lantern","state":"upcoming","date":"2027-03-01","genres":"Action, RPG","ord":1},
          {"id":"b","title":"Copper Meadow","state":"backlog","about":"Mine already.","bg":"https://mine/bg.jpg","ord":2,"rawgOk":true}
        ],"duels":[],"played":[]}""",
    ).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }
    private val today = LocalDate.parse("2026-11-12")

    private fun rawg(name: String, released: String = "2027-03-12") = Json.parseToJsonElement(
        """{"id":4242,"name":"$name","metacritic":82,"released":"$released","background_image":"https://rawg/bg.jpg","description_raw":"A lantern keeper walks the night.",
          "rating":4.31,"ratings_count":1200,"tags":[{"name":"Singleplayer","language":"eng"},{"name":"Open World","language":"eng"},{"name":"RPG","language":"eng"},{"name":"Atmospheric","language":"eng"},{"name":"Отличный","language":"rus"}]}""",
    ).jsonObject

    @Test fun titlesMatchByWordsOrNearly() {
        assertTrue(Lookup.titleMatch("Starfall Lantern", "starfall: lantern"))
        assertTrue(Lookup.titleMatch("Starfall Lanterns", "Starfall Lantern"))
        assertFalse(Lookup.titleMatch("Starfall Lantern", "Starfall Lantern II: Dusk"))
    }

    @Test fun anotherGameChangesNothingUntilYouSaySo() {
        val f = Lookup.read(rawg("Starfall Lantern II: Dusk"), g("a"), today) as Lookup.Found.Game
        assertFalse(f.sure)
        assertEquals("Nothing changed — it came back with “Starfall Lantern II: Dusk”, which isn't this game. If it really is, use it anyway and it'll remember the match.", Lookup.notSure(f))
        val old = Lookup.read(rawg("Starfall Lantern", "2019-05-01"), g("a"), today) as Lookup.Found.Game
        assertFalse("already out years ago, for a game you have coming", old.sure)
        assertEquals("“Starfall Lantern” is already out — that looks like an older game in the series", old.why)
        val (d, say) = Lookup.force(g("a"), Edit.open(lib, g("a")), f)
        assertEquals("use it anyway remembers the match", true, (d["rawgOk"] as JsonPrimitive).content.toBoolean())
        assertTrue(say.startsWith("Got "))
        assertTrue("a game you said yes to before is sure", (Lookup.read(rawg("Something else"), g("b"), today) as Lookup.Found.Game).sure)
    }

    @Test fun tagsMergeYoursFirstAtMostEight() {
        val tags = rawg("x")["tags"] as JsonArray
        assertEquals("Action, RPG, Open World, Dark", Lookup.mergeTags("Action, RPG", tags))
        val many = Json.parseToJsonElement("""[{"name":"Roguelite"},{"name":"Deckbuilding"},{"name":"Sci-fi"},{"name":"Co-op"},{"name":"Funny"},{"name":"Relaxing"},{"name":"Story Rich"},{"name":"Turn-Based Combat"}]""").jsonArray
        assertEquals(8, Lookup.mergeTags("Action, RPG", many).split(", ").size)
    }

    @Test fun pullFillsTheDraftNotTheGame() {
        val f = Lookup.read(rawg("Starfall Lantern"), g("a"), today) as Lookup.Found.Game
        assertTrue(f.sure)
        val (d, say) = Lookup.apply(g("a"), Edit.open(lib, g("a")), f)
        assertEquals("Got 2 tags and critic 82 and date 2027-03-12 — tap Save. RAWG users rate it 4.3/5 from 1200 votes.", say)
        assertEquals("82", d.typed("critic", ""))
        assertEquals("A lantern keeper walks the night.", d.typed("about", ""))
        assertEquals("https://rawg/bg.jpg", d.str("bg"))
        assertEquals("2027-03-12", d.str("date"))
        assertEquals("nothing is in the game yet", null, lib.games.first { it.id == "a" }["critic"])
        val out = (Edit.save(lib, g("a"), d, Instant.parse("2026-11-12T08:00:00Z").toEpochMilli()) { null }[Practice.GAMES] as JsonObject)["a"] as JsonObject
        assertEquals(JsonPrimitive(82), out["critic"])
        assertEquals(JsonPrimitive(4242), out["rawgId"])
        assertEquals(JsonPrimitive("https://rawg/bg.jpg"), out["bg"])
        assertEquals(JsonPrimitive("Action, RPG, Open World, Dark"), out["genres"])
    }

    @Test fun aBlurbOrBackdropOfYoursStays() {
        val f = Lookup.read(rawg("Copper Meadow", "2026-01-01"), g("b"), today) as Lookup.Found.Game
        val (d, _) = Lookup.apply(g("b"), Edit.open(lib, g("b")), f)
        assertEquals("Mine already.", d.typed("about", "Mine already."))
        assertEquals("https://mine/bg.jpg", d.str("bg"))
    }

    @Test fun nothingNewSaysSo() {
        val f = Lookup.Found.Game(JsonPrimitive(1), "Starfall Lantern", 0, "", JsonArray(emptyList()), "", "2027-03-01", 0.0, 0, true, "")
        assertEquals("No critic score yet and the date is unchanged.", Lookup.apply(g("a"), Edit.open(lib, g("a")), f).second)
    }
}
