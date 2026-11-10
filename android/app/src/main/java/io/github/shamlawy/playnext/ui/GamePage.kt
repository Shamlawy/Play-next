package io.github.shamlawy.playnext.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Page
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Plat
import io.github.shamlawy.playnext.data.Practice
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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

/** Your progress drawing itself (v271): the line's `cubic-bezier(.65,0,.35,1)`, a new dot's pop `cubic-bezier(.34,1.4,.64,1)`. */
private val TRACK = CubicBezierEasing(.65f, 0f, .35f, 1f)
private val POP = CubicBezierEasing(.34f, 1.4f, .64f, 1f)

/** CSS `ease-out`. */
private val EASE_OUT = CubicBezierEasing(0f, 0f, .58f, 1f)

/**
 * What a game's page reads (made once per copy of your games): the page's words ([page]) and the swipe's order
 * ([order]: every game in the Queue's order, `sortGames(allGames())`).
 */
@Immutable
class PageView(val page: Page, val order: List<Game>, val lib: Library) {
    /** The games a practice change touched ("Practice copy: changes reset when the app reopens" on their page). */
    val changed: Set<String> = Practice.changed(lib)
}

/**
 * The page's words and order for [ready]'s games: the Queue its day build made off Main when it's for this copy (a
 * fresh one otherwise), and the swipe in the Queue's order, your practice drags included.
 */
@Composable
fun rememberPageView(ready: Games.State.Ready, pick: QueuePick): PageView {
    val lib = ready.lib
    val now = LocalNow.current()
    val zone = java.time.ZoneId.systemDefault()
    val made = ready.cards?.queue?.takeIf { it.lib === lib && it.covers(now, zone) }
    val day = io.github.shamlawy.playnext.data.JsDate.todayLocal(now, zone)
    val q = remember(lib, made, day) { made ?: io.github.shamlawy.playnext.data.Queue(lib, Rank(lib, now, zone), now, zone) }
    val sort = pick.sort
    val all = pick.moved
    val moved = if (pick.movedFor === lib.from) all else emptyMap()
    return remember(q, sort, moved) { PageView(Page(q, now, zone), Page.order(q, sort, moved), lib) }
}

/**
 * The swipe's long run of pages: each game [REPEAT] times over, so a swipe past the last game comes round to the first
 * (the web's order wraps) without the pager ever reaching an end.
 */
private const val REPEAT = 400

