package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Home's numbers (`renderHome` 11250, `dxHome` 11115, `dxWire` 11222, the dice 11507, Nexi's line 13316 + 13747) on
 * made-up games, at noon in Dubai.
 */
class HomeTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = Instant.parse("2026-11-07T08:00:00Z").toEpochMilli()

    private fun home(games: String, played: String = "[]", duels: String = "[]", extra: String = ""): Home {
        val lib = Library(Json.parseToJsonElement("""{"games":[$games],"played":$played,"duels":$duels$extra}""").jsonObject)
        val rk = Rank(lib, now, zone)
        return Home(Queue(lib, rk, now, zone), now, zone)
    }

    private val five = """
        {"id":"a","title":"Ninefold Gate","state":"backlog","hype":6,"genres":"RPG","hours":30},
        {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-11-10","hype":9,"genres":"Action"},
        {"id":"c","title":"Quiet Harbour","state":"playing","hype":4,"genres":"Puzzle","hours":12.5},
        {"id":"d","title":"Starfall Lantern","state":"upcoming","date":"2027-02-03","hype":7},
        {"id":"e","title":"Ashen Duel","state":"backlog","comp":true,"hype":10},
        {"id":"f","title":"Old Lighthouse","state":"played","hype":8,"genres":"Puzzle"}"""

    @Test fun rankedFollowsOnDeckThenHype() {
        val h = home(five, played = """[{"id":"f","real":9}]""")
        // played and dropped games are out; competitive sits last (no On deck score: -1)
        assertEquals(listOf("a", "b", "c", "d", "e"), h.live.map { it.id })
        val r = h.ranked.map { it.id }
        assertEquals("e", r.last())
        // out now (backlog/playing) beats coming: c (playing + the puzzle like your favourite) and a lead b and d
        assertEquals(setOf("a", "c"), r.take(2).toSet())
        assertEquals(r.first(), h.pick?.id)
        assertEquals(r.drop(1).take(4), h.next.map { it.id })
    }

    @Test fun tiesGoToEloThenHype() {
        // the same On deck score: no genres, same platform rule, all coming and undated: hype decides
        val h = home(
            """{"id":"x","title":"Kite Run","hype":3},{"id":"y","title":"Moth Bell","hype":8},{"id":"z","title":"Reed Song","hype":5}""",
        )
        // hype is also in On deck's score, so it leads either way; the order is the web's
        assertEquals(listOf("y", "z", "x"), h.ranked.map { it.id })
    }

    @Test fun theDiceSkipsTheLastPickAndTakesTheTopThree() {
        val h = home(five)
        val r = h.ranked
        // roll 0 → the first not last; with the top game as the last pick the first choice is the second
        assertEquals(r[1].id, h.pickOne(r[0].id, 0.0)?.id)
        assertEquals(r[0].id, h.pickOne(null, 0.0)?.id)
        // .99 of three → the third
        assertEquals(r[2].id, h.pickOne(null, .99)?.id)
        // one game, which was the last pick: still that one (`|| pick`)
        val one = home("""{"id":"a","title":"Ninefold Gate","state":"backlog"}""")
        assertEquals("a", one.pickOne("a", .5)?.id)
        assertNull(home("").pickOne(null, .5))
    }

    @Test fun datedFutAndHours() {
        val h = home(five)
        assertEquals(listOf("b", "d"), h.dated.map { it.id })
        assertEquals(listOf("b", "d"), h.fut.map { it.id })
        // 30 + 12.5; a competitive game counts 0
        assertEquals(42.5, h.hours, 1e-9)
        val wk = home("""{"id":"a","title":"Ninefold Gate","date":"2026-11-07"},{"id":"b","title":"Copper Meadow","date":"2026-11-08"}""")
        // today's midnight has passed: only tomorrow's is still ahead
        assertEquals(listOf("b"), wk.fut.map { it.id })
    }

    @Test fun searchFindsEveryGameWhereTheTextComesFirst() {
        val h = home(five, played = """[{"id":"f","real":9}]""")
        assertEquals(emptyList<String>(), h.search("  ").map { it.id })
        // "ight" only in Old Lighthouse; "ar" in Starfall Lantern (at 2) before Quiet Harbour (at 7)
        assertEquals(listOf("f"), h.search("IGHT").map { it.id })
        assertEquals(listOf("d", "c"), h.search("ar").map { it.id })
        assertEquals("Played", h.found(h.search("light").first()))
        assertEquals("3 days away", h.found(h.search("copper").first()))
        assertEquals("", h.found(h.search("ninefold").first()))
        val many = home((1..9).joinToString(",") { """{"id":"g$it","title":"Game $it"}""" })
        assertEquals(6, many.search("game").size)
    }

    @Test fun rowWords() {
        val h = home(five)
        val g = { id: String -> h.live.first { it.id == id } }
        assertEquals("10 Nov", h.whenOf(g("b")))
        assertEquals("≈ 30h", h.whenOf(g("a")))
        assertEquals("≈ 12.5h", h.whenOf(g("c")))
        assertEquals("No date yet", h.whenOf(g("e")))
        val out = home("""{"id":"a","title":"Ninefold Gate","date":"2026-11-01"}""")
        assertEquals("Out now", out.whenOf(out.live[0]))
        assertEquals("RPG", h.genreOf(g("a")))
        // no genres: the state's word
        assertEquals("Upcoming", h.genreOf(g("d")))
    }

    @Test fun countsAndTheTop() {
        val h = home(five, played = """[{"id":"f","real":9},{"id":"f","real":8,"again":true}]""", duels = """[{"a":"a","b":"c","w":"a"},{"a":"a","b":"b","w":""}]""")
        assertEquals("every finish, replays too", 2, h.playedCount)
        assertEquals("duels with a winner", 1, h.settled)
        assertEquals(listOf("f"), h.top.map { it.id })
        assertEquals(9.0, h.score(h.top[0])!!, 1e-9)
    }

    @Test fun nexiSpeaksInHisVoice() {
        assertEquals(Nexi.Said("Sam, play ", " next!"), Nexi.homeLine("hype", "Sam"))
        assertEquals(Nexi.Said("Play ", " next!"), Nexi.homeLine("hype", ""))
        assertEquals(Nexi.Said("May I suggest ", " next, Sam."), Nexi.homeLine("butler", "Sam"))
        assertEquals(Nexi.Said("Next up, no pressure: ", "."), Nexi.homeLine("coach", "Sam"))
        assertEquals(Nexi.Said("Obviously it's ", " next. Try to keep up."), Nexi.homeLine("rival", ""))
        assertEquals(Nexi.Said("Your next quest, Sam: ", "."), Nexi.homeLine("bard", "Sam"))
        assertEquals(Nexi.Said("Best match for you next: ", "."), Nexi.homeLine("nerd", "Sam"))
        val lib = { p: String -> Library(Json.parseToJsonElement("""{"games":[],"nxPersona":"$p"}""").jsonObject) }
        assertEquals("bard", Nexi.persona(lib("bard")))
        assertEquals("an unknown voice is hype", "hype", Nexi.persona(lib("pirate")))
    }

    @Test fun eveningIsFourByDefault() {
        val lib = { e: String -> Library(Json.parseToJsonElement("""{"games":[]$e}""").jsonObject) }
        assertEquals(4.0, lib("").evening, 0.0)
        assertEquals(12.0, lib(""","evening":30""").evening, 0.0)
        assertEquals(1.0, lib(""","evening":0.2""").evening, 0.0)
        assertEquals(3.0, lib(""","evening":3""").evening, 0.0)
        // the card back keeps its own 2
        val q = lib("").let { Queue(it, Rank(it, now, zone), now, zone) }
        assertTrue(q.evening == 2.0)
    }
}
