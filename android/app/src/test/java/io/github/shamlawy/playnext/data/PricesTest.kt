package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.net.Routes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Prices on made-up games (index.html 19415-19700, android/parity/2-map/2-edit.md §7b, M4): which stores a game is
 * watched on, what the phone asks the helper (never the watch list's id/watch/off/all), the chips' words in your
 * currency, the ringed best deal and the line under it, a game you own, and what an answer and a failure leave.
 */
class PricesTest {
    private val now = Instant.parse("2026-11-14T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private fun lib(extra: String = "") = Library(Json.parseToJsonElement(LIB.replace("\"games\":", "$extra\"games\":")).jsonObject)
    private val lib = lib()
    private fun g(l: Library, id: String) = l.games.first { it.id == id }
    private fun rk(l: Library) = Rank(l, now, utc)

    @After fun clean() { Prices.forget() }

    @Test fun whichStoresAGameIsWatchedOn() {
        assertTrue("PC: Steam", Prices.want(lib, g(lib, "pc"), "st"))
        assertFalse("PC: not the PS Store", Prices.want(lib, g(lib, "pc"), "ps"))
        assertTrue("PS5: the PS Store", Prices.want(lib, g(lib, "ps"), "ps"))
        assertTrue("Switch: the eShop", Prices.want(lib, g(lib, "sw"), "ns"))
        assertTrue("no platforms: every store", Prices.STORES.all { Prices.want(lib, g(lib, "any"), it) })
        assertFalse("told it isn't on Steam", Prices.want(lib, g(lib, "nost"), "st"))
        assertFalse("played", Prices.watched(lib, rk(lib), g(lib, "done")))
        assertFalse("owned (M4)", Prices.watched(lib, rk(lib), g(lib, "own")))
        val off = lib("\"pxSteam\":false,")
        assertFalse("Steam switched off", Prices.want(off, g(off, "pc"), "st"))
        val all = lib("\"pxOff\":true,")
        assertNull("prices off", Prices.card(all, rk(all), g(all, "pc"), "ae"))
    }

    @Test fun theAskNeverTouchesTheWatchList() = runBlocking {
        var asked = ""
        Prices.post = { b -> if (asked.isEmpty()) asked = b; """{"ok":true,"res":{}}""" }  // the first ask (a second goes for stores with no answer)
        Prices.fetch(lib, rk(lib), g(lib, "ps"), "ae", { now })
        val b = Json.parseToJsonElement(asked).jsonObject
        assertEquals("ae", (b["cc"] as JsonPrimitive).content)
        assertEquals("the eShop isn't in the Gulf", "us", (b["nscc"] as JsonPrimitive).content)
        assertEquals("ae", (b["pscc"] as JsonPrimitive).content)
        assertEquals("AED", (b["mc"] as JsonPrimitive).content)
        assertTrue("rates asked for with the prices", b.containsKey("fx"))
        assertFalse(asked, Regex("\"(id|watch|off|all)\"\\s*:").containsMatchIn(asked))
        assertEquals(Routes.Allow, Routes.check("POST", Routes.HELPER_HOST, "/prices", asked))
        val it = (b["items"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("-1", (it["st"] as JsonPrimitive).content.removeSuffix(".0"))
        assertEquals("", (it["ps"] as JsonPrimitive).content)
        assertEquals("-", (it["ns"] as JsonPrimitive).content)
        val own = Prices.item(lib, g(lib, "own"), now)
        assertTrue("an owned game isn't priced", own["st"].toString() == "-1" && (own["ps"] as JsonPrimitive).content == "-" && (own["ns"] as JsonPrimitive).content == "-")
    }

    @Test fun pricesInYourCurrencyAndTheBestDeal() = runBlocking {
        // Steam in USD cents, the PS Store in AED fils; shown in AED (the pegged rate until the helper's)
        Prices.post = { """{"ok":true,"res":{"any":{
            "st":{"id":7001,"name":"Moonwell","cur":"USD","base":5999,"now":2999,"pct":50,"baseF":"${'$'}59.99","nowF":"${'$'}29.99","end":1764000000000},
            "ps":{"id":"EP0001-CUSA00001_00-MOONWELL00000000","name":"Moonwell","cur":"AED","base":24900,"now":24900,"pct":0,"baseF":"AED 249.00","nowF":"AED 249.00"},
            "ns":{"none":1}}}}""" }
        val l = lib
        Prices.fetch(l, rk(l), g(l, "any"), "ae", { now })
        val c = Prices.card(l, rk(l), g(l, "any"), "ae")!!
        assertEquals(listOf("ps", "st", "ns"), c.chips.map { it.s })
        val st = c.chips[1]
        assertEquals("29.99 USD x 3.6725", "AED\u00A0110", st.label)
        assertEquals(50, st.pct)
        assertTrue("cheapest in one currency: ringed", st.best)
        assertEquals("AED 249", c.chips[0].label)
        assertEquals("n/a", c.chips[2].label)
        assertEquals("Steam's sale end isn't kept (index.html pxTake keeps it for the PS Store and eShop only)", "Steam is cheapest · ≈ in AED", c.cap)
        assertTrue(Prices.show(c))
        assertEquals("the store id it found is kept", 7001.0, Backup.num(Prices.f(g(l, "any"), "pxSt"), 0.0, 0.0, 1e9), 0.0)
        assertEquals("https://store.steampowered.com/app/7001/", Prices.url(l, g(l, "any"), "st", "ae"))
        // a later, higher price keeps the lowest seen
        Prices.post = { """{"ok":true,"res":{"any":{"st":{"id":7001,"name":"Moonwell","cur":"USD","base":5999,"now":5999,"pct":0,"baseF":"${'$'}59.99","nowF":"${'$'}59.99"}}}}""" }
        Prices.fetch(l, rk(l), g(l, "any"), "ae", { now + 1 })
        assertEquals(2999.0, Prices.px(g(l, "any"), "st")!!.low!!, 0.0)
        assertTrue(Prices.card(l, rk(l), g(l, "any"), "ae")!!.cap.contains("lowest seen AED\u00A0110"))
    }

    @Test fun inYourPsPlusWinsAndTheStoresOwnCurrencyStaysPut() = runBlocking {
        val l = lib("\"psPlus\":2,\"pxCur\":\"store\",")
        Prices.post = { """{"ok":true,"res":{"ps":{"ps":{"id":"x","name":"Glass Road","cur":"USD","base":3999,"now":3999,"pct":0,"baseF":"${'$'}39.99","nowF":"${'$'}39.99","plus":2}}}}""" }
        Prices.fetch(l, rk(l), g(l, "ps"), "us", { now })
        val c = Prices.card(l, rk(l), g(l, "ps"), "us")!!
        val ps = c.chips.single()
        assertEquals(Prices.Kind.Plus, ps.kind)
        assertTrue(ps.best && ps.incl)
        assertEquals("PS Store is in your PS Plus", c.cap)
        assertEquals("$39.99", Prices.short("$39.99 USD"))
        assertEquals("AED 249", Prices.short("AED 249.00"))
    }

    @Test fun nothingYetSaysNothingAndAFailureKeepsOldPrices() = runBlocking {
        assertFalse("never checked: a row of dashes says nothing", Prices.show(Prices.card(lib, rk(lib), g(lib, "pc"), "ae")))
        assertTrue("an owned game says so", Prices.card(lib, rk(lib), g(lib, "own"), "ae")!!.own)
        assertTrue("never checked: due", Prices.due(lib, rk(lib), g(lib, "pc"), now))
        Prices.post = { throw Recap.HelperError("helper-old") }
        Prices.fetch(lib, rk(lib), g(lib, "pc"), "ae", { now })
        assertEquals("helper-old", Prices.err)
        assertFalse("tried a moment ago: not again for an hour", Prices.due(lib, rk(lib), g(lib, "pc"), now + 60_000))
        assertTrue(Prices.due(lib, rk(lib), g(lib, "pc"), now + 3_600_001))
        assertEquals("—", Prices.card(lib, rk(lib), g(lib, "pc"), "ae")!!.chips.single().label)
    }

    @Test fun theBackupsPricesShowAndAgeOut() {
        val l = lib()
        val c = Prices.card(l, rk(l), g(l, "kept"), "us")!!
        assertEquals("$19.99", c.chips.single().label)
        assertFalse("checked an hour ago", Prices.due(l, rk(l), g(l, "kept"), now))
        assertTrue("12 hours on", Prices.due(l, rk(l), g(l, "kept"), now + 12 * 3_600_000L))
    }

    companion object {
        val LIB = """{"games":[
            {"id":"pc","title":"Moonwell PC","state":"backlog","platforms":"PC"},
            {"id":"ps","title":"Glass Road","state":"backlog","platforms":"PS5"},
            {"id":"sw","title":"Lantern Bay","state":"backlog","platforms":"Switch"},
            {"id":"any","title":"Moonwell","state":"backlog"},
            {"id":"nost","title":"Ash Garden","state":"backlog","platforms":"PC","pxSt":-1},
            {"id":"done","title":"Tin Crown","state":"played","platforms":"PC"},
            {"id":"own","title":"Iron Tide","state":"backlog","platforms":"PC","own":true},
            {"id":"kept","title":"Fallen Kite","state":"backlog","platforms":"PC","pxSt":42,"px":{"st":{"id":42,"name":"Fallen Kite","cur":"USD","base":1999,"now":1999,"pct":0,"baseF":"${'$'}19.99","nowF":"${'$'}19.99","at":1794639600000}}}
        ],"played":[{"id":"done","real":7,"t":"2024-02-01"}]}"""
    }
}
