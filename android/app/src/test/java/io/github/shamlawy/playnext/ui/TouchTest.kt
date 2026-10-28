package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.math.abs

/**
 * Touch, gestures and back, with real touches and the activity's own back (Phase 0's Perfect run found each of these
 * by hand; they failed here before their fixes). Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class TouchTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    private val lib = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"hours":22,"added":"2026-10-20"},
               {"id":"c","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":3,"colour":"#2E9E6A","genres":["Action"],"hours":12,"added":"2026-06-14"}
             ],"duels":[],"played":[]}
            """.trimIndent(),
        ).jsonObject,
    )
    private val ready = Games.State.Ready(lib, copyAt = now - 3_600_000L)

    @Suppress("UNCHECKED_CAST")
    private val games = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(null) as MutableStateFlow<Games.State>

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        games.value = Games.State.Starting
    }

    private fun shell(nav: () -> Nav, go: (Nav) -> Unit) =
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav(), ready, go = go) } }

    private fun restoreUp() = rule.onAllNodesWithText("Bring your games").fetchSemanticsNodes().size

    /** Settings › "Use a different restore code", then back: the restore screen goes and Settings shows again. */
    @Test fun backAfterADifferentRestoreCode() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        games.value = ready
        rule.setContent { PlayNextRoot(null) {} }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Use a different restore code").performClick()
        rule.waitUntil(5_000) { games.value is Games.State.NoCode }
        rule.waitForIdle()
        assertEquals("the restore screen is up", 1, restoreUp())
        val d = rule.activity.onBackPressedDispatcher
        rule.runOnIdle { d.onBackPressed() }
        rule.waitForIdle()
        assertEquals("back closed the restore screen", 0, restoreUp())
        assertTrue("Settings is back, and back still closes it before leaving the app", d.hasEnabledCallbacks())
    }

    /** The same, with "Not now": one tap. */
    @Test fun notNowAfterADifferentRestoreCode() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        games.value = ready
        rule.setContent { PlayNextRoot(null) {} }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Use a different restore code").performClick()
        rule.waitUntil(5_000) { games.value is Games.State.NoCode }
        rule.waitForIdle()
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        assertEquals("one Not now closed the restore screen", 0, restoreUp())
    }

    /** "Swipe-down closes any sheet" (Decisions, Oct 26): a drag down from Settings' title. */
    @Test fun settingsClosesOnASwipeDown() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("home", listOf("settings")))
        shell({ nav }) { nav = it }
        val t = rule.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { swipe(Offset(t.center.x, t.top + 10f), Offset(t.center.x, t.top + 900f), 250) }
        rule.waitForIdle()
        assertTrue("a swipe down left Settings open: ${nav.stack}", nav.stack.isEmpty())
    }

    /** A short drag down (a fidget) springs back and leaves Settings open. */
    @Test fun aShortDragDownKeepsSettings() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("home", listOf("settings")))
        shell({ nav }) { nav = it }
        val t = rule.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { swipe(Offset(t.center.x, t.top + 10f), Offset(t.center.x, t.top + 60f), 600) }
        rule.waitForIdle()
        assertEquals(listOf("settings"), nav.stack)
    }

    /** A drag that starts on the dimmed space around Settings isn't a tap on it. */
    @Test fun aDragOnTheScrimKeepsSettings() {
        RuntimeEnvironment.setQualifiers("w859dp-h1000dp-xxhdpi")
        var nav by mutableStateOf(Nav("home", listOf("settings")))
        shell({ nav }) { nav = it }
        val t = rule.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        // left of the sheet (it's centred and at most 620 wide on an 859 screen, so 120 of scrim each side)
        val x = 30f
        assertTrue(x < t.left - 60f)
        rule.onRoot().performTouchInput { swipe(Offset(x, 500f), Offset(x, 900f), 300) }
        rule.waitForIdle()
        assertEquals("a drag on the scrim closed Settings", listOf("settings"), nav.stack)
        // a plain tap there still closes it
        rule.onRoot().performTouchInput { click(Offset(x, 500f)) }
        rule.waitForIdle()
        assertTrue(nav.stack.isEmpty())
    }

    /** Unfolded: two quick taps on the rail's Settings open it once (the second lands on the scrim and is let go). */
    @Test fun aDoubleTapOnTheRailsSettings() {
        RuntimeEnvironment.setQualifiers("w859dp-h1000dp-xxhdpi")
        var nav by mutableStateOf(Nav("home"))
        shell({ nav }) { nav = it }
        val g = rule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { click(g.center) }
        rule.mainClock.advanceTimeBy(120)
        rule.onRoot().performTouchInput { click(g.center) }
        rule.waitForIdle()
        assertEquals("a double tap on the rail's Settings opened and closed it", listOf("settings"), nav.stack)
    }

    /** Folded: two quick taps on Settings' Close close it once (the second lands on nothing, not on what was under it). */
    @Test fun aDoubleTapOnClose() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("home", listOf("settings")))
        shell({ nav }) { nav = it }
        rule.onNodeWithText("Close").performScrollTo()
        val done = rule.onNodeWithText("Close").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { click(done.center) }
        rule.mainClock.advanceTimeBy(120)
        rule.onRoot().performTouchInput { click(done.center) }
        rule.waitForIdle()
        assertTrue("a double tap on Close closed Settings and opened something under it: ${nav.stack}", nav.stack.isEmpty())
    }

    /** The pill's lit segment: a second pick while it still slides carries on from where it's drawn, no jump. */
    @Test fun thePillsSegmentNeverJumps() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("games"))
        val look = Look.of(lib)
        rule.setContent { Frame(look, Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.waitForIdle()
        val volt = look.volt
        fun btn(label: String) = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().minBy { it.size.height }.boundsInRoot
        val labels = Prefs.of(lib).views("games").map { it.second }
        val b0 = btn(labels[0])
        val b1 = btn(labels[1])
        val b2 = btn(labels[2])
        fun segLeft(): Int {
            val px = rule.onRoot().captureToImage().toPixelMap()
            val y = b1.center.y.toInt()
            for (x in (b0.left.toInt() - 30).coerceAtLeast(0) until (b2.right.toInt() + 30).coerceAtMost(px.width - 1)) {
                val c = px[x, y]
                if (abs(c.red - volt.red) < .12f && abs(c.green - volt.green) < .12f && abs(c.blue - volt.blue) < .12f) return x
            }
            return -1
        }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { click(b1.center) }
        Snapshot.sendApplyNotifications()
        val trail = mutableListOf<Int>()
        repeat(6) { rule.mainClock.advanceTimeBy(16); trail += segLeft() }
        rule.onRoot().performTouchInput { click(b2.center) }
        Snapshot.sendApplyNotifications()
        val trail2 = mutableListOf<Int>()
        repeat(4) { rule.mainClock.advanceTimeBy(16); trail2 += segLeft() }
        rule.mainClock.autoAdvance = true
        assertTrue("the segment jumped from ${trail.last()} to ${trail2.first()} ($trail then $trail2)", abs(trail2.first() - trail.last()) < 40)
    }
}
