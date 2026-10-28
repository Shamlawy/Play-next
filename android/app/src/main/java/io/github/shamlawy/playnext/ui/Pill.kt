package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.gen.Web
import java.util.Locale

/**
 * A touch that starts on something that scrolls sideways is that thing's own (the web's `sideScroller`): the tab
 * swipes leave it alone. The shell clears it at each first touch; a side scroller marks it in the same pass.
 */
class SwipeGuard {
    var inScroller = false

    /** When the last finger lifted, and when what's open last changed (that lift's time): see Shell's settle. */
    var lastUp = Long.MIN_VALUE / 2
    var settle = Long.MIN_VALUE / 2
}

val LocalSwipeGuard = staticCompositionLocalOf { SwipeGuard() }

/**
 * The floating view pill (`.vpill`, index.html 1054 and 11049, with Speed boost's solid ground: native has no
 * backdrop blur): the open tab's views, the lit one on a sliding accent segment. Over three views it scrolls sideways
 * (at most 92% of the screen or 560 wide). The segment takes its new width at once and slides from the old spot in
 * 280ms (translate + scaleX from the old width, as v261); each label swaps colour by crossfading two drawn layers.
 * The buttons and the segment are placed in one layout pass, so the segment is never a frame late or the wrong size.
 */
@Composable
fun ViewPill(views: List<Pair<String, String>>, on: String, screen: Dp, pick: (String) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val calm = LocalCalm.current
    val guard = LocalSwipeGuard.current
    val shape = RoundedCornerShape(999.dp)
    val scroll = rememberScrollState()
    val wide = views.size > 3
    // where the segment was last placed; a new set of views (another tab) has none and draws it in place, like the
    // web's rebuilt pill
    val placed = remember(views) { Spot() }
    // one slide per pick, made while composing: the frame the pick lands in already draws the segment at its old spot,
    // or, picked again mid-slide, where that slide has it right now (never a jump back to the last pick's end)
    val slide = remember(views, on) {
        val was = placed.slide
        Slide(was?.drawnX(placed.x) ?: placed.x, was?.drawnW(placed.w) ?: placed.w, still = calm || placed.w == 0f).also { placed.slide = it }
    }
    LaunchedEffect(slide) { slide.play() }
    Box(
        modifier
            .widthIn(max = if (wide) minOf(screen * .92f, 560.dp) else Dp.Unspecified)
            .shadow(19.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
            .background(Color(Web.PILL_BG), shape)
            .border(1.dp, Color(Web.PILL_EDGE), shape)
            .topLight(shape, .22f, sheen = true)
            .pointerInput(wide) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (scroll.maxValue > 0 && e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true
                    }
                }
            },
    ) {
        Layout(
            content = {
                // the lit segment: under the labels, top and bottom 6 in, sliding on its own layer
                Box(Modifier.shadow(7.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade).background(look.volt, shape).topLight(shape, .5f))
                for ((k, label) in views) {
                    // keyed by view: another tab's labels start at their own colours instead of fading from these
                    key(k) {
                        val sel = k == on
                        val a by animateFloatAsState(if (sel) 1f else 0f, tween(if (calm) 0 else 180), label = "pill")
                        Box(
                            Modifier
                                .clickable(role = Role.Tab) { if (!sel) pick(k) }
                                .semantics { selected = sel; contentDescription = label }
                                .padding(horizontal = 15.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val text = label.uppercase(Locale.ROOT)
                            BasicText(text, style = PILL.copy(color = Pn.mute), softWrap = false, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 1f - a })
                            BasicText(text, style = PILL.copy(color = Pn.void), softWrap = false, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = a })
                        }
                    }
                }
            },
            modifier = Modifier.then(if (wide) Modifier.horizontalScroll(scroll) else Modifier).padding(6.dp),
        ) { parts, _ ->
            val buttons = parts.drop(1).map { it.measure(Constraints()) }
            val gap = 4.dp.roundToPx()
            val xs = buttons.runningFold(0) { x, b -> x + b.width + gap }
            val h = buttons.maxOfOrNull { it.height } ?: 0
            val i = views.indexOfFirst { it.first == on }
            val lit = buttons.getOrNull(i)
            val segment = lit?.let { parts[0].measure(Constraints.fixed(it.width, h)) }
            layout((xs.last() - gap).coerceAtLeast(0), h) {
                if (lit != null && segment != null) {
                    placed.x = xs[i].toFloat()
                    placed.w = lit.width.toFloat()
                    segment.placeWithLayer(xs[i], 0) { with(slide) { draw(xs[i].toFloat(), lit.width.toFloat()) } }
                }
                buttons.forEachIndexed { j, b -> b.place(xs[j], 0) }
            }
        }
    }
}

