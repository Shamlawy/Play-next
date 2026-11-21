package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Home
import io.github.shamlawy.playnext.data.Notes
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home's middle tiles (Phase 3 piece 3; index.html `dxHome` 11152-11214, `dzPlay` 24615, `cdTick` 11618): Your queue
 * (the count in the breathing orb with its covers round it), Playing now (the colour cards, or the Classic covers),
 * Your statistic (the queue's hours as a donut) and Next out (the tilted ticket counting down to the second). Each
 * hides with its Customise switch (`S.homeHide`: queue, playing, hours, soon), and the rest take its room as the
 * web's grid does: folded the queue and the donut side by side, then Playing now, then the ticket; unfolded the queue
 * beside Playing now, the donut beside the ticket. [rise] is the panel's first-open rise for a piece.
 */
@Composable
internal fun HomeTiles(h: Home, wide: Boolean, screen: Dp, rise: (Int) -> Modifier) {
    val q = h.on("queue")
    val r = h.on("playing") && h.playing.isNotEmpty()
    val s = h.on("hours")
    val k = h.on("soon")
    val gap = if (wide) 18.dp else 14.dp
    // the queue's tile is as tall as its neighbour (a grid row), and folded never under its title + a 176 box
    val qMin = if (wide) 0.dp else 176.dp + QTITLE
    if (wide) {
        // "q q q r r r r r" / "s s s s k k k k": the queue fills Playing now's height; the donut and the ticket sit
        // under a line
        if (q || r) {
            TilePair(
                if (q) { m -> QueueTile(h, wide, screen, m, fill = r) } else null, 3f,
                if (r) { m -> PlayingTile(h, wide, screen, m) } else null, 5f,
                gap, qMin, rise(3),
            )
        }
        if (s || k) {
            Row(Modifier.fillMaxWidth().then(rise(4)), horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (s) StatTile(h, wide, screen, Modifier.weight(1f).topLine())
                if (k) TicketTile(h, wide, Modifier.weight(1f).topLine())
            }
        }
    } else {
        // "q s" / "r r" / "k k": the queue's box grows to the donut's height
        if (q || s) {
            TilePair(
                if (q) { m -> QueueTile(h, wide, screen, m, fill = s) } else null, 1f,
                if (s) { m -> StatTile(h, wide, screen, m) } else null, 1f,
                gap, qMin, rise(3),
            )
        }
        if (r) PlayingTile(h, wide, screen, rise(4))
        if (k) TicketTile(h, wide, rise(4))
    }
}

/** "Your queue" and the 10 under it: the queue box starts this far down its tile. */
private val QTITLE = 31.dp

/**
 * A grid row of two tiles ([aw] : [bw] of the width, [gap] between): [b] is laid out at its own height and [a] (the
 * queue) is given exactly that, or [aMin] when taller. With one of them missing the other takes the row.
 */
@Composable
private fun TilePair(
    a: (@Composable (Modifier) -> Unit)?, aw: Float,
    b: (@Composable (Modifier) -> Unit)?, bw: Float,
    gap: Dp, aMin: Dp, modifier: Modifier,
) {
    if (a == null || b == null) {
        Box(modifier.fillMaxWidth()) { (a ?: b)?.invoke(Modifier.fillMaxWidth()) }
        return
    }
    androidx.compose.ui.layout.Layout(
        contents = listOf({ a(Modifier) }, { b(Modifier) }),
        modifier = modifier.fillMaxWidth(),
    ) { (am, bm), c ->
        val g = gap.roundToPx()
        val w = c.maxWidth
        val wa = ((w - g) * aw / (aw + bw)).toInt()
        val wb = w - g - wa
        val bp = bm.first().measure(androidx.compose.ui.unit.Constraints(minWidth = wb, maxWidth = wb))
        val ha = maxOf(bp.height, aMin.roundToPx())
        val ap = am.first().measure(androidx.compose.ui.unit.Constraints.fixed(wa, ha))
        layout(w, maxOf(ha, bp.height)) {
            ap.place(0, 0)
            bp.place(wa + g, 0)
        }
    }
}

/** Unfolded, the donut and the ticket start under a faint line (`border-top: 1px --dxl; padding-top: 18px`). */
private fun Modifier.topLine() = drawBehind { drawRect(DXL, size = Size(size.width, 1.dp.toPx())) }.padding(top = 18.dp)

/** A tile's title (`.dxt`: 15, 600, white), with a small grey count after it, as one line of words. */
@Composable
private fun TileTitle(text: String, count: String = "") {
    val words = if (count.isEmpty()) androidx.compose.ui.text.AnnotatedString(text) else androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Pn.mute, fontWeight = FontWeight.Medium))
        append(" $count")
        pop()
    }
    BasicText(words, style = DXT.fixed(), maxLines = 1, softWrap = false, modifier = Modifier.semantics { heading() })
}

