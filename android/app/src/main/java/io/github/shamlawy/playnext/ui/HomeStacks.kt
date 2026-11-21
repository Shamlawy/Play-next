package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Home
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * More for you (Phase 3 piece 4; index.html `renderHome` 11309-11469, `pnStack` 11910, `pnHold` 11961, `pnStackEdit`
 * 11970): two stacks of cards you swipe through, an upright one (`sB`: your year and month, play sessions, duels,
 * settle a duel, needs a verdict) and a sideways one (`sC`: where your hours go, standings, where the year bunches
 * up). Folded one above the other, 250 tall each; unfolded side by side, 290 tall. Each card sits on its game's
 * picture, darkened so its words read; dots show where you are. Hold a stack half a second to order and hide its
 * cards. The taste cards (your read, tonight, the hype trap…) join these stacks in piece 5.
 */
@Composable
internal fun MoreForYou(h: Home, wide: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        BasicText(
            "More for you", style = MF_HEAD.fixed(),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp).semantics { heading() },
        )
        if (wide) {
            Row(Modifier.fillMaxWidth().height(290.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stack(h, "sB", Modifier.weight(1f).fillMaxHeight())
                Stack(h, "sC", Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stack(h, "sB", Modifier.fillMaxWidth().height(250.dp))
                Stack(h, "sC", Modifier.fillMaxWidth().height(250.dp))
            }
        }
    }
}

/** Which card each stack was on (`pnStackPos`): kept while the app runs, so a redraw doesn't send it back to the first. */
internal object StackPos {
    private val at = HashMap<String, Int>()
    operator fun get(key: String): Int = at[key] ?: 0
    operator fun set(key: String, n: Int) { at[key] = n }
    fun clear() = at.clear()
}

private val STACK_SHAPE = RoundedCornerShape(22.dp)

/**
 * One stack (`.stkw`): the upright one ([key] "sB") pages up and down with its dots in a column on the right, the
 * sideways one ("sC") left and right with its dots along the foot. A swipe stays in the stack (`overscroll-behavior:
 * contain`): at its ends the page doesn't take over. A hold opens Edit this stack.
 */
@Composable
private fun Stack(h: Home, key: String, modifier: Modifier) {
    val vertical = key == "sB"
    val cards = remember(h, key) { h.stack(key) }
    val acts = LocalHomeActs.current
    val edit by rememberUpdatedState {
        // the web's pnStackEdit has nothing to show for a stack with no cards at all (every one switched off)
        if (h.stackAll(key).isNotEmpty()) {
            Buzz.ms(18)
            Crumbs.tap("stack edit $key", "home")
            acts?.sheet?.invoke("stack:$key")
        }
    }
    Box(
        modifier
            .clip(STACK_SHAPE)
            .holdToEdit { edit() }
            .semantics { onLongClick(label = "Edit this stack") { edit(); true } },
    ) {
        if (cards.isEmpty()) {
            // `.stkempty`: everything here is hidden; the stack can still be held
            Box(Modifier.fillMaxSize().tileGround(null, false, false), contentAlignment = Alignment.Center) {
                BasicText("Everything here is hidden. Hold to edit.", style = SUB.fixed().copy(textAlign = TextAlign.Center), modifier = Modifier.padding(16.dp))
            }
        } else {
            StackPages(h, key, cards, vertical)
        }
    }
}

