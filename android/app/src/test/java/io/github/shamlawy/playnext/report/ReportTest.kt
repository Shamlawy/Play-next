package io.github.shamlawy.playnext.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRulesTest {
    /** Made with index.html's own bugHash (node, Oct 26), so the app and the web hash text the same way. */
    @Test
    fun hashMatchesTheWebApp() {
        assertEquals("811c9dc5", ReportRules.hash(""))
        assertEquals("4f9f2cab", ReportRules.hash("hello"))
        assertEquals("e7340868", ReportRules.hash("android|error|TypeError: x is not a function|core.js"))
        assertEquals("6287720f", ReportRules.hash("Café ☕ ü"))
    }

    @Test
    fun oneProblemOneSignature() {
        val a = ReportRules.sig("error", "Index 3 out of bounds for length 2", "Home.kt:120", "home", 0)
        val b = ReportRules.sig("error", "Index 7 out of bounds for length 5", "Home.kt:98", "probe", 99)
        assertEquals("numbers, the line number and (for errors) the screen don't split a problem", a, b)
        assertNotEquals(a, ReportRules.sig("stuck", "Index 3 out of bounds for length 2", "Home.kt:120", "home", 0))
        assertTrue(Regex("^[0-9a-f]{8}$").matches(a))
    }

    @Test
    fun slowAndLayoutAreSplitByScreen() {
        assertNotEquals(ReportRules.sig("slow", "A tap took 300ms", "", "home", 0), ReportRules.sig("slow", "A tap took 300ms", "", "probe", 0))
    }

    @Test
    fun userReportsNeverMerge() {
        assertNotEquals(ReportRules.sig("user", "It froze", "", "home", 1), ReportRules.sig("user", "It froze", "", "home", 2))
    }

    @Test
    fun appSignaturesNeverMeetTheWebApps() {
        // The web app's sig is the same hash without "android|" in front.
        assertNotEquals(ReportRules.hash("error|boom|x.js"), ReportRules.sig("error", "boom", "x.js", "", 0))
    }

    @Test
    fun logOnlyKinds() {
        assertTrue("probe" in ReportRules.LOG_ONLY)
        assertTrue("ran" in ReportRules.LOG_ONLY)
        assertFalse("error" in ReportRules.LOG_ONLY)
    }
}

class ScrubTest {
    @Test
    fun aPlantedTitleNeverLeaves() {
        val t = Scrub.text("Crash while opening Starfall Odyssey II on the queue", names = listOf("Starfall Odyssey II", "x"))
        assertFalse(t.contains("Starfall"))
        assertTrue(t.contains("‹game›"))
    }

    @Test
    fun keysTokensAndTheRestoreCodeAreRemoved() {
        val tok = "0123456789abcdef".repeat(4)
        val id = "abcdef0123456789abcdef01"
        val t = Scrub.text(
            "GET /vault/get?id=$id&tok=secret1 Bearer $tok code PN-AB12-CD34-EF56-GH78-JK90 raw AB12CD34EF56GH78JK90 rawg=1234567",
            secrets = listOf("1234567"),
        )
        for (bad in listOf(tok, id, "secret1", "AB12-CD34", "AB12CD34EF56GH78JK90", "1234567")) assertFalse("$bad left in: $t", t.contains(bad))
        assertTrue(t.contains("PN-…"))
    }

    @Test
    fun plainWordsStay() {
        assertEquals("The PNG on Home didn't load", Scrub.text("The PNG on Home didn't load"))
    }

    @Test
    fun longTextIsCut() {
        assertEquals(2000, Scrub.text("a".repeat(5000)).length)
    }
}