/**
 * A game's page in its New style (Phase 2 piece 1; android/parity/2-map/1-page.md §1-4, 9): the hero with its backdrop
 * and logo, the cover and name, the tag row, the four tiles, the one smart button, Your progress, About and the
 * screenshots, with the bar that sticks once the hero has scrolled away, and a swipe sideways to the next game in the
 * Queue's order. It grows out of where you tapped (scale .93 and a third seen → whole, 300ms, index.html 11821) and
 * shrinks back when closed (→ .9 and gone, 220ms, also following the back gesture); the cover you tapped flies onto the
 * page's own (v225: lifts off the spot, lands tilted -6° and settles at -4°, 380ms) while the page's cover waits hidden.
 * Transform and opacity only. Calm, or "Motion on this page" off (`S.hubAnim`), nothing on the page moves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamePage(
    g: Game,
    view: PageView,
    tap: Offset,
    from: FlyFrom?,
    wide: Boolean,
    shrinking: () -> Unit = {},
    covers: (Boolean) -> Unit = {},
    toQueue: (String) -> Unit = {},
    close: () -> Unit,
) {
    val calm = LocalCalm.current
    val prefs = LocalPrefs.current
    // "Motion on this page" off is calm for the page alone (3-critic.md X4: the switch says it freezes everything there)
    val still = calm || !prefs.hubAnim
    // a page you come back to (back from one opened on top of it) is already open: no second grow or flight
    var opened by rememberSaveable { mutableStateOf(false) }
    val grow = remember { Animatable(if (still || opened) 1f else 0f) }
    val shut = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // where closing takes you: back to what was under the page, or (a genre's tap) the Queue filtered to it
    var after by remember { mutableStateOf<String?>(null) }
    val end by rememberUpdatedState(close)
    val queue by rememberUpdatedState(toQueue)
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
        if (!still) shut.animateTo(1f, tween(220, easing = SHUT))
        val to = after
        if (to != null) queue(to) else end()
    }
    // back closes the page and shows what you opened it from (the owner, Oct 28); the back gesture shrinks it with the
    // finger (3-critic.md M9), a gesture let go of springs back, and a second back while it shrinks waits
    PredictiveBackHandler(enabled = !closing) { events ->
        try {
            events.collect { e -> if (!still) shut.snapTo(e.progress * .35f) }
            closing = true
        } catch (c: CancellationException) {
            // the handler's job is already cancelled here, so the spring back runs outside it (else it never moves)
            if (!still) withContext(NonCancellable) { shut.animateTo(0f, tween(180, easing = Ease.house)) }
            throw c
        }
    }
    BackHandler(enabled = closing) { }

    // the pager over every game in the Queue's order; the one showing ([cur]) is kept through a fold or unfold
    val order = view.order.ifEmpty { listOf(g) }
    var cur by rememberSaveable { mutableStateOf(g.id) }
    val n = order.size
    val idx = order.indexOfFirst { it.id == cur }.coerceAtLeast(0)
    val loop = n >= 2
    val pager = rememberPagerState(initialPage = if (loop) n * (REPEAT / 2) + idx else 0) { if (loop) n * REPEAT else 1 }
    // a practice change can move games about in the order: the page showing stays the one you were on
    LaunchedEffect(order) {
        if (!loop) return@LaunchedEffect
        val at = order.getOrNull(pager.currentPage % n)
        if (at?.id != cur && !pager.isScrollInProgress) pager.scrollToPage(pager.currentPage - pager.currentPage % n + idx)
    }
    // a committed swipe: the new game, its sound and a short buzz (the web's beep("swipe") and vibrate(9))
    LaunchedEffect(pager, order) {
        snapshotFlow { pager.settledPage }.drop(1).collect { p ->
            val ng = order.getOrNull(if (loop) p % n else 0) ?: return@collect
            if (ng.id != cur) {
                cur = ng.id
                Sounds.play(Sfx.Swipe)
                Buzz.swipeTick()
                Crumbs.add("swipe game page")
            }
        }
    }

    // [outer, the page inside its grow, the page's cover]: the flight is measured from these once the page is laid out
    val at = remember { arrayOfNulls<LayoutCoordinates>(3) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var landed by remember { mutableStateOf(from == null || still || opened) }
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
    // the one line a button leaves ("… is now Playing"), at the foot for a moment
    var toast by remember { mutableStateOf<PageToast?>(null) }
    // the screenshots full screen, over the page (it holds the swipe while it's open)
    var viewer by remember { mutableStateOf<Pair<List<Pair<String, String>>, Int>?>(null) }
    LaunchedEffect(toast) { val t = toast ?: return@LaunchedEffect; delay(2_400); if (toast === t) toast = null }
    val acts = remember {
        PageActs(
            leave = { t -> if (!closing) { after = t; closing = true } },
            say = { s -> toast = PageToast(s, (toast?.n ?: 0) + 1) },
            shots = { l, i -> viewer = l to i },
        )
    }
    val firstPage = if (loop) n * (REPEAT / 2) + order.indexOfFirst { it.id == g.id }.coerceAtLeast(0) else 0

    // the whole screen is the page's to touch, also round its edges while it grows or shrinks: a tap there never reaches
    // what's under it (the rail's Settings opened under a closing page, which then stayed open, unseen). It only
    // listens: being touchable is enough to keep the touch from what's under it, and consuming here would cancel the
    // page's own buttons (a tap checks, after its parents, that nothing consumed it). Every sideways swipe here is the
    // page's (the pager, the strip, the viewer): the Shell's tab swipe leaves its lift alone, so a quick flick turns the
    // page instead of ending as a cancelled drag (as the shuffler does)
    val guard = LocalSwipeGuard.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { at[0] = it }.pointerInput(Unit) {
        awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); if (e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true } }
    }) {
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
                .background(Pn.pan)
                .ownTaps()
                .semantics { paneTitle = "Game page" },
        ) {
            val pageW = maxWidth
            val pageH = maxHeight
            HorizontalPager(
                pager,
                Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = loop && !closing && viewer == null,
                key = { it },
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = if (still) snap() else tween(360, easing = Ease.house), snapPositionalThreshold = .22f),
            ) { p ->
                val pg = order[if (loop) p % n else 0]
                // only the page showing is there for TalkBack and the tests: a neighbour composed ahead stays silent
                val shown = p == pager.currentPage
                Box(
                    Modifier.fillMaxSize().then(if (shown) Modifier else Modifier.clearAndSetSemantics { }).graphicsLayer {
                        // the live drag (`hubPaint`): the page going shrinks to .88 and fades to .55 as the next one comes in
                        val f = abs(pager.getOffsetDistanceInPages(p)).coerceIn(0f, 1f)
                        if (!still) {
                            scaleX = 1f - .12f * f
                            scaleY = 1f - .12f * f
                            alpha = 1f - .45f * f
                        }
                    },
                ) {
                    GameBody(
                        pg, view, pageW, pageH, wide, still, acts,
                        cover = if (p == firstPage && pg.id == g.id) CoverSpot({ at[2] = it }, landed) else null,
                    )
                }
            }
            toast?.let { t -> PageNote(t, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomRoom.current + 20.dp)) }
            viewer?.let { (l, i) -> ShotViewer(l, i) { viewer = null } }
        }
        // the cover in flight goes with the page when it's closed mid-flight (it was left opaque over the fading page,
        // then cut off mid-air)
        flight?.let { f -> FlyingCover(f, g) { 1f - shut.value } }
    }
}

/**
 * What a page's buttons can ask of the whole page: close it ([leave]: null back to what was under it, else into the
 * Queue's tab, a genre), a line at the foot ([say]), the screenshots full screen ([shots]).
 */
