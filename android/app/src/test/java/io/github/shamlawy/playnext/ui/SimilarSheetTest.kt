package io.github.shamlawy.playnext.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Similar
import io.github.shamlawy.playnext.data.SimilarTest
import kotlinx.coroutines.Dispatchers
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
 * Phase 2 piece 4's Similar sheet on the screen: your games most like one (how alike, each opens its page), Steam
 * players' picks asked as it opens (one you have says so), the trailer, and "show them in the Queue" lighting the
 * Queue's "Like" tab. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimilarSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-15T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>
    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = "{\"rk\":\"\"}\n" + SimilarTest.LIB.replace("{\"games\":", "{\"theme\":\"midnight\",\"duels\":[],\"games\":")
    private var asked = 0

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        // pictures decode on the test's own thread (L281): a slow machine never shoots blank tiles
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(ApplicationProvider.getApplicationContext()).coroutineContext(Dispatchers.Unconfined).build())
        Games.clock = { now }
        Games.zone = { ZoneId.of("UTC") }
        val lib = Games::class.java.getDeclaredMethod("build", String::class.java).apply { isAccessible = true }.invoke(Games, backup) as Library
        state.value = Games::class.java.getDeclaredMethod("ready", Library::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(Games, lib, now - 3_600_000L) as Games.State.Ready
        Similar.post = { asked++; SimilarTest.ANSWER }
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        put("day", null)
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
        Similar.forget()
        SingletonImageLoader.reset()
    }

    private var nav by mutableStateOf(Nav("games", listOf("similar:a")))

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
    private fun said(text: String) = rule.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun yourGamesLikeItThenSteamsPicks() {
        show(listOf("similar:a"))
        assertTrue(has("Like Moonwell"))
        assertTrue("how alike, in the strip", has("Glass Road · 53%"))
        waitFor("Steam's picks came in") { has("Tactics of the Tide") }
        assertEquals("asked once", 1, asked)
        assertTrue(has("Steam players who like Moonwell love this · both Souls-like"))
        assertTrue("one you have says so", has("In your library ›"))
        assertTrue("the others offer their trailer", said("Trailer for Tactics of the Tide") && said("Trailer for Northbound"))
        assertTrue(has("Watch the Moonwell trailer"))
        rule.onRoot().captureRoboImage("src/test/screenshots/similar-412.png")
    }

    @Test fun showThemInTheQueueLightsTheLikeTab() {
        show(listOf("similar:a"))
        rule.onNodeWithText("show them in the Queue").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("the sheet closed", emptyList<String>(), nav.stack)
        assertEquals("queue", nav.views["games"])
        rule.onNodeWithText("Like Moonwell").performScrollTo()
        // the Like tab is the lit one (on All its chip is there too, just not selected; L301: found by its description)
        assertEquals("the Like tab is lit", 1, rule.onAllNodes(hasContentDescription("Like Moonwell", substring = true) and isSelected()).fetchSemanticsNodes().size)
        assertTrue("its game", has("Glass Road"))
        assertFalse("games not like it are left out", has("Harbour Lights") || has("Tin Crown"))
    }

    @Test fun aGameInTheStripOpensItsPage() {
        show(listOf("similar:a"))
        rule.onNodeWithText("Glass Road · 53%").performClick()
        // the sheet closes, then the page opens 320 ms later
        waitFor("its page opened") { nav.stack == listOf("game:b") }
    }
}
