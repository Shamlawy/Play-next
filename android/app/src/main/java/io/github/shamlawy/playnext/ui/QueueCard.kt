package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import io.github.shamlawy.playnext.report.Crumbs
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Plat
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Big
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/**
 * The open Queue card (v299 front "Sweep", v300 back "Controller", v306 turn; android/parity/1-map/2-card.md): the
 * game's picture fills the card, its box art sits on a slanted strap in its colour with the status, one headline, the
 * platforms and genres; a ticket bottom-left (a released game's stub torn off), the logo bottom-right. One tap closes
 * it into its row (after 320 ms, waiting for a second), two quick taps turn it over in 420 ms, always the same way. A
 * double tap while it turns sends it back from where it is (the owner, Nov 3: "As you like"; the web ignores it). The
 * box art opens the game's page. The back's Edit opens the game's Edit; its ⋯ holds Competitive.
 */
@Composable
fun QueueCard(q: Queue, g: Game, n: Int, fresh: Boolean, flipped: Boolean, mod: Modifier, entered: () -> Unit, flip: (Boolean) -> Unit, foot: Dp = 26.dp, close: () -> Unit) {
    val calm = LocalCalm.current
    val st = remember(g, q) { q.stat(g) }
    val accent = Color(g.colour)
    val scope = rememberCoroutineScope()
    // the side it shows lives in the Queue's pick (`flipId`): a card scrolled away and back keeps it
    val angle = remember(g.id) { Animatable(if (flipped) 180f else 0f) }
    var turns by remember(g.id) { mutableStateOf(if (flipped) 1 else 0) }
    var backMade by remember(g.id) { mutableStateOf(flipped) }
    val enter = remember(g.id) { Animatable(if (fresh && !calm) 0f else ENTER_END) }
    val backT = remember(g.id) { Animatable(BACK_END) }
    val onBack = Math.floorMod(turns, 2) == 1
    androidx.compose.runtime.LaunchedEffect(g.id) {
        if (fresh) Sounds.play(if (st.torn) Sfx.CardTear else Sfx.CardOpen)
        // once: scrolled away and back, it doesn't open (or sound) again
        entered()
        if (enter.value < ENTER_END) enter.animateTo(ENTER_END, tween(ENTER_END.toInt(), easing = LinearEasing))
    }
    // one tap closes (after waiting [TAP_GAP] for a second), two turn; a tap on a button or a scroll is neither (v307)
    val taps = remember(g.id) { Taps() }
    val turn = {
        // mid-turn: back to the side it came from, whichever way it is heading; at rest: over, always the same way
        turns = if (!angle.isRunning) turns + 1
        else (if (angle.targetValue > angle.value) Math.floor(angle.value / 180.0) else Math.ceil(angle.value / 180.0)).toInt()
        val back = Math.floorMod(turns, 2) == 1
        flip(back)
        if (back) {
            backMade = true
            Sounds.play(Sfx.CardFlip)
            Sounds.play(Sfx.CardBack)
        } else Sounds.play(Sfx.CardFlipBack)
        Buzz.big(Big.CardTurn)
        val to = turns * 180f
        scope.launch {
            if (back) { if (calm) backT.snapTo(BACK_END) else { backT.snapTo(0f); launch { backT.animateTo(BACK_END, tween(BACK_END.toInt(), easing = LinearEasing)) } } }
            if (calm) angle.snapTo(to) else angle.animateTo(to, tween((TURN_MS * abs(to - angle.value) / 180f).toInt().coerceAtLeast(60), easing = TURN_EASE))
        }
        Unit
    }
    // one tap: it folds into its row at once (the row fades in where the card was and the rows under it glide up, Queue.kt);
    // the web fades the card for 220 ms first, which left a still hole and then a jump (the owner, Nov 5: "sluggish")
    val shutNow = {
        if (!angle.isRunning) {
            Sounds.play(Sfx.CardShut)
            close()
        }
    }
    val shape = RoundedCornerShape(Web.U_R.dp)
    BoxWithConstraints(
        mod.padding(bottom = foot).fillMaxWidth()
            .pointerInput(g.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up == null) { taps.pending?.cancel(); taps.pending = null; return@awaitEachGesture }
                    val t = up.uptimeMillis
                    val p = taps.pending
                    if (p != null && t - taps.at < TAP_GAP) {
                        p.cancel(); taps.pending = null
                        turn()
                    } else {
                        taps.at = t
                        taps.pending = scope.launch { delay(TAP_GAP); taps.pending = null; shutNow() }
                    }
                }
            }
            .semantics {
                contentDescription = g.title + ", " + st.head
                stateDescription = if (onBack) "back" else "front"
            },
    ) {
        val wide = maxWidth >= 620.dp
        val d = if (wide) CardDims.wide else CardDims.folded
        Box(Modifier.fillMaxWidth().height(d.h)) {
            val cam = 1600f / 72f
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    cameraDistance = cam * density
                    rotationY = angle.value
                    alpha = if (cos(Math.toRadians(angle.value.toDouble())) > 0) 1f else 0f
                }.clip(shape).background(Pn.void).border(1.dp, Pn.line, shape),
            ) { Front(q, g, n, st, accent, d, enter, onBack) }
            if (backMade) Box(
                Modifier.fillMaxSize().graphicsLayer {
                    cameraDistance = cam * density
                    rotationY = angle.value - 180f
                    alpha = if (cos(Math.toRadians(angle.value - 180.0)) > 0) 1f else 0f
                }.clip(shape).background(Pn.void).border(1.dp, Pn.line, shape),
            ) { Back(q, g, n, accent, d, backT, onBack) }
        }
    }
}