/** Where the lit segment sits in the pill (px), kept by the layout. Not state: only the next pick reads it. */
private class Spot {
    var x = 0f
    var w = 0f

    /** The slide drawing it now. */
    var slide: Slide? = null
}

/** One move of the lit segment from where it was ([fromX], [fromW]) to its new spot: 280ms, translate + scaleX. */
private class Slide(private val fromX: Float, private val fromW: Float, still: Boolean) {
    private val p = Animatable(if (still) 1f else 0f)

    suspend fun play() {
        if (p.value < 1f) p.animateTo(1f, tween(280, easing = Ease.slide))
    }

    /** Where this slide draws the left edge of a segment placed at [x] at this moment (scaled from the left edge). */
    fun drawnX(x: Float) = if (p.value >= 1f) x else x + (fromX - x) * (1f - p.value)

    /** And its drawn width, for a segment [w] wide. */
    fun drawnW(w: Float) = if (p.value >= 1f || w <= 0f) w else fromW + (w - fromW) * p.value

    fun GraphicsLayerScope.draw(x: Float, w: Float) {
        val v = p.value
        if (v >= 1f || w <= 0f) return
        translationX = (fromX - x) * (1f - v)
        scaleX = fromW / w + (1f - fromW / w) * v
        transformOrigin = TransformOrigin(0f, .5f)
    }
}

/** `.vpill button`: 11px, 900 (Poppins draws 700), uppercase, .04em. */
private val PILL = androidx.compose.ui.text.TextStyle(fontFamily = Fonts.poppins, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.04.em)

/**
 * `inset 0 1px 0 rgba(255,255,255,a)`: a hairline of light along the top edge, inside the shape; with [sheen] also the
 * pill's `::before` (the top 30% fading from white .09).
 */
fun Modifier.topLight(shape: Shape, a: Float, sheen: Boolean = false) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val p = Path().apply {
        when (outline) {
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    val down = Path().apply { addPath(p, Offset(0f, 1.dp.toPx())) }
    val lip = Path.combine(PathOperation.Difference, p, down)
    val glow = Brush.verticalGradient(0f to Pn.white.copy(alpha = .09f), 1f to Color.Transparent, endY = size.height * .3f)
    val clip = Path.combine(PathOperation.Intersect, p, Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height * .3f)) })
    onDrawWithContent {
        drawContent()
        if (sheen) drawPath(clip, glow)
        drawPath(lip, Pn.white, alpha = a)
    }
}

/**
 * With the pill off (`S.pill === false`), the views as a row of chips under the header (`subChips`): Clean chips, a
 * hairline on `--u-card`, the open one filled with your accent.
 */
@Composable
fun ViewChips(views: List<Pair<String, String>>, on: String, pick: (String) -> Unit) {
    val look = LocalLook.current
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for ((k, label) in views) {
            val sel = k == on
            Box(
                Modifier
                    .then(if (sel) Modifier.background(look.volt, Pn.pill) else Modifier.background(Pn.card, Pn.pill).border(1.dp, Pn.line2, Pn.pill))
                    .clickable(role = Role.Tab) { if (!sel) pick(k) }
                    .semantics { selected = sel; contentDescription = label }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                BasicText(
                    label,
                    style = Type.body.copy(color = if (sel) Pn.void else Pn.ice, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                    softWrap = false, maxLines = 1,
                )
            }
        }
    }
}
