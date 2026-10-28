package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.report.Crumbs
import io.github.shamlawy.playnext.report.ReportRules
import io.github.shamlawy.playnext.report.Scrub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReportRulesTest {
    /** Values from index.html's own bugHash (node, Oct 26 2026): the app's signatures are made the web app's way. */
    @Test
    fun hashIsTheWebAppsFnv() {
        assertEquals("811c9dc5", ReportRules.hash(""))
        assertEquals("e40c292c", ReportRules.hash("a"))
        assertEquals("b0bd20b9", ReportRules.hash("android|error|TypeError: x is #|f"))
        assertEquals("1df5d457", ReportRules.hash("héllo 🎮")) // UTF-16 units, like charCodeAt
    }

    @Test
    fun eachVersionsRanReportIsItsOwn() {
        // the digits alone would merge them, and firstToday dropped the second release of a day
        val a = ReportRules.sig("ran", "Android app v9 ran", "", "home", 1, apart = "v9")
        val b = ReportRules.sig("ran", "Android app v10 ran", "", "home", 1, apart = "v10")
        assertNotEquals(a, b)
        // no apart, no change: every other report keeps the signature it has in the helper's store
        assertEquals(ReportRules.sig("error", "x", "", "home", 1), ReportRules.sig("error", "x", "", "home", 1, apart = ""))
    }

    @Test
    fun oneProblemOneSignature() {
        val a = ReportRules.sig("error", "Index 3 out of bounds for length 2", "Queue.kt:41:7", "queue", 1)
        val b = ReportRules.sig("error", "Index 9 out of bounds for length 8", "Queue.kt:88", "home", 2)
        assertEquals("numbers, line numbers and the screen don't split an error", a, b)
        assertTrue(Regex("[0-9a-f]{8}").matches(a))
        // a stutter is per screen, a hand-written report is always its own
        assertNotEquals(ReportRules.sig("slow", "A 120ms stutter on home", "", "home", 1), ReportRules.sig("slow", "A 120ms stutter on home", "", "probe", 1))
        assertNotEquals(ReportRules.sig("user", "x", "", "home", 1), ReportRules.sig("user", "x", "", "home", 2))
        // never the same as the web app's signature for the same words
        assertNotEquals(ReportRules.hash("error|m|a"), ReportRules.sig("error", "m", "a", "", 0))
    }

    @Test
    fun logOnlyKindsMatchBugsync() {
        val js = Repo.text(".github/scripts/bugsync.mjs")
        val set = Regex("""const LOG_ONLY = new Set\(\[([^\]]*)]\)""").find(js)!!.groupValues[1]
            .split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }.toSet()
        assertEquals(set, ReportRules.LOG_ONLY)
    }

    @Test
    fun scrubKeepsTitlesKeysAndTheCodeOnThePhone() {
        val title = "Made Up Quest Seven"
        val code = "PN-ABCDE-FGHJK-LMNPQ-RSTUV"
        val tok = "0123456789abcdef".repeat(4)
        val id = "abcdef0123456789abcdef01"
        val raw = "Crash opening $title (made up quest seven) with $code tok=$tok id $id " +
            "https://x.dev/a?key=SECRETKEY123&b=1 rawg 9f8e7d6c5b4a3f2e1d0c and data:image/png;base64," + "A".repeat(60)
        val out = Scrub.text(raw, names = listOf(title, "ab"), secrets = listOf("9f8e7d6c5b4a3f2e1d0c"))
        for (leak in listOf(title, "made up quest seven", code, "ABCDE-FGHJK", tok, id, "SECRETKEY123", "9f8e7d6c5b4a3f2e1d0c", "AAAAAAAAAA")) {
            assertFalse("'$leak' got through: $out", out.contains(leak, ignoreCase = true))
        }
        assertTrue(out.contains("‹game›"))
        assertTrue("short names are left alone", Scrub.text("tab", names = listOf("ab")) == "tab")
        assertTrue(Scrub.text("x".repeat(5000)).length <= 2000)
    }

    /** The library line: sizes only, never a name, and "out" counts games that are out and not finished (made-up games). */
    @Test
    fun libraryLineCountsWithoutNames() {
        val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
        val pic = "data:image/png;base64," + "A".repeat(4096)
        val lib = Library(
            Json.parseToJsonElement(
                """{"games":[
                  {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","img":"$pic"},
                  {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02"},
                  {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14"},
                  {"id":"d","title":"Quiet Orbit","state":"upcoming","date":""},
                  {"id":"e","title":"Paper Kingdom","state":"played","date":"2025-12-01"}],
                 "lists":[{"n":"Couch co-op"}],"played":[],"duels":[]}""",
            ).jsonObject,
        )
        val line = ReportRules.libLine(lib, now)
        assertEquals("5 games (2 out, 2 coming), 1 lists, 1 uploaded pictures (3KB), longest title 16 chars", line)
        assertFalse(line.contains("Lantern") || line.contains("Couch"))
    }

    @Test
    fun crumbsKeepTheLast14InOrder() {
        Crumbs.clear()
        repeat(16) { Crumbs.add("tap [tab] on games/queue $it", 1_700_000_000_000L + it * 1000L, ZoneId.of("UTC")) }
        assertEquals(10, Crumbs.last().size)
        assertEquals("22:13:35 tap [tab] on games/queue 15", Crumbs.last().last())
        assertEquals("the oldest two went", 14, Crumbs.last(99).size)
        assertEquals("22:13:22 tap [tab] on games/queue 2", Crumbs.last(99).first())
        Crumbs.clear()
    }
}