/**
 * The open card's front on its own, for the Game Shuffler's deck (the owner, Nov 2: "yeah queue card is better"): the
 * same picture, strap, ticket and logo, at the folded or the [big] (from 620 wide) size; no taps of its own but the box
 * art's (the game's page). [fresh] plays its opening (the strap sliding in, the glint) as each card comes up.
 */
@Composable
fun QueueFace(q: Queue, g: Game, n: Int, big: Boolean, fresh: Boolean, mod: Modifier) {
    val calm = LocalCalm.current
    val st = remember(g, q) { q.stat(g) }
    val enter = remember(g.id) { Animatable(if (fresh && !calm) 0f else ENTER_END) }
    androidx.compose.runtime.LaunchedEffect(g.id) {
        if (enter.value < ENTER_END) enter.animateTo(ENTER_END, tween(ENTER_END.toInt(), easing = LinearEasing))
    }
    val d = if (big) CardDims.wide else CardDims.folded
    val shape = RoundedCornerShape(Web.U_R.dp)
    Box(
        mod.fillMaxWidth().height(d.h).clip(shape).background(Pn.void).border(1.dp, Pn.line, shape)
            .semantics { contentDescription = g.title + ", " + st.head },
    ) { Front(q, g, n, st, Color(g.colour), d, enter, false) }
}

/** The card's height at the folded or [big] size. */
fun queueFaceHeight(big: Boolean): Dp = if (big) CardDims.wide.h else CardDims.folded.h

private class Taps {
    var at = 0L
    var pending: Job? = null
}

private const val TAP_GAP = 320L
private const val TURN_MS = 420f
private val TURN_EASE = CubicBezierEasing(.3f, 0f, .15f, 1f)
private const val ENTER_END = 2000f
private const val BACK_END = 1800f

/** A part of a timeline: 0 before [start] ms, 1 after [start] + [dur], eased between. */
private fun seg(t: Float, start: Float, dur: Float, e: Easing = Ease.house): Float = e.transform(((t - start) / dur).coerceIn(0f, 1f))

/** The front's and back's sizes, folded and from 620 wide (CSS 27177-27358, px = dp). */
private class CardDims(
    val h: Dp, val strapTop: Dp, val strapW: Float, val strapH: Dp, val strapR: Dp,
    val cov: Dp, val covAt: Dp, val infoLeft: Dp, val infoTop: Dp, val infoInset: Dp, val infoGap: Dp,
    val kick: TextUnit, val head: TextUnit, val genre: TextUnit, val logo: Dp, val logoGap: Dp, val ver: TextUnit, val verUp: Dp,
    val tkAt: Dp, val tkLeft: Dp, val tkH: Dp, val tkPad: Dp, val tkNum: TextUnit, val tkLabel: TextUnit,
    val lgW: Float, val lgMax: Dp, val lgH: Dp, val lgAt: Dp, val title: TextUnit,
    // the back
    val pad: Dp, val padBottom: Dp, val padSide: Dp, val gap: Dp, val bCov: Dp, val bLogo: Dp, val bTitle: TextUnit, val sub: TextUnit,
    val ctrl: Dp, val ctrlGap: Dp, val score: TextUnit, val btn: Dp, val btnNum: TextUnit, val btnCap: TextUnit, val cols: Int,
    val four: TextUnit, val fourGap: Dp, val fourLabel: TextUnit, val cap: TextUnit,
) {
    companion object {
        val folded = CardDims(
            340.dp, 36.dp, .72f, 108.dp, 24.dp, 92.dp, 18.dp, 124.dp, 26.dp, 136.dp, 4.dp,
            11.5.sp, 18.sp, 12.sp, 16.dp, 8.dp, 9.sp, 5.dp,
            16.dp, 16.dp, 60.dp, 12.dp, 20.sp, 10.5.sp,
            .4f, 1000.dp, 60.dp, 16.dp, 20.sp,
            14.dp, 18.dp, 14.dp, 12.dp, 30.dp, 28.dp, 16.sp, 12.sp,
            128.dp, 12.dp, 17.sp, 36.dp, 11.sp, 10.sp, 4,
            18.sp, 8.dp, 10.5.sp, 12.sp,
        )
        val wide = CardDims(
            555.dp, 56.dp, .54f, 176.dp, 30.dp, 150.dp, 30.dp, 214.dp, 64.dp, 226.dp, 8.dp,
            13.sp, 26.sp, 14.sp, 22.dp, 13.dp, 10.sp, 6.dp,
            26.dp, 28.dp, 88.dp, 20.dp, 32.sp, 12.5.sp,
            .4f, 340.dp, 100.dp, 28.dp, 30.sp,
            22.dp, 26.dp, 26.dp, 18.dp, 44.dp, 40.dp, 22.sp, 13.5.sp,
            236.dp, 28.dp, 30.sp, 52.dp, 13.sp, 12.sp, 8,
            32.sp, 26.dp, 12.5.sp, 13.5.sp,
        )
    }
}

