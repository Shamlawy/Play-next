package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.net.Routes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
 * Prices on made-up games (index.html 19418-19700, android/parity/2-map/2-edit.md §7b, 3-critic.md M4): which stores a
 * game is watched on, the card from the backup's last check (converted into your currency, the best deal ringed), the
 * money words, PS Plus, a game you own, and the helper's answer (never a watch-list write).
 */
class PricesTest {
    private val now = Instant.parse("2026-11-15T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private val lib = Library(Json.parseToJsonElement(LIB.replace("NOW", now.toString())).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }
    private val rk = Rank(lib, now, utc)
    private val r = Prices.region(lib, "Asia/Dubai", "AE")

    @After fun clean() { Prices.forget() }

    @Test fun whichStoresAreWatched() {
        assertTrue(Prices.want(lib, g("pc"), "st"))
        assertTrue(Prices.want(lib, g("pc"), "ps"))
        assertFalse(Prices.want(lib, g("pc"), "ns"))
        assertFalse("Switch only", Prices.want(lib, g("sw"), "st"))
        assertTrue(Prices.want(lib, g("sw"), "ns"))
        assertTrue("no platforms: every store", listOf("st", "ps", "ns").all { Prices.want(lib, g("bare"), it) })
        assertFalse("told it isn't on Steam", Prices.want(lib, g("nost"), "st"))
        assertNull("played", Prices.card(lib, rk, g("done"), r, utc))
        assertEquals("ae", r.cc)
        assertEquals("us", r.nscc)
        assertEquals("ae", r.pscc)
        assertEquals("AED", r.mc)
    }

    @Test fun moneyWords() {
        assertEquals("$59.99", Prices.money(59.99, "USD"))
        assertEquals("AED\u00A0249", Prices.money(249.0, "AED"))
        assertEquals("€9.50", Prices.money(9.5, "EUR"))
        assertEquals("AED 249", Prices.short("AED 249.00"))
        assertEquals("$59.99", Prices.short("$59.99 USD"))
        assertEquals("$60", Prices.short("$60.00"))
        assertEquals("3 Mar", Prices.end(Instant.parse("2027-03-03T10:00:00Z").toEpochMilli().toDouble(), utc))
    }

    @Test fun theCardFromTheBackupsCheck() {
        val c = Prices.card(lib, rk, g("pc"), r, utc)!!
        assertTrue(c.shows)
        assertEquals(listOf("ps", "st"), c.chips.map { it.s })
        val ps = c.chips[0]
        assertEquals("49.99 USD at 3.6725 a dollar", "AED\u00A0184", ps.text)
        assertEquals(Prices.Kind.PRICE, ps.kind)
        assertTrue("the cheaper one is ringed", ps.best)
        assertEquals(29, ps.pct)
        assertEquals("AED\u00A0257", ps.old)
        assertEquals("AED\u00A0220", c.chips[1].text)
        assertFalse(c.chips[1].best)
        assertEquals("PS Store is cheapest · ends 1 Dec · ≈ in AED", c.cap)
        assertTrue(ps.tip, ps.tip.startsWith("PS Store, about AED"))
    }

    @Test fun inYourPsPlusAndAGameYouOwn() {
        val plus = Library(Json.parseToJsonElement(LIB.replace("NOW", now.toString()).replace("\"psPlus\":0", "\"psPlus\":2")).jsonObject)
        val c = Prices.card(plus, Rank(plus, now, utc), plus.games.first { it.id == "inc" }, r, utc)!!
        assertEquals("✓ Plus", c.chips[0].text)
        assertEquals(Prices.Kind.INCL, c.chips[0].kind)
        assertTrue(c.cap, c.cap.startsWith("PS Store is in your PS Plus"))
        val own = Prices.card(lib, rk, g("own"), r, utc)!!
        assertTrue(own.own && own.shows)
        val it = Prices.item(lib, g("own"), now)
        assertEquals(-1.0, Backup.num(it["st"], 0.0, -9.0, 9.0), 0.0)
        assertEquals("-", Backup.str(it["ps"]))
        assertFalse("nothing asked for a game you own", Prices.dueOnOpen(lib, rk, g("own"), now))
    }

    @Test fun theHelpersAnswerNeverWritesTheWatchList() = runBlocking {
        var asked = ""
        Prices.post = { b ->
            asked = b
            """{"ok":true,"res":{"bare":{"st":{"id":4410,"name":"","cur":"USD","base":1999,"now":999,"pct":50,"baseF":"$19.99","nowF":"$9.99"},
               "ps":{"none":1},"ns":{"id":"70010000000001","name":"","cur":"USD","base":20,"now":20,"pct":0,"baseF":"$20.00","nowF":"$20.00","url":"https://example.com/ns"}}},
               "fx":{"ok":true,"base":"USD","r":{"USD":1,"AED":3.6725},"t":1}}"""
        }
        assertTrue(Prices.dueOnOpen(lib, rk, g("bare"), now))
        assertTrue(Prices.fetch(lib, g("bare"), r, { now }))
        assertTrue(asked, asked.contains("\"cc\":\"ae\"") && asked.contains("\"pscc\":\"ae\"") && asked.contains("\"mc\":\"AED\"") && asked.contains("\"st\":0"))
        assertEquals(Routes.Allow, Routes.check("POST", Routes.HELPER_HOST, "/prices", asked))
        assertEquals(4410.0, Backup.num(Prices.field(g("bare"), "pxSt"), 0.0, 0.0, 1e9), 0.0)
        assertEquals(JsonPrimitive("https://example.com/ns"), Prices.field(g("bare"), "pxNsU"))
        assertEquals("a store that said 'not there' is remembered", now.toDouble(), Backup.num(Prices.field(g("bare"), "pxPsNo"), 0.0, 0.0, 1e15), 0.0)
        val c = Prices.card(lib, rk, g("bare"), r, utc)!!
        assertEquals(listOf("n/a", "AED\u00A036.69", "AED\u00A073.45"), c.chips.map { it.text })
        assertTrue("Steam is cheapest", c.chips[1].best)
        assertFalse("asked once an hour", Prices.dueOnOpen(lib, rk, g("bare"), now + 60_000))
    }

    @Test fun anOldHelperAndNoNetworkKeepThePrices() = runBlocking {
        Prices.post = { throw Recap.HelperError("helper-old") }
        assertFalse(Prices.fetch(lib, g("pc"), r, { now }))
        assertEquals("helper-old", Prices.err)
        assertEquals("the backup's prices still show", "AED\u00A0184", Prices.card(lib, rk, g("pc"), r, utc)!!.chips[0].text)
        Prices.post = { throw java.io.IOException("offline") }
        assertFalse(Prices.fetch(lib, g("pc"), r, { now }))
        assertFalse(Prices.busy(g("pc")))
    }

    companion object {
        const val LIB = """{"pxCC":"ae","psPlus":0,"fx":{"r":{"USD":1,"AED":3.6725},"at":NOW},"games":[
            {"id":"pc","title":"Moonwell","state":"backlog","platforms":["PC","PS5"],"px":{
              "st":{"id":1,"cur":"USD","base":5999,"now":5999,"pct":0,"baseF":"$59.99","nowF":"$59.99","at":NOW},
              "ps":{"id":"UP0000-PPSA00001_00-TESTGAME00000000","cur":"USD","base":6999,"now":4999,"pct":29,"baseF":"$69.99","nowF":"$49.99","end":1796126400000,"at":NOW}}},
            {"id":"sw","title":"Glass Road","state":"backlog","platforms":"Switch"},
            {"id":"bare","title":"Lantern Bay","state":"backlog"},
            {"id":"nost","title":"Ash Garden","state":"backlog","platforms":"PC","pxSt":-1},
            {"id":"inc","title":"Tin Crown","state":"backlog","platforms":"PS5","px":{"ps":{"id":"x","cur":"USD","base":3999,"now":3999,"pct":0,"nowF":"$39.99","plus":2,"at":NOW}}},
            {"id":"own","title":"Iron Tide","state":"backlog","platforms":"PC","own":true},
            {"id":"done","title":"Fallen Kite","state":"played","platforms":"PC"}
        ],"played":[{"id":"done","real":8,"t":"2024-02-01"}]}"""
    }
}
