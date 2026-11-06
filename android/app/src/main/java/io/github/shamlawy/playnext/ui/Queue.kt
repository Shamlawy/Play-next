package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.layout.LocalPinnableContainer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import io.github.shamlawy.playnext.kit.Buzz
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size as CoilSize
import coil3.transform.Transformation
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Lists
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The Queue's list (Phase 1 piece 1; android/parity/1-map/1-list.md): the bar under the header (`qdDeck`: the tabs with
 * their counts and your lists, one Order button with its grouped menu), On deck's three photo cards (v303) and the
 * closed rows (v301 Strap, the v290 logo slot) with the Dusty mark, in a lazy list so only the rows on screen are drawn.
 * A tap on a row opens its card (QueueCard.kt, piece 2); Shuffle, Boss, drag, swipe to delete and Character select
 * (piece 3) come next. A practice copy: the tab, order and open card you pick last until the app reopens.
 */
@Immutable
class QueueView(val q: Queue, val tab: String, val sort: String, val shown: List<Game>, val now: Long, val src: Library = q.lib)

/** The Queue's own numbers for [lib] at this minute: the tab and order picked, the games shown in that order. */
@Composable
fun rememberQueueView(lib: Library, pick: QueuePick, made: Queue? = null): QueueView {
    val now = LocalNow.current()
    val zone = java.time.ZoneId.systemDefault()
    // what it says changes at the phone's midnight (today, dust, just out) and at UTC midnight (a date's out or coming,
    // the days to go), never between: the Games day build makes it off Main for that stretch ([made]); this one only
    // fills in until a missed rollover catches up, or for a Ready made by hand
    val day = io.github.shamlawy.playnext.data.JsDate.todayLocal(now, zone)
    val utcDay = Math.floorDiv(now, io.github.shamlawy.playnext.data.JsDate.DAY)
    // the practice deletes (read every time, as [QueuePick.moved]): the Queue works on the backup without them
    val gone = pick.deleted.takeIf { pick.deletedFor === lib } ?: emptySet()
    val base = remember(lib, gone) { lib.without(gone) }
    val q = remember(base, day, utcDay, made) { made?.takeIf { it.lib === base && it.covers(now, zone) } ?: Queue(base, Rank(base, now, zone), now, zone) }
    val tab = q.tabNow(pick.tab)
    val sort = pick.sort
    val all = pick.moved // read every time, so a first drop redraws
    val moved = if (pick.movedFor === lib) all else emptyMap()
    val shown = remember(q, tab, sort, moved) { q.sort(q.list(tab), sort, moved) }
    return QueueView(q, tab, sort, shown, now, lib)
}

/** The Queue's tab and order: the web app's (`S.qFilter`, `S.sort`) to start with, then yours until the app reopens. */
class QueuePick(lib: Library) {
    var tab by mutableStateOf(Backup.str(lib["qFilter"]).ifEmpty { "all" })
    var sort by mutableStateOf(Backup.str(lib["sort"]).ifEmpty { "manual" })

    /** The open card (`focusId`, `collapsed`): at start the first game shown is open, as on the web. */
    var focus by mutableStateOf<String?>(null)
    var collapsed by mutableStateOf(false)

    /** The card a tap just opened: it plays its entrance and sound once (a card open at start just sits there). */
    var fresh by mutableStateOf<String?>(null)

    /** The open card showing its back (`flipId`), kept while it scrolls away and back. */
    var flip by mutableStateOf<String?>(null)

    /** The card that's open in [shown]: the one you tapped, else the first, none once closed. */
    fun openIn(shown: List<Game>): String? = if (collapsed || shown.isEmpty()) null else focus?.takeIf { id -> shown.any { it.id == id } } ?: shown[0].id

    /** A tap on a closed row: its card opens and the one that was open closes into its row. */
    fun open(id: String) { focus = id; collapsed = false; fresh = id; flip = null; shut = null }

    /** The row a card just closed into: the card folds into it, once ([shutBack]: the side the card showed). */
    var shut by mutableStateOf<String?>(null)
    var shutBack by mutableStateOf(false)

    /** The card folding into its row now, if any: the rows under it rise ([QueueFold]). */
    var fold by mutableStateOf<QueueFold?>(null)

    /**
     * The practice order (Phase 1 is read only): a drag's new places, kept in memory for this backup until the app is
     * opened again, nothing saved (android/parity/1-map/1-list.md 9).
     */
    var moved by mutableStateOf<Map<String, Double>>(emptyMap())

    /** The backup [moved] belongs to: a newer backup (the start-up check, back after a while) starts from its own order. */
    var movedFor by mutableStateOf<Library?>(null, referentialEqualityPolicy())

    /** The row (or open card) held and moving, null when none. */
    var drag by mutableStateOf<QueueDrag?>(null)

    /** Just dropped: the rows snap into their new places with no glide (the web skips it after a drop), for two frames. */
    var dropped by mutableStateOf<String?>(null)

    /** Shuffle's last pick (`qdLast`, memory only): never the same game twice running. */
    var lastShuf by mutableStateOf<String?>(null)

    /** The game Shuffle just picked: its row lights up and the list brings it to the middle ([QueueLit.n] tells two picks of one game apart). */
    var lit by mutableStateOf<QueueLit?>(null)

    /** A short line over the view pill ("Tonight: …"), gone after [NOTE_MS]. */
    var note by mutableStateOf<QueueLit?>(null)

    /** Shuffle's taps so far: only goes up, so every pick (also of the same game) is new to the ring and the scroll. */
    var taps by mutableIntStateOf(0)

    /**
     * Shuffle's tap (`qdShuffle`, index.html 8810): a random game from [Queue.shufflePool]; when the shown tab doesn't
     * hold it the Queue goes back to All (the web saves that, here it lasts until the app reopens, like the tab).
     */
    fun shuffle(q: Queue, rnd: kotlin.random.Random = kotlin.random.Random.Default): Game? {
        val pool = q.shufflePool(lastShuf)
        val n = ++taps
        if (pool.isEmpty()) {
            note = QueueLit("", "Nothing to pick from yet", n)
            return null
        }
        val g = pool[rnd.nextInt(pool.size)]
        lastShuf = g.id
        if (q.list(q.tabNow(tab)).none { it.id == g.id }) tab = "all"
        lit = QueueLit(g.id, g.title, n)
        note = QueueLit(g.id, "Tonight: " + g.title, n)
        return g
    }

    /** A short line where Shuffle's goes (Boss mode's "comes in a later update"). */
    fun say(text: String) { note = QueueLit("", text, ++taps) }

    /** One tap on the open card [id]: it closes into its row. */
    fun close(id: String? = null) { shutBack = id != null && flip == id; focus = null; collapsed = true; fresh = null; flip = null; shut = id }

    /** The row (or open card) swiped left to show its Delete button: one at a time. */
    var swiped by mutableStateOf<String?>(null)

    /**
     * The practice deletes (Phase 1 is read only): games swiped away, gone from this backup's Queue until the app opens
     * again, nothing saved. [deletedFor] is the backup they belong to (a newer one starts whole).
     */
    var deleted by mutableStateOf<Set<String>>(emptySet())
    var deletedFor by mutableStateOf<Library?>(null, referentialEqualityPolicy())

    /** The last delete, offered back on the Undo bar for a few seconds. */
    var undo by mutableStateOf<QueueGone?>(null)

    /** Delete [g] from [lib]'s Queue (the web's `qdel`, with Android's Undo in place of its question). */
    fun delete(lib: Library, g: Game) {
        if (deletedFor !== lib) { deleted = emptySet(); deletedFor = lib }
        deleted = deleted + g.id
        if (focus == g.id) focus = null
        if (flip == g.id) flip = null
        swiped = null
        undo = QueueGone(g.id, g.title, (undo?.n ?: 0) + 1)
    }

    /** The Undo bar's button: the last deleted game comes back where it was. */
    fun undoDelete() {
        val u = undo ?: return
        deleted = deleted - u.id
        undo = null
    }
}

/** A deleted game on the Undo bar ([n] counts deletes, so a second one restarts the bar's time). */
@Immutable
class QueueGone(val id: String, val title: String, val n: Int)

/** The colours the web CSS writes as literals (index.html 27366-27546, 24517-24533, 21096), read by tools/android-gen.mjs. */
object QTone {
    private fun base(c: Long) = Color(c).copy(alpha = 1f)
    val playing = base(Web.QS_PLAYING) // --qsc for playing
    val coming = base(Web.QS_COMING) // --qsc for coming
    val played = base(Web.QS_PLAYED) // --qsc for played
    val shade = base(Web.Q_SHADE) // the picture's veil
    val shadeDeep = base(Web.Q_SHADE_DEEP) // the veil's right edge
    val ticket = base(Web.Q_TICKET) // the ticket's ground
    val pale = base(Web.Q_PALE) // the ticket's labels, the folded date
    val photo = base(Web.Q_PHOTO) // On deck's picture ground
    val onVolt = base(Web.Q_ON_VOLT) // words on On deck's button
    val dust = base(Web.Q_DUST) // the dust's dots
    val dustVeil = base(Web.Q_DUST_VEIL) // the dust's veil
}

