package io.github.shamlawy.playnext.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Practice
import io.github.shamlawy.playnext.data.Why
import io.github.shamlawy.playnext.data.Library
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
 * Phase 2 piece 5 on the screen: the page's ⋯ opens the drawer of everything else; Reset scores asks first and then
 * clears that game's scores in the practice copy; Why you'll love it, Lists and the calling card open over the page;
 * Show in queue goes to the Queue with that game's card open. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoreDrawerTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-16T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>
    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = """
        {"rk":""}
        {"theme":"midnight","lists":[{"id":"l1","n":"Co-op","e":"🎮"}],"games":[
          {"id":"a","title":"Moonwell","state":"played","date":"2024-03-01","ord":1,"colour":"#2E9E6A","platforms":"PC","hype":8,"mine":{"story":9}},
          {"id":"b","title":"Glass Road","state":"backlog","date":"2023-05-01","ord":2,"colour":"#3A6FD8"},
          {"id":"c","title":"Lantern Bay","state":"played","ord":3,"colour":"#C0563A"}
        ],"duels":[{"a":"a","b":"c","w":"a"}],"played":[{"id":"a","real":9,"t":"2026-01-02"},{"id":"c","real":6,"t":"2026-02-02"}]}
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
        io.github.shamlawy.playnext.data.Prices.post = { """{"ok":true,"res":{}}""" }
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        put("day", null)
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
        Why.forget()
        io.github.shamlawy.playnext.data.Reviews.forget()
        io.github.shamlawy.playnext.data.Prices.forget()
    }

    private val ready get() = state.value as Games.State.Ready
    private var nav by mutableStateOf(Nav("games", listOf("game:a")))

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
            rule.mainClock.advanceTimeBy(50)
            assertTrue(what, System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun has(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun tap(text: String) { val n = rule.onNode(hasText(text) and hasClickAction()); runCatching { n.performScrollTo() }; n.performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(400) }
    private fun more() { rule.onNode(hasContentDescription("More for this game")).performScrollTo().performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(600) }

    @Test fun theDrawerHoldsTheRestAndResetAsksFirst() {
        show(listOf("game:a"))
        assertFalse("closed at first", has("Reset scores"))
        more()
        for (t in listOf("Show in queue", "Calling card", "Trailer", "Similar", "Lists", "Prices", "Where was I?", "Why you'll love it", "Reset scores")) assertTrue(t, has(t))
        assertFalse("its score is the main row's", has("Score ·"))
        rule.onRoot().captureRoboImage("src/test/screenshots/game-more-412.png")
        tap("Reset scores")
        assertTrue(has("Reset every score for Moonwell?"))
        tap("Cancel")
        assertTrue("kept", ready.lib.played.first { it.id == "a" }.scored)
        tap("Reset scores")
        tap("Reset")
        assertTrue(has("Scores reset for Moonwell"))
        waitFor("the copy rebuilt") { !ready.lib.played.first { it.id == "a" }.scored }
        assertEquals(setOf("a"), Practice.wiped(ready.lib))
        assertTrue("its duel went", ready.lib.duels.isEmpty())
    }

    @Test fun itsButtonsOpenTheirSheetsAndShowInQueueGoesThere() {
        show(listOf("game:a"))
        more()
        tap("Why you'll love it")
        assertEquals(listOf("game:a", "why:a"), nav.stack)
        assertTrue("one other rated game", has("Rate at least 3 games you've finished"))
        tap("Close")
        assertEquals(listOf("game:a"), nav.stack)
        tap("Lists")
        assertEquals(listOf("game:a", "glists:a"), nav.stack)
        assertTrue(has("Lists for Moonwell"))
        rule.onNode(hasContentDescription("Co-op, doesn't have this game") and hasClickAction()).performClick()
        waitFor("in the list") { ready.lib.games.first { it.id == "a" }["lists"].toString().contains("l1") }
        rule.onNode(hasContentDescription("Close") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(400)
        tap("Show in queue")
        waitFor("on the Queue") { nav.stack.isEmpty() }
        assertEquals("games", nav.tab)
    }
}
