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
import io.github.shamlawy.playnext.data.Hours
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
 * Phase 2 piece 4's first slice on the screen: the length tile opens How long to beat (W4: every game but a
 * competitive one), which asks HowLongToBeat when it has nothing, shows its three numbers with how you play lit, fills
 * empty hours by itself, and offers Use HowLongToBeat's for numbers you typed. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HoursSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-14T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>
    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = """
        {"rk":""}
        {"theme":"midnight","playStyle":"extras","games":[
          {"id":"a","title":"Moonwell","state":"backlog","date":"2024-03-01","ord":1,"colour":"#2E9E6A","platforms":"PC"},
          {"id":"b","title":"Glass Road","state":"backlog","date":"2023-05-01","ord":2,"colour":"#3A6FD8","hours":25,"hrsSrc":"me",
           "hl":{"id":6,"n":"Glass Road","m":40,"x":50,"c":80,"cnt":1234,"at":${now - 86_400_000L}}},
          {"id":"cp","title":"Arena Nine","state":"backlog","ord":3,"comp":true},
          {"id":"r","title":"Pocket Comet","state":"backlog","date":"2026-11-10","ord":4,"colour":"#8A5CD0","platforms":"PC","hours":9,
           "rv":{"mc":84,"mcP":"PC","at":${now - 2 * 86_400_000L}}},
          {"id":"px","title":"Lantern Bay","state":"backlog","date":"2025-06-01","ord":5,"colour":"#C0563A","hours":14}
        ],"duels":[],"played":[]}
    """.trimIndent()

    private var calls = 0

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
        Hours.post = { calls++; """{"ok":true,"src":"hltb","res":{"a":{"id":5,"n":"Moonwell","m":11.5,"x":18,"c":30,"cnt":420}}}""" }
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        put("day", null)
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
        Hours.forget()
        io.github.shamlawy.playnext.data.Reviews.forget()
        io.github.shamlawy.playnext.data.Prices.forget()
        askOnOpen = false
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
    private val tile = hasContentDescription("How long to beat") and hasClickAction()

    @Test fun theLengthTileAsksHowLongToBeatAndFillsEmptyHours() {
        show(listOf("game:a"))
        rule.onNode(tile).performClick()
        rule.waitForIdle()
        assertEquals(listOf("game:a", "hours:a"), nav.stack)
        waitFor("HowLongToBeat answered") { has("From 420 players' times on HowLongToBeat") }
        assertEquals(1, calls)
        assertTrue("11.5 is 12 (a half only under 10)", has("12h"))
        assertTrue("how you play: story + extras", has("how you play"))
        waitFor("the practice copy took its hours") { Backup.str(ready.lib.games.first { it.id == "a" }["hrsSrc"]) == "hltb" }
        assertEquals(18.0, Backup.num(ready.lib.games.first { it.id == "a" }["hoursX"], 0.0, 0.0, 1e9), 0.0)
        waitFor("the sheet says it follows by itself") { has("Your plans use these numbers") }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/hours-sheet-412.png")
    }

    @Test fun typedHoursGetUseHowLongToBeats() {
        show(listOf("game:b"))
        rule.onNode(tile).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(400)
        assertEquals("an answer already: no ask", 0, calls)
        assertTrue(has("From 1,234 players' times on HowLongToBeat"))
        assertTrue(has("typed by you"))
        assertFalse("typed by you: no Keep mine", has("Keep mine"))
        rule.onNode(hasText("Use HowLongToBeat's") and hasClickAction()).performClick()
        rule.waitForIdle()
        waitFor("the practice copy took its hours") { Backup.num(ready.lib.games.first { it.id == "b" }["hours"], 0.0, 0.0, 1e9) == 40.0 }
        waitFor("and says so") { has("Using HowLongToBeat's hours") }
    }

    @Test fun aCompetitiveGamesLengthIsNoButton() {
        show(listOf("game:cp"))
        rule.onAllNodes(tile).fetchSemanticsNodes().let { assertTrue("no How long to beat on a competitive game", it.isEmpty()) }
        assertTrue("its tile still says so", has("no end"))
    }

    /** Piece 4's Reviews: a game out 4 days ago, checked 2 days ago, asks again as its page opens; Steam's players join the critics. */
    @Test fun aFreshGamesReviewsAreAskedAgainAsItsPageOpens() {
        askOnOpen = true
        io.github.shamlawy.playnext.data.Reviews.post = { """{"ok":true,"res":{"r":{"st":4410,"d":"Very Positive","p":91,"n":1234}}}""" }
        show(listOf("game:r"))
        rule.mainClock.advanceTimeBy(1_000)
        waitFor("Steam's players came in") { has("Very Positive · 1,234 on Steam") }
        assertTrue(has("Critics · PC"))
        assertTrue(has("checked today"))
        assertTrue("the guess's line (lower case on the web too when no reason comes first)", has("a first guess"))
        assertFalse("the tile already says the guess", has("You, probably"))
        rule.onNode(hasText("a first guess", substring = true)).performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/reviews-412.png")
    }

    /**
     * Piece 4's Prices: a game with no platforms is priced on all three stores as its page opens; the chips show each
     * price (Steam's sale with its badge), the cheapest ringed, and the line under them says why; it opens the sheet.
     */
    @Test fun aGamesPricesComeInAsItsPageOpens() {
        askOnOpen = true
        var asked = ""
        io.github.shamlawy.playnext.data.Prices.post = { b ->
            asked = b
            """{"ok":true,"res":{"px":{
              "st":{"id":7001,"name":"Lantern Bay","cur":"USD","base":5999,"now":2999,"pct":50,"baseF":"$59.99","nowF":"$29.99","end":1795000000000},
              "ps":{"id":"UP0001-CUSA00001_00-LANTERNBAY000000","name":"Lantern Bay","cur":"USD","base":6999,"now":4899,"pct":30,"baseF":"$69.99","nowF":"$48.99"},
              "ns":{"id":"70010000000001","name":"Lantern Bay","cur":"USD","base":49.99,"now":49.99,"pct":0,"baseF":"$49.99","nowF":"$49.99"}}}}"""
        }
        show(listOf("game:px"))
        rule.mainClock.advanceTimeBy(1_000)
        waitFor("the prices came in") { has("Steam is cheapest") }
        assertFalse("never the watch list", Regex("\"(id|watch|off|all)\"\\s*:").containsMatchIn(asked))
        assertTrue(has("$29.99") && has("$48.99") && has("$49.99"))
        assertTrue("Steam's sale badge", has("−50%"))
        rule.onNode(hasText("Price alerts", substring = true)).performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/prices-412.png")
        rule.onNode(hasText("Price alerts", substring = true)).performClick()
        rule.mainClock.advanceTimeBy(600)
        waitFor("the sheet") { has("Check this game now") }
        assertTrue(has("Open Steam") && has("Open PS Store") && has("Open eShop"))
        assertTrue("its old price", has("$69.99"))
        rule.onRoot().captureRoboImage("src/test/screenshots/prices-sheet-412.png")
    }
}