/**
 * What Home's endless loops (the orb's spin and glow) share: one clock in ms while Home shows, still in calm, under a
 * game page, scrolled off the screen ([onScreen] false) or with the app away (frames stop by themselves then).
 */
@Composable
private fun rememberLoopClock(onScreen: () -> Boolean): () -> Float {
    val calm = LocalCalm.current
    val covered = LocalCovered.current
    val see by rememberUpdatedState(onScreen)
    val held = { covered() || !see() }
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(calm) {
        if (calm) return@LaunchedEffect
        var last = -1L
        while (true) {
            androidx.compose.animation.core.withInfiniteAnimationFrameMillis { ms ->
                // under a game page nothing here draws: the clock holds (no frames asked for its sake are wasted
                // after this one, as the next only comes once it shows again)
                if (last >= 0 && !held()) t.floatValue += (ms - last).coerceIn(0L, 100L)
                last = ms
            }
            if (held()) {
                last = -1L
                // asleep until it shows again (both read snapshot state), no timer waking meanwhile
                snapshotFlow { held() }.first { !it }
            }
        }
    }
    return remember(calm) { { if (calm) 0f else t.floatValue } }
}

// ---------------------------------------------------------------- Your queue

/**
 * `.dxq`: "Your queue" over a box with the count in the orb (a slowly turning wobbly disc in the accent's colours, a
 * glow breathing behind it, the dark core with "N games waiting") and up to seven covers round it, each at its own
 * spot and tilt. A tap opens the Queue.
 */