/** `color-mix(in srgb, a t, b)` with transparency (premultiplied, as CSS mixes): not [mix], which drops alpha. */
fun mixA(a: Color, b: Color, t: Float): Color {
    val al = a.alpha * t + b.alpha * (1 - t)
    if (al == 0f) return Color.Transparent
    fun ch(x: Float, y: Float) = ((x * a.alpha * t + y * b.alpha * (1 - t)) / al).coerceIn(0f, 1f)
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue), al)
}

/** `color-mix(in oklab, a t, b)` (Compose's lerp mixes in Oklab). */
fun mixOk(a: Color, b: Color, t: Float): Color = lerp(b, a, t)

fun stateTone(k: Queue.Kind, volt: Color): Color = when (k) {
    Queue.Kind.Playing -> QTone.playing
    Queue.Kind.Coming -> QTone.coming
    Queue.Kind.Played -> QTone.played
    Queue.Kind.Dropped -> Pn.mute
    Queue.Kind.Out -> volt
}

/** The Queue's items in the page's lazy list: the bar, On deck and the rows (or what's empty). */
fun LazyListScope.queueItems(v: QueueView, pick: QueuePick, list: LazyListState, folded: Boolean, calm: Boolean, mod: Modifier, sheet: (String) -> Unit, open: (String) -> Unit) {
    val q = v.q
    if (q.lib.games.isEmpty()) {
        item("q-empty") { Empty("Queue is empty. Open Add and search for the first game.", mod) }
        return
    }
    item("q-bar") { QueueBar(v, pick, mod, sheet) }
    val deck = if (q.lib.off("deckOff") || Backup.truthy(q.lib["deckOff"]) || v.tab == "done") emptyList() else q.deck(v.tab)
    if (deck.isNotEmpty()) item("q-deck") { OnDeck(q, v.tab, deck, mod, open) }
    if (v.shown.isEmpty()) item("q-none") { Empty("Nothing here right now. Try another filter.", mod) }
    val openId = pick.openIn(v.shown)
    itemsIndexed(v.shown, key = { _, g -> "q:" + g.id }) { i, g ->
        val dr = pick.drag
        val held = dr?.id == g.id
        // the rows between where the held one was and where it would land step aside by its height (`shiftRows`)
        val shift = when {
            dr == null || held -> 0f
            dr.from < dr.to && i in dr.from + 1..dr.to -> -dr.pitch
            dr.to < dr.from && i in dr.to until dr.from -> dr.pitch
            else -> 0f
        }
        val aside = animateFloatAsState(shift, if (dr == null || calm) snap() else tween(STEP_ASIDE_MS, easing = Ease.glide), label = "aside")
        if (pick.dropped == g.id) {
            LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; if (pick.dropped == g.id) pick.dropped = null }
            // gone from the screen within those frames: never leave every row's glide off
            DisposableEffect(Unit) { onDispose { if (pick.dropped == g.id) pick.dropped = null } }
        }
        val volt = LocalLook.current.volt
        val guard = LocalSwipeGuard.current
        val now by rememberUpdatedState(v)
        val opener by rememberUpdatedState(open)
        val open1 = g.id == openId
        // held, the row stays composed when the list scrolls it off screen at an edge (else its gesture would end)
        if (held) {
            val pins = LocalPinnableContainer.current
            DisposableEffect(Unit) { val h = pins?.pin(); onDispose { h?.release() } }
        }
        // swiped open (to show Delete): read through derivedStateOf, so opening one row redraws only that row
        val swipedOpen by remember(g.id, pick) { derivedStateOf { pick.swiped == g.id } }
        val px = LocalDensity.current
        val openPx = with(px) { DEL_OPEN.dp.toPx() }
        val slide = remember(g.id) { QueueSlide(if (swipedOpen) -openPx else 0f) }
        LaunchedEffect(slide, swipedOpen, slide.held, openPx) {
            val to = if (swipedOpen) -openPx else 0f
            if (slide.held || slide.x == to) return@LaunchedEffect
            if (calm) slide.x = to else animate(slide.x, to, animationSpec = tween(DEL_SNAP_MS, easing = DEL_EASE)) { x, _ -> slide.x = x }
        }
        // scrolling the list closes the open one (`qdel` closes on scroll)
        if (swipedOpen) LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { if (it && pick.swiped == g.id) pick.swiped = null } }
        // remembered, so a redraw of the list hands each row the very same modifiers and Compose can skip the rows
        // that didn't change (new lambdas every time made every row redraw: RedrawTest)
        // (a card folding into its row above this one: this row starts that much lower and glides up, [QueueFold])
        val layer = remember(g.id, i, aside, pick) {
            Modifier.graphicsLayer {
                val d = pick.drag
                val f = pick.fold
                translationY = (if (d == null) 0f else if (d.id == g.id) d.dy else aside.value) + (if (f != null && i > f.at) f.by * (1f - f.t.value) else 0f)
            }
        }
        val ringR = if (open1) Web.U_R.dp else if (folded) 20.dp else 22.dp
        val gap = if (open1) 26.dp else 12.dp
        val ring = remember(volt, ringR, open1) { Modifier.heldRing(volt, ringR, gap) }
        val holdIt = remember(g.id, i, pick, list, guard) { Modifier.hold(g.id, i, { now }, pick, list, guard) { opener(it) } }
        val swipeIt = remember(g.id, pick, slide, guard) { Modifier.swipeDelete(g.id, pick, slide, guard) }
        // the card that just closed folds into its row (the owner, Nov 5: it "just disappears"): the item takes the row's
        // height at once, every row under it (also rows coming on screen) starts lower by the difference and glides up,
        // covering a copy of the card that fades into the row coming in underneath. Only moves and fades per frame
        // (Android's motion rule: graphicsLayer translation, scale, alpha, rotation; nothing is laid out again).
        val shutting = !open1 && pick.shut == g.id
        val t = if (shutting) remember { Animatable(if (calm) 1f else 0f) } else null
        if (t != null) DisposableEffect(Unit) { onDispose { if (pick.shut == g.id) pick.shut = null } }
        if (t != null) LaunchedEffect(t) { t.animateTo(1f, tween(SHUT_MS, easing = Ease.glide)); if (pick.shut == g.id) pick.shut = null }
        // the item's last height: the open card's (its gap included), when it closes
        val itemH = remember(g.id) { RowHeight() }
        if (t != null) {
            val rowPx = with(px) { ((if (folded) 104 else 138) + ROW_GAP).dp.toPx() }
            // set before this frame's layout and drawing, so the rows under it never show at their new places first
            DisposableEffect(t) {
                if (itemH.px > rowPx) pick.fold = QueueFold(g.id, i, itemH.px - rowPx, t)
                onDispose { if (pick.fold?.id == g.id) pick.fold = null }
            }
        }
        // no row glides while a card folds (the fold moves them) or just after a drop (the web skips it)
        val m = mod.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (calm || pick.dropped != null || pick.shut != null) null else tween(SHUT_MS, easing = Ease.glide))
            // held: drawn over the rows under it (a folding card's copy stays under them, so they cover it as they rise)
            .zIndex(if (held) 1f else 0f)
            .then(layer)
            .then(if (held) ring else Modifier)
            .then(holdIt)
            .then(swipeIt)
            .onSizeChanged { if (t == null) itemH.px = it.height.toFloat() }
        val slid = remember(slide) { Modifier.graphicsLayer { translationX = slide.x } }
        val showDel by remember(slide) { derivedStateOf { slide.x < -0.5f } }
        Box(m) {
            // the red Delete waits under the row's right end and shows only while the row slides aside
            if (showDel) DeleteUnder(gap, ringR) { Sounds.play(Sfx.Back); Crumbs.tap("delete", "queue"); pick.delete(now.src, g) }
            if (open1) QueueCard(q, g, i + 1, pick.fresh == g.id, pick.flip == g.id, slid, entered = { if (pick.fresh == g.id) pick.fresh = null },
                flip = { back -> pick.flip = if (back) g.id else null }) { pick.close(g.id) }
            else {
                val rm = if (t == null) slid else slid.graphicsLayer { alpha = ((t.value - ROW_IN_FROM) / ROW_IN_FOR).coerceIn(0f, 1f) }
                QueueRow(q, g, i + 1, v.sort, folded, rm) { id -> Crumbs.tap("row", "queue"); pick.open(id) }
                if (t != null) {
                    val folding by remember(t) { derivedStateOf { t.value < 1f } }
                    val k = remember(t) { { t.value } }
                    if (folding) ShutGhost(q, g, i + 1, pick.shutBack, k)
                }
            }
            // Shuffle's pick lights up where it sits (read through derivedStateOf: a pick redraws only its own row)
            val litHere by remember(g.id, pick) { derivedStateOf { pick.lit?.takeIf { it.id == g.id } } }
            litHere?.let { LitRing(it, remember(g.colour) { Color(g.colour) }, ringR, gap, calm, slid) }
        }
    }
}

/**
 * The Undo bar after a delete (Android's addition, plan Oct 26: the web asks first and has no Undo): "Deleted <name>"
 * and Undo for [UNDO_MS], then it goes. A second delete restarts the time.
 */
