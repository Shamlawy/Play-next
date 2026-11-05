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
import androidx.compose.ui.test.onNodeWithContentDescription
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
    private var grum = false
    /** Only the last filler is out (the top two dropped, the others coming), so Shuffle picks a row far down. */
    private var deep = false
    private val lib get() = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,"deckOff":true,"grum":$grum,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"${if (deep) "dropped" else "backlog"}","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"platforms":"PS5, Switch 2, PC, Wii","hours":22,"hype":8,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"${if (deep) "dropped" else "playing"}","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"logged":12,"added":"2026-09-02"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"colour":"#B06AD8","genres":["RPG","Strategy"],"platforms":"PS4, PS5, Vita","added":"2026-10-25"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9","genres":["Strategy","RPG"],"added":"2026-01-10",
                "mine":{"story":9,"music":8.5,"gameplay":8,"replay":6,"visuals":9,"world":8,"chars":7.5,"pace":7}}${
                (1..fillers).joinToString("") { k ->
                    val n = k.toString().padStart(2, '0')
                    ",{\"id\":\"m$k\",\"title\":\"Filler $n\",\"state\":\"backlog\",\"date\":\"${if (deep && k < fillers) 2027 else 2026}-0${1 + k % 9}-10\",\"ord\":${6 + k},\"colour\":\"#4A7A5C\",\"genres\":[\"Puzzle\"],\"added\":\"2026-10-01\"}"
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

    /** A long swipe (past 92 dp, held): Delete runs right up to the row, so no empty space shows past it. */
    @Test fun aLongSwipeLeavesNoGap() {
        show()
        val r = row("Hollow Tide")
        val was = r.fetchSemanticsNode().boundsInRoot
        val px = rule.density.density
        r.performTouchInput {
            val p = Offset(width * .88f, height * .3f)
            down(p)
            for (k in 1..8) moveTo(p + Offset(-180.dp.toPx() * k / 8, 0f), delayMillis = 16)
        }
        rule.mainClock.advanceTimeBy(100)
        val del = delete().onLast().fetchSemanticsNode().boundsInRoot
        val now = row("Hollow Tide").fetchSemanticsNode().boundsInRoot
        assertEquals("held at the most, the row is 138 dp aside", 138f, (was.right - now.right) / px, 2f)
        assertEquals("Delete ends at the row's old right end", was.right, del.right, 2f * px)
        assertTrue("and starts where the row now ends: no gap", del.left <= now.right + 1f)
        rule.onRoot().captureRoboImage("src/test/screenshots/queue-swiped-far-412.png")
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(600)
        assertEquals("let go, it settles 92 dp aside", 92f, (was.right - row("Hollow Tide").fetchSemanticsNode().boundsInRoot.right) / px, 2f)
    }

    /** Right at the foot the list runs fast (2.4 dp a millisecond): a second there carries the row well down the list. */
    @Test fun theEdgeIsFastest() {
        fillers = 30
        show()
        val r = row("Hollow Tide").fetchSemanticsNode().boundsInRoot
        val h = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        rule.onRoot().performTouchInput {
            val p = Offset(r.left + r.width * .12f, r.top + r.height * .3f)
            down(p)
            advanceEventTime(500)
            val foot = Offset(p.x, h - 24.dp.toPx())
            for (k in 1..4) moveTo(p + (foot - p) * (k / 4f), delayMillis = 16)
            for (k in 1..20) moveTo(foot + Offset(0f, if (k % 2 == 0) 1f else 0f), delayMillis = 50)
            up()
        }
        rule.mainClock.advanceTimeBy(1_500)
        // where it landed: the highest filler number above it (android-v17's 0.9 dp/ms got about 8 rows in that second)
        row("Hollow Tide")
        val top = rule.onAllNodes(hasText("Hollow Tide", substring = true) and hasText("28 h left", substring = true) and hasClickAction()).onLast().fetchSemanticsNode().boundsInRoot.top
        val above = rule.onAllNodes(hasText("Filler ", substring = true) and hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top < top }
            .mapNotNull { n -> n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString(" ")?.let { Regex("Filler (\\d+)").find(it)?.groupValues?.get(1)?.toInt() } }
        assertTrue("landed past filler 14 (got ${above.maxOrNull()})", (above.maxOrNull() ?: 0) >= 14)
    }

    /** One tap on the open card: it folds into its row (frames of the 560 ms fold, after the 320 ms wait for a second tap). */
    @Test fun theCardFoldsIntoItsRow() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithContentDescription("Starfall Lantern, ", substring = true).onFirst()
            .performTouchInput { click(Offset(width * .7f, height * .45f)) }
        rule.mainClock.advanceTimeBy(330)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        for (ms in listOf(16, 140, 280, 420, 600)) {
            rule.mainClock.advanceTimeBy(if (ms == 16) 16 else 140)
            rule.onRoot().captureRoboImage("src/test/screenshots/queue-fold-$ms.png")
        }
        assertTrue("closed: no card says which side shows", !open("Starfall Lantern"))
    }

    /** android-v20: Shuffle picks a game that's out, says "Tonight: …" and brings its row to the middle of the list. */
    @Test fun shuffleBringsTonightsGameToTheMiddle() {
        fillers = 24
        show()
        // the clock by hand: left to itself, the test waits out the scroll and the line's 2.4 s with it
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Shuffle: pick tonight's game").performClick()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(400)
        val note = rule.onAllNodes(hasText("Tonight: ", substring = true)).fetchSemanticsNodes()
        assertEquals("one line says tonight's game", 1, note.size)
        val name = note[0].config[SemanticsProperties.Text].first().text.removePrefix("Tonight: ")
        assertTrue("a game that's out", name != "Ninefold Gate" && name != "Paper Kingdom")
        // a pick far down takes the list's own jump and then the 420 ms centring: measure once it has settled (it
        // failed 3 runs in 11 when measured at a fixed 1.2 s, the list still on its way)
        rule.mainClock.advanceTimeBy(1_600)
        // its row (or its open card) is on screen, near the middle of the window
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // (the header card's line can say the name too, L228: the row or open card is the last tappable match; a row
        // the lazy list keeps aside for reuse can still answer with no size, so only the ones on screen count)
        // (the open card, the first game's at start, names itself with no tap of its own: the biggest match on screen)
        val all = rule.onAllNodes((hasText(name, substring = true) and hasClickAction()) or hasContentDescription("$name, ", substring = true))
            .fetchSemanticsNodes()
        val it = all.filter { n -> n.boundsInRoot.height > 0f && n.boundsInRoot.bottom > root.top && n.boundsInRoot.top < root.bottom }
            .maxByOrNull { n -> n.boundsInRoot.height }
        assertTrue("the picked game ($name) is on screen: ${all.map { n -> n.boundsInRoot }}", it != null)
        val mid = it!!.boundsInRoot.center.y
        // (the last rows can't reach the middle: then the list has gone as far as it goes, as scrollIntoView does)
        val y = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val atEnd = y.value() >= y.maxValue() - 1f
        assertTrue("$name near the middle (${mid} of ${root.height}, at the end $atEnd): ${all.map { n -> n.boundsInRoot }}",
            mid > root.height * .25f && (mid < root.height * .75f || atEnd))
        assertEquals("Shuffle walked no views", "queue", nav.views["games"])
        // and the line goes after a while
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(0, rule.onAllNodes(hasText("Tonight: ", substring = true)).fetchSemanticsNodes().size)
    }

    /**
     * android-v20 review (a): a pick is done once its ring has run, so leaving the Queue and coming back never brings the
     * list back to an old pick (it stays where you left it).
     */
    @Test fun anOldPickNeverScrollsTheListAgain() {
        fillers = 24
        deep = true
        show()
        val shuf = "Shuffle: pick tonight's game"
        fun bar() = rule.onAllNodesWithContentDescription(shuf).fetchSemanticsNodes().size
        fun apply() = androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(shuf).performClick()
        apply()
        // the scroll, then the ring's 2.3 s
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals("the pick (the last filler) took the list down, past the bar", 0, bar())
        list.performScrollToIndex(0)
        rule.mainClock.advanceTimeBy(500)
        assertEquals("back at the top, Shuffle shows", 1, bar())
        nav = Nav("home")
        apply()
        rule.mainClock.advanceTimeBy(1_500)
        assertEquals("Home shows, not the Queue", 0, bar())
        nav = Nav("games", views = mapOf("games" to "queue"))
        apply()
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals("the list didn't go back to the old pick", 1, bar())
    }

    /**
     * android-v20 review (c): the "Tonight" line's time runs while an Undo bar covers its place, so it never comes back
     * once the Undo bar goes.
     */
    @Test fun tonightsLineDoesntComeBackAfterAnUndoBar() {
        show()
        swipe(row("Ninefold Gate"), -120f)
        rule.mainClock.advanceTimeBy(600)
        tapDelete()
        rule.mainClock.advanceTimeBy(800)
        assertEquals(1, rule.onAllNodes(hasText("Deleted Ninefold Gate")).fetchSemanticsNodes().size)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Shuffle: pick tonight's game").performClick()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("the Undo bar holds the place", 0, rule.onAllNodes(hasText("Tonight: ", substring = true)).fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals("the Undo bar went", 0, rule.onAllNodes(hasText("Deleted Ninefold Gate")).fetchSemanticsNodes().size)
        assertEquals("and the old line stays gone", 0, rule.onAllNodes(hasText("Tonight: ", substring = true)).fetchSemanticsNodes().size)
    }

    /** android-v20: Boss says "Boss", and with Grumble on the Shadow's HP (the backlog's hours: 22 + 40 here). */
    @Test fun bossShowsItsHpOnlyWithGrumble() {
        show()
        assertEquals(1, rule.onAllNodesWithContentDescription("Boss: the Shadow of the Backlog").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodes(hasText("Boss")).fetchSemanticsNodes().size)
    }

    @Test fun bossWithGrumbleOn() {
        grum = true
        show()
        assertEquals(1, rule.onAllNodesWithContentDescription("Boss: the Shadow of the Backlog, 62 HP").fetchSemanticsNodes().size)
    }
}
