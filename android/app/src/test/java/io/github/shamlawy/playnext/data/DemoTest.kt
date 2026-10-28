package io.github.shamlawy.playnext.data

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The made-up backup CI's emulators walk the screens with (Demo): it opens like a real one and never on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DemoTest {
    private val now = Instant.parse("2026-10-28T08:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Dubai")

    @Test
    fun opensLikeARealBackup() {
        val rb = Backup.read(Demo.text(now, zone))
        assertTrue(rb.msg, rb.ok)
        val lib = Library(Backup.load(Backup.apply(rb.data!!), now, zone))
        assertEquals(40, lib.games.size)
        assertEquals("every state", setOf("backlog", "playing", "upcoming", "played"), lib.games.map { it.state }.toSet())
        assertEquals("titles don't repeat", 40, lib.games.map { it.title }.toSet().size)
        assertTrue("some coming this week", lib.games.any { it.isUpcoming(now) && it.date.isNotEmpty() && it.date <= "2026-11-04" })
        assertEquals(14, lib.played.size)
        assertEquals(24, lib.duels.size)
        assertTrue("a duel is between two games", lib.duels.all { it.a != it.b })
        assertTrue("a duel's winner is one of its two", lib.duels.all { it.w == it.a || it.w == it.b })
    }

    /** "What should I play?" opens the Queue card's "Up next" (worked out with your games) as On deck's first game. */
    @Test
    fun upNextIsOnDecksFirst() {
        val lib = Library(Backup.load(Backup.apply(Backup.read(Demo.text(now, zone)).data!!), now, zone))
        val first = Rank(lib, now, zone).onDeck(1).first().id
        assertEquals(first, Shx.all(lib, now, zone)["queue"]?.line?.g?.id)
    }

    @Test
    fun neverOnAPhoneEvenWithTheSwitch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putInt(app.contentResolver, Demo.KEY, 1)
        assertFalse("Robolectric isn't an emulator, as the phone isn't", Demo.on(app))
    }

    /** The walk finds a game's cover by these first words (tools/android-smoke.sh and the Baseline Profile's walk). */
    @Test
    fun theWalksKnowTheTitles() {
        val words = "(" + Demo.FIRST.joinToString("|") + ") [A-Za-z0-9 ]+"
        assertTrue(Demo.text(now, zone).let { t -> Regex("\"title\":\"([^\"]+)\"").findAll(t).all { Regex(words).matches(it.groupValues[1]) } })
        for (f in listOf("../../tools/android-smoke.sh", "../baselineprofile/src/main/java/io/github/shamlawy/playnext/baselineprofile/BaselineProfileGenerator.kt")) {
            assertTrue("$f names the made-up titles as $words", File(f).readText().contains(words))
        }
    }
}
