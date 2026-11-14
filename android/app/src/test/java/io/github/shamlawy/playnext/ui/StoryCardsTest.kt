package io.github.shamlawy.playnext.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Forecast
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Recap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * Phase 2 piece 3b on the screen: a playing game's page shows its Finish forecast and, under its note, the story so far
 * once the helper answers; a finished game's Then & now takes the place of the line; a drifting game's card parks it, or
 * opens Something short in between, whose Start it puts the short game in Playing. Made-up games; pictures of the
 * playing page's progress and the finished one's are kept with the screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryCardsTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private fun noon(d: String) = Instant.parse(d + "T12:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>
    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    /** Extra fields for game b (the playing one). */
    private var storyB = ""

    private val backup get() = """
        {"rk":""}
        {"theme":"midnight","playStyle":"main","games":[
          {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":1,"colour":"#2E9E6A","genres":"Action","hours":12,"logged":4,
           "plAt":${now - 4 * day},"platforms":"PC",$storyB"wasI":[{"t":${now - day},"x":"Second island, the lighthouse"}]},
          {"id":"h","title":"Lantern Rush","state":"upcoming","date":"2026-10-30","ord":2,"colour":"#3A6FD8","hype":8},
          {"id":"a","title":"Starfall Lantern","state":"played","date":"2026-01-30","ord":3,"colour":"#3A6FD8","genres":"Action","hours":22,"hype":7,
           "cap":{"h":7,"pr":7.4,"cr":80,"st":${noon("2026-02-10")},"ad":"2026-01-02","hrs":0,"n1":{"t":${noon("2026-02-11")},"x":"Just met the ferryman"},"at":${noon("2026-03-01")},"e0":"2026-02-27"},
           "wasI":[{"t":${noon("2026-02-11")},"x":"Just met the ferryman"},{"t":${noon("2026-02-26")},"x":"Final boss next"}]},
          {"id":"d","title":"Iron Tide","state":"playing","date":"2026-01-01","ord":4,"colour":"#B0412E","hours":20,"genres":"RPG, Action",
           "lgH":[[${noon("2026-10-07")},3],[${noon("2026-10-09")},3],[${noon("2026-10-11")},3],[${noon("2026-10-13")},1],[${noon("2026-10-18")},0.5]]},
          {"id":"x","title":"Fallen Kite","state":"dropped","date":"2025-01-01","ord":5,"genres":"RPG, Action"},
          {"id":"s","title":"Pocket Comet","state":"backlog","date":"2026-01-01","ord":6,"colour":"#8A5CD0","hours":5,"genres":"Puzzle"}
        ],"duels":[],"played":[{"id":"a","real":8,"t":"2026-03-01"}]}
    """.trimIndent()

    private val locale0 = java.util.Locale.getDefault()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        java.util.Locale.setDefault(java.util.Locale.UK) // the expectations are in day-month order
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        Games.clock = { now }
        Games.zone = { ZoneId.of("UTC") }
        load()
        Recap.post = { """{"recap":"You reached the island chain and lit the first beacon; the lighthouse keeper sent you north.","where":"the lighthouse","sure":0.9}""" }
    }

    private fun load() {
        val lib = Games::class.java.getDeclaredMethod("build", String::class.java).apply { isAccessible = true }.invoke(Games, backup) as Library
        state.value = Games::class.java.getDeclaredMethod("ready", Library::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(Games, lib, now - 3_600_000L) as Games.State.Ready
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        put("day", null)
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
        Moment.reset()
        Recap.forget()
        Forecast.forget()
        java.util.Locale.setDefault(locale0)
    }

    private val ready get() = state.value as Games.State.Ready
    private var nav by mutableStateOf(Nav("games", listOf("game:b")))

    private fun show(start: List<String>) {
        nav = Nav("games", start)
        rule.setContent {
            val games by Games.state.collectAsState()
            val lib = (games as Games.State.Ready).lib
            Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        rule.waitForIdle()
    }

    private fun waitFor(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!ok()) {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(what, System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun has(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun aPlayingGameShowsItsForecastAndTheStorySoFar() {
        show(listOf("game:b"))
        rule.onNodeWithText("Going by your pace so far.", substring = true).performScrollTo()
        assertTrue("4h in 4 days: 8h left at 1h a day", has("· 8h left"))
        assertTrue("the hyped game out before then", has("Lantern Rush comes out 30\u00A0Oct, before you'll finish."))
        assertTrue("the track says it too", has("~4\u00A0Nov"))
        rule.mainClock.advanceTimeBy(700)
        waitFor("the recap came in") { has("The story so far · the lighthouse") }
        rule.onNodeWithText("The story so far · the lighthouse").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/story-playing-412.png")
    }

    /** Day before month on a US phone too, like the page's other dates ("Out 11 May"), so one card never mixes orders. */
    @Test fun datesKeepTheAppsOrder() {
        val utc = ZoneId.of("UTC")
        assertEquals("4\u00A0Nov", io.github.shamlawy.playnext.data.Story.nice(noon("2026-11-04"), utc, java.util.Locale.US))
        assertEquals("3\u00A0Mar\u00A02027", io.github.shamlawy.playnext.data.Story.rdNice("2027-03-03", now, utc, java.util.Locale.US))
    }

    /** The web's backup can hold a failed recap: once it's 6 hours old the page asks again and the card comes back. */
    @Test fun anOldFailedRecapIsAskedAgain() {
        storyB = """"wiStory":{"t":${now - day},"x":"","err":"helper said no","at":${now - 7 * 3_600_000L}},"""
        load()
        show(listOf("game:b"))
        assertFalse("an error shows no card", has("Remembering the story so far"))
        rule.mainClock.advanceTimeBy(700)
        waitFor("the recap came in") { has("The story so far · the lighthouse") }
    }

    @Test fun aFinishedGameShowsThenAndNow() {
        show(listOf("game:a"))
        rule.onNodeWithText("vs my finish forecast (27\u00A0Feb)").performScrollTo()
        assertTrue(has("2 days late"))
        assertTrue(has("Nexi's guess (off by 0.6)"))
        assertTrue("the first note in quotes", has("“Just met the ferryman”"))
        assertTrue("the last one too", has("“Final boss next”"))
        assertTrue("Then & now replaces the line", !has("Finished in "))
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/story-played-412.png")
    }

    @Test fun parkItSendsADriftingGameBackToTheBacklog() {
        show(listOf("game:d"))
        rule.onNodeWithText("You're drifting from this one").performScrollTo()
        assertTrue(has("your sessions shrank from about 3h to 45 min"))
        rule.onNode(hasText("Park it") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        waitFor("the practice copy parked it") { ready.lib.games.first { it.id == "d" }.rawState == "backlog" }
    }

    @Test fun somethingShortStartsAShortGame() {
        show(listOf("game:d"))
        rule.onNode(hasText("Something short in between") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("game:d", "short:d"), nav.stack)
        rule.mainClock.advanceTimeBy(400)
        assertTrue(has("Pocket Comet"))
        assertTrue(has("about 5h to beat · Puzzle"))
        rule.onNode(hasText("Start it") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals("Start it closes the sheet", listOf("game:d"), nav.stack)
        waitFor("the short one is playing") { ready.lib.games.first { it.id == "s" }.rawState == "playing" }
        assertEquals("s", io.github.shamlawy.playnext.data.Backup.str(ready.lib.games.first { it.id == "d" }["dwBack"]))
    }
}