@Composable
private fun QueueTile(h: Home, wide: Boolean, screen: Dp, modifier: Modifier, fill: Boolean) {
    val acts = LocalHomeActs.current
    val n = h.live.size
    Column(
        modifier
            .clickable(role = Role.Button, onClickLabel = "Open your queue") {
                Sounds.play(Sfx.Open)
                Crumbs.tap("queue tile", "home")
                acts?.tab?.invoke("games")
            }
            .semantics(mergeDescendants = true) { contentDescription = "Your queue: $n " + (if (n == 1) "game" else "games") + " waiting" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TileTitle("Your queue")
        // the box: as tall as the row leaves (beside a neighbour), else as the orb needs, never under 176 folded; the
        // covers sit at their spots as parts of its final size
        val minH = if (wide) 0.dp else 176.dp
        androidx.compose.ui.layout.Layout(
            content = {
                Orb(n, wide, screen)
                h.queueCovers.forEachIndexed { i, g ->
                    val sz = Web.DX_SPOT[i][2]
                    val turn = Web.DX_SPOT[i][3]
                    PicBox(
                        remember(g) { Pic.cover(g) }, g,
                        Modifier
                            .size(sz.dp)
                            .graphicsLayer { rotationZ = turn.toFloat() }
                            .shadow(10.dp, COVER_SHAPE, ambientColor = Q_SHADE, spotColor = Q_SHADE)
                            .clip(COVER_SHAPE),
                        ground = Color(Web.DX_Q_PIC),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fill) Modifier.weight(1f) else Modifier)
                // no clip: the web's box lets the edge covers and the glow hang over it
                .background(Pn.card, RoundedCornerShape(22.dp)),
        ) { ms, c ->
            val w = c.maxWidth
            val side = minOf((w * .74f).toInt(), 190.dp.roundToPx())
            val orb = ms.first().measure(androidx.compose.ui.unit.Constraints.fixed(side, side))
            val covers = ms.drop(1).map { it.measure(androidx.compose.ui.unit.Constraints()) }
            val hgt = maxOf(c.minHeight, minH.roundToPx(), orb.height, if (c.hasBoundedHeight && fill) c.maxHeight else 0)
            layout(w, hgt) {
                orb.place((w - orb.width) / 2, (hgt - orb.height) / 2)
                covers.forEachIndexed { i, p ->
                    val (x, y) = Web.DX_SPOT[i]
                    p.place((w * x / 100f).toInt() - p.width / 2, (hgt * y / 100f).toInt() - p.height / 2)
                }
            }
        }
    }
}

private val COVER_SHAPE = RoundedCornerShape(30)
private val Q_SHADE = Color(Web.DX_Q_PIC_SHADE)

/**
 * `.dxorb`: [size] square; the glow (`::before`, 16% wider each side, the accent fading out by 66%) breathes over 4.5 s
 * (opacity .45 to 1, scale .94 to 1.04, eased each way); the blob turns once in 9 s; the core is 76% wide. Only
 * graphicsLayer values move, so a frame redraws nothing; calm: both still.
 */
@Composable
private fun Orb(n: Int, wide: Boolean, screen: Dp) {
    val volt = LocalLook.current.volt
    val volt2 = LocalLook.current.volt2
    // off the screen (scrolled past, or Home's page swiped away) the orb asks for no frames
    var seen by remember { mutableStateOf(true) }
    val clock = rememberLoopClock { seen }
    val calm = LocalCalm.current
    Box(Modifier.fillMaxSize().onVisibilityChanged(minFractionVisible = .01f) { seen = it }, contentAlignment = Alignment.Center) {
        // the glow, 132% of the orb (drawn past its box)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p0 = if (calm) 1f else breath(clock())
                    scaleX = 1.32f
                    scaleY = 1.32f
                    alpha = .45f + .55f * p0
                }
                .graphicsLayer {
                    val p = if (calm) 1f else breath(clock())
                    val sc = .94f + .1f * p
                    scaleX = sc
                    scaleY = sc
                }
                .drawWithCache {
                    // `radial-gradient(circle, …)` reaches the box's corner (half its diagonal); the round edge clips it
                    val brush = Brush.radialGradient(0f to volt.copy(alpha = .38f), .66f to volt.copy(alpha = 0f), center = this.size.center, radius = this.size.minDimension / 2 * 1.4142f)
                    onDrawBehind { drawCircle(brush) }
                },
        )
        // the blob: a conic sweep from 200deg through the accent's second colour and the three fixed ones
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (calm) 0f else (clock() / 9000f % 1f) * 360f }
                .drawWithCache {
                    val outline = BLOB.createOutline(this.size, layoutDirection, this)
                    val path = (outline as Outline.Generic).path
                    val colours = listOf(volt2, Color(Web.DX_ORB_A), Color(Web.DX_ORB_B), Color(Web.DX_ORB_C), volt2)
                    // CSS's conic "from 200deg" starts at 200° clockwise from the top; Android's sweep starts at 3
                    // o'clock, so its start is turned by 200 - 90
                    val c0 = this.size.center
                    val sweep = object : androidx.compose.ui.graphics.ShaderBrush() {
                        override fun createShader(size: Size): androidx.compose.ui.graphics.Shader =
                            androidx.compose.ui.graphics.SweepGradientShader(c0, colours).apply {
                                setLocalMatrix(android.graphics.Matrix().apply { setRotate(110f, c0.x, c0.y) })
                            }
                    }
                    val glow = Color(Web.DX_ORB_GLOW)
                    onDrawBehind {
                        drawPath(path, glow, style = Stroke(2.dp.toPx()))
                        drawPath(path, sweep)
                    }
                },
        )
        // the core: the page's void with a faint ring, the count and "games waiting"
        Column(
            Modifier
                .fillMaxSize(.76f)
                .shadow(14.dp, CircleShape, ambientColor = Color(Web.DX_ORB_SHADE), spotColor = Color(Web.DX_ORB_SHADE))
                .background(Pn.void, CircleShape)
                .border(1.dp, Color(Web.DX_ORB_RING), CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // unfolded the web's later `.dxq .box b { clamp(56px, 8vw, 92px) }` wins over the orb's own size
            val big = if (wide) (screen.value * .08f).coerceIn(56f, 92f) else (screen.value * .1f).coerceIn(34f, 58f)
            BasicText("$n", style = COUNT.copy(fontSize = big.sp, lineHeight = big.sp).fixed(), maxLines = 1, softWrap = false)
            BasicText(if (n == 1) "game waiting" else "games waiting", style = WAITING.fixed(), maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** `pnBreath` at [ms]: 0 at the start, 1 half-way, eased in and out each way, 4.5 s round. */
private fun breath(ms: Float): Float {
    val t = ms / 4500f % 1f
    return if (t < .5f) EaseInOut.transform(t * 2) else 1f - EaseInOut.transform((t - .5f) * 2)
}

/** `border-radius: 46% 54% 42% 58% / 55% 45% 55% 45%`: the blob's wobble. */
private val BLOB = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        return Outline.Generic(Path().apply {
            addRoundRect(
                RoundRect(
                    0f, 0f, w, h,
                    topLeftCornerRadius = CornerRadius(w * .46f, h * .55f),
                    topRightCornerRadius = CornerRadius(w * .54f, h * .45f),
                    bottomRightCornerRadius = CornerRadius(w * .42f, h * .55f),
                    bottomLeftCornerRadius = CornerRadius(w * .58f, h * .45f),
                ),
            )
        })
    }
}

// ---------------------------------------------------------------- Playing now

/**
 * `.dxplay`: "Playing now" (or "Out & waiting", "Top of your list") and three cards. The New look (`dzPlay`, the
 * default): tall colour cards in each game's own colour, a big title, its picture fading in at the foot and a thin bar
 * of how far you are; folded they scroll sideways and stop at a card's edge. Classic (Customise › Playing now cards):
 * three covers side by side with the hype, the stars and a progress pill.
 */
@Composable
private fun PlayingTile(h: Home, wide: Boolean, screen: Dp, modifier: Modifier) {
    val classic = Backup.truthy((h.q.lib["dzX"] as? kotlinx.serialization.json.JsonObject)?.get("play"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TileTitle(h.playingTitle)
        if (classic) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                h.playing.forEach { g -> ClassicCard(h, g, Modifier.weight(1f)) }
                repeat(3 - h.playing.size) { Spacer(Modifier.weight(1f)) }
            }
        } else if (screen >= 700.dp) {
            // unfolded the three share the row (`flex: 1 1 0`)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                h.playing.forEach { g -> ColourCard(h, g, Modifier.weight(1f)) }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val w = minOf(maxWidth * .64f, 240.dp)
                val state = rememberLazyListState()
                val guard = LocalSwipeGuard.current
                LazyRow(
                    state = state,
                    flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // a touch that lands on the row while it can scroll is the row's own (the web's `sideScroller`):
                        // Shell's tab swipe leaves it alone, marked in the same first pass it clears the mark in
                        .pointerInput(state) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    if ((state.canScrollForward || state.canScrollBackward) && e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true
                                }
                            }
                        },
                ) {
                    itemsIndexed(h.playing, key = { _, g -> g.id }) { _, g -> ColourCard(h, g, Modifier.width(w)) }
                }
            }
        }
    }
}

