package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.gen.Web
import kotlin.math.max
import kotlin.math.roundToInt

/** A cover that was tapped to open its game: where it was on the screen (window px) and which game it showed. */
class FlyFrom(val rect: Rect, val g: Game)

/**
 * The box this sits in on the screen as drawn (turned, dealt), like the web's getBoundingClientRect, and not cut to the
 * card it hangs out of: where a flight starts.
 */
fun LayoutCoordinates.boxInWindow(): Rect {
    val root = findRootCoordinates()
    val r = root.localBoundingBoxOf(this, clipBounds = false)
    return Rect(root.localToWindow(r.topLeft), root.localToWindow(r.bottomRight))
}

/**
 * Opens a game's page; [from] is the cover that was tapped (null when nothing with a cover was: the page only grows).
 * The shell provides it, so a cover anywhere (the header cards now, the Queue and Library with their phases) can.
 */
val LocalOpenGame = staticCompositionLocalOf<(String, FlyFrom?) -> Unit> { { _, _ -> } }

/** The page's close (`cubic-bezier(.4,0,1,1)`) and the cover's flight (`cubic-bezier(.3,.9,.35,1)`). */
private val SHUT = CubicBezierEasing(.4f, 0f, 1f, 1f)
private val FLY = CubicBezierEasing(.3f, .9f, .35f, 1f)

/**
 * A game's page, for now a placeholder (the page itself is phase 2): its picture, its cover, name and state. It grows
 * out of where you tapped (scale .93 and a third seen → whole, 300ms, index.html 11821) and shrinks back when closed
 * (→ .9 and gone, 220ms); the cover you tapped flies onto the page's own (v225: lifts off the spot, lands tilted -6°
 * and settles at -4°, 380ms) while the page's cover waits hidden. Transform and opacity only. Calm, or "Motion on
 * this page" off (`S.hubAnim`), it simply opens and closes.
 */
