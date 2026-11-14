package io.github.shamlawy.playnext.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Phase 2 piece 3b on made-up games (android/parity/2-map/1-page.md §5.4-5.7, index.html 20025-24060): the Finish
 * forecast's three paces and its clash line, Then & now's cells (the real minus, early/late against the forecast), the
 * Drifting card's thresholds and reasons, Something short's picks, and the story so far's request, cache and retry.
 */
class StoryTest {
    private val day = JsDate.DAY
    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private val uk = Locale.UK
    private fun noon(d: String) = Instant.parse(d + "T12:00:00Z").toEpochMilli()
    private val lib = Library(Json.parseToJsonElement(LIB.replace("NOW6", (now - 6 * day).toString()).replace("NOW1", (now - day).toString())
        .replace("D1", noon("2026-10-07").toString()).replace("D2", noon("2026-10-09").toString()).replace("D3", noon("2026-10-11").toString())
        .replace("D4", noon("2026-10-13").toString()).replace("D5", noon("2026-10-18").toString()).replace("ST", noon("2026-10-01").toString())).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }
    private fun rk() = Rank(lib, now, utc)
    private val none: (String) -> JsonElement? = { null }

    @After fun clean() { Forecast.forget(); Recap.forget() }

    @Test fun theForecastGoesByYourPaceThenYourPlanThenYourEvenings() {
        val r = rk()
        val a = Forecast.of(lib, r, g("p1"), now, utc)!!
        assertEquals("6h in 6 days", 1.0, a.pace, 1e-9)
        assertEquals("your pace so far", a.src)
        assertEquals(14, a.days)
        assertEquals("2026-11-10", a.date)
        val b = Forecast.of(lib, r, g("p2"), now, utc)!!
        assertEquals("your plan of 2h a day", b.src)
        assertEquals(5, b.days)
        val c = Forecast.of(lib, r, g("p3"), now, utc)!!
        assertEquals("about 4h an evening, 4 evenings a week", c.src)
        assertEquals("10h at 16/7 a day", 5, c.days)
        assertNull("finished", Forecast.of(lib, r, g("f1"), now, utc))
        assertNull("competitive", Forecast.of(lib, r, g("cp"), now, utc))
    }

    @Test fun aHypedGameOutBeforeYouFinishIsAClash() {
        val r = rk()
        val e = Forecast.of(lib, r, g("p1"), now, utc)!!
        val c = Forecast.clash(lib, r, g("p1"), e, now, utc)!!
        assertEquals("h1", c.r.id)
        assertEquals(10, c.until)
        val (line, by, clash) = Forecast.words(e, c, now, utc, uk)
        assertEquals(listOf("Done around " to false, "10\u00A0Nov" to true, " · 14h left" to false), line)
        assertEquals("Going by your pace so far.", by)
        assertEquals("Lantern Rush comes out 6\u00A0Nov, before you'll finish. About 1.4h a day finishes it in time.", clash)
        assertNull("p2 finishes before it", Forecast.clash(lib, r, g("p2"), Forecast.of(lib, r, g("p2"), now, utc)!!, now, utc))
        val far = Forecast.Clash(g("p1"), g("h1"), 2, 7.0)
        assertEquals("You'd need 7h a day, so you'll probably switch to Lantern Rush and come back", Forecast.fix(far))
    }

    @Test fun theTrackShowsTheForecastUnderFinished() {
        val q = Queue(lib, rk(), now, utc)
        val p = Page(q, now, utc, uk)
        assertEquals("~10\u00A0Nov", p.steps(g("p1"))[2].sub)
        assertNotNull(p.forecast(g("p1")))
    }

    @Test fun theFirstForecastRidesIntoTheSnapshot() {
        val r = rk()
        Forecast.seen(g("p1"), Forecast.of(lib, r, g("p1"), now, utc), now)
        val out = Practice.finish(lib, g("p1"), now + day, utc, none)
        val cap = ((out[Practice.GAMES] as JsonObject)["p1"] as JsonObject)["cap"] as JsonObject
        assertEquals(JsonPrimitive("2026-11-10"), cap["e0"])
    }

    /** Android forgets its first forecasts on a restart: one first seen minutes before you finish isn't measured against. */
    @Test fun aForecastFromFinishDayIsNotTheFirst() {
        Forecast.seen(g("p1"), Forecast.of(lib, rk(), g("p1"), now, utc), now)
        val out = Practice.finish(lib, g("p1"), now + 60_000L, utc, none)
        val cap = ((out[Practice.GAMES] as JsonObject)["p1"] as JsonObject)["cap"] as JsonObject
        assertNull(cap["e0"])
    }

    @Test fun thenAndNowLaysOutHowItWent() {
        val c = ThenNow.of(lib, rk(), g("f1"), now, utc, uk)!!
        assertEquals(
            listOf(
                "2 days late" to "vs my finish forecast (18\u00A0Oct)",
                "7 → 8" to "hype → score (+1)",
                "7.4" to "Nexi's guess (off by 0.6)",
                "19 days" to "from 1\u00A0Oct to 20\u00A0Oct",
                "80" to "critics · you liked it more",
            ),
            c.cells,
        )
        assertEquals("first", c.first!!.x)
        assertEquals("last", c.last!!.x)
        val f2 = ThenNow.of(lib, rk(), g("f2"), now, utc, uk)!!
        assertEquals("the real minus sign", "hype → score (−1.5)", f2.cells[0].second)
        assertEquals("spot on", "hype → score: spot on", ThenNow.of(lib, rk(), g("f3"), now, utc, uk)!!.cells[0].second)
        assertNull("too little to say", ThenNow.of(lib, rk(), g("f4"), now, utc, uk))
    }

