package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Where was I's rules (v212, `wi*`, index.html 19254-19366; android/parity/2-map/1-page.md §5.3) on made-up notes: how
 * long ago reads, the card's welcome back after two days, the quick chips, the clean-up, the oldest dropped past
 * WI_MAX, a delete, and notes the web never shows skipped.
 */
class NotesTest {
    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val none: (String) -> JsonElement? = { null }
    private fun game(vararg notes: Pair<Any?, String>) = Game(
        JsonObject(
            mapOf(
                "id" to JsonPrimitive("a"), "title" to JsonPrimitive("Starfall Lantern"), "state" to JsonPrimitive("playing"),
                "wasI" to JsonArray(notes.map { (t, x) -> JsonObject(mapOf("t" to (t?.let { JsonPrimitive(it as Number) } ?: JsonPrimitive("")), "x" to JsonPrimitive(x))) }),
            ),
        ),
    )
    private fun notes(out: Map<String, JsonElement>) = ((out[Practice.GAMES] as JsonObject)["a"] as JsonObject)["wasI"] as JsonArray

    @Test fun agoReadsLikeTheWeb() {
        val day = JsDate.DAY
        assertEquals("today", Notes.ago(now - 3_600_000, now))
        assertEquals("a note from the future is today", "today", Notes.ago(now + 60_000, now))
        assertEquals("yesterday", Notes.ago(now - day, now))
        assertEquals("13 days ago", Notes.ago(now - 13 * day, now))
        assertEquals("2 weeks ago", Notes.ago(now - 14 * day, now))
        assertEquals("8 weeks ago", Notes.ago(now - 59 * day, now))
        assertEquals("2 months ago", Notes.ago(now - 60 * day, now))
        assertEquals("12 months ago", Notes.ago(now - 365 * day, now))
    }

    @Test fun afterTwoDaysTheCardWelcomesYouBack() {
        val fresh = Notes.Note(now - JsDate.DAY, "x", 0)
        assertFalse(Notes.back(fresh, now))
        assertEquals("Where you left off · yesterday", Notes.head(fresh, now))
        val old = Notes.Note(now - 3 * JsDate.DAY, "x", 0)
        assertTrue(Notes.back(old, now))
        assertEquals("Welcome back · last time, 3 days ago", Notes.head(old, now))
    }

    @Test fun theNewestNoteIsLastAndUnreadableOnesAreSkipped() {
        val g = game(1761000000000L to "first", null to "no time", 1761500000000L to "", 1761600000000L to "newest")
        assertEquals(listOf("first", "newest"), Notes.of(g).map { it.x })
        assertEquals("its place in wasI, for the delete", 3, Notes.last(g)!!.at)
        assertNull(Notes.last(game()))
    }

    @Test fun quickChipsFillOrFollow() {
        assertEquals("Boss fight", Notes.chip("  ", "Boss fight"))
        assertEquals("At the castle, boss fight", Notes.chip("At the castle.", "Boss fight"))
        assertEquals("At the castle, boss fight", Notes.chip("At the castle ", "Boss fight"))
        assertTrue("the web's chips are plain words", Web.WI_QUICK.isNotEmpty())
    }

    @Test fun aNoteIsCleanedAndAdded() {
        val out = Notes.add(game(1761000000000L to "first"), "  Second\n\n  island   now ", now, none)
        val all = notes(out)
        assertEquals(2, all.size)
        assertEquals(JsonPrimitive("Second island now"), (all[1] as JsonObject)["x"])
        assertEquals(JsonPrimitive(now), (all[1] as JsonObject)["t"])
        assertEquals(Notes.LONGEST, Notes.clean("x".repeat(400)).length)
        assertEquals("blank words write nothing", emptyMap<String, JsonElement>(), Notes.add(game(), " \n ", now, none))
    }

    @Test fun theOldestGoesPastTheMost() {
        val full = game(*Array(Web.WI_MAX) { (1761000000000L + it) to "n$it" })
        val all = notes(Notes.add(full, "newest", now, none))
        assertEquals(Web.WI_MAX, all.size)
        assertEquals(JsonPrimitive("n1"), (all.first() as JsonObject)["x"])
        assertEquals(JsonPrimitive("newest"), (all.last() as JsonObject)["x"])
    }

    @Test fun aQuickSecondTapSeesTheFirstOnesWrite() {
        // the library is rebuilt off the main thread, so a second delete can still be handed the old game
        val g = game(1761000000000L to "n0", 1761500000000L to "n1", 1761600000000L to "n2", 1761700000000L to "n3")
        val first = Notes.drop(g, 3, none)
        val second = Notes.drop(g, 2) { first[it] }
        assertEquals("the note deleted first stays gone", listOf("n0", "n1"), notes(second).map { ((it as JsonObject)["x"] as JsonPrimitive).content })
        val added = Notes.add(g, "n4", now) { first[it] }
        assertEquals(listOf("n0", "n1", "n2", "n4"), notes(added).map { ((it as JsonObject)["x"] as JsonPrimitive).content })
    }

    @Test fun aDeleteTakesThatNoteOnly() {
        val g = game(1761000000000L to "first", 1761500000000L to "second", 1761600000000L to "third")
        assertEquals(listOf("first", "third"), notes(Notes.drop(g, 1, none)).map { ((it as JsonObject)["x"] as JsonPrimitive).content })
        assertEquals("a place that isn't there writes nothing", emptyMap<String, JsonElement>(), Notes.drop(g, 7, none))
    }
}
