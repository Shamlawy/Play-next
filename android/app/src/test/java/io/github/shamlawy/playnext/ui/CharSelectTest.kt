package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
import java.time.Instant
import java.util.TimeZone

/**
 * Character select (Phase 1 piece 3, android-v25; `qfTap`): the first card open at start, a tap on its cover closes it
 * into the panel, a tap on another cover moves the panel, a tap on the picked one opens its card, a tap on the open
 * one closes it; Customise › Closed games switches between the rows and the covers. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class CharSelectTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private fun lib(look: String) = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":[],"deckOff":true$look,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"hours":12,"hype":8},
               {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":2,"hours":30,"logged":10},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3},
               {"id":"d","title":"Quiet Harbour","state":"played","date":"2025-02-01","ord":4}
             ],"duels":[],"played":[{"id":"d","score":8}]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    @After
    fun tearDown() {
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
    }

    private fun show(look: String = ""","qLook":"select"""") {
        val l = lib(look)
        state.value = Games.State.Ready(l, now - 3_600_000L)
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(Nav("games", views = mapOf("games" to "queue")), games, go = {}) }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
    }

    private val isCover = SemanticsMatcher("a cover") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Pick" }

    private fun cover(title: String): SemanticsNodeInteraction = rule.onNode(isCover and hasContentDescription(title))

    private fun tap(title: String) {
        // near its top: a cover brought on screen sits at the foot, under the floating view pill (L239)
        cover(title).performScrollTo().performTouchInput { click(Offset(width / 2f, height * .15f)) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
    }

    /** The panel is up for [title]: its box art opens the game's page. */
    private fun panelFor(title: String) = rule.onAllNodesWithContentDescription("Open $title's page").fetchSemanticsNodes().size

    private fun picked(): String = rule.onNode(isCover and isSelected()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()

    @Test fun aCoverPerGameAndTheFirstCardOpen() {
        show()
        for (t in listOf("Starfall Lantern", "Copper Meadow", "Ninefold Gate", "Quiet Harbour")) cover(t).assertExists()
        assertEquals("Starfall Lantern", picked())
        assertEquals("a card is open at start, no panel", 0, panelFor("Starfall Lantern"))
    }

    @Test fun tapsMoveThePanelOpenAndCloseTheCard() {
        show()
        // the open one's cover closes its card into the panel
        tap("Starfall Lantern")
        assertEquals(1, panelFor("Starfall Lantern"))
        // another cover moves the panel and the P1 marker
        tap("Copper Meadow")
        assertEquals(1, panelFor("Copper Meadow"))
        assertEquals(0, panelFor("Starfall Lantern"))
        assertEquals("Copper Meadow", picked())
        // the picked one opens its card in the panel's place
        tap("Copper Meadow")
        assertEquals(0, panelFor("Copper Meadow"))
        // with a card open, another cover opens that game's card
        tap("Ninefold Gate")
        assertEquals("Ninefold Gate", picked())
        assertEquals(0, panelFor("Ninefold Gate"))
        // and a tap on the open one closes it
        tap("Ninefold Gate")
        assertEquals(1, panelFor("Ninefold Gate"))
    }

    @Test fun customiseSwitchesRowsAndCovers() {
        show(look = "")
        assertTrue("rows: no covers", rule.onAllNodes(isCover).fetchSemanticsNodes().isEmpty())
        Games.customise("qLook", JsonPrimitive("select"))
        rule.waitUntil(5_000) { rule.onAllNodes(isCover).fetchSemanticsNodes().size == 4 }
        Games.customise("qLook", JsonPrimitive("strap"))
        rule.waitUntil(5_000) { rule.onAllNodes(isCover).fetchSemanticsNodes().isEmpty() }
    }
}
