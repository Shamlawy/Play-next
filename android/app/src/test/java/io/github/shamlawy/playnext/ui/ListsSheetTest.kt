package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Lists
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Your lists (Phase 1, M2): the Queue bar's lists button opens them; a list shows in the Queue; making, renaming and
 * deleting one show at once and stay in memory (Games.customise). Made-up games and lists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ListsSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>

    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private fun lib(lists: String) = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":$lists,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"lists":["l1"]},
               {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":2},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"lists":["l1"]}
             ],"duels":[],"played":[]}
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

    /** The Queue with the games in Games' own state, so a change made in the sheet comes back to the screen. */
    private fun show(l: Library, stack: List<String> = emptyList()): () -> Nav {
        state.value = Games.State.Ready(l, now - 3_600_000L)
        var nav by mutableStateOf(Nav("games", stack, mapOf("games" to "queue")))
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        return { nav }
    }

    private val ready get() = state.value as Games.State.Ready

    @Test fun theBarsListsButtonOpensYourLists() {
        val nav = show(lib("[]"))
        rule.onNodeWithContentDescription("Make a list").performClick()
        rule.waitForIdle()
        assertEquals(listOf("lists"), nav().stack)
        rule.onNodeWithText("No lists yet. Make your first one below.").assertExists()
        // a second tap right after the sheet opened lands on nothing (Shell's settle): a finger waits a moment
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), nav().stack)
    }

    @Test fun aListShowsInTheQueue() {
        val nav = show(lib("""[{"id":"l1","n":"Couch nights","e":"🛋️"}]"""), listOf("lists"))
        rule.onNodeWithText("2 games · show in queue").assertExists()
        val copper = { rule.onAllNodesWithText("Copper Meadow").fetchSemanticsNodes().size }
        val before = copper()
        rule.onNodeWithContentDescription("Couch nights, 2 games, show in queue").performClick()
        rule.waitForIdle()
        assertEquals("the sheet closes", emptyList<String>(), nav().stack)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Couch nights, 2")) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertExists()
        assertEquals("a game in the list is there (On deck and its row)", 2, rule.onAllNodesWithText("Starfall Lantern", substring = true).fetchSemanticsNodes().size)
        assertEquals("a game not in the list leaves the rows (the header card may still name it)", before - 1, copper())
    }

    /**
     * M72 (second-look on #688): a Customise slider let go of keeps showing that value until the rebuilt games bring it
     * back; it snapped back to the old one meanwhile. Here the games never come back (a hand-made copy), so the old
     * code showed 150 again.
     */
    @Test fun aCustomiseSliderKeepsTheValueLetGo() {
        val l = lib("[]")
        rule.setContent { Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(Nav("games", listOf("customise:queue"), mapOf("games" to "queue")), Games.State.Ready(l, now), go = {}) } }
        val big = rule.onNodeWithContentDescription("On a row, how big")
        big.performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "150px"))
        big.performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        rule.waitForIdle()
        big.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "90px"))
    }

    @Test fun makeRenameAndDeleteShowAtOnce() {
        show(lib("[]"), listOf("lists"))
        // no name: it asks for one
        rule.onNodeWithText("Make").performScrollTo().performClick()
        rule.onNodeWithText("Give the list a name").assertExists()
        rule.onNodeWithContentDescription("New list's name").performTextInput("Short ones")
        // the marks are drawn icons named for TalkBack; the pick is stored as the web's emoji
        rule.onNodeWithContentDescription("Star").assertIsSelected()
        rule.onNodeWithContentDescription("Moon").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Moon").assertIsSelected()
        rule.onNodeWithText("Make").performScrollTo().performClick()
        rule.waitUntil(5_000) { Lists.of(ready.lib).size == 1 }
        rule.waitForIdle()
        // a new list opens straight on its games; Done goes back to your lists
        rule.onNodeWithText("Add to Short ones").assertExists()
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Short ones").assertExists()
        rule.onNodeWithText("0 games · show in queue").assertExists()
        assertEquals("the moon's emoji, as the web stores it", Lists.MARKS[4].emo, Lists.of(ready.lib)[0].e)
        assertEquals("list:moon", Lists.icon(Lists.of(ready.lib)[0].e))
        // the Queue's bar has its tab too (under the sheet)
        rule.onNodeWithContentDescription("Short ones, 0").assertExists()

        rule.onNodeWithContentDescription("Rename").performClick()
        rule.onNodeWithContentDescription("Name of Short ones").performTextInput("Quick ")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5_000) { Lists.of(ready.lib).firstOrNull()?.n == "Quick Short ones" }

        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Keep").performClick()
        assertEquals("Keep keeps it", 1, Lists.of(ready.lib).size)
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(5_000) { Lists.of(ready.lib).isEmpty() }
        rule.waitForIdle()
        rule.onNodeWithText("No lists yet. Make your first one below.").assertExists()
    }

    private fun lists(gid: String) = ready.lib.games.first { it.id == gid }.let { Lists.inLists(null, it) }

    /** The owner, Nov 3: "add a feature to add games to the new playlist I create". A list's + opens every game. */
    @Test fun aListsPlusPutsGamesInAndTakesThemOut() {
        show(lib("""[{"id":"l1","n":"Couch nights","e":"🛋️"}]"""), listOf("lists"))
        rule.onNodeWithContentDescription("Add games to Couch nights").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Add to Couch nights").assertExists()
        rule.onNodeWithText("2 games in it", substring = true).assertExists()
        val row = { t: String -> rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(t)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)) }
        row("Starfall Lantern").assertIsOn()
        row("Copper Meadow").assertIsOff()
        // two quick taps on two games: both stick (each builds on the last, not on a copy still rebuilding)
        row("Copper Meadow").performClick()
        row("Starfall Lantern").performClick()
        row("Copper Meadow").assertIsOn()
        row("Starfall Lantern").assertIsOff()
        rule.waitUntil(5_000) { lists("b") == listOf("l1") && lists("a").isEmpty() }
        assertEquals("the third game is untouched", listOf("l1"), lists("c"))
        rule.waitForIdle()
        rule.onNodeWithText("2 games in it", substring = true).assertExists()
        // and back again
        row("Copper Meadow").performClick()
        rule.waitUntil(5_000) { lists("b").isEmpty() }
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("1 game · show in queue").assertExists()
    }

    @Test fun theSearchNarrowsTheGames() {
        val many = Library(
            Json.parseToJsonElement(
                """{"theme":"midnight","lists":[{"id":"l1","n":"Couch nights","e":"🛋️"}],"games":[""" +
                    (1..10).joinToString(",") { """{"id":"g$it","title":"${if (it == 7) "Ninefold Gate" else "Copper Meadow $it"}","state":"backlog","ord":$it}""" } +
                    """],"duels":[],"played":[]}""",
            ).jsonObject,
        )
        show(many, listOf("lists"))
        rule.onNodeWithContentDescription("Add games to Couch nights").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Search your games").performTextInput("ninef")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Ninefold Gate").assertExists().performClick()
        // only the match is left in the picker (the Queue under the sheet still has its rows)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Copper Meadow 3")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .assertDoesNotExist()
        rule.waitUntil(5_000) { lists("g7") == listOf("l1") }
    }

    /** M73 (second-look on #690): deleting the list the Queue shows takes it off its games and the Queue goes back to All. */
    @Test fun deletingTheShownListGoesBackToAll() {
        val nav = show(lib("""[{"id":"l1","n":"Couch nights","e":"🛋️"}]"""), listOf("lists"))
        rule.onNodeWithContentDescription("Couch nights, 2 games, show in queue").performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), nav().stack)
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Manage lists").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(5_000) { Lists.of(ready.lib).isEmpty() && lists("a").isEmpty() && lists("c").isEmpty() }
        rule.waitForIdle()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("All, 3")) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertExists()
    }
}
