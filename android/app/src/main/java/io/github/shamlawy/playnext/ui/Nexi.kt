package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Nexi's seat (Phase 1 piece 3, android/parity/1-map/2-card.md §6, critic X5/X11): his face built once, seated at the
 * rail's foot on every tab unfolded (Home too: Home's own face comes with Phase 3), and floating when the rail is hidden.
 * Folded there's no Nexi on the tabs, as on the web (`#pnbub` is display:none under 620). His menu, personalities,
 * pictures, hats, sparks and chat come with his own phases (8a, 8b).
 */

/** His size (`#pnbub`, 62x62) and, seated, how big his parts are drawn (`html.nxseat #pnbub > * { scale: .72 }`). */
private val NX = 62.dp
private const val SEAT_SCALE = .72f

/** The room the rail keeps at its foot for him (`html.nxseat #hrail { padding-bottom: 84px }`) and his seat above its edge (16). */
internal val SEAT_ROOM = 84.dp
private val SEAT_UP = 16.dp

/** Gliding in and out of the seat (index.html 21133: 560ms) and floating to an edge after a drag (12031: 520ms). */
private val GLIDE = CubicBezierEasing(.34f, 1.3f, .64f, 1f)
private val SNAP = CubicBezierEasing(.34f, 1.45f, .64f, 1f)

/** His pop on leaving the seat (`bubPop .6s`) and his line's (`#pnbubmsg`'s transform .45s). */
private val POP = CubicBezierEasing(.34f, 1.56f, .64f, 1f)

/**
 * Where the rail and floating Nexi are: the rail shown or hidden behind him, on the right or the left, and his parked
 * height. Yours from the backup (`S.railHide`, `S.railSide`, `S.bubPos`) until you move them here; from then on kept on
 * this phone (critic X5), never written back to the backup.
 */
@Stable
class RailSpot(prefs: Prefs) {
    var hidden by mutableStateOf(flag(HIDE) ?: prefs.railHide)
        private set
    var left by mutableStateOf(flag(LEFT) ?: prefs.railLeft)
        private set

    /** Floating Nexi's top in dp from the window's top; null till he's parked once. */
    var y by mutableStateOf(if (Store.ready() && Store.prefs.contains(Y)) Store.prefs.getFloat(Y, 0f) else prefs.bubY)
        private set

    val at: RailAt get() = RailAt(!hidden, left)

    /** One tap on seated Nexi (`railHideNow`): the rail slides away and he floats. */
    fun hide() {
        hidden = true
        keep()
    }

    /** One tap on floating Nexi: the rail comes back on his side (v221, #129). */
    fun show(onLeft: Boolean) {
        left = onLeft
        hidden = false
        keep()
    }

    /** He was dragged and snapped to an edge: the side and height he's parked at (`S.bubPos`, `S.railSide`). */
    fun park(top: Float, onLeft: Boolean) {
        y = top
        left = onLeft
        keep()
    }

    private fun keep() {
        if (!Store.ready()) return
        Store.prefs.edit().putBoolean(HIDE, hidden).putBoolean(LEFT, left).apply { y?.let { putFloat(Y, it) } }.apply()
    }

    private companion object {
        const val HIDE = "nx:railHide"
        const val LEFT = "nx:railLeft"
        const val Y = "nx:y"
        fun flag(k: String): Boolean? = if (Store.ready() && Store.prefs.contains(k)) Store.prefs.getBoolean(k, false) else null
    }
}

/** The rail as the page's layout sees it: shown, and on which side. */
@androidx.compose.runtime.Immutable
data class RailAt(val shown: Boolean = true, val left: Boolean = false)

/** His face for the moment (`PN_MOODS`: the few the seat uses; the rest come with his personalities, Phase 8a). */
enum class Mood { None, Happy, Talk, Dizzy, Grab }

/** A line he's saying, numbered so a newer one always replaces it (`pnSayN`). */
@androidx.compose.runtime.Immutable
class NexiLine(val text: String, val n: Int)

