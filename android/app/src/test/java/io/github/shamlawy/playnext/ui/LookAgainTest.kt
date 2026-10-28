package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

/**
 * "Try again" and "Check for a newer copy" always show that they did something (bugs 4, L184): the label holds for a
 * moment even when the answer is instant or the same, and the line says when it last looked. These failed before their
 * fixes. Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class LookAgainTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val lib = Library(
        Json.parseToJsonElement(
            """{"theme":"midnight","games":[{"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1}],"duels":[],"played":[]}""",
        ).jsonObject,
    )
    private val offline = "No connection. Try again when you're online."

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    @After
    fun tearDown() {
        Store.prefs.edit().remove("copyLooked").apply()
    }

    private fun up(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** The same error coming back (the state the screen sees doesn't change) still shows Trying again, then when. */
    @Test fun tryAgainShowsItTriedWhenTheSameErrorComesBack() {
        Store.prefs.edit().putLong("copyLooked", now - 60_000L).apply()
        var st by mutableStateOf<Games.State>(Games.State.Fetching(offline))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(Nav("home"), st, go = {}) } }
        assertTrue("when it last tried", up("Tried at 7:59 AM."))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Try again").performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(16)
        assertTrue("the tap shows at once", up("Trying again…"))
        // Games answers within a frame, with the very same line
        st = Games.State.Fetching(offline, checking = true)
        st = Games.State.Fetching(offline)
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(400)
        assertTrue("still showing after 400 ms", up("Trying again…"))
        rule.mainClock.advanceTimeBy(400)
        assertTrue("back after the hold", up("Try again") && up(offline))
    }

    /** Settings › Check for a newer copy: when it looked, and a label that holds when the answer is instant. */
    @Test fun checkForANewerCopySaysWhenItLooked() {
        Store.prefs.edit().putLong("copyLooked", now - 60_000L).apply()
        val ready = Games.State.Ready(lib, copyAt = now - 3_600_000L)
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(Nav("home", listOf("settings")), ready, go = {}) } }
        rule.mainClock.advanceTimeBy(400)
        assertTrue(up("Checked at 7:59 AM: this is the newest copy."))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Check for a newer copy").performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(16)
        assertTrue("the tap shows at once", up("Looking for a newer copy…"))
        rule.mainClock.advanceTimeBy(400)
        assertTrue("still showing after 400 ms", up("Looking for a newer copy…"))
        rule.mainClock.advanceTimeBy(400)
        assertTrue("back after the hold", up("Check for a newer copy"))
    }

    /** A failed look says when it tried, under the reason. */
    @Test fun aFailedCheckSaysWhenItTried() {
        Store.prefs.edit().putLong("copyLooked", now - 60_000L).apply()
        val ready = Games.State.Ready(lib, copyAt = now - 3_600_000L, note = "No connection, so this is the copy from last time.")
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(Nav("home", listOf("settings")), ready, go = {}) } }
        rule.mainClock.advanceTimeBy(400)
        assertTrue(up("Tried at 7:59 AM."))
        assertEquals(0, rule.onAllNodesWithText("Checked at 7:59 AM: this is the newest copy.").fetchSemanticsNodes().size)
    }
}
