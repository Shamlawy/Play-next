package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Shx
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The header cards as they're drawn: no maths while drawing (the numbers come with your games, Perfect run speed 2), the
 * spotlight line and the fan's covers take a finger (touch 8), and the numbers keep their words whole in a split screen.
 * Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class HeaderCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    /** ScreensTest's made-up games (a new copy each time: the fallback's cache goes by the copy). */
    private fun lib() = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,
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

    private var nav by mutableStateOf(Nav("games"))

    private fun show(width: Int, ready: Games.State.Ready, font: Float = 1f) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${if (width < 600) 915 else 1000}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(font)
        rule.setContent { Frame(Look.of(ready.lib), Prefs.of(ready.lib), now = { now }) { Shell(nav, ready, go = { nav = it }) } }
        rule.waitForIdle()
    }

    private val screens = listOf("games" to "lib", "games" to "played", "games" to "replay", "plan" to null, "taste" to null, "add" to null, "games" to "queue")

    private fun go(t: String, v: String?) {
        nav = Nav(t, views = v?.let { mapOf(t to it) } ?: emptyMap())
        rule.waitForIdle()
    }

    @Test fun drawingTheCardsDoesNoMaths() {
        val l = lib()
        show(412, Games.State.Ready(l, now - 3_600_000L, cards = Shx.all(l, now, utc)))
        val made = Shx.built.get()
        for ((t, v) in screens) go(t, v)
        assertEquals("a card was worked out while drawing", made, Shx.built.get())
        assertTrue("the Queue's card is drawn", rule.onAllNodes(hasText("waiting")).fetchSemanticsNodes().isNotEmpty())
    }

    /** The way it was before: a copy without its numbers works them out while the screen draws (what the counter catches). */
    @Test fun withoutTheNumbersDrawingDoesTheMaths() {
        val made = Shx.built.get()
        show(412, Games.State.Ready(lib(), now - 3_600_000L))
        for ((t, v) in screens) go(t, v)
        assertTrue("the counter sees maths in composition", Shx.built.get() > made)
    }

    private fun px(dp: Int) = with(rule.density) { dp.dp.toPx() }

    /** The header card's own node among those that match: it's the page's first thing. */
    private fun top(m: SemanticsMatcher): SemanticsNode = rule.onAllNodes(m).fetchSemanticsNodes().minBy { it.boundsInRoot.top }

    /** The header card itself: the page's first big named box. */
    private fun card(): Rect = top(
        SemanticsMatcher("the header card") {
            SemanticsProperties.ContentDescription in it.config && it.boundsInRoot.height > px(120) && it.boundsInRoot.width > px(250)
        },
    ).boundsInRoot

    /** The lowest match inside the card (its numbers sit at its foot; the chart's caption is higher up). */
    private fun foot(m: SemanticsMatcher): SemanticsNode {
        val c = card()
        return rule.onAllNodes(m).fetchSemanticsNodes().filter { it.boundsInRoot.top >= c.top && it.boundsInRoot.bottom <= c.bottom }.maxBy { it.boundsInRoot.top }
    }

    private fun cover(title: String): Rect = top(hasClickAction() and hasContentDescription(title)).boundsInRoot

    /** Where a tap lands: the game it opened, if any; then back to the Queue, past the settle that follows a tap. */
    private fun opened(x: Float, y: Float): String? {
        rule.onRoot().performTouchInput { click(Offset(x, y)) }
        rule.waitForIdle()
        val got = nav.stack.lastOrNull()?.removePrefix("game:")
        nav = Nav("games")
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        return got
    }

    @Test fun theSpotlightLineTakesATapAroundIt() {
        val l = lib()
        val cards = Shx.all(l, now, utc)
        show(412, Games.State.Ready(l, now - 3_600_000L, cards = cards))
        val id = cards["queue"]!!.line!!.g.id
        val b = top(hasClickAction() and hasText("Up next", substring = true)).boundsInRoot
        println("spotlight line: ${b.height / px(1)}dp tall")
        // Compose stretches a target under 48dp to 48 for a finger when nothing else is nearer: 8 above and below open it
        assertEquals("8dp above the line", id, opened(b.center.x, b.top - px(8)))
        assertEquals("8dp below the line", id, opened(b.center.x, b.bottom + px(8)))
        assertEquals("well away from it, nothing", null, opened(b.center.x, b.top - px(24)))
    }

    @Test fun eachBackCoverOpensFromItsStrip() {
        val l = lib()
        val cards = Shx.all(l, now, utc)
        show(412, Games.State.Ready(l, now - 3_600_000L, cards = cards))
        // folded, three covers: the first in front on the right, the other two showing a strip each to its left
        val cv = cards["queue"]!!.covers.take(3)
        val box = cv.map { cover(it.title) }
        for (i in 1..2) {
            val strip = box[i - 1].left - box[i].left
            println("back cover $i: a strip ${strip / px(1)}dp wide, ${box[i].height / px(1)}dp tall")
            assertEquals("the middle of back cover $i's strip", cv[i].id, opened(box[i].left + strip / 2, box[i].center.y))
        }
    }

    private fun layout(n: SemanticsNode): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action!!(out)
        return out[0]
    }

    /** A word cut across two lines ("waiti / ng"), not a wrap at a space. */
    private fun brokeAWord(t: TextLayoutResult): Boolean {
        val s = t.layoutInput.text.text
        return (0 until t.lineCount - 1).any { i -> t.getLineEnd(i).let { e -> e in 1 until s.length && s[e - 1] != ' ' && s[e] != ' ' } }
    }

    @Test fun theNumbersStayWholeAt320() = numbersStayWhole(320)
    @Test fun theNumbersStayWholeAt340() = numbersStayWhole(340)
    @Test fun theNumbersStayWholeAt359() = numbersStayWhole(359)

    /** The phone's biggest font: the numbers and their labels grow to 1.3 and stay (`fixed()`), so they fit as at 1.3. */
    @Test fun theNumbersStayWholeWithTheBiggestFont() = numbersStayWhole(412, font = 2f)

    /** A split screen (or a small phone): every card's numbers whole, their short labels on one line, clear of the fan. */
    private fun numbersStayWhole(w: Int, font: Float = 1f) {
        val l = lib()
        val cards = Shx.all(l, now, utc)
        show(w, Games.State.Ready(l, now - 3_600_000L, cards = cards), font)
        // the Queue first: its "waiting", "to beat" and "out now" were the ones that broke
        for ((t, v) in screens.reversed()) {
            go(t, v)
            val kind = if (t == "games") v!! else t
            val fan = cards[kind]!!.covers.take(3).minOfOrNull { cover(it.title).left } ?: Float.MAX_VALUE
            for (s in cards[kind]!!.stats) {
                val num = layout(foot(hasText(s.value + (s.unit ?: ""))))
                assertFalse("$w $kind: ${s.value}${s.unit ?: ""} is cut", num.didOverflowWidth)
                val node = foot(hasText(s.label))
                val label = layout(node)
                println("$w x$font $kind: '${s.label}' ${label.lineCount} line(s), ends ${(fan - node.boundsInRoot.right) / px(1)}dp before the fan")
                assertFalse("$w $kind: '${s.label}' breaks a word", brokeAWord(label))
                // a short label stays on one line ("to beat", "out now"); a longer one ("average score") may wrap at a space
                if (s.label.length <= 9) assertEquals("$w $kind: '${s.label}' on one line", 1, label.lineCount)
                assertTrue("$w $kind: '${s.label}' runs under the fan", node.boundsInRoot.right <= fan)
            }
        }
    }
}
