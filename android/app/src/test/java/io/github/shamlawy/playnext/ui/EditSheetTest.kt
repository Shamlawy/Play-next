package io.github.shamlawy.playnext.ui

import android.app.Application
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
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
 * A game's Edit on the screen (Phase 2 piece 2): the page's pencil opens it over the page; a group opens on its own
 * with "Back to all"; Save puts the change into the practice copy and the page shows it; back closes the open group
 * first, then Edit, back to the page; Cancel throws the draft away; Remove takes the game to the Queue's Undo.
 * Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class EditSheetTest {
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
          {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":"Action","hours":22},
          {"id":"b","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":2,"colour":"#2E9E6A","genres":"Action","hours":12}
        ],"duels":[],"played":[]}
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
    }

    private val ready get() = state.value as Games.State.Ready
    private val edit = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Edit")
    private var nav by mutableStateOf(Nav("games", listOf("game:a")))
    private lateinit var back: OnBackPressedDispatcher

    private fun show() {
        rule.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val games by Games.state.collectAsState()
            val lib = (games as Games.State.Ready).lib
            Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        rule.waitForIdle()
    }

    // with the clock paused (a timed line would run out under waitForIdle, L242): the practice copy rebuilds off Main
    private fun waitFor(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!ok()) {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(what, System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun openEdit() {
        rule.onNodeWithContentDescription("Edit").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(edit).assertExists()
        assertEquals(listOf("game:a", "edit:a"), nav.stack)
        // past the shell's settle after an opening tap (a quick second tap lands on nothing)
        rule.mainClock.advanceTimeBy(400)
    }

    @Test fun thePencilOpensEditAndSavePutsTheChangeOnThePage() {
        show()
        openEdit()
        rule.onNodeWithText("Name").performClick()
        rule.onNodeWithText("Back to all").assertExists()
        rule.onNode(hasContentDescription("Title")).performTextReplacement("Starfall Lantern Deluxe")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Save").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("Save closes Edit, back to the page", listOf("game:a"), nav.stack)
        // the note right away: on a busy machine the wait below can outlast its 2.4s
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Saved").assertExists()
        waitFor("the practice copy has the new name") { ready.lib.games.first { it.id == "a" }.title == "Starfall Lantern Deluxe" }
        assertTrue("the page shows the new name", rule.onAllNodes(hasText("Starfall Lantern Deluxe")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun backClosesTheOpenGroupFirstThenEdit() {
        show()
        openEdit()
        rule.onNodeWithText("Release").performClick()
        rule.onNodeWithText("Back to all").assertExists()
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Back to all").assertDoesNotExist()
        assertEquals(listOf("game:a", "edit:a"), nav.stack)
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
        assertEquals(listOf("game:a"), nav.stack)
    }

    @Test fun cancelThrowsTheDraftAway() {
        show()
        openEdit()
        rule.onNodeWithText("Where it stands").performClick()
        rule.onNodeWithText("Played it").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertEquals(listOf("game:a"), nav.stack)
        assertEquals("backlog", ready.lib.games.first { it.id == "a" }.rawState)
        assertTrue(ready.lib.played.isEmpty())
    }

    @Test fun reviewChangesListsWhatSaveWouldDo() {
        show()
        openEdit()
        rule.onNodeWithText("Where it stands").performClick()
        rule.onNodeWithText("Playing now").performClick()
        rule.onNodeWithText("Review changes").performClick()
        rule.onNodeWithText("1 change waiting:").assertExists()
        assertEquals("nothing saved yet", "backlog", ready.lib.games.first { it.id == "a" }.rawState)
    }

    @Test fun removeTakesTheGameToTheQueuesUndo() {
        show()
        openEdit()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Remove").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("the Queue, nothing open", emptyList<String>(), nav.stack)
        assertEquals("games", nav.tab)
        waitFor("the game is gone") { ready.lib.games.none { it.id == "a" } }
        rule.onNodeWithText("Undo").assertExists()
    }
}
