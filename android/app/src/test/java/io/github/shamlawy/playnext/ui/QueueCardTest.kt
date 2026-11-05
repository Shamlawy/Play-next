package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
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
 * The open Queue card (Phase 1 piece 2, android/parity/1-map/2-card.md): the first game shown is open at start, a tap
 * on a row opens its card, one tap closes it after 320 ms (a second tap in time turns it instead), two taps turn it
 * and two more while it turns send it back (the owner, Nov 3), the box art opens the game's page. Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class QueueCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private val lib = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,"deckOff":true,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"platforms":"PS5, Switch 2, PC, Wii","hours":22,"hype":8,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"logged":12,"added":"2026-09-02"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"colour":"#B06AD8","genres":["RPG","Strategy"],"platforms":"PS4, PS5, Vita","added":"2026-10-25"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9","genres":["Strategy","RPG"],"added":"2026-01-10",
                "mine":{"story":9,"music":8.5,"gameplay":8,"replay":6,"visuals":9,"world":8,"chars":7.5,"pace":7}}
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

    @Test fun theFirstGameShownIsOpenAtStart() {
        show()
        assertTrue("the first game's card is open", open("Starfall Lantern, Ready to play"))
        assertTrue("the next game is a closed row", !open("Hollow Tide"))
        assertEquals("front", side("Starfall Lantern"))
    }

    @Test fun oneTapClosesItAfterWaitingForASecond() {
        show()
        rule.mainClock.autoAdvance = false
        card("Starfall Lantern").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f)) }
        rule.mainClock.advanceTimeBy(200)
        assertTrue("still open while it waits for a second tap", open("Starfall Lantern"))
        rule.mainClock.advanceTimeBy(800)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("closed into its row", !open("Starfall Lantern"))
    }

    @Test fun aTurnedCardKeepsItsSideWhenItScrollsAwayAndBack() {
        show()
        taps("Starfall Lantern"); rule.mainClock.advanceTimeBy(2_500)
        assertEquals("back", side("Starfall Lantern"))
        row("Ninefold Gate", "Out in 18 days")
        rule.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("Customise", substring = true))
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("still on its back", "back", side("Starfall Lantern"))
    }

    @Test fun aTapOnARowOpensItsCardAndClosesTheOther() {
        show()
        tapRow(row("Hollow Tide", "28 h left"))
        rule.mainClock.advanceTimeBy(2_500)
        assertTrue(open("Hollow Tide, 28 h left"))
        assertTrue(!open("Starfall Lantern"))
    }

    @Test fun twoTapsTurnItAndTwoMoreWhileItTurnsSendItBack() {
        show()
        rule.mainClock.autoAdvance = false
        taps("Starfall Lantern")
        rule.mainClock.advanceTimeBy(600)
        assertEquals("on its back", "back", side("Starfall Lantern"))
        rule.mainClock.advanceTimeBy(2_000)
        // back again, then a double tap mid-turn: it heads back to the back without landing on the front
        taps("Starfall Lantern")
        rule.mainClock.advanceTimeBy(150)
        assertEquals("front", side("Starfall Lantern"))
        taps("Starfall Lantern")
        rule.mainClock.advanceTimeBy(600)
        assertEquals("sent back the way it came", "back", side("Starfall Lantern"))
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("a turn never closes the card", open("Starfall Lantern"))
    }

    @Test fun theBoxArtOpensTheGamesPage() {
        show()
        // turned over and back first: the hidden back must not catch the box art's tap (review on #695)
        taps("Starfall Lantern"); rule.mainClock.advanceTimeBy(2_500)
        taps("Starfall Lantern"); rule.mainClock.advanceTimeBy(2_500)
        assertEquals("front", side("Starfall Lantern"))
        rule.onNodeWithContentDescription("Open Starfall Lantern's own page").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("game:a"), nav.stack)
        assertTrue("the card stays open under the page", open("Starfall Lantern"))
    }

    // ---- pictures: the front and the back, folded and unfolded

    private fun shot(name: String, width: Int, game: String, back: Boolean, head: String = "") {
        show(width)
        if (game != "Starfall Lantern") {
            tapRow(row(game, head))
            rule.mainClock.advanceTimeBy(2_500)
        }
        card(game)
        if (back) { taps(game); rule.mainClock.advanceTimeBy(2_500) }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name-$width.png")
    }

    @Test fun front412() = shot("card-front", 412, "Starfall Lantern", false)
    @Test fun front859() = shot("card-front", 859, "Starfall Lantern", false)
    @Test fun back412() = shot("card-back", 412, "Starfall Lantern", true)
    @Test fun back859() = shot("card-back", 859, "Starfall Lantern", true)
    @Test fun coming412() = shot("card-coming", 412, "Ninefold Gate", false, "Out in 18 days")
    @Test fun playing412() = shot("card-playing-back", 412, "Hollow Tide", true, "28 h left")
}
