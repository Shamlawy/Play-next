package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.WhatsNew
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Show me (android-v44, the owner: "the tests you want me to make please make it easier for me"): a release's card in
 * What's new takes you to what it changed, with a game of yours that has it, and the Try it bar stays at the foot
 * until you answer: It works (to the log), Something's off (to Claude, in your words), then Next. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-15T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>
    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = "{\"rk\":\"\"}\n" + LIB
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val realSend = Reporter.send
    private lateinit var lib: Library

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Store.init(ctx)
        Store.prefs.edit().clear().commit()
        java.io.File(ctx.filesDir, "reports").deleteRecursively()
        Reporter.init(ctx)
        Reporter.send = { body -> sent += body; true }
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        Games.clock = { now }
        Games.zone = { ZoneId.of("UTC") }
        put("practice", emptyMap<String, Any>())
        put("day", null)
        lib = Games::class.java.getDeclaredMethod("build", String::class.java).apply { isAccessible = true }.invoke(Games, backup) as Library
        state.value = Games::class.java.getDeclaredMethod("ready", Library::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(Games, lib, now - 3_600_000L) as Games.State.Ready
        kotlinx.coroutines.runBlocking { News.load(ctx) }
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        put("day", null)
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
        Store.prefs.edit().clear().commit()
        Reporter.send = realSend
    }

    private var nav by mutableStateOf(Nav("home"))
    private val hold = QueueHold()

    private fun show(start: Nav) {
        nav = start
        rule.setContent {
            val games by Games.state.collectAsState()
            val l = (games as Games.State.Ready).lib
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, hold, go = { nav = it }) }
        }
        rule.waitForIdle()
    }

    private fun has(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun entry(v: Int) = News.all!!.first { it.v == v }

    @Test fun eachPlaceFindsAGameThatHasIt() {
        assertEquals("one you're playing", "p", Guide.game(lib, "playing", now)?.id)
        assertEquals("the longest About", "l", Guide.game(lib, "about", now)?.id)
        assertEquals("one you haven't played that's out", "l", Guide.game(lib, "unplayed", now)?.id)
        assertEquals("one with prices", "m", Guide.game(lib, "priced", now)?.id)
        assertFalse("blocks from before v33 go home: no Show me", Guide.shows(entry(20)))
        assertTrue(Guide.shows(entry(43)))
        // what each block's go says is a place the guide knows (a typo would leave its card without Show me)
        News.all!!.filter { it.v in 33..43 }.forEach { assertTrue("v${it.v} go=${it.go}", Guide.shows(it)) }
        assertFalse("v44's place is What's new itself: no Show me there", Guide.shows(entry(44)))
        assertTrue("v45 opens a game you haven't played", Guide.shows(entry(45)))
        assertTrue("v46 opens Home", Guide.shows(entry(46)))
        assertTrue("v47 opens Home (the dice)", Guide.shows(entry(47)))
        assertTrue("v48 opens Home (the tiles)", Guide.shows(entry(48)))
        assertTrue("v49 opens Home (More for you)", Guide.shows(entry(49)))
        assertFalse("a typo is no place", Guide.known("game:abuot"))
        assertTrue(Guide.line(entry(43)).startsWith("Let's try v43: In Games › Queue, double tap a card to turn it"))
    }

    @Test fun showMeOpensTheQueueWithACardTurnedAndTheBarThenNext() {
        Tried.mark(49, "ok")
        Tried.mark(48, "ok")
        Tried.mark(47, "ok")
        Tried.mark(46, "ok") // tried already, so Next after v43 is v45 (v42 is past the last five)
        show(Nav("games", listOf("news:0")))
        rule.onNodeWithText("What's new").assertExists()
        // v49, v48, v47 and v46 are marked tried (their cards show the answer, no Show me) and v44's place is What's new itself, so
        // v45's is the first and v43's the second
        rule.onAllNodesWithText("Show me")[1].performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(600)
        assertEquals("What's new closed", emptyList<String>(), nav.stack)
        assertEquals("queue", nav.views["games"])
        val pick = hold.made!!
        assertTrue("a card is open", pick.focus != null)
        assertEquals("and turned", pick.focus, pick.flip)
        // the Queue was already under What's new: its open card turns over (its back ends with Like this)
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("it shows its back", 1, rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "back")).fetchSemanticsNodes().size)
        assertTrue("Try it bar", has("Try it · v43"))
        rule.onRoot().captureRoboImage("src/test/screenshots/guide-bar-412.png")

        rule.onNodeWithText("It works").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertEquals("ok", Tried.all()[43])
        assertTrue(has("Marked as working"))
        rule.onNodeWithText("Next: v45").performClick()
        rule.mainClock.advanceTimeBy(600)
        assertEquals("v45 is about a game you haven't played: that game's page", listOf("game:l"), nav.stack)
        assertTrue(has("Try it · v45"))

        rule.onNodeWithText("Hide").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertFalse("folded into its pill", has("It works"))
        rule.onNodeWithText("Try it · v45").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertTrue(has("It works"))
    }

    @Test fun somethingsOffGoesToClaudeInYourWords() {
        show(Nav("games", listOf("news:0")))
        // v49, v48, v47, v46, v45, v43, then v42
        rule.onAllNodesWithText("Show me")[6].performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(600)
        assertTrue(has("Try it · v42"))
        rule.onNodeWithText("Something's off").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onNode(hasContentDescription("What's off?")).performTextInput("the pictures jump")
        rule.onNodeWithText("Send").performClick()
        val end = System.currentTimeMillis() + 5_000
        // the answer is kept at once; the report goes off the main thread
        while (sent.isEmpty() && System.currentTimeMillis() < end) { rule.mainClock.advanceTimeBy(100); Thread.sleep(20) }
        assertEquals("off", Tried.all()[42])
        assertTrue("sent as a Reported problem: $sent", sent.any { "\"kind\":\"user\"" in it && "Try it v42, something's off: the pictures jump" in it })
        // and What's new says so on that release's card
        rule.onNodeWithText("Done").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertNull("the bar is gone", rule.onAllNodesWithText("Try it · v42").fetchSemanticsNodes().firstOrNull())
    }

    @Test fun settingsCountsWhatsLeftToTry() {
        assertEquals("the last five releases, less the guide's own", listOf(49, 48, 47, 46, 45), Tried.left(News.all!!).map(WhatsNew.Entry::v))
        Tried.mark(45, "ok")
        assertEquals(listOf(49, 48, 47, 46), Tried.left(News.all!!).map { it.v })
    }

    companion object {
        private val ABOUT = "A long tale. ".repeat(40)
        val LIB = """{"theme":"midnight","duels":[],"games":[
            {"id":"p","title":"Moonwell","state":"playing","date":"2024-03-01","genres":["Action"]},
            {"id":"l","title":"Glass Road","state":"backlog","date":"2023-05-01","about":"$ABOUT"},
            {"id":"u","title":"Pocket Comet","state":"backlog","date":"2025-01-10","about":"Short."},
            {"id":"m","title":"Lantern Bay","state":"backlog","date":"2025-06-01","px":{"st":{"p":19.99,"c":"USD","t":1763000000000}}},
            {"id":"d","title":"Harbour Lights","state":"played","date":"2022-01-01"}
        ],"played":[{"id":"d","real":7,"t":"2024-02-01"}]}"""
    }
}
