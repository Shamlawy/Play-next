package io.github.shamlawy.playnext.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.text.TextStyle
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
 * The open card (piece 2), Shuffle, Boss, drag, swipe to delete and Character select (piece 3) come next; until then a
 * tap on a row opens the game's page. A practice copy: the tab and order you pick last until the app reopens.
 */
@Immutable
class QueueView(val q: Queue, val tab: String, val sort: String, val shown: List<Game>, val now: Long)

/** The Queue's own numbers for [lib] at this minute: the tab and order picked, the games shown in that order. */
@Composable
fun rememberQueueView(lib: Library, pick: QueuePick): QueueView {
    val now = LocalNow.current()
    val zone = java.time.ZoneId.systemDefault()
    // the day matters (out / coming / dust), the minute doesn't: worked out again for a new copy or a new day
    val day = io.github.shamlawy.playnext.data.JsDate.todayLocal(now, zone)
    val q = remember(lib, day) { Queue(lib, Rank(lib, now, zone), now, zone) }
    val tab = q.tabNow(pick.tab)
    val sort = pick.sort
    val shown = remember(q, tab, sort) { q.sort(q.list(tab), sort) }
    return QueueView(q, tab, sort, shown, now)
}

/** The Queue's tab and order: the web app's (`S.qFilter`, `S.sort`) to start with, then yours until the app reopens. */
class QueuePick(lib: Library) {
    var tab by mutableStateOf(Backup.str(lib["qFilter"]).ifEmpty { "all" })
    var sort by mutableStateOf(Backup.str(lib["sort"]).ifEmpty { "manual" })
}

/** The colours the web CSS writes as literals (index.html 27366-27546, 24517-24533, 21096): named once here. */
object QTone {
    val playing = Color(0xFF3FD9A4) // --qsc for playing (27376): rgb(63 217 164)
    val coming = Color(0xFF6FD6FF) // --qsc for coming: rgb(111 214 255)
    val played = Color(0xFFC7A3FF) // --qsc for played: rgb(199 163 255)
    val shade = Color(0xFF070A14) // rgba(7,10,20,x): the picture's veil
    val shadeDeep = Color(0xFF04060E) // rgba(4,6,14,.66): the veil's right edge
    val ticket = Color(0xFF050810) // rgba(5,8,16,.88): the ticket's ground
    val pale = Color(0xFFEAF2FF) // rgba(234,242,255,x): the ticket's labels, the folded date
    val photo = Color(0xFF141A2E) // rgb(20,26,46): On deck's picture ground
    val onVolt = Color(0xFF060A16) // rgb(6,10,22): words on On deck's button
    val dust = Color(0xFFD6CEBE) // rgba(214,206,190,x): the dust's dots
    val dustVeil = Color(0xFFBEB4A0) // rgba(190,180,160,x): the dust's veil
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
fun LazyListScope.queueItems(v: QueueView, pick: QueuePick, folded: Boolean, calm: Boolean, mod: Modifier, open: (String) -> Unit) {
    val q = v.q
    if (q.lib.games.isEmpty()) {
        item("q-empty") { Empty("Queue is empty. Open Add and search for the first game.", mod) }
        return
    }
    item("q-bar") { QueueBar(v, pick, folded, mod) }
    val deck = if (q.lib.off("deckOff") || Backup.truthy(q.lib["deckOff"]) || v.tab == "done") emptyList() else q.deck(v.tab)
    if (deck.isNotEmpty()) item("q-deck") { OnDeck(q, v.tab, deck, mod, open) }
    if (v.shown.isEmpty()) item("q-none") { Empty("Nothing here right now. Try another filter.", mod) }
    itemsIndexed(v.shown, key = { _, g -> "q:" + g.id }) { i, g ->
        QueueRow(q, g, i + 1, v.sort, folded, mod.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (calm) null else tween(560, easing = Ease.glide)), open)
    }
}

@Composable
private fun Empty(text: String, mod: Modifier) {
    Box(mod.padding(top = 4.dp).fillMaxWidth().background(Pn.pan, Pn.r).border(1.dp, Pn.line, Pn.r).padding(18.dp)) {
        BasicText(text, style = Type.note)
    }
}

