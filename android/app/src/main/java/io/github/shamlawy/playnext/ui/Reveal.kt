package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.R
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pick for me's reveal (`pnReveal`, index.html 11692; android/parity/3-map/2-rail-reel-widgets.md §1): the dice picks a
 * game and this plays over everything, the rail too. A flash and a hit, the game's picture swings in on a slanted band,
 * a slash in its colour and a white one cut across, "TONIGHT'S PICK" slides in, the name lands with a slam and the
 * screen shakes; at 950 ms the game's page opens behind it, and at 1550 ms (or at a tap, or back) the scene cracks and
 * breaks into 8 shards that fly off, the page under them. None of it when calm: the page opens straight away.
 */
object Reveal {
    class Run(val g: Game, val colour: Color, val n: Int)

    /** The reveal playing, or null. */
    var run by mutableStateOf<Run?>(null)
        private set
    private var n = 0

    fun start(g: Game) {
        n += 1
        // `g.colour || "#4DA3FF"` (the burst's first colour falls back the same way)
        run = Run(g, Game.hex(Backup.str(g["colour"]))?.let { Color(it) } ?: Color(Web.CELE[0]), n)
    }

    internal fun done(r: Run) {
        if (run === r) run = null
    }

    /** Tests start from nothing playing. */
    internal fun reset() {
        run = null
    }
}

/** The scene's times (ms): its clock runs to [BREAK], where it cracks by itself. */
private const val OPEN = 950f
private const val BREAK = 1550f
/** The crack shows this long before the shards (ms), and the shards' whole flight (the overlay goes after it). */
private const val CRACKED = 110
private const val FLIGHT = 850f

private val CSS_EASE = CubicBezierEasing(.25f, .1f, .25f, 1f) // `ease`
private val EASE_OUT = CubicBezierEasing(0f, 0f, .58f, 1f) // `ease-out`
private val SLASH = CubicBezierEasing(.7f, 0f, .2f, 1f) // `.pvsl`'s cubic-bezier(.7,0,.2,1)
private val FLY = CubicBezierEasing(.45f, 0f, .7f, .2f) // the shards' cubic-bezier(.45,0,.7,.2)

/** tan(12°) and the slashes' turn (`rotate(-12deg)`). */
private const val LEAN = 0.21255656f
private const val TURN = -12f

/** Archivo italic 900 at `wdth` 72, as `.pvname` sets it (Fonts.archivo is the default width). */
private val ARCHIVO_NARROW = FontFamily(
    Font(R.font.archivo_italic, weight = FontWeight.Black, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.width(72f))),
)

/** How far [t] is through a part that starts at [from] and lasts [ms], 0 to 1. */
private fun seg(t: Float, from: Float, ms: Float): Float = ((t - from) / ms).coerceIn(0f, 1f)

/** `.pvart`'s band: `polygon(0 14%, 100% 0, 100% 86%, 0 100%)`. */
private val BAND = GenericShape { s, _ ->
    moveTo(0f, .14f * s.height)
    lineTo(s.width, 0f)
    lineTo(s.width, .86f * s.height)
    lineTo(0f, s.height)
    close()
}

/** Places this at [x], [y] (px, from the screen's corner) at [w] x [h], whatever the room (the slashes run off it). */
private fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier = layout { m, c ->
    val p = m.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)))
    layout(c.maxWidth, c.maxHeight) { p.place(x.roundToInt(), y.roundToInt()) }
}

/** The shake (`pvShake`, 250 ms, linear): (6,-4), (-5,3), (3,2) at its quarters, in dp as the web's px. */
private fun shake(p: Float): Offset {
    if (p <= 0f || p >= 1f) return Offset.Zero
    val xs = floatArrayOf(0f, 6f, -5f, 3f, 0f)
    val ys = floatArrayOf(0f, -4f, 3f, 2f, 0f)
    val i = (p * 4).toInt().coerceAtMost(3)
    val f = p * 4 - i
    return Offset(xs[i] + (xs[i + 1] - xs[i]) * f, ys[i] + (ys[i + 1] - ys[i]) * f)
}

