package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * data/Taste.kt and Home's taste cards (Phase 3 piece 5) on made-up games, for the branches GoldenTest's seed doesn't
 * reach: every `relationship` label, `hypeBias`, `genrePattern`, `categoryPull`, `expectedInfo`, `tonight`'s points,
 * the hype trap running hot, Furthest from your hype's letdown and JavaScript's `toFixed`.
 */
class TasteTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = Instant.parse("2026-11-22T08:00:00Z").toEpochMilli()

    private fun q(games: String, played: String = "[]", extra: String = ""): Queue {
        val lib = Library(Json.parseToJsonElement("""{"games":[$games],"played":$played$extra}""").jsonObject)
        return Queue(lib, Rank(lib, now, zone), now, zone)
    }

    private fun game(s: String) = Game(Json.parseToJsonElement(s).jsonObject)
    private fun rec(s: String) = Played(Json.parseToJsonElement(s).jsonObject)
    private fun rel(g: String, p: String, dropped: Boolean = false) = Taste.relationship(game(g), rec(p), dropped)?.first

    @Test fun everyRelationshipLabel() {
        assertEquals("The one that got away", rel("""{"hype":8}""", """{"real":6}""", dropped = true))
        assertEquals("Left on the shelf", rel("""{"hype":5}""", """{"real":6}""", dropped = true))
        assertEquals("The obsession", rel("""{"hype":5}""", """{"real":6,"runs":3}"""))
        assertEquals("The heartbreak", rel("""{"hype":9}""", """{"real":5}"""))
        assertEquals("The hidden gem", rel("""{"hype":4}""", """{"real":8}"""))
        assertEquals(
            "You barely expected anything from it. It repaid you 4 points over.",
            Taste.relationship(game("""{"hype":4}"""), rec("""{"real":8}"""), false)?.second,
        )
        assertEquals("The attachment", rel("{}", """{"real":9.5,"again":true}"""))
        assertEquals("The benchmark", rel("{}", """{"real":9}"""))
        assertEquals("Called it", rel("""{"hype":7}""", """{"real":7.5}"""))
        assertEquals("The write-off", rel("{}", """{"real":3}"""))
        assertEquals("The steady one", rel("{}", """{"real":6}"""))
        // nothing to read before a real score
        assertNull(rel("""{"hype":7}""", "{}"))
        assertNull(rel("""{"hype":7}""", """{"real":0,"z":1}"""))
    }

    private val mixed = """
        {"id":"p1","title":"Glass Orchard","state":"played","hype":8,"genres":["RPG"],"mine":{"story":8}},
        {"id":"p2","title":"Tin Comet","state":"played","hype":6,"genres":["RPG","Puzzle"],"mine":{"story":7,"music":5}},
        {"id":"p3","title":"Brass Tide","state":"played","hype":9,"genres":["Action"],"mine":{"story":5}},
        {"id":"u1","title":"Pale Signal","state":"backlog","hype":8,"genres":["RPG"]},
        {"id":"u2","title":"Moth Engine","state":"backlog","hype":7,"genres":["Sports"]}"""
    private val mixedPlayed = """[{"id":"p1","real":9},{"id":"p2","real":8},{"id":"p3","real":6}]"""

    @Test fun biasGenresPullAndExpected() {
        val t = Taste(q(mixed, mixedPlayed))
        assertTrue(t.ready.ok)
        assertEquals(3, t.ready.have)
        // the verdict against the hype: +1, +2, -3
        assertEquals(3, t.bias.n)
        assertEquals(0.0, t.bias.mean, 1e-12)
        assertEquals(Math.sqrt(14.0 / 3), t.bias.spread, 1e-12)
        assertEquals(1, t.bias.hits)
        // only a genre with two or more games counts
        assertEquals(listOf("RPG"), t.genres.map { it.name })
        assertEquals(1.5, t.genres[0].mean, 1e-12)
        // story tracks the score exactly; nothing else has three pairs
        assertEquals("Story", t.topPull?.label)
        assertEquals(1.0, t.topPull!!.r!!, 1e-12)
        assertNull(t.pull.first { it.k == "music" }.r)
        val x = t.expected(game("""{"hype":8,"genres":["RPG"]}"""))!!
        assertEquals(.75, x.drift, 1e-12)
        assertEquals(8.75, x.score, 1e-12)
        assertEquals("RPG", x.genre)
        assertEquals(2, x.genreN)
        assertEquals(7.0, t.expected(game("""{"hype":7,"genres":["Sports"]}"""))!!.score, 1e-12)
        assertNull(t.expected(game("""{"genres":["RPG"]}""")))
        // under S.analysisAt nothing is read
        val shut = Taste(q(mixed, mixedPlayed, ""","analysisAt":4"""))
        assertFalse(shut.ready.ok)
        assertNull(shut.expected(game("""{"hype":8}""")))
    }

    @Test fun tonightWeighsLengthAndWhatYouStarted() {
        val t = Taste(
            q(
                """
                {"id":"t1","title":"Short Fuse","state":"backlog","hype":5,"hours":2},
                {"id":"t2","title":"Long Road","state":"playing","hype":5,"hours":30},
                {"id":"t3","title":"Not Yet","state":"upcoming","date":"2027-03-01","hype":10,"hours":1},
                {"id":"t4","title":"No Clock","state":"backlog","hype":10}""",
            ),
        )
        val n = t.tonight(2.0, now)!!
        assertEquals(3, n.n)
        // 12.5 + 22 fits tonight; 25 + nothing; 12.5 + 26 started - 25 for a 30 hour game
        assertEquals(listOf("t1", "t4", "t2"), (listOf(n.top) + n.rest).map { it.g.id })
        assertEquals(listOf(34.5, 25.0, 13.5), (listOf(n.top) + n.rest).map { it.pts })
        assertEquals(listOf("short enough to finish tonight"), n.top.why)
        assertEquals(listOf("no length on it yet"), n.rest[0].why)
        assertEquals(listOf("you're already in it", "a 30 hour commitment — not a tonight decision"), n.rest[1].why)
        val one = t.tonight(1.0, now)!!
        assertEquals(listOf("about 2 sittings like tonight"), (listOf(one.top) + one.rest).first { it.g.id == "t1" }.why)
        assertNull(Taste(q("""{"id":"t3","title":"Not Yet","state":"upcoming","hype":10}""")).tonight(2.0, now))
    }

    private val hot = """
        {"id":"p1","title":"Iron Psalm","state":"played","hype":9,"genres":["Action"]},
        {"id":"p2","title":"Cinder Lane","state":"played","hype":8,"genres":["Action"]},
        {"id":"p3","title":"Grey Pilot","state":"played","hype":8,"genres":["Action"]},
        {"id":"u1","title":"Velvet Storm","state":"backlog","hype":8,"genres":["Action"],"hours":20}"""
    private val hotPlayed = """[{"id":"p1","real":6},{"id":"p2","real":6},{"id":"p3","real":7}]"""

    @Test fun homeTasteCardsWhenYouRunHot() {
        val h = Home(q(hot, hotPlayed), now, zone)
        assertEquals(listOf("k-wrap", "k-month", "Your read", "What decides it", "Duels", "k-swing", "k-trap", "k-lean"), h.stack("sB").map { it.id })
        assertEquals(listOf("k-tonight", "k-far", "k-year"), h.stack("sC").map { it.id })
        val read = h.stack("sB").first { it.id == "Your read" } as Home.More.Big
        assertEquals("−2.0", read.big)
        assertEquals("You run hot — games land below your hype.", read.sub)
        assertEquals("taste", read.go)
        // no area scores yet: the card says how it works itself out, and its tap goes to Played
        val cat = h.stack("sB").first { it.id == "What decides it" } as Home.More.Big
        assertEquals("", cat.big)
        assertEquals("played", cat.go)
        val swing = h.stack("sB").first { it.id == "k-swing" } as Home.More.Big
        assertEquals("Grey Pilot", swing.name)
        assertEquals("Called it", swing.big)
        assertEquals("p3", swing.open?.id)
        val trap = h.stack("sB").first { it.id == "k-trap" } as Home.More.Big
        assertEquals("Hype trap", trap.name)
        assertEquals("8 → 6.0", trap.big + trap.em)
        assertEquals("Velvet Storm — on 3 finished games you tend to land 2.0 under your hype, and more so with Action. Give or take 0.8.", trap.sub)
        assertTrue(trap.warn)
        assertEquals("u1", trap.open?.id)
        val lean = h.stack("sB").first { it.id == "k-lean" } as Home.More.Big
        assertEquals("beats your hype by -2.0 across 3 games", lean.sub)
        val far = h.stack("sC").first { it.id == "k-far" } as Home.More.Far
        assertEquals(listOf("p3", "p1"), far.rows.map { it.g.id })
        assertEquals(listOf("9", "6", "you said 9 · -3.0 short"), far.rows[1].let { listOf(it.was, it.now, it.cap) })
        val night = h.stack("sC").first { it.id == "k-tonight" } as Home.More.Tonight
        assertEquals("With 4h tonight", night.name)
        assertEquals("Velvet Storm", night.top.title)
        // 20 hours at 4 a night is neither a tonight nor a long haul; the record says it may land low
        assertEquals("your record says this one may land under your hype", night.why)
        assertEquals("", night.rest)
    }

    @Test fun tonightKeepsYourHoursAndWaitsWhenNothingIsOut() {
        val h = Home(q(hot, hotPlayed, ""","tonightH":2"""), now, zone)
        assertEquals("With 2h tonight", h.stack("sC").first { it.id == "k-tonight" }.name)
        // nothing out: a plain card in the upright stack says so
        val none = Home(q("""{"id":"z","title":"Far Shore","state":"upcoming","hype":4}"""), now, zone)
        assertEquals("Nothing in the queue is out yet. This wakes up when a game you own is waiting.", (none.stack("sB").first { it.id == "Tonight" } as Home.More.Big).sub)
        assertTrue(none.stack("sC").none { it.id == "k-tonight" })
        // switched off in Customise › Home: gone from both
        val off = Home(q(hot, hotPlayed, ""","homeHide":["tonight","trap","read"]"""), now, zone)
        assertTrue((off.stack("sB") + off.stack("sC")).none { it.id in setOf("k-tonight", "Tonight", "k-trap", "Your read") })
    }

    @Test fun toFixedAsJavaScript() {
        assertEquals("-0.0", Taste.fixed(-.04, 1))
        assertEquals("1.3", Taste.fixed(1.25, 1))
        assertEquals("1.00", Taste.fixed(1.005, 2))
        assertEquals("4", Taste.fixed(3.5, 0))
    }
}
