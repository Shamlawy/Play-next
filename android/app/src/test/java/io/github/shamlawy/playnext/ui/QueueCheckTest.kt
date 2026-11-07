package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
import java.time.Instant
import java.util.TimeZone

/**
 * The Phase 1 check's fixes on the Queue (android/parity/1-perfect.md): back closes what the Queue has open (A1), a
 * card in Character select keeps a new pick (A2), two fingers never slide or hold a row (A3), the Queue's picks outlive
 * the restore screen (A4) and Android closing the app (C1), the Undo bar's time runs while you're away (A5), back steps
 * out of Your lists' pages (A6) and a list that didn't survive (C2), a tab or order change lets go of the open card
 * (A7), Character select's pick follows the tab and its gaps match the web (M78). Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QueueCheckTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private fun lib(extra: String = "", lists: String = "[]") = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,"deckOff":true,"startTab":"queue","lists":$lists$extra,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action"],"hours":22,"hype":8,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":["RPG"],"hours":40,"logged":12,"hype":5,"added":"2026-09-02"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"colour":"#B06AD8","genres":["Strategy"],"hype":9,"added":"2026-10-25"},
               {"id":"d","title":"Copper Meadow","state":"backlog","date":"2026-05-11","ord":4,"colour":"#2E9E6A","genres":["Puzzle"],"hours":12,"hype":3,"added":"2026-06-14"}
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

    private var nav by mutableStateOf(Nav("games", views = mapOf("games" to "queue")))

    /** The Shell on Games › Queue with [l] in Games' own state (a change made in a sheet comes back to the screen). */
    private fun show(l: Library = lib(), stack: List<String> = emptyList()) {
        state.value = Games.State.Ready(l, now - 3_600_000L)
        nav = Nav("games", stack, mapOf("games" to "queue"))
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        settle()
    }

    private fun settle(ms: Long = 1_500) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    private val back get() = rule.activity.onBackPressedDispatcher
    private fun pressBack() { rule.runOnIdle { back.onBackPressed() }; settle() }

    // a row says its name and its headline (the header card's line can say the name too: L228)
    private val heads = mapOf("Hollow Tide" to "28 h left", "Ninefold Gate" to "Out in 18 days", "Copper Meadow" to "Ready to play")
    private fun rowM(name: String) = hasText(name, substring = true) and hasText(heads.getValue(name), substring = true) and hasClickAction()
    private fun row(name: String): SemanticsNodeInteraction {
        rule.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(rowM(name))
        return rule.onAllNodes(rowM(name)).onLast().also { it.performScrollTo() }
    }

    /** A finger on a row's top left (a row at the screen's foot sits under the floating view pill: L228, L239). */
    private fun tapRow(name: String) { row(name).performTouchInput { click(Offset(width * .12f, height * .3f)) }; settle() }

    /** The open card's game (a card says front/back for TalkBack), or null when none is open. */
    private fun openCard(): String? = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and hasContentDescription(", ", substring = true))
        .fetchSemanticsNodes().firstOrNull { SemanticsProperties.ContentDescription in it.config && it.config[SemanticsProperties.StateDescription].let { s -> s == "front" || s == "back" } }
        ?.config?.get(SemanticsProperties.ContentDescription)?.first()?.substringBefore(", ")

    private fun deletes() = rule.onAllNodes(hasText("Delete") and hasClickAction()).fetchSemanticsNodes().size

    /** [r]'s right side taken [dx] dp sideways by one finger in 8 steps, then let go. */
    private fun swipe(r: SemanticsNodeInteraction, dx: Float) = r.performTouchInput {
        val p = Offset(width * .88f, height * .3f)
        down(p)
        for (k in 1..8) moveTo(p + Offset(dx.dp.toPx() * k / 8, 0f), delayMillis = 16)
        up()
    }

    private fun chip(name: String) = rule.onNode(hasContentDescription("$name, ", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
    private fun tapChip(name: String) { chip(name).performClick(); settle() }
    private fun lit(name: String) = chip(name).fetchSemanticsNode().config[SemanticsProperties.Selected]

    // ---------------------------------------------------------------- A1: back

    @Test fun backClosesASwipedRowThenTheCardYouOpenedThenLeaves() {
        show()
        assertEquals("the first game's card is open at start", "Starfall Lantern", openCard())
        assertTrue("that card isn't yours: back leaves the app from the tab", !back.hasEnabledCallbacks())
        tapRow("Hollow Tide")
        assertEquals("Hollow Tide", openCard())
        swipe(row("Ninefold Gate"), -120f)
        settle(600)
        assertEquals("Delete shows under the swiped row", 1, deletes())
        pressBack()
        assertEquals("back slid the row shut", 0, deletes())
        assertEquals("and left the card open", "Hollow Tide", openCard())
        pressBack()
        assertEquals("the next back closed the card", null, openCard())
        assertTrue("then back leaves the app", !back.hasEnabledCallbacks())
        assertEquals("nothing else changed", listOf("games", "queue"), listOf(nav.tab, nav.views["games"]))
    }

    // ---------------------------------------------------------------- A7: a tab or an order lets go of the card

    @Test fun aTabOrOrderChangeLetsGoOfTheCardYouOpened() {
        show()
        tapRow("Copper Meadow")
        assertEquals("Copper Meadow", openCard())
        // Out still holds it; the web's `focusId = null`: the first game shown opens instead
        tapChip("Out")
        assertEquals("Starfall Lantern", openCard())
        tapRow("Copper Meadow")
        rule.onNode(hasContentDescription("Sort: ", substring = true)).performClick()
        settle(400)
        rule.onNodeWithText("Most hyped").performClick()
        settle()
        assertEquals("a new order opens its first game", "Starfall Lantern", openCard())
    }

    // ---------------------------------------------------------------- A3: two fingers

    @Test fun twoFingersOnARowWalkTheTabsAndSlideNoDelete() {
        show()
        row("Hollow Tide").performTouchInput {
            val p = Offset(width * .6f, height * .3f)
            val q = Offset(width * .8f, height * .3f)
            down(0, p)
            down(1, q)
            for (k in 1..8) {
                updatePointerBy(0, Offset(-15.dp.toPx(), 0f))
                updatePointerBy(1, Offset(-15.dp.toPx(), 0f))
                move(16)
            }
            up(1)
            up(0)
        }
        settle(600)
        assertEquals("no Delete", 0, deletes())
        assertTrue("two fingers walked the tabs (${nav.tab})", nav.tab != "games")
    }

    @Test fun twoFingersHeldOnARowNeverHoldIt() {
        // in a sorted order a hold opens the game's page
        show(lib(""","sort":"hype""""))
        row("Hollow Tide").performTouchInput {
            down(0, Offset(width * .3f, height * .3f))
            down(1, Offset(width * .5f, height * .3f))
            advanceEventTime(700)
            move(16)
            up(1)
            up(0)
        }
        settle()
        assertEquals("no game page opened", emptyList<String>(), nav.stack)
    }

    // ---------------------------------------------------------------- A5: the Undo bar's time

    @Test fun theUndoBarsTimeRunsWhileYouAreAway() {
        show()
        swipe(row("Ninefold Gate"), -120f)
        settle(600)
        rule.onAllNodes(hasText("Delete") and hasClickAction()).onLast().performTouchInput { click(Offset(width * .5f, height * .15f)) }
        rule.waitForIdle()
        assertEquals("the Undo bar shows", 1, rule.onAllNodesWithText("Deleted Ninefold Gate").fetchSemanticsNodes().size)
        // away on another tab for longer than the bar lasts
        nav = nav.go("home")
        settle(500)
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        nav = nav.go("games")
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(200)
        assertEquals("back on the Queue: the bar is gone, not starting over", 0, rule.onAllNodesWithText("Deleted Ninefold Gate").fetchSemanticsNodes().size)
        rule.mainClock.autoAdvance = true
    }

    // ---------------------------------------------------------------- A4 + C1: the Queue's picks last

    /** Settings › "Use a different restore code" takes the tabs away; back brings them, and the Queue is as you left it. */
    @Test fun theQueueKeepsItsPicksPastTheRestoreScreen() {
        val l = lib()
        state.value = Games.State.Ready(l, now - 3_600_000L)
        rule.setContent { PlayNextRoot(null) {} }
        settle()
        tapChip("Coming")
        assertTrue(lit("Coming"))
        rule.onNodeWithContentDescription("Settings").performClick()
        settle(400)
        rule.onNodeWithText("Use a different restore code").performScrollTo().performClick()
        rule.waitUntil(5_000) { state.value is Games.State.NoCode }
        settle()
        assertEquals("the restore screen is up", 1, rule.onAllNodesWithText("Bring your games").fetchSemanticsNodes().size)
        pressBack()
        // your games again (here by hand), then back out of Settings
        state.value = Games.State.Ready(l, now - 3_600_000L)
        settle()
        pressBack()
        assertTrue("the Queue still shows Coming", lit("Coming"))
    }

    @Test fun theQueueKeepsItsPicksWhenAndroidClosesTheApp() {
        val l = lib()
        state.value = Games.State.Ready(l, now - 3_600_000L)
        val tester = StateRestorationTester(rule)
        tester.setContent { PlayNextRoot(null) {} }
        settle()
        tapChip("Out")
        tapRow("Copper Meadow")
        assertEquals("Copper Meadow", openCard())
        tester.emulateSavedInstanceStateRestore()
        settle()
        assertTrue("the tab came back", lit("Out"))
        assertEquals("and the card you opened", "Copper Meadow", openCard())
    }

    // ---------------------------------------------------------------- A6 + C2: Your lists

    @Test fun backStepsOutOfYourListsPagesFirst() {
        show(lib(lists = """[{"id":"l1","n":"Couch nights","e":"🛋️"}]"""), listOf("lists"))
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Add games to Couch nights").performClick()
        settle()
        assertEquals("Add games is up", 1, rule.onAllNodesWithText("Add to Couch nights").fetchSemanticsNodes().size)
        pressBack()
        assertEquals("back closed Add games", 0, rule.onAllNodesWithText("Add to Couch nights").fetchSemanticsNodes().size)
        assertEquals("and kept the sheet", listOf("lists"), nav.stack)
        rule.onNodeWithContentDescription("Delete").performClick()
        settle()
        val asking = { rule.onAllNodesWithText("Keep", substring = true).fetchSemanticsNodes().size }
        assertTrue("the delete question shows", asking() > 0)
        pressBack()
        assertEquals("back closed the question", 0, asking())
        assertEquals(listOf("lists"), nav.stack)
        pressBack()
        assertEquals("then back closes the sheet", emptyList<String>(), nav.stack)
    }

    @Test fun aListMadeBeforeAndroidClosedTheAppDoesNotReopen() {
        val l = lib()
        state.value = Games.State.Ready(l, now - 3_600_000L)
        nav = Nav("games", listOf("lists"), mapOf("games" to "queue"))
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        settle()
        rule.onNodeWithContentDescription("New list's name").performScrollTo().performTextInput("Short ones")
        rule.onNodeWithText("Make").performScrollTo().performClick()
        settle()
        assertEquals("Add games for the new list", 1, rule.onAllNodesWithText("Add to Short ones").fetchSemanticsNodes().size)
        // Android closes the app: the practice copy (and the list in it) is gone, the sheet's own state comes back
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Ready(l, now - 3_600_000L)
        tester.emulateSavedInstanceStateRestore()
        settle()
        assertEquals("no Add games for a list that's gone", 0, rule.onAllNodesWithText("Add to Short ones").fetchSemanticsNodes().size)
        rule.onNodeWithText("No lists yet. Make your first one below.").assertExists()
    }

    // ---------------------------------------------------------------- Character select (A2, M78)

    private val isCover = SemanticsMatcher("a cover") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Pick" }
    private fun cover(title: String): SemanticsNodeInteraction = rule.onNode(isCover and hasContentDescription(title))
    private fun tapCover(title: String) = cover(title).performScrollTo().performTouchInput { click(Offset(width / 2f, height * .15f)) }
    private fun picked(): String = rule.onNode(isCover and isSelected()).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
    private val select = ""","qLook":"select""""

    @Test fun aCoverTappedWhileTheCardWaitsKeepsTheNewCard() {
        show(lib(select))
        assertEquals("Starfall Lantern", openCard())
        rule.mainClock.autoAdvance = false
        // one tap on the open card: it waits 320 ms for a second before it closes
        rule.onNode(hasContentDescription("Starfall Lantern, ", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .performTouchInput { click(Offset(width * .7f, height * .45f)) }
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(120)
        // (on screen already: a scroll would wait for a clock that isn't running)
        cover("Hollow Tide").performTouchInput { click(Offset(width / 2f, height * .15f)) }
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(1_500)
        rule.mainClock.autoAdvance = true
        settle()
        assertEquals("the new card stays open", "Hollow Tide", openCard())
    }

    @Test fun thePickFollowsTheTabAsOnTheWeb() {
        show(lib(select))
        // close the card at start into the panel, then pick another game
        tapCover("Starfall Lantern"); settle()
        tapCover("Copper Meadow"); settle()
        assertEquals("Copper Meadow", picked())
        // Coming doesn't show it: its first game is picked, and stays picked back on All (`qfSel`, index.html 8954)
        tapChip("Coming")
        assertEquals("Ninefold Gate", picked())
        tapChip("All")
        assertEquals("Ninefold Gate", picked())
    }

    @Test fun theCardAndThePanelSitTheWebsGapAboveTheCovers() {
        show(lib(select))
        val d = rule.density.density
        fun coversTop() = rule.onAllNodes(isCover).fetchSemanticsNodes().minOf { it.boundsInRoot.top }
        val card = rule.onNode(hasContentDescription("Starfall Lantern, ", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .fetchSemanticsNode().boundsInRoot
        assertEquals("under the open card: 14 + the grid's 8", 22f, (coversTop() - card.bottom) / d, 1f)
        tapCover("Starfall Lantern"); settle()
        val panel = rule.onNode(SemanticsMatcher("the panel") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Open its card" })
            .fetchSemanticsNode().boundsInRoot
        assertEquals("under the panel too", 22f, (coversTop() - panel.bottom) / d, 1f)
    }
}
