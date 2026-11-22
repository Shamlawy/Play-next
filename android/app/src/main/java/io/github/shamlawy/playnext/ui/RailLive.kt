package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Strip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

/*
 * The unfolded rail's live part (v251, index.html 24262-24400; android/parity/3-map/2-rail-reel-widgets.md §3): the
 * blinking "LIVE" label and the strip of tiles under it ([Strip.items]). The strip drifts upward forever at 15 dp a
 * second, moved only by a graphicsLayer: it holds enough copies of its tiles to fill its height plus one, and the loop
 * moves exactly one copy, so it never jumps (it drifts even when every tile fits, as the web's does: 3-critic.md W4).
 * A finger down holds it, a drag past 8 dp scrubs it both ways (never a tap then), and 2 s after the finger lifts it
 * carries on from there. Calm: one copy, still, scrolled by the finger. Paused while the rail is hidden.
 */

/** A tile's height: 8 of air, the 44 picture, 6, the value's line, the caption's line, 7 (the web's 58-wide tile, grown for the 112 rail). */
internal val STRIP_TILE_H = 94.dp

/** The gap under each tile (`.rlv-k` gap 10 and its 10 at the foot, so each tile and its gap is one step). */
private val STRIP_GAP = 10.dp

/** The tile's width: the views tray's (8 in from the rail's sides). */
private val STRIP_TILE_W = 96.dp

/** How fast it drifts (`RLV_SPEED`, px a second there): slow enough to read. */
private val STRIP_SPEED = 15.dp

/** The "LIVE" label's box: a 5 dp dot under the word, set on its side, reading upward. */
internal val LIVE_LABEL_H = 44.dp

/** The fades at the strip's top and bottom (`.rlv::before/::after`, 18 of `--ink`). */
private val STRIP_FADE = 18.dp

/**
 * `.rh.rlv-h`: "LIVE" on its side (reading upward, as the web's rotated vertical writing) with a 5 dp dot in your accent
 * under it that blinks every 2.4 s (to .25 at half way); the dot keeps still when calm or while the rail is hidden.
 */
