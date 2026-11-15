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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
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
import kotlin.math.min
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
 * graphicsLayer only: no layout or redraw runs while it moves (second-look on #748). [k] is what's left of the move, 1 → 0.
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
        // the page's own offset carries over even when the other text was moving (its words and what's under it snap)
        val page = if (running) c * k else 0f
        if (running && !same) end()
        val drawn = if (same) foot + d * k else from.toFloat()
        val boxWas = if (same) foot else from.toFloat()
        val grow = to - boxWas
        // the scroll can't go under 0: a page that only scrolled because the text was open drops to the top, no further
        val clamp = scroll?.let { s -> max(0f, s.value - max(0f, s.maxValue + grow)) } ?: 0f
        who = me
        foot = to.toFloat()
        d = drawn - to
        c = page - clamp
        k = 1f
        running = true
        job?.cancel()
        job = scope.launch {
            val self = coroutineContext[Job]
            try {
                animate(1f, 0f, animationSpec = tween(420, easing = Ease.house)) { v, _ -> k = v }
            } finally {
                // ended, or cancelled because the text left the page mid-move: nothing stays shifted (a newer move owns
                // [job] and keeps its own state)
                if (job === self) { job = null; k = 0f; running = false }
            }
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
                // the fourth line's foot from the whole text's layout, at rest and while moving alike, so a close never
                // snaps by a rounding difference when it ends
                val short = if (over) all.getLineBottom(lines - 1).roundToInt() else all.size.height
                hs[0] = short
                hs[1] = all.size.height
                if (over != cut) cut = over
                layout(p.width, if (open) all.size.height else short) { p.place(0, 0) }
            }
            // the walking foot without a redraw: this layer clips to its box and is stretched from its top to the drawn
            // foot, the words inside are stretched back, so they stay put and only the clip's edge moves (graphicsLayer only)
            .graphicsLayer {
                val s = footScale(move.clip(me), size.height)
                clip = s != 1f
                transformOrigin = TOP
                scaleY = s
            },
    ) {
        BasicText(
            text, style = style, maxLines = if (whole) Int.MAX_VALUE else lines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                val s = footScale(move.clip(me), size.height)
                transformOrigin = TOP
                scaleY = if (s > .001f) 1f / s else 1f
            },
        )
    }
}

private val TOP = TransformOrigin(0f, 0f)

/** How much a text's clipping layer stretches: its drawn foot [h] over its box [box]; 1 when it isn't moving. */
private fun footScale(h: Float, box: Float): Float = if (h < 0f || box <= 0f) 1f else h / box

/**
 * A rounded card behind [content] whose foot follows [extra] (px taller or shorter than its box) while a text inside
 * opens or closes, with no redraw (the house rule: graphicsLayer only): three pieces drawn once, the top cap still, the
 * middle stretched from its top and the bottom cap moved by [extra]. Its edge is [line], [w] wide.
 */
@Composable
internal fun GrowCard(extra: () -> Float, r: Dp, fill: Color, line: Color?, w: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        Layout(
            content = {
                Spacer(Modifier.drawBehind { cap(true, r.toPx(), fill, line, w.toPx()) })
                Spacer(
                    Modifier
                        .graphicsLayer {
                            transformOrigin = TOP
                            scaleY = if (size.height > 0f) max(0f, size.height + extra()) / size.height else 1f
                        }
                        .drawBehind {
                            drawRect(fill)
                            if (line != null) {
                                val lw = w.toPx()
                                drawRect(line, size = Size(lw, size.height))
                                drawRect(line, topLeft = Offset(size.width - lw, 0f), size = Size(lw, size.height))
                            }
                        },
                )
                Spacer(Modifier.graphicsLayer { translationY = extra() }.drawBehind { cap(false, r.toPx(), fill, line, w.toPx()) })
            },
            modifier = Modifier.matchParentSize(),
        ) { ms, cs ->
            val wd = cs.maxWidth
            val ht = cs.maxHeight
            val rr = min(r.roundToPx(), ht / 2)
            val ps = listOf(rr, ht - 2 * rr, rr).zip(ms) { h, m -> m.measure(Constraints.fixed(wd, h)) }
            layout(wd, ht) {
                ps[0].place(0, 0)
                ps[1].place(0, rr)
                ps[2].place(0, ht - rr)
            }
        }
        content()
    }
}

/** A card's rounded top ([top]) or bottom: the card's corner drawn in a strip [r] tall. */
private fun DrawScope.cap(top: Boolean, r: Float, fill: Color, line: Color?, w: Float) {
    val tall = size.height * 2 + 2f
    val y = if (top) 0f else size.height - tall
    clipRect {
        drawRoundRect(fill, topLeft = Offset(0f, y), size = Size(size.width, tall), cornerRadius = CornerRadius(r, r))
        if (line != null) {
            val h = w / 2
            drawRoundRect(line, topLeft = Offset(h, y + h), size = Size(size.width - w, tall - w), cornerRadius = CornerRadius(r - h, r - h), style = Stroke(w))
        }
    }
}