@Composable
fun QueueUndo(pick: QueuePick, modifier: Modifier) {
    val u = pick.undo ?: return
    val volt = LocalLook.current.volt
    LaunchedEffect(u) { kotlinx.coroutines.delay(UNDO_MS); if (pick.undo === u) pick.undo = null }
    Row(
        modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 16.dp)
            .background(Pn.card2, Pn.rs).border(1.dp, Pn.line2, Pn.rs).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("Deleted ${u.title}", Modifier.weight(1f).padding(vertical = 12.dp), style = Type.body.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.sizeIn(minWidth = Pn.touch, minHeight = Pn.touch).clickable(role = Role.Button) { Sounds.play(Sfx.Tap); Crumbs.tap("undo", "queue"); pick.undoDelete() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("Undo", style = Type.button.copy(color = volt).fixed(), softWrap = false, maxLines = 1) }
    }
}

private const val UNDO_MS = 5_000L

/** Shuffle's pick ([id], its [title]) or a line about it; [n] counts the taps, so a second pick of the same game lights again. */
@Immutable
data class QueueLit(val id: String, val title: String, val n: Int)

/** The web's toast stays about this long. */
private const val NOTE_MS = 2_400L

/** Shuffle's ring (`.qslit`, 27417): 2.3 s, in to full by 12%, held to 75%, out by the end, on the web's ease. */
internal const val LIT_MS = 2_300

/** The line Shuffle leaves ("Tonight: …"), where the Undo bar goes (it waits while an Undo bar shows). */
@Composable
fun QueueNote(pick: QueuePick, modifier: Modifier) {
    val u = pick.note ?: return
    // the time runs while an Undo bar covers it too, so an old line never comes back once the Undo bar goes
    LaunchedEffect(u) { kotlinx.coroutines.delay(NOTE_MS); if (pick.note === u) pick.note = null }
    if (pick.undo != null) return
    BasicText(
        u.title,
        modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 16.dp)
            .background(Pn.card2, Pn.rs).border(1.dp, Pn.line2, Pn.rs).padding(horizontal = 16.dp, vertical = 13.dp),
        style = Type.body.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The ring on the row Shuffle picked (`.qslit`): an inside line in --ice and a wider one in the game's colour, faded in
 * and out (only its alpha moves), then gone.
 */
@Composable
private fun BoxScope.LitRing(lit: QueueLit, accent: Color, r: Dp, gap: Dp, calm: Boolean, slid: Modifier) {
    val a = remember(lit) { Animatable(0f) }
    LaunchedEffect(lit) {
        a.animateTo(1f, keyframes {
            durationMillis = LIT_MS
            // (an easing applies from its keyframe to the next: in on the web's ease, held, out on it)
            0f at 0 using Ease.glide
            1f at (LIT_MS * .12f).toInt()
            1f at (LIT_MS * .75f).toInt() using Ease.glide
            0f at LIT_MS
        })
    }
    val ring = remember(accent, r) { mixA(accent, Color.Transparent, .6f) }
    Box(
        Modifier.matchParentSize().padding(bottom = gap).then(slid)
            .graphicsLayer { alpha = if (calm) (if (a.isRunning) 1f else 0f) else a.value }
            .drawBehind {
                val w2 = 2.dp.toPx()
                val w6 = 6.dp.toPx()
                drawRoundRect(ring, Offset(w6 / 2, w6 / 2), Size(size.width - w6, size.height - w6), CornerRadius((r.toPx() - w6 / 2).coerceAtLeast(0f)), style = Stroke(w6))
                drawRoundRect(Pn.ice, Offset(w2 / 2, w2 / 2), Size(size.width - w2, size.height - w2), CornerRadius((r.toPx() - w2 / 2).coerceAtLeast(0f)), style = Stroke(w2))
            },
    )
}

/**
 * Brings Shuffle's pick to the middle of the list (`scrollIntoView({block: "center"})`): when its row isn't on screen
 * the list first goes to it (rows sit together, so a row on screen tells where the others are), then centres it.
 */
internal suspend fun LazyListState.centerRow(id: String, shownNow: () -> List<Game>, calm: Boolean) {
    // wait (a second at most) until the list on screen is the one that holds the pick: right after Shuffle sends the
    // Queue back to All, the layout can still be the old tab's, whose row places would aim at the wrong row
    val shown = kotlinx.coroutines.withTimeoutOrNull(1_000L) {
        snapshotFlow { layoutInfo to shownNow() }.first { (li, s) -> s.any { it.id == id } && laidOutAs(li, s) }.second
    } ?: return
    val at = shown.indexOfFirst { it.id == id }
    repeat(4) {
        val vis = layoutInfo.visibleItemsInfo
        val item = vis.firstOrNull { it.key == "q:$id" }
        if (item != null) {
            val mid = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val by = (item.offset + item.size / 2 - mid).toFloat()
            if (calm) scrollBy(by) else animateScrollBy(by, tween(LIT_SCROLL_MS, easing = Ease.glide))
            return
        }
        val anchor = vis.firstOrNull { (it.key as? String)?.startsWith("q:") == true }
        val to = if (anchor != null) anchor.index + at - shown.indexOfFirst { "q:" + it.id == anchor.key } else (vis.lastOrNull()?.index ?: return) + 1
        val i = to.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        if (calm) scrollToItem(i) else animateScrollToItem(i)
    }
}

private const val LIT_SCROLL_MS = 420

/** The rows on screen in [li] sit where [shown] puts them (the same index gap between any two), so it was laid out from it. */
internal fun laidOutAs(li: androidx.compose.foundation.lazy.LazyListLayoutInfo, shown: List<Game>): Boolean {
    val pos = HashMap<String, Int>(shown.size * 2).also { m -> shown.forEachIndexed { i, g -> m["q:" + g.id] = i } }
    var gap: Int? = null
    for (item in li.visibleItemsInfo) {
        val key = item.key as? String ?: continue
        if (!key.startsWith("q:")) continue
        val i = pos[key] ?: return false
        if (gap == null) gap = item.index - i else if (item.index - i != gap) return false
    }
    return true
}

/**
 * The red button under a swiped row (`.qdel`: the row's height, its right corners): a tap deletes the game. It is as
 * wide as the furthest swipe (138), so a long swipe never shows a gap past it (the owner, Nov 5), and the row's slide
 * uncovers it: nothing about it moves or is laid out again. "Delete" sits in the middle of the 92 that show when open.
 */
@Composable
private fun BoxScope.DeleteUnder(gap: Dp, r: Dp, delete: () -> Unit) {
    val shape = RoundedCornerShape(topEnd = r, bottomEnd = r)
    Box(Modifier.matchParentSize().padding(bottom = gap), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier.width(DEL_MAX.dp).fillMaxHeight().clip(shape).background(Pn.hot)
                .clickable(role = Role.Button, onClickLabel = "Delete", onClick = delete),
            contentAlignment = Alignment.CenterEnd,
        ) { Box(Modifier.width(DEL_OPEN.dp), contentAlignment = Alignment.Center) { BasicText("Delete", style = QType.del.fixed(), softWrap = false, maxLines = 1) } }
    }
}

/** A row's sideways place while it's swiped (`qdel`'s translate on the row's children): [held] while a finger moves it. */
@Stable
private class QueueSlide(start: Float) {
    var x by mutableFloatStateOf(start)
    var held by mutableStateOf(false)
}

/** A closing card folds into its row on the rows' own glide (560 ms): the row fades in from 35% to 75% of it, under the card, which fades from 50% (the glide is mostly done by then, so the rows rising over the card never leave a hole above them). */
private const val SHUT_MS = 560
private const val ROW_IN_FROM = .35f
private const val ROW_IN_FOR = .4f
private const val GHOST_OUT_FROM = .5f
private const val GHOST_OUT_FOR = .45f

/** An item's measured height (its gap included): the open card's, read when it closes. */
@Stable
private class RowHeight { var px by mutableFloatStateOf(0f) }

/**
 * A card folding into its row: the rows after index [at] start [by] px lower and rise as [t] goes 0 → 1 (read while
 * drawing, so the fold redraws nothing).
 */
@Stable
class QueueFold(val id: String, val at: Int, val by: Float, val t: Animatable<Float, *>)

/**
 * The card folding into its row after one tap ([t] reads its progress, 0 → 1): the open card as it was (its side
 * [back]), taking no room (the row under it sets the item's height), with no touches and nothing for TalkBack. The rows
 * under it rise over it while it fades out.
 */
@Composable
private fun ShutGhost(q: Queue, g: Game, n: Int, back: Boolean, t: () -> Float) {
    val m = remember(t) {
        Modifier
            // measured whole, placed at the top, 0 high: the item is as tall as its row (a 0-high box takes no touches)
            .layout { me, c -> val p = me.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity)); layout(p.width, 0) { p.place(0, 0) } }
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = 1f - ((t() - GHOST_OUT_FROM) / GHOST_OUT_FOR).coerceIn(0f, 1f) }
    }
    QueueCard(q, g, n, false, back, m, entered = {}, flip = {}) {}
}

/** A closed row's gap under it. */
private const val ROW_GAP = 12

/** `qdel`: the open row sits 92 aside, a finger takes it at most 138, past half it snaps open in .24s on this curve. */
private const val DEL_OPEN = 92
private const val DEL_MAX = 138
private const val DEL_SNAP_MS = 240
private val DEL_EASE = CubicBezierEasing(.2f, .9f, .25f, 1f)
private const val HOLD_MS = 380L
private const val STEP_ASIDE_MS = 170