private object CType {
    val base = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)
    val shadow = Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 3f)
}

private fun kindTone(k: Queue.Kind): Color = when (k) {
    Queue.Kind.Coming -> Pn.ice
    Queue.Kind.Out -> Color(Web.QK_OUT)
    Queue.Kind.Playing -> Color(Web.QK_PLAYING)
    Queue.Kind.Played -> Color(Web.QK_PLAYED)
    Queue.Kind.Dropped -> Pn.mute
}

// ---------------------------------------------------------------- the front (`qkFront`)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Front(q: Queue, g: Game, n: Int, st: Queue.Stat, accent: Color, d: CardDims, enter: Animatable<Float, AnimationVector1D>, onBack: Boolean) {
    val lib = q.lib
    val deep = Color(Web.QK_DEEP)
    val pic = remember(g, lib) { rowPic(g, lib) }
    val open = LocalOpenGame.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        // the picture (scaled down into place as it opens), or the game's colour glowing from the top right
        if (pic != null) {
            val bx = Backup.num(g["bgX"], 100.0, 0.0, 100.0).toFloat() / 50 - 1
            val by = Backup.num(g["bgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            PicBox(
                pic, g,
                Modifier.fillMaxSize().graphicsLayer { val s = 1.07f - .07f * seg(enter.value, 0f, 1200f); scaleX = s; scaleY = s },
                align = BiasAlignment(bx, by), ground = Pn.ink,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Pn.ink).drawBehind {
                drawRect(Brush.radialGradient(listOf(mixA(accent, Color.Transparent, .32f), Color.Transparent), Offset(size.width * .85f, 0f), size.width * 1.2f * .62f))
            })
        }
        // the veil: dark at the top left behind the words, and up from the foot behind the ticket and logo
        Box(Modifier.fillMaxSize().drawBehind {
            val v = Pn.void
            val sy = .75f * size.height / size.width
            scale(1f, sy, pivot = Offset.Zero) {
                drawRect(Brush.radialGradient(0f to v.copy(alpha = .78f), .5f to v.copy(alpha = .35f), .78f to v.copy(alpha = 0f), center = Offset.Zero, radius = size.width), size = Size(size.width, size.height / sy))
            }
            drawRect(Brush.verticalGradient(0f to v.copy(alpha = 0f), .48f to v.copy(alpha = 0f), .74f to v.copy(alpha = .6f), 1f to v.copy(alpha = .94f)))
        })
        // the strap: skewed -6° from its left middle, the game's colour darkened on the left; it slides in, then a glint
        Box(
            Modifier.offset(y = d.strapTop).width(w * d.strapW).height(d.strapH)
                .graphicsLayer { translationX = -1.12f * size.width * (1 - seg(enter.value, 0f, 600f)) }
                .drawWithCache {
                    val k = tan(Math.toRadians(-6.0)).toFloat()
                    val r = d.strapR.toPx()
                    val p = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius.Zero, CornerRadius(r), CornerRadius(r), CornerRadius.Zero))
                        transform(Matrix().apply { values[Matrix.SkewY] = k })
                    }
                    val fill = Brush.horizontalGradient(0f to mixA(accent, deep, .55f), .55f to mixA(accent, deep, .76f), 1f to accent)
                    onDrawWithContent {
                        drawPath(p, fill)
                        clipPath(p) { this@onDrawWithContent.drawContent() }
                    }
                },
        ) {
            // the glint: drawn once, slid across on its own layer (only translation and alpha change per frame)
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        val gt = seg(enter.value, 1150f, 900f, CubicBezierEasing(0f, 0f, .58f, 1f))
                        alpha = if (gt > 0f && gt < 1f) 1f else 0f
                        translationX = size.width * .3f * (-1.2f + 5.4f * gt)
                    }
                    .drawWithCache {
                        val sheenW = size.width * .3f
                        val b = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.Transparent), 0f, sheenW)
                        onDrawBehind { drawRect(b, Offset(0f, -size.height), Size(sheenW, size.height * 3)) }
                    },
            )
        }
        // the box art: straight, top left; a tap opens the game's page (its cover flies there)
        val at = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val cs = RoundedCornerShape(12.dp)
        Box(
            Modifier.offset(d.covAt, d.covAt).width(d.cov).aspectRatio(2f / 3f)
                .graphicsLayer {
                    val t = enter.value
                    val a = seg(t, 250f, 420f)
                    val b = seg(t, 670f, 180f)
                    alpha = a
                    translationY = (if (t < 670f) -30f + 33f * a else 3f - 3f * b) * density
                }
                .onGloballyPositioned { at[0] = it }
                .clickable(enabled = !onBack, role = Role.Button) { open(g.id, at[0]?.takeIf { it.isAttached }?.let { FlyFrom(it.boxInWindow(), g) }) }
                .semantics { contentDescription = "Open " + g.title + "'s own page" }
                .clip(cs).border(1.dp, Color.White.copy(alpha = .14f), cs),
        ) {
            val ix = Backup.num(g["imgX"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            val iy = Backup.num(g["imgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), align = BiasAlignment(ix, iy), ground = Color(Web.COVER_BG))
        }
        // the words on the strap: status and place, the headline, the platforms, two genres
        Column(
            Modifier.offset(d.infoLeft, d.infoTop).width((w * d.strapW - d.infoInset).coerceAtLeast(40.dp)),
            verticalArrangement = Arrangement.spacedBy(d.infoGap),
        ) {
            val line = { i: Int -> Modifier.graphicsLayer { val p = seg(enter.value, LINE_AT[i], 450f); alpha = p; translationY = 10.dp.toPx() * (1 - p) } }
            val tone = kindTone(st.kind)
            Row(line(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(13.dp).background(tone.copy(alpha = .3f), Pn.pill).padding(3.dp).background(tone, Pn.pill))
                val ks = CType.base.copy(fontSize = d.kick, lineHeight = d.kick * 1.2f, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .8f), shadow = CType.shadow).fixed()
                BasicText(st.word + " · " + q.spot(n, false), style = ks, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = fits(ks.fontSize, .82f))
            }
            // a short headline never ends in "…" (the design eye): it steps down a little to fit the strap first
            val hs = CType.base.copy(fontSize = d.head, lineHeight = d.head * 1.1f, fontWeight = FontWeight.Bold, letterSpacing = (-.02).em, color = Color.White, shadow = CType.shadow).fixed()
            BasicText(st.head, style = hs, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = fits(hs.fontSize, .78f), modifier = line(1))
            val chips = remember(g) { Plat.chips(g["platforms"]) }
            if (chips.isNotEmpty()) PlatRow(chips, d.logo, d.logoGap, d.ver, d.verUp, Color.White, line(2).padding(top = 3.dp))
            val gn = Rank.platList(g["genres"]).take(2).joinToString(" · ")
            if (gn.isNotEmpty()) BasicText(
                gn, style = CType.base.copy(fontSize = d.genre, lineHeight = d.genre * 1.3f, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .82f), shadow = CType.shadow).fixed(),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = line(3),
            )
        }
        // the ticket, bottom left
        CardTicket(st, accent, d, enter, Modifier.align(Alignment.BottomStart).padding(start = d.tkLeft, bottom = d.tkAt))
        // the logo (or the name) in its fixed slot, bottom right
        CardLogo(g, d, w, enter, Modifier.align(Alignment.BottomEnd).padding(end = d.lgAt, bottom = d.lgAt))
    }
}

