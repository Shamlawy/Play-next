package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasContentDescription
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
import java.util.TimeZone

/** Settings › Open the app on (`S.startTab`): where the app starts with your games. Made-up games only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class StartTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun lib(start: String, many: Int = 1) = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,"startTab":"$start",
             "games":[${(1..many).joinToString(",") { """{"id":"g$it","title":"Made-up Game $it","state":"backlog","date":"2026-09-30","ord":$it,"colour":"#3A6FD8"}""" }}],
             "duels":[],"played":[]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Suppress("UNCHECKED_CAST")
    private val games = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(null) as MutableStateFlow<Games.State>

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    @After
    fun tearDown() {
        games.value = Games.State.Starting
    }

    /** Which of [labels] is lit (the tab bar's tab or the pill's view). */
    private fun lit(vararg labels: String) = labels.filter { l ->
        rule.onAllNodes(hasContentDescription(l) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun opensOnTheTabYouPicked() {
        games.value = Games.State.Ready(lib("played"), System.currentTimeMillis())
        rule.setContent { PlayNextRoot(null) {} }
        rule.waitForIdle()
        assertEquals(listOf("Games"), lit("Home", "Games", "Plan", "Taste"))
        assertEquals(listOf("Played"), lit("Queue", "Library", "Played", "Replay"))
    }

    @Test fun planIsTheWebsCal() {
        games.value = Games.State.Ready(lib("cal"), System.currentTimeMillis())
        rule.setContent { PlayNextRoot(null) {} }
        rule.waitForIdle()
        assertEquals(listOf("Plan"), lit("Home", "Games", "Plan", "Taste"))
    }

    /** A link that opened the app goes where it points, whatever the start tab says. */
    @Test fun aLinkStillGoesWhereItPoints() {
        games.value = Games.State.Ready(lib("played"), System.currentTimeMillis())
        var done = false
        rule.setContent { PlayNextRoot(Route.Tab("taste", "duels")) { done = true } }
        rule.waitForIdle()
        assertTrue(done)
        assertEquals(listOf("Taste"), lit("Home", "Games", "Plan", "Taste"))
    }

    /**
     * Samsung closing the app in the background, and the app coming back while this phone's copy is still opening: the
     * page waits for your games instead of drawing short and cutting the kept scroll spot to the top (before the fix it
     * came back at 0 from 2832).
     */
    @Test fun theScrollSpotSurvivesARestartWhileOpening() {
        val l = lib("queue", many = 60)
        games.value = Games.State.Ready(l, System.currentTimeMillis())
        val tester = StateRestorationTester(rule)
        tester.setContent { PlayNextRoot(null) {} }
        rule.waitForIdle()
        fun barTop() = rule.onAllNodes(hasContentDescription("Games") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .fetchSemanticsNodes().minOf { it.positionInRoot.y }
        val top = barTop()
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * .7f, endY = top + 100f, durationMillis = 400) }
        rule.waitForIdle()
        val scrolled = barTop()
        assertTrue("the page didn't scroll ($top → $scrolled)", scrolled < top - 500f)
        games.value = Games.State.Starting
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        games.value = Games.State.Ready(l, System.currentTimeMillis())
        rule.waitForIdle()
        assertEquals(scrolled, barTop(), 2f)
    }

    /** Your games arriving after the start (no copy on the phone yet) still open on your tab, once. */
    @Test fun gamesThatComeLaterStartThereToo() {
        games.value = Games.State.Fetching()
        rule.setContent { PlayNextRoot(null) {} }
        rule.waitForIdle()
        assertEquals(listOf("Home"), lit("Home", "Games", "Plan", "Taste"))
        games.value = Games.State.Ready(lib("taste"), System.currentTimeMillis())
        rule.waitForIdle()
        assertEquals(listOf("Taste"), lit("Home", "Games", "Plan", "Taste"))
    }
}
