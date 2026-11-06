package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Lists
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.data.Shuffler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.random.Random

/**
 * The Game Shuffler (Phase 1 piece 3, android-v24): Sort › Game shuffler opens the deck; a swipe or the buttons say
 * tempted or not now, Back one undoes it, Your picks lists them and can save them as a list; Everything waiting or Out
 * now only. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShuffleDeckTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    // two out (a backlog game, one being played), one coming, one played: the deck holds three, two of them out
    private val lib = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":[],
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"hours":12,"hype":8},
               {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":2},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3},
               {"id":"d","title":"Quiet Harbour","state":"played","date":"2025-02-01","ord":4}
             ],"duels":[],"played":[{"id":"d","score":8}]}
            """.trimIndent(),
        ).jsonObject,
    )
    private val titles = listOf("Starfall Lantern", "Copper Meadow", "Ninefold Gate", "Quiet Harbour")

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

    private fun show(stack: List<String> = emptyList()): () -> Nav {
        state.value = Games.State.Ready(lib, now - 3_600_000L)
        var nav by mutableStateOf(Nav("games", stack, mapOf("games" to "queue")))
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        rule.waitForIdle()
        // a tap right after a step opens lands on nothing (Shell's settle)
        rule.mainClock.advanceTimeBy(1_000)
        return { nav }
    }

    private val inDeck = hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Game shuffler"))

    /** The card on show: the open Queue card's front, named "<title>, <headline>". */
    private fun card(): SemanticsNodeInteraction = rule.onNode(
        inDeck and SemanticsMatcher("a deck card") { n ->
            val d = n.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.firstOrNull().orEmpty()
            titles.any { d.startsWith("$it, ") }
        },
    )

    private fun place() = rule.onNode(inDeck and SemanticsMatcher("where you are") { n ->
        n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { Regex("^\\d+ / \\d+$").matches(it.text) }
    }).fetchSemanticsNode().config[SemanticsProperties.Text].first().text

    private fun picks() = rule.onAllNodesWithContentDescription("Your picks", substring = true).fetchSemanticsNodes()
        .single().config[SemanticsProperties.ContentDescription].first()

    @Test fun sortsMenuOpensTheDeck() {
        val nav = show()
        rule.onNode(hasContentDescription("Sort: ", substring = true)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Game shuffler").performClick()
        rule.waitForIdle()
        assertEquals(listOf("shuffle"), nav().stack)
        assertEquals("everything waiting: not the played one", "1 / 3", place())
        card().assertExists()
    }

    @Test fun theButtonsTemptAndBackOneUndoes() {
        show(listOf("shuffle"))
        rule.onNode(inDeck and hasContentDescription("Tempted")).performClick()
        rule.waitForIdle()
        assertEquals("2 / 3", place())
        assertEquals("Your picks, 1", picks())
        rule.onNodeWithContentDescription("Not now").performClick()
        rule.waitForIdle()
        assertEquals("3 / 3", place())
        assertEquals("Your picks, 1", picks())
        rule.onNodeWithContentDescription("Back one").performClick()
        rule.onNodeWithContentDescription("Back one").performClick()
        rule.waitForIdle()
        assertEquals("1 / 3", place())
        assertEquals("both undone", "Your picks", picks())
    }

    @Test fun aSwipeRightTemptsAndLeftDoesnt() {
        show(listOf("shuffle"))
        val first = card().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        card().performTouchInput { swipeRight(startX = width * .2f, endX = width * .9f, durationMillis = 250) }
        rule.waitForIdle()
        assertEquals("2 / 3", place())
        assertEquals("Your picks, 1", picks())
        assertTrue("a new card came up", card().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first() != first)
        card().performTouchInput { swipeLeft(startX = width * .8f, endX = width * .1f, durationMillis = 250) }
        rule.waitForIdle()
        assertEquals("3 / 3", place())
        assertEquals("Your picks, 1", picks())
    }

    @Test fun aShortSwipeSpringsBack() {
        show(listOf("shuffle"))
        card().performTouchInput { swipeRight(startX = width * .4f, endX = width * .4f + 40f, durationMillis = 200) }
        rule.waitForIdle()
        assertEquals("1 / 3", place())
        assertEquals("Your picks", picks())
    }

    @Test fun outNowOnlyDealsTheGamesThatAreOut() {
        show(listOf("shuffle"))
        rule.onNodeWithText("Out now only").performClick()
        rule.waitForIdle()
        assertEquals("the coming one stays out", "1 / 2", place())
    }

    @Test fun yourPicksOpenAndBecomeAList() {
        val nav = show(listOf("shuffle"))
        repeat(3) {
            rule.onNode(inDeck and hasContentDescription("Tempted")).performClick()
            rule.waitForIdle()
        }
        // the deck ran out: the picks show by themselves
        rule.onNodeWithText("Tempted by 3").assertExists()
        rule.onNodeWithText("Save as a list").performScrollTo().performClick()
        rule.waitUntil(5_000) { Lists.of((state.value as Games.State.Ready).lib).size == 1 }
        val l = Lists.of((state.value as Games.State.Ready).lib).single()
        assertTrue(l.n, l.n.startsWith("Tempted · "))
        assertEquals("the target mark", "list:target", Lists.icon(l.e))
        val over = Games.practiced(Lists.IN) as JsonObject
        for (g in listOf("a", "b", "c")) assertTrue("$g is in it", l.id in Lists.inLists(over, lib.games.first { it.id == g }))
        rule.waitForIdle()
        rule.onNodeWithText("Saved as", substring = true).assertExists()
        // a pick opens its page over the deck; back comes to the deck
        rule.onNodeWithContentDescription("Copper Meadow, Playing now, open its page").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("shuffle", "game:b"), nav().stack)
    }

    // ---- the numbers

    private fun queue(l: Library = lib): Queue {
        val z = ZoneId.of("UTC")
        return Queue(l, Rank(l, now, z), now, z)
    }

    @Test fun theDeckHoldsWhatsWaiting() {
        val q = queue()
        assertEquals(setOf("a", "b", "c"), Shuffler.deck(q, Shuffler.ALL, Random(1)).toSet())
        assertEquals(setOf("a", "b"), Shuffler.deck(q, Shuffler.OUT, Random(1)).toSet())
    }

    @Test fun swipesUndoAndComeBackFromTheSavedForm() {
        var d = Shuffler.Deck(Shuffler.ALL, listOf("a", "b", "c"))
        d = d.swipe(true).swipe(false).swipe(true)
        assertEquals(listOf("a", "c"), d.picks)
        assertTrue("the deck ran out", d.done)
        assertEquals(d, Shuffler.Deck.restore(d.save()))
        d = d.undo()
        assertEquals(listOf("a"), d.picks)
        assertEquals(2, d.at)
        // a not-now on a pick takes it out, and undoing that puts it back
        d = d.go(0).swipe(false)
        assertEquals(emptyList<String>(), d.picks)
        assertEquals(listOf("a"), d.undo().picks)
    }

    @Test fun theWordsUnderACard() {
        val q = queue()
        // at the web's 4 hours an evening: 12 h is 3 evenings
        assertEquals("It's out and waiting for you. About 3 evenings at your usual pace. You hyped it 8/10.", Shuffler.why(q, lib.games.first { it.id == "a" }))
        assertEquals("It lands 14 Nov, in 18 days.", Shuffler.why(q, lib.games.first { it.id == "c" }))
        assertEquals("Ninefold Gate", Shuffler.short("Ninefold Gate: The Long Night"))
    }

    @Test fun saveAsListNamesItForTheDay() {
        state.value = Games.State.Ready(lib, now)
        val name = saveAsList(lib, lib.games.take(2), LocalDate.of(2026, 11, 6))
        assertEquals("Tempted · 6 Nov", name)
    }
}
