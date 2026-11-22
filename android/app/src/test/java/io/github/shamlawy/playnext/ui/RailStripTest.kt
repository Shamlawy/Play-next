package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assert
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.time.ZoneId
import java.util.TimeZone

/**
 * The unfolded rail's live strip and dice (Phase 3 piece 6, android-v51): the tiles drift upward on their own, a tap on
 * one opens its game, a drag scrubs the strip and never opens anything, the backlog's tile goes to the Games tab, calm
 * keeps it still, and the dice plays Pick for me (dimmed with under two games). Shot: the rail at 859. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RailStripTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-11-07T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private fun lib(extra: String = "", games: String = GAMES) = Library(
        Json.parseToJsonElement("""{"theme":"midnight","lists":[],"userName":"Sam","duels":[],"played":[]$extra,"games":[$games]}""").jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.prefs.edit().clear().commit()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Reveal.reset()
        RuntimeEnvironment.setQualifiers("w859dp-h1000dp-xxhdpi")
    }

    @After
    fun tearDown() {
        Reveal.reset()
        state.value = Games.State.Starting
    }

    /**
     * Shows the Shell at 859. [held] stops the test clock first: while it advances by itself the test rule cancels every
     * endless loop (Compose's InfiniteAnimationPolicy in tests), so the drift only runs on a held clock.
     */
    private fun show(l: Library = lib(), held: Boolean = false, built: Boolean = true): () -> Nav {
        state.value = Games.State.Ready(l, now - 3_600_000L, cards = if (built) Shx.all(l, now, ZoneId.of("UTC")) else null)
        var nav by mutableStateOf(Nav("home"))
        if (held) rule.mainClock.autoAdvance = false
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        if (held) step(1_000) else {
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(1_000)
        }
        return { nav }
    }

    private fun step(ms: Long) {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(ms)
    }

    /** The first copy's tile for [title] (the repeated copies are hidden from TalkBack, so there's one). */
    private fun tile(title: String) = rule.onNode(hasContentDescription("$title: ", substring = true))
    private fun top(title: String) = tile(title).fetchSemanticsNode().positionInRoot.y

    @Test fun theStripShowsYourGamesAndATapOpensOne() {
        val nav = show()
        // playing (how far: 3 of 12 hours), the next release, then the rest as Home ranks them, then the backlog
        tile("Copper Meadow").assertExists()
        rule.onNode(hasContentDescription("Copper Meadow: 25% ", substring = true)).assertExists()
        rule.onNode(hasContentDescription("Ninefold Gate: 3d until out", substring = true)).assertExists()
        rule.onNodeWithContentDescription("12h backlog").assertExists()
        rule.mainClock.autoAdvance = false
        tile("Ninefold Gate").performTouchInput { click(Offset(width / 2f, 10f)) }
        step(500)
        assertEquals(listOf("game:c"), nav().stack)
    }

    @Test fun itDriftsUpwardOnItsOwn() {
        show(held = true)
        val y0 = top("Copper Meadow")
        step(2_000)
        val y1 = top("Copper Meadow")
        val d = rule.density.density
        // 15 dp a second
        assertEquals("it drifted about 30 dp up", 30f, (y0 - y1) / d, 3f)
    }

    @Test fun aDragScrubsItAndNeverOpensAGame() {
        val nav = show()
        rule.mainClock.autoAdvance = false
        val y0 = top("Ninefold Gate")
        val strip = tile("Ninefold Gate")
        strip.performTouchInput { down(Offset(width / 2f, 10f)) }
        // a frame between moves, so the strip has moved under the finger before the next one (a finger read in the
        // moving layer would see less than it moved)
        for (dy in listOf(-20f, -20f, -20f, -20f)) {
            step(16)
            strip.performTouchInput { moveBy(Offset(0f, dy)) }
        }
        step(16)
        val withFinger = y0 - top("Ninefold Gate")
        strip.performTouchInput { up() }
        step(500)
        assertTrue("nothing opened", nav().stack.isEmpty())
        assertEquals("the strip followed the finger 80 px up", 80f, withFinger, 3f)
        // held for 2 s after the lift, then it carries on
        val held = top("Ninefold Gate")
        step(1_000)
        assertEquals("still held a second after the lift", held, top("Ninefold Gate"), 1f)
        step(3_000)
        assertTrue("drifting again", top("Ninefold Gate") < held - 5f)
    }

    @Test fun theBacklogTileGoesToTheGamesTab() {
        // two games, so the backlog's tile is in sight: playing, up next, the backlog
        val nav = show(lib(games = GAMES.substringBefore(",\n            {\"id\":\"c\"")))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("12h backlog").performTouchInput { click(Offset(width / 2f, 10f)) }
        step(500)
        assertEquals("games", nav().tab)
        assertTrue(nav().stack.isEmpty())
    }

    @Test fun calmKeepsItStill() {
        show(lib(""","motion":false"""), held = true)
        val y0 = top("Copper Meadow")
        step(3_000)
        assertEquals("no drift when calm", y0, top("Copper Meadow"), .5f)
    }

    @Test fun theDicePlaysPickForMe() {
        val nav = show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Pick one for me").performClick()
        step(32)
        val run = Reveal.run
        assertNotNull("the rail's dice starts the reveal", run)
        step(1_000)
        assertEquals("its page opens behind it", listOf("game:" + run!!.g.id), nav().stack)
    }

    @Test fun theDiceIsDimmedWithOneGame() {
        val nav = show(lib(games = """{"id":"a","title":"Starfall Lantern","state":"backlog","hype":8,"hours":12}"""))
        val dice = rule.onNodeWithContentDescription("Pick one for me")
        dice.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        dice.performClick()
        rule.waitForIdle()
        assertNull(Reveal.run)
        assertTrue(nav().stack.isEmpty())
    }

    @Test fun withoutTheDayBuildTheStripAndDiceStillWork() {
        // Ready before the off-Main build has made Home (no cards): the rail makes its own, as Home's screen does
        val nav = show(built = false)
        tile("Copper Meadow").assertExists()
        val dice = rule.onNodeWithContentDescription("Pick one for me")
        dice.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
        rule.mainClock.autoAdvance = false
        dice.performClick()
        step(32)
        assertNotNull("the dice starts the reveal", Reveal.run)
        step(1_000)
        assertEquals(1, nav().stack.size)
    }

    @Test fun rail859() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onRoot().captureRoboImage("src/test/screenshots/rail-strip-859.png")
    }

    private companion object {
        const val GAMES = """
            {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","hype":8,"hours":12,"colour":"#7A3CFF"},
            {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-03-01","hype":5,"hours":12,"logged":3,"colour":"#E0662B"},
            {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-10","hype":7,"colour":"#2BB673"},
            {"id":"d","title":"Moth Bell","state":"upcoming","date":"2027-01-20","hype":3,"colour":"#4DA3FF"}
        """
    }
}
