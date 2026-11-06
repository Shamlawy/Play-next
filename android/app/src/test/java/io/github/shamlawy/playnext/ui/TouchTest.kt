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
        // past the settle that follows opening it (a double tap's second tap lands on nothing)
        rule.mainClock.advanceTimeBy(400)
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
        // past the settle that follows opening it (a double tap's second tap lands on nothing)
        rule.mainClock.advanceTimeBy(400)
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

    /**
     * Option B (Nov 6), unfolded: no pill at the foot; the open tab's views are words in the rail under its icon, the open
     * one selected, and a tap on another picks it.
     */
    @Test fun theRailListsTheOpenTabsViews() {
        RuntimeEnvironment.setQualifiers("w859dp-h1000dp-xxhdpi")
        var nav by mutableStateOf(Nav("games"))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val games = rule.onNodeWithContentDescription("Games").fetchSemanticsNode().boundsInRoot
        val labels = Prefs.of(lib).views("games").map { it.second }
        for (l in labels) {
            val b = rule.onAllNodesWithContentDescription(l).fetchSemanticsNodes().single().boundsInRoot
            assertTrue("$l in the rail on the right ($b)", b.left > root.right * .8f)
            assertTrue("$l under the Games icon ($b, $games)", b.top >= games.bottom - 1f)
        }
        val px = 3f
        assertTrue("the rail is wide enough for its words", rule.onNodeWithContentDescription(labels[1]).fetchSemanticsNode().boundsInRoot.width / px >= 100f)
        rule.onNodeWithContentDescription(labels[1]).performClick()
        rule.waitForIdle()
        assertEquals(Prefs.of(lib).views("games")[1].first, nav.views["games"])
        // Plan's views follow Plan's icon
        nav = Nav("plan")
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(labels[1]).fetchSemanticsNodes().size)
        val plan = Prefs.of(lib).views("plan").first().second
        assertEquals(1, rule.onAllNodesWithContentDescription(plan).fetchSemanticsNodes().size)
    }
}
