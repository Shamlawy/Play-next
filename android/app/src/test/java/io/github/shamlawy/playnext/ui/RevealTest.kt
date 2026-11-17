package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
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
import java.util.TimeZone

/**
 * Pick for me's reveal (Phase 3 piece 2, android-v47): the dice plays it over Home, its sounds come in the web's order,
 * the page opens behind it at 950 ms and it breaks by itself at 1550 ms; a tap or back breaks it at once (back never
 * closes what's under it), a swipe over it never walks the tabs, and calm opens the page with no reveal. Shots: the
 * scene with its name and tag, and the shards flying. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RevealTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-11-07T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private val heard = mutableListOf<Sfx>()

    private fun lib(extra: String = "") = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":[],"userName":"Sam"$extra,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","hype":8,"colour":"#7A3CFF"},
               {"id":"b","title":"Copper Meadow","state":"playing","hype":5,"colour":"#E0662B"},
               {"id":"e","title":"Moth Bell","state":"backlog","hype":3,"colour":"#2BB673"}
             ],"duels":[],"played":[]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.prefs.edit().clear().commit()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Reveal.reset()
        Sounds.heard = { heard += it }
    }

    @After
    fun tearDown() {
        Sounds.heard = null
        Reveal.reset()
        state.value = Games.State.Starting
    }

    private fun show(l: Library = lib(), width: Int = 412): () -> Nav {
        RuntimeEnvironment.setQualifiers("w${width}dp-h915dp-xxhdpi")
        state.value = Games.State.Ready(l, now - 3_600_000L)
        var nav by mutableStateOf(Nav("home"))
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        rule.waitForIdle()
        // a tap right after a step opens lands on nothing (Shell's settle)
        rule.mainClock.advanceTimeBy(1_000)
        return { nav }
    }

    /** Taps the dice with the clock held, so each moment of the reveal can be looked at. */
    private fun roll() {
        rule.mainClock.autoAdvance = false
        heard.clear()
        rule.onNodeWithContentDescription("I'll pick one").performClick()
        step(32)
    }

    private fun step(ms: Long) {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(ms)
    }

    @Test fun theDicePlaysTheRevealAndThePageOpensBehindIt() {
        val nav = show()
        rule.mainClock.autoAdvance = false
        heard.clear()
        rule.onNodeWithContentDescription("I'll pick one").performClick()
        step(32)
        val run = Reveal.run
        assertNotNull("the dice starts the reveal", run)
        assertTrue("nothing opens yet", nav().stack.isEmpty())
        rule.onNodeWithContentDescription("Tonight's pick: " + run!!.g.title).assertExists()
        step(1_000)
        assertEquals("the page opens behind it at 950 ms", listOf("game:" + run.g.id), nav().stack)
        assertNotNull("still showing over it", Reveal.run)
        step(2_000)
        assertNull("gone once the shards have flown", Reveal.run)
        assertEquals(listOf("game:" + run.g.id), nav().stack)
        assertEquals("the web's sounds, in order", listOf(Sfx.Hit, Sfx.Slash, Sfx.Tick, Sfx.Slam, Sfx.Crack, Sfx.Shatter), heard.filter { it != Sfx.Tap && it != Sfx.Open })
    }

    @Test fun aTapBreaksItAtOnceAndOpensThePage() {
        val nav = show()
        roll()
        val run = Reveal.run!!
        step(300)
        rule.onNodeWithContentDescription("Tonight's pick: " + run.g.title).performTouchInput { down(center); up() }
        step(32)
        assertEquals("a tap opens the page at once", listOf("game:" + run.g.id), nav().stack)
        step(1_200)
        assertNull(Reveal.run)
        // the slash had played; the tick and the slam it skipped never come
        assertEquals(listOf(Sfx.Hit, Sfx.Slash, Sfx.Crack, Sfx.Shatter), heard.filter { it != Sfx.Tap && it != Sfx.Open })
    }

    @Test fun backBreaksItAndNeverClosesWhatsUnder() {
        val nav = show()
        roll()
        val run = Reveal.run!!
        step(200)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        step(1_200)
        assertNull(Reveal.run)
        assertEquals("back broke the reveal, the page it opened stays", listOf("game:" + run.g.id), nav().stack)
    }

    @Test fun aSwipeOverItNeverWalksTheTabs() {
        val nav = show()
        roll()
        val run = Reveal.run!!
        step(200)
        rule.onNodeWithContentDescription("Tonight's pick: " + run.g.title).performTouchInput { swipeLeft() }
        step(1_500)
        assertEquals("home", nav().tab)
    }

    @Test fun calmOpensThePageWithNoReveal() {
        val nav = show(lib(""","motion":"off""""))
        rule.onNodeWithContentDescription("I'll pick one").performClick()
        rule.waitForIdle()
        assertNull("no reveal when calm", Reveal.run)
        assertEquals(1, nav().stack.size)
        assertTrue(nav().stack.single().startsWith("game:"))
    }

    @Test fun shots412() = shots(412)

    @Test fun shots859() = shots(859)

    private fun shots(w: Int) {
        show(width = w)
        roll()
        step(1_300)
        rule.onRoot().captureRoboImage("src/test/screenshots/reveal-$w.png")
        rule.onNodeWithContentDescription("Tonight's pick: " + Reveal.run!!.g.title).performTouchInput { down(center); up() }
        step(110 + 32 + 260)
        rule.onRoot().captureRoboImage("src/test/screenshots/reveal-shards-$w.png")
    }
}
