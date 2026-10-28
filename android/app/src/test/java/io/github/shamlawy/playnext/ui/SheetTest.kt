package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** The Settings sheet: taps on it are its own, and TalkBack reads it part by part. Made-up games only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class SheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val lib = Library(
        Json.parseToJsonElement(
            """{"theme":"midnight","games":[{"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1}],"duels":[],"played":[]}""",
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    /**
     * Before, the sheet caught its taps with a clickable, which merged every word in it into one node: TalkBack read
     * the whole sheet as one button, and "Updates" was the size of the sheet.
     */
    @Test fun eachPartIsItsOwn() {
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib)) { Shell(Nav("home", listOf("settings")), Games.State.Ready(lib, 0), go = {}) } }
        val b = rule.onNodeWithText("Updates").fetchSemanticsNode().boundsInRoot
        assertTrue("\"Updates\" is ${b.height} tall: merged into the sheet", b.height < 200f)
    }

    /** A tap on the sheet between its buttons leaves it open; only the dimmed space round it closes it. */
    @Test fun aTapOnTheSheetKeepsIt() {
        var nav by mutableStateOf(Nav("home", listOf("settings")))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib)) { Shell(nav, Games.State.Ready(lib, 0), go = { nav = it }) } }
        rule.mainClock.advanceTimeBy(400)
        val t = rule.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { click(t.center.copy(x = t.right + 150f)) }
        rule.waitForIdle()
        assertEquals(listOf("settings"), nav.stack)
    }
}