/**
 * `.dzp`: 300 tall, round 26, the game's colour half mixed into the dark; the kicker, the title (24, three lines at
 * most), a round arrow chip, the picture over the foot's 60% fading in from 42% down it, and a white bar of how far
 * you are while you play it.
 */
@Composable
private fun ColourCard(h: Home, g: Game, modifier: Modifier) {
    val open = LocalOpenGame.current
    val c = remember(h, g) { h.card(g) }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .height(300.dp)
            .clip(shape)
            .background(mix(Color(Web.DZ_BASE), Color(g.colour), .5f))
            .clickable(role = Role.Button, onClickLabel = "Open ${g.title}") { open(g.id, null) },
    ) {
        // the picture (backdrop, else cover) in the foot's 60%, clear at its top and whole from 42% of the way down
        PicBox(
            remember(g) { Pic.backdrop(g) ?: Pic.cover(g) }, g,
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(.6f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(FADE_IN, blendMode = BlendMode.DstIn)
                },
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            BasicText(c.kicker, style = DZ_K.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.82f).graphicsLayer { alpha = .8f })
            // a word never splits (the browser lets it run to the clipped edge; three cards unfolded leave ~90dp): a name
            // whose longest word is wider shrinks until it fits, as the Queue's rows do
            WholeName(g.title, DZ_T.fixed(), Modifier.padding(top = 6.dp).fillMaxWidth(.82f), maxLines = 3)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).size(34.dp).background(Color(Web.DZ_GO), CircleShape).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { PnIcon("ui:arrow", Color.White, 14.dp, null) }
        c.bar?.let { p ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(Web.DZ_TRACK))
                    .drawBehind { drawRoundRect(Color.White, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(2.dp.toPx())) },
            )
        }
    }
}

/** `mask-image: linear-gradient(to bottom, transparent, black 42%)`. */
private val FADE_IN = Brush.verticalGradient(0f to Color.Transparent, .42f to Color.Black)

/**
 * `.dxc` (Classic): the cover whole (3 : 4.9, round 20) darkening from 38% to its foot; the hype upright in a dark pill
 * at the top right; at the foot the name, where you were (a pin), hours left (or the win rate for a competitive game),
 * five stars of the hype and a progress pill with a small play mark (a competitive game: its red pill instead).
 */
