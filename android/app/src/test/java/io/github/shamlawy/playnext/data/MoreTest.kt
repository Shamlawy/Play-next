package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.kit.CallingCard
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 2 piece 5 on made-up games (android/parity/2-map/1-page.md §5.2 and §6, 2-edit.md §7d): the ⋯ drawer's buttons
 * and order, Reset scores in the practice copy, Why you'll love it's request, cache and answer, and the calling card's
 * seeded scraps against the web's own numbers.
 */
class MoreTest {
    private val now = Instant.parse("2026-11-16T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private fun lib(s: String = LIB) = Library(Json.parseToJsonElement(s).jsonObject)
    private fun page(l: Library) = Page(Queue(l, Rank(l, now, utc), now, utc), now, utc)
    private fun g(l: Library, id: String) = l.games.first { it.id == id }

    @After fun clean() = Why.forget()

    @Test fun theDrawerLeavesOutWhatTheMainRowHasAndPutsPricesBeforeWhereWasI() {
        val l = lib()
        // a played, scored game: its main row is the score, so Score isn't in the drawer
        assertEquals(
            listOf("queue", "card", "trailer", "similar", "lists", "prices", "note", "why", "reset"),
            page(l).more(g(l, "a")).map { it.kind },
        )
        assertEquals("Lists · 2", page(l).more(g(l, "a")).first { it.kind == "lists" }.label)
        assertEquals("Where was I? · 1", page(l).more(g(l, "a")).first { it.kind == "note" }.label)
        // upcoming: the main row is the trailer, so no Trailer here; no finish, so no Score
        assertEquals(listOf("queue", "card", "similar", "lists", "prices", "note", "why", "reset"), page(l).more(g(l, "up")).map { it.kind })
        // a competitive game's main row is the note: Prices goes first, as the web's splice at -1 → 0 does
        assertEquals(listOf("prices", "queue", "card", "trailer", "similar", "lists", "why", "reset"), page(l).more(g(l, "comp")).map { it.kind })
    }

    @Test fun resetScoresClearsTheGamesScoresItsFinishesAndItsDuelsOnly() {
        val l = lib()
        val practice = HashMap<String, JsonElement>()
        practice += Practice.reset(g(l, "a")) { practice[it] }
        val out = lib(JsonObject(Json.parseToJsonElement(LIB).jsonObject + practice).let(Practice::laid).toString())
        val a = g(out, "a")
        for (k in listOf("hype", "mine", "subs", "why")) assertNull(k, a[k])
        assertEquals("Star Quest", a.title)
        val recs = out.played.filter { it.id == "a" }
        assertEquals(2, recs.size)
        assertTrue(recs.all { it.real == 0.0 && !it.again && it.o["runs"] == null && !it.scored })
        assertEquals("the other games' finishes stay", 8.0, out.played.first { it.id == "b" }.real!!, 0.0)
        assertEquals(listOf("b" to "c"), out.duels.map { it.a to it.b })
        assertEquals(setOf("a"), Practice.wiped(out))
        assertTrue("the page's practice line", "a" in Practice.changed(out))
        // reset twice: still one entry
        practice += Practice.reset(g(out, "a")) { practice[it] }
        assertEquals(1, (practice[Practice.WIPED] as JsonArray).size)
    }

    @Test fun whyAsksWithYourTasteAndKeepsTheAnswerWhileYourScoresStay() = runBlocking {
        val l = lib()
        var sent = ""
        Why.post = { b -> sent = b; """{"text":"VERDICT: **Strong fit**\n- You loved **long** quests\n2. It's shorter than most\n\n* A third"}""" }
        // the backup's answer is for these scores: used as is
        assertEquals("Mixed", Why.shown(Why.kept(l, g(l, "up"))!!).verdict)
        val a = Why.ask(l, g(l, "b"))
        val body = Json.parseToJsonElement(sent).jsonObject
        val taste = body["taste"] as JsonArray
        // a has two finishes, both scored (9 and 9), then c (7): b's own isn't sent
        assertEquals(listOf("Star Quest", "Star Quest", "Sky Fall"), taste.map { it.jsonObject["title"]!!.jsonPrimitive.content })
        assertEquals("Story 9", (taste[0].jsonObject["parts"] as JsonArray)[0].jsonPrimitive.content)
        assertEquals("true", body["game"]!!.jsonObject["played"]!!.jsonPrimitive.content)
        assertEquals("8", body["game"]!!.jsonObject["myScore"]!!.jsonPrimitive.content)
        val s = Why.shown(a)
        assertEquals("Strong fit", s.verdict)
        assertEquals(listOf("You loved long quests", "It's shorter than most", "A third"), s.points)
        assertEquals(3, s.n)
        assertNotNull(Why.kept(l, g(l, "b")))
        // a reset leaves the backup's answer unused
        val practice = HashMap<String, JsonElement>()
        practice += Practice.reset(g(l, "up")) { practice[it] }
        val out = lib(JsonObject(Json.parseToJsonElement(LIB).jsonObject + practice).let(Practice::laid).toString())
        assertNull(Why.kept(out, g(out, "up")))
    }

    @Test fun overEighteenRatedGamesTheTop12AndBottom6Go() {
        val taste = (1..20).map { JsonObject(mapOf("title" to kotlinx.serialization.json.JsonPrimitive("t$it"))) }
        val l = lib()
        val body = Json.parseToJsonElement(Why.body(l, g(l, "up"), taste)).jsonObject
        val sent = (body["taste"] as JsonArray).map { it.jsonObject["title"]!!.jsonPrimitive.content }
        assertEquals((1..12).map { "t$it" } + (15..20).map { "t$it" }, sent)
        assertEquals("null", body["game"]!!.jsonObject["myScore"].toString())
        assertFalse(body["game"]!!.jsonObject["played"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun theCallingCardsScrapsMatchTheWebs() {
        // numbers from the web's own seed and Mulberry32 (node, index.html 8247-8248)
        assertEquals(2603777243L, CallingCard.seedOf("Star Quest"))
        val r = CallingCard.Rnd(2603777243L)
        assertEquals(.4327701411675662, r.next(), 1e-15)
        assertEquals(.2884376801084727, r.next(), 1e-15)
        assertEquals(.2444350642617792, r.next(), 1e-15)
        // a name past the basic plane counts each character's first half, as charCodeAt(0) does
        assertEquals(1031082569L, CallingCard.seedOf("Ünïcode 🎮 Saga"))
        assertEquals(.4319115658290684, CallingCard.Rnd(1031082569L).next(), 1e-15)
        assertEquals("You arrive on 5 March. Your hype stands at 7.5/10. Clear your schedule. We are coming to play you.", CallingCard.note(false, "2027-03-05", 7.5, now))
        assertEquals("We have beaten you once before. We are coming back for another run.", CallingCard.note(true, "2020-01-01", 0.0, now))
        assertEquals("You have waited in the queue long enough. Your hype stands at 9/10. We are coming to play you next.", CallingCard.note(false, "", 9.0, now))
    }

    private companion object {
        const val LIB = """{"games":[
          {"id":"a","title":"Star Quest","state":"played","hype":9,"mine":{"story":9},"subs":{"x":1},"why":["story"],"lists":["l1","l2"],"wasI":[{"t":1,"x":"at the boss"}],"genres":"RPG"},
          {"id":"b","title":"Moon Run","state":"played","genres":"Action","desc":"A run on the moon."},
          {"id":"c","title":"Sky Fall","state":"played"},
          {"id":"up","title":"Next Thing","state":"upcoming","date":"2027-03-05"},
          {"id":"comp","title":"Ladder","comp":true,"date":"2020-01-01"}],
          "played":[{"id":"a","real":9,"t":"2026-01-02","again":1,"runs":2},{"id":"a","real":9,"t":"2026-05-02"},{"id":"b","real":8,"t":"2026-02-02"},{"id":"c","real":7,"t":"2026-03-02"}],
          "duels":[{"a":"a","b":"b","w":"a"},{"a":"b","b":"c","w":"b"},{"a":"c","b":"a","w":"a"}],
          "aiWhy":{"up":{"text":"VERDICT: Mixed\n- one","n":3,"k":"a:9|a:9|b:8|c:7"}}}"""
    }
}
