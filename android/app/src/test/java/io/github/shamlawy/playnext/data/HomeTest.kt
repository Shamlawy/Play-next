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
    // ------------------------------------------------------------ the middle tiles (piece 3)

    @Test fun playingNowTakesPlayingThenWaitingThenRanked() {
        val h = home(five)
        assertEquals("Playing now", h.playingTitle)
        // c is played now; a and e are in the backlog (out and waiting); then the rest of ranked
        assertEquals(listOf("c", "a", "e"), h.playing.map { it.id })
        val none = home("""{"id":"x","title":"Kite Run","state":"upcoming","hype":3},{"id":"y","title":"Moth Bell","state":"backlog"}""")
        assertEquals("Out & waiting", none.playingTitle)
        assertEquals(listOf("y", "x"), none.playing.map { it.id })
        assertEquals("Top of your list", home("""{"id":"x","title":"Kite Run","state":"upcoming"}""").playingTitle)
    }

    @Test fun cardWordsForEveryKindOfGame() {
        val h = home(
            five + """,{"id":"g","title":"Pale Orbit","state":"playing","hours":20,"logged":5},
            {"id":"k","title":"Iron Arena","state":"playing","comp":true,"diary":[{"w":3,"l":1},{"d":1}]},
            {"id":"m","title":"Glass Tide","state":"backlog","comp":true,"diary":[{"t":1}]},
            {"id":"n","title":"Fresh Paint","state":"backlog","date":"2026-11-01","hype":5,"hours":8,"logged":10}""",
        )
        val g = { id: String -> h.card(h.live.first { it.id == id }) }
        // playing with hours: how far through, a bar, hours left
        assertEquals("Playing · 25%", g("g").kicker)
        assertEquals(.25f, g("g").bar!!, 1e-6f)
        assertEquals("15h left", g("g").small)
        assertEquals("25%", g("g").pctText)
        // playing without hours: no bar, the genre as its small line
        assertEquals("Playing · 0%", g("c").kicker)
        // competitive: no bar, the win rate, then the diary count
        assertEquals("Competitive", g("k").kicker)
        assertNull(g("k").bar)
        assertEquals("60% wins", g("k").small)
        assertEquals("1 diary entry", g("m").small)
        // not playing: the genre, else "Out now" when fresh, else "Up next"
        assertEquals("RPG", g("a").kicker)
        assertEquals("Out now", g("n").kicker)
        assertEquals("Up next", g("d").kicker)
        // past its hours: 0h left, 100%; hype 5 is 3 stars (rounded half up), 10 is 5
        assertEquals("0h left", g("n").small)
        assertEquals(1f, g("n").pct, 0f)
        assertEquals(3, g("n").stars)
        assertEquals(5, g("e").stars)
        // no hours: "—"
        assertEquals("—", g("d").pctText)
    }

    @Test fun theDonutsSlicesAndMiddle() {
        val h = home(
            """{"id":"a","title":"A","hours":40},{"id":"b","title":"B","hours":30},{"id":"c","title":"C","hours":20},
            {"id":"d","title":"D","hours":6},{"id":"e","title":"E","hours":4},{"id":"f","title":"F"}""",
        )
        assertEquals(listOf("A", "B", "C", "Other"), h.donut.map { it.label })
        assertEquals(listOf(.4, .3, .2, .1), h.donut.map { it.share })
        assertEquals(listOf(0, 1, 2, 3), h.donut.map { it.colour })
        assertEquals("100h", h.donutHours)
        // 100 hours at 4 an evening
        assertEquals("≈ 25 evenings", h.donutSmall)
        assertEquals("Queue hours", h.donutHead)
        val big = home("""{"id":"a","title":"A","hours":900},{"id":"b","title":"B","hours":700}""", extra = ""","playStyle":"extras","evening":12""")
        // extras: 1.5x each (1350 + 1050), the thousands with a comma, the style named
        assertEquals("2,400h", big.donutHours)
        assertEquals("≈ 200 evenings", big.donutSmall)
        assertTrue(big.donutHead.startsWith("Queue hours · "))
        val none = home("""{"id":"a","title":"A"}""")
        assertTrue(none.donut.isEmpty())
        assertEquals("—h", none.donutHours)
        assertEquals("add hours to games", none.donutSmall)
    }

    @Test fun theTicketCountsDownThenSaysItsOut() {
        val h = home(five)
        assertEquals("b", h.ticket?.id)
        assertTrue(h.ticketAhead)
        assertEquals("3 days to go · 10 Nov", h.ticketLine(h.ticket!!))
        val out = home("""{"id":"a","title":"A","date":"2026-10-01"},{"id":"b","title":"B","date":"2026-11-02"}""")
        // none ahead: the latest dated one, already out
        assertEquals("b", out.ticket?.id)
        assertTrue(!out.ticketAhead)
        assertEquals("It's out — go play it!", out.ticketLine(out.ticket!!))
        assertNull(home("""{"id":"a","title":"A"}""").ticket)
        // to the local midnight of 10 Nov in Dubai from noon on 7 Nov: 2 days 12 hours
        assertEquals(listOf(2, 12, 0, 0), Home.countdown("2026-11-10", now, zone).toList())
        assertEquals(listOf(2, 11, 58, 55), Home.countdown("2026-11-10", now + 65_000, zone).toList())
        assertEquals(listOf(0, 0, 0, 0), Home.countdown("2026-11-01", now, zone).toList())
    }

    @Test fun theSwitchesHideTiles() {
        val h = home(five, extra = ""","homeHide":["queue","soon",3]""")
        assertTrue(!h.on("queue"))
        assertTrue(!h.on("soon"))
        assertTrue(h.on("playing"))
        assertTrue(h.on("hours"))
        assertEquals(h.ranked.take(5).map { it.id }, h.queueCovers.map { it.id })
    }
}
