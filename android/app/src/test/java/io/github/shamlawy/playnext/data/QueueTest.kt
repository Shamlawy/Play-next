package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The Queue's maths beyond the golden numbers: orders that must never throw, and the stretch a Queue covers. Made-up games. */
class QueueTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = 1_793_000_000_000L // 2026-10-26 09:33 UTC, 1:33 PM in Dubai

    private fun lib(n: Int): Library {
        // past, coming, empty and unreadable dates mixed, enough games (32+) for TimSort's merge runs
        val dates = listOf("2025-03-04", "2027-01-09", "", "someday", "2024-11-30", "2026-12-01", "Q3", "2023-07-15")
        val games = (0 until n).joinToString(",") { i -> """{"id":"g$i","title":"Game $i","state":"backlog","ord":$i,"date":"${dates[(i * 5 + i / 3) % dates.size]}"}""" }
        return Library(Json.parseToJsonElement("""{"games":[$games]}""").jsonObject)
    }

    @Test fun soonestNeverBreaksTheSortContract() {
        for (n in listOf(8, 33, 64, 200)) {
            val l = lib(n)
            val q = Queue(l, Rank(l, now, zone), now, zone)
            val out = q.sort(l.games, "date")
            assertEquals(n, out.size)
            // coming first (soonest first), then no date, then the past newest first with the unreadable ones last
            val tier = { g: Game -> if (g.date.isEmpty()) 1 else if ((JsDate.utcMidnight(g.date) ?: Long.MIN_VALUE) >= now) 0 else 2 }
            assertEquals(out.map(tier), out.map(tier).sorted())
            val past = out.filter { tier(it) == 2 }
            assertTrue("unreadable dates sit last", past.dropWhile { JsDate.utcMidnight(it.date) != null }.all { JsDate.utcMidnight(it.date) == null })
        }
    }

    @Test fun aQueueCoversItsOwnStretchOnly() {
        val l = lib(4)
        val q = Queue(l, Rank(l, now, zone), now, zone)
        assertTrue(q.covers(now + 60_000, zone))
        assertFalse("past UTC midnight (4 AM in Dubai) the days to go change", q.covers((Math.floorDiv(now, JsDate.DAY) + 1) * JsDate.DAY, zone))
        assertFalse("another zone", q.covers(now, ZoneId.of("UTC")))
        q.warm()
        assertSame("On deck's list comes back the same while the Queue lives", q.deck("all"), q.deck("all"))
    }
}
