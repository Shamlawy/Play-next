package io.github.shamlawy.playnext.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

/** The Queue's prices on made-up games (android-v52): PS5 first, Switch when it isn't there, and the two price orders. */
class PriceTagsTest {
    private val now = Instant.parse("2026-11-23T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private val lib = Library(Json.parseToJsonElement(LIB).jsonObject)
    private val rk = Rank(lib, now, utc)
    private val q = Queue(lib, rk, now, utc)
    private fun g(id: String) = lib.games.first { it.id == id }
    private fun res(s: String) = Json.parseToJsonElement(s).jsonObject

    @After fun clean() { Prices.forget() }

    private fun land() {
        for (g in lib.games) Prices.take(lib, g, res(ANSWERS), now)
    }

    @Test fun aPs5GameShowsItsPs5PriceAndItsSale() {
        land()
        val t = q.priceTag(g("sale"))!!
        assertEquals("ps", t.s)
        assertEquals("AED 26.17", t.now)
        assertEquals("AED 262", t.old)
        assertEquals(90, t.pct)
    }

    @Test fun aGameNotOnPs5ShowsItsSwitchPrice() {
        land()
        assertEquals("ns", q.priceTag(g("switch"))!!.s)
        assertEquals("PS5 said no, so the Switch price", "ns", q.priceTag(g("both"))!!.s)
    }

    @Test fun ownedPcOnlyAndPs4OnlyGamesShowNoPrice() {
        land()
        assertNull("you own it", q.priceTag(g("own")))
        assertNull("not on PS5 or Switch", q.priceTag(g("pc")))
        assertNull("PS4 only", q.priceTag(g("ps4")))
    }

    @Test fun olderNintendoGamesAreNeverAskedAnEshopPrice() {
        val old = Library(Json.parseToJsonElement("""{"games":[
            {"id":"ds","title":"x","state":"backlog","platforms":"Nintendo 3DS"},
            {"id":"n64","title":"x","state":"backlog","platforms":"Nintendo 64, Wii"},
            {"id":"ns","title":"x","state":"backlog","platforms":"Nintendo Switch 2"}
        ],"played":[]}""").jsonObject)
        assertEquals(emptyList<String>(), PriceTags.stores(old, old.games[0]))
        assertEquals(emptyList<String>(), PriceTags.stores(old, old.games[1]))
        assertEquals(listOf("ns"), PriceTags.stores(old, old.games[2]))
    }

    @Test fun aPs5AnswerTheHelperHasntCheckedNeverShows() {
        // the backup's older pick could be another product: a false bargain in the price orders
        Prices.take(lib, g("sale"), res("""{"sale":{"ps":{"id":"EP0001-PPSA00001_00-CHEAPMODE0000000","name":"Mode","cur":"AED","base":8598,"now":8598,"pct":0,"baseF":"85.98 AED","nowF":"85.98 AED"}}}"""), now)
        assertFalse(Prices.p5Checked(g("sale")))
        assertNull(q.priceTag(g("sale")))
        assertNotNull("so it's asked again", PriceTags.ask(lib, rk, g("sale"), now))
    }

    @Test fun aSaleThatEndedIsTheFullPriceAgain() {
        land()
        val after = Instant.parse("2026-11-30T08:00:00Z").toEpochMilli()
        val t = PriceTags.of(lib, Rank(lib, after, utc), g("sale"), "ae", after)!!
        assertEquals(0, t.pct)
        assertEquals("AED 262", t.now)
    }

    @Test fun biggestDiscountFirstThenFullPriceThenNoPrice() {
        land()
        val order = q.sort(q.list("all"), "sale").map { it.id }
        assertEquals(listOf("sale", "switch", "both"), order.take(3))
        assertTrue("no price last", order.indexOf("full") < order.indexOf("own") && order.indexOf("full") < order.indexOf("pc"))
    }

    @Test fun cheapestFirstAcrossCurrenciesWithPsPlusOnTop() {
        land()
        val order = q.sort(q.list("all"), "cheap").map { it.id }
        assertEquals("in your PS Plus costs nothing more", "plus", order[0])
        // AED 26.17 = $7.13, then $14.99, then $19.99, then AED 261.76 = $71.28
        assertEquals(listOf("sale", "both", "switch", "full"), order.subList(1, 5))
    }

    @Test fun priceOrdersKeepATotalOrderOnALongList() {
        land()
        val many = (1..60).flatMap { lib.games }
        assertEquals(many.size, q.sort(many, "sale").size)
        assertEquals(many.size, q.sort(many, "cheap").size)
    }

    @Test fun theBulkAskNeverTouchesSteamOrTheWatchList() = runBlocking {
        val asks = mutableListOf<String>()
        Prices.post = { b -> asks += b; """{"ok":true,"res":{"both":{"ps":{"none":1,"p5":1}}}}""" }
        PriceTags.refresh(lib, rk, "ae", null) { now }
        assertTrue(asks.isNotEmpty())
        for (a in asks) {
            assertFalse(a, Regex("\"(id|watch|off|all)\"\\s*:").containsMatchIn(a))
            val items = Json.parseToJsonElement(a).jsonObject["items"]!!.jsonArray
            assertTrue("five a call", items.size <= 5)
            for (i in items) assertEquals("-1", (i.jsonObject["st"] as JsonPrimitive).content.removeSuffix(".0"))
        }
        // PS5 said no on the first pass: the second asks the eShop for it
        val second = asks.drop(1).joinToString().let { Regex("\"k\":\"both\"[^}]*").find(it)?.value.orEmpty() }
        assertTrue(second, "\"ns\":\"\"" in second)
    }

    @Test fun aStoreThatKeepsFailingIsAskedOnceAnHour() = runBlocking {
        var calls = 0
        Prices.post = { calls++; """{"ok":true,"res":{"full":{"ps":{"err":"PS Store said 500"}}}}""" }
        PriceTags.refresh(lib, rk, "ae", null) { now }
        val first = calls
        assertEquals("one pass: nothing was answered, so no second ask", 1, first)
        PriceTags.refresh(lib, rk, "ae", null) { now + 60_000 }
        assertEquals("the next Queue open asks nothing", first, calls)
        PriceTags.refresh(lib, rk, "ae", null) { now + 3_700_000 }
        assertTrue("an hour on it tries again", calls > first)
    }

    @Test fun pricesComeBackFromThePhonesOwnFile() = runBlocking {
        val dir = Files.createTempDirectory("prices").toFile()
        Prices.post = { ANSWERS.let { a -> """{"ok":true,"res":$a}""" } }
        PriceTags.refresh(lib, rk, "ae", dir) { now }
        assertNotNull(q.priceTag(g("sale")))
        Prices.forget()
        assertNull("gone from memory", q.priceTag(g("sale")))
        Prices.post = { throw IllegalStateException("no network") }
        PriceTags.refresh(lib, rk, "ae", dir) { now }
        assertEquals("AED 26.17", q.priceTag(g("sale"))!!.now)
        dir.deleteRecursively()
        Unit
    }

    private companion object {
        val LIB = """{"pxCC":"ae","psPlus":2,"games":[
            {"id":"full","title":"Hollow Tide","state":"backlog","date":"2025-01-01","ord":1,"platforms":"PS5"},
            {"id":"sale","title":"Starfall Lantern","state":"backlog","date":"2025-01-01","ord":2,"platforms":"PS5, Switch 2"},
            {"id":"switch","title":"Glass Road","state":"backlog","date":"2025-01-01","ord":3,"platforms":"Switch"},
            {"id":"both","title":"Pocket Comet","state":"backlog","date":"2025-01-01","ord":4,"platforms":"PS5, Switch"},
            {"id":"plus","title":"Paper Kingdom","state":"backlog","date":"2025-01-01","ord":5,"platforms":"PS5"},
            {"id":"own","title":"Moonwell","state":"backlog","date":"2025-01-01","ord":6,"platforms":"PS5","own":true},
            {"id":"pc","title":"Ninefold Gate","state":"backlog","date":"2025-01-01","ord":7,"platforms":"PC"},
            {"id":"ps4","title":"Quiet Harbour","state":"backlog","date":"2025-01-01","ord":8,"platforms":"PS4"}
        ],"played":[]}"""
        private fun ps(id: String, base: Int, now: Int, extra: String = "") = """{"id":"EP0001-PPSA0000${id}_00-MADEUPGAME000000","name":"x","cur":"AED","base":$base,"now":$now,"pct":${if (base > now) Math.round((1 - now.toDouble() / base) * 100) else 0},"baseF":"${"%.2f".format(base / 100.0)} AED","nowF":"${"%.2f".format(now / 100.0)} AED","p5":1$extra}"""
        private fun ns(id: String, base: Double, now: Double) = """{"id":"7001000000000$id","name":"x","url":"","cur":"USD","base":$base,"now":$now,"pct":${Math.round((1 - now / base) * 100)},"baseF":"$$base","nowF":"$$now"}"""
        val ANSWERS = """{
            "full":{"ps":${ps("1", 26176, 26176)}},
            "sale":{"ps":${ps("2", 26176, 2617, ",\"end\":" + Instant.parse("2026-11-28T00:00:00Z").toEpochMilli())}},
            "switch":{"ns":${ns("3", 39.99, 19.99)}},
            "both":{"ps":{"none":1,"p5":1},"ns":${ns("4", 29.99, 14.99)}},
            "plus":{"ps":${ps("5", 22436, 22436, ",\"plus\":2")}},
            "own":{"ps":${ps("6", 10000, 10000)}}
        }"""
    }
}