/** One shard: its corners as fractions of the screen, its way out (unit), how far, its turn and how long it flies. */
private class Shard(val pts: List<Offset>, val dir: Offset, val dist: Float, val turn: Float, val ms: Float) {
    val mid = Offset((pts[0].x + pts[1].x + pts[2].x) / 3, (pts[0].y + pts[1].y + pts[2].y) / 3)
}

/** The reveal over everything, with its sounds and buzzes; it opens the game through [open] (no sound of its own). */
@Composable
fun PickReveal(calm: Boolean, open: (String) -> Unit) {
    val r = Reveal.run ?: return
    val opener by rememberUpdatedState(open)
    if (calm) {
        // calm came on before it showed: the page, as the dice does when calm
        LaunchedEffect(r) {
            opener(r.g.id)
            Reveal.done(r)
        }
        return
    }
    key(r.n) {
        val scope = rememberCoroutineScope()
        val t = remember { Animatable(0f) }
        val crack = remember { Animatable(0f) }
        val fly = remember { Animatable(0f) }
        // 0 the live scene, 1 cracked, 2 in shards
        var phase by remember { mutableIntStateOf(0) }
        var opened by remember { mutableStateOf(false) }
        val openBehind = {
            if (!opened) {
                opened = true
                opener(r.g.id)
            }
        }
        // the crack's centre and the 8 points round the edge (the corners stay, the edges' middles wander), then each
        // shard's flight: from the centre outwards, a third to a little more than the screen, turning up to 40° either way
        val shards = remember {
            val rnd = java.util.Random()
            val c = Offset(.48f + rnd.nextFloat() * .08f, .44f + rnd.nextFloat() * .1f)
            val e = listOf(0f to 0f, .5f to 0f, 1f to 0f, 1f to .5f, 1f to 1f, .5f to 1f, 0f to 1f, 0f to .5f).map { (x, y) ->
                Offset(if (x == .5f) .35f + rnd.nextFloat() * .3f else x, if (y == .5f) .3f + rnd.nextFloat() * .4f else y)
            }
            c to e.indices.map { k ->
                val pts = listOf(c, e[k], e[(k + 1) % e.size])
                val mx = (pts[0].x + pts[1].x + pts[2].x) / 3 - c.x
                val my = (pts[0].y + pts[1].y + pts[2].y) / 3 - c.y
                val len = hypot(mx, my).takeIf { it > 0f } ?: 1f
                Shard(pts, Offset(mx / len, my / len), .7f + rnd.nextFloat() * .4f, (rnd.nextFloat() - .5f) * 80f, 650f + rnd.nextFloat() * 150f)
            }
        }
        val shatter = {
            if (phase == 0) {
                phase = 1
                openBehind()
                Sounds.play(Sfx.Crack)
                Buzz.ms(40)
                scope.launch {
                    // the scene stops where it is: the shards are cut from its finished look, as the web draws them
                    t.stop()
                    t.snapTo(BREAK)
                    launch { crack.animateTo(1f, tween(160, easing = EASE_OUT)) }
                    // the crack holds a moment, then the scene is the shards and a white blink hides the swap
                    val hold = Animatable(0f)
                    hold.animateTo(1f, tween(CRACKED, easing = LinearEasing))
                    phase = 2
                    Sounds.play(Sfx.Shatter)
                    fly.animateTo(FLIGHT, tween(FLIGHT.toInt(), easing = LinearEasing))
                    Reveal.done(r)
                }
            }
        }
        val shatterNow by rememberUpdatedState(shatter)
        LaunchedEffect(Unit) {
            Sounds.play(Sfx.Hit)
            Buzz.ms(12, 60, 25)
            // each sound and the page's opening when the scene's clock reaches it (a tap past them skips the rest)
            launch {
                var last = 0f
                snapshotFlow { t.value }.collect { v ->
                    if (phase == 0) {
                        if (last < 110f && v >= 110f) Sounds.play(Sfx.Slash)
                        if (last < 500f && v >= 500f) Sounds.play(Sfx.Tick)
                        if (last < 600f && v >= 600f) Sounds.play(Sfx.Slam)
                        if (last < OPEN && v >= OPEN) openBehind()
                        if (v >= BREAK) shatterNow()
                    }
                    last = v
                }
            }
            t.animateTo(BREAK, tween(BREAK.toInt(), easing = LinearEasing))
        }
        // a reveal that leaves early (calm came on, the screen went) still opens its game
        DisposableEffect(Unit) { onDispose { if (!opened) { opened = true; opener(r.g.id) } } }
        // back breaks it now, as a tap does; it never closes what's under it
        BackHandler { shatterNow() }
        RevealScene(r, t, crack, fly, phase, shards.first, shards.second) { shatterNow() }
    }
}