/** A stack's pages and its dots. */
@Composable
private fun BoxScope.StackPages(h: Home, key: String, cards: List<Home.More>, vertical: Boolean) {
        val n by rememberUpdatedState(cards.size)
        val state = rememberPagerState(initialPage = StackPos[key].coerceIn(0, cards.lastIndex)) { n }
        LaunchedEffect(state, key) { snapshotFlow { state.currentPage }.collect { StackPos[key] = it } }
        val guard = LocalSwipeGuard.current
        if (vertical) {
            VerticalPager(
                state, Modifier.fillMaxSize().nestedScroll(CONTAIN), pageSpacing = 12.dp,
                key = { cards.getOrNull(it)?.id ?: it },
            ) { p -> cards.getOrNull(p)?.let { MoreCard(h, it, true) } }
        } else {
            HorizontalPager(
                state,
                Modifier
                    .fillMaxSize()
                    .nestedScroll(CONTAIN)
                    // a touch that lands here while it can turn is the stack's own: Shell's tab swipe leaves it alone (L332)
                    .pointerInput(state) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (n > 1 && e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true
                            }
                        }
                    },
                pageSpacing = 12.dp,
                key = { cards.getOrNull(it)?.id ?: it },
            ) { p -> cards.getOrNull(p)?.let { MoreCard(h, it, false) } }
        }
        if (cards.size > 1) Dots(state, cards.size, vertical)
}

/** What the page gets once a stack can't go further: nothing (`overscroll-behavior: contain`). */
private val CONTAIN = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Hold half a second (`pnHold`: 550 ms, a move of 10 or more lets go) and [fire] runs; the rest of that touch is then
 * swallowed, so the card under the finger doesn't open and the stack doesn't turn. The down and what follows are read
 * in the same pass (L232).
 */
private fun Modifier.holdToEdit(fire: () -> Unit): Modifier = pointerInput(Unit) {
    // the pager starts dragging at the touch slop (~8), before the web's 10: let go there too, as Queue's hold does
    val slop = minOf(10.dp.toPx(), viewConfiguration.touchSlop)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val ended = withTimeoutOrNull(550L) {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || (c.position - down.position).getDistance() >= slop) break
            }
        }
        if (ended == null) {
            fire()
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}

/**
 * `.stkd`: a dot per card (6, `--ice` at .28), the current one stretched 2.4 along the stack's way and lit; a tap on
 * one turns to that card. Calm: the dot changes at once and the stack jumps.
 */
