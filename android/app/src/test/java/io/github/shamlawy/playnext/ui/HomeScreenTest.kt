package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Home
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * Home's top (Phase 3 piece 1, android-v46): the search lies over the big card and opens what you tap (or the first
 * with Go), the dice opens one of the top three and never the last pick, Played and Nexi's face say which update
 * brings them, Customise › Home's chips change your copy, the counts hide, and unfolded Nexi's Home face steps aside
 * while he sits in the rail. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-07T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Games) as MutableStateFlow<Games.State>

    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private fun lib(extra: String = "") = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":[],"userName":"Sam"$extra,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","hype":8,"genres":"RPG","hours":30},
               {"id":"b","title":"Copper Meadow","state":"playing","hype":5,"genres":"Puzzle"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","hype":9},
               {"id":"e","title":"Moth Bell","state":"backlog","hype":3},
               {"id":"f","title":"Reed Song","state":"upcoming","date":"2027-01-20","hype":2},
               {"id":"d","title":"Quiet Harbour","state":"played","genres":"Puzzle"}
             ],"duels":[],"played":[{"id":"d","real":8}]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.prefs.edit().clear().commit()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
    }

    @After
    fun tearDown() {
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
    }

    private lateinit var setNav: (Nav) -> Unit

    private fun show(l: Library = lib()): () -> Nav {
        state.value = Games.State.Ready(l, now - 3_600_000L)
        var nav by mutableStateOf(Nav("home"))
        setNav = { nav = it }
        rule.setContent {
            val games by Games.state.collectAsState()
            Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        rule.waitForIdle()
        // a tap right after a step opens lands on nothing (Shell's settle)
        rule.mainClock.advanceTimeBy(1_000)
        return { nav }
    }

    private fun ranked(l: Library) = Home(Queue(l, Rank(l, now, ZoneId.systemDefault()), now, ZoneId.systemDefault()), now, ZoneId.systemDefault()).ranked

    private val inResults = hasAnyAncestor(hasContentDescription("Search results"))

    @Test fun theSearchLiesOverTheCardAndOpensWhatYouTap() {
        val nav = show()
        val field = rule.onNodeWithContentDescription("Search your games")
        field.performClick()
        field.performTextInput("har")
        rule.waitForIdle()
        // played games too, with their small word
        rule.onNode(hasText("Quiet Harbour") and inResults).assertExists()
        rule.onNode(hasText("Played") and inResults).assertExists()
        field.performTextReplacement("zz")
        rule.waitForIdle()
        rule.onNode(hasText("No game called that.") and inResults).assertExists()
        // "gate" finds Ninefold Gate, whose row lies over the big card: the tap reaches the row, not the card
        field.performTextReplacement("gate")
        rule.waitForIdle()
        rule.onNode(hasText("Ninefold Gate") and inResults).performClick()
        rule.waitForIdle()
        assertEquals(listOf("game:c"), nav().stack)
    }

    @Test fun goOpensTheFirstFound() {
        val nav = show()
        val field = rule.onNodeWithContentDescription("Search your games")
        field.performClick()
        field.performTextInput("moth")
        rule.waitForIdle()
        field.performImeAction()
        rule.waitForIdle()
        assertEquals(listOf("game:e"), nav().stack)
    }

    @Test fun theDiceOpensOneOfTheTopThreeNeverTheLastPick() {
        val l = lib()
        val all = ranked(l).map { it.id }
        val nav = show(l)
        var last: String? = null
        repeat(6) {
            rule.onNodeWithContentDescription("I'll pick one").performClick()
            rule.waitForIdle()
            val id = nav().stack.single().removePrefix("game:")
            // the web's r2: the ranking without the last pick, then one of its first three
            val top3 = all.filter { it != last }.take(3)
            assertTrue("one of the top three: $id", id in top3)
            if (last != null) assertNotEquals("never the last pick twice running", last, id)
            assertEquals(id, PickLast.get())
            last = id
            rule.runOnIdle { setNav(Nav("home")) }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(1_000)
        }
    }

    @Test fun playedAndNexisFaceSayWhichUpdateBringsThem() {
        show()
        // the line lasts 2.4 s: the clock waits while it's checked (L242)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Played (1)").performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Played arrives in phase 5.").assertExists()
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithContentDescription("Chat with Nexi").performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Chatting with Nexi arrives in phase 8.").assertExists()
    }

    @Test fun nexiSpeaksInHisVoiceAndTheCountsCanHide() {
        show(lib(""","nxPersona":"butler","homeCount":false"""))
        rule.onNodeWithText("Play next").assertExists()
        rule.onNodeWithText("Played").assertExists()
        rule.onNode(hasText("May I suggest", substring = true) and hasText("next, Sam.", substring = true)).assertExists()
    }

    /** Games rebuilds your copy off Main after a change: wait (real time) until [ok], then let the screen catch up. */
    private fun waitFor(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!ok()) {
            assertTrue(what, System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test fun customiseHomeChangesYourCopy() {
        val nav = show()
        rule.onNodeWithContentDescription("Customise Home").performClick()
        rule.waitForIdle()
        assertEquals(listOf("customise:home"), nav().stack)
        rule.onNodeWithText("Tiles on Home").assertExists()
        rule.mainClock.advanceTimeBy(1_000)
        val lib = { (Games.state.value as Games.State.Ready).lib }
        val hide = { (lib()["homeHide"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content } }
        rule.onNodeWithText("Next out").performScrollTo().performClick()
        waitFor("Next out turned off") { hide() == listOf("soon") }
        rule.onNodeWithText("Monthly recap").performScrollTo().performClick()
        waitFor("Monthly recap turned off too") { hide() == listOf("soon", "monthly") }
        rule.onNodeWithText("Next out").performScrollTo().performClick()
        waitFor("on again leaves the others off") { hide() == listOf("monthly") }
        rule.onNodeWithText("Hide").performScrollTo().performClick()
        waitFor("the counts hidden") { lib()["homeCount"] == JsonPrimitive(false) }
    }

    @Test fun unfoldedNexisHomeFaceStepsAsideWhileHeSits() {
        RuntimeEnvironment.setQualifiers("w859dp-h915dp-xxhdpi")
        show()
        assertEquals(0, rule.onAllNodesWithContentDescription("Chat with Nexi").fetchSemanticsNodes().size)
        rule.onNode(hasText("Play next (5)")).assertExists()
    }

    /** With the rail hidden he floats instead of sitting, so Home shows its own face again (the web's seatOn()). */
    @Test fun unfoldedWithTheRailHiddenHomesFaceIsBack() {
        RuntimeEnvironment.setQualifiers("w859dp-h915dp-xxhdpi")
        show(lib(""","railHide":true"""))
        assertEquals(1, rule.onAllNodesWithContentDescription("Chat with Nexi").fetchSemanticsNodes().size)
    }

    /**
     * Back hides the keyboard but leaves the field focused: the results stayed over the card and the next Back left
     * Home. Now Back lets go of the field, the results close, and Home stays.
     */
    @Test fun backClosesTheSearchAndStaysOnHome() {
        lateinit var back: androidx.activity.OnBackPressedDispatcher
        state.value = Games.State.Ready(lib(), now - 3_600_000L)
        var nav by mutableStateOf(Nav("home"))
        rule.setContent {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val games by Games.state.collectAsState()
            Frame(Look.of(lib()), Prefs.of(lib()), now = { now }) { Shell(nav, games, go = { nav = it }) }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        val field = rule.onNodeWithContentDescription("Search your games")
        field.performClick()
        field.performTextInput("gate")
        rule.waitForIdle()
        rule.onNode(hasText("Ninefold Gate") and inResults).assertExists()
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodes(inResults).fetchSemanticsNodes().size)
        assertEquals(Nav("home"), nav)
    }
}
