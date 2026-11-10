package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Character select (v301, the owner's second look for closed games, Settings › Queue cards › Closed games; `S.qLook`
 * "select"): the picked game in a big panel (its picture, logo, colour and three fighter-style bars) over a grid of
 * covers with a P1 marker on the picked one. A tap on another cover moves the panel to it; a tap on the picked one (or
 * on the panel) opens its card in the panel's place; with a card open a tap on any cover opens that game's, and on the
 * open one closes it (`qfTap`, index.html 8845). The panel's box art opens the game page.
 */
internal fun Library.charSelect(): Boolean = Backup.str(this["qLook"]) == "select"

/** The grid's columns for a page [inner] wide (`repeat(auto-fill, minmax(86px, 1fr))`, 10 between). */
internal fun selCols(inner: Dp): Int = maxOf(1, ((inner.value + SEL_GAP) / (SEL_MIN + SEL_GAP)).toInt())

private const val SEL_MIN = 86f
private const val SEL_GAP = 10f

/** The picked game: the one you last tapped if it's still shown, else the first. */
internal fun QueuePick.selIn(shown: List<Game>): String? = sel?.takeIf { id -> shown.any { it.id == id } } ?: shown.firstOrNull()?.id

/** `qfTap(id)`: a tap on a cover (or the panel, [id] = the picked one). */
internal fun QueuePick.tapCover(id: String, shown: List<Game>) {
    val open = openIn(shown)
    when {
        open == id -> { Sounds.play(Sfx.Back); close(); selSwap++ }
        open != null || id == selIn(shown) -> open(id) // the card plays its own entrance and sound
        else -> { Sounds.play(Sfx.Tap); selSwap++ }
    }
    sel = id
}

/** The panel (or the open card) and the grid, in place of the rows; each line of covers is its own lazy item. */
internal fun LazyListScope.selectItems(v: QueueView, pick: QueuePick, cols: Int, folded: Boolean, calm: Boolean, mod: Modifier, open: (String) -> Unit) {
    val q = v.q
    val shown = v.shown
    val openId = pick.openIn(shown)
    val selId = openId ?: pick.selIn(shown) ?: return
    val at = shown.indexOfFirst { it.id == selId }
    val g = shown[at]
    item("q-sel") {
        if (openId != null) {
            // one card per game (Phase 1 check A2): another cover tapped while this card waits out its one-tap close
            // takes the wait with it, so the new card never snaps shut
            key(g.id) {
                QueueCard(q, g, at + 1, pick.fresh == g.id, pick.flip == g.id, mod, entered = { if (pick.fresh == g.id) pick.fresh = null },
                    flip = { back -> pick.flip = if (back) g.id else null }, foot = 0.dp) { pick.sel = g.id; pick.close() } // the card plays its own shut sound
            }
        } else {
            SelPanel(q, g, at + 1, pick, folded, calm, mod, tap = { Crumbs.tap("panel", "queue"); pick.tapCover(g.id, v.shown) }, page = open)
        }
    }
    val lines = (shown.size + cols - 1) / cols
    for (r in 0 until lines) {
        item(selLine(v.tab, r)) {
            // under the panel or the open card: their 14 (`.qfsel`, `.qfwrap > .hero`) and the grid's own 8 on top
            Row(mod.padding(top = if (r == 0) 22.dp else 0.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(SEL_GAP.dp)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    val cg = shown.getOrNull(i)
                    if (cg == null) Spacer(Modifier.weight(1f))
                    else SelCell(q, cg, i + 1, cg.id == selId, pick, calm, Modifier.weight(1f)) {
                        Crumbs.tap("cover", "queue")
                        pick.tapCover(cg.id, v.shown)
                    }
                }
            }
        }
    }
}

/**
 * A line of covers' key: it names the tab, so right after Shuffle sends the Queue back to All a layout still showing the
 * old tab (with or without On deck above the grid) is never taken for the new one's lines.
 */