private class PageActs(val leave: (String?) -> Unit, val say: (String) -> Unit, val shots: (List<Pair<String, String>>, Int) -> Unit)

/** The page cover the flight lands on: where it is laid out, and whether the flight has landed (it waits hidden till then). */
private class CoverSpot(val placed: (LayoutCoordinates) -> Unit, val landed: Boolean)

@Immutable
internal class PageToast(val text: String, val n: Int)

/** Your progress's lit steps as last shown, per game, for as long as the app runs (v271's `seen`): a move draws itself. */
private val trackSeen = HashMap<String, Int>()

/** One game's page: its own scroll, the hero, the body, and the bar that sticks over the top once the hero has gone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameBody(g: Game, view: PageView, pageW: Dp, pageH: Dp, wide: Boolean, still: Boolean, acts: PageActs, cover: CoverSpot?) {
    val page = view.page
    val lib = view.lib
    val rail = LocalRailAt.current
    val big = pageW >= 760.dp
    // unfolded, which side the rail is on (none while it's hidden behind Nexi): the words and Back keep clear of it
    val railR = rail.shown && !rail.left && pageW >= RAIL_FROM
    val railL = rail.shown && rail.left && pageW >= RAIL_FROM
    val accent = Color(g.colour)
    val volt = LocalLook.current.volt
    val scroll = rememberScrollState()
    val heroH = (pageH * if (big) .58f else .52f).coerceIn(270.dp, if (big) 560.dp else 460.dp)
    val heroPx = with(LocalDensity.current) { heroH.toPx() }
    // what slides when the tag row unfolds or About opens (transform only): each part under it moves up by the height
    // change and settles back, so nothing jumps
    val tagSlide = remember(g.id) { Animatable(0f) }
    val aboutSlide = remember(g.id) { Animatable(0f) }
    // the web keeps 104 / 110 from a 72-wide rail; Android's rail is 112 wide (the owner's Option B), so the body keeps
    // the room every other screen does (railRoom: RAIL_OUT + 12), or the tiles and cards run under it
    val padS = if (railL) RAIL_OUT + 12.dp else if (big) 28.dp else 16.dp
    val padE = if (railR) RAIL_OUT + 12.dp else if (big) 28.dp else 16.dp
    val top = LocalBottomRoom.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Hero(g, heroH, big, accent)
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 980.dp).fillMaxWidth().offset(y = (-30).dp)
                    .padding(start = padS, end = padE, bottom = 90.dp),
            ) {
                Head(g, page, pageW, big, accent, cover)
                val shift = Modifier.graphicsLayer { translationY = tagSlide.value }
                Tags(g, page, lib, still, acts, tagSlide)
                Tiles(g, page, pageW, big, shift)
                MainRow(g, page, lib, big, acts, shift)
                if (g.id in view.changed) {
                    BasicText(
                        "Practice copy: changes reset when the app reopens",
                        style = Type.note.copy(fontSize = 11.5.sp).fixed(),
                        modifier = shift.padding(top = 10.dp),
                    )
                }
                Progress(g, page, still, volt, shift)
                val about = page.about(g)
                if (about.isNotEmpty()) About(about, still, aboutSlide, shift)
                // one list per game and copy, so the strip isn't redrawn on every pass of the page
                val shots = remember(page, g) { page.shots(g) }
                if (shots.isNotEmpty()) Shots(g, shots, big, acts, Modifier.graphicsLayer { translationY = tagSlide.value + aboutSlide.value })
            }
            Spacer(Modifier.height(top))
        }
        // Back, top right (`#full .hub .fullx`, index.html 16523): 16 in, 100 unfolded where the rail sits
        BackPill(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp, end = if (railR) RAIL_OUT + 8.dp else 8.dp)) { acts.leave(null) }
        StickyBar(g, scroll, heroPx, railR, still, acts)
    }
}

/** The hero: the backdrop (`bgSource`, framed by `bgX`/`bgY`, 50/30 here), its shade and the game's colour, the logo. */
@Composable
internal fun Hero(g: Game, h: Dp, big: Boolean, accent: Color) {
    val glow = remember(accent) { accent.copy(alpha = .34f) }
    val top = remember { Color(Web.GD_HERO_TOP) }
    Box(Modifier.fillMaxWidth().height(h).clipToBounds()) {
        val back = Pic.backdrop(g)
        val bx = Backup.num(g["bgX"], 50.0, 0.0, 100.0).toFloat()
        val by = Backup.num(g["bgY"], 30.0, 0.0, 100.0).toFloat()
        if (back != null) PicBox(back, g, Modifier.fillMaxSize(), ground = Color(Web.GD_BG), align = BiasAlignment(bx / 50f - 1f, by / 50f - 1f))
        else Box(Modifier.fillMaxSize().background(Color(Web.GD_BG)))
        // `.gd-hero::after`: a shade at the top, clear in the middle, the page's colour at the foot, and the game's colour
        // glowing up from the bottom right (an ellipse 90% x 70% at 85% 100%, gone by 70% of it)
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(0f to top, .26f to Color.Transparent, .55f to Color.Transparent, 1f to Pn.pan))
                val rx = size.width * .9f
                val ry = size.height * .7f
                val c = Offset(size.width * .85f, size.height)
                withTransform({ scale(1f, ry / rx, c) }) {
                    drawRect(Brush.radialGradient(0f to glow, .7f to Color.Transparent, center = c, radius = rx), topLeft = Offset(0f, c.y - rx), size = androidx.compose.ui.geometry.Size(size.width, rx * 2))
                }
            },
        )
        // the logo (`g.art`): bottom left, never taking a touch
        val art = Backup.str(g["art"])
        if (art.isNotEmpty()) {
            AsyncImage(
                model = picUrl(art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = if (big) 28.dp else 18.dp, bottom = if (big) 50.dp else 40.dp)
                    .fillMaxWidth(.52f).heightIn(max = if (big) 96.dp else 70.dp),
            )
        }
    }
}