/**
 * A held row near the top or bottom of what you see (within 110 dp of the list's edge inside the bars) scrolls the
 * list: 0.12 dp a millisecond where the zone starts, rising on a curve to 2.4 at the edge and past it (the owner, Nov 5:
 * "faster the more I'm close to the edge"; android-v17's straight line to 0.9 felt flat).
 */
private const val EDGE_ZONE = 110
private const val EDGE_SLOW = 0.12f
private const val EDGE_FAST = 2.4f

/**
 * A row held and moving (`startDrag`, index.html 7417): where it was ([from], its index in what's shown), the rows'
 * middles in the list ([mids], measured as they come on screen: the list scrolls by itself at the edges), its own
 * height with its gap ([pitch]), how far it is drawn from its place ([dy]: the finger's way plus what the list
 * scrolled) and the slot it would land in ([to]). Positions are the list's own (the row's offset plus [scrolled]), so
 * rows the scroll brings in fit with those measured first.
 */
@Stable
class QueueDrag(val id: String, val from: Int, private val mids: HashMap<Int, Float>, val pitch: Float, private val mid0: Float, finger: Float) {
    var dy by mutableFloatStateOf(0f)
    var to by mutableIntStateOf(from)

    /**
     * How far the list scrolled while held (gesture bookkeeping only), and where the finger is in the list's window
     * (state, so the edge scroll sleeps until the finger reaches an edge: no frame runs while it's away).
     */
    internal var scrolled = 0f
    internal var finger by mutableFloatStateOf(finger)

    /** A row of what's shown, [k], seen with its middle [mid] in the window now. */
    internal fun see(k: Int, mid: Float) { mids[k] = mid + scrolled }

    /** `overDrag`: moving down, the last row whose middle the held one's middle has passed; up, the first. */
    fun slot(): Int {
        val mid = mid0 + dy
        var t = from
        for ((k, m) in mids) if (k > from && m < mid && k > t) t = k
        if (t == from) for ((k, m) in mids) if (k < from && m > mid && k < t) t = k
        return t
    }
}

/** Where the row [id] sits in the list's window (its offset from the top), so a finger's moves can be measured on screen. */
private fun LazyListState.rowAt(id: String): Offset = Offset(0f, (layoutInfo.visibleItemsInfo.firstOrNull { it.key == "q:$id" }?.offset ?: 0).toFloat())

/** The held row's 2px ring in your accent (`.row.dragging`'s outline), around the row, not its gap under it. */
private fun Modifier.heldRing(c: Color, r: Dp, gap: Dp) = drawWithContent {
    drawContent()
    val w = 2.dp.toPx()
    drawRoundRect(c, Offset(-w / 2, -w / 2), Size(size.width + w, size.height - gap.toPx() + w), CornerRadius(r.toPx() + w / 2), style = Stroke(w))
}

/**
 * Hold a row or the open card (index.html 8965-8991): 380 ms still (a move over 10 dp is a scroll or a swipe and lets
 * go). In your own order it lifts and follows the finger, the rows it passes step aside, and where it's let go is its
 * new place in the practice order; in any other order the hold opens the game's page instead. Watched in the first
 * pass, so once it is held the finger's moves and lift are taken here: the list doesn't scroll, the row isn't tapped
 * and the views don't swipe. Held near the window's top or bottom, the list scrolls that way (the owner, Nov 5: the
 * web doesn't), faster the closer the finger is to the edge.
 */
private fun Modifier.hold(id: String, i: Int, view: () -> QueueView, pick: QueuePick, list: LazyListState, guard: SwipeGuard, open: (String) -> Unit) =
    // keyed by the row and its place only: a redraw mid-drag (the slot changing) must not restart it and let go
    pointerInput(id, i) {
        coroutineScope {
            val jobs = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                // a finger that stops a fling is the list's: it never holds (review on android-v16)
                if (list.isScrollInProgress) return@awaitEachGesture
                suspend fun AwaitPointerEventScope.drain() {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.forEach { it.consume() }
                        if (e.changes.none { it.pressed }) break
                    }
                }
                // the web lets go after 10 px; here no later than the list's own scroll starts, so a slow scroll never holds
                val slop = minOf(10.dp.toPx(), viewConfiguration.touchSlop)
                // waiting, it watches last (Final): a scroll the list took (consumed), a lift or a move lets go. Measured
                // from the window, since a scrolling list carries the row along under the finger.
                val start = down.position + list.rowAt(id)
                val let = withTimeoutOrNull(HOLD_MS) {
                    while (true) {
                        val c = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed || c.isConsumed || list.isScrollInProgress || (c.position + list.rowAt(id) - start).getDistance() > slop) break
                    }
                    true
                }
                if (let != null || list.isScrollInProgress) return@awaitEachGesture
                val v = view()
                guard.inScroller = true
                // a row swiped open closes when another is held
                pick.swiped = null
                if (v.sort != "manual") {
                    // a hold in a sorted order opens the game's page (`openFull`), and the lift taps nothing
                    open(id)
                    drain()
                    return@awaitEachGesture
                }
                val at = HashMap<Any, Int>(v.shown.size * 2).also { m -> v.shown.forEachIndexed { k, g -> m["q:" + g.id] = k } }
                val seen = list.layoutInfo.visibleItemsInfo.filter { it.key in at }
                val me = seen.firstOrNull { it.key == "q:$id" } ?: return@awaitEachGesture drain()
                val mids = HashMap<Int, Float>(v.shown.size * 2).also { m -> seen.forEach { m[at[it.key]!!] = it.offset + it.size / 2f } }
                val d = QueueDrag(id, i, mids, me.size.toFloat(), me.offset + me.size / 2f, me.offset + down.position.y)
                pick.drag = d
                Sounds.play(Sfx.Nudge)
                Buzz.ms(14)
                val edges = jobs.launch { edgeScroll(d, list, at, EDGE_ZONE.dp.toPx(), EDGE_SLOW.dp.toPx(), EDGE_FAST.dp.toPx()) }
                try {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val c = e.changes.firstOrNull { it.id == down.id }
                        e.changes.forEach { it.consume() }
                        if (c == null || !c.pressed) break
                        val step = c.position.y - c.previousPosition.y
                        d.dy += step
                        d.finger += step
                        val t = d.slot()
                        if (t != d.to) { d.to = t; Sounds.play(Sfx.Swipe) }
                    }
                    // `endDrag`: the shown rows in their new order are numbered 1, 2, 3… (only those shown, as the web)
                    val ids = v.shown.map { it.id }.toMutableList()
                    ids.add(d.to, ids.removeAt(d.from))
                    pick.dropped = id
                    if (pick.movedFor !== v.src) { pick.moved = emptyMap(); pick.movedFor = v.src }
                    pick.moved = pick.moved + ids.mapIndexed { k, x -> x to (k + 1).toDouble() }
                    pick.sort = "manual"
                    Sounds.play(Sfx.Tap)
                } finally {
                    edges.cancel()
                    pick.drag = null
                }
            }
        }
    }

/**
 * While a row is held: a finger within [zone] of the top or bottom of what you see scrolls the list that way each
 * frame, from [slow] px a millisecond where the zone starts to [fast] at the edge (squared, so the last part speeds up
 * most). The edges are the list's inside its padding (the status bar above, the tab bar and pill below), where a finger
 * can reach. What the list scrolled moves the held row along (it stays under the finger), and rows that come on screen
 * are measured for the slot maths. Away from the edges, or at the list's end, it waits for the finger and asks for no
 * frames.
 */
private suspend fun edgeScroll(d: QueueDrag, list: LazyListState, at: Map<Any, Int>, zone: Float, slow: Float, fast: Float) {
    fun push(): Float {
        val info = list.layoutInfo
        val top = info.viewportStartOffset.toFloat() + info.beforeContentPadding
        val bottom = info.viewportEndOffset.toFloat() - info.afterContentPadding
        return when {
            d.finger < top + zone -> -((top + zone - d.finger) / zone).coerceIn(0f, 1f)
            d.finger > bottom - zone -> ((d.finger - (bottom - zone)) / zone).coerceIn(0f, 1f)
            else -> 0f
        }
    }
    // signed like [p]: up the list for the top edge
    fun speed(p: Float) = (slow + (fast - slow) * p * p) * (if (p < 0f) -1f else 1f)
    while (true) {
        snapshotFlow { push() != 0f }.first { it }
        var last = withFrameMillis { it }
        while (true) {
            val p = push()
            if (p == 0f) break
            val t = withFrameMillis { it }
            val ms = (t - last).coerceIn(0L, 32L).toFloat()
            last = t
            val got = list.scrollBy(speed(p) * ms)
            if (got == 0f && ms > 0f) {
                // the list's end: wait for the finger to move
                val at0 = d.finger
                snapshotFlow { d.finger }.first { it != at0 }
                last = withFrameMillis { it }
                continue
            }
            d.scrolled += got
            d.dy += got
            list.layoutInfo.visibleItemsInfo.forEach { it -> at[it.key]?.let { k -> d.see(k, it.offset + it.size / 2f) } }
            val s = d.slot()
            if (s != d.to) { d.to = s; Sounds.play(Sfx.Swipe) }
        }
    }
}