@Composable
fun GamePage(g: Game, tap: Offset, from: FlyFrom?, wide: Boolean, shrinking: () -> Unit = {}, covers: (Boolean) -> Unit = {}, close: () -> Unit) {
    val calm = LocalCalm.current
    val motion = !calm && LocalPrefs.current.hubAnim
    // a page you come back to (back from one opened on top of it) is already open: no second grow or flight
    var opened by rememberSaveable { mutableStateOf(false) }
    val grow = remember { Animatable(if (calm || opened) 1f else 0f) }
    val shut = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val end by rememberUpdatedState(close)
    LaunchedEffect(Unit) {
        opened = true
        grow.animateTo(1f, tween(300, easing = Ease.house))
        // grown: the page hides all that's under it, so that stops drawing (Android draws covered layers every frame);
        // not when back came during the grow (the page is already shrinking off what's under it)
        if (!closing) covers(true)
    }
    DisposableEffect(Unit) { onDispose { covers(false) } }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        covers(false)
        shrinking()
        if (!calm) shut.animateTo(1f, tween(220, easing = SHUT))
        end()
    }
    // back closes the page and shows what you opened it from (the owner, Oct 28); a second back while it shrinks waits
    BackHandler { closing = true }

    // [outer, the page inside its grow, the page's cover]: the flight is measured from these once the page is laid out
    val at = remember { arrayOfNulls<LayoutCoordinates>(3) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var landed by remember { mutableStateOf(from == null || !motion || opened) }
    LaunchedEffect(Unit) {
        if (landed || from == null) return@LaunchedEffect
        withFrameNanos { }
        val (outer, page, cov) = at
        if (outer == null || page == null || cov == null || !cov.isAttached || !page.isAttached) { landed = true; return@LaunchedEffect }
        // the cover's resting box (inside the grow, so the grow doesn't count) and where the tapped one sits, both in outer
        val rest = Rect(page.localPositionOf(cov, Offset.Zero), cov.size.toSize())
        val src = Rect(outer.windowToLocal(from.rect.topLeft), outer.windowToLocal(from.rect.bottomRight))
        if (rest.width <= 0f || rest.top > outer.size.height || rest.bottom < 0f) { landed = true; return@LaunchedEffect }
        val f = Flight(rest, src)
        flight = f
        f.p.animateTo(1f, tween(380, easing = FLY))
        landed = true
        flight = null
    }

    // the whole screen is the page's to touch, also round its edges while it grows or shrinks: a tap there never reaches
    // what's under it (the rail's Settings opened under a closing page, which then stayed open, unseen). It only
    // listens: being touchable is enough to keep the touch from what's under it, and consuming here would cancel the
    // page's own buttons (a tap checks, after its parents, that nothing consumed it)
    Box(Modifier.fillMaxSize().onGloballyPositioned { at[0] = it }.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val gv = grow.value
                    val sv = shut.value
                    val s = (.93f + .07f * gv) * (1f - .1f * sv)
                    scaleX = s
                    scaleY = s
                    alpha = ((.35f + .65f * gv) * (1f - sv)).coerceIn(0f, 1f)
                    if (tap.isSpecified && size.width > 0f && size.height > 0f) transformOrigin = TransformOrigin(tap.x / size.width, tap.y / size.height)
                }
                .onGloballyPositioned { at[1] = it }
                .background(Pn.void)
                .ownTaps()
                .semantics { paneTitle = "Game page" },
        ) {
            val heroH = (maxHeight * if (wide) .58f else .52f).coerceIn(270.dp, if (wide) 560.dp else 460.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(heroH).clipToBounds()) {
                    PicBox(Pic.backdrop(g) ?: Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.SHX_COVER))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Pn.void.copy(alpha = .35f), 1f to Pn.void)))
                }
                Column(
                    Modifier.widthIn(max = 880.dp).fillMaxWidth().offset(y = (-56).dp)
                        // unfolded, the rail stays over the page: its words keep clear of it (`#full .gd-body`, 104)
                        .padding(start = if (wide) 28.dp else 20.dp, end = if (wide) 104.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val shape = RoundedCornerShape(if (wide) 18.dp else 16.dp)
                        Box(
                            Modifier
                                .width(if (wide) 118.dp else 84.dp)
                                .aspectRatio(3f / 4f)
                                .onGloballyPositioned { at[2] = it }
                                .graphicsLayer { rotationZ = -4f; alpha = if (landed) 1f else 0f }
                                .shadow(18.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
                                .clip(shape)
                                .border(2.dp, Pn.white.copy(alpha = .9f), shape),
                        ) { PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.SHX_COVER)) }
                        Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val label = Web.STATES.firstOrNull { it.first == g.state }?.second ?: g.state
                            BasicText(
                                label,
                                style = Type.kicker.copy(color = Pn.void),
                                modifier = Modifier.background(LocalLook.current.volt, Pn.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            BasicText(g.title, style = Type.title(wide), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Card {
                        BasicText("The game page comes in phase 2", style = Type.head)
                        BasicText("Pictures, prices, reviews, hours to beat, news, your notes, Where was I, Similar and Edit, all from the web app.", style = Type.note)
                    }
                }
            }
            // Back, top right (`#full .hub .fullx`, index.html 16523): 16 in, 100 unfolded where the rail sits
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp, end = if (wide) 92.dp else 8.dp)
                    .sizeIn(minWidth = Pn.touch, minHeight = Pn.touch)
                    .clickable(role = Role.Button) { closing = true }
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "Back",
                    style = Type.button.copy(fontSize = 14.sp, color = Pn.ice),
                    modifier = Modifier
                        .background(Pn.pan.copy(alpha = .82f), Pn.pill)
                        .border(1.dp, Pn.line2, Pn.pill)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        // the cover in flight goes with the page when it's closed mid-flight (it was left opaque over the fading page,
        // then cut off mid-air)
        flight?.let { f -> FlyingCover(f, g) { 1f - shut.value } }
    }
}

/** One flight: the page cover's resting box and the tapped cover's box, in the page's untransformed space (px). */
private class Flight(val rest: Rect, val src: Rect) {
    val p = Animatable(0f)
}

/** The copy that flies: placed at the resting box, moved there from the tapped cover by its layer alone. */
@Composable
private fun FlyingCover(f: Flight, g: Game, seen: () -> Float) {
    val d = LocalDensity.current
    val shape = RoundedCornerShape(16.dp)
    val r = f.rest
    val sc = max(f.src.width / r.width, f.src.height / r.height)
    val fx = f.src.center.x - r.center.x
    val fy = f.src.center.y - r.center.y
    Box(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
            .graphicsLayer {
                // the web's keyframes on one eased clock: lift off (translate + the tapped size) → 72%: on the spot, 1.06
                // and -6° → settle at 1 and -4°
                val p = f.p.value
                alpha = seen()
                if (p <= .72f) {
                    val k = p / .72f
                    translationX = fx * (1f - k)
                    translationY = fy * (1f - k)
                    val s = sc + (1.06f - sc) * k
                    scaleX = s
                    scaleY = s
                    rotationZ = -6f * k
                } else {
                    val k = (p - .72f) / .28f
                    val s = 1.06f - .06f * k
                    scaleX = s
                    scaleY = s
                    rotationZ = -6f + 2f * k
                }
            }
            .shadow(18.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
            .clip(shape)
            .border(2.dp, Pn.white.copy(alpha = .9f), shape),
    ) { PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.SHX_COVER)) }
}
