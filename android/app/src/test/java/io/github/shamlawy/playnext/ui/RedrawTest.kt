package io.github.shamlawy.playnext.ui

import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
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
 * Redraw counts (gate 3, docs/ANDROID-PLAN.md: "redraw counts per scripted scroll and open"): Compose's own tracing hook
 * (the one Android Studio's recomposition counts use) counts how often each of our composables runs while a script
 * scrolls the Queue, opens a card, turns it, closes it and drags a row. Each count must stay within
 * android/redraw-budget.txt: a change that makes rows redraw per frame (a state read in composition instead of in a
 * layer) fails here, on every run, with no emulator. After a change that lowers a count, write the new numbers in.
 * Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class RedrawTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private var extra = ""
    private val lib get() = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight",$extra"bgGrad":30,"deckOff":true,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"platforms":"PS5, Switch 2, PC, Wii","hours":22,"hype":8,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"logged":12,"added":"2026-09-02"},
               {"id":"c","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":3,"colour":"#B06AD8","genres":["RPG","Strategy"],"platforms":"PS4, PS5, Vita","added":"2026-10-25"},
               {"id":"g","title":"Copper Orchard","state":"backlog","date":"2026-06-01","ord":4,"colour":"#C98A3A","genres":["Puzzle"],"added":"2026-07-01"},
               {"id":"h","title":"Glass Harbour","state":"backlog","date":"2026-05-01","ord":5,"colour":"#3AC9A0","genres":["Adventure"],"added":"2026-07-02"},
               {"id":"i","title":"Ember Circuit","state":"backlog","date":"2026-04-01","ord":7,"colour":"#D8463A","genres":["Racing"],"added":"2026-07-03"},
               {"id":"j","title":"Quiet Meridian","state":"backlog","date":"2026-03-01","ord":8,"colour":"#6A7BD8","genres":["RPG"],"added":"2026-07-04"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9","genres":["Strategy","RPG"],"added":"2026-01-10",
                "mine":{"story":9,"music":8.5,"gameplay":8,"replay":6,"visuals":9,"world":8,"chars":7.5,"pace":7}}
             ],
             "played":[{"id":"f","real":9.2,"t":"2026-09-12"}]}
            """.trimIndent(),
        ).jsonObject,
    )

    private var nav by mutableStateOf(Nav("games", views = mapOf("games" to "queue")))

    private fun show(width: Int = 412) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${if (width < 600) 915 else 1000}dp-xxhdpi")
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, Games.State.Ready(lib, now - 3_600_000L), go = { nav = it }) } }
        rule.mainClock.advanceTimeBy(2_500)
    }

    // the open card is named "<title>, <headline>" and says which side shows (the header card's covers are named by title)
    private fun card(name: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription("$name, ", substring = true).also { it.performScrollTo() }
    private fun open(name: String) = rule.onAllNodesWithContentDescription(name, substring = true).fetchSemanticsNodes()
        .any { SemanticsProperties.StateDescription in it.config }
    /** A closed row: the last tappable thing that says the name (the header card's line comes first). */
    private fun row(name: String, head: String): SemanticsNodeInteraction {
        // the Queue is a lazy list: bring the row on screen first (only what's on screen is drawn); a row says its name
        // and its headline (the header card's line says only the name)
        val m = hasText(name, substring = true) and hasText(head, substring = true) and hasClickAction()
        rule.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(m)
        // then all of it (a row cut off at the screen's edge would take the tap off screen)
        return rule.onAllNodes(m).onLast().also { it.performScrollTo() }
    }
    private fun side(name: String) = card(name).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    /** Two taps on the card's picture (clear of the box art), [gap] ms apart. */
    private fun taps(name: String, gap: Long = 90) = card(name).performTouchInput {
        click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f))
        advanceEventTime(gap)
        click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f))
    }

    /** A finger on a row's top left: a row scrolled to the screen's foot sits under the floating view pill's middle. */
    private fun tapRow(r: SemanticsNodeInteraction) = r.performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)) }


    private val counts = HashMap<String, Int>()

    /** Counts each of our composables by name ("QueueRow"), from the tracer's "pkg.Name (File.kt:12)". */
    @OptIn(androidx.compose.runtime.InternalComposeTracingApi::class)
    private fun counting(on: Boolean) = androidx.compose.runtime.Composer.setTracer(if (!on) null else object : androidx.compose.runtime.CompositionTracer {
        override fun isTraceInProgress() = true
        override fun traceEventEnd() {}
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            if (!info.startsWith("io.github.shamlawy.playnext.")) return
            val name = info.substringBefore(" (").substringAfterLast('.')
            counts[name] = (counts[name] ?: 0) + 1
        }
    })

    private fun phase(name: String, act: () -> Unit): Map<String, Int> {
        rule.waitForIdle()
        counts.clear()
        counting(true)
        try { act(); rule.mainClock.advanceTimeBy(3_000); rule.waitForIdle() } finally { counting(false) }
        return counts.filterKeys { it in WATCH }.toSortedMap().mapKeys { "$name.${it.key}" }
    }

    @Test fun redrawsStayWithinTheBudget() {
        show()
        val got = LinkedHashMap<String, Int>()
        val list = { rule.onAllNodes(hasScrollToNodeAction()).onLast() }
        got += phase("scroll") {
            list().performTouchInput { swipeUp(centerY + 300f, centerY - 600f, 300) }
            list().performTouchInput { swipeDown(centerY - 600f, centerY + 300f, 300) }
        }
        got += phase("open") { tapRow(row("Hollow Tide", "28 h left")) }
        got += phase("turn") { taps("Hollow Tide"); rule.mainClock.advanceTimeBy(2_500); taps("Hollow Tide") }
        got += phase("close") { card("Hollow Tide").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .7f, height * .45f)) } }
        got += phase("drag") {
            row("Ninefold Gate", "Out in 18 days")
            row("Starfall Lantern", "Ready to play").performTouchInput {
                val p = androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)
                down(p); advanceEventTime(500)
                for (k in 1..24) moveTo(p + androidx.compose.ui.geometry.Offset(0f, 116.dp.toPx() * 1.6f * k / 24), delayMillis = 16)
                up()
            }
        }
        val budget = java.io.File("../redraw-budget.txt").readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
            .associate { it.substringBefore(' ') to it.substringAfterLast(' ').toInt() }
        val report = got.entries.joinToString("\n") { (k, v) -> "$k $v" + (budget[k]?.let { b -> " (budget $b)" } ?: " (no budget)") }
        println("redraw counts:\n$report")
        val over = got.filter { (k, v) -> v > (budget[k] ?: Int.MAX_VALUE) }
        val missing = got.keys.filter { it !in budget }
        assertTrue("redraws over the budget (android/redraw-budget.txt):\n" + over.entries.joinToString("\n") { "${it.key} ${it.value} > ${budget[it.key]}" }, over.isEmpty())
        assertTrue("no budget yet for: $missing (add them to android/redraw-budget.txt)", missing.isEmpty())
    }

    private companion object {
        /** The Queue's pieces whose redraws count (the rest follow from them). */
        val WATCH = setOf("QueueRow", "QueueCard", "QueueBar", "OnDeck", "Front", "Back", "Vessel", "CardTicket", "CardLogo")
    }
}