@Composable
private fun ClassicCard(h: Home, g: Game, modifier: Modifier) {
    val open = LocalOpenGame.current
    val volt = LocalLook.current.volt
    val c = remember(h, g) { h.card(g) }
    val wi = remember(g) { if (g.comp) null else Notes.last(g)?.x?.takeIf { it.isNotBlank() } }
    val hype = Backup.num(g["hype"], 0.0, 0.0, 10.0)
    Box(
        modifier
            .aspectRatio(3f / 4.9f)
            .clip(RoundedCornerShape(20.dp))
            .background(Pn.card)
            .clickable(role = Role.Button, onClickLabel = "Open ${g.title}") { open(g.id, null) },
    ) {
        PicBox(remember(g) { Pic.cover(g) }, g, Modifier.fillMaxSize(), ground = Pn.card)
        Box(Modifier.fillMaxSize().background(CLASSIC_SHADE))
        if (hype > 0) {
            // `.vb`: the flame and the number written upwards (`writing-mode: vertical-rl` turned 180°)
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(Web.DX_VB), Pn.pill).padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UpText(Rank.jsRound(hype).toLong().toString())
                    PnIcon("ui:flame", Pn.hot, 12.dp, null)
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(g.title, style = DXC_NAME.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            if (wi != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    PnIcon("ui:pin", DXC_SMALL.color, 10.dp, null)
                    BasicText(" $wi", style = DXC_SMALL.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            BasicText(c.small, style = DXC_SMALL.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.semantics { contentDescription = "${c.stars} of 5 stars" }, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(5) { i -> PnIcon("ui:star", if (i < c.stars) Color(Web.DX_STAR) else Color(Web.DX_STAR_OFF), 9.dp, null) }
            }
            if (g.comp) {
                BasicText(
                    "Competitive", style = DXCOMP.fixed(), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(top = 5.dp).background(Color(Web.DX_COMP), Pn.pill).padding(horizontal = 9.dp, vertical = 3.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BoxWithConstraints(Modifier.weight(1f).height(20.dp).clip(Pn.pill).background(Color(Web.DX_PB_TRACK))) {
                        // `calc(40px + (100% - 40px) * pct)`: never shorter than its words
                        val fill = 40.dp + (maxWidth - 40.dp) * c.pct
                        Box(Modifier.width(fill.coerceAtMost(maxWidth)).fillMaxHeight().background(volt, Pn.pill))
                        BasicText(c.pctText, style = PB_TEXT.fixed(), maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterStart).padding(start = 9.dp))
                    }
                    Box(Modifier.size(20.dp).background(Color(Web.DX_PB_GO), CircleShape), contentAlignment = Alignment.Center) {
                        PnIcon("ui:go", Color.White, 11.dp, null)
                    }
                }
            }
        }
    }
}

/** Words turned to read upwards (the web's vertical pill text). */
@Composable
private fun UpText(text: String) {
    val style = VB.fixed()
    androidx.compose.ui.layout.Layout(content = { BasicText(text, style = style, maxLines = 1, softWrap = false) }) { m, c ->
        val p = m.first().measure(androidx.compose.ui.unit.Constraints())
        layout(p.height, p.width) {
            p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = -90f }
        }
    }
}

/** `.dxc::after`: clear down to 38%, near black by 80%. */
private val CLASSIC_SHADE = Brush.verticalGradient(0f to Color.Transparent, .38f to Color(Web.DX_C_SHADE).copy(alpha = 0f), .8f to Color(Web.DX_C_SHADE), 1f to Color(Web.DX_C_SHADE))

// ---------------------------------------------------------------- Your statistic

/**
 * `.dxstat`: "Your statistic" and "Queue hours" (with how you play, unless main story), the chart button (Plan comes
 * in phase 5), the donut of the queue's hours (the three games holding the most and the rest), its middle with the
 * hours and "≈ N evenings", the legend, and Plan evenings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatTile(h: Home, wide: Boolean, screen: Dp, modifier: Modifier) {
    val say = rememberSay()
    val volt = LocalLook.current.volt
    val plan = {
        Sounds.play(Sfx.Open)
        Crumbs.tap("plan", "home")
        say(soon("plan"))
    }
    val segs = h.donut
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                BasicText("Your statistic", style = DXT.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                BasicText(h.donutHead, style = ST_SMALL.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = plan).semantics { contentDescription = "Plan" },
                contentAlignment = Alignment.Center,
            ) { PnIcon("ui:chart", Pn.ice, 22.dp, null) }
        }
        val words = h.donutHours + ", " + h.donutSmall
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .widthIn(max = if (wide) 200.dp else 210.dp)
                .aspectRatio(1f)
                .semantics(mergeDescendants = true) { contentDescription = "Queue hours: $words" },
            contentAlignment = Alignment.Center,
        ) {
            Donut(segs, Modifier.fillMaxSize())
            // `.core`: 58% wide, lit from 28% down, with its shadow
            Column(
                Modifier
                    .fillMaxSize(.58f)
                    .shadow(16.dp, CircleShape, ambientColor = Pn.shade, spotColor = Pn.shade)
                    .background(CORE, CircleShape)
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val big = if (wide) (screen.value * .028f).coerceIn(20f, 30f) else (screen.value * .054f).coerceIn(20f, 30f)
                BasicText("Queue hours", style = CORE_SMALL.fixed(), maxLines = 1, softWrap = false)
                BasicText(h.donutHours, style = CORE_BIG.copy(fontSize = big.sp, lineHeight = (big * 1.1f).sp).fixed(), maxLines = 1, softWrap = false)
                BasicText(h.donutSmall, style = CORE_SMALL.fixed(), maxLines = 1, softWrap = false)
            }
        }
        if (segs.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (s in segs) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).background(Color(Web.DX_RING[s.colour]), CircleShape))
                        BasicText(
                            buildLegend(s.label, Rank.jsRound(s.share * 100).toLong().toString() + "%"),
                            style = LG.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // `.dxpill.wide`: 46 tall, at most 210 wide, its words shown
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .widthIn(max = 210.dp)
                .height(46.dp)
                .border(1.5.dp, volt, Pn.pill)
                .clip(Pn.pill)
                .clickable(role = Role.Button, onClick = plan),
            contentAlignment = Alignment.Center,
        ) { BasicText("Plan evenings", style = WIDE_PILL.fixed(), maxLines = 1, softWrap = false) }
    }
}

/** "Name 42%" with the share in grey. */
private fun buildLegend(name: String, share: String) = androidx.compose.ui.text.buildAnnotatedString {
    append(name)
    append(" ")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Pn.mute))
    append(share)
    pop()
}