private val LINE_AT = floatArrayOf(550f, 620f, 700f, 760f)

/** Its size, or down to [least] of it in small steps when the line won't fit (then "…" as the last resort). */
private fun fits(size: TextUnit, least: Float) = TextAutoSize.StepBased(minFontSize = size * least, maxFontSize = size, stepSize = .5.sp)

/** The platforms: each family's logo with its versions raised beside it ("4·Vita"), iOS as a word, then names. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatRow(chips: List<Plat.Chip>, size: Dp, gap: Dp, ver: TextUnit, up: Dp, tint: Color, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (c in chips) {
            val small = CType.base.copy(fontSize = ver, lineHeight = ver, fontWeight = FontWeight.Bold, color = tint).fixed()
            when {
                c.k == null -> BasicText(c.text ?: "", style = CType.base.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = tint).fixed(), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 2.dp))
                else -> Row(Modifier.height(size), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (c.k == "io") BasicText("iOS", style = small.copy(fontSize = 12.sp, lineHeight = 12.sp), maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 3.dp))
                    else PnIcon("plat:" + c.k, tint, size, label = c.k)
                    if (c.sub.isNotEmpty()) BasicText(c.sub, style = small, maxLines = 1, softWrap = false, modifier = Modifier.offset(y = -up + 2.dp))
                }
            }
        }
    }
}

/**
 * A ticket half's outline: rounded on its outer side, with a 7dp half-circle bitten out at each end of the perforation.
 * A whole ticket's perforation is the seam, so both halves are bitten there; on a torn one it lies [perf] inside the
 * body (the owner, Nov 12: the line sat in the gap between the pieces, not inside the ticket), and the stub that tore
 * off has a plain straight edge.
 */
private class Notched(private val stub: Boolean, private val r: Dp, private val bite: Dp, private val perf: Dp?) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rr = with(density) { r.toPx() }
        val b = with(density) { bite.toPx() }
        val body = Path().apply {
            addRoundRect(
                if (stub) RoundRect(Rect(Offset.Zero, size), CornerRadius(rr), CornerRadius.Zero, CornerRadius.Zero, CornerRadius(rr))
                else RoundRect(Rect(Offset.Zero, size), CornerRadius.Zero, CornerRadius(rr), CornerRadius(rr), CornerRadius.Zero),
            )
        }
        if (stub && perf != null) return Outline.Generic(body)
        val x = if (stub) size.width else with(density) { (perf ?: 0.dp).toPx() }
        val holes = Path().apply {
            addOval(Rect(Offset(x, 0f), b))
            addOval(Rect(Offset(x, size.height), b))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}

/** How far inside the body a torn ticket's perforation runs (just past the notch's radius, so the line reads as printed on it). */
private val TORN_PERF = 9.dp