private fun selLine(tab: String, r: Int) = "qs:$tab:$r"

/**
 * Shuffle's pick in Character select: the line of covers holding it comes to the middle (`scrollIntoView({block:
 * "center"})` on its cover), going there first when it's off screen (lines sit together, so one on screen tells where).
 */
internal suspend fun LazyListState.centerCover(id: String, viewNow: () -> QueueView, cols: Int, calm: Boolean) {
    val v = kotlinx.coroutines.withTimeoutOrNull(1_000L) { androidx.compose.runtime.snapshotFlow { viewNow() }.first { v -> v.shown.any { it.id == id } } } ?: return
    val line = v.shown.indexOfFirst { it.id == id } / cols
    val pre = selLine(v.tab, 0).dropLast(1)
    repeat(4) {
        val vis = layoutInfo.visibleItemsInfo
        val item = vis.firstOrNull { it.key == pre + line }
        if (item != null) {
            val mid = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val by = (item.offset + item.size / 2 - mid).toFloat()
            if (calm) scrollBy(by) else animateScrollBy(by, tween(420, easing = Ease.glide))
            // the change that picked it (a card closing into the panel, On deck coming back) may land after the first
            // measure: measure again once and make up what's left
            layoutInfo.visibleItemsInfo.firstOrNull { it.key == pre + line }?.let { now ->
                val left = (now.offset + now.size / 2 - mid).toFloat()
                if (kotlin.math.abs(left) > 1f) { if (calm) scrollBy(left) else animateScrollBy(left, tween(240, easing = Ease.glide)) }
            }
            return
        }
        val anchor = vis.firstOrNull { (it.key as? String)?.startsWith(pre) == true }
        val panel = vis.firstOrNull { it.key == "q-sel" }
        val to = when {
            anchor != null -> anchor.index + line - (anchor.key as String).removePrefix(pre).toInt()
            panel != null -> panel.index + 1 + line
            else -> (vis.lastOrNull()?.index ?: return) + 1
        }
        val i = to.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        if (calm) scrollToItem(i) else animateScrollToItem(i)
    }
}

// ---------------------------------------------------------------- the panel

/**
 * `qfPanel`: the picked game's picture behind (dark on the left, in its colour across the middle), its spot, how it
 * stands, its logo (else its name, big), its name, three segmented bars (Hype, Length, then Score / Release / Done /
 * Expect by how it stands) and its box art bottom-right. A new pick slides its words in from the left and lifts its
 * box art (`qfswap`, 420 ms; only moves and fades).
 */