/**
 * Nexi on screen: where he is (his top-left in px, moved only by translation), his eyes' look, his mood, his line and
 * his pop. One per window, made by the Shell.
 */
@Stable
class NexiState {
    val pos = Animatable(Offset.Unspecified, Offset.VectorConverter)
    val eyes = Animatable(Offset.Zero, Offset.VectorConverter)
    val pop = Animatable(1f)
    var mood by mutableStateOf(Mood.None)
    var line by mutableStateOf<NexiLine?>(null)
    var dragging by mutableStateOf(false)
    private var said = 0
    private var moodJob: Job? = null
    private var lineJob: Job? = null

    fun mood(m: Mood, ms: Long, scope: kotlinx.coroutines.CoroutineScope) {
        mood = m
        moodJob?.cancel()
        moodJob = scope.launch {
            delay(ms)
            if (mood == m) mood = Mood.None
        }
    }

    /** `pnBubSay`: shows [text] for 5.5 s, his mouth talking for 1.7 s. */
    fun say(text: String, scope: kotlinx.coroutines.CoroutineScope) {
        val l = NexiLine(text, ++said)
        line = l
        mood(Mood.Talk, 1700, scope)
        lineJob?.cancel()
        lineJob = scope.launch {
            delay(SAY_MS)
            if (line === l) line = null
        }
    }

    /** `pnBubHush`: his line goes (grabbed, or a newer one coming). */
    fun hush() {
        lineJob?.cancel()
        line = null
    }

    companion object {
        /** How long a line stays (`pnSayT`, 5500). */
        const val SAY_MS = 5500L
    }
}

/**
 * Nexi over everything but the slash: seated at the rail's foot ([seated]: the rail shown and the seat on), or floating
 * when the rail is hidden. Seated: one tap (after 400 ms, so a second can follow) hides the rail and he glides out; a
 * double tap or a 600 ms hold is his menu's (Phase 8a: for now he says so). Floating: drag him, a flick carries 120 ms
 * further and he snaps to the nearest side; one tap brings the rail back on his side. At rest in the seat he only
 * blinks (v296); speaking or floating he bobs, breathes and his ring turns. Calm: nothing loops or glides.
 */