@Composable
private fun RevealScene(
    r: Reveal.Run,
    t: Animatable<Float, *>,
    crack: Animatable<Float, *>,
    fly: Animatable<Float, *>,
    phase: Int,
    centre: Offset,
    shards: List<Shard>,
    tap: () -> Unit,
) {
    val g = r.g
    val c = r.colour
    val title = g.title
    val scene = rememberGraphicsLayer()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // the whole screen is its to touch: every finger breaks it, and none reaches the tab, its swipes or the rail
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    tap()
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
            }
            .clearAndSetSemantics {
                paneTitle = "Tonight's pick"
                contentDescription = "Tonight's pick: $title"
                onClick("Skip") { tap(); true }
            },
    ) {
        val d = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // the live scene, drawn into [scene] as it goes so the shards are cut from it; in shards it's no longer drawn
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = shake(seg(t.value, 600f, 250f))
                    translationX = k.x * density
                    translationY = k.y * density
                }
                .drawWithContent {
                    if (phase < 2) {
                        scene.record { this@drawWithContent.drawContent() }
                        drawLayer(scene)
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = CSS_EASE.transform(seg(t.value, 0f, 120f)) }.background(Color(Web.RV_BG)))
            // the picture: 112% of the screen from -6%, on a slanted band, darker to the foot; it swings in from 1.35x
            // and -3° (`pvArt`, .9 s from 220 ms)
            Box(
                Modifier
                    .at(-.06f * w, -.06f * h, 1.12f * w, 1.12f * h)
                    .graphicsLayer {
                        val p = Ease.enter.transform(seg(t.value, 220f, 900f))
                        alpha = p
                        scaleX = 1.35f - .35f * p
                        scaleY = 1.35f - .35f * p
                        rotationZ = -3f * (1f - p)
                    }
                    .clip(BAND)
                    .background(c),
            ) {
                PicBox(remember(g) { Pic.backdrop(g) ?: Pic.cover(g) }, g, Modifier.fillMaxSize(), ground = c)
                Box(Modifier.fillMaxSize().background(SHADE))
            }
            // the slashes: the game's colour (26% of the screen high at 38%) and a white one (5% at 58%), 160% wide from
            // -30%, turned -12° and slid in along their own line from -110%, then gone
            Slash(w, h, .38f, .26f, c, t, 100f, 500f, 600f, 350f)
            Slash(w, h, .58f, .05f, Color(Web.RV_SLASH_B), t, 180f, 450f, 650f, 300f)
            // "TONIGHT'S PICK": a white tag leaning 12°, top right, sliding in from 80 (`pvTag`, .5 s from 500 ms)
            BasicText(
                "TONIGHT'S PICK",
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(d) { (.16f * h).toDp() }, end = with(d) { (.06f * w).toDp() })
                    .graphicsLayer {
                        val p = Ease.enter.transform(seg(t.value, 500f, 500f))
                        alpha = p
                        translationX = 80.dp.toPx() * (1f - p)
                    }
                    .drawWithContent {
                        val m = Matrix().apply {
                            values[Matrix.SkewX] = -LEAN
                            values[Matrix.TranslateX] = LEAN * size.height / 2
                        }
                        withTransform({ transform(m) }) { this@drawWithContent.drawContent() }
                    }
                    .background(Color(Web.RV_TAG))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                style = TextStyle(
                    fontFamily = Fonts.archivo, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                    fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = .12.em, color = Color(Web.RV_TAG_INK),
                ).fixed(),
                maxLines = 1, softWrap = false,
            )
            // the bar in the game's colour, 70% wide at 11% from the foot, growing from the left (`pvBar`, .5 s from 700)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = with(d) { (.11f * h).toDp() })
                    .fillMaxWidth(.7f)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, .5f)
                        scaleX = Ease.enter.transform(seg(t.value, 700f, 500f))
                    }
                    .height(6.dp)
                    .background(c),
            )
            // the name: capitals, Archivo italic 900 narrowed, 9% of the screen's width (34 to 84), a hard shadow in the
            // game's colour; it slides in from -60 (`pvName`, .6 s from 580 ms). The web also leans it 14° on the way in:
            // left out, so only the layer moves.
            val fs = with(d) { (w / density * .09f).coerceIn(34f, 84f).dp.toSp() }
            BasicText(
                title.uppercase(Locale.ROOT),
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = with(d) { (.06f * w).toDp() }, end = with(d) { (.06f * w).toDp() }, bottom = with(d) { (.14f * h).toDp() })
                    .graphicsLayer {
                        val p = Ease.enter.transform(seg(t.value, 580f, 600f))
                        alpha = p
                        translationX = -60.dp.toPx() * (1f - p)
                    },
                style = TextStyle(
                    fontFamily = ARCHIVO_NARROW, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                    fontSize = fs, lineHeight = fs * .92f, color = Color(Web.RV_NAME),
                    shadow = Shadow(c, Offset(6f * d.density, 6f * d.density), 0f),
                ),
            )
        }
        // in shards: 8 pieces of the scene as it was, each its own layer flying off from the crack's centre
        if (phase == 2) {
            val far = max(w, h)
            for (s in shards) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = FLY.transform(seg(fly.value, 0f, s.ms))
                            transformOrigin = TransformOrigin(s.mid.x, s.mid.y)
                            translationX = s.dir.x * far * s.dist * p
                            translationY = s.dir.y * far * s.dist * p
                            rotationZ = s.turn * p
                            scaleX = 1f + .1f * p
                            scaleY = 1f + .1f * p
                            alpha = 1f - p
                        }
                        .drawBehind {
                            val path = Path().apply {
                                s.pts.forEachIndexed { i, q -> if (i == 0) moveTo(q.x * size.width, q.y * size.height) else lineTo(q.x * size.width, q.y * size.height) }
                                close()
                            }
                            clipPath(path) { drawLayer(scene) }
                        },
                )
            }
            // the white blink over the swap (.9 → 0 in 180 ms)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = .9f * (1f - EASE_OUT.transform(seg(fly.value, 0f, 180f))) }.background(Color(Web.RV_FLASH)))
        }
        // the crack: 8 white lines from the centre to the edge, fading in (`pvCrack`, .16 s)
        if (phase == 1) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = crack.value }) {
                val o = Offset(centre.x * size.width, centre.y * size.height)
                for (s in shards) drawLine(Color(Web.RV_FLASH), o, Offset(s.pts[1].x * size.width, s.pts[1].y * size.height), 3.dp.toPx())
            }
        }
        // the white flash at the start: full until 35% of .3 s, then out
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = seg(t.value, 0f, 300f)
                alpha = if (p >= 1f) 0f else if (p <= .35f) 1f else 1f - EASE_OUT.transform((p - .35f) / .65f)
            }.background(Color(Web.RV_FLASH)),
        )
    }
}