@Composable
internal fun LiveLabel(volt: Color, still: Boolean) {
    val blink = if (still) null else rememberInfiniteTransition("live").animateFloat(1f, .25f, infiniteRepeatable(tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), "dot")
    Box(Modifier.size(14.dp, LIVE_LABEL_H).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Row(
            Modifier
                // laid out sideways: the row's width is the box's height, then turned a quarter left
                .layout { m, _ ->
                    val p = m.measure(androidx.compose.ui.unit.Constraints())
                    layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
                }
                .graphicsLayer { rotationZ = -90f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(5.dp).graphicsLayer { alpha = blink?.value ?: 1f }.background(volt, CircleShape))
            BasicText("LIVE", style = LIVE.fixed(), maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The strip itself: [tiles] (none: "Add a game and I'll keep an eye on it here."), drifting unless [calm], paused unless
 * [running] (the rail shown). A game's tile opens its page ([open]); the backlog's goes to the Games tab ([toGames]).
 * Shorter than one tile, it shows nothing.
 */
@Composable
internal fun LiveStrip(
    tiles: List<Strip.Tile>,
    volt: Color,
    calm: Boolean,
    running: Boolean,
    open: (String) -> Unit,
    toGames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawWithContent {
                drawContent()
                val f = STRIP_FADE.toPx()
                drawRect(Brush.verticalGradient(listOf(Pn.ink, Pn.ink.copy(alpha = 0f)), 0f, f), size = Size(size.width, f))
                drawRect(Brush.verticalGradient(listOf(Pn.ink.copy(alpha = 0f), Pn.ink), size.height - f, size.height), Offset(0f, size.height - f), Size(size.width, f))
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (tiles.isEmpty()) {
            BasicText(
                "Add a game and I'll keep an eye on it here.",
                style = NONE.fixed(),
                modifier = Modifier.width(STRIP_TILE_W).padding(horizontal = 6.dp, vertical = 10.dp),
            )
            return@BoxWithConstraints
        }
        if (maxHeight < STRIP_TILE_H) return@BoxWithConstraints
        val copy = (STRIP_TILE_H + STRIP_GAP) * tiles.size
        if (calm) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                StripCopy(tiles, volt, open, toGames)
            }
            return@BoxWithConstraints
        }
        // the copies: enough to cover the strip plus one, so moving one copy's height loops without a seam
        val n = maxOf(2, ceil(maxHeight / copy).toInt() + 1)
        val d = LocalDensity.current
        val copyPx = with(d) { copy.toPx() }
        val speed = with(d) { STRIP_SPEED.toPx() }
        // where the loop is, as a part of one copy, so new tiles keep the strip's place
        val at = remember { mutableFloatStateOf(0f) }
        var held by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val resume = remember { arrayOfNulls<Job>(1) }
        LaunchedEffect(running, held, copyPx) {
            if (!running || held) return@LaunchedEffect
            var last = -1L
            while (true) {
                withInfiniteAnimationFrameMillis { ms ->
                    if (last >= 0) at.floatValue = (at.floatValue + (ms - last).coerceIn(0L, 100L) * speed / 1000f / copyPx) % 1f
                    last = ms
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -at.floatValue * copyPx }
                // touch: a finger down holds it, a drag scrubs it (and is never a tap), 2 s after the lift it carries on
                .pointerInput(copyPx) {
                    val slop = 8.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        resume[0]?.cancel()
                        held = true
                        val from = at.floatValue
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            val dy = c.position.y - down.position.y
                            if (!moved && abs(dy) >= slop) moved = true
                            if (moved) {
                                at.floatValue = (((from - dy / copyPx) % 1f) + 1f) % 1f
                                c.consume()
                            }
                            if (!c.pressed) break
                        }
                        resume[0] = scope.launch { delay(2000); held = false }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (i in 0 until n) {
                // the repeated copies are for the eye only: TalkBack reads the first
                Column(if (i == 0) Modifier else Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                    StripCopy(tiles, volt, open, toGames)
                }
            }
        }
    }
}

@Composable
private fun StripCopy(tiles: List<Strip.Tile>, volt: Color, open: (String) -> Unit, toGames: () -> Unit) {
    for (t in tiles) {
        StripTile(t, volt) { t.g?.let { open(it.id) } ?: toGames() }
        Spacer(Modifier.height(STRIP_GAP))
    }
}

/**
 * `.rlv-i`: on `--u-card`, round 18: the game's picture in a 44 circle on its colour (the backlog's an hourglass on a wash
 * of your accent), the ring of how far (accent; `--hot` for a sale) on a faint track, the kind's badge at its foot, the
 * value bold (`--hot` for a sale, your accent for a release a week or less away) and the caption, cut with "…".
 */
@Composable
private fun StripTile(t: Strip.Tile, volt: Color, onClick: () -> Unit) {
    val g = t.g
    val hot = t.k == "sale"
    val accent = if (hot) Pn.hot else volt
    val label = if (g != null) "${g.title}: ${t.v} ${t.s}" else "${t.v} ${t.s}"
    Column(
        Modifier
            .size(STRIP_TILE_W, STRIP_TILE_H)
            .background(Pn.card, TILE_SHAPE)
            .clip(TILE_SHAPE)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(top = 8.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(CIRCLE)
                .drawBehind {
                    if (t.p > 0) {
                        val w = 3.dp.toPx()
                        val r = size.minDimension / 2 + 2.dp.toPx()
                        val tl = Offset(size.width / 2 - r, size.height / 2 - r)
                        val sz = Size(r * 2, r * 2)
                        drawArc(volt.copy(alpha = .18f), 0f, 360f, false, tl, sz, style = Stroke(w))
                        drawArc(accent, -90f, 360f * Math.round(t.p * 100).toFloat() / 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    }
                },
        ) {
            if (g != null) {
                PicBox(remember(g) { Pic.backdrop(g) ?: Pic.cover(g) }, g, Modifier.matchParentSize().clip(CircleShape), ground = Color(g.colour))
            } else {
                Box(Modifier.matchParentSize().background(volt.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                    PnIcon("ui:hglass", volt, 22.dp, null)
                }
            }
            val badge = when (t.k) { "play" -> "ui:go"; "rel" -> "ui:cal"; "sale" -> "ui:tag"; "next" -> "ui:star"; else -> null }
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(6.dp, 5.dp)
                        .requiredSize(18.dp)
                        .background(Pn.ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { PnIcon(badge, accent, 11.dp, null) }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(t.v, style = VALUE.copy(color = if (hot) Pn.hot else if (t.soon) volt else Pn.ice).fixed(), maxLines = 1, softWrap = false)
        Spacer(Modifier.height(1.dp))
        BasicText(t.s, style = CAPTION.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(STRIP_TILE_W - 12.dp))
    }
}

private val CIRCLE: Dp = 44.dp
private val TILE_SHAPE = RoundedCornerShape(18.dp)
private val LIVE = TextStyle(fontFamily = Fonts.poppins, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .14.em, color = Pn.mute)
private val VALUE = TextStyle(fontFamily = Fonts.poppins, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Pn.ice)
private val CAPTION = TextStyle(fontFamily = Fonts.poppins, fontSize = 10.sp, lineHeight = 12.sp, color = Pn.mute, textAlign = TextAlign.Center)
private val NONE = TextStyle(fontFamily = Fonts.poppins, fontSize = 10.sp, lineHeight = 13.sp, color = Pn.mute, textAlign = TextAlign.Center)
