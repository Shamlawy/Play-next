package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Score it's rules (`qScore` / `qScoreSet`, index.html 13142-13200; android/parity/2-map/1-page.md §5.1) on made-up
 * games: the buttons' words, the sheet's small line, scoring a game you hadn't marked played marks it (with Then & now's
 * snapshot, taken once), Don't remember and a real 0, and the first real score kept in the snapshot as `got`.
 */
class ScoreTest {
    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val lib = Library(Json.parseToJsonElement(SLIB).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }
    private val none: (String) -> JsonElement? = { null }
    private fun rk() = Rank(lib, now, ZoneId.of("UTC"))

    private fun fields(out: Map<String, JsonElement>, id: String) = (out[Practice.GAMES] as JsonObject)[id] as JsonObject
    private fun rec(out: Map<String, JsonElement>, id: String) = (out[Practice.RECS] as JsonObject)[id] as JsonObject

    @Test fun buttonsSayWholePointsAndHalves() {
        assertEquals(11, Score.WHOLE.size)
        assertEquals(10, Score.HALVES.size)
        assertEquals("7", Score.label(7.0))
        assertEquals("7½", Score.label(7.5))
        assertEquals("0", Score.label(0.0))
        assertEquals("the web's replace keeps the 0", "0½", Score.label(0.5))
    }

    @Test fun theSmallLineSaysWhereTheGameStands() {
        val r = rk()
        assertEquals("Scoring it marks it played · hype was 7", Score.line(r, g("a")))
        assertEquals("#1 · tap to change · hype was 8", Score.line(r, g("b")))
        assertEquals("Unscored (you don't remember)", Score.line(r, g("d")))
        assertEquals(8.5, Score.current(r, g("b"))!!, 0.0)
        assertNull("Don't remember has no score", Score.current(r, g("d")))
    }

    @Test fun scoringAnUnplayedGameMarksItPlayedWithItsSnapshot() {
        val out = Score.set(lib, g("a"), 8.0, now, none)
        val f = fields(out, "a")
        assertEquals(JsonPrimitive("played"), f["state"])
        val cap = f["cap"] as JsonObject
        assertEquals(JsonPrimitive(7), cap["h"])
        assertEquals(JsonPrimitive(now), cap["at"])
        assertEquals("the first Where was I note rides along", JsonPrimitive("Just met the ferryman"), (cap["n1"] as JsonObject)["x"])
        val r = rec(out, "a")
        assertEquals(JsonPrimitive(8), r["real"])
        assertEquals("dated by UTC, as syncPlayed", JsonPrimitive("2026-10-27"), r["t"])
        assertFalse(r.containsKey("dk"))
        assertFalse(r.containsKey("z"))
    }

    @Test fun aSnapshotIsTakenOnlyOnce() {
        val f = linkedMapOf<String, JsonElement>()
        Score.capOnFinish(lib, g("c"), now, f)
        assertFalse("c already has one", f.containsKey("cap"))
        Score.capOnFinish(lib, g("a"), now, f)
        assertNotNull(f["cap"])
    }

    @Test fun dontRememberAndARealZero() {
        val dk = rec(Score.set(lib, g("b"), null, now, none), "b")
        assertEquals(JsonPrimitive(0), dk["real"])
        assertEquals(JsonPrimitive(1), dk["dk"])
        assertEquals("the record keeps its runs", JsonPrimitive(1), dk["runs"])
        assertEquals("and its date", JsonPrimitive("2025-06-20"), dk["t"])
        val zero = rec(Score.set(lib, g("d"), 0.0, now, none), "d")
        assertEquals(JsonPrimitive(0), zero["real"])
        assertEquals("a real 0 is a score (z)", JsonPrimitive(1), zero["z"])
        assertFalse(zero.containsKey("dk"))
        val back = rec(Score.set(lib, g("d"), 6.5, now, none), "d")
        assertFalse(back.containsKey("z"))
        assertEquals(JsonPrimitive(6.5), back["real"])
    }

    @Test fun anAlreadyPlayedGameWritesOnlyItsRecord() {
        val out = Score.set(lib, g("b"), 9.0, now, none)
        assertFalse("no game fields without a snapshot to fill", out.containsKey(Practice.GAMES))
        assertEquals(JsonPrimitive(9), rec(out, "b")["real"])
    }

    @Test fun theFirstRealScoreIsKeptInTheSnapshot() {
        val got = fields(Score.set(lib, g("c"), 7.5, now, none), "c")["cap"] as JsonObject
        assertEquals(JsonPrimitive(7.5), got["got"])
        assertEquals("the rest of the snapshot stays", JsonPrimitive(6), got["pr"])
        val c2 = Game(JsonObject(g("c").o + ("cap" to got)))
        val again = Score.set(lib, c2, 3.0, now, none)
        assertFalse("got is written once", again.containsKey(Practice.GAMES))
        val noGuess = Game(JsonObject(g("c").o + ("cap" to JsonObject(mapOf("pr" to JsonPrimitive(0))))))
        assertFalse("no guess, nothing to compare", Score.set(lib, noGuess, 5.0, now, none).containsKey(Practice.GAMES))
    }

    @Test fun dontRememberLeavesGotAlone() {
        assertFalse(Score.set(lib, g("c"), null, now, none).containsKey(Practice.GAMES))
    }

    @Test fun earlierPracticeWritesAreKept() {
        val cur = mapOf<String, JsonElement>(Practice.RECS to JsonObject(mapOf("x" to JsonPrimitive(1))))
        val out = Score.set(lib, g("b"), 4.0, now) { cur[it] }
        assertTrue((out[Practice.RECS] as JsonObject).containsKey("x"))
    }
}

private val SLIB = """
    {"rateMode":"simple","playStyle":"extras",
     "games":[
       {"id":"a","title":"Starfall Lantern","state":"playing","date":"2026-09-30","ord":1,"hype":7,"plAt":1760000000000,
        "wasI":[{"t":1761000000000,"x":"Just met the ferryman"},{"t":1761500000000,"x":"Second island"}]},
       {"id":"b","title":"Copper Meadow","state":"played","date":"2025-05-11","ord":2,"hours":12,"hype":8},
       {"id":"c","title":"Quiet Harbour","state":"played","date":"2026-01-02","ord":3,"cap":{"h":5,"pr":6,"cr":0,"st":0,"ad":"","hrs":0,"n1":null,"at":1761000000000}},
       {"id":"d","title":"Glass Orchard","state":"played","date":"2024-03-01","ord":4}
     ],
     "duels":[],"played":[{"id":"b","real":8.5,"t":"2025-06-20","runs":1},{"id":"c","real":0,"t":"2026-02-01"},{"id":"d","real":0,"dk":1,"t":"2024-04-01"}]}
""".trimIndent()