/** The cover (tilted -4°, a white ring), the kicker pill in the game's colour and the name. */
@Composable
private fun Head(g: Game, page: Page, vw: Dp, big: Boolean, accent: Color, cover: CoverSpot?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val shape = RoundedCornerShape(if (big) 18.dp else 16.dp)
        val seen = cover?.landed ?: true
        Box(
            Modifier
                .width(if (big) 118.dp else 84.dp)
                .aspectRatio(3f / 4f)
                .then(if (cover != null) Modifier.onGloballyPositioned { cover.placed(it) } else Modifier)
                .graphicsLayer { rotationZ = -4f; alpha = if (seen) 1f else 0f }
                .shadow(18.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
                .clip(shape)
                .border(2.dp, Pn.white.copy(alpha = .9f), shape),
        ) { PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.GD_BG)) }
        Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
            BasicText(
                page.kicker(g),
                style = Type.kicker.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .06.em, color = Color(Web.GD_KICK_INK)).fixed(),
                // short things stay whole: it shrinks to fit, never "…"
                maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.5.sp, stepSize = .5.sp),
                modifier = Modifier.padding(bottom = 6.dp).background(accent, Pn.pill).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            // `clamp(24px, 7vw, 40px)/1.08`, 700, -.02em, white, wrapping
            val fs = (vw.value * .07f).coerceIn(24f, 40f)
            BasicText(
                g.title,
                style = Type.title(big).copy(fontSize = fs.sp, lineHeight = (fs * 1.08f).sp, letterSpacing = (-.02).em, color = Pn.white),
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/**
 * The tag row (`gdTags`): the state chip in the game's colour, the first three genres (each a button with how many of
 * your games share it), +N unfolding the rest and Steam players' tags in place, then the platforms (logos, or Names).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(g: Game, page: Page, lib: Library, still: Boolean, acts: PageActs, slide: Animatable<Float, *>) {
    val t = remember(g, page) { page.tags(g) }
    val state = page.stateChip(g)
    val names = Backup.truthy(lib["platText"])
    val plats = remember(g) { Plat.chips(g["platforms"]) }
    val platNames = remember(g) { Rank.platList(g["platforms"]).take(3) }
    if (state.isEmpty() && t.base.isEmpty() && t.st.isEmpty() && plats.isEmpty()) return
    var open by remember(g.id) { mutableStateOf(false) }
    var h0 by remember(g.id) { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val accent = Color(g.colour)
    val volt = LocalLook.current.volt
    val chipText = Type.body.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium).fixed()
    FlowRow(
        Modifier.padding(top = 14.dp).fillMaxWidth().onSizeChanged { s ->
            // the unfold: what's below starts where it was and slides to its new place (360ms, transform only)
            val was = h0
            h0 = s.height
            if (was >= 0 && was != s.height && !still) scope.launch { slide.snapTo((was - s.height).toFloat()); slide.animateTo(0f, tween(360, easing = Ease.house)) }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.isNotEmpty()) {
            Chip(state, chipText, fill = mixA(accent, Color(Web.GD_ACC_BASE), .26f), edge = mixA(accent, Color.Transparent, .55f))
        }
        val genre = { x: String ->
            Sounds.play(Sfx.Tap)
            Crumbs.tap("genre", "game page")
            acts.leave("G:$x")
        }
        for (x in t.base.take(3)) GenreChip(x, t.n(x), chipText, dashed = false, tap = genre)
        if (open) {
            for ((i, x) in t.base.drop(3).withIndex()) Rise(i, still) { GenreChip(x, t.n(x), chipText, dashed = false, tap = genre) }
            if (t.st.isNotEmpty()) {
                BasicText("Steam players say", style = Type.note.copy(fontSize = 11.sp, lineHeight = 14.sp).fixed(), modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 4.dp))
                for ((i, x) in t.st.withIndex()) Rise(i + t.base.size - 3, still) { GenreChip(x, t.n(x), chipText, dashed = true, tap = genre) }
            }
        }
        if (t.more > 0) {
            val label = if (open) "Less" else "+${t.more}"
            Box(
                Modifier.sizeIn(minHeight = 32.dp)
                    .semantics { contentDescription = if (open) "Show fewer tags" else "Show ${t.more} more tags" }
                    .clickable(role = Role.Button) { Sounds.play(Sfx.Tap); open = !open },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label, style = chipText.copy(color = volt, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false,
                    modifier = Modifier.background(volt.copy(alpha = .1f), Pn.pill).border(1.dp, volt.copy(alpha = .4f), Pn.pill).padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        if (names) for (x in platNames) Chip(x, chipText)
        else if (plats.isNotEmpty()) PlatPill(plats)
    }
}

/** A chip that fades and grows in when the tags unfold (240ms, 22ms apart, 12 at most). */
@Composable
private fun Rise(i: Int, still: Boolean, content: @Composable () -> Unit) {
    val a = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) { delay(minOf(i, 12) * 22L); a.animateTo(1f, tween(240, easing = EASE_OUT)) } }
    Box(Modifier.graphicsLayer { alpha = a.value; val s = .9f + .1f * a.value; scaleX = s; scaleY = s }) { content() }
}