@Composable
private fun CardTicket(st: Queue.Stat, accent: Color, d: CardDims, enter: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    val perf = if (st.torn) TORN_PERF else null
    val stubShape = remember(perf) { Notched(true, 12.dp, 7.dp, perf) }
    val bodyShape = remember(perf) { Notched(false, 12.dp, 7.dp, perf) }
    val num = CType.base.copy(fontSize = d.tkNum, lineHeight = d.tkNum, fontWeight = FontWeight.Bold, letterSpacing = (-.03).em, fontFeatureSettings = "tnum").fixed()
    val label = CType.base.copy(fontSize = d.tkLabel, lineHeight = d.tkLabel * 1.2f, fontWeight = FontWeight.SemiBold).fixed()
    Row(
        modifier.height(d.tkH).graphicsLayer {
            val t = enter.value
            val p = seg(t, 750f, 600f, CubicBezierEasing(.34f, 1.56f, .64f, 1f))
            alpha = seg(t, 750f, 600f, LinearEasing).coerceAtMost(1f)
            translationY = 40.dp.toPx() * (1 - p)
            rotationZ = -6f * (1 - p)
        },
    ) {
        TicketHalf(st.stub, true, st.torn, accent, d, enter, stubShape, num, label)
        TicketHalf(st.body, false, st.torn, accent, d, enter, bodyShape, num, label)
    }
}

