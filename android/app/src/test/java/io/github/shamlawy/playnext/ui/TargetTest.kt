package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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

/**
 * The top bar's views are drawn as 40 tall pills but each is a 48 touch, the bar's whole height: a tap on the bar's
 * rim just above or below a pill is that view's (Perfect run, touch 8; L245: a pointer input on a padded bar took
 * those taps before Compose's own widening could).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class TargetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun lib(extra: String) = Library(
        Json.parseToJsonElement(
            """{"theme":"midnight"$extra,"games":[{"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1}],"duels":[],"played":[]}""",
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    /** A tap [dy] px inside the top (positive) or the foot (negative) of the node labelled [label]; its height. */
    private fun tapInside(label: String, dy: Float): Float {
        val b = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot // the bar is at the top
        val y = if (dy > 0) b.top + dy else b.bottom + dy
        rule.onRoot().performTouchInput { click(Offset(b.center.x, y)) }
        rule.waitForIdle()
        return b.height
    }

    private fun check(extra: String, below: Boolean) {
        val l = lib(extra)
        var nav by mutableStateOf(Nav("games"))
        rule.setContent { Frame(Look.of(l), Prefs.of(l)) { Shell(nav, Games.State.Ready(l, 0), go = { nav = it }) } }
        rule.waitForIdle()
        val px = 3f // xxhdpi
        // 2dp in from the touch's edge: 2dp off the drawn pill, on the bar's rim
        val h = tapInside("Played", if (below) -2 * px else 2 * px)
        assertTrue("the target is ${h / px}dp tall", h / px >= 48f)
        assertEquals("played", nav.views["games"])
    }

    @Test fun theBarsViewsTakeATapJustOffBelow() = check("", below = true)

    @Test fun theBarsViewsTakeATapJustOffAbove() = check("", below = false)
}