@Composable
private fun Chip(text: String, style: androidx.compose.ui.text.TextStyle, fill: Color = Pn.card, edge: Color = Pn.line) {
    BasicText(
        text, style = style, maxLines = 1, softWrap = false,
        modifier = Modifier.sizeIn(minHeight = 32.dp).background(fill, Pn.pill).border(1.dp, edge, Pn.pill).padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

/** A genre (or a Steam players' tag, dashed): a button to your games with it, with how many share it when that's 2+. */
@Composable
private fun GenreChip(x: String, n: Int, style: androidx.compose.ui.text.TextStyle, dashed: Boolean, tap: (String) -> Unit) {
    Row(
        Modifier.sizeIn(minHeight = 32.dp)
            .semantics { contentDescription = if (n > 1) "$n of your games are $x · tap to see them" else "See your $x games" }
            .clickable(role = Role.Button) { tap(x) }
            .background(Pn.card, Pn.pill)
            .then(if (dashed) Modifier.dashedPill(Pn.line2) else Modifier.border(1.dp, Pn.line, Pn.pill))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(x, style = style, maxLines = 1, softWrap = false)
        if (n > 1) BasicText(n.toString(), style = style.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute), maxLines = 1, softWrap = false)
    }
}

/** A pill's dashed outline (`.gtag.st`'s `border-style: dashed`). */
private fun Modifier.dashedPill(c: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        c, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** The platforms in one pill (`.pfms`, v226/v228): each family's logo with its version raised beside it, a line between. */
@Composable
private fun PlatPill(chips: List<Plat.Chip>) {
    val small = Type.body.copy(fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.01).em).fixed()
    Row(
        Modifier.height(32.dp).background(Pn.card, Pn.pill).border(1.dp, Pn.line, Pn.pill).padding(horizontal = 4.dp)
            .semantics { contentDescription = chips.joinToString(", ") { (it.k ?: it.text ?: "") + " " + it.sub } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((i, c) in chips.withIndex()) {
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Pn.line2))
            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                when {
                    c.k == null -> BasicText(c.text ?: "", style = small, maxLines = 1, softWrap = false)
                    c.k == "io" -> BasicText("iOS", style = small, maxLines = 1, softWrap = false)
                    else -> PnIcon("plat:" + c.k, Pn.ice, 17.dp, label = null)
                }
                if (c.sub.isNotEmpty()) BasicText(c.sub, style = small, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The four tiles (`gdGlance`): a number never cut short, its size coming down to fit (the web's check calls a cut one a bug). */
@Composable
private fun Tiles(g: Game, page: Page, pageW: Dp, big: Boolean, shift: Modifier) {
    val tiles = page.tiles(g)
    val volt = LocalLook.current.volt
    // `clamp(14px, 3.7vw, 22px)`
    val fs = (pageW.value * .037f).coerceIn(14f, 22f)
    Row(shift.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((i, t) in tiles.withIndex()) {
            Column(
                Modifier.weight(1f).background(Pn.card, RoundedCornerShape(16.dp)).border(1.dp, Pn.line, RoundedCornerShape(16.dp))
                    .padding(start = 5.dp, end = 5.dp, top = 11.dp, bottom = 10.dp)
                    .semantics(mergeDescendants = true) { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    t.value,
                    style = Type.stat.copy(fontSize = fs.sp, lineHeight = (fs * 1.1f).sp, letterSpacing = (-.01).em, color = if (t.none) Pn.mute else if (i == 0) volt else Pn.white),
                    maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = fs.sp, stepSize = .5.sp),
                )
                BasicText(
                    t.label, style = Type.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp).fixed(), maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.5.sp, stepSize = .5.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The main row (`gdNext`): the one thing worth doing next, in your accent, then Edit (a round 50 wide button with the
 * drawn pencil, `.gd-main button.ic`). The ⋯ drawer comes with piece 5.
 */
@Composable
private fun MainRow(g: Game, page: Page, lib: Library, big: Boolean, acts: PageActs, shift: Modifier) {
    val next = page.next(g)
    val edit = LocalEditActs.current
    if (next.isEmpty() && edit == null) return
    val volt = LocalLook.current.volt
    val ctx = LocalContext.current
    val now = LocalNow.current
    Row(shift.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in next) {
            val icon = when (b.kind) { "finish" -> "ui:flag"; else -> "ui:play" }
            Row(
                Modifier
                    .then(if (big) Modifier.widthIn(max = 300.dp) else Modifier.weight(1f))
                    .sizeIn(minHeight = Pn.touch)
                    .shadow(12.dp, Pn.pill, ambientColor = Color.Transparent, spotColor = volt.copy(alpha = .9f))
                    .background(volt, Pn.pill)
                    .clickable(role = Role.Button) {
                        Crumbs.tap(b.kind, "game page")
                        when (b.kind) {
                            "start" -> {
                                Sounds.play(Sfx.Add)
                                Games.change(Practice.start(lib, g, now()) { Games.practiced(it) })
                                acts.say("${g.title} is now Playing")
                            }
                            "finish" -> {
                                Sounds.play(Sfx.Add)
                                Games.change(Practice.finish(lib, g, now(), java.time.ZoneId.systemDefault()) { Games.practiced(it) })
                            }
                            "trailer" -> {
                                Sounds.play(Sfx.Tap)
                                val u = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(g.title + " trailer"))
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                PnIcon(icon, Pn.void, 18.dp, label = null)
                BasicText(b.label, style = Type.button.copy(fontSize = 14.sp, lineHeight = 20.sp, color = Pn.void).fixed(), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = .5.sp))
            }
        }
        if (edit != null) {
            Box(
                Modifier.width(50.dp).sizeIn(minHeight = Pn.touch).background(Pn.card, Pn.pill).border(1.dp, Pn.line2, Pn.pill)
                    .clickable(role = Role.Button) {
                        Crumbs.tap("edit", "game page")
                        edit.open(g.id)
                    }
                    .semantics { contentDescription = "Edit" },
                contentAlignment = Alignment.Center,
            ) { PnIcon("ui:edit", Pn.ice, 18.dp, label = null) }
        }
    }
}

/** A page section (`gdSec`): a card with its drawn icon, title and a small meta on the right. */
@Composable
private fun Section(icon: String, title: String, meta: String, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.padding(top = 12.dp).fillMaxWidth().background(Pn.card, shape).border(1.dp, Pn.line, shape).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 15.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PnIcon(icon, Pn.ice, 17.dp, label = null)
            BasicText(title, style = Type.head.copy(fontSize = 13.sp, lineHeight = 16.sp).fixed(), maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (meta.isNotEmpty()) BasicText(meta, style = Type.note.copy(fontSize = 11.5.sp, lineHeight = 14.sp).fixed(), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.5.sp, stepSize = .5.sp))
        }
        content()
    }
}

/**
 * Your progress (`gdProgress`): three steps, out → playing → finished (or dropped), lit up to where the game is, and the
 * line under them. When the game moves on while the app is open, the line draws itself from the old step to the new one
 * and each new dot pops (v271; transform only, none when calm).
 */
@Composable
private fun Progress(g: Game, page: Page, still: Boolean, volt: Color, modifier: Modifier) {
    val st = page.state(g)
    val steps = page.steps(g)
    val litN = steps.count { it.c == "done" || it.c == "now" }
    val prev = remember(g.id) { trackSeen[g.id] }
    // the lit length in steps (0 = the first dot, 2 = all the way): from where it was to where it is
    val run = remember(g.id) { Animatable(((prev ?: litN) - 1).coerceAtLeast(0).toFloat()) }
    val pops = remember(g.id) { List(3) { Animatable(1f) } }
    val dropSeen = remember(g.id) { mutableStateOf(st == "dropped") }
    LaunchedEffect(litN, st) {
        val was = trackSeen[g.id]
        trackSeen[g.id] = litN
        val to = (litN - 1).coerceAtLeast(0).toFloat()
        val dropNow = st == "dropped" && !dropSeen.value
        dropSeen.value = st == "dropped"
        if (still || was == null || (was >= litN && !dropNow)) { run.snapTo(to); return@LaunchedEffect }
        val from = run.value
        val stepsMoved = abs(to - from).roundToInt().coerceAtLeast(1)
        launch { run.animateTo(to, tween(300 + 140 * stepsMoved, easing = TRACK)) }
        // each new dot pops as the line reaches it (`scale .4 → 1.3 → 1`, 380ms); a drop pops the last dot
        val fresh = (was until litN).toMutableList().also { if (dropNow) it += 2 }
        for (i in fresh.distinct()) launch {
            val at = if (i <= from) 0L else ((i - from) / maxOf(1f, to - from) * (300 + 140 * stepsMoved)).toLong()
            pops[i].snapTo(.4f)
            delay(at)
            pops[i].animateTo(1f, keyframes { durationMillis = 380; .4f at 0 using POP; 1.3f at 220 using POP; 1f at 380 })
        }
    }
    Section("ui:compass", "Your progress", page.stateWord(st), modifier) {
        val grey = Pn.line2
        val lit = volt.copy(alpha = .55f)
        Box(Modifier.padding(top = 12.dp).fillMaxWidth()) {
            // the line behind the dots, from the first dot's middle to the last's: grey, and the lit part over it, grown
            // from the left by its scale alone
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp)) {
                val third = maxWidth / 3
                Box(Modifier.padding(start = third / 2).width(third * 2).fillMaxHeight().background(grey))
                Box(
                    Modifier.padding(start = third / 2).width(third * 2).fillMaxHeight()
                        .graphicsLayer { transformOrigin = TransformOrigin(0f, .5f); scaleX = (run.value / 2f).coerceIn(0f, 1f) }
                        .background(lit),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                for ((i, s) in steps.withIndex()) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val on = s.c == "done" || s.c == "now"
                        Box(Modifier.size(14.dp).graphicsLayer { val p = pops[i].value; scaleX = p; scaleY = p }, contentAlignment = Alignment.Center) {
                            // requiredSize: the 22 halo spills out of its 14 dot box (a plain size was held to 14 and never showed, M80)
                            if (s.c == "now") Box(Modifier.requiredSize(22.dp).background(volt.copy(alpha = .2f), CircleShape))
                            Box(
                                Modifier.size(14.dp).clip(CircleShape).background(
                                    when (s.c) { "done" -> volt; "drop" -> Color(Web.GD_DROP); else -> Pn.pan },
                                ).border(2.dp, when (s.c) { "done", "now" -> volt; "drop" -> Color(Web.GD_DROP); else -> Color(Web.GD_DOT_EDGE) }, CircleShape),
                            )
                        }
                        BasicText(s.name, style = Type.label.copy(fontSize = 12.5.sp, lineHeight = 15.sp, letterSpacing = 0.em, color = if (on) Pn.ice else Pn.mute).fixed(), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.5.sp, stepSize = .5.sp))
                        BasicText(s.sub.ifEmpty { " " }, style = Type.note.copy(fontSize = 11.sp, lineHeight = 14.sp).fixed(), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = .5.sp))
                    }
                }
            }
        }
        page.line(g)?.let { parts ->
            BasicText(
                buildAnnotatedString { for ((t, b) in parts) if (b) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t) },
                style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp).fixed(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** About (`gdAbout`): four lines, then "Read more" when there's more to read; what's under it slides, the new lines fade in. */
@Composable
private fun About(text: String, still: Boolean, slide: Animatable<Float, *>, modifier: Modifier) {
    var open by remember(text) { mutableStateOf(false) }
    var cut by remember(text) { mutableStateOf(false) }
    var h0 by remember(text) { mutableIntStateOf(-1) }
    val fade = remember(text) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val volt = LocalLook.current.volt
    Section("ui:book", "About", "", modifier) {
        BasicText(
            text,
            style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 20.9.sp, color = Color(Web.GD_DESC)).fixed(),
            maxLines = if (open) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { r -> if (!open) cut = r.hasVisualOverflow },
            modifier = Modifier.padding(top = 10.dp).graphicsLayer { alpha = fade.value }.onSizeChanged { s ->
                val was = h0
                h0 = s.height
                if (was >= 0 && was != s.height && !still) scope.launch { slide.snapTo((was - s.height).toFloat()); slide.animateTo(0f, tween(420, easing = Ease.house)) }
            },
        )
        if (text.length > 170 && (cut || open)) {
            Box(
                Modifier.padding(top = 2.dp).sizeIn(minHeight = Pn.touch).clickable(role = Role.Button) {
                    Sounds.play(Sfx.Tap)
                    open = !open
                    if (open && !still) scope.launch { fade.snapTo(.45f); fade.animateTo(1f, tween(420, easing = EASE_OUT)) }
                },
                contentAlignment = Alignment.CenterStart,
            ) { BasicText(if (open) "Read less" else "Read more", style = Type.button.copy(fontSize = 13.sp, lineHeight = 16.sp, color = volt).fixed()) }
        }
    }
}

/** Screenshots (`gdShots`): a strip of 16:9 pictures that snaps to each one's start; a tap opens them full screen. */
@Composable
private fun Shots(g: Game, shots: List<Pair<String, String>>, big: Boolean, acts: PageActs, modifier: Modifier) {
    val src = Backup.str(g["shotsSrc"])
    Section("ui:camera", "Screenshots", if (src.isNotEmpty()) "from $src" else "", modifier) {
        val list = rememberLazyListState()
        BoxWithConstraints(Modifier.padding(top = 10.dp).fillMaxWidth()) {
            val w = if (big) 260.dp else minOf(maxWidth * .78f, 300.dp)
            LazyRow(
                state = list,
                flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Start),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // the strip keeps its own sideways drag: it never turns the page to the next game (a web quirk not copied),
                // also past its first or last shot (the pager's own page connection keeps what the strip can't use;
                // GamePageGestureTest), and the page's guard keeps it from the Shell's tab swipe
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(shots) { i, s ->
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.width(w).aspectRatio(16f / 9f).clip(shape).background(Color(Web.GD_BG))
                            .semantics { contentDescription = "Screenshot ${i + 1} of ${shots.size}" }
                            .clickable(role = Role.Button) { Sounds.play(Sfx.Open); acts.shots(shots, i) },
                    ) { PicBox(Pic.Url(s.first), null, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/** The screenshots full screen (`#shv`): one per page, the big copy over the small one, the count, a close button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShotViewer(shots: List<Pair<String, String>>, start: Int, close: () -> Unit) {
    BackHandler { close() }
    val pg = rememberPagerState(initialPage = start) { shots.size }
    run {
        Box(Modifier.fillMaxSize().background(Color(Web.GD_VIEWER)).ownTaps().semantics { paneTitle = "Screenshots" }) {
            HorizontalPager(pg, Modifier.fillMaxSize()) { p ->
                Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "Close") { close() }, contentAlignment = Alignment.Center) {
                    AsyncImage(model = picUrl(shots[p].first), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp))
                    AsyncImage(model = picUrl(shots[p].second), contentDescription = "Screenshot ${p + 1} of ${shots.size}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp).size(Pn.touch)
                    .clickable(role = Role.Button) { close() }.semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { PnIcon("ui:close", Pn.ice, 22.dp, label = null) }
            BasicText(
                "${pg.currentPage + 1} / ${shots.size}",
                style = Type.note.fixed(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomRoom.current + 24.dp),
            )
        }
    }
}

/** Back: a pill with a drawn chevron (`#full-close`). */
@Composable
private fun BackPill(modifier: Modifier, close: () -> Unit) {
    Box(
        modifier.sizeIn(minWidth = Pn.touch, minHeight = Pn.touch).clickable(role = Role.Button) { close() }.semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.background(Pn.pan.copy(alpha = .82f), Pn.pill).border(1.dp, Pn.line2, Pn.pill).padding(start = 10.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PnIcon("ui:back", Pn.ice, 16.dp, label = null)
            BasicText("Back", style = Type.button.copy(fontSize = 14.sp, color = Pn.ice).fixed())
        }
    }
}

/**
 * The bar that sticks over the top once the hero has scrolled away (`.gdsb`, v281; W2: when the hero's foot reaches the
 * top): the cover, the name (a tap goes back to the top) and Back. It slides down and fades in (280 / 180ms).
 */
@Composable
private fun StickyBar(g: Game, scroll: androidx.compose.foundation.ScrollState, heroPx: Float, railR: Boolean, still: Boolean, acts: PageActs) {
    val stuck by remember(heroPx) { derivedStateOf { scroll.value >= heroPx } }
    val a by animateFloatAsState(if (stuck) 1f else 0f, if (still) snap() else tween(180), label = "bar alpha")
    val y by animateFloatAsState(if (stuck) 0f else -1f, if (still) snap() else tween(280, easing = Ease.house), label = "bar slide")
    if (!stuck && a == 0f) return
    val scope = rememberCoroutineScope()
    val calm = LocalCalm.current
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = a; translationY = y * size.height }.background(Pn.pan).drawBehind {
            drawRect(Pn.line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
        }.ownTaps(),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.height(56.dp).fillMaxWidth().padding(start = 16.dp, end = if (railR) RAIL_OUT + 8.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(30.dp, 40.dp).clip(RoundedCornerShape(8.dp))) { PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.GD_BG)) }
            Box(
                Modifier.weight(1f).sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClickLabel = "Back to the top") {
                    Sounds.play(Sfx.Tap)
                    scope.launch { if (calm || still) scroll.scrollTo(0) else scroll.animateScrollTo(0) }
                },
                contentAlignment = Alignment.CenterStart,
            ) { BasicText(g.title, style = Type.name.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            BackPill(Modifier) { acts.leave(null) }
        }
    }
}

/** A line a button leaves, at the foot for a moment (the web's toast). */
@Composable
internal fun PageNote(t: PageToast, modifier: Modifier) {
    BasicText(
        t.text,
        modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 16.dp)
            .background(Pn.card2, Pn.rs).border(1.dp, Pn.line2, Pn.rs).padding(horizontal = 16.dp, vertical = 13.dp),
        style = Type.body.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
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