/** One half of the ticket: the stub (the game's colour, torn off a released game) or the body with its perforation (at its seam, or [TORN_PERF] inside it once torn). */
@Composable
private fun TicketHalf(v: Pair<String, String>, stub: Boolean, torn: Boolean, accent: Color, d: CardDims, enter: Animatable<Float, AnimationVector1D>, shape: Shape, num: TextStyle, label: TextStyle) {
    val deep = Color(Web.QK_DEEP)
    Column(
        Modifier.fillMaxHeight().graphicsLayer {
            if (!torn) return@graphicsLayer
            val k = ((enter.value - 1400f) / 500f).coerceIn(0f, 1f)
            if (stub) {
                // none, a tug at 35%, past it at 70%, then at rest: hanging off at 8° (`qkTear`)
                transformOrigin = TransformOrigin(0f, 1f)
                val (tx, ty, r) = when {
                    k < .35f -> Triple(-1f * k / .35f, 0f, -2f * k / .35f)
                    k < .7f -> ((k - .35f) / .35f).let { u -> Triple(-1f - 4f * u, 5f * u, -2f + 12f * u) }
                    else -> ((k - .7f) / .3f).let { u -> Triple(-5f, 5f - u, 10f - 2f * u) }
                }
                translationX = tx.dp.toPx(); translationY = ty.dp.toPx(); rotationZ = r
            } else translationX = (if (k < .35f) k / .35f else 1f + 3f * (k - .35f) / .65f).dp.toPx()
        }.then(
            if (stub) Modifier.background(Brush.linearGradient(listOf(accent, mixA(accent, deep, .68f)), Offset.Zero, Offset(120f, 330f)), shape)
            else Modifier.background(mixA(accent, Color(Web.QK_TICKET), .3f), shape).drawBehind {
                val sw = 2.dp.toPx()
                val x = if (torn) TORN_PERF.toPx() else sw / 2
                drawLine(mixA(accent, Pn.ice.copy(alpha = .4f), .55f), Offset(x, 0f), Offset(x, size.height), sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 3, sw * 2)))
            },
        ).padding(start = d.tkPad + if (torn && !stub) TORN_PERF else 0.dp, end = d.tkPad),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(v.first, style = num.copy(color = if (stub) Color.White else Pn.ice, shadow = if (stub) Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 6f) else null), maxLines = 1, softWrap = false)
        BasicText(v.second, style = label.copy(color = if (stub) Color.White.copy(alpha = .85f) else Pn.ice.copy(alpha = .72f)), maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun CardLogo(g: Game, d: CardDims, cardW: Dp, enter: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    val art = Backup.str(g["art"])
    val slotW = minOf(cardW * d.lgW, d.lgMax)
    Box(
        modifier.width(slotW).height(d.lgH)
            .graphicsLayer {
                val p = seg(enter.value, 1000f, 600f)
                alpha = p; translationY = 8.dp.toPx() * (1 - p); val s = .97f + .03f * p; scaleX = s; scaleY = s
            }
            .drawBehind {
                // a soft dark halo behind the logo (`.qkt::before`, inset -60 -40 -40 -50, closest-side)
                val l = -50.dp.toPx(); val t = -60.dp.toPx()
                val w = size.width + 90.dp.toPx(); val h = size.height + 100.dp.toPx()
                val sy = h / w
                translate(l, t) {
                    scale(1f, sy, pivot = Offset.Zero) {
                        drawRect(Brush.radialGradient(listOf(Pn.void.copy(alpha = .72f), Pn.void.copy(alpha = 0f)), Offset(w / 2, w / 2), w / 2), size = Size(w, w))
                    }
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (art.isNotEmpty()) {
            val ctx = LocalContext.current
            val req = remember(art) { ImageRequest.Builder(ctx).data(picUrl(art)).allowHardware(false).transformations(InkCrop(art)).build() }
            var ink by remember(art) { mutableStateOf(LogoInk.known[art]) }
            AsyncImage(
                model = req, contentDescription = g.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd,
                colorFilter = if (ink?.dark == true) SILHOUETTE else null, onSuccess = { ink = LogoInk.known[art] },
                modifier = Modifier.fillMaxSize(),
            )
        } else BasicText(
            g.title,
            style = CType.base.copy(fontSize = d.title, lineHeight = d.title * 1.08f, fontWeight = FontWeight.Bold, letterSpacing = (-.015).em, textAlign = TextAlign.End,
                shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 14f)).fixed(),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- the back (`qkBack`, v300 "Controller")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Back(q: Queue, g: Game, n: Int, accent: Color, d: CardDims, t: Animatable<Float, AnimationVector1D>, onBack: Boolean) {
    val lib = q.lib
    val rk = q.rk
    val areas = remember(g, q) { q.areas(g) }
    val played = rk.isPlayed(g)
    val p = rk.playedRec(g)
    val score = q.myScore(g)
    val big = areas?.big(score)
    // ⋯'s menu, open until a pick or the card turns back
    var menu by remember(g.id) { mutableStateOf(false) }
    LaunchedEffect(onBack) { if (!onBack) menu = false }
    val runs = p?.let { Backup.num(it.o["runs"], 1.0, 1.0, 99.0).toInt() } ?: 1
    val hrs = rk.hrsOf(g)
    val num = { v: Double -> Queue.jsNum(v) }
    val critic = Backup.num(g["critic"], 0.0, -1e9, 1e9).takeIf { it != 0.0 }?.let(num)
    val hype = q.hype(g).takeIf { it != 0.0 }?.let(num)
    val four: List<Triple<String, String, String>> = if (played) listOf(
        Triple(if (score != 0) "$score" else Queue.DASH, "", "my score"),
        if (p?.scored == true) Triple(num(p.real!!), "/10", "it deserved") else Triple(Queue.DASH, "", "it deserved"),
        Triple("$runs", "×", "played"),
        Triple(critic ?: Queue.DASH, "", "critics"),
    ) else {
        val cd = q.countdownWords(g)
        val e = q.expected(g)
        listOf(
            Triple(cd.first, "", cd.second),
            Triple(if (hrs != 0.0) num(hrs) else Queue.DASH, if (hrs != 0.0) "h" else "", "to beat"),
            Triple(hype ?: Queue.DASH, if (hype != null) "/10" else "", "hype"),
            if (e != null) Triple(String.format(java.util.Locale.ROOT, "%.1f", e), "/10", "we'd expect") else Triple(critic ?: Queue.DASH, "", if (critic != null) "critics" else "we'd expect"),
        )
    }
    val pic = remember(g, lib) { rowPic(g, lib) }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        if (pic != null) PicBox(pic, g, Modifier.fillMaxSize(), align = BiasAlignment(0f, -.4f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to mixA(accent, Color(Web.QK_DEEP).copy(alpha = .93f), .26f), .46f to Color(Web.QK_DEEP).copy(alpha = .96f), 1f to Color(Web.QK_DEEP).copy(alpha = .98f))))
        val small = CType.base.copy(fontWeight = FontWeight.SemiBold, color = Pn.mute)
        Column(
            // the back scrolls only while it shows: hidden, it would still catch the front's touches (the box art)
            Modifier.fillMaxSize().then(if (onBack) Modifier.verticalScroll(scroll) else Modifier).padding(start = d.padSide, end = d.padSide, top = d.pad, bottom = d.padBottom),
            verticalArrangement = Arrangement.spacedBy(d.gap),
        ) {
            // the header: box art, the logo, Edit and ⋯
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val cs = RoundedCornerShape(6.dp)
                Box(Modifier.width(if (d.cols == 8) 44.dp else 30.dp).aspectRatio(2f / 3f).clip(cs).border(1.dp, Pn.ice.copy(alpha = .18f), cs)) {
                    PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.COVER_BG))
                }
                val art = Backup.str(g["art"])
                Box(Modifier.weight(1f).height(d.bLogo), contentAlignment = Alignment.CenterStart) {
                    if (art.isNotEmpty()) {
                        val ctx = LocalContext.current
                        val req = remember(art) { ImageRequest.Builder(ctx).data(picUrl(art)).allowHardware(false).transformations(InkCrop(art)).build() }
                        var ink by remember(art) { mutableStateOf(LogoInk.known[art]) }
                        AsyncImage(model = req, contentDescription = g.title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            colorFilter = if (ink?.dark == true) SILHOUETTE else null, onSuccess = { ink = LogoInk.known[art] }, modifier = Modifier.fillMaxSize())
                    } else BasicText(g.title, style = CType.base.copy(fontSize = d.bTitle, lineHeight = d.bTitle * 1.1f, fontWeight = FontWeight.Bold, letterSpacing = (-.015).em).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Edit and ⋯ (`.qbib`): the drawn pencil opens Edit, ⋯ holds Competitive (Diary comes with Phase 5)
                LocalEditActs.current?.let { acts -> BackButtons(g, acts, onBack) { menu = !menu } }
            }
            val acts = LocalEditActs.current
            if (menu && onBack && acts != null) BackMenu(g) { menu = false; acts.comp(g) }
            BasicText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = mixA(accent, Pn.ice, .4f))) { append(q.spot(n, true)) }
                    append(" · " + q.whenWords(g))
                },
                style = small.copy(fontSize = d.sub, lineHeight = d.sub * 1.4f).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // the controller (your score on its screen) and the eight round buttons, one per area
            val guess = areas?.guess == true
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.ctrlGap)) {
                Column(Modifier.width(d.ctrl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(64f / 44f)) {
                        Vessel(true, (big ?: 0) / 10.0, if (guess) mixOk(accent, Pn.ink, .6f) else accent, guess, t, 220f, 1000f, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().padding(bottom = d.ctrl * 44f / 64f * .13f), contentAlignment = Alignment.Center) {
                            BasicText(big?.toString() ?: Queue.DASH, style = CType.base.copy(fontSize = d.score, lineHeight = d.score, fontWeight = FontWeight.Bold, letterSpacing = (-.03).em, fontFeatureSettings = "tnum").fixed(), maxLines = 1, softWrap = false)
                        }
                    }
                    BasicText(if (areas == null) "no scores yet" else if (guess) "Nexi's guess" else "my score", style = small.copy(fontSize = 10.5.sp, lineHeight = 13.sp).fixed(), maxLines = 1, softWrap = false)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in Queue.AREAS.indices.chunked(d.cols)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in row) {
                                val (k, l) = Queue.AREAS[i]
                                val v = areas?.vals?.get(i) ?: 0.0
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(d.btn), contentAlignment = Alignment.Center) {
                                        Vessel(false, v, mixOk(accent, Pn.ink, if (guess) .42f else .68f), guess, t, 300f + i * 50f, 800f, Modifier.fillMaxSize())
                                        BasicText(
                                            if (v != 0.0) String.format(java.util.Locale.ROOT, "%.1f", v) else Queue.DASH,
                                            style = CType.base.copy(fontSize = d.btnNum, lineHeight = d.btnNum, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", shadow = Shadow(Pn.void.copy(alpha = .85f), Offset(0f, 1f), 3f)).fixed(),
                                            maxLines = 1, softWrap = false,
                                        )
                                    }
                                    BasicText(Web.RING_CAP[k] ?: l, style = small.copy(fontSize = d.btnCap, lineHeight = d.btnCap * 1.3f).fixed(), maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
            BasicText(
                if (areas == null) "Score a game you've played and Nexi starts guessing these"
                else if (guess) "Nexi's guess, area by area, from the games you've scored"
                else if (played && runs > 1) "Your scores · played $runs×" else "Your scores",
                style = small.copy(fontSize = d.cap, lineHeight = d.cap * 1.4f).fixed(),
            )
            // the four numbers, each with its bar in the game's colour, rising in
            // each number as wide as its words, spread across (equal columns cut "available now" on a folded phone)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                four.forEachIndexed { i, (v, u, l) ->
                    Row(
                        Modifier.padding(end = if (i < 3) d.fourGap else 0.dp).graphicsLayer {
                            val p2 = seg(t.value, 450f + i * 50f, 400f, Ease.enter)
                            alpha = p2; translationY = 12.dp.toPx() * (1 - p2)
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.width(4.dp).height(d.four.value.dp * 1.05f + d.fourLabel.value.dp * 1.4f).background(Brush.verticalGradient(listOf(mixA(accent, Pn.ice, .45f), accent)), RoundedCornerShape(3.dp)))
                        Column {
                            BasicText(
                                buildAnnotatedString {
                                    append(v)
                                    if (u.isNotEmpty()) withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute, letterSpacing = 0.sp)) { append(u) }
                                },
                                style = CType.base.copy(fontSize = d.four, lineHeight = d.four * 1.05f, fontWeight = FontWeight.Bold, letterSpacing = (-.03).em, fontFeatureSettings = "tnum").fixed(),
                                maxLines = 1, softWrap = false,
                            )
                            BasicText(l, style = small.copy(fontSize = d.fourLabel, lineHeight = d.fourLabel * 1.4f).fixed(), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            // the platforms and how many evenings it takes
            val chips = remember(g) { Plat.chips(g["platforms"]) }
            if (chips.isNotEmpty() || hrs != 0.0) {
                // the evenings line moves whole onto its own line when it doesn't fit beside the logos (flex-wrap)
                FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (chips.isNotEmpty()) PlatRow(chips, 18.dp, 12.dp, 9.5.sp, 5.dp, Pn.ice, Modifier)
                    if (hrs != 0.0) {
                        val eve = q.evening
                        BasicText("About ${Math.ceil(hrs / eve).toInt()} evenings at ${num(eve)} hours a night", style = small.copy(fontSize = 12.sp, lineHeight = 16.sp).fixed(), maxLines = 1, softWrap = false)
                    }
                }
            }
            for ((key, head) in listOf("learn" to "Learn", "tips" to "Play it")) {
                val text = Backup.str(g[key])
                if (text.isNotEmpty()) BasicText(
                    buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append(head) }; append(" $text") },
                    style = CType.base.copy(fontSize = 13.sp, lineHeight = 19.sp, color = Pn.ice.copy(alpha = .78f)).fixed(),
                )
            }
        }
    }
}