/**
 * The donut's ring (an SVG circle of r 40 in a 100 box, stroke 11, turned to start at the top): the track, then each
 * slice as a round-capped arc its share long less a 15 gap (with two or more), at least .5.
 */
@Composable
private fun Donut(segs: List<Home.Slice>, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 100f
        val r = 40f * u
        val sw = 11f * u
        val tl = Offset(center.x - r, center.y - r)
        val box = Size(r * 2, r * 2)
        drawCircle(Color(Web.DX_DONUT_TRACK), r, center, style = Stroke(sw, cap = StrokeCap.Butt))
        val c = 2 * PI.toFloat() * 40f
        val gap = if (segs.size > 1) 15f else 0f
        var at = 0f
        for (s in segs) {
            val len = maxOf(.5f, s.share.toFloat() * c - gap)
            drawArc(
                Color(Web.DX_RING[s.colour]), -90f + at / c * 360f, len / c * 360f, false, tl, box,
                style = Stroke(sw, cap = StrokeCap.Round),
            )
            at += s.share.toFloat() * c
        }
    }
}

/** `.donut .core`: `radial-gradient(circle at 50% 28%, #1b2346, #0b0f1d 70%)`. */
private val CORE: Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: Size): androidx.compose.ui.graphics.Shader {
        val c = Offset(size.width * .5f, size.height * .28f)
        // a circle's 100% reaches the farthest corner
        val r = maxOf(c.getDistance(), (Offset(size.width, size.height) - c).getDistance(), (Offset(size.width, 0f) - c).getDistance(), (Offset(0f, size.height) - c).getDistance())
        return androidx.compose.ui.graphics.RadialGradientShader(c, r, listOf(Color(Web.DX_CORE_A), Color(Web.DX_CORE_B), Color(Web.DX_CORE_B)), listOf(0f, .7f, 1f))
    }
}

// ---------------------------------------------------------------- Next out

/**
 * `.dxtick`: "Next out (N)" and the next game still ahead on a tilted ticket in the accent (-3°, unfolded -5°), its
 * cover hanging over the top turned the other way, a notch at its foot; the name, "12 days to go · 13 Sept", the
 * countdown's four boxes ticking each second, and Open game. Once the last dated one is out: "It's out — go play it!"
 * and no countdown. Nothing dated: a ticket that says how to get one, with Add a game.
 */
