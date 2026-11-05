package io.github.shamlawy.playnext.ui

import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
 * Hold and drag in the Queue (index.html 8965-8991, 7417-7474; android/parity/1-map/1-list.md 9): a 380 ms hold in your
 * own order lifts a row, the finger takes it to a new place (the practice order, kept in memory); in a sorted order the
 * hold opens the game's page; a short press or a scroll is no hold. And the open card closes at once after its wait.
 * Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class QueueDragTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private var extra = ""
    private var more = ""
    private val lib get() = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight",$extra"bgGrad":30,"deckOff":true,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"platforms":"PS5, Switch 2, PC, Wii","hours":22,"hype":8,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"logged":12,"added":"2026-09-02"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"colour":"#B06AD8","genres":["RPG","Strategy"],"platforms":"PS4, PS5, Vita","added":"2026-10-25"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9","genres":["Strategy","RPG"],"added":"2026-01-10",
                "mine":{"story":9,"music":8.5,"gameplay":8,"replay":6,"visuals":9,"world":8,"chars":7.5,"pace":7}}$more
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

    // the open card is named "<title>, <headline>" and says which side shows (the header card's covers are named by title)
    private fun card(name: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription("$name, ", substring = true).also { it.performScrollTo() }
    private fun open(name: String) = rule.onAllNodesWithContentDescription(name, substring = true).fetchSemanticsNodes()
        .any { SemanticsProperties.StateDescription in it.config }
    /** A closed row: the last tappable thing that says the name (the header card's line comes first). */
    private fun row(name: String, head: String): SemanticsNodeInteraction {
        // the Queue is a lazy list: bring the row on screen first (only what's on screen is drawn); a row says its name
        // and its headline (the header card's line says only the name)
        val m = hasText(name, substring = true) and hasText(head, substring = true) and hasClickAction()
        rule.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(m)
        // then all of it (a row cut off at the screen's edge would take the tap off screen)
        return rule.onAllNodes(m).onLast().also { it.performScrollTo() }
    }
    private fun side(name: String) = card(name).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    /** Two taps on the card's picture (clear of the box art), [gap] ms apart. */
    private fun taps(name: String, gap: Long = 90) = card(name).performTouchInput {
        click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f))
        advanceEventTime(gap)
        click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f))
    }

    /** A finger on a row's top left: a row scrolled to the screen's foot sits under the floating view pill's middle. */
    private fun tapRow(r: SemanticsNodeInteraction) = r.performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)) }

    /** Whether [a] is above [b] on screen, both read at the same scroll (after bringing [b] into view). */
    private fun above(a: Pair<String, String>, b: Pair<String, String>): Boolean {
        row(b.first, b.second)
        fun top(p: Pair<String, String>) = rule.onAllNodes(hasText(p.first, substring = true) and hasText(p.second, substring = true) and hasClickAction())
            .onLast().fetchSemanticsNode().boundsInRoot.top
        return top(a) < top(b)
    }
    private val hollow = "Hollow Tide" to "28 h left"
    private val ninefold = "Ninefold Gate" to "Out in 18 days"

    /** Hold a closed row still for [holdMs], then take it [rows] row heights down (folded: 104 + 12 dp) and let go. */
    private fun drag(r: SemanticsNodeInteraction, rows: Float, holdMs: Long = 500) = r.performTouchInput {
        val p = androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)
        down(p)
        advanceEventTime(holdMs)
        val pitch = 116.dp.toPx() * rows
        for (k in 1..8) moveTo(p + androidx.compose.ui.geometry.Offset(0f, pitch * k / 8), delayMillis = 32)
        up()
    }

    @Test fun aHeldRowMovesToWhereItIsLetGo() {
        show()
        row("Ninefold Gate", "Out in 18 days")
        assertTrue("before: Hollow Tide is above Ninefold Gate", above(hollow, ninefold))
        drag(row("Hollow Tide", "28 h left"), 1.3f)
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("after: Ninefold Gate is above Hollow Tide", !above(hollow, ninefold))
        assertTrue("a drag opens no card", !open("Hollow Tide"))
        assertTrue("and the open card stays open", open("Starfall Lantern"))
    }

    /** The picture mid-drag: the held row in its ring over the row that stepped aside (folded and unfolded). */
    private fun heldShot(width: Int) {
        show(width)
        row(ninefold.first, ninefold.second)
        val r = row(hollow.first, hollow.second)
        val pitch = if (width < 600) 116 else 150
        r.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)); advanceEventTime(500)
            for (k in 1..6) moveBy(androidx.compose.ui.geometry.Offset(0f, pitch.dp.toPx() * .2f), delayMillis = 32)
        }
        rule.mainClock.advanceTimeBy(400)
        rule.onRoot().captureRoboImage("src/test/screenshots/queue-held-$width.png")
        rule.onRoot().performTouchInput { up() }
    }
    @Test fun held412() = heldShot(412)
    @Test fun held859() = heldShot(859)

    @Test fun aShortPressIsNoHold() {
        show()
        drag(row("Hollow Tide", "28 h left"), 1.3f, holdMs = 120)
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("a move before the hold is a scroll: the order stays", above(hollow, ninefold))
    }

    @Test fun aHoldInASortedOrderOpensTheGamesPage() {
        extra = "\"sort\":\"hype\","
        show()
        row("Hollow Tide", "28 h left").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)); advanceEventTime(500); up()
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("game:b"), nav.stack)
        assertTrue("the lift taps nothing: no card opened", !open("Hollow Tide"))
    }

    /** A finger that stops a fling is the list's: held still for 500 ms in a sorted order it opens no game page. */
    @Test fun aFingerStoppingAFlingNeverHolds() {
        extra = "\"sort\":\"hype\","
        more = (1..12).joinToString("") { k ->
            ",{\"id\":\"m$k\",\"title\":\"Filler Quest $k\",\"state\":\"backlog\",\"date\":\"2026-0${1 + k % 9}-10\",\"ord\":${6 + k},\"colour\":\"#4A7A5C\",\"genres\":[\"Puzzle\"],\"hype\":${k % 7 + 1},\"added\":\"2026-10-01\"}"
        }
        show()
        val scroller = rule.onAllNodes(hasScrollToNodeAction()).onLast()
        fun firstTop() = rule.onAllNodes(hasText("Filler Quest", substring = true) and hasClickAction()).fetchSemanticsNodes()
            .minOf { it.boundsInRoot.top }
        val before = firstTop()
        scroller.performTouchInput {
            // a quick flick up, let go, and 30 ms later a finger lands mid-list and stays still
            swipeUp(startY = height * .85f, endY = height * .25f, durationMillis = 60)
            advanceEventTime(30)
            down(center)
            advanceEventTime(500)
            up()
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("the flick scrolled the list", firstTop() < before)
        assertEquals("no game page opened", emptyList<String>(), nav.stack)
    }

    @Test fun oneTapClosesTheCardRightAfterItsWait() {
        show()
        rule.mainClock.autoAdvance = false
        card("Starfall Lantern").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f)) }
        rule.mainClock.advanceTimeBy(250)
        assertTrue("still open while it waits for a second tap", open("Starfall Lantern"))
        rule.mainClock.advanceTimeBy(90)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(32)
        assertTrue("closed into its row once the wait is over, no fade first", !open("Starfall Lantern"))
    }
}