@Composable
private fun SelPanel(q: Queue, g: Game, n: Int, pick: QueuePick, folded: Boolean, calm: Boolean, mod: Modifier, tap: () -> Unit, page: (String) -> Unit) {
    val lib = q.lib
    val accent = remember(g.colour) { Color(g.colour) }
    val volt = LocalLook.current.volt
    val st = remember(g, q) { q.stat(g) }
    val pic = remember(g, lib) { rowPic(g, lib) }
    val shape = RoundedCornerShape(if (folded) 20.dp else 24.dp)
    // a tap that leaves the panel showing (a new pick, a card closed into it) plays the swap once; a tab change, a
    // Shuffle or the panel scrolling back into view just shows it (`qfTap` adds `.qfswap` only there)
    val swap = remember { Animatable(if (!calm && pick.selSwap != pick.selSwapSeen) 0f else 1f) }
    LaunchedEffect(pick.selSwap) {
        if (pick.selSwap == pick.selSwapSeen) return@LaunchedEffect
        pick.selSwapSeen = pick.selSwap
        if (calm) swap.snapTo(1f) else {
            swap.snapTo(0f)
            swap.animateTo(1f, tween(420, easing = Ease.house))
        }
    }
    val bars = remember(g, q, st) { selBars(q, g, st) }
    BoxWithConstraints(
        mod.fillMaxWidth().height(if (folded) 236.dp else 300.dp).clip(shape).background(Pn.void)
            .clickable(role = Role.Button, onClickLabel = "Open its card", onClick = tap)
            .semantics { contentDescription = g.title + ", " + st.word + ", " + st.head },
    ) {
        if (pic != null) {
            val bx = Backup.num(g["bgX"], 100.0, 0.0, 100.0).toFloat() / 50 - 1
            val by = Backup.num(g["bgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            PicBox(pic, g, Modifier.fillMaxSize(), align = BiasAlignment(bx, by))
        }
        // `.qfsel::before`: near-black on the left, the game's colour across the middle, a shade along the foot
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    .18f to Pn.void,
                    .46f to mixA(accent, SHADE, .3f),
                    .80f to SHADE_FAR,
                    1f to SHADE_FAR,
                ),
            ).background(Brush.verticalGradient(0f to Color.Transparent, .58f to Color.Transparent, 1f to FOOT)),
        )
        val sw = maxWidth
        Column(
            Modifier.fillMaxHeight().width(sw * (if (folded) .6f else .52f))
                .padding(start = if (folded) 16.dp else 26.dp, top = if (folded) 16.dp else 22.dp, bottom = if (folded) 16.dp else 22.dp)
                .graphicsLayer {
                    val t = swap.value
                    translationX = -18.dp.toPx() * (1 - t)
                    alpha = t
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BasicText(
                    n.toString().padStart(2, '0'), style = SType.kick.fixed(), maxLines = 1, softWrap = false,
                    modifier = Modifier.background(mixOk(accent, Color.Black, .75f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Box(Modifier.size(8.dp).background(stateTone(st.kind, volt), Pn.pill))
                WholeKick(st.word + " · " + st.head, st.head, SType.kick.fixed())
            }
            val art = Backup.str(g["art"])
            if (art.isNotEmpty()) {
                SelLogo(art, Modifier.padding(top = if (folded) 10.dp else 14.dp).fillMaxWidth().height(if (folded) 62.dp else 92.dp))
                BasicText(
                    g.title, style = (if (folded) SType.nameF else SType.name).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (folded) 4.dp else 8.dp),
                )
            } else {
                // no logo: the name takes its place, big (the web leaves the logo's room empty)
                BasicText(
                    g.title, style = (if (folded) SType.bigF else SType.big).fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (folded) 10.dp else 14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(if (folded) 5.dp else 7.dp)) {
                for (b in bars) SelBar(b, accent, folded)
            }
        }
        // the box art bottom-right (`.qfcv`): its own page
        val cvW = if (folded) 88.dp else 132.dp
        val cvH = if (folded) 128.dp else 190.dp
        val cs = RoundedCornerShape(14.dp)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = if (folded) 16.dp else 26.dp, bottom = if (folded) 16.dp else 22.dp)
                .graphicsLayer {
                    val t = swap.value
                    translationY = 14.dp.toPx() * (1 - t)
                    alpha = t
                }
                .size(cvW, cvH).shadow(18.dp, cs, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(cs).border(2.dp, CV_EDGE, cs)
                .clickable(role = Role.Button) { Crumbs.tap("panel cover", "queue"); page(g.id) }
                .semantics { contentDescription = "Open " + g.title + "'s page" },
        ) {
            val ix = Backup.num(g["imgX"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            val iy = Backup.num(g["imgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), align = BiasAlignment(ix, iy), ground = Color(Web.COVER_BG))
        }
        // the inside line in the game's colour
        Box(Modifier.fillMaxSize().border(1.dp, accent.copy(alpha = .42f), shape))
    }
}

/** One of the panel's bars: what it is, how full (0-1), and the number at its end. */
private class SelBarV(val label: String, val v: Float, val text: String)

/** `qfPanel`'s three bars: Hype, Length (of 100 h), then Score, Release, Done or Expect by how the game stands. */
private fun selBars(q: Queue, g: Game, st: Queue.Stat): List<SelBarV> {
    val hype = Backup.num(g["hype"], 0.0, 0.0, 10.0)
    val hrs = q.rk.hrsOf(g)
    val num = { d: Double -> Backup.jsNumber(Rank.jsRound(d * 10) / 10).content }
    val third = when (st.kind) {
        Queue.Kind.Played -> q.myScore(g).let { SelBarV("Score", it / 100f, if (it > 0) it.toString() else "—") }
        Queue.Kind.Coming -> SelBarV("Release", q.progress(g, st), st.stub.first)
        Queue.Kind.Playing, Queue.Kind.Dropped -> q.progress(g, st).let { SelBarV("Done", it, (it * 100).roundToInt().toString() + "%") }
        Queue.Kind.Out -> q.expected(g).let { e -> SelBarV("Expect", ((e ?: 0.0) / 10).toFloat(), if (e == null) "—" else num(e)) }
    }
    return listOf(
        SelBarV("Hype", (hype / 10).toFloat(), if (hype > 0) num(hype) + "/10" else "—"),
        SelBarV("Length", minOf(1.0, hrs / 100).toFloat(), if (hrs > 0) num(hrs) + " h" else "—"),
        third,
    )
}

/** `.qfseg`: ten slanted segments, the first [SelBarV.v] of them lit in the game's colour. */
@Composable
private fun SelBar(b: SelBarV, accent: Color, folded: Boolean) {
    val on = remember(accent) { mixOk(accent, Color.White, Web.QF_SEG_MIX).copy(alpha = Web.QF_SEG_ALPHA) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(b.label, style = (if (folded) SType.barF else SType.bar).fixed(), maxLines = 1, softWrap = false, modifier = Modifier.width(if (folded) 52.dp else 64.dp))
        val lit = (b.v.coerceIn(0f, 1f) * 10).roundToInt()
        Box(
            Modifier.weight(1f).height(if (folded) 8.dp else 10.dp).padding(start = 2.dp).drawWithCache {
                // the ten shapes made once per size, not on every draw (Phase 1 check B2)
                val gap = 3.dp.toPx()
                val w = (size.width - gap * 9) / 10
                val lean = size.height * .25f // skewX(-14deg)
                val segs = List(10) { k ->
                    val x = k * (w + gap)
                    Path().apply { moveTo(x + lean, 0f); lineTo(x + w, 0f); lineTo(x + w - lean, size.height); lineTo(x, size.height); close() }
                }
                onDrawBehind { segs.forEachIndexed { k, p -> drawPath(p, if (k < lit) on else OFF) } }
            },
        )
        BasicText(
            b.text, style = (if (folded) SType.valF else SType.value).copy(textAlign = TextAlign.End).fixed(), maxLines = 1, softWrap = false,
            modifier = Modifier.width(if (folded) 44.dp else 56.dp),
        )
    }
}

/** The panel's logo (`.qflg`): left-aligned, as big as its box allows, a near-black one drawn light (as on the rows). */
@Composable
private fun SelLogo(url: String, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val req = remember(url) { ImageRequest.Builder(ctx).data(picUrl(url)).allowHardware(false).transformations(InkCrop(url)).build() }
    var ink by remember(url) { mutableStateOf(LogoInk.known[url]) }
    AsyncImage(
        model = req, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
        colorFilter = if (ink?.dark == true) SILHOUETTE else null,
        onSuccess = { ink = LogoInk.known[url] },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- the grid

/**
 * `.qfc`: a cover with its spot (bottom left) and a dot in how it stands (bottom right); a played one is dimmed. The
 * picked one wears a ring and a glow in its colour and the P1 tag above. Shuffle's pick lights up here ([LitRing]).
 */
@Composable
private fun SelCell(q: Queue, g: Game, n: Int, on: Boolean, pick: QueuePick, calm: Boolean, modifier: Modifier, tap: () -> Unit) {
    val accent = remember(g.colour) { Color(g.colour) }
    val volt = LocalLook.current.volt
    val kind = remember(g, q) { q.stat(g).kind }
    val shape = RoundedCornerShape(14.dp)
    val ring = remember(accent) { mixOk(accent, Color.White, Web.QF_RING_MIX).copy(alpha = Web.QF_RING_ALPHA) }
    Box(modifier.aspectRatio(2f / 3f)) {
        Box(
            Modifier.fillMaxSize()
                .then(if (on) Modifier.shadow(14.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
                .graphicsLayer { alpha = if (kind == Queue.Kind.Played && !on) .62f else 1f }
                .clip(shape).background(Pn.pan)
                .clickable(role = Role.Tab, onClickLabel = "Pick", onClick = tap)
                .semantics { contentDescription = g.title; selected = on },
        ) {
            val ix = Backup.num(g["imgX"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            val iy = Backup.num(g["imgY"], 50.0, 0.0, 100.0).toFloat() / 50 - 1
            PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), align = BiasAlignment(ix, iy), ground = Color(Web.COVER_BG))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to FOOT)))
            BasicText(
                n.toString().padStart(2, '0'), style = SType.spot.fixed(), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 6.dp),
            )
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 10.dp).size(8.dp).background(stateTone(kind, volt), Pn.pill))
            Box(Modifier.fillMaxSize().border(if (on) 3.dp else 1.dp, if (on) ring else Pn.line2, shape))
        }
        if (on) {
            BasicText(
                "P1", style = SType.p1.fixed(), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-12).dp)
                    .background(mixOk(accent, Color.White, Web.QF_P1_MIX).copy(alpha = Web.QF_P1_ALPHA), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // Shuffle's pick lights up where it sits (read through derivedStateOf: a pick redraws only its own cover)
        val litHere by remember(g.id, pick) { derivedStateOf { pick.lit?.takeIf { it.id == g.id } } }
        litHere?.let { LitRing(it, accent, 14.dp, 0.dp, calm, Modifier) }
    }
}

// `.qfsel::before`, `.qfseg i` and `.qfcv`, read from index.html by tools/android-gen.mjs (alpha included); the ring,
// P1 and a lit segment mix the game's colour with white by `.qfc.on`'s, `.qfp1`'s and `.qfseg i.on`'s own shares
private val SHADE = Color(Web.QF_SHADE)
private val SHADE_FAR = Color(Web.QF_SHADE_FAR)
private val FOOT = Color(Web.QF_FOOT) // along the panel's and the covers' foot
private val OFF = Color(Web.QF_SEG_OFF)
private val CV_EDGE = Color(Web.QF_CV_EDGE) // the box art's 2px light edge (`.qfcv`)

private object SType {
    private val base = Type.body
    val kick = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Pn.ice)
    val name = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Pn.ice.copy(alpha = .82f))
    val nameF = name.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val big = base.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Pn.ice)
    val bigF = big.copy(fontSize = 24.sp, lineHeight = 29.sp)
    val bar = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute)
    val barF = bar.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val value = base.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Pn.ice)
    val valF = value.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val spot = base.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Pn.ice)
    val p1 = base.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = Pn.void)
}

/**
 * The panel's kicker kept whole (Phase 1 check B7): "Out now · Ready to play" when it fits, else the headline alone
 * ("Ready to play"; the dot beside it already says the state), never a word cut with "…". One layout, both texts
 * measured, the one that fits placed: no subcomposition, no second frame.
 */
@Composable
private fun WholeKick(full: String, short: String, style: TextStyle) {
    Layout({
        BasicText(full, style = style, maxLines = 1, softWrap = false)
        BasicText(short, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }) { ms, c ->
        val loose = c.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val a = ms[0].measure(loose)
        val p = if (a.width <= c.maxWidth) a else ms[1].measure(c.copy(minWidth = 0))
        layout(p.width, p.height) { p.place(0, 0) }
    }
}
