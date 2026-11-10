package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
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
import java.time.Instant
import java.util.TimeZone

/**
 * The game page's own gestures (Phase 2 piece 1, review of android-v29): a back gesture let go of springs the page back
 * to its full size (3-critic.md M9), and the screenshot strip keeps its sideways drag past its last shot, so it never
 * turns the page to the next game. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GamePageGestureTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    private val lib = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action"],"hours":22,
                "shots":[{"u":"https://example.invalid/a1.jpg"},{"u":"https://example.invalid/a2.jpg"},{"u":"https://example.invalid/a3.jpg"}]},
               {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":2,"colour":"#2E9E6A","genres":["Action"],"hours":12,
                "shots":[{"u":"https://example.invalid/b1.jpg"},{"u":"https://example.invalid/b2.jpg"}]}
             ],
             "duels":[],"played":[]}
            """.trimIndent(),
        ).jsonObject,
    )
    private val ready = Games.State.Ready(lib, copyAt = now - 3 * 3_600_000L)

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    private val page = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Game page")

    private fun pageWidth() = rule.onNode(page).fetchSemanticsNode().boundsInRoot.width

    @Test fun aBackGestureLetGoOfSpringsThePageBack() {
        lateinit var back: OnBackPressedDispatcher
        rule.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(Nav("games", listOf("game:a")), ready, go = {}) }
        }
        rule.waitForIdle()
        val full = pageWidth()
        rule.runOnUiThread {
            back.dispatchOnBackStarted(BackEventCompat(10f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(200f, 900f, .6f, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()
        assertTrue("the page shrinks with the finger", pageWidth() < full * .99f)
        rule.runOnUiThread { back.dispatchOnBackCancelled() }
        rule.waitForIdle()
        assertEquals("let go of, it springs back", full, pageWidth(), .5f)
    }

    /** A quick flick shorter than the .22-width drag turns the page: the Shell's tab swipe leaves its lift to the page. */
    @Test fun aQuickFlickTurnsThePage() {
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(Nav("games", listOf("game:a")), ready, go = {}) } }
        rule.waitForIdle()
        rule.onNode(page).performTouchInput {
            val y = height * .2f
            val d = 150.dp.toPx()
            swipe(Offset(width * .8f, y), Offset(width * .8f - d, y), durationMillis = 60)
        }
        rule.waitForIdle()
        assertTrue("the next game's page", rule.onAllNodesWithContentDescription("of 2", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun theScreenshotStripNeverTurnsThePage() {
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(Nav("games", listOf("game:a")), ready, go = {}) } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Screenshot 1 of 3").performScrollTo()
        // past the last shot and on: what the strip can't use stays with it
        repeat(4) {
            rule.onNodeWithContentDescription("Screenshot 2 of 3").performTouchInput { swipeLeft() }
            rule.waitForIdle()
        }
        val ofA = rule.onAllNodesWithContentDescription("of 3", substring = true).fetchSemanticsNodes().size
        val ofB = rule.onAllNodesWithContentDescription("of 2", substring = true).fetchSemanticsNodes().size
        assertEquals("still the first game's page (its shots $ofA, the next game's $ofB)", 0, ofB)
        assertTrue("the first game's shots are there", ofA > 0)
    }
}