/**
 * Swipe a row (or the open card) left to show its Delete button (`qdel`, index.html 15070-15142): after 10 dp the
 * finger's way decides, sideways (|dx| > 1.3 |dy|) takes the row along, at most 138 aside; let go past half of 92 it
 * stays open at 92, else it slides back. Watched in the first pass, so once sideways the list doesn't scroll, the row
 * isn't tapped and the views don't swipe. One row is open at a time: a touch on another row (or on the open row away
 * from Delete) closes it and taps nothing, a scroll closes it too.
 */
private fun Modifier.swipeDelete(id: String, pick: QueuePick, slide: QueueSlide, guard: SwipeGuard) = pointerInput(id) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val open = DEL_OPEN.dp.toPx()
        val wasOpen = pick.swiped == id
        // the Delete button under the open row takes its own tap
        if (wasOpen && down.position.x > size.width - open) return@awaitEachGesture
        // another row is open: this touch closes it, and its lift taps nothing
        val eat = pick.swiped != null
        if (eat && !wasOpen) pick.swiped = null
        val slop = 10.dp.toPx()
        val base = if (wasOpen) -open else 0f
        var sliding = false
        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    if (sliding || eat) c.consume()
                    if (sliding) pick.swiped = if (slide.x < -open / 2) id else if (pick.swiped == id) null else pick.swiped
                    else if (wasOpen) pick.swiped = null
                    break
                }
                // a hold took the finger (it lifted the row): leave it
                if (!sliding && c.isConsumed) break
                val way = c.position - down.position
                if (!sliding) {
                    if (way.getDistance() < slop) continue
                    if (abs(way.x) <= 1.3f * abs(way.y)) {
                        // a scroll: the list takes it (and closes this row if it was open)
                        if (wasOpen) pick.swiped = null
                        break
                    }
                    sliding = true
                    slide.held = true
                    guard.inScroller = true
                }
                slide.x = (base + way.x).coerceIn(-DEL_MAX.dp.toPx(), 0f)
                c.consume()
            }
        } finally {
            slide.held = false
        }
    }
}

@Composable
private fun Empty(text: String, mod: Modifier) {
    Box(mod.padding(top = 4.dp).fillMaxWidth().background(Pn.pan, Pn.r).border(1.dp, Pn.line, Pn.r).padding(18.dp)) {
        BasicText(text, style = Type.note)
    }
}

// ---------------------------------------------------------------- the bar (Option B, Nov 6: one row of chips + Sort)

/**
 * The Queue's bar (the owner's Option B, Nov 6): one row of pill chips, All · Out · Coming · Played (and your lists)
 * with their live counts, the lit one filled in your accent, the rest dark with a hairline. The row never wraps: when
 * it runs out of room it scrolls sideways (a touch on it is its own, not a view swipe). At its end one Sort button
 * opens a small menu with the orders, Shuffle and Boss (the old Order button, Shuffle and Boss, moved in there).
 */
@Composable
private fun QueueBar(v: QueueView, pick: QueuePick, mod: Modifier, sheet: (String) -> Unit) {
    val q = v.q
    val tabs = Queue.TABS + q.lists.map { "L:" + it.id }
    val guard = LocalSwipeGuard.current
    val scroll = rememberScrollState()
    // each chip's left edge and width in the row (px), for bringing the lit one into view
    val spots = remember { HashMap<String, IntArray>() }
    val calm = LocalCalm.current
    val air = with(LocalDensity.current) { 16.dp.roundToPx() }
    // the lit chip is always in sight: one lit far along the row (a list shown from Your lists, or the tab kept from
    // before) scrolls the row to it; a chip already on screen leaves the row where it is
    LaunchedEffect(v.tab) {
        withFrameNanos { }
        val at = spots[v.tab] ?: return@LaunchedEffect
        val view = scroll.viewportSize
        if (view <= 0) return@LaunchedEffect
        val to = when {
            at[0] < scroll.value -> at[0] - air
            at[0] + at[1] > scroll.value + view -> at[0] + at[1] - view + air
            else -> return@LaunchedEffect
        }.coerceIn(0, scroll.maxValue)
        if (calm) scroll.scrollTo(to) else scroll.animateScrollTo(to)
    }
    // Boss (`#fx-boss`): the Shadow's HP only while Grumble is on (v286, v302)
    val hp = if (Backup.truthy(q.lib["grum"])) q.boss else null
    Row(mod.padding(top = 6.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .weight(1f)
                .pointerInput(scroll) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (scroll.maxValue > 0 && e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true
                        }
                    }
                }
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (t in tabs) {
                FilterChip(q.tabName(t), q.counts[t] ?: 0, t == v.tab, Modifier.onPlaced { c -> spots[t] = intArrayOf(c.positionInParent().x.toInt(), c.size.width) }) {
                    if (t != pick.tab) {
                        Sounds.play(Sfx.Tap)
                        Crumbs.tap("queue tab", "games/queue")
                        pick.tab = t
                    }
                }
            }
        }
        SortButton(v, pick, hp, sheet)
    }
}

/** One chip: "Out 26", 36 tall inside a 48 touch; lit = filled with your accent, else `--u-card` with a hairline. */
@Composable
private fun FilterChip(name: String, n: Int, on: Boolean, mod: Modifier, tap: () -> Unit) {
    val volt = LocalLook.current.volt
    Box(
        mod.height(Pn.touch)
            .semantics { contentDescription = "$name, $n"; selected = on }
            .clickable(role = Role.Tab, onClick = tap),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(CHIP_H)
                .then(if (on) Modifier.background(volt, Pn.pill) else Modifier.background(Pn.card, Pn.pill).border(1.dp, Pn.line2, Pn.pill))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(name, style = QType.chip.copy(color = if (on) Pn.void else Pn.ice).fixed(), maxLines = 1, softWrap = false)
            BasicText(n.toString(), style = QType.count.copy(color = if (on) Pn.void.copy(alpha = .72f) else Pn.mute).fixed(), maxLines = 1, softWrap = false)
        }
    }
}

/** The chips' and Sort's height (inside their 48 touch). */
private val CHIP_H = 36.dp

/**
 * Sort: a sliders icon and "Sort", as tall as the chips (its line in your accent while the Queue isn't in your own
 * order). A tap opens [SortMenu] under it; a tap outside closes it.
 */
