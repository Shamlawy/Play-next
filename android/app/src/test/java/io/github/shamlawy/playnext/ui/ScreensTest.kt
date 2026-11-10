package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Shuffler
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.kit.Shared
import io.github.shamlawy.playnext.update.Updater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
 * The screens as pictures (Robolectric draws them, no emulator): restore, Home, each tab's views with their header
 * cards and the views (the rail unfolded, the sticky top bar folded), the tab-switch slash mid-sweep, Settings and Home without a code, folded
 * (412) and unfolded (859). The approved pictures live in src/test/screenshots; android-check fails when
 * one changes (verifyRoborazziDebug), and `./gradlew :app:recordRoborazziDebug` writes new ones after a meant change.
 * Made-up games only: the repo is public. The clock is fixed, so "as of" and "in N days" never move.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// a plain Application: PlayNextApp starts the backup check and WorkManager, which a picture doesn't need
@Config(application = Application::class, sdk = [36])
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        // "as of" is in the phone's zone: the same picture on every machine
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    /** Made-up games with what the header cards read: genres, hours, when each was added, finishes, a duel. */
    private fun lib(extra: String = "") = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30$extra,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"hours":22,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide: The Long Way Down to the Sunken Archive","state":"backlog","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"added":"2026-09-02"},
               {"id":"c","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":3,"colour":"#2E9E6A","genres":["Action"],"hours":12,"added":"2026-06-14"},
               {"id":"d","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":4,"colour":"#B06AD8","genres":["RPG","Strategy"],"added":"2026-10-25"},
               {"id":"e","title":"Quiet Orbit","state":"upcoming","date":"","ord":5,"colour":"#D89A3A","added":"2026-07-30"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9","genres":["Strategy","RPG"],"added":"2026-01-10"},
               {"id":"g","title":"Lumen Drift","state":"played","date":"2024-03-08","ord":7,"colour":"#E0617F","genres":["Action","Strategy"],"added":"2026-02-21"}
             ],
             "duels":[{"a":"f","b":"g","w":"f","t":"2026-10-01"}],
             "played":[{"id":"f","real":9.2,"t":"2026-09-12","again":true},{"id":"g","real":7.4,"t":"2026-08-03"}]}
            """.trimIndent(),
        ).jsonObject,
    )
    private val lib = lib()
    private val ready = Games.State.Ready(lib, copyAt = now - 3 * 3_600_000L)

    /**
     * [yours]: your theme, background and settings from the backup ([l]), else the app's own (no code yet). [font]: the
     * phone's font size; [ready] runs before the picture (a scroll to what it's about).
     */
    private fun shot(
        name: String, width: Int, yours: Boolean = true, l: Library = lib, height: Int = if (width < 600) 915 else 1000,
        font: Float = 1f, ready: () -> Unit = {}, content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(font)
        rule.setContent { Frame(Look.of(l.takeIf { yours }), Prefs.of(l.takeIf { yours }), now = { now }) { content() } }
        ready()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name-$width.png")
    }

    private fun tab(t: String, view: String? = null, l: Library = lib) =
        @Composable { Shell(Nav(t, views = view?.let { mapOf(t to it) } ?: emptyMap()), Games.State.Ready(l, now - 3 * 3_600_000L), go = {}) }

    @Test fun restore412() = shot("restore", 412, yours = false) { RestoreScreen(onSkip = {}, onDone = {}) }
    @Test fun restore859() = shot("restore", 859, yours = false) { RestoreScreen(onSkip = {}, onDone = {}) }
    @Test fun home412() = shot("home", 412) { Shell(Nav("home"), ready, go = {}) }
    @Test fun home859() = shot("home", 859) { Shell(Nav("home"), ready, go = {}) }
    @Test fun games412() = shot("games", 412) { Shell(Nav("games"), ready, go = {}) }
    @Test fun games859() = shot("games", 859) { Shell(Nav("games"), ready, go = {}) }
    /** The Queue's rows with On deck off (On deck's cards are 410 tall: with them the rows start below the picture). */
    @Test fun queue412() = lib(""","deckOff":true,"sort":"hype","qFilter":"all"""").let { l -> shot("queue", 412, l = l, content = tab("games", "queue", l)) }
    @Test fun queue859() = lib(""","deckOff":true""").let { l -> shot("queue", 859, l = l, content = tab("games", "queue", l)) }
    /** Character select (android-v25): the first card closed into the picked game's panel, over the covers. */
    private val selLib = lib(""","deckOff":true,"qLook":"select"""")
    private val closeFirst = {
        rule.mainClock.advanceTimeBy(2_500)
        rule.onNode(SemanticsMatcher("the picked cover") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Pick" } and isSelected()).performScrollTo()
            .performTouchInput { click(Offset(width / 2f, height * .15f)) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNode(hasContentDescription("Sort: ", substring = true)).performScrollTo()
        rule.waitForIdle()
    }
    @Test fun charsel412() = shot("charsel", 412, l = selLib, ready = closeFirst, content = tab("games", "queue", selLib))
    @Test fun charsel859() = shot("charsel", 859, l = selLib, ready = closeFirst, content = tab("games", "queue", selLib))
    @Test fun played412() = shot("played", 412, content = tab("games", "played"))
    @Test fun played859() = shot("played", 859, content = tab("games", "played"))
    @Test fun plan412() = shot("plan", 412, content = tab("plan"))
    @Test fun plan859() = shot("plan", 859, content = tab("plan"))
    @Test fun taste412() = shot("taste", 412, content = tab("taste"))
    @Test fun add859() = shot("add", 859, content = tab("add"))

    /** A Steam link shared into the app: Add names the game it points at. */
    @Test fun shared412() = shot("shared", 412) {
        CompositionLocalProvider(LocalShared provides Shared("Lantern Hollow", 123450)) { tab("add")() }
    }

    /** A game's page (Phase 2's New style): the hero, the tilted cover and kicker, the tags, tiles, the main button, Your progress; a game with no box art. A finished one shows no main button yet. */
    @Test fun game412() = shot("game", 412) { Shell(Nav("games", listOf("game:b")), ready, go = {}) }
    @Test fun game859() = shot("game", 859) { Shell(Nav("games", listOf("game:b")), ready, go = {}) }
    @Test fun gamePlayed412() = shot("game-played", 412) { Shell(Nav("games", listOf("game:f")), ready, go = {}) }

    // a game's Edit over its page (Phase 2 piece 2): the Game tab's groups, one group open, the Played group, Look
    private fun editOpen(vararg taps: String) = {
        for (t in taps) {
            rule.mainClock.advanceTimeBy(400)
            // the last match: the tab bar can say the same word ("Played")
            rule.onAllNodesWithText(t).onLast().performClick()
            rule.waitForIdle()
        }
    }
    @Test fun edit412() = shot("edit", 412) { Shell(Nav("games", listOf("game:b", "edit:b")), ready, go = {}) }
    @Test fun edit859() = shot("edit", 859) { Shell(Nav("games", listOf("game:b", "edit:b")), ready, go = {}) }
    @Test fun editRelease412() = shot("edit-release", 412, ready = editOpen("Release")) { Shell(Nav("games", listOf("game:b", "edit:b")), ready, go = {}) }
    @Test fun editPlayed412() = shot("edit-played", 412, ready = editOpen("Scores", "Played")) { Shell(Nav("games", listOf("game:f", "edit:f")), ready, go = {}) }
    @Test fun editLook412() = shot("edit-look", 412, ready = editOpen("Look", "Cover and colour")) { Shell(Nav("games", listOf("game:b", "edit:b")), ready, go = {}) }

    /**
     * A tap on a header card's cover opens its game: 60ms in, the page is growing from the tap and the cover's copy is
     * on its way from the fan to the page's cover; once it lands the page is open and Close takes it away.
     */
    @Test fun coverFliesOntoItsPage() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("games"))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.waitForIdle()
        // the Queue's fan holds the games On deck picks; tap whichever made it in front
        val g = lib.games.first { rule.onAllNodesWithContentDescription(it.title).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithContentDescription(g.title)[0].performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(60)
        rule.onRoot().captureRoboImage("src/test/screenshots/fly-412.png")
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { assertEquals("game:${g.id}", nav.overlay) }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle { assertNull(nav.overlay) }
    }

    /**
     * Back walks what you opened (the owner, Oct 28): a game opened over Settings, then another over it; each back shows
     * the one before, with Settings kept open under them, then the tab you were on (never another tab).
     */
    @Test fun backFindsWhereYouCameFrom() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("taste", listOf("settings")))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.runOnIdle { nav = nav.open("game:a") }
        rule.runOnIdle { nav = nav.open("game:c") }
        rule.onNodeWithText("Close").assertExists() // Settings' Close: it stays under the pages opened from it
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle { assertEquals(listOf("settings", "game:a"), nav.stack) }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle { assertEquals("settings", nav.overlay) }
        rule.onNodeWithText("Close").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(Nav("taste"), nav) }
        rule.onNodeWithText("Close").assertDoesNotExist()
    }

    /**
     * Unfolded, the rail is over the game page (as on the web), so a tap on its Settings while the page shrinks away
     * opens Settings with the page gone: before android-v9's fix it opened Settings under the closing page, whose close
     * then took Settings away instead, and the page stayed, unseen, holding back and taps.
     */
    @Test fun aTapWhileAPageClosesDoesNotLeaveItStuck() {
        RuntimeEnvironment.setQualifiers("w859dp-h1000dp-xxhdpi")
        var nav by mutableStateOf(Nav("games", listOf("game:a")))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Back").performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(150)
        rule.onNodeWithContentDescription("Settings").performTouchInput { click(Offset(width - 3f, height - 3f)) }
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { assertEquals(Nav("games", listOf("settings")), nav) }
        rule.onNodeWithText("Close").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(Nav("games"), nav) }
    }

    /** Header cards Simple (numbers and covers). */
    @Test fun simple412() = lib(""","shxX":["fan"]""").let { l -> shot("simple", 412, l = l, content = tab("games", "replay", l)) }

    /** Header cards off: a plain title. */
    @Test fun plain859() = lib(""","shxOff":true""").let { l -> shot("plain", 859, l = l, content = tab("taste", "duels", l)) }

    /** The slash 250ms into a tap from Home to Games: the name in, the slab starting to sweep off, the stripe trailing. */
    @Test fun slash412() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("home"))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Games").performClick()
        // with the clock held, the tap's change reaches the screen only once the snapshot is applied
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(250)
        rule.onRoot().captureRoboImage("src/test/screenshots/slash-412.png")
    }

    /**
     * Instant still slides a new tab in (the web's v110 slide, 26 from the right): 100ms after the tap the page is on
     * its way, a second later at rest. (v7's first build kept the entrance inside the tab's own state, which a new tab
     * starts fresh, so no tab switch ever played it.)
     */
    @Test fun pageEntersOnATabSwitch() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        val l = lib(""","tabFade":false""")
        var nav by mutableStateOf(Nav("home"))
        rule.setContent { Frame(Look.of(l), Prefs.of(l), now = { now }) { Shell(nav, Games.State.Ready(l, now - 3 * 3_600_000L), go = { nav = it }) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Games").performClick()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        // the Queue's header card (the widest node named Queue: the pill's button is the other)
        fun cardX() = rule.onAllNodesWithContentDescription("Queue").fetchSemanticsNodes().maxBy { it.size.width }.positionInRoot.x
        val mid = cardX()
        rule.mainClock.advanceTimeBy(1000)
        val rest = cardX()
        assertTrue("the page slides in from the right: ${mid}px mid-way, ${rest}px at rest", mid - rest > 6f)
    }

    /** A swipe across a button walks the views and doesn't tap the button too (no code yet: Games' wide button). */
    @Test fun swipeAcrossAButtonDoesNotTapIt() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("games"))
        val prefs = Prefs.of(null)
        rule.setContent { Frame(Look.of(null), prefs, now = { now }) { Shell(nav, Games.State.NoCode, go = { nav = it }) } }
        val b = rule.onNodeWithText("Type my restore code").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { swipe(Offset(b.right - 20f, b.center.y), Offset(b.left + 20f, b.center.y)) }
        rule.runOnIdle {
            assertEquals("lib", prefs.viewOf("games", nav.views))
            assertNull("the button under the swipe opened ${nav.overlay}", nav.overlay)
        }
    }

    /** Real touches: one finger walks Games' views (none past the first), two fingers walk the tabs. */
    @Test fun swipesWalkViewsThenTabs() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("games"))
        val prefs = Prefs.of(lib)
        rule.setContent { Frame(Look.of(lib), prefs, now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals("lib", prefs.viewOf("games", nav.views)) }
        rule.onRoot().performTouchInput { swipeRight() }
        rule.onRoot().performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals("games" to "queue", nav.tab to prefs.viewOf("games", nav.views)) }
        rule.onRoot().performTouchInput {
            // the second finger above the first: just under the middle is On deck's side scroller, which keeps its own
            // swipes (LocalSwipeGuard), and the header card's height decides where it starts (M71 moved it up 6dp)
            down(0, Offset(width * .85f, centerY))
            down(1, Offset(width * .85f, centerY - 120f))
            repeat(10) {
                updatePointerBy(0, Offset(-width * .07f, 0f))
                updatePointerBy(1, Offset(-width * .07f, 0f))
                move()
            }
            up(0)
            up(1)
        }
        rule.runOnIdle { assertEquals("plan", nav.tab) }
        // a short or steep drag does nothing
        rule.onRoot().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX - 40f, centerY)) }
        rule.onRoot().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX - 400f, centerY + 400f)) }
        rule.runOnIdle { assertEquals("plan" to "month", nav.tab to prefs.viewOf("plan", nav.views)) }
    }
    /** The Queue's Customise (M1): the live groups only, over the Queue. */
    @Test fun customise412() = shot("customise", 412) { Shell(Nav("games", listOf("customise:queue"), mapOf("games" to "queue")), ready, go = {}) }

    /** Your lists (M2), opened from the Queue bar: two lists (one with games in it), the emoji, the name, Make. */
    private val listsLib = lib(""","lists":[{"id":"l1","n":"Couch nights","e":"🛋️"},{"id":"l2","n":"Short ones","e":"⚡"}]""").let { l ->
        // two of the games are in Couch nights
        val games = (l.state["games"] as JsonArray).map { g ->
            val o = g.jsonObject
            if (o["id"]?.jsonPrimitive?.content in setOf("a", "c")) JsonObject(o + ("lists" to JsonArray(listOf(JsonPrimitive("l1"))))) else o
        }
        Library(JsonObject(l.state + ("games" to JsonArray(games))))
    }
    @Test fun lists412() = shot("lists", 412, l = listsLib) { Shell(Nav("games", listOf("lists"), mapOf("games" to "queue")), Games.State.Ready(listsLib, now - 3 * 3_600_000L), go = {}) }
    @Test fun lists859() = shot("lists", 859, l = listsLib) { Shell(Nav("games", listOf("lists"), mapOf("games" to "queue")), Games.State.Ready(listsLib, now - 3 * 3_600_000L), go = {}) }
    /** A list's + (the owner, Nov 3): every game with its cover, the two in the list ticked. */
    private val openAdd = {
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Add games to Couch nights").performClick()
        rule.waitForIdle()
    }
    @Test fun listsAdd412() = shot("lists-add", 412, l = listsLib, ready = openAdd) { Shell(Nav("games", listOf("lists"), mapOf("games" to "queue")), Games.State.Ready(listsLib, now - 3 * 3_600_000L), go = {}) }
    @Test fun listsAdd859() = shot("lists-add", 859, l = listsLib, ready = openAdd) { Shell(Nav("games", listOf("lists"), mapOf("games" to "queue")), Games.State.Ready(listsLib, now - 3 * 3_600_000L), go = {}) }

    /** The Game shuffler (android-v24): the deck on its first card, and the picks once three tempted you. */
    private fun deckShot(name: String, width: Int, then: () -> Unit = {}) {
        Shuffler.rnd = kotlin.random.Random(7)
        try {
            // the card's opening (the strap, the glint) played out
            shot(name, width, ready = { rule.mainClock.advanceTimeBy(2_500); rule.waitForIdle(); then() }) { Shell(Nav("games", listOf("shuffle"), mapOf("games" to "queue")), ready, go = {}) }
        } finally {
            Shuffler.rnd = kotlin.random.Random.Default
        }
    }
    private val tempt3 = {
        rule.mainClock.advanceTimeBy(1_000)
        repeat(3) {
            rule.onNodeWithContentDescription("Tempted").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("Your picks, 3").performClick()
        rule.waitForIdle()
    }
    @Test fun shuffle412() = deckShot("shuffle", 412)
    @Test fun shuffle859() = deckShot("shuffle", 859)
    @Test fun shufflePicks412() = deckShot("shuffle-picks", 412, tempt3)

    /** The header card's Customise chip opens the Queue's Customise; Close and back shut it. */
    @Test fun theQueuesCustomiseChipOpensItsOwnGroups() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        var nav by mutableStateOf(Nav("games", views = mapOf("games" to "queue")))
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.onNodeWithContentDescription("Customise").performClick()
        rule.waitForIdle()
        assertEquals(listOf("customise:queue"), nav.stack)
        rule.onNodeWithText("Customise Queue").assertExists()
        rule.onNodeWithText("Your platform right now").assertExists()
        rule.onNodeWithText("Close").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), nav.stack)
    }

    /** Only the Queue's header card has the chip in Phase 1: elsewhere it would be a dead tap (M10). */
    @Test fun otherScreensHaveNoCustomiseChipYet() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { tab("games", "played")() } }
        assertEquals(0, rule.onAllNodesWithContentDescription("Customise").fetchSemanticsNodes().size)
    }

    @Test fun settings412() = shot("settings", 412) { Shell(Nav("home", listOf("settings")), ready, go = {}) }
    @Test fun settings859() = shot("settings", 859) { Shell(Nav("home", listOf("settings")), ready, go = {}) }
    /** After Check now found nothing newer: the card says when it looked (the owner on v5: the tap seemed to do nothing). */
    @Test fun checked412() {
        Store.prefs.edit().putLong("updChecked", now - 20 * 60_000L).commit()
        Updater.set(Updater.State.UpToDate)
        try {
            // the Updates card sits below the first screenful: scrolled to, so the picture shows the line it's about
            shot("settings-checked", 412, ready = { rule.onNodeWithText("Check now").performScrollTo() }) { Shell(Nav("home", listOf("settings")), ready, go = {}) }
        } finally {
            Updater.set(Updater.State.Idle)
        }
    }

    /** Unfolded on its side (or a short split screen): the rail packs closer and scrolls, Settings stays whole. */
    @Test fun short915() = shot("short", 915, height = 360, content = tab("games"))

    /** A narrow window: the closed tabs share the room, Settings stays on the bar. */
    @Test fun narrow320() = shot("narrow", 320, height = 640, content = tab("games"))

    /** The phone's font size at 1.3 and 2.0: body words grow, tab names, the pill and header numbers stop at 1.3. */
    @Test fun font13() = shot("font13", 412, font = 1.3f, content = tab("games"))
    @Test fun font20() = shot("font20", 412, font = 2f, content = tab("games"))
    @Test fun settingsFont20() = shot("settings-font20", 412, font = 2f) { Shell(Nav("home", listOf("settings")), ready, go = {}) }

    @Test fun noCode412() = shot("no-code", 412, yours = false) { Shell(Nav("home"), Games.State.NoCode, go = {}) }
    @Test fun noCode859() = shot("no-code", 859, yours = false) { Shell(Nav("home"), Games.State.NoCode, go = {}) }
}