@Composable
fun NexiSeat(
    nx: NexiState,
    spot: RailSpot,
    seatOn: Boolean,
    w: Dp,
    h: Dp,
    bottomRoom: Dp,
    nexi: io.github.shamlawy.playnext.data.Nexi?,
    prefs: Prefs,
    calm: Boolean,
    modifier: Modifier = Modifier,
) {
    val floating = spot.hidden
    if (!seatOn && !floating) return
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val guard = LocalSwipeGuard.current
    val look = LocalLook.current
    val clock = LocalNow.current
    val ins = WindowInsets.safeDrawing
    val px = with(d) { NX.toPx() }
    val wPx = with(d) { w.toPx() }
    val hPx = with(d) { h.toPx() }
    val rightIn = ins.getRight(d, LayoutDirection.Ltr).toFloat()
    val leftIn = ins.getLeft(d, LayoutDirection.Ltr).toFloat()
    val topIn = ins.getTop(d).toFloat()
    val bottom = max(with(d) { bottomRoom.toPx() }, ins.getBottom(d).toFloat())
    val edge = with(d) { Web.PN_EDGE.dp.toPx() }
    // the seat (`seatPos`): in the middle of the rail's width, 16 above its foot
    val seat = with(d) {
        val railW = RAIL_W.toPx()
        val railX = if (spot.left) leftIn + 12.dp.toPx() else wPx - rightIn - 12.dp.toPx() - railW
        Offset(railX + (railW - px) / 2, hPx - bottom - 12.dp.toPx() - SEAT_UP.toPx() - px)
    }
    // where he floats (`pnBubEdge`): 34 from the side he's on, between 34 from the top and 90 above the foot
    val floatAt = { x: Float, y: Float ->
        val lo = topIn + edge
        val hi = max(lo, hPx - bottom - px - with(d) { 90.dp.toPx() })
        Offset(if (x + px / 2 < wPx / 2) leftIn + edge else wPx - rightIn - px - edge, min(hi, max(lo, y)))
    }
    val parked = { floatAt(if (spot.left) 0f else wPx, spot.y?.let { with(d) { it.dp.toPx() } } ?: (hPx * .55f)) }
    val target = if (floating) parked() else seat
    val calmNow by rememberUpdatedState(calm)
    // the first place is a snap; a change between seat and floating glides (skipped when calm or under 4 px); a new
    // window size (a fold, a turn) puts him straight where he belongs
    val was = remember { arrayOf<Boolean?>(null) }
    LaunchedEffect(floating, seat, wPx, hPx) {
        val from = nx.pos.value
        val glide = was[0] != null && was[0] != floating && !calmNow && from.isSpecified() && (from - target).getDistance() > 4f
        was[0] = floating
        if (glide) nx.pos.animateTo(target, tween(560, easing = GLIDE)) else nx.pos.snapTo(target)
    }
    // leaving the seat (`pnBubShow`): he pops (`bubPop`), smiles, then says the day's news once a day
    val shownOnce = remember { arrayOf(false) }
    val atStart = remember { floating }
    LaunchedEffect(floating) {
        if (!floating) return@LaunchedEffect
        val first = !shownOnce[0]
        shownOnce[0] = true
        // the rail hidden when the app opens: he shows 300 ms after it's drawn (`pnRailShown`)
        if (first && atStart) delay(300)
        if (!calmNow) launch { nx.pop.snapTo(.2f); nx.pop.animateTo(1f, tween(600, easing = POP)) }
        nx.mood(Mood.Happy, 1200, scope)
        val l = nexi?.let { news(it, prefs, clock()) } ?: return@LaunchedEffect
        delay(1200)
        nx.say(l, scope)
    }
    // asleep 01:00-06:00 (checked every minute, as the web's nap), on the app's clock
    val hour = { java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.systemDefault()).hour }
    var asleep by remember { mutableStateOf(hour() in 1..5) }
    LaunchedEffect(clock) {
        while (true) {
            delay(60_000)
            asleep = hour() in 1..5
        }
    }
    val talking = nx.line != null
    // seated and quiet: only his blink (v296); speaking or floating: he moves as before
    val still = !floating && !talking
    var taps by remember { mutableStateOf(0) }
    val tapJob = remember { arrayOfNulls<Job>(1) }
    val menu = {
        nx.mood(Mood.Happy, 900, scope)
        Crumbs.tap("nexi menu", "rail")
        nx.say(MENU_LATER, scope)
    }
    val tap = {
        tapJob[0]?.cancel()
        taps++
        if (taps >= 2) {
            taps = 0
            menu()
        } else {
            tapJob[0] = scope.launch {
                delay(400)
                if (taps == 1) {
                    nx.hush()
                    if (spot.hidden) {
                        Crumbs.tap("nexi: show the rail", "rail")
                        spot.show(nx.pos.value.x + px / 2 < wPx / 2)
                    } else {
                        Crumbs.tap("nexi: hide the rail", "rail")
                        spot.hide()
                    }
                }
                taps = 0
            }
        }
    }
    val tapNow by rememberUpdatedState(tap)
    val menuNow by rememberUpdatedState(menu)
    val floatingNow by rememberUpdatedState(floating)
    val floatAtNow by rememberUpdatedState(floatAt)
    Box(
        modifier
            .layout { m, c ->
                val p = m.measure(Constraints.fixed(px.toInt(), px.toInt()))
                layout(c.maxWidth, c.maxHeight) {
                    // placed with a layer, so moving him never lays anything out
                    p.placeWithLayer(0, 0) {
                        val at = nx.pos.value
                        if (at.isSpecified()) {
                            translationX = at.x
                            translationY = at.y
                        }
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = if (floatingNow) "Nexi: show the rail" else "Nexi: hide the rail"
                onClick { tapNow(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // his touches are his own: never a swipe between tabs or views
                    guard.inScroller = true
                    nx.hush()
                    nx.mood = Mood.Grab
                    nx.dragging = true
                    val slop = 14.dp.toPx()
                    val start = nx.pos.value
                    var drag = Offset.Zero
                    var moved = false
                    var held = false
                    var vel = Offset.Zero
                    var lastT = down.uptimeMillis
                    // the hold counts from the touch, not from the last small move
                    val holdAt = down.uptimeMillis + 600
                    var seen = down.uptimeMillis
                    while (true) {
                        val e = if (!moved && !held) {
                            val wait = holdAt - seen
                            if (wait <= 0) null else withTimeoutOrNull(wait) { awaitPointerEvent() }
                        } else awaitPointerEvent()
                        if (e == null) {
                            held = true
                            menuNow()
                            continue
                        }
                        val c = e.changes.firstOrNull { it.id == down.id } ?: e.changes.first()
                        seen = c.uptimeMillis
                        if (!c.pressed) break
                        val dd = c.positionChange()
                        drag += dd
                        if (!moved && drag.getDistance() > slop) moved = true
                        if (moved && floatingNow && !held) {
                            c.consume()
                            val dt = max(1L, c.uptimeMillis - lastT)
                            vel = Offset(dd.x / dt, dd.y / dt)
                            lastT = c.uptimeMillis
                            scope.launch { nx.pos.snapTo(start + drag) }
                        }
                    }
                    nx.dragging = false
                    if (nx.mood == Mood.Grab) nx.mood = Mood.None
                    if (held) return@awaitEachGesture
                    if (!moved) {
                        tapNow()
                        return@awaitEachGesture
                    }
                    if (!floatingNow) return@awaitEachGesture
                    // a flick carries 120 ms further, then he snaps to the nearest side
                    val flung = start + drag + vel * 120f
                    val to = floatAtNow(flung.x, flung.y)
                    val fast = vel.getDistance() / density > 1.2f
                    nx.mood(if (fast) Mood.Dizzy else Mood.Happy, if (fast) 1600 else 900, scope)
                    spot.park(to.y / density, to.x + px / 2 < wPx / 2)
                    scope.launch {
                        if (calmNow) nx.pos.snapTo(to) else {
                            nx.pos.snapTo(flung)
                            nx.pos.animateTo(to, tween(520, easing = SNAP))
                        }
                    }
                }
            },
    ) {
        Face(nx, seated = !floating, still = still, calm = calm, asleep = asleep, stage = nexi?.stage ?: 1, party = nexi?.party == true,
            badge = if (floating) nexi?.badge(prefs.nxBadge).orEmpty() else "", volt = look.volt, volt2 = look.volt2)
    }
    val l = nx.line
    if (l != null) Saying(nx, l, px, wPx, calm)
}

/** What he says leaving his seat (`pnBubShow`): a release day's line, else the day's brief, each once a day on this phone. */
private fun news(n: io.github.shamlawy.playnext.data.Nexi, prefs: Prefs, now: Long): String? {
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    var line: String? = null
    val store = Store.ready()
    fun said(k: String) = store && Store.prefs.getString(k, "") == today
    fun mark(k: String) { if (store) Store.prefs.edit().putString(k, today).apply() }
    if (!said("nx:day")) {
        mark("nx:day")
        line = n.brief(prefs.userName)
    }
    val p = n.partyLine
    if (p != null && !said("nx:party")) {
        mark("nx:party")
        line = p
    }
    return line
}

/** What a double tap or a hold says till his menu comes (Phase 8a). */
internal const val MENU_LATER = "My menu comes in a later update"

private fun Offset.isSpecified() = this != Offset.Unspecified && !x.isNaN()

/**
 * His face (`pnBubble`, index.html 12003; css 2302-2425): the breath glow, his ring, the round core with his face (the
 * ground, two cheeks, two eyes that blink and look, a mouth) and his badge.
 */
@Composable
private fun Face(
    nx: NexiState,
    seated: Boolean,
    still: Boolean,
    calm: Boolean,
    asleep: Boolean,
    stage: Int,
    party: Boolean,
    badge: String,
    volt: Color,
    volt2: Color,
) {
    val moving = !still && !calm
    val loops = rememberInfiniteTransition("nexi")
    // pnBreath 4.5s: opacity .45 <-> 1, scale .94 <-> 1.04
    val breath by if (moving) loops.animateFloat(0f, 1f, infiniteRepeatable(tween(2250, easing = FastOutSlowInEasing), RepeatMode.Reverse), "breath") else remember { mutableStateOf(1f) }
    // blob 6s (1.4s on a release day): the ring turns
    val spin by if (moving) loops.animateFloat(0f, 360f, infiniteRepeatable(tween(if (party) 1400 else 6000, easing = LinearEasing)), "spin") else remember { mutableStateOf(0f) }
    // bubBob 3.4s: up 5 and -3° at the middle
    val bob by if (moving && nx.mood != Mood.Grab) loops.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "bob") else remember { mutableStateOf(0f) }
    val scale = if (seated) SEAT_SCALE else 1f
    val glowIn = if (seated) 4.dp else 20.dp
    val auraIn = if (seated) 7.dp else (12 + 2 * stage).dp
    val aura = if (party) listOf(Color(Web.NX_PARTY_0), Color(Web.NX_PARTY_1), Color(Web.NX_PARTY_2), Color(Web.NX_PARTY_3), Color(Web.NX_PARTY_0))
    else listOf(volt2, Color(Web.NX_AURA_A), Color(Web.NX_AURA_B), Color(Web.NX_AURA_G2), volt2)
    // CSS's conic starts at 12 o'clock (`from 200deg`; the party's from 0), Compose's sweep at 3
    val auraFrom = if (party) -90f else 110f
    Box(Modifier.size(NX), contentAlignment = Alignment.Center) {
        // ::before: the breath glow in your accent (not one of his scaled parts)
        Box(
            Modifier
                .requiredSize(NX + glowIn * 2)
                .graphicsLayer {
                    if (moving) {
                        alpha = .45f + .55f * breath
                        scaleX = .94f + .1f * breath
                        scaleY = scaleX
                    }
                }
                .drawBehind { drawCircle(Brush.radialGradient(0f to volt.copy(alpha = .4f), .66f to Color.Transparent, radius = size.minDimension / 2)) },
        )
        // his ring: a turning disc behind the core with a soft edge
        Box(
            Modifier
                .requiredSize(NX + auraIn * 2)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = auraFrom + spin
                }
                .drawBehind {
                    val r = size.minDimension / 2
                    drawCircle(Brush.radialGradient(.86f to Color(Web.NX_AURA_EDGE), 1f to Color.Transparent, radius = r + 8.dp.toPx()), radius = r + 8.dp.toPx())
                    drawCircle(Brush.sweepGradient(aura), radius = r)
                },
        )
        // the core: --void rim 3, his shadow, the bob and the pop
        Box(
            Modifier
                .size(NX)
                .graphicsLayer {
                    val g = if (nx.mood == Mood.Grab) .9f else 1f
                    scaleX = scale * nx.pop.value * g
                    scaleY = scaleX
                    translationY = -5.dp.toPx() * bob * scale
                    rotationZ = -3f * bob
                }
                .shadow(14.dp, CircleShape, ambientColor = Color(Web.NX_SHADE), spotColor = Color(Web.NX_SHADE))
                .background(Pn.void, CircleShape)
                .padding(3.dp)
                .drawBehind { faceGround() },
        ) {
            Eyes(nx, still = calm, asleep = asleep, moving = !calm)
            Mouth(nx.mood, asleep, calm)
            if (asleep) Zz(calm)
        }
        if (badge.isNotEmpty()) Badge(badge, scale)
    }
}