// ---------------------------------------------------------------- the bar (`qdDeck`, CSS 27439-27520)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueBar(v: QueueView, pick: QueuePick, folded: Boolean, mod: Modifier) {
    val q = v.q
    val volt = LocalLook.current.volt
    val tabs = Queue.TABS + q.lists.map { "L:" + it.id }
    val tabRow = @Composable { m: Modifier ->
        FlowRow(
            m.background(Pn.pan, RoundedCornerShape(18.dp)).border(1.dp, Pn.line2, RoundedCornerShape(18.dp)).padding(horizontal = 4.dp, vertical = if (folded) 0.dp else 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (t in tabs) {
                val on = t == v.tab
                val name = q.tabName(t)
                val n = q.counts[t] ?: 0
                Box(
                    Modifier.weight(1f).height(48.dp)
                        .semantics { contentDescription = "$name, $n"; selected = on }
                        .clickable(role = Role.Tab) {
                            if (t != pick.tab) {
                                Sounds.play(Sfx.Tap)
                                Crumbs.tap("queue tab", "games/queue")
                                pick.tab = t
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(if (folded) 40.dp else 44.dp)
                            .then(if (on) Modifier.background(Brush.verticalGradient(listOf(volt.copy(alpha = .3f), volt.copy(alpha = .16f))), RoundedCornerShape(14.dp)).border(1.dp, volt.copy(alpha = .55f), RoundedCornerShape(14.dp)) else Modifier)
                            .padding(horizontal = if (folded) 9.dp else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (folded) 5.dp else 7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (on) volt else Pn.mute
                        val list = q.lists.firstOrNull { "L:" + it.id == t }
                        if (list != null) {
                            if (list.e.isNotEmpty()) BasicText(list.e, style = TextStyle(fontSize = 15.sp).fixed(), maxLines = 1)
                        } else {
                            QIcon(t, tint, 17.dp)
                        }
                        // folded, only the lit tab says its name (the others: icon + count)
                        if (!folded || on) {
                            BasicText(name, style = (if (folded) QType.tabF else QType.tab).copy(color = if (on) Pn.ice else Pn.mute).fixed(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        BasicText(n.toString(), style = QType.count.copy(color = tint).fixed(), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
    if (folded) {
        Column(mod.padding(top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tabRow(Modifier.fillMaxWidth())
            OrderButton(v, pick, true, Modifier.fillMaxWidth())
        }
    } else {
        Row(mod.padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            tabRow(Modifier.weight(1f))
            OrderButton(v, pick, false, Modifier.widthIn(min = 148.dp))
        }
    }
}

/** Order (`#sortby`): "Order" over the order's name, a caret; its menu groups the orders (`#sortpop`). */
@Composable
private fun OrderButton(v: QueueView, pick: QueuePick, folded: Boolean, modifier: Modifier) {
    val volt = LocalLook.current.volt
    val manual = v.sort == "manual"
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (folded) 16.dp else 18.dp)
    Box(modifier) {
        Row(
            Modifier.height(if (folded) 48.dp else 54.dp)
                .background(Pn.pan, shape).border(1.dp, if (!manual) volt.copy(alpha = .6f) else Pn.line2, shape)
                .semantics { contentDescription = "Order: " + Queue.sortName(v.sort) }
                .clickable(role = Role.Button) {
                    Sounds.play(Sfx.Tap)
                    open = !open
                }
                .padding(horizontal = if (folded) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QdIcon("order", if (!manual) volt else Pn.ice, 20.dp)
            Column(Modifier.weight(1f, fill = folded)) {
                BasicText("Order", style = QType.small.fixed(), maxLines = 1)
                BasicText(Queue.sortName(v.sort), style = QType.bold.fixed(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            QdIcon("car", Pn.mute, 16.dp, Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
        }
        if (open) {
            val d = LocalDensity.current
            Popup(
                alignment = if (folded) Alignment.TopStart else Alignment.TopEnd,
                offset = with(d) { IntOffset(0, ((if (folded) 48.dp else 54.dp) + 8.dp).roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                OrderMenu(v.sort) { k ->
                    open = false
                    if (k != pick.sort) pick.sort = k
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderMenu(sort: String, choose: (String) -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.widthIn(max = 470.dp).padding(horizontal = 16.dp)
            .background(Pn.card, shape).border(1.dp, Pn.line2, shape).padding(14.dp),
    ) {
        for ((head, keys) in Queue.GROUPS) {
            BasicText(head, style = QType.group.fixed(), modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 8.dp))
            FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in keys) {
                    val on = k == sort
                    val s = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.heightIn(min = 48.dp).semantics { selected = on }.clickable(role = Role.RadioButton) { choose(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            Queue.sortName(k),
                            style = QType.option.copy(color = if (on) Pn.void else Pn.ice).fixed(),
                            maxLines = 1,
                            modifier = Modifier.height(38.dp).background(if (on) volt else Pn.card2, s)
                                .then(if (on) Modifier else Modifier.border(1.dp, Pn.line, s)).padding(horizontal = 13.dp).wrapCenter(),
                        )
                    }
                }
            }
        }
    }
}

/** A single line of text centred in its box's height (the web's 38px option buttons). */
private fun Modifier.wrapCenter() = this.then(Modifier.padding(top = 9.dp))

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
                BasicText(
                    g.title, style = (if (folded) QType.nameF else QType.name).fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (folded) 0.dp else 2.dp).then(if (folded) Modifier else Modifier.heightIn(min = 48.dp)),
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
private fun rowPic(g: Game, lib: Library): Pic? {
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
    min(rowW.value * (if (folded) .27f else .28f), 1.4f * Backup.num(lib["rowArtW"], 150.0, 40.0, 400.0).toFloat()).dp

/** A logo's ink, measured once per address: the box its visible pixels fill (with the .02 guard) and whether it's near-black. */
class Ink(val aspect: Float, val dark: Boolean)

private object LogoInk {
    val known = ConcurrentHashMap<String, Ink>()
}

/**
 * Crops a logo to its ink (alpha > 24, the 2% guard around it) and paints a near-black one light (`brightness(0)
 * invert(.9)`: every visible pixel 230, alpha kept), measured on a copy at most 200 wide like `lfMeasure`.
 */
private class InkCrop(private val url: String) : Transformation() {
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

private val SILHOUETTE = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 230f, 0f, 0f, 0f, 0f, 230f, 0f, 0f, 0f, 0f, 230f, 0f, 0f, 0f, 1f, 0f)),
)

@Composable
private fun LogoSlot(url: String, rowW: Dp, folded: Boolean, lib: Library) {
    val slotW = logoSlotW(rowW, folded, lib)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val req = remember(url) { ImageRequest.Builder(ctx).data(picUrl(url)).allowHardware(false).transformations(InkCrop(url)).build() }
    var ink by remember(url) { mutableStateOf(LogoInk.known[url]) }
    val op = Backup.num(lib["rowArtOp"], 95.0, 0.0, 100.0).toFloat() / 100
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

/** `QICON` (index.html 7335): the tabs' 16x16 icons, drawn (they have strokes, so not through [Icons]). */
@Composable
private fun QIcon(kind: String, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 16f, pivot = Offset.Zero) {
            fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, a: Float = 1f) = drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(r), alpha = a)
            val ring = { drawCircle(tint, 6.4f, Offset(8f, 8f), alpha = .55f, style = Stroke(1.6f)) }
            when (kind) {
                "all" -> { rr(2f, 2.2f, 12f, 3f, 1.2f, .45f); rr(2f, 6.4f, 12f, 3f, 1.2f, .75f); rr(2f, 10.6f, 12f, 3f, 1.2f) }
                "out" -> { ring(); drawPath(Path().apply { moveTo(6.6f, 5.2f); lineTo(11f, 8f); lineTo(6.6f, 10.8f); close() }, tint) }
                "done" -> {
                    ring()
                    drawPath(Path().apply { moveTo(5f, 8.3f); lineTo(7.1f, 10.4f); lineTo(11f, 6.2f) }, tint, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                else -> {
                    drawRoundRect(tint, Offset(1.6f, 3.2f), Size(12.8f, 11f), CornerRadius(2f), alpha = .55f, style = Stroke(1.5f))
                    rr(1.6f, 3.2f, 12.8f, 3.2f, 1.6f); rr(4.4f, 1.2f, 1.6f, 3.2f, .8f); rr(10f, 1.2f, 1.6f, 3.2f, .8f)
                    drawCircle(tint, 1.7f, Offset(8f, 10.4f))
                }
            }
        }
    }
}

/** `QD_IC` (index.html 8769): the bar's 24x24 icons, in the v247 style (one part at .35). */
private val QD_IC = mapOf(
    "order" to listOf("M4 5h16v2.4H4z" to 1f, "M4 10.8h11v2.4H4z" to .35f, "M4 16.6h6.5V19H4z" to 1f),
    "car" to listOf("M6.5 9h11L12 15.5z" to 1f),
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
    val tab = base.copy(fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val tabF = tab.copy(fontSize = 13.5.sp)
    val count = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val small = base.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute)
    val bold = base.copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
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