private class VesselPaths(val glass: Path, val wave: Path, val deco: List<Path>, val screen: Path?, val shine: Path?)

private val vesselCache = HashMap<Boolean, VesselPaths>()

private fun paths(big: Boolean): VesselPaths = vesselCache.getOrPut(big) {
    fun p(d: String) = PathParser().parsePathString(d).toPath()
    VesselPaths(
        p(if (big) Web.QB_PAD else Web.QB_BTN), p(Web.QB_WAVE),
        if (big) listOf(
            p("M9 17h4v-4h4v4h4v4h-4v4h-4v-4H9z"),
            p("M46.7 14a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0-4.6 0z"), p("M52.2 19.5a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0-4.6 0z"),
            p("M41.2 19.5a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0-4.6 0z"), p("M46.7 25a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0-4.6 0z"),
        ) else emptyList(),
        if (big) Path().apply { addRoundRect(RoundRect(Rect(23f, 10f, 41f, 25f), CornerRadius(3f))) } else null,
        if (big) null else p("M8.5 11a9 9 0 0 1 5-4.5"),
    )
}

/**
 * `qbVessel`: the controller (64x44) or a round button (32x32) of glass, filled to [v] (0-10) with liquid in [fill];
 * dashed glass for Nexi's guess. The liquid rises in from below on [t] (ms after the turn) from [at] for [dur].
 */