/** The face's ground (`radial-gradient(circle at 34% 28%, #1c2c52, #070a14 72%)`) and its cheeks (9x5 at 58%, 14% in). */
private fun DrawScope.faceGround() {
    val c = Offset(size.width * .34f, size.height * .28f)
    val far = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y))
    drawCircle(Brush.radialGradient(0f to Color(Web.NX_FACE), .72f to Color(Web.NX_FACE_FAR), center = c, radius = far))
    val cw = 9.dp.toPx()
    val ch = 5.dp.toPx()
    val top = size.height * .58f
    val cheek = Color(Web.NX_CHEEK)
    drawOval(cheek, Offset(size.width * .14f, top), Size(cw, ch))
    drawOval(cheek, Offset(size.width * .86f - cw, top), Size(cw, ch))
}

/** His eyes (11x15 at 32% from the top, 27% in), glowing, blinking 4.6 s (the right one .04 s later), looking his way. */
@Composable
private fun BoxScope.Eyes(nx: NexiState, still: Boolean, asleep: Boolean, moving: Boolean) {
    val loops = rememberInfiniteTransition("eyes")
    val blinkSpec = { lag: Int ->
        InfiniteRepeatableSpec<Float>(
            keyframes {
                durationMillis = 4600
                1f at 0
                1f at 4278
                .1f at 4416
                1f at 4600
            },
            RepeatMode.Restart, StartOffset(lag, StartOffsetType.Delay),
        )
    }
    val blinks = !still && !asleep && nx.mood != Mood.Happy && nx.mood != Mood.Dizzy
    val bl by if (blinks) loops.animateFloat(1f, 1f, blinkSpec(0), "l") else remember { mutableStateOf(1f) }
    val br by if (blinks) loops.animateFloat(1f, 1f, blinkSpec(40), "r") else remember { mutableStateOf(1f) }
    val spin by if (nx.mood == Mood.Dizzy && moving) loops.animateFloat(0f, 360f, infiniteRepeatable(tween(450, easing = LinearEasing)), "dz") else remember { mutableStateOf(0f) }
    val mood = nx.mood
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                val e = nx.eyes.value
                translationX = e.x
                translationY = e.y
            },
    ) {
        for (right in listOf(false, true)) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleY = if (right) br else bl
                        // each eye blinks round its own middle
                        transformOrigin = TransformOrigin(if (right) .73f - 5.5f / 56 else .27f + 5.5f / 56, .32f + 7.5f / 56)
                    }
                    .drawBehind { eye(right, mood, asleep, spin) },
            )
        }
    }
}

