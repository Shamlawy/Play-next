package io.github.shamlawy.playnext.ui

import android.app.Application
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Notes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * Score it and Where was I on the screen (Phase 2 piece 3a): a played game's Score it opens the sheet over its page, a
 * tap on a score writes it into the practice copy and the sheet closes itself; back closes the sheet, not the page; a
 * playing game's Where was I card opens the note sheet and Save note puts the note on the page. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ScoreNoteSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>
    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = """
        {"rk":""}
        {"theme":"midnight","games":[
          {"id":"a","title":"Starfall Lantern","state":"played","date":"2026-01-30","ord":1,"colour":"#3A6FD8","genres":"Action","hours":22,"hype":7},
          {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":2,"colour":"#2E9E6A","genres":"Action","hours":12}
        ],"duels":[],"played":[{"id":"a","real":0,"t":"2026-03-01"}]}
    """.trimIndent()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        Games.clock = { now }
        Games.zone = { ZoneId.of("UTC") }
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
    }

    private val ready get() = state.value as Games.State.Ready
    private var nav by mutableStateOf(Nav("games", listOf("game:a")))
    private lateinit var back: OnBackPressedDispatcher

    private fun show(start: List<String>) {
        nav = Nav("games", start)
        rule.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
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

    private fun openScore() {
        rule.onNode(hasText("Score it") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("game:a", "score:a"), nav.stack)
        rule.mainClock.advanceTimeBy(400)
    }

    @Test fun aTapOnAScoreWritesItAndTheSheetClosesItself() {
        show(listOf("game:a"))
        openScore()
        rule.onNodeWithText("Played, not scored yet · hype was 7").assertExists()
        rule.onNodeWithContentDescription("8½").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        assertEquals("the sheet closes 260 ms after the tap", listOf("game:a"), nav.stack)
        waitFor("the practice copy has the score") { ready.lib.played.firstOrNull { it.id == "a" }?.real == 8.5 }
        assertTrue("the page's button now says the score", rule.onAllNodes(hasText("8.5") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun backClosesTheSheetNotThePage() {
        show(listOf("game:a"))
        openScore()
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
        assertEquals(listOf("game:a"), nav.stack)
        assertTrue("nothing written", ready.lib.played.single { it.id == "a" }.real == 0.0)
    }

    @Test fun theWhereWasICardOpensTheNoteSheetAndSaveShowsTheNote() {
        show(listOf("game:b"))
        rule.onNodeWithText("Where are you up to?").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("game:b", "note:b"), nav.stack)
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithContentDescription("Your note").performTextInput("Second island, the lighthouse")
        rule.onNodeWithText("Save note").performClick()
        rule.waitForIdle()
        assertEquals("Save note closes the sheet", listOf("game:b"), nav.stack)
        waitFor("the note is in the practice copy") { Notes.last(ready.lib.games.first { it.id == "b" })?.x == "Second island, the lighthouse" }
        assertTrue("the page shows it", rule.onAllNodes(hasText("Second island, the lighthouse")).fetchSemanticsNodes().isNotEmpty())
    }
}
