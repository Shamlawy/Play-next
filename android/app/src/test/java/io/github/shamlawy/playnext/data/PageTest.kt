package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * A game's page, New style (Phase 2 piece 1, android/parity/2-map/1-page.md §2-4): the kicker, the four tiles, the one
 * smart button, Your progress and the tags, each branch as index.html words it; and the one practice copy (M7) every
 * screen reads. Made-up games, a fixed clock.
 */
class PageTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = Instant.parse("2026-11-10T09:00:00Z").toEpochMilli() // 1 PM in Dubai

    private fun lib(games: String, played: String = "", extra: String = ""): Library =
        Library(Json.parseToJsonElement("""{"games":[$games],"played":[$played],"duels":[]$extra}""").jsonObject)

    private fun page(l: Library) = Page(Queue(l, Rank(l, now, zone), now, zone), now, zone, Locale.UK)

    private fun one(g: String, played: String = "", extra: String = ""): Pair<Page, Game> = lib(g, played, extra).let { l -> page(l) to l.games[0] }

    @Test fun theKickerSaysWhenItComesOutOrHowItEnded() {
        one("""{"id":"a","title":"A","state":"upcoming","date":"2026-11-11"}""").let { (p, g) -> assertEquals("1 day away", p.kicker(g)) }
        one("""{"id":"a","title":"A","state":"upcoming","date":"2026-11-20"}""").let { (p, g) -> assertEquals("10 days away", p.kicker(g)) }
        // today's UTC midnight passed 9 hours ago: ceil(-.375) is -0, which JavaScript calls 0, so "Out today"
        one("""{"id":"a","title":"A","state":"backlog","date":"2026-11-10"}""").let { (p, g) -> assertEquals("Out today", p.kicker(g)) }
        one("""{"id":"a","title":"A","state":"backlog","date":"2026-11-09"}""").let { (p, g) -> assertEquals("Out now", p.kicker(g)) }
        one("""{"id":"a","title":"A","state":"backlog"}""").let { (p, g) -> assertEquals("No date yet", p.kicker(g)) }
        one("""{"id":"a","title":"A","state":"dropped","date":"2024-01-01"}""").let { (p, g) -> assertEquals("Dropped", p.kicker(g)) }
        one("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":8,"t":"2026-03-04"}""").let { (p, g) -> assertEquals("Finished · Mar 2026", p.kicker(g)) }
        one("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":8}""").let { (p, g) -> assertEquals("Finished", p.kicker(g)) }
    }

    @Test fun theFourTilesTakeEveryBranch() {
        one("""{"id":"a","title":"A","state":"upcoming"}""").let { (p, g) ->
            assertEquals(listOf("TBA", "—", "—", "—"), p.tiles(g).map { it.value })
            assertEquals(listOf("no date yet", "length", "hype", "not scored"), p.tiles(g).map { it.label })
            assertTrue(p.tiles(g)[1].none)
        }
        one("""{"id":"a","title":"A","state":"upcoming","date":"2026-12-03","hours":30,"hype":8}""").let { (p, g) ->
            assertEquals(listOf("3 Dec", "30h", "8/10"), p.tiles(g).take(3).map { it.value })
            assertEquals(listOf("release", "8 evenings", "hype"), p.tiles(g).take(3).map { it.label })
        }
        one("""{"id":"a","title":"A","state":"backlog","date":"2026-09-07","hours":2}""", extra = ""","evening":4""").let { (p, g) ->
            assertEquals("7 Sep", p.tiles(g)[0].value)
            assertEquals("released", p.tiles(g)[0].label)
            assertEquals("always 'evenings', at least one", "1 evenings", p.tiles(g)[1].label)
        }
        one("""{"id":"a","title":"A","state":"backlog","date":"2024-02-29"}""").let { (p, g) ->
            assertEquals("another year: the year big, the day under it", listOf("2024", "29 Feb"), p.tiles(g)[0].let { listOf(it.value, it.label) })
        }
        one("""{"id":"a","title":"A","state":"backlog","comp":true,"hours":50}""").let { (p, g) ->
            assertEquals(listOf("—", "no end"), p.tiles(g)[1].let { listOf(it.value, it.label) })
            assertEquals(listOf("—", "competitive"), p.tiles(g)[3].let { listOf(it.value, it.label) })
        }
        one("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":7.5,"t":"2026-01-02"}""").let { (p, g) ->
            assertEquals(listOf("7.5", "your score"), p.tiles(g)[3].let { listOf(it.value, it.label) })
        }
        one("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":0,"z":1}""").let { (p, g) ->
            assertEquals("a deliberate 0 is a score", "0", p.tiles(g)[3].value)
        }
        one("""{"id":"a","title":"A","state":"backlog","critic":84}""").let { (p, g) ->
            assertEquals(listOf("84", "critics"), p.tiles(g)[3].let { listOf(it.value, it.label) })
        }
        // Nexi's likely score: your hype against how your scores land (one played game 2 over its hype)
        val l = lib("""{"id":"a","title":"A","state":"backlog","hype":6},{"id":"b","title":"B","state":"played","hype":5}""", """{"id":"b","real":7,"t":"2026-01-02"}""")
        assertEquals(listOf("8.0", "likely score"), page(l).tiles(l.games[0])[3].let { listOf(it.value, it.label) })
    }

    @Test fun theOneButtonFollowsTheState() {
        assertEquals(listOf("Start playing"), one("""{"id":"a","title":"A","state":"backlog"}""").let { (p, g) -> p.next(g).map { it.label } })
        assertEquals(listOf("I finished it"), one("""{"id":"a","title":"A","state":"playing"}""").let { (p, g) -> p.next(g).map { it.label } })
        assertEquals(listOf("Pick it up again"), one("""{"id":"a","title":"A","state":"dropped"}""").let { (p, g) -> p.next(g).map { it.label } })
        assertEquals(listOf("Watch the trailer"), one("""{"id":"a","title":"A","state":"upcoming","date":"2027-01-01"}""").let { (p, g) -> p.next(g).map { it.label } })
        // an upcoming state with a past date reads as out (`isUpcoming`)
        assertEquals(listOf("Start playing"), one("""{"id":"a","title":"A","state":"upcoming","date":"2025-01-01"}""").let { (p, g) -> p.next(g).map { it.label } })
        // Score it, Leave a note and Duel come later: no button that does nothing (the owner's Nov 10 answer)
        assertTrue(one("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":0}""").let { (p, g) -> p.next(g).isEmpty() })
        assertTrue(one("""{"id":"a","title":"A","state":"playing","comp":true}""").let { (p, g) -> p.next(g).isEmpty() })
    }

    @Test fun yourProgressLightsUpToWhereTheGameIs() {
        one("""{"id":"a","title":"A","state":"upcoming","date":"2026-12-03"}""").let { (p, g) ->
            assertEquals(listOf("Coming" to "now", "Playing" to "", "Finished" to ""), p.steps(g).map { it.name to it.c })
            assertEquals("3 Dec", p.steps(g)[0].sub)
            assertEquals("Coming", p.stateWord(p.state(g)))
            assertEquals("Comes out 3 December 2026 · 23 days away", p.line(g)!!.joinToString("") { it.first })
        }
        one("""{"id":"a","title":"A","state":"upcoming"}""").let { (p, g) ->
            assertEquals("TBA", p.steps(g)[0].sub)
            assertEquals("No release date yet. I'll tell you when one appears.", p.line(g)!!.single().first)
        }
        one("""{"id":"a","title":"A","state":"playing","date":"2025-06-01","logged":12}""").let { (p, g) ->
            assertEquals(listOf("done", "now", ""), p.steps(g).map { it.c })
            assertEquals(listOf("1 Jun 25", "12h in"), p.steps(g).take(2).map { it.sub })
            assertEquals("Playing now", p.stateWord(p.state(g)))
            assertEquals("its note and forecast come with piece 3", null, p.line(g))
        }
        one("""{"id":"a","title":"A","state":"playing","plAt":${now - 5 * JsDate.DAY}}""").let { (p, g) -> assertEquals("since 5 Nov", p.steps(g)[1].sub) }
        one("""{"id":"a","title":"A","state":"backlog","date":"2026-09-07"}""").let { (p, g) ->
            assertEquals("Out since 7 Sept. Ready when you are.", p.line(g)!!.joinToString("") { it.first })
            assertEquals("Waiting", p.stateWord(p.state(g)))
        }
        one("""{"id":"a","title":"A","state":"played","logged":30}""", """{"id":"a","real":9,"t":"2026-04-02"}""").let { (p, g) ->
            assertEquals(listOf("done", "done", "done"), p.steps(g).map { it.c })
            assertEquals(listOf("30h", "Apr 2026"), p.steps(g).drop(1).map { it.sub })
            assertEquals("Finished in Apr 2026. You gave it 9.", p.line(g)!!.joinToString("") { it.first })
        }
        one("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":0}""").let { (p, g) ->
            assertEquals("Finished. Not scored yet.", p.line(g)!!.joinToString("") { it.first })
        }
        one("""{"id":"a","title":"A","state":"dropped"}""").let { (p, g) ->
            assertEquals(listOf("Dropped" to "drop"), p.steps(g).drop(2).map { it.name to it.c })
            assertEquals("You put this one down.", p.line(g)!!.single().first)
        }
    }

    @Test fun genresCountYourGamesAndSteamTagsSkipTheGenericOnes() {
        val l = lib(
            """{"id":"a","title":"A","genres":["Action","RPG","Action","Indie","Puzzle"],"stTags":["Souls-like","Singleplayer","rpg","Great Soundtrack","Metroidvania"]},""" +
                """{"id":"b","title":"B","genres":"Action, Shooter"},{"id":"c","title":"C","stTags":["Souls-like"]}""",
        )
        val t = page(l).tags(l.games[0])
        assertEquals("repeats go", listOf("Action", "RPG", "Indie", "Puzzle"), t.base)
        // one-word generic tags go; a multi-word one never matches (gdKey has no spaces): a web quirk kept on purpose
        assertEquals(listOf("Souls-like", "Great Soundtrack", "Metroidvania"), t.st)
        assertEquals("one genre past three + three tags", 4, t.more)
        assertEquals("Steam tags count too", 2, t.n("Souls-like"))
        assertEquals(2, t.n("Action"))
        assertEquals(1, t.n("Puzzle"))
        // the Queue's genre tab holds every game with it, in any state
        val q = Queue(l, Rank(l, now, zone), now, zone)
        assertEquals(listOf("a", "c"), q.list("G:Souls-like").map { it.id })
        assertEquals(2, q.count("G:Souls-like"))
        assertEquals("G:Souls-like", q.tabNow("G:Souls-like"))
        assertEquals("Souls-like", q.tabName("G:Souls-like"))
    }

    @Test fun theSwipeGoesThroughEveryGameInTheQueuesOrder() {
        val l = lib("""{"id":"a","title":"A","state":"played","ord":3},{"id":"b","title":"B","state":"backlog","ord":1},{"id":"c","title":"C","state":"dropped","ord":2}""")
        val q = Queue(l, Rank(l, now, zone), now, zone)
        assertEquals("played and dropped too, in your order", listOf("b", "c", "a"), Page.order(q, "manual", emptyMap()).map { it.id })
        assertEquals("your practice drags count", listOf("a", "b", "c"), Page.order(q, "manual", mapOf("a" to 0.5)).map { it.id })
    }

    // ---- the one practice copy (3-critic.md M7) ----

    private fun laid(l: Library, edits: Map<String, kotlinx.serialization.json.JsonElement>) =
        Library(Backup.load(Practice.laid(JsonObject(l.state + edits)), now, zone))

    @Test fun startingAGameSetsItPlayingAndRemembersWhen() {
        val l = lib("""{"id":"a","title":"A","state":"backlog"},{"id":"b","title":"B","state":"dropped","plAt":5}""")
        val x = laid(l, Practice.start(l, l.games[0], now) { null })
        assertEquals("playing", x.games[0].state)
        assertEquals(JsonPrimitive(now), x.games[0]["plAt"])
        assertEquals(setOf("a"), Practice.changed(x))
        // a start time already there stays (`if (!g.plAt)`)
        val y = laid(l, Practice.start(l, l.games[1], now) { null })
        assertEquals(JsonPrimitive(5), y.games[1]["plAt"])
    }

    @Test fun finishingAddsTheRecordWithTodaysDateOnThePhone() {
        val l = lib("""{"id":"a","title":"A","state":"playing","plAt":1000}""")
        // 11 PM UTC on the 9th is the 10th in Dubai: the date is the phone's (fnDone's en-CA), not UTC's
        val late = Instant.parse("2026-11-09T23:00:00Z").toEpochMilli()
        val x = laid(l, Practice.finish(l, l.games[0], late, zone) { null })
        assertEquals("played", x.games[0].state)
        assertEquals("the start time stays for the pace", JsonPrimitive(1000), x.games[0]["plAt"])
        assertEquals("2026-11-10", x.played.single().t)
        assertFalse("played, not scored yet", x.played.single().scored)
        val q = Queue(x, Rank(x, now, zone), now, zone)
        assertEquals("the Queue counts it played at once", 1, q.counts["done"])
    }

    @Test fun pickingAFinishedGameUpAgainTakesItsRecordAway() {
        val l = lib("""{"id":"a","title":"A","state":"played"}""", """{"id":"a","real":8,"t":"2026-01-02"}""")
        val x = laid(l, Practice.start(l, l.games[0], now) { null })
        assertTrue(x.played.isEmpty())
        assertEquals("load() no longer forces it played", "playing", x.games[0].state)
    }

    @Test fun changesBuildOnEachOtherAndADeleteTakesEverythingOfTheGame() {
        val l = lib(
            """{"id":"a","title":"A","state":"backlog"},{"id":"b","title":"B","state":"played"}""",
            """{"id":"b","real":6,"t":"2026-01-02"}""",
            extra = ""","duels":[{"a":"a","b":"b","w":"a"}]""",
        )
        val practice = HashMap<String, kotlinx.serialization.json.JsonElement>()
        practice += Practice.start(l, l.games[0], now) { practice[it] }
        val x = laid(l, practice)
        practice += Practice.finish(x, x.games[0], now, zone) { practice[it] }
        val y = laid(l, practice)
        assertEquals("start then finish", "played", y.games[0].state)
        assertEquals(JsonPrimitive(now), y.games[0]["plAt"])
        assertEquals(setOf("a", "b"), y.played.mapNotNull { it.id }.toSet())
        practice[Practice.GONE] = Practice.gone(practice[Practice.GONE], "b", true)
        val z = laid(l, practice)
        assertEquals(listOf("a"), z.games.map { it.id })
        assertEquals(listOf("a"), z.played.mapNotNull { it.id })
        assertTrue("its duels go too", z.duels.isEmpty())
        practice[Practice.GONE] = Practice.gone(practice[Practice.GONE], "b", false)
        assertEquals("Undo brings it back", listOf("a", "b"), laid(l, practice).games.map { it.id })
        assertFalse("the practice keys never stay in the copy", laid(l, practice).state.keys.any { it == Practice.GAMES || it == Practice.RECS || it == Practice.GONE })
    }
}