    @Test fun driftingNeedsALongGapAndOneMoreSign() {
        val r = Drift.risk(lib, rk(), g("d1"), now, utc)!!
        assertEquals(
            listOf("you haven't played it in 8 days, and you usually come back every 2 days", "your sessions shrank from about 3h to 45 min"),
            r.why,
        )
        assertEquals("It's going the way Fallen Kite went before you dropped it.", Drift.line(r))
        assertNull("played two days ago", Drift.risk(lib, rk(), g("p1"), now, utc))
        val later = now + 6 * day
        assertNull("14 days away: Still playing? asks instead", Drift.risk(lib, Rank(lib, later, utc), g("d1"), later, utc))
    }

    @Test fun somethingShortIsOutShortAndDifferent() {
        val q = Queue(lib, rk(), now, utc)
        assertEquals(listOf("s1"), Drift.cleanse(q, g("d1")).map { it.id })
    }

    @Test fun theButtonsWriteThePracticeCopy() {
        val park = Drift.park(g("d1"), now, none)
        assertEquals(JsonPrimitive("backlog"), ((park[Practice.GAMES] as JsonObject)["d1"] as JsonObject)["state"])
        val ok = Drift.ok(lib, g("d1"), now, none)
        assertEquals(JsonPrimitive(now), (ok["dwAsked"] as JsonObject)["d1"])
        val go = Drift.startShort(lib, g("d1"), g("s1"), now, none)
        val gs = go[Practice.GAMES] as JsonObject
        assertEquals(JsonPrimitive("playing"), (gs["s1"] as JsonObject)["state"])
        assertEquals(JsonPrimitive("s1"), (gs["d1"] as JsonObject)["dwBack"])
    }

    @Test fun theStorySoFarAsksOnceAndRetriesAfterSixHours() = runBlocking {
        val p1 = g("p1")
        val body = Json.parseToJsonElement(Recap.body(p1)).jsonObject
        assertEquals(JsonPrimitive("Moonwell"), body["title"])
        assertEquals(JsonPrimitive("2026"), body["year"])
        assertEquals(JsonPrimitive("PC"), body["platform"])
        assertEquals(JsonPrimitive("chapter 3"), body["note"])
        assertEquals("[\"chapter 1\"]", body["earlier"].toString())
        var calls = 0
        var t = now
        Recap.post = { calls++; throw java.io.IOException("offline") }
        Recap.fetch(lib, p1) { t }
        assertFalse("an error shows nothing", Recap.shows(lib, p1))
        Recap.fetch(lib, p1) { t }
        assertEquals("not again within 6 hours", 1, calls)
        t += Recap.RETRY
        Recap.post = { calls++; """{"recap":"You crossed the bridge.","where":"Chapter 3","sure":0.4}""" }
        Recap.fetch(lib, p1) { t }
        assertEquals(2, calls)
        val c = Recap.of(lib, p1)!!
        assertEquals("You crossed the bridge.", c.x)
        assertEquals("Chapter 3", c.w)
        assertTrue(Recap.shows(lib, p1))
        Recap.fetch(lib, p1) { t }
        assertEquals("a recap is kept", 2, calls)
    }
}

private val LIB = """
    {"playStyle":"main",
     "games":[
       {"id":"p1","title":"Moonwell","state":"playing","date":"2026-03-01","hours":20,"logged":6,"plAt":NOW6,"platforms":"PC, Switch",
        "wasI":[{"t":NOW6,"x":"chapter 1"},{"t":NOW1,"x":"chapter 3"}]},
       {"id":"p2","title":"Tin Robin","state":"playing","date":"2026-03-01","hours":10,"daily":2,"plAt":NOW1},
       {"id":"p3","title":"Brass Fable","state":"playing","date":"2026-03-01","hours":10},
       {"id":"cp","title":"Arena Nine","state":"playing","comp":true,"hours":10},
       {"id":"h1","title":"Lantern Rush","state":"upcoming","date":"2026-11-06","hype":8},
       {"id":"f1","title":"Paper Dunes","state":"played","date":"2026-01-01","hype":7,
        "cap":{"h":7,"pr":7.4,"cr":80,"st":ST,"ad":"2026-09-01","hrs":0,"n1":{"t":1760000000000,"x":"first"},"at":1761000000000,"e0":"2026-10-18"},
        "wasI":[{"t":1760000000000,"x":"first"},{"t":1760900000000,"x":"last"}]},
       {"id":"f2","title":"Salt Choir","state":"played","date":"2026-01-01","cap":{"h":9,"pr":8,"cr":0,"st":0,"ad":"","at":1761000000000}},
       {"id":"f3","title":"Cinder Path","state":"played","date":"2026-01-01","cap":{"h":6,"pr":6,"cr":0,"st":0,"ad":"","at":1761000000000}},
       {"id":"f4","title":"Owl Station","state":"played","date":"2026-01-01"},
       {"id":"d1","title":"Iron Tide","state":"playing","date":"2026-01-01","hours":20,"genres":"RPG, Action",
        "lgH":[[D1,3],[D2,3],[D3,3],[D4,1],[D5,0.5]]},
       {"id":"x1","title":"Fallen Kite","state":"dropped","date":"2025-01-01","genres":"RPG, Action"},
       {"id":"s1","title":"Pocket Comet","state":"backlog","date":"2026-01-01","hours":5,"genres":"Puzzle"},
       {"id":"s2","title":"Long Road","state":"backlog","date":"2026-01-01","hours":40,"genres":"Puzzle"}
     ],
     "duels":[],"played":[{"id":"f1","real":8,"t":"2026-10-20"},{"id":"f2","real":7.5,"t":"2026-10-20"},{"id":"f3","real":6,"t":"2026-10-20"},{"id":"f4","real":5,"t":"2026-10-20"}]}
""".trimIndent()