@Composable
private fun Vessel(big: Boolean, v: Double, fill: Color, guess: Boolean, t: Animatable<Float, AnimationVector1D>, at: Float, dur: Float, modifier: Modifier) {
    val ps = remember(big) { paths(big) }
    val (vw, top, bot) = if (big) Triple(64f, 7f, 43f) else Triple(32f, 3f, 31f)
    val y = bot - (v / 10.0).toFloat().coerceIn(0f, 1f) * (bot - top)
    // The glass and its outlines are drawn once; the liquid and the bubbles are drawn once at rest, each on its own layer,
    // and only those layers' translation and alpha follow [t] (no redraw per frame, second-look on #695).
    Box(
        modifier.drawWithCache {
            val k = size.width / vw
            val clip = Path().apply { addPath(ps.glass); transform(Matrix().apply { scale(k, k) }) }
            onDrawWithContent {
                scale(k, pivot = Offset.Zero) { drawPath(ps.glass, Pn.ice.copy(alpha = .05f)) }
                clipPath(clip) { this@onDrawWithContent.drawContent() }
                scale(k, pivot = Offset.Zero) {
                    drawPath(ps.glass, Pn.ice.copy(alpha = .4f), style = Stroke(1.2f, pathEffect = if (guess) PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f)) else null))
                    for (dp in ps.deco) drawPath(dp, Pn.ice.copy(alpha = .3f), style = Stroke(1.2f))
                    ps.screen?.let {
                        drawPath(it, Pn.void.copy(alpha = .72f))
                        drawPath(it, Pn.ice.copy(alpha = .32f), style = Stroke(1f))
                    }
                    ps.shine?.let { drawPath(it, Pn.ice.copy(alpha = .45f), style = Stroke(1.4f, cap = StrokeCap.Round)) }
                }
            }
        },
    ) {
        // the liquid at its level; it rises from below the glass (bot + 4) to [y]
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                val rise = seg(t.value, at, dur, Ease.enter)
                translationY = (bot + 4f - y) * (1 - rise) * size.width / vw
            },
        ) { scale(size.width / vw, pivot = Offset.Zero) { translate(0f, y) { drawPath(ps.wave, fill) } } }
        // two bubbles riding up through the liquid
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                val b = ((t.value - 750f) / 1000f).coerceIn(0f, 1f)
                alpha = if (b > 0f && b < 1f) (if (b < .3f) b / .3f else 1f - (b - .3f) / .7f) * .7f else 0f
                val rise = seg(t.value, at, dur, Ease.enter)
                translationY = ((bot + 4f - y) * (1 - rise) - 14f * b) * size.width / vw
            },
        ) {
            scale(size.width / vw, pivot = Offset.Zero) {
                translate(0f, y) {
                    drawCircle(Pn.ice, 1.6f, Offset(if (big) 26f else 12f, 16f))
                    drawCircle(Pn.ice, 1.1f, Offset(if (big) 40f else 20f, 22f))
                }
            }
        }
    }
}

/**
 * The back's round buttons (`.qbi`, 32 on `--u-card`, a 48 touch): Edit, and ⋯ for its small menu. Only while the back
 * shows: hidden, they'd take the front's taps.
 */
@Composable
private fun BackButtons(g: Game, acts: EditActs, onBack: Boolean, more: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((icon, label) in QB_BTNS) {
            Box(
                Modifier.size(Pn.touch).then(
                    if (onBack) Modifier.clickable(role = Role.Button) {
                        if (icon == "qb:edit") { Crumbs.tap("card back edit", "queue"); acts.open(g.id) } else more()
                    } else Modifier,
                ).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp).background(Pn.card, Pn.pill).border(1.dp, Pn.line2, Pn.pill), contentAlignment = Alignment.Center) { PnIcon(icon, Pn.ice, 18.dp, null) }
            }
        }
    }
}

/** ⋯'s menu (`.hmenu`): Competitive, with the drawn tick when it is (Diary comes with Phase 5). */
@Composable
private fun BackMenu(g: Game, comp: () -> Unit) {
    Row(
        Modifier.background(Pn.pan, Pn.rs).border(1.dp, Pn.line2, Pn.rs).sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = comp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PnIcon("qb:comp", Pn.ice, 18.dp, null)
        BasicText("Competitive", style = Type.label.copy(color = Pn.ice), maxLines = 1, softWrap = false)
        if (g.comp) PnIcon("ui:tick", Pn.ice, 16.dp, "on")
    }
}

private val QB_BTNS = listOf("qb:edit" to "Edit", "qb:more" to "More")
