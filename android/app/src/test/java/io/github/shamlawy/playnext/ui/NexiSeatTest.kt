package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Shx
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
import java.time.ZoneId
import java.util.TimeZone

/**
 * Nexi's seat (Phase 1 piece 3; android/parity/1-map/2-card.md §6): unfolded he sits at the rail's foot on every tab,
 * folded there's none; one tap hides the rail and he floats, one tap on him floating brings it back on his side; a
 * double tap is his menu's (for now he says so); dragged, he snaps to the nearest side; leaving the seat he says the
 * day's brief once a day; the seat switched off leaves the rail without him. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class NexiSeatTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-07T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private fun lib(extra: String) = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":[],"deckOff":true$extra,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"hours":12},
               {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-11-10","ord":2},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-12-14","ord":3}
             ],"duels":[],"played":[]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.prefs.edit().clear().commit()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w859dp-h1000dp-xxhdpi")
    }

    @After
    fun tearDown() {
        state.value = Games.State.Starting
    }

    private fun show(extra: String = "", tab: String = "games") {
        val l = lib(extra)
        state.value = Games.State.Ready(l, now - 3_600_000L, cards = Shx.all(l, now, ZoneId.of("UTC")))
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(Nav(tab, views = mapOf("games" to "queue")), games, go = {}) }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun settle(ms: Long = 1_500) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    private fun nexi(): Rect = rule.onNode(hasContentDescription("Nexi: ", substring = true)).fetchSemanticsNode().boundsInRoot
    private fun nexiCount() = rule.onAllNodes(hasContentDescription("Nexi: ", substring = true)).fetchSemanticsNodes().size
    private fun railSettings(): Rect = rule.onNode(hasContentDescription("Settings")).fetchSemanticsNode().boundsInRoot
    private fun floating() = rule.onAllNodes(hasContentDescription("Nexi: show the rail")).fetchSemanticsNodes().isNotEmpty()
    private fun tapNexi() {
        rule.onNode(hasContentDescription("Nexi: ", substring = true)).performTouchInput { click() }
        settle()
    }
    private fun says(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private val width get() = rule.onRoot().fetchSemanticsNode().boundsInRoot.width

    private fun seatedOn(t: String) {
        show(tab = t)
        assertEquals("one Nexi on $t", 1, nexiCount())
        val n = nexi()
        val s = railSettings()
        assertTrue("in the rail's middle on $t: $n vs $s", kotlin.math.abs(n.center.x - s.center.x) < 2f)
        assertTrue("under Settings on $t", n.top > s.bottom)
        assertTrue("seated, not floating", !floating())
    }

    @Test fun seatedAtTheRailsFootOnTheQueue() = seatedOn("games")

    @Test fun seatedOnHomeToo() = seatedOn("home")

    @Test fun foldedThereIsNoNexi() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        show()
        assertEquals(0, nexiCount())
    }

    @Test fun theSeatSwitchedOffLeavesTheRailWithoutHim() {
        show(""","nxSeatOff":true""")
        assertEquals(0, nexiCount())
        railSettings()
    }

    @Test fun oneTapHidesTheRailAndHeFloatsThenOneTapBringsItBack() {
        show()
        val seat = nexi()
        tapNexi()
        // his line comes 1.2 s after he leaves the seat
        settle(500)
        assertTrue("floating once the rail is hidden", floating())
        val f = nexi()
        assertTrue("he glided out of the seat: $seat → $f", f.top < seat.top - 40f)
        assertTrue("34 from the right edge", kotlin.math.abs(width - f.right - 34f * density()) < 2f)
        assertTrue("the rail took its buttons away", railSettings().left >= width)
        assertTrue("leaving the seat he says the day's brief", says("Today's brief"))
        settle(6_000)
        assertTrue("and it goes after 5.5 s", !says("Today's brief"))
        tapNexi()
        assertTrue("seated again", !floating())
        val back = nexi()
        assertTrue("back in his seat", kotlin.math.abs(back.center.x - seat.center.x) < 2f && kotlin.math.abs(back.top - seat.top) < 2f)
        tapNexi()
        settle(3_000)
        assertTrue("the brief comes once a day", !says("Today's brief"))
    }

    @Test fun aDoubleTapIsHisMenusNotAHide() {
        show()
        rule.onNode(hasContentDescription("Nexi: ", substring = true)).performTouchInput {
            click()
            advanceEventTime(120)
            click()
        }
        settle()
        assertTrue("still seated", !floating())
        assertTrue("he says his menu is coming", says(MENU_LATER))
    }

    /** Seated, a finger on him isn't a grab (web: the seat's handler stops the free one): his line stays while it's down. */
    @Test fun aTouchOnSeatedNexiKeepsHisLine() {
        show()
        rule.onNode(hasContentDescription("Nexi: ", substring = true)).performTouchInput {
            click()
            advanceEventTime(120)
            click()
        }
        settle()
        assertTrue(says(MENU_LATER))
        rule.onNode(hasContentDescription("Nexi: ", substring = true)).performTouchInput { down(center) }
        settle(200)
        assertTrue("his line stays under a seated touch", says(MENU_LATER))
        rule.onNode(hasContentDescription("Nexi: ", substring = true)).performTouchInput { up() }
        settle(100)
        assertTrue("still seated", !floating())
    }

    @Test fun draggedHeSnapsToTheNearestSideAndTheRailOpensThere() {
        show(""","railHide":true""")
        settle()
        assertTrue("the backup's hidden rail: he floats from the start", floating())
        val f = nexi()
        rule.onNode(hasContentDescription("Nexi: ", substring = true)).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(-25f * density(), 0f)) }
            up()
        }
        settle()
        val left = nexi()
        assertTrue("snapped 34 from the left edge: $left", kotlin.math.abs(left.left - 34f * density()) < 2f)
        assertTrue("about the same height", kotlin.math.abs(left.top - f.top) < 4f * density())
        tapNexi()
        assertTrue("seated again", !floating())
        assertTrue("the rail came back on his side", railSettings().right < width / 2)
        assertTrue("his seat is in it", nexi().center.x < width / 2)
    }

    /** Floating, mid-line: his face, ring, badge and his line beside him (the loops sit still under test). */
    @Test fun floating859() {
        show(""","railHide":true,"userName":"Sam"""")
        settle(2_000)
        assertTrue(says("Today's brief, Sam"))
        rule.onRoot().captureRoboImage("src/test/screenshots/nexi-float-859.png")
    }

    private fun density() = rule.density.density
}