/** One slash: [top] and [tall] as parts of the screen's height; in at [from] over [ms], gone at [gone] over [goneMs]. */
@Composable
private fun Slash(w: Float, h: Float, top: Float, tall: Float, c: Color, t: Animatable<Float, *>, from: Float, ms: Float, gone: Float, goneMs: Float) {
    Box(
        Modifier
            .at(-.3f * w, top * h, 1.6f * w, tall * h)
            .graphicsLayer {
                val p = SLASH.transform(seg(t.value, from, ms))
                // `rotate(-12deg) translateX(-110%)`: the slide runs along the slash's own line
                val back = -1.1f * size.width * (1f - p)
                val a = Math.toRadians(TURN.toDouble())
                rotationZ = TURN
                translationX = back * cos(a).toFloat()
                translationY = back * sin(a).toFloat()
                alpha = 1f - CSS_EASE.transform(seg(t.value, gone, goneMs))
            }
            .background(c),
    )
}

/** `.pvart::after`: `linear-gradient(0deg, rgba(0,0,0,.85), rgba(0,0,0,.05) 55%)`. */
private val SHADE = Brush.verticalGradient(
    0f to Color(Web.RV_SHADE_MID), .45f to Color(Web.RV_SHADE_MID), 1f to Color(Web.RV_SHADE_FOOT),
)