@Composable
private fun SortButton(v: QueueView, pick: QueuePick, hp: Pair<Double, Double>?, sheet: (String) -> Unit) {
    val volt = LocalLook.current.volt
    val sorted = v.sort != "manual"
    var open by remember { mutableStateOf(false) }
    val d = LocalDensity.current
    // from the menu's top to the window's foot (px): the menu ends above the taskbar's room, not in it
    var below by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val foot = maxOf(LocalBottomRoom.current, with(d) { WindowInsets.safeDrawing.getBottom(d).toDp() })
    Box(
        Modifier.onGloballyPositioned { c ->
            val top = c.positionInRoot().y + with(d) { (Pn.touch + 2.dp).toPx() }
            below = (c.findRootCoordinates().size.height - top).toInt()
        },
    ) {
        Box(
            Modifier.height(Pn.touch)
                .semantics { contentDescription = "Sort: " + Queue.sortName(v.sort) }
                .clickable(role = Role.Button) {
                    Sounds.play(Sfx.Tap)
                    Crumbs.tap("sort", "games/queue")
                    open = !open
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(CHIP_H)
                    .background(Pn.card, Pn.pill).border(1.dp, if (sorted) volt.copy(alpha = .6f) else Pn.line2, Pn.pill)
                    .padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QdIcon("sort", if (sorted) volt else Pn.ice, 18.dp)
                BasicText("Sort", style = QType.chip.copy(color = Pn.ice).fixed(), maxLines = 1, softWrap = false)
            }
        }
        if (open) {
            val room = if (below == Int.MAX_VALUE) Dp.Infinity else with(d) { below.toDp() } - foot - 12.dp
            Popup(
                alignment = Alignment.TopEnd,
                // the menu sits [MENU_SHADE] in from the popup's edges, so its shadow fits inside the popup's window
                // (it was cut off flat at the menu's edges); the offset puts the menu itself where it was
                offset = with(d) { IntOffset(MENU_SHADE.roundToPx(), (Pn.touch + 2.dp - MENU_SHADE).roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val n = hp?.first?.let { Backup.jsNumber(it).content }
                SortMenu(
                    v.sort, n, room,
                    choose = { k -> open = false; if (k != pick.sort) pick.sort = k },
                    lists = {
                        open = false
                        Crumbs.tap("lists", "games/queue")
                        sheet("lists")
                    },
                    shuffle = {
                        open = false
                        Crumbs.tap("shuffle", "games/queue")
                        // nothing to pick from: only the line, no sound
                        if (pick.shuffle(v.q) != null) Sounds.play(Sfx.Nudge)
                    },
                    deck = {
                        open = false
                        Crumbs.tap("game shuffler", "games/queue")
                        sheet("shuffle")
                    },
                    boss = {
                        open = false
                        Sounds.play(Sfx.Tap)
                        Crumbs.tap("boss", "games/queue")
                        // the Boss screen comes with Phase 9 (the old button only showed): a line says so, never a dead tap
                        pick.say(BOSS_SOON)
                    },
                )
            }
        }
    }
}

/** The room round Sort's menu inside its popup for the menu's 20 shadow to fade out in. */
private val MENU_SHADE = 24.dp

/** What Boss mode says until its screen is built (Phase 9). */
internal const val BOSS_SOON = "Boss mode's screen comes in a later update"

/**
 * Sort's menu: the orders in their three groups (`QD_GROUPS`), the one in use ticked; a line; "Your lists" (the
 * owner, Nov 6: "do whatever", the PR's suggested home for the old "+", the only way into the lists sheet); "Shuffle the queue";
 * "Game shuffler" (the web's hold on Shuffle: the deck, ui/Shuffle.kt);
 * "Boss mode" (the only red thing, with the Shadow's HP while Grumble is on). On a short screen the orders scroll and
 * Shuffle and Boss stay in view.
 */
@Composable
private fun SortMenu(sort: String, hp: String?, room: Dp, choose: (String) -> Unit, lists: () -> Unit, shuffle: () -> Unit, deck: () -> Unit, boss: () -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(18.dp)
    val tall = LocalConfiguration.current.screenHeightDp.dp
    // at most 62% of the screen, and never past the taskbar's room under Sort ([room]); 160 at the least
    val most = minOf(maxOf(240.dp, tall * .62f), maxOf(160.dp, room))
    Column(
        Modifier.padding(MENU_SHADE).width(264.dp).heightIn(max = most)
            .shadow(20.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
            .background(Pn.card, shape).border(1.dp, Pn.line2, shape).padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            for ((head, keys) in Queue.GROUPS) {
                BasicText(head, style = QType.group.fixed(), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp))
                for (k in keys) {
                    val on = k == sort
                    MenuRow(Queue.sortName(k), Modifier.semantics { selected = on }, Role.RadioButton, { choose(k) }) {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { if (on) QdIcon("tick", volt, 20.dp) }
                        BasicText(Queue.sortName(k), style = QType.option.copy(color = if (on) Pn.ice else Pn.ice.copy(alpha = .86f)).fixed(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Pn.line2))
        MenuRow("Your lists", Modifier, Role.Button, lists) {
            QdIcon("list", volt, 20.dp)
            BasicText("Your lists", style = QType.option.fixed(), maxLines = 1, softWrap = false)
        }
        MenuRow("Shuffle the queue", Modifier, Role.Button, shuffle) {
            QdIcon("shuf", volt, 20.dp)
            BasicText("Shuffle the queue", style = QType.option.fixed(), maxLines = 1, softWrap = false)
        }
        MenuRow("Game shuffler", Modifier, Role.Button, deck) {
            QdIcon("deck", volt, 20.dp)
            BasicText("Game shuffler", style = QType.option.fixed(), maxLines = 1, softWrap = false)
        }
        MenuRow("Boss mode" + (hp?.let { ", $it HP" } ?: ""), Modifier, Role.Button, boss) {
            QdIcon("boss", Pn.hot, 20.dp)
            BasicText("Boss mode", style = QType.option.copy(color = Pn.hot).fixed(), maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (hp != null) BasicText("$hp HP", style = QType.hp.copy(color = Pn.hot.copy(alpha = .8f)).fixed(), maxLines = 1, softWrap = false)
        }
    }
}

/** One line of Sort's menu: 48 tall, its icon then its words. */
@Composable
private fun MenuRow(label: String, modifier: Modifier, role: Role, tap: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(Pn.touch)
            .semantics { contentDescription = label }
            .clickable(role = role, onClick = tap)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- On deck (v259 photo cards, v303)

@Composable
private fun OnDeck(q: Queue, tab: String, deck: List<Game>, mod: Modifier, open: (String) -> Unit) {
    val guard = LocalSwipeGuard.current
    Column(mod.padding(bottom = 4.dp)) {
        BasicText(q.deckHead(tab), style = QType.deckHead.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 700.dp) {
                Row(Modifier.padding(top = 8.dp, bottom = 12.dp, start = 2.dp, end = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    deck.forEachIndexed { i, g -> DeckCard(q, g, i, Modifier.weight(1f), open) }
                }
            } else {
                val state = rememberLazyListState()
                val w = min(maxWidth.value * .76f, 300f).dp
                LazyRow(
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true
                            }
                        }
                    },
                    state = state,
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    flingBehavior = rememberSnapFlingBehavior(state),
                ) {
                    items(deck.size, key = { deck[it].id }) { i -> DeckCard(q, deck[i], i, Modifier.width(w), open) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckCard(q: Queue, g: Game, i: Int, modifier: Modifier, open: (String) -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier.height(410.dp).background(Pn.pan, shape).border(1.dp, Color.White.copy(alpha = .06f), shape)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button) { open(g.id) }.padding(8.dp),
    ) {
        PicBox(Pic.backdrop(g) ?: Pic.cover(g), g, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)), ground = QTone.photo)
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(g.title, style = QType.deckTitle.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(q.deckWhy(g), style = QType.deckWhy.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for ((lead, value) in q.deckMeta(g)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (lead.isNotEmpty()) BasicText(lead, style = QType.deckMeta.copy(color = Pn.ice.copy(alpha = .65f)).fixed(), maxLines = 1)
                        BasicText(value, style = QType.deckMeta.fixed(), maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(46.dp).background(volt, RoundedCornerShape(23.dp)), contentAlignment = Alignment.Center) {
                    BasicText(if (i == 0) "Play this next" else "Open", style = QType.deckBtn.fixed(), maxLines = 1)
                }
                Box(Modifier.size(46.dp).border(1.dp, Color.White.copy(alpha = .18f), Pn.pill), contentAlignment = Alignment.Center) {
                    BasicText("#${i + 1}", style = QType.bold.copy(fontSize = 14.sp).fixed(), maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- a closed row (`row()`, CSS 27366-27440)

@Composable
private fun QueueRow(q: Queue, g: Game, n: Int, sort: String, folded: Boolean, mod: Modifier, open: (String) -> Unit) {
    val lib = q.lib
    val accent = Color(g.colour)
    val volt = LocalLook.current.volt
    val st = remember(g, q) { q.stat(g) }
    val prog = remember(g, q, st) { q.progress(g, st) }
    val dust = remember(g, q) { q.dustLevel(g) }
    val tone = stateTone(st.kind, volt)
    val art = Backup.str(g["art"]).takeIf { it.isNotEmpty() && !lib.off("rowArt") }
    val pic = remember(g, lib) { rowPic(g, lib) }
    val shape = RoundedCornerShape(if (folded) 20.dp else 22.dp)
    val ring = if (st.kind == Queue.Kind.Playing) mixA(accent, Pn.ice, .8f) to 1.5.dp else accent.copy(alpha = .34f) to 1.dp
    BoxWithConstraints(
        mod.padding(bottom = 12.dp).fillMaxWidth().height(if (folded) 104.dp else 138.dp).clip(shape)
            .then(if (art == null) Modifier.background(Pn.pan) else Modifier)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button) { open(g.id) },
    ) {
        val rowW = maxWidth
        // the game's picture from 28% (folded 34%) across, under a veil that keeps the left side near-black
        if (pic != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth().padding(start = rowW * (if (folded) .34f else .28f)).background(Pn.void)) {
                val bx = Backup.num(g["bgX"], 100.0, 0.0, 100.0).toFloat() / 50 - 1
                val by = Backup.num(g["bgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
                PicBox(pic, g, Modifier.fillMaxSize(), align = BiasAlignment(bx, by))
            }
            val played = st.kind == Queue.Kind.Played
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        *(if (played) arrayOf(
                            .22f to Pn.void, .40f to mixA(accent, QTone.shade.copy(alpha = .93f), .18f),
                            .66f to QTone.shade.copy(alpha = .74f), 1f to QTone.shade.copy(alpha = .62f),
                        ) else arrayOf(
                            .22f to Pn.void, .40f to mixA(accent, QTone.shade.copy(alpha = .9f), .34f),
                            .64f to mixA(accent, QTone.shade.copy(alpha = .62f), .14f), .78f to QTone.shade.copy(alpha = .5f),
                            1f to QTone.shadeDeep.copy(alpha = .66f),
                        )),
                    ),
                ),
            )
        }
        Strap(accent, folded)
        if (art != null) LogoSlot(art, rowW, folded, lib)
        // the content: spot, box art, (gutter), words, ticket
        val slot = if (art != null) logoSlotW(rowW, folded, lib) + 22.dp else 16.dp
        Row(
            Modifier.fillMaxSize().padding(start = if (folded) 12.dp else 20.dp, end = slot, top = if (folded) 9.dp else 10.dp, bottom = if (folded) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                n.toString().padStart(2, '0'),
                style = (if (folded) QType.spotF else QType.spot).fixed(),
                maxLines = 1, softWrap = false,
                modifier = Modifier.width(if (folded) 26.dp else 44.dp),
            )
            Spacer(Modifier.width(if (folded) 8.dp else 12.dp))
            val cw = if (folded) 46.dp else 58.dp
            val ch = if (folded) 68.dp else 86.dp
            val cs = RoundedCornerShape(if (folded) 8.dp else 10.dp)
            Box(Modifier.size(cw, ch).border(2.dp, Color.White.copy(alpha = .22f), cs).padding(0.dp).clip(cs)) {
                val ix = Backup.num(g["imgX"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
                val iy = Backup.num(g["imgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
                PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), align = BiasAlignment(ix, iy), ground = Color(Web.COVER_BG))
            }
            Spacer(Modifier.width(if (folded) 34.dp else 54.dp)) // the empty gutter column and its two gaps
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                if (!folded) {
                    val kick = if (n == 1 && st.kind != Queue.Kind.Playing && st.kind != Queue.Kind.Played) "Up next" else st.word
                    BasicText(kick, style = QType.kicker.copy(color = mixOk(accent, Color.White, .45f)).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                WholeName(
                    g.title, (if (folded) QType.nameF else QType.name).fixed(),
                    Modifier.padding(top = if (folded) 0.dp else 2.dp).then(if (folded) Modifier else Modifier.heightIn(min = 48.dp)),
                )
                Row(Modifier.padding(top = if (folded) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (folded) 8.dp else 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(14.dp).background(tone.copy(alpha = .24f), Pn.pill).padding(3.dp).background(tone, Pn.pill))
                        BasicText(st.head, style = (if (folded) QType.metaF else QType.meta).fixed(), maxLines = 1, softWrap = false)
                    }
                    if (folded && g.date.isNotEmpty()) {
                        val day = q.shortDate(g.date)
                        if (day.isNotEmpty()) BasicText(
                            day,
                            style = QType.metaF.copy(color = QTone.pale.copy(alpha = .72f), textDecoration = if (q.lateDate(g)) TextDecoration.LineThrough else null).fixed(),
                            maxLines = 1, softWrap = false,
                        )
                    }
                    q.sortValue(g, sort)?.let { SortPill(Queue.sortName(sort), it, folded) }
                }
            }
            if (!folded) Ticket(st, accent, Modifier.padding(start = 18.dp))
        }
        // the line along the foot: how far along (played full, coming how close, dashed)
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .07f)).drawBehind {
                val w = size.width * prog
                if (w <= 0f) return@drawBehind
                if (st.kind == Queue.Kind.Coming) {
                    var x = 0f
                    val dash = 8.dp.toPx()
                    val gap = 5.dp.toPx()
                    while (x < w) {
                        drawRect(tone, Offset(x, 0f), Size(min(dash, w - x), size.height))
                        x += dash + gap
                    }
                } else {
                    drawRect(Brush.horizontalGradient(listOf(mixOk(accent, Color.White, .7f), tone), 0f, w), size = Size(w, size.height))
                }
            },
        )
        // the ring (inset), and the dust over everything
        Box(Modifier.fillMaxSize().border(ring.second, ring.first, shape))
        if (dust > 0) Dust(dust)
    }
}

/** `qsPic(g)`: the row's (and the open card's) picture, never the logo; none when pictures are off. */
internal fun rowPic(g: Game, lib: Library): Pic? {
    if (Backup.str(lib["cardBg"]) == "plain" || Backup.truthy(g["bgOff"])) return null
    Backup.str(g["hero"]).ifEmpty { null }?.let { return Pic.Url(it) }
    if (Backup.truthy(g["steamId"])) return Pic.Url(Web.STEAM_CDN + Backup.str(g["steamId"]) + "/library_hero.jpg")
    Backup.str(g["bg"]).ifEmpty { null }?.let { return Pic.Url(it) }
    return Pic.cover(g)
}

@Composable
private fun SortPill(name: String, value: String, folded: Boolean) {
    val volt = LocalLook.current.volt
    Row(
        Modifier.background(volt.copy(alpha = .18f), Pn.pill).padding(horizontal = 9.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!folded) BasicText(name, style = QType.pillName.fixed(), maxLines = 1, softWrap = false)
        BasicText(value, style = QType.pillValue.fixed(), maxLines = 1, softWrap = false)
    }
}

/** The slanted strap in the game's colour (`.qsstrap` and its lighter stripe): the slant is only on the strap. */
@Composable
private fun Strap(accent: Color, folded: Boolean) {
    val dark = mixOk(accent, Color.Black, .6f)
    val mid = mixOk(accent, Color.Black, .88f)
    val stripe = mixOk(accent, Color.White, .55f).copy(alpha = .85f)
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val k = tan(Math.toRadians(if (folded) 9.0 else 15.0)).toFloat()
            val h = size.height
            val left = (if (folded) -30 else -44).dp.toPx()
            val w = (if (folded) 140 else 220).dp.toPx()
            fun band(x0: Float, x1: Float) = Path().apply {
                moveTo(x0, 0f); lineTo(x1, 0f); lineTo(x1 - k * h, h); lineTo(x0 - k * h, h); close()
            }
            val strap = band(left, left + w)
            val sw = (if (folded) 4 else 6).dp.toPx()
            val sx = left + w - 3.dp.toPx() - sw
            val st = band(sx, sx + sw)
            val fill = Brush.horizontalGradient(.2f to dark, .92f to mid, startX = left, endX = left + w)
            onDrawBehind {
                drawPath(strap, fill)
                drawPath(st, stripe)
            }
        },
    )
}

/** The ticket (`.qstk`): the stub and the body side by side; a released game's stub hangs torn. */
@Composable
private fun Ticket(st: Queue.Stat, accent: Color, modifier: Modifier) {
    val fill = mixA(accent, QTone.ticket.copy(alpha = .88f), .26f)
    val edge = accent.copy(alpha = .55f)
    val perf = mixA(accent, Color.White.copy(alpha = .35f), .5f)
    Row(modifier.height(62.dp)) {
        val stubShape = if (st.torn) RoundedCornerShape(12.dp, 2.dp, 4.dp, 12.dp) else RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp)
        TicketCell(
            st.stub, fill,
            Modifier.graphicsLayer {
                if (st.torn) {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    translationX = -5.dp.toPx()
                    translationY = 4.dp.toPx()
                    rotationZ = -6f
                }
            }.background(fill, stubShape).border(1.dp, edge, stubShape),
        )
        val bodyShape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)
        TicketCell(
            st.body, fill,
            Modifier.background(fill, bodyShape).border(1.dp, edge, bodyShape).drawBehind {
                val w = 2.dp.toPx()
                drawLine(perf, Offset(w / 2, 0f), Offset(w / 2, size.height), w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 2, w * 2)))
            },
        )
    }
}

@Composable
private fun TicketCell(v: Pair<String, String>, fill: Color, modifier: Modifier) {
    Column(modifier.fillMaxHeight().widthIn(min = 72.dp).padding(horizontal = 13.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(v.first, style = QType.ticketValue.fixed(), maxLines = 1, softWrap = false)
        BasicText(v.second, style = QType.ticketLabel.fixed(), maxLines = 1, softWrap = false)
    }
}

/** The Dusty mark (`DUST_BASE`): two grids of dots and a veil; level 3 adds a cobweb in the corner. */
@Composable
private fun Dust(level: Int) {
    Box(
        Modifier.fillMaxSize().alpha(when (level) { 1 -> .5f; 2 -> .8f; else -> 1f }).drawBehind {
            drawRect(Brush.verticalGradient(listOf(QTone.dustVeil.copy(alpha = .10f), QTone.dustVeil.copy(alpha = .04f))))
            fun grid(tw: Float, th: Float, ox: Float, oy: Float, r: Float, a: Float) {
                val c = QTone.dust.copy(alpha = a)
                var y = oy.dp.toPx()
                while (y < size.height) {
                    var x = ox.dp.toPx()
                    while (x < size.width) {
                        drawCircle(c, r.dp.toPx(), Offset(x, y))
                        x += tw.dp.toPx()
                    }
                    y += th.dp.toPx()
                }
            }
            // the CSS tiles put each dot at the tile's middle, offset by the background position
            grid(23f, 19f, 3f + 11.5f, 5f + 9.5f, 1.05f, .55f)
            grid(13f, 17f, 9f + 6.5f, 2f + 8.5f, .85f, .4f)
            if (level == 3) {
                val s = 15.dp.toPx()
                val x0 = size.width - 10.dp.toPx() - s
                val y0 = 2.dp.toPx()
                val web = QTone.dust.copy(alpha = .8f)
                val stroke = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                // a corner cobweb: spokes from the top-right corner and two arcs across them (drawn, no emoji: v297)
                val c = Offset(x0 + s, y0)
                for (a in listOf(90f, 120f, 150f, 180f)) {
                    val r = Math.toRadians(a.toDouble())
                    drawLine(web, c, Offset(c.x + (s * Math.cos(r)).toFloat(), c.y + (s * Math.sin(r)).toFloat()), stroke.width)
                }
                for (rr in listOf(.45f, .8f)) drawArc(web, 90f, 90f, false, Offset(c.x - s * rr, c.y - s * rr), Size(2 * s * rr, 2 * s * rr), style = stroke)
            }
        },
    )
}

// ---------------------------------------------------------------- the logo slot (v290, `lfRule`)

/** `--rslot`: min(28% (folded 27%) of the row, 1.4 × Row logo width (150)). */
private fun logoSlotW(rowW: Dp, folded: Boolean, lib: Library): Dp =
    min(rowW.value * (if (folded) .27f else .28f), 1.4f * Backup.num(lib["rowArtW"], 150.0, 40.0, 200.0).toFloat()).dp

/** A logo's ink, measured once per address: the box its visible pixels fill (with the .02 guard) and whether it's near-black. */
class Ink(val aspect: Float, val dark: Boolean)

internal object LogoInk {
    val known = ConcurrentHashMap<String, Ink>()
}

/**
 * Crops a logo to its ink (alpha > 24, the 2% guard around it) and paints a near-black one light (`brightness(0)
 * invert(.9)`: every visible pixel 230, alpha kept), measured on a copy at most 200 wide like `lfMeasure`.
 */
internal class InkCrop(private val url: String) : Transformation() {
    override val cacheKey = "ink:v290:$url"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val k = min(1f, 200f / max(input.width, input.height))
        val sw = max(1, (input.width * k).toInt())
        val sh = max(1, (input.height * k).toInt())
        val small = Bitmap.createScaledBitmap(input, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        var x0 = sw
        var y0 = sh
        var x1 = -1
        var y1 = -1
        var lum = 0.0
        var lumN = 0
        for (y in 0 until sh) for (x in 0 until sw) {
            val c = px[y * sw + x]
            val a = c ushr 24
            if (a > 24) {
                if (x < x0) x0 = x; if (y < y0) y0 = y; if (x > x1) x1 = x; if (y > y1) y1 = y
            }
            if (a > 128) {
                lum += .2126 * ((c shr 16) and 255) + .7152 * ((c shr 8) and 255) + .0722 * (c and 255)
                lumN++
            }
        }
        val dark = lumN > 0 && Math.round(lum / lumN) < 50
        val g = .02f
        val fx0 = if (x1 < 0) 0f else max(0f, x0.toFloat() / sw - g)
        val fy0 = if (x1 < 0) 0f else max(0f, y0.toFloat() / sh - g)
        val fx1 = if (x1 < 0) 1f else min(1f, (x1 + 1f) / sw + g)
        val fy1 = if (x1 < 0) 1f else min(1f, (y1 + 1f) / sh + g)
        val cx = (fx0 * input.width).toInt()
        val cy = (fy0 * input.height).toInt()
        val cw = max(1, ((fx1 - fx0) * input.width).toInt().coerceAtMost(input.width - cx))
        val chh = max(1, ((fy1 - fy0) * input.height).toInt().coerceAtMost(input.height - cy))
        val out = Bitmap.createBitmap(input, cx, cy, cw, chh)
        LogoInk.known[url] = Ink(cw.toFloat() / chh, dark)
        return out
    }
}

internal val SILHOUETTE = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 230f, 0f, 0f, 0f, 0f, 230f, 0f, 0f, 0f, 0f, 230f, 0f, 0f, 0f, 1f, 0f)),
)

@Composable
private fun LogoSlot(url: String, rowW: Dp, folded: Boolean, lib: Library) {
    val slotW = logoSlotW(rowW, folded, lib)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val req = remember(url) { ImageRequest.Builder(ctx).data(picUrl(url)).allowHardware(false).transformations(InkCrop(url)).build() }
    var ink by remember(url) { mutableStateOf(LogoInk.known[url]) }
    val op = Backup.num(lib["rowArtOp"], 95.0, 10.0, 100.0).toFloat() / 100
    Box(
        Modifier.fillMaxSize().padding(end = 14.dp, top = 8.dp, bottom = 8.dp).alpha(op),
        contentAlignment = Alignment.CenterEnd,
    ) {
        BoxWithConstraints(Modifier.width(slotW).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            // lfRule: every logo the same ink area (26% of the slot), at least 30% of its height, at most 86% / the slot width
            val i = ink
            val mod = if (i == null) Modifier.fillMaxSize() else {
                val wv = maxWidth.value
                val hv = maxHeight.value
                val area = wv * hv * .26f
                var hc = max(sqrt(area / i.aspect), hv * .3f)
                var wc = i.aspect * hc
                if (hc > hv * .86f) { hc = hv * .86f; wc = i.aspect * hc }
                if (wc > wv) { wc = wv; hc = wc / i.aspect }
                Modifier.size(wc.dp, hc.dp)
            }
            AsyncImage(
                model = req, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd,
                colorFilter = if (i?.dark == true) SILHOUETTE else null,
                onSuccess = { ink = LogoInk.known[url] },
                modifier = mod,
            )
        }
    }
}

// ---------------------------------------------------------------- icons

/** `QD_IC` (index.html 8769): the bar's 24x24 icons, in the v247 style (one part at .35). */
private val QD_IC = mapOf(
    // Sort's sliders and the menu's tick (Android's own, in the same style: one part at .35)
    "sort" to listOf("M3 5.8h18v2.4H3zM3 10.8h18v2.4H3zM3 15.8h18v2.4H3z" to .35f, "M6 3.5h3.2v7H6zM14.8 8.5H18v7h-3.2zM9 13.5h3.2v7H9z" to 1f),
    "tick" to listOf("M9.6 15.6 5.4 11.4 3.7 13.1l5.9 5.9L20.3 8.3l-1.7-1.7z" to 1f),
    // Your lists (the old lists button's mark: a box with a plus)
    "list" to listOf("M4 5h16v14H4z" to .35f, "M11 8h2v3h3v2h-3v3h-2v-3H8v-2h3z" to 1f),
    // the Game shuffler: a card behind a card (Android's own, same style)
    "deck" to listOf("M9 3.5h9.5A1.5 1.5 0 0 1 20 5v12a1.5 1.5 0 0 1-1.5 1.5H9A1.5 1.5 0 0 1 7.5 17V5A1.5 1.5 0 0 1 9 3.5z" to .35f, "M5.5 6.5H15A1.5 1.5 0 0 1 16.5 8v12a1.5 1.5 0 0 1-1.5 1.5H5.5A1.5 1.5 0 0 1 4 20V8a1.5 1.5 0 0 1 1.5-1.5z" to 1f),
    "shuf" to listOf(
        "M3 6.5h4.2l9 11H21v-2.4h-3.6l-9-11H3z" to .35f,
        "M16.2 4.1h3.6L21 2.6v6l-1.2-1.4h-3.6l-2.6 3.1-1.6-1.9zM3 15.1h4.2l2.6-3.1 1.6 1.9-3.2 3.6H3z" to 1f,
    ),
    "boss" to listOf(
        "M12 6.5c3.9 0 7 3 7 6.8v2.4c0 1.4-1.1 2.5-2.5 2.5H15v2.3H9v-2.3H7.5C6.1 18.2 5 17.1 5 15.7v-2.4c0-3.8 3.1-6.8 7-6.8z" to .35f,
        "M3.6 2.8c-.6 3.3.4 5.7 3.3 6.8l-1 1.7C2.6 9.8 1.9 6.4 3.6 2.8zm16.8 0c1.7 3.6 1 7-2.3 8.5l-1-1.7c2.9-1.1 3.9-3.5 3.3-6.8zM8.2 12.4h3v2.6h-3zM12.8 12.4h3v2.6h-3z" to 1f,
    ),
)
private val qdVectors = HashMap<String, ImageVector>()

@Composable
private fun QdIcon(name: String, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val v = remember(name) {
        qdVectors.getOrPut(name) {
            ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
                for ((d, a) in QD_IC.getValue(name)) addPath(PathParser().parsePathString(d).toNodes(), PathFillType.NonZero, fill = SolidColor(Color.Black), fillAlpha = a)
            }.build()
        }
    }
    Image(rememberVectorPainter(v), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

// ---------------------------------------------------------------- type (the web's px = sp)

private object QType {
    private val base = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)

    /** The Delete button under a swiped row (`.qdel`: 14, .04em, Poppins Bold: it has no 800; sentence case, not the web's capitals). */
    val del = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em)
    /** A bar chip's name (and Sort's word). */
    val chip = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val count = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val bold = base.copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
    val hp = base.copy(fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = Pn.mute)
    val group = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute)
    val option = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val deckHead = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute, letterSpacing = .08.em)
    val deckTitle = base.copy(fontSize = 26.sp, lineHeight = 27.3.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.02).em)
    val deckWhy = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Pn.ice.copy(alpha = .72f))
    val deckMeta = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val deckBtn = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = QTone.onVolt)
    val spot = base.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.04).em, fontFeatureSettings = "tnum",
        shadow = Shadow(Pn.shade.copy(alpha = .45f), Offset(0f, 2f), 8f))
    val spotF = spot.copy(fontSize = 19.sp, lineHeight = 22.sp)
    val kicker = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val name = base.copy(fontSize = 19.sp, lineHeight = 22.8.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.015).em, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val nameF = name.copy(fontSize = 15.5.sp, lineHeight = 18.6.sp)
    val meta = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val metaF = meta.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
    val pillName = base.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    val pillValue = base.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val ticketValue = base.copy(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val ticketLabel = base.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = QTone.pale.copy(alpha = .62f))
}

/**
 * A closed row's name in at most two lines that never breaks inside a word (the owner's screenshot, Nov 6: a long
 * one-word name split mid-word; the web keeps short things whole too): when its longest word is wider than the
 * line, the name shrinks until that word fits, to [NAME_LEAST] of its size at the least.
 */
@Composable
private fun WholeName(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val wpx = constraints.maxWidth
        val sized = remember(text, style, wpx) {
            val longest = if (wpx == Constraints.Infinity) 0 else text.split(' ').filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
            if (longest <= wpx) style else {
                val k = (wpx * .98f / longest).coerceAtLeast(NAME_LEAST)
                style.copy(fontSize = style.fontSize * k, lineHeight = style.lineHeight * k)
            }
        }
        BasicText(text, style = sized, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** How small [WholeName] may shrink a name so its longest word stays whole. */
private const val NAME_LEAST = .72f