/** One eye in the face's 56 box: the plain oval with its glow, or its look in a mood. */
private fun DrawScope.eye(right: Boolean, mood: Mood, asleep: Boolean, spin: Float) {
    val u = size.width / 56f
    val ink = Color(Web.NX_EYE)
    val glow = Color(Web.NX_GLOW)
    fun at(w: Float) = if (right) size.width * .73f - w * u else size.width * .27f
    when {
        asleep -> {
            // `.sleep .eye`: 3 high at 42%, no glow
            drawRoundRect(ink, Offset(at(11f), size.height * .42f), Size(11 * u, 3 * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
        }
        mood == Mood.Happy -> {
            // `.happy .eye`: an arch 8 high, a 3 line along its top
            val x = at(11f)
            val top = size.height * .32f
            drawArc(ink, 180f, 180f, false, Offset(x + 1.5f * u, top + 1.5f * u), Size(8 * u, 10 * u), style = Stroke(3 * u, cap = StrokeCap.Round))
        }
        mood == Mood.Dizzy -> {
            // `.dizzy .eye`: a 12 ring, open on one side, spinning
            val x = at(12f)
            val top = size.height * .32f
            rotate(spin, Offset(x + 6 * u, top + 6 * u)) {
                drawArc(ink, 0f, 270f, false, Offset(x + 1.25f * u, top + 1.25f * u), Size(9.5f * u, 9.5f * u), style = Stroke(2.5f * u))
            }
        }
        else -> {
            val x = at(11f)
            val top = size.height * .32f
            val s = if (mood == Mood.Grab) 1.3f else 1f
            val ew = 11 * u * s
            val eh = 15 * u * s
            val ox = x + 5.5f * u - ew / 2
            val oy = top + 7.5f * u - eh / 2
            // `box-shadow: 0 0 8px #8FD0FF`: a blur of 8 fades out about 4 past the eye's edge
            val rim = 4.5f * u
            val r = eh / 2 + rim
            drawOval(
                Brush.radialGradient((eh / 2 - rim) / r to glow.copy(alpha = .55f), (eh / 2) / r to glow.copy(alpha = .3f), 1f to Color.Transparent, center = Offset(ox + ew / 2, oy + eh / 2), radius = r),
                Offset(ox - rim, oy - rim), Size(ew + rim * 2, eh + rim * 2),
            )
            drawOval(ink, Offset(ox, oy), Size(ew, eh))
        }
    }
}

/** His mouth (14x7 at 62%: a smile line 2.5 thick), and its look in a mood. */
@Composable
private fun BoxScope.Mouth(mood: Mood, asleep: Boolean, calm: Boolean) {
    val talk by if (mood == Mood.Talk && !calm) rememberInfiniteTransition("talk").animateFloat(.4f, 1.3f, infiniteRepeatable(tween(160, easing = FastOutSlowInEasing), RepeatMode.Reverse), "t") else remember { mutableStateOf(1f) }
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                if (mood == Mood.Talk) {
                    scaleY = talk
                    transformOrigin = TransformOrigin(.5f, .62f + 4f / 56)
                }
            }
            .drawBehind {
                val u = size.width / 56f
                val ink = Color(Web.NX_MOUTH)
                val cx = size.width / 2
                val top = size.height * .62f
                when {
                    mood == Mood.Talk -> drawOval(ink, Offset(cx - 5.5f * u, top), Size(11 * u, 8 * u))
                    mood == Mood.Happy -> drawArc(ink, 0f, 180f, true, Offset(cx - 8 * u, top - 8 * u), Size(16 * u, 16 * u))
                    mood == Mood.Dizzy -> rotate(-12f, Offset(cx, top + 2 * u)) {
                        drawRoundRect(ink, Offset(cx - 6 * u, top), Size(12 * u, 4 * u), androidx.compose.ui.geometry.CornerRadius(2 * u))
                    }
                    mood == Mood.Grab -> drawCircle(ink, 3.25f * u, Offset(cx, top + 4.5f * u), style = Stroke(2.5f * u))
                    else -> {
                        val half = (if (asleep) 4f else 7f) * u
                        drawArc(ink, 0f, 180f, false, Offset(cx - half + 1.25f * u, top - 7 * u + 1.25f * u), Size(half * 2 - 2.5f * u, 14 * u - 2.5f * u), style = Stroke(2.5f * u, cap = StrokeCap.Round))
                    }
                }
            },
    )
}

