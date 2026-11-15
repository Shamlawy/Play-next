package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One text on a game page opening or closing (android-v41, the owner, Nov 14: "when pressing on the text to close it …
 * the screenshots and the window itself stutters"). android-v38 let the text change size first and pushed what's under
 * it back from onSizeChanged, a coroutine later: one frame showed the screenshots jumped, and a close near the page's
 * foot also jumped the whole page as its scroll shrank. Now everything is set in the tap itself, in one frame:
 * - the text's box takes its new height at once, its words stay drawn to where they were and a clip walks to the new
 *   foot ([clip]); a card around it draws its edge with the clip ([extra]);
 * - what's under it is moved back to where it was and glides into place ([below]);
 * - a close that will shrink the page past the scroll moves the whole page back by that much ([pageY]), so nothing jumps.
 * Draw and transform only: no layout runs while it moves. [k] is what's left of the move, 1 → 0.
 */
@Stable
internal class TextMove(private val scroll: ScrollState?) {
    /** Which text moves ([STORY] or [ABOUT]); 0 none. */
    var who by mutableIntStateOf(0)
        private set

    /** True while a move runs: the closing text keeps all its lines drawn until it ends. */
    var running by mutableStateOf(false)
        private set
    private var k by mutableFloatStateOf(0f)
    private var d by mutableFloatStateOf(0f)
    private var c by mutableFloatStateOf(0f)
    private var foot by mutableFloatStateOf(0f)
    private var job: Job? = null

    /** The moving text's drawn foot, px from its top; -1 when it isn't moving (read while drawing only). */
    fun clip(me: Int): Float = if (who == me && running) foot + d * k else -1f

    /** How far past (or short of) its box [me]'s words are drawn right now (a card around it grows by it). */
    fun extra(me: Int): Float = if (who == me && running) d * k else 0f

    /** The offset for a block under the text at [level] or deeper (the story so far is above About). */
    fun below(level: Int): Float = if (running && who in 1..level) d * k else 0f

    /** The whole page's offset (a close near the foot, where the scroll would shrink). */
    val pageY: Float get() = if (running) c * k else 0f

    /** [me] goes from a box [from] px tall to [to]: called in the tap, before the text's own state flips. */
    fun start(me: Int, from: Int, to: Int, still: Boolean, scope: CoroutineScope) {
        if (still) { end(); return }
        val same = running && who == me
        if (running && !same) end()
        val drawn = if (same) foot + d * k else from.toFloat()
        val boxWas = if (same) foot else from.toFloat()
        val page = if (same) c * k else 0f
        val grow = to - boxWas
        val clamp = scroll?.let { s -> max(0f, s.value - (s.maxValue + grow)) } ?: 0f
        who = me
        foot = to.toFloat()
        d = drawn - to
        c = page - clamp
        k = 1f
        running = true
        job?.cancel()
        job = scope.launch {
            animate(1f, 0f, animationSpec = tween(420, easing = Ease.house)) { v, _ -> k = v }
            end()
        }
    }

    private fun end() {
        job?.cancel()
        job = null
        k = 0f
        running = false
    }

    companion object {
        const val STORY = 1
        const val ABOUT = 2
    }
}

/** The page's [TextMove] (GameBody provides it); a text outside a page opens and closes with no glide. */
internal val LocalTextMove = staticCompositionLocalOf<TextMove?> { null }

/**
 * Words that open and close on a tap (T60, the owner, Nov 14: "press on the text for it to expand and compress, not
 * read more or less"): [lines] lines ending in "…" when there's more; a tap opens all of it and a tap closes it. TalkBack
 * still hears "Read more" / "Read less" as the tap's name. [me] names it for the page's [TextMove].
 */
@Composable
internal fun OpenText(text: String, style: TextStyle, still: Boolean, me: Int, modifier: Modifier = Modifier, lines: Int = 4) {
    val move = LocalTextMove.current ?: remember { TextMove(null) }
    var open by remember(text) { mutableStateOf(false) }
    var cut by remember(text) { mutableStateOf(false) }
    // [the cut box, the whole text] at the last width: read by the tap, never by composition
    val hs = remember(text) { IntArray(2) }
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val scope = rememberCoroutineScope()
    val whole = open || (move.running && move.who == me)
    val flip: () -> Unit = {
        Sounds.play(Sfx.Tap)
        move.start(me, if (open) hs[1] else hs[0], if (open) hs[0] else hs[1], still, scope)
        open = !open
    }
    Box(
        modifier
            .clickable(enabled = cut || open, onClickLabel = if (open) "Read less" else "Read more", onClick = flip)
            .layout { m, cs ->
                val all = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = cs.maxWidth))
                val over = all.lineCount > lines
                val p = m.measure(cs)
                // closed and still: the real four-line box; else the fourth line's foot from the whole text's layout
                val short = if (!whole) p.height else if (over) all.getLineBottom(lines - 1).roundToInt() else all.size.height
                hs[0] = short
                hs[1] = all.size.height
                if (over != cut) cut = over
                layout(p.width, if (open) all.size.height else short) { p.place(0, 0) }
            }
            .drawWithContent {
                val h = move.clip(me)
                if (h < 0f) drawContent() else clipRect(bottom = h) { this@drawWithContent.drawContent() }
            },
    ) {
        BasicText(text, style = style, maxLines = if (whole) Int.MAX_VALUE else lines, overflow = TextOverflow.Ellipsis)
    }
}

/** A rounded card drawn behind its content, [extra] px taller (or shorter) than its box, its edge [line] [w] px wide. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.growCard(extra: Float, r: Float, fill: androidx.compose.ui.graphics.Color, line: androidx.compose.ui.graphics.Color?, w: Float) {
    val sz = androidx.compose.ui.geometry.Size(size.width, max(0f, size.height + extra))
    val cr = androidx.compose.ui.geometry.CornerRadius(r, r)
    drawRoundRect(fill, size = sz, cornerRadius = cr)
    if (line != null) {
        val h = w / 2
        drawRoundRect(line, topLeft = androidx.compose.ui.geometry.Offset(h, h), size = androidx.compose.ui.geometry.Size(sz.width - w, sz.height - w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r - h, r - h), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    }
}
