package io.github.shamlawy.playnext.data

import androidx.compose.ui.graphics.Color
import io.github.shamlawy.playnext.ui.Look
import io.github.shamlawy.playnext.ui.Nav
import io.github.shamlawy.playnext.ui.asOfLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The parts of the restore the golden file doesn't reach, and the app's own rules around it. */
class BackupTest {
    private val uae = ZoneId.of("Asia/Dubai")

    @Test
    fun anEmptyBackupGetsNoMadeUpGames() {
        val s = Backup.load(Json.parseToJsonElement("{\"games\":[]}").jsonObject, 0, uae)
        assertEquals(0, Library(s).games.size)
        assertTrue("the lists exist", s.keys.containsAll(listOf("games", "duels", "played")))
    }

    @Test
    fun keepsTheBackupWordForWord() {
        // numbers, key order and fields the app doesn't know come back exactly as they went in
        val raw = "{\"games\":[{\"id\":\"g\",\"title\":\"T\",\"hours\":12.50,\"zz\":{\"a\":[1,2]},\"big\":1761000000000}],\"played\":[],\"duels\":[],\"x\":1}"
        val s = Backup.load(Backup.apply(Backup.read(raw).data!!), 0, uae)
        assertEquals("{\"id\":\"g\",\"title\":\"T\",\"hours\":12.50,\"zz\":{\"a\":[1,2]},\"big\":1761000000000}", s["games"].toString().removeSurrounding("[", "]"))
        assertEquals(listOf("games", "played", "duels", "x"), s.keys.toList())
    }

    @Test
    fun themesComeFromTheWebTables() {
        val lib = Library(Json.parseToJsonElement("{\"games\":[],\"theme\":\"emerald\",\"bgBase\":\"#0E1A3D\",\"bgGrad\":45}").jsonObject)
        val look = Look.of(lib)
        assertEquals(Color(0xFF3DDC97), look.volt)
        assertEquals(45, look.bgGrad)
        assertEquals("an unknown theme is Midnight", Look().volt, Look.of(Library(Json.parseToJsonElement("{\"games\":[],\"theme\":\"x\"}").jsonObject)).volt)
        // chamTone, checked against index.html's: chamTone("#FF5470", .64) and .42
        assertEquals(0xFFFA4C69L, Look.chamTone(0xFFFF5470L, .64))
        assertEquals(0xFFD10527L, Look.chamTone(0xFFFF5470L, .42))
    }

    @Test
    fun backGoesTheWayTheOwnerPicked() {
        // 1a: what's open first, then the last tab (each once, newest first), then out of the app
        var n = Nav().go("games").go("plan").go("games")
        assertEquals("games", n.tab)
        assertEquals(listOf("home", "plan"), n.history)
        n = n.open("settings")
        assertTrue(n.canGoBack)
        n = n.back()
        assertEquals("games", n.tab)
        assertEquals(null, n.overlay)
        n = n.back()
        assertEquals("plan", n.tab)
        n = n.back()
        assertEquals("home", n.tab)
        assertEquals(false, n.canGoBack)
    }

    @Test
    fun saysWhenTheCopyWasMade() {
        val now = java.time.ZonedDateTime.of(2026, 10, 27, 14, 30, 0, 0, uae).toInstant().toEpochMilli()
        assertEquals("Your games as of 2:05 PM", asOfLine(now - 25 * 60_000, now, uae))
        assertEquals("Your games as of Oct 25, 9:00 AM", asOfLine(java.time.ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, uae).toInstant().toEpochMilli(), now, uae))
    }
}
