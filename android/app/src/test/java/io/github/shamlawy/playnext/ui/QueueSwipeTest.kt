package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
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
import java.time.Instant
import java.util.TimeZone

/**
 * android-v17 (the owner, Nov 5): a held row scrolls the Queue at the window's top and bottom edges, and a row swiped
 * left shows Delete (`qdel`, index.html 15070-15142), with an Undo bar in place of the web's question. Delete is a
 * practice copy: the game leaves this backup's Queue until the app opens again. Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class QueueSwipeTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private var fillers = 0
    private val lib get() = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,"deckOff":true,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"platforms":"PS5, Switch 2, PC, Wii","hours":22,"hype":8,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"logged":12,"added":"2026-09-02"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"colour":"#B06AD8","genres":["RPG","Strategy"],"platforms":"PS4, PS5, Vita","added":"2026-10-25"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9","genres":["Strategy","RPG"],"added":"2026-01-10",
                "mine":{"story":9,"music":8.5,"gameplay":8,"replay":6,"visuals":9,"world":8,"chars":7.5,"pace":7}}${
                (1..fillers).joinToString("") { k ->
                    val n = k.toString().padStart(2, '0')
                    ",{\"id\":\"m$k\",\"title\":\"Filler $n\",\"state\":\"backlog\",\"date\":\"2026-0${1 + k % 9}-10\",\"ord\":${6 + k},\"colour\":\"#4A7A5C\",\"genres\":[\"Puzzle\"],\"added\":\"2026-10-01\"}"
                }
            }
             ],
             "played":[{"id":"f","real":9.2,"t":"2026-09-12"}]}
            """.trimIndent(),
        ).jsonObject,
    )

    private var nav by mutableStateOf(Nav("games", views = mapOf("games" to "queue")))

    private fun show(width: Int = 412) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${if (width < 600) 915 else 1000}dp-xxhdpi")
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, Games.State.Ready(lib, now - 3_600_000L), go = { nav = it }) } }
        rule.mainClock.advanceTimeBy(2_500)
    }

    private val list get() = rule.onAllNodes(hasScrollToNodeAction()).onLast()

    // a row says its name and its headline (the header card's line can say the name too: L228)
    private val heads = mapOf("Hollow Tide" to "28 h left", "Ninefold Gate" to "Out in 18 days")
    private fun rowM(name: String) = hasText(name, substring = true) and hasText(heads.getValue(name), substring = true) and hasClickAction()

    /** A closed row: the last tappable thing with its name and headline, all on screen. */
    private fun row(name: String): SemanticsNodeInteraction {
        list.performScrollToNode(rowM(name))
        return rule.onAllNodes(rowM(name)).onLast().also { it.performScrollTo() }
    }
    private fun shown(name: String) = rule.onAllNodes(rowM(name)).fetchSemanticsNodes().isNotEmpty()
    private fun open(name: String) = rule.onAllNodesWithContentDescription(name, substring = true).fetchSemanticsNodes()
        .any { SemanticsProperties.StateDescription in it.config }
    private fun delete() = rule.onAllNodes(hasText("Delete") and hasClickAction())

    /** A tap near Delete's top: its middle can sit under the floating view pill, like the row's foot (L228). */
    private fun tapDelete() = delete().onLast().performTouchInput { click(Offset(width * .5f, height * .15f)) }
    /** All's count in the bar (a lazy list's first rows go when it scrolls: bring the bar back first, L213). */
    private fun allCount(): Int {
        val tab = hasContentDescription("All, ", substring = true)
        // (a list that fits the screen has nothing to scroll)
        if (rule.onAllNodes(tab).fetchSemanticsNodes().isEmpty()) list.performScrollToNode(tab)
        return rule.onAllNodesWithContentDescription("All, ", substring = true).fetchSemanticsNodes().first()
            .config[SemanticsProperties.ContentDescription].first().substringAfter(", ").toInt()
    }

    /**
     * A finger on [r]'s right side, taken [dx] dp sideways in 8 steps, then let go (a row brought on screen sits at its
     * foot, under the floating view pill's middle: L228).
     */
    private fun swipe(r: SemanticsNodeInteraction, dx: Float) = r.performTouchInput {
        val p = Offset(width * .88f, height * .3f)
        down(p)
        for (k in 1..8) moveTo(p + Offset(dx.dp.toPx() * k / 8, 0f), delayMillis = 16)
        up()
    }

    @Test fun aRowSwipedLeftShowsDeleteAndStaysOpen() {
        show()
        swipe(row("Hollow Tide"), -120f)
        rule.mainClock.advanceTimeBy(600)
        assertEquals("Delete shows under the swiped row", 1, delete().fetchSemanticsNodes().size)
        assertEquals("the swipe walked no views", "queue", nav.views["games"])
        assertTrue("and opened no card", !open("Hollow Tide"))
    }

    @Test fun aShortSwipeSlidesBack() {
        show()
        swipe(row("Hollow Tide"), -30f)
        rule.mainClock.advanceTimeBy(600)
        assertEquals("under half of 92 dp: no Delete left", 0, delete().fetchSemanticsNodes().size)
    }

    @Test fun aTapElsewhereClosesItAndTapsNothing() {
        show()
        swipe(row("Hollow Tide"), -120f)
        rule.mainClock.advanceTimeBy(600)
        // a tap on the open card where it already is (a scroll first would close the row by itself)
        val starfall = rule.onAllNodesWithContentDescription("Starfall Lantern, ", substring = true).onFirst()
        fun tapCard() = starfall.performTouchInput { click(Offset(width * .7f, height * .9f)) }
        tapCard()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("the tap closed the open row", 0, delete().fetchSemanticsNodes().size)
        assertTrue("and the card it landed on stayed open", open("Starfall Lantern"))
        // the next tap is a tap again: it closes the card into its row
        tapCard()
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("a second tap closes the card", !open("Starfall Lantern"))
    }

    @Test fun deleteThenUndo() {
        show()
        val before = allCount()
        swipe(row("Hollow Tide"), -120f)
        rule.mainClock.advanceTimeBy(600)
        tapDelete()
        rule.mainClock.advanceTimeBy(800)
        assertEquals("the tap stayed on the Queue", "queue", nav.views["games"])
        assertTrue("the row is gone", !shown("Hollow Tide"))
        assertEquals("All counts one less", before - 1, allCount())
        assertEquals("the Undo bar names it", 1, rule.onAllNodes(hasText("Deleted Hollow Tide")).fetchSemanticsNodes().size)
        rule.onRoot().captureRoboImage("src/test/screenshots/queue-undo-412.png")
        rule.onAllNodes(hasText("Undo") and hasClickAction()).onLast().performClick()
        rule.mainClock.advanceTimeBy(800)
        assertEquals("Undo brings it back", before, allCount())
        assertTrue("in its row", shown("Hollow Tide"))
        assertEquals("and the bar goes", 0, rule.onAllNodes(hasText("Deleted Hollow Tide")).fetchSemanticsNodes().size)
    }

    @Test fun theUndoBarGoesByItself() {
        show()
        swipe(row("Ninefold Gate"), -120f)
        rule.mainClock.advanceTimeBy(600)
        tapDelete()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, rule.onAllNodes(hasText("Deleted Ninefold Gate")).fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals("gone after 5 s", 0, rule.onAllNodes(hasText("Deleted Ninefold Gate")).fetchSemanticsNodes().size)
        assertTrue("and the game stays deleted", !shown("Ninefold Gate"))
    }

    /** The picture of a swiped row: Delete in the house's hot red under its right end (folded and unfolded). */
    private fun swipedShot(width: Int) {
        show(width)
        swipe(row("Hollow Tide"), -120f)
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage("src/test/screenshots/queue-swiped-$width.png")
    }
    @Test fun swiped412() = swipedShot(412)
    @Test fun swiped859() = swipedShot(859)

    /** Held near the window's foot the list scrolls by itself, so the row can be taken past the first screen. */
    @Test fun aHeldRowScrollsTheListAtTheEdge() {
        fillers = 14
        show()
        assertTrue("before: Hollow Tide is on the first screen", shown("Hollow Tide"))
        val r = row("Hollow Tide").fetchSemanticsNode().boundsInRoot
        val h = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        rule.onRoot().performTouchInput {
            val p = Offset(r.left + r.width * .12f, r.top + r.height * .3f)
            down(p)
            advanceEventTime(500)
            val foot = Offset(p.x, h - 24.dp.toPx())
            for (k in 1..8) moveTo(p + (foot - p) * (k / 8f), delayMillis = 32)
            // the finger stays at the foot for 3 s while the list runs on
            for (k in 1..60) moveTo(foot + Offset(0f, if (k % 2 == 0) 1f else 0f), delayMillis = 50)
            up()
        }
        rule.mainClock.advanceTimeBy(1_500)
        list.performScrollToIndex(0)
        rule.mainClock.advanceTimeBy(500)
        assertTrue("after: it landed past the first screen", !shown("Hollow Tide"))
        assertTrue("and the first rows are where they were", shown("Ninefold Gate"))
        row("Hollow Tide")
        assertTrue("it is still in the Queue", shown("Hollow Tide"))
        assertTrue("a drag opens no card", !open("Hollow Tide"))
    }
}

