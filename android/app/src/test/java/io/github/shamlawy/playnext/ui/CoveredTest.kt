package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
import kotlin.math.abs

/** What a fully grown game page covers stops drawing, and draws again as the page closes. Made-up games only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class CoveredTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val lib = Library(
        Json.parseToJsonElement(
            """{"theme":"midnight","games":[{"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8"}],"duels":[],"played":[]}""",
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    /** How different two pictures of the screen are (mean channel difference over a grid). */
    private fun diff(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap): Float {
        var d = 0f
        var n = 0
        for (x in 0 until a.width step 23) for (y in 0 until a.height step 23) {
            val p = a[x, y]
            val q = b[x, y]
            d += abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue)
            n++
        }
        return d / n
    }

    @Test fun theTabDrawsAgainWhenThePageCloses() {
        var nav by mutableStateOf(Nav("games"))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib)) { Shell(nav, Games.State.Ready(lib, 0), go = { nav = it }) } }
        rule.waitForIdle()
        val before = rule.onRoot().captureToImage().toPixelMap()
        rule.runOnIdle { nav = nav.open("game:a") }
        rule.waitForIdle()
        val open = rule.onRoot().captureToImage().toPixelMap()
        assertTrue("the page didn't open", diff(before, open) > .05f)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), nav.stack)
        assertTrue("the tab stayed hidden after the page closed", diff(before, rule.onRoot().captureToImage().toPixelMap()) < .01f)
    }

    /** Back while the page is still growing: what's under it never vanishes while the page shrinks off it. */
    @Test fun backDuringTheGrowNeverHidesTheTab() {
        var nav by mutableStateOf(Nav("games"))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib)) { Shell(nav, Games.State.Ready(lib, 0), go = { nav = it }) } }
        rule.waitForIdle()
        val before = rule.onRoot().captureToImage().toPixelMap()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { nav = nav.open("game:a") }
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription("Back").performClick()
        Snapshot.sendApplyNotifications()
        // past the grow's end, the shrink nearly done: the tab shows through the fading page, never a blank screen
        rule.mainClock.advanceTimeBy(176)
        val mid = (0 until 6).map { rule.mainClock.advanceTimeBy(16); diff(before, rule.onRoot().captureToImage().toPixelMap()) }
        // each frame closer to the tab as the page fades (before the guard: 0.09, then 0.15 as the tab blanked out)
        assertTrue("the tab blanked out under the shrinking page: $mid", mid.zipWithNext().all { (a, b) -> b <= a + .01f })
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(emptyList<String>(), nav.stack)
        assertTrue("the tab stayed hidden after the page closed", diff(before, rule.onRoot().captureToImage().toPixelMap()) < .01f)
    }
}