@Composable
private fun TicketTile(h: Home, wide: Boolean, modifier: Modifier) {
    val volt = LocalLook.current.volt
    val volt2 = LocalLook.current.volt2
    val open = LocalOpenGame.current
    val acts = LocalHomeActs.current
    val g = h.ticket
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TileTitle("Next out", if (h.ticketAhead) "(${h.fut.size})" else "")
        val turn = if (wide) -5f else -3f
        val top = if (g != null) (if (wide) 60.dp else 54.dp) else 8.dp
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = top, bottom = if (wide) 18.dp else 16.dp, start = if (wide) 10.dp else 14.dp, end = if (wide) 10.dp else 14.dp)
                .graphicsLayer { rotationZ = turn }
                .shadow(22.dp, RoundedCornerShape(26.dp), clip = false, ambientColor = volt2, spotColor = volt2)
                .drawWithCache {
                    val brush = cssLinear(160f, size, listOf(0f to volt, .7f to volt2, 1f to volt2))
                    onDrawBehind {
                        drawRoundRect(brush, cornerRadius = CornerRadius(26.dp.toPx()))
                        // the notch: a 58 x 29 half circle in the panel's colour, at the foot's middle
                        val nw = 58.dp.toPx()
                        val nh = 29.dp.toPx()
                        drawArc(Pn.pan, 180f, 180f, true, Offset((size.width - nw) / 2, size.height - nh + 1.dp.toPx()), Size(nw, nh * 2))
                    }
                },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = if (g != null) 96.dp else 30.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (g == null) {
                    BasicText("Nothing dated yet", style = TK_NAME.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText("Give a game a release date and its countdown lands here.", style = TK_LINE.fixed(), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp).graphicsLayer { alpha = .92f })
                    TicketButton("Add a game") {
                        Sounds.play(Sfx.Open)
                        Crumbs.tap("add", "home")
                        acts?.tab?.invoke("add")
                    }
                } else {
                    BasicText(g.title, style = TK_NAME.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText(h.ticketLine(g), style = TK_LINE.fixed(), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp).graphicsLayer { alpha = .92f })
                    if (h.ticketAhead) Countdown(g.date, Modifier.padding(bottom = 16.dp))
                    TicketButton("Open game") { open(g.id, null) }
                }
            }
            if (g != null) {
                // `.post`: 96 wide, 3:4, 44 above the ticket's top, turned 7°, a white edge
                PicBox(
                    remember(g) { Pic.cover(g) }, g,
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-44).dp)
                        .width(96.dp)
                        .aspectRatio(3f / 4f)
                        .graphicsLayer { rotationZ = 7f }
                        .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Pn.shade, spotColor = Pn.shade)
                        .clip(RoundedCornerShape(16.dp))
                        .border(3.dp, Color.White.copy(alpha = .92f), RoundedCornerShape(16.dp)),
                    ground = Pn.card,
                )
            }
        }
    }
}

/** `.tk button`: a black pill, 13/600 white words. */
@Composable
private fun TicketButton(text: String, onClick: () -> Unit) {
    BasicText(
        text, style = TK_BTN.fixed(), maxLines = 1, softWrap = false,
        modifier = Modifier
            .shadow(10.dp, Pn.pill, ambientColor = Pn.shade, spotColor = Pn.shade)
            .clip(Pn.pill)
            .background(Color(Web.DX_TK_BTN))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 11.dp),
    )
}

/**
 * `.cd`: days, hours, minutes and seconds to the release's local midnight, each in its own box, ticking once a second
 * while the app is open. A digit that changes slides up out of its box as the new one rises in with a small overshoot
 * (520 ms); in calm, under a game page or off the screen it simply changes.
 */
@Composable
private fun Countdown(date: String, modifier: Modifier) {
    val now = LocalNow.current
    val covered = LocalCovered.current
    val zone = remember { java.time.ZoneId.systemDefault() }
    var parts by remember(date) { mutableStateOf(Home.countdown(date, now(), zone)) }
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(date, life) {
        life.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = now()
                if (!covered()) parts = Home.countdown(date, t, zone)
                delay(1000 - t % 1000)
            }
        }
    }
    var seen by remember { mutableStateOf(false) }
    val p = parts
    Row(
        modifier
            .onVisibilityChanged { seen = it }
            .semantics(mergeDescendants = true) { contentDescription = "${p[0]} days, ${p[1]} hours, ${p[2]} minutes, ${p[3]} seconds to go" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((i, u) in UNITS.withIndex()) {
            val v = if (i == 0) p[0].toString() else p[i].toString().padStart(2, '0')
            Column(
                Modifier.weight(1f, fill = false).width(50.dp).background(Color(Web.DX_CDB), RoundedCornerShape(12.dp)).padding(start = 2.dp, end = 2.dp, top = 7.dp, bottom = 5.dp).clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Digit(v, { seen })
                BasicText(u, style = CD_UNIT.fixed(), maxLines = 1, softWrap = false)
            }
        }
    }
}

private val UNITS = listOf("D", "H", "M", "S")

