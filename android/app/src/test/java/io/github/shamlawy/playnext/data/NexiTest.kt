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
 * Nexi's numbers (`pnNext` 12099, `pnBubBadge` 12103, `pnBubShow`'s brief and release-day lines 12475, `pnEvo` 14017),
 * on made-up games, at noon in Dubai.
 */
class NexiTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = Instant.parse("2026-11-07T08:00:00Z").toEpochMilli()

    private fun nexi(games: String, played: String = "[]"): Nexi {
        val lib = Library(Json.parseToJsonElement("""{"games":[$games],"played":$played,"duels":[]}""").jsonObject)
        return Nexi(lib, Rank(lib, now, zone), now, zone)
    }

    @Test fun theNextReleaseAndItsCountdown() {
        val n = nexi(
            """{"id":"a","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14"},
               {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-11-10","hours":20},
               {"id":"c","title":"Quiet Harbour","state":"backlog","date":"2025-01-01","hours":12.4}""",
        )
        assertEquals("Copper Meadow", n.next?.first?.title)
        assertEquals("its midnight is 2 days and 12 hours away", 3, n.next?.second)
        assertEquals("3d", n.badge("count"))
        assertEquals("the default is the countdown", "3d", n.badge(""))
        assertEquals("every unplayed game's hours", "32h", n.badge("hours"))
        assertEquals("off", "", n.badge("off"))
        assertEquals("his own records come later", "", n.badge("streak"))
        assertFalse(n.party)
        assertNull(n.partyLine)
    }

    @Test fun aPlayedGameIsNeverNext() {
        val n = nexi("""{"id":"a","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14"}""", """[{"id":"a","real":8}]""")
        assertNull(n.next)
        assertEquals("", n.badge("count"))
        assertEquals("Nothing on the calendar. Backlog time", n.brief(""))
    }

    @Test fun theDailyBrief() {
        val week = nexi(
            """{"id":"a","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14"},
               {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-11-10"},
               {"id":"c","title":"Starfall Lantern","state":"upcoming","date":"2026-12-24"}""",
        )
        assertEquals("Today's brief, Sam: 2 releases this week, next is Copper Meadow in 3d", week.brief("Sam"))
        assertEquals("Today's brief: 2 releases this week, next is Copper Meadow in 3d", week.brief(""))
        val later = nexi("""{"id":"c","title":"Starfall Lantern","state":"upcoming","date":"2026-12-24"}""")
        assertEquals("Nothing out this week. Next up: Starfall Lantern in 47 days. Backlog time", later.brief("Sam"))
    }

    @Test fun aReleaseDayIsAParty() {
        val n = nexi(
            """{"id":"a","title":"Ninefold Gate","state":"upcoming","date":"2026-11-07"},
               {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-11-07"},
               {"id":"c","title":"Starfall Lantern","state":"upcoming","date":"2026-12-24"}""",
        )
        assertTrue(n.party)
        assertEquals("TODAY", n.badge("count"))
        assertEquals("Ninefold Gate is OUT today (plus 1 more)! Party time!", n.partyLine)
        assertTrue("today counts in this week's brief", n.brief("").startsWith("Today's brief: 2 releases this week"))
    }

    @Test fun hisRingGrowsWithScoredFinishes() {
        val g = """{"id":"a","title":"Ninefold Gate","state":"played"}"""
        assertEquals(1, nexi(g).stage)
        val five = (1..5).joinToString(",") { """{"id":"p$it","real":7}""" }
        assertEquals(2, nexi(g, "[$five]").stage)
        val unscored = (1..12).joinToString(",") { """{"id":"u$it","dk":1,"real":7}""" }
        assertEquals("'don't remember' doesn't count", 1, nexi(g, "[$unscored]").stage)
        val lots = (1..40).joinToString(",") { """{"id":"p$it","real":7}""" }
        assertEquals(5, nexi(g, "[$lots]").stage)
    }
}
