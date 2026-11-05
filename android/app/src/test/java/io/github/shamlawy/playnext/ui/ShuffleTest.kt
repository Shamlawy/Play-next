package io.github.shamlawy.playnext.ui

import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

/** Shuffle's tap and the Boss's HP (android-v20; the numbers against the web's are in GoldenTest). */
class ShuffleTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = 1_793_000_000_000L // 2026-10-26, 1:33 PM in Dubai

    private fun queue(games: String, extra: String = ""): Queue {
        val l = Library(Json.parseToJsonElement("""{"games":[$games]$extra}""").jsonObject)
        return Queue(l, Rank(l, now, zone), now, zone)
    }

    // two out (backlog), one coming (a date next year), one finished
    private val four = """{"id":"a","title":"Alpha","state":"backlog","hours":12},{"id":"b","title":"Beta","state":"backlog"},""" +
        """{"id":"c","title":"Gamma","date":"2027-03-01"},{"id":"d","title":"Delta","state":"played","hours":30}"""

    @Test
    fun aTapPicksAGameThatsOutAndNeverTheSameTwiceRunning() {
        val q = queue(four)
        val pick = QueuePick(q.lib)
        val seen = ArrayList<String>()
        repeat(12) {
            val g = pick.shuffle(q, Random(it))!!
            assertTrue("only games that are out", g.id in setOf("a", "b"))
            if (seen.isNotEmpty()) assertNotEquals("never twice running", seen.last(), g.id)
            seen += g.id
            assertEquals(g.id, pick.lit!!.id)
            assertEquals("Tonight: " + g.title, pick.note!!.title)
        }
        // each pick of the same game is a new one, so its row lights again
        assertEquals(12, pick.lit!!.n)
    }

    @Test
    fun aPickTheTabDoesntShowGoesBackToAll() {
        val q = queue(four)
        val pick = QueuePick(q.lib).apply { tab = "soon" }
        pick.shuffle(q, Random(3))
        assertEquals("all", pick.tab)
        pick.tab = "out"
        pick.shuffle(q, Random(4))
        assertEquals("a game that's out stays under Out", "out", pick.tab)
    }

    @Test
    fun nothingToPickSaysSo() {
        // a dropped game is never picked (played ones are those with a played record)
        val q = queue("""{"id":"d","title":"Delta","state":"dropped"}""")
        val pick = QueuePick(q.lib)
        assertNull(pick.shuffle(q))
        assertNull(pick.lit)
        assertEquals("Nothing to pick from yet", pick.note!!.title)
    }

    @Test
    fun onlyTheComingOnesWhenNoneIsOut() {
        val q = queue("""{"id":"c","title":"Gamma","date":"2027-03-01"},{"id":"e","title":"Eps","date":"2027-04-01"}""")
        assertEquals(listOf("c", "e"), q.shufflePool(null).map { it.id })
        assertEquals(listOf("e"), q.shufflePool("c").map { it.id })
        // the only one left is picked again
        assertEquals(listOf("c"), queue("""{"id":"c","title":"Gamma","date":"2027-03-01"}""").shufflePool("c").map { it.id })
    }

    @Test
    fun theBossCountsTheBacklogsHours() {
        // 12 + 20 (no hours) for the two out; the coming one and the finished one don't count
        assertEquals(32.0 to 32.0, queue(four).boss)
        // the bar's top is the most it has been
        assertEquals(32.0 to 90.0, queue(four, ""","fxBossMax":90""").boss)
        assertEquals(0.0 to 1.0, queue("""{"id":"d","title":"Delta","state":"played"}""").boss)
    }
}