/** Asleep, a z rises from his head (`pnZz 2.4s`). */
@Composable
private fun BoxScope.Zz(calm: Boolean) {
    val t by if (!calm) rememberInfiniteTransition("zz").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = CubicBezierEasing(.42f, 0f, 1f, 1f))), "z") else remember { mutableStateOf(.4f) }
    BasicText(
        "z",
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = 2.dp, end = 8.dp)
            .graphicsLayer {
                alpha = if (t < .4f) t / .4f else 1f - (t - .4f) / .6f
                translationX = 6.dp.toPx() * t
                translationY = (6 - 14 * t).dp.toPx()
                scaleX = .6f + .6f * t
                scaleY = scaleX
            },
        style = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 11.sp, color = Color(Web.NX_ZZ)),
    )
}

/** His badge (`.badge`): the countdown or the queue's hours, on `--volt2` under his chin. */
@Composable
private fun BoxScope.Badge(text: String, scale: Float) {
    val shape = RoundedCornerShape(9.dp)
    BasicText(
        text,
        Modifier
            .align(Alignment.BottomCenter)
            .offset(y = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(4.dp, shape, ambientColor = Color(Web.NX_BADGE_SHADE), spotColor = Color(Web.NX_BADGE_SHADE))
            .background(LocalLook.current.volt2, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, lineHeight = 13.sp, color = Color(Web.NX_BADGE_INK)),
        maxLines = 1, softWrap = false,
    )
}

/**
 * His line (`#pnbubmsg`, `pnBubSay`): on `--ice` in `--void` 700 13/1.3, at most 240 wide, 8 below his top, on the
 * side away from his edge (12 left of him, or 74 from his left); it pops from .6 and fades in. A tap on it hides it.
 */
@Composable
private fun Saying(nx: NexiState, line: NexiLine, px: Float, wPx: Float, calm: Boolean) {
    val d = LocalDensity.current
    val pop = remember(line) { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(line) { if (!calm) pop.animateTo(1f, tween(450, easing = POP)) }
    val fade = remember(line) { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(line) { if (!calm) fade.animateTo(1f, tween(300)) }
    val gap = with(d) { 12.dp.toPx() }
    val far = with(d) { 74.dp.toPx() }
    val down = with(d) { 8.dp.toPx() }
    val shape = RoundedCornerShape(16.dp)
    // read through derivedStateOf: his line moves with him, but only a change of side redraws it
    val right by remember(wPx) { androidx.compose.runtime.derivedStateOf { nx.pos.value.let { it.isSpecified() && it.x > wPx / 2 } } }
    // his eyes turn 4 toward it while he talks
    LaunchedEffect(line, right) {
        nx.eyes.animateTo(with(d) { Offset((if (right) -4 else 4).dp.toPx(), 1.dp.toPx()) }, tween(180))
    }
    BasicText(
        line.text,
        Modifier
            .layout { m, c ->
                val p = m.measure(Constraints(maxWidth = min(c.maxWidth, with(d) { 240.dp.roundToPx() })))
                layout(c.maxWidth, c.maxHeight) {
                    p.placeWithLayer(0, 0) {
                        val at = nx.pos.value
                        val x = if (right) at.x - p.width - gap else at.x + far
                        translationX = x
                        translationY = at.y + down
                        val s = .6f + .4f * pop.value
                        scaleX = s
                        scaleY = s
                        alpha = fade.value
                        transformOrigin = TransformOrigin(if (right) 1f else 0f, .5f)
                    }
                }
            }
            .shadow(18.dp, shape, ambientColor = Color(Web.NX_SAY_SHADE), spotColor = Color(Web.NX_SAY_SHADE))
            .background(Pn.ice, shape)
            .pointerInput(line) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val up = waitForUpOrCancellation(PointerEventPass.Main)
                    if (up != null) nx.hush()
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(max = 212.dp),
        style = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp, color = Pn.void),
    )
}

/** Where a finger is, for floating Nexi's eyes (up to 5 toward it, 180 ms ease-out, `look`). */
internal fun NexiState.lookAt(finger: Offset, px: Float, maxPx: Float, scope: kotlinx.coroutines.CoroutineScope) {
    val at = pos.value
    if (!at.isSpecified() || dragging) return
    val dx = finger.x - at.x - px / 2
    val dy = finger.y - at.y - px / 2
    val dist = max(1f, hypot(dx, dy))
    val k = min(1f, dist / (maxPx * 20)) * maxPx
    scope.launch { eyes.animateTo(Offset(dx / dist * k, dy / dist * k), tween(180, easing = CubicBezierEasing(0f, 0f, .58f, 1f))) }
}