/**
 * One countdown box's number: when it changes (and Home is calm-free, shown and on screen) the old one slides up 110%
 * and fades (`cubic-bezier(.5,0,.2,1)`) while the new rises from 110% below to 10% above by three quarters of the way
 * and settles (`cubic-bezier(.2,.9,.3,1)`), both 520 ms, clipped to the box.
 */
@Composable
private fun Digit(v: String, onScreen: () -> Boolean) {
    val calm = LocalCalm.current
    val covered = LocalCovered.current
    var shown by remember { mutableStateOf(v) }
    var old by remember { mutableStateOf<String?>(null) }
    val k = remember { Animatable(1f) }
    val see by rememberUpdatedState(onScreen)
    LaunchedEffect(v) {
        if (v == shown) return@LaunchedEffect
        val was = shown
        shown = v
        if (calm || covered() || !see()) {
            old = null
            k.snapTo(1f)
            return@LaunchedEffect
        }
        old = was
        k.snapTo(0f)
        launch {
            try {
                k.animateTo(1f, tween(520, easing = LinearEasing))
            } finally {
                old = null
            }
        }
    }
    val style = CD_DIGIT.fixed()
    Box(Modifier.graphicsLayer { clip = true }, contentAlignment = Alignment.Center) {
        BasicText(
            shown, style = style, maxLines = 1, softWrap = false,
            modifier = Modifier.graphicsLayer {
                val t = k.value
                if (t < 1f) {
                    val e = RISE_IN.transform(t)
                    translationY = size.height * if (e < .75f) 1.1f + (-.1f - 1.1f) * (e / .75f) else -.1f * (1 - (e - .75f) / .25f)
                }
            },
        )
        old?.let { o ->
            BasicText(
                o, style = style, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val e = SLIDE_OUT.transform(k.value)
                    translationY = -1.1f * size.height * e
                    alpha = 1 - e
                },
            )
        }
    }
}

private val SLIDE_OUT = CubicBezierEasing(.5f, 0f, .2f, 1f)
private val RISE_IN = CubicBezierEasing(.2f, .9f, .3f, 1f)

/**
 * CSS's `linear-gradient(<deg>, …)` over a box of [size]: the line through the middle at that angle (0° up, clockwise),
 * as long as the box's corners need.
 */
internal fun cssLinear(deg: Float, size: Size, stops: List<Pair<Float, Color>>): Brush {
    val a = deg / 180f * PI.toFloat()
    val dx = sin(a)
    val dy = -cos(a)
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2
    val c = Offset(size.width / 2, size.height / 2)
    return Brush.linearGradient(*stops.toTypedArray(), start = c - Offset(dx * half, dy * half), end = c + Offset(dx * half, dy * half))
}

/** Something drawn over the page is covering it (a game page has fully grown over everything): loops hold still. */
val LocalCovered = staticCompositionLocalOf<() -> Boolean> { { false } }

// ---------------------------------------------------------------- words

private val P = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)
private val DXT = P.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val COUNT = P.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.03).em, color = Color.White, textAlign = TextAlign.Center)
private val WAITING = P.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = Pn.mute)
private val DZ_K = P.copy(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
private val DZ_T = P.copy(
    fontSize = 24.sp, lineHeight = 25.4.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.02).em, color = Color.White,
    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .25f), Offset(0f, 1f), 12f),
)
private val DXC_NAME = P.copy(fontSize = 12.5.sp, lineHeight = 15.6.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
private val DXC_SMALL = P.copy(fontSize = 10.5.sp, lineHeight = 14.sp, color = Pn.ice.copy(alpha = .8f), textAlign = TextAlign.Center)
private val DXCOMP = P.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
private val PB_TEXT = P.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = Color(Web.DX_PB_INK))
private val VB = P.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val ST_SMALL = P.copy(fontSize = 11.sp, lineHeight = 15.sp, color = Pn.mute)
private val CORE_SMALL = P.copy(fontSize = 9.5.sp, lineHeight = 12.4.sp, color = Pn.mute, textAlign = TextAlign.Center)
private val CORE_BIG = P.copy(fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
private val LG = P.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
private val WIDE_PILL = P.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val TK_NAME = P.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
private val TK_LINE = P.copy(fontSize = 12.sp, lineHeight = 17.4.sp, color = Color.White, textAlign = TextAlign.Center)
private val TK_BTN = P.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val CD_DIGIT = P.copy(fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFeatureSettings = "tnum")
private val CD_UNIT = P.copy(fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = .08.em, color = Color.White.copy(alpha = .78f))

/** `--dxl`: the panel's faint line. */
private val DXL = Pn.line