@Composable
private fun BoxScope.Dots(state: PagerState, n: Int, vertical: Boolean) {
    val calm = LocalCalm.current
    val scope = rememberCoroutineScope()
    val content: @Composable () -> Unit = {
        for (i in 0 until n) {
            val on = state.currentPage == i
            val s by animateFloatAsState(if (on) 2.4f else 1f, if (calm) snap() else tween(400, easing = Ease.house), label = "dot")
            // each dot's touch is its 15-wide step (the 6 dot and the 9 gap), across the strip's 24
            Box(
                Modifier
                    .then(if (vertical) Modifier.size(24.dp, 15.dp) else Modifier.size(15.dp, 24.dp))
                    .clickable(role = Role.Button, onClickLabel = "Card ${i + 1} of $n") {
                        scope.launch { if (calm) state.scrollToPage(i) else state.animateScrollToPage(i) }
                    }
                    .semantics { contentDescription = "Card ${i + 1} of $n" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { if (vertical) scaleY = s else scaleX = s }
                        .background(if (on) Pn.ice else DOT, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
    if (vertical) {
        // a column on the right edge in the middle (each dot centred in its 24-wide touch, the web's 8 in + 3)
        Column(Modifier.align(Alignment.CenterEnd)) { content() }
    } else {
        Row(Modifier.align(Alignment.BottomCenter)) { content() }
    }
}

private val DOT = Pn.ice.copy(alpha = .28f)

/**
 * A card's ground: its game's picture (`.dxbg`: the backdrop, else the box art, framed 30% down) under a shade
 * darkening up from the foot and, in the upright stack, a tint of the game's colour from the top left; without a
 * picture the tile's own gradient. The line round it is `--edge`, `--hot` for a warning.
 */
@Composable
private fun Modifier.tileGround(g: Game?, upright: Boolean, warn: Boolean): Modifier {
    val pic = remember(g) { g?.let { Pic.backdrop(it) ?: (Pic.cover(it) as? Pic.Url) } }
    return this
        .then(
            if (pic != null) Modifier.background(Pn.ink)
            else Modifier.drawBehind { drawRect(Brush.linearGradient(TILE, start = Offset(size.width * .33f, 0f), end = Offset(size.width * .67f, size.height))) },
        )
        .border(1.dp, if (warn) Pn.hot else Pn.edge, STACK_SHAPE)
}

private val HEX = Regex("^#[0-9a-fA-F]{6}$")
private val TILE = listOf(Pn.steel, Pn.ink, Pn.ink)

/** One card: its ground and picture, its words (the upright stack's sit at the foot under their title), its tap. */
@Composable
private fun MoreCard(h: Home, c: Home.More, upright: Boolean) {
    val say = rememberSay()
    val warn = (c as? Home.More.Big)?.warn == true
    val go = c.go?.takeIf { c !is Home.More.Months }
    Box(
        Modifier
            .fillMaxSize()
            .clip(STACK_SHAPE)
            .tileGround(c.bg, upright, warn)
            .then(
                if (go != null) Modifier.clickable(role = Role.Button, onClickLabel = c.name) {
                    Sounds.play(Sfx.Open)
                    Crumbs.tap("more ${c.id}", "home")
                    say(soon(go))
                } else Modifier,
            ),
    ) {
        val pic = remember(c.bg) { c.bg?.let { Pic.backdrop(it) ?: (Pic.cover(it) as? Pic.Url) } }
        if (pic != null) {
            // under the shade (drawn over the content's ground by tileGround's drawWithCache, so the picture goes first)
            PicBox(pic, c.bg, Modifier.fillMaxSize().clearAndSetSemantics {}, align = BiasAlignment(0f, -.4f))
            Box(Modifier.fillMaxSize().shadeOver(upright, c.bg))
        }
        if (go != null) {
            // `.tile.go2::after`: an arrow in the top right corner
            BasicText(
                "→", style = ARROW.fixed().copy(color = if (warn) Pn.hot else LocalLook.current.volt),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 11.dp).clearAndSetSemantics {},
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = if (upright) 28.dp else 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicText(c.name.uppercase(), style = (if (pic != null) H5_SHADED else H5).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (upright) Spacer(Modifier.weight(1f))
            when (c) {
                is Home.More.Big -> BigWords(c)
                is Home.More.Week -> WeekWords(c)
                is Home.More.Stand -> StandRows(c)
                is Home.More.Hours -> HoursBars(c)
                is Home.More.Months -> MonthBars(c, say)
            }
        }
    }
}

/** The shade over a card's picture (drawn here, over the picture, as the web's gradient layers sit on the image). */
@Composable
private fun Modifier.shadeOver(upright: Boolean, g: Game?): Modifier {
    val volt2 = LocalLook.current.volt2
    val tint = if (g != null && Backup.str(g["colour"]).matches(HEX)) Color(g.colour).copy(alpha = 0x57 / 255f) else volt2.copy(alpha = .34f)
    return drawWithCache {
        val shade = if (upright) Brush.verticalGradient(0f to Color(Web.MF_TOP), .48f to Color(Web.MF_MID), .84f to Color(Web.MF_FOOT))
        else Brush.verticalGradient(0f to Color(Web.MF_SC_TOP), .4f to Color(Web.MF_SC_MID), 1f to Color(Web.MF_SC_FOOT))
        val corner = Brush.linearGradient(0f to tint, .6f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height))
        onDrawBehind {
            drawRect(shade)
            if (upright) drawRect(corner)
        }
    }
}

/** `.big` (24, black weight) with its small grey `em`, a meter when there is one, then `.sub`. */
@Composable
private fun ColumnScope.BigWords(c: Home.More.Big) {
    val big = BIG.fixed()
    val em = EM.fixed()
    BasicText(
        buildAnnotatedString {
            append(c.big)
            if (c.em.isNotEmpty()) withStyle(SpanStyle(fontSize = em.fontSize, fontWeight = em.fontWeight, color = em.color, letterSpacing = em.letterSpacing)) { append(c.em) }
        },
        style = big, maxLines = if (c.word) 2 else 1, overflow = TextOverflow.Ellipsis,
    )
    c.meter?.let { Meter(it) }
    if (c.sub.isNotEmpty()) BasicText(c.sub, style = SUB.fixed(), maxLines = 3, overflow = TextOverflow.Ellipsis)
}

/** `.meter`: 4 tall, `--edge`, filled in your accent. */
@Composable
private fun Meter(f: Float) {
    val volt = LocalLook.current.volt
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Pn.edge, RoundedCornerShape(3.dp))
            .drawBehind { drawRoundRect(volt, size = Size(size.width * f.coerceIn(0f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())) },
    )
}

/** Play sessions: N this week, a bar per day (`.ss-wk`: 64 tall, up to 26 wide, round 6) over its letter, the line. */
@Composable
private fun ColumnScope.WeekWords(c: Home.More.Week) {
    val volt = LocalLook.current.volt
    val big = BIG.fixed()
    val em = EM.fixed()
    BasicText(
        buildAnnotatedString {
            append("${c.n}")
            withStyle(SpanStyle(fontSize = em.fontSize, fontWeight = em.fontWeight, color = em.color, letterSpacing = em.letterSpacing)) { append(" this week") }
        },
        style = big, maxLines = 1,
    )
    Row(Modifier.fillMaxWidth().height(64.dp).padding(top = 5.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        c.bars.forEachIndexed { i, f ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .widthIn(max = 26.dp)
                        .fillMaxWidth()
                        .drawBehind {
                            val hh = size.height * f.coerceIn(0f, 1f)
                            drawRoundRect(volt, topLeft = Offset(0f, size.height - hh), size = Size(size.width, hh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                        },
                )
                BasicText(c.days.getOrElse(i) { "" }, style = DAY.fixed())
            }
        }
    }
    BasicText(c.sub, style = SUB.fixed(), maxLines = 3, overflow = TextOverflow.Ellipsis)
}

/** Standings (`.stand`): a row per place, its colour (or `--edge`) down its left, its number, title and Elo bar; a tap opens it. */
@Composable
private fun StandRows(c: Home.More.Stand) {
    val open = LocalOpenGame.current
    val volt = LocalLook.current.volt
    Column(Modifier.fillMaxWidth().padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        c.rows.forEachIndexed { i, (g, f) ->
            val tint = if (c.tint) Color(g.colour) else Pn.edge
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 25.dp)
                    .padding(start = 0.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${g.title}") { open(g.id, null) }
                    .drawBehind { drawRect(tint, size = Size(3.dp.toPx(), size.height)) }
                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText("${i + 1}", style = STAND_N.fixed(), modifier = Modifier.width(15.dp))
                BasicText(g.title, style = STAND_T.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(46.dp, 4.dp)
                        .background(Pn.edge, RoundedCornerShape(2.dp))
                        .drawBehind { drawRect(volt, size = Size(size.width * f.coerceIn(0f, 1f), size.height)) }
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * Where your hours go: the note, a pill bar per genre (your top three in your accent, the rest `--edge`, Other in
 * `--ice`) with its hours in a small pill over it and its name under it, then whose share is biggest. The web's bars
 * sit in a 200-tall column the 250 card squeezes to a sliver; here they take the room the card leaves.
 */
@Composable
private fun ColumnScope.HoursBars(c: Home.More.Hours) {
    val volt = LocalLook.current.volt
    BasicText(c.note, style = NOTE.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(
        Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp).clearAndSetSemantics {
            contentDescription = c.bars.joinToString(", ") { it.label + " " + it.value }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (b in c.bars) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    BasicText(
                        b.value, style = PV.fixed().copy(color = if (b.kind == Home.Bar.COOL) Pn.mute else Pn.ice), maxLines = 1,
                        modifier = Modifier
                            .padding(bottom = 5.dp)
                            .background(Pn.void, Pn.pill)
                            .border(1.dp, Pn.edge, Pn.pill)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(b.h.coerceIn(.09f, 1f))
                            .background(
                                when (b.kind) {
                                    Home.Bar.HOT -> volt
                                    Home.Bar.OTHER -> Pn.ice.copy(alpha = .9f)
                                    else -> Pn.edge.copy(alpha = .85f)
                                },
                                Pn.pill,
                            ),
                    )
                }
                BasicText(b.label.uppercase(), style = PL.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (c.foot.isNotEmpty()) BasicText(c.foot, style = NOTE.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/**
 * Where the year bunches up (`.yearbar`): twelve bars (46 tall, a line under them), each the month's share of the
 * busiest (6% when empty), the busiest lit; the month letters under them. A tap on one would open Plan's calendar on
 * that month (phase 5).
 */
@Composable
private fun MonthBars(c: Home.More.Months, say: (String) -> Unit) {
    val volt = LocalLook.current.volt
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(47.dp)
                .drawBehind { drawRect(Pn.edge, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            c.counts.forEachIndexed { i, n ->
                val f = if (n > 0) Math.floor(n.toDouble() / c.peak * 100 + .5).toFloat() / 100f else .06f
                val hot = n > 0 && n == c.peak
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = "Open ${MONTH_NAMES[i]} in the calendar") {
                            Sounds.play(Sfx.Open)
                            say(soon("month"))
                        }
                        .semantics { contentDescription = "$n in ${MONTH_NAMES[i]}" }
                        .drawBehind {
                            val hh = maxOf(3.dp.toPx(), size.height * f)
                            val r = 3.dp.toPx()
                            val path = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        0f, size.height - hh, size.width, size.height,
                                        topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r), topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                                    ),
                                )
                            }
                            drawPath(path, if (hot) volt else Pn.edge)
                        },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (m in "JFMAMJJASOND") BasicText(m.toString(), style = YEAR_L.fixed(), modifier = Modifier.weight(1f))
        }
    }
}

private val MONTH_NAMES = io.github.shamlawy.playnext.data.Year.MONTHS

// ---------------------------------------------------------------- Edit this stack

/**
 * Edit this stack (`#stkedit`): each card of the stack ([key]) in its order, with up and down and Shown / Hidden (a
 * hidden one dims). Done keeps the order and what's hidden (`S.stackCfg[key]`), Reset forgets both; closing it any
 * other way keeps nothing.
 */
@Composable
internal fun StackSheet(modifier: Modifier, key: String, games: Games.State, close: () -> Unit) {
    val ready = games as? Games.State.Ready
    SheetFrame(modifier, close) {
        BasicText("Edit this stack", style = Type.sheetTitle, modifier = Modifier.padding(bottom = 2.dp))
        BasicText("Order and pick which cards it holds.", style = Type.note)
        if (ready != null) {
            val h = rememberHome(ready)
            // seeded once: a library rewrite (sync, a save, midnight) rebuilds h, and must not wipe what's being edited
            val list = remember(key) { mutableStateListOf<Pair<String, String>>().apply { addAll(h.stackAll(key).map { it.id to it.name }) } }
            var hid by remember(key) { mutableStateOf(h.stackHidden(key)) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEachIndexed { n, (id, name) -> androidx.compose.runtime.key(id) {
                    val off = id in hid
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (off) .45f else 1f }
                            .background(Pn.card, RoundedCornerShape(14.dp))
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        BasicText(name, style = ER.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        MoveBtn("up", "Move $name up", n > 0) { list.add(n - 1, list.removeAt(n)) }
                        MoveBtn("down", "Move $name down", n < list.lastIndex) { list.add(n + 1, list.removeAt(n)) }
                        Box(
                            Modifier
                                .heightIn(min = Pn.touch)
                                .widthIn(min = 76.dp)
                                .clickable(role = Role.Switch, onClickLabel = if (off) "Show $name" else "Hide $name") { hid = if (off) hid - id else hid + id }
                                .semantics { contentDescription = name + if (off) ": hidden" else ": shown" },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                if (off) "Hidden" else "Shown", style = ER_BTN.fixed(),
                                modifier = Modifier.background(Pn.pan, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Btn("Reset") {
                    val cfg = (ready.lib["stackCfg"] as? JsonObject).orEmpty() - key
                    Games.customise("stackCfg", JsonObject(cfg))
                    close()
                }
                Btn("Done", primary = true) {
                    val cfg = (ready.lib["stackCfg"] as? JsonObject).orEmpty() + (
                        key to JsonObject(
                            mapOf(
                                "order" to JsonArray(list.map { JsonPrimitive(it.first) }),
                                "hidden" to JsonArray(list.map { it.first }.filter { it in hid }.map(::JsonPrimitive)),
                            ),
                        )
                    )
                    Games.customise("stackCfg", JsonObject(cfg))
                    close()
                }
            }
        }
    }
}

/** ↑ / ↓ (`data-mv`): a drawn chevron in a 48 touch, faint and still at the ends. */
@Composable
private fun MoveBtn(dir: String, label: String, can: Boolean, move: () -> Unit) {
    Box(
        Modifier
            .size(Pn.touch)
            .clickable(enabled = can, role = Role.Button, onClickLabel = label) { move() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(Pn.pan, RoundedCornerShape(10.dp))
                .graphicsLayer { alpha = if (can) 1f else .3f }
                .drawBehind {
                    // a chevron 10 wide, 2 thick, pointing [dir]
                    val w = 10.dp.toPx()
                    val hh = 5.dp.toPx()
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val up = dir == "up"
                    val tip = Offset(cx, if (up) cy - hh / 2 else cy + hh / 2)
                    val l = Offset(cx - w / 2, if (up) cy + hh / 2 else cy - hh / 2)
                    val r = Offset(cx + w / 2, l.y)
                    drawLine(Pn.ice, l, tip, 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Pn.ice, tip, r, 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                },
        )
    }
}

// ---------------------------------------------------------------- type (measured on the web at 412, Nov 21)

private val P = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)
private val MF_HEAD = P.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val H5 = P.copy(fontSize = 9.5.sp, lineHeight = 13.8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .14.em, color = Pn.mute)
private val H5_SHADED = H5.copy(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 1f), 8f))
private val BIG = P.copy(fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, letterSpacing = (-.01).em)
private val EM = P.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Pn.mute)
private val SUB = P.copy(fontSize = 11.sp, lineHeight = 14.3.sp, color = Pn.mute)
private val ARROW = P.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold)
private val DAY = P.copy(fontSize = 10.sp, lineHeight = 13.sp, color = Pn.mute)
private val STAND_N = P.copy(fontSize = 10.sp, lineHeight = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Pn.mute)
private val STAND_T = P.copy(fontSize = 12.sp, lineHeight = 17.4.sp, fontWeight = FontWeight.Bold)
private val NOTE = P.copy(fontSize = 12.sp, lineHeight = 16.5.sp, color = Pn.mute)
private val PV = P.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
private val PL = P.copy(fontSize = 9.5.sp, lineHeight = 13.8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .04.em, color = Pn.mute, textAlign = TextAlign.Center)
private val YEAR_L = P.copy(fontSize = 8.5.sp, lineHeight = 12.3.sp, color = Pn.mute, textAlign = TextAlign.Center)
private val ER = P.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
private val ER_BTN = P.copy(fontSize = 13.sp, lineHeight = 18.sp)
