package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.JsDate
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Shx
import io.github.shamlawy.playnext.gen.Web
import java.time.ZoneId

/**
 * Each screen's sizes for the header card (index.html 16672-16827), by the screen's width as the web's media queries
 * read it: phones up to 619, the unfolded Fold from 620 (with 620-759 its own small band).
 */
private class Dims(screen: Dp) {
    val wide = screen >= 620.dp
    private val band = wide && screen < 760.dp
    val fw = if (wide) 250.dp else 124.dp // --fw: the fan's width
    val cw = if (wide) 90.dp else 60.dp // --cw: a cover's width
    val fanRight = if (!wide) 12.dp else if (band) 24.dp else 150.dp
    val fanBottom = if (wide) 22.dp else 16.dp // below the card's foot, cut by its corner
    val fanH = if (wide) 150.dp else 100.dp
    val coverR = if (wide) 14.dp else 12.dp
    val padTop = if (wide) 22.dp else 16.dp
    val padSide = if (wide) 24.dp else 16.dp
    val padBottom = if (wide) 20.dp else 16.dp
    val title = if (wide) 34.sp else 28.sp
    val statGap = if (wide) 28.dp else 14.dp
    val statB = if (wide) 32.sp else 23.sp
    val statSmall = if (wide) 12.sp else 11.sp
    /** How much less than the card's inside the numbers may take (the fan's room). */
    val statCut = fw + if (!wide) 6.dp else if (band) 30.dp else 170.dp
    val sideCut = fw + if (!wide) 4.dp else if (band) 30.dp else 170.dp
    val line = if (wide) 15.sp else 13.sp
    val lineSmall = if (wide) 12.5.sp else 11.sp
    val lineEm = if (wide) 13.sp else 11.5.sp
    val lineTop = if (wide) 2.dp else 0.dp
    val tagGap = if (wide) 6.dp else 5.dp
    val tagTop = if (wide) 6.dp else 4.dp
    val tagFont = if (wide) 12.sp else 11.sp
    val tagV = if (wide) 4.dp else 3.dp
    val tagH = if (wide) 11.dp else 9.dp
    val chartH = if (wide) 40.dp else 26.dp
    val barGap = if (wide) 4.dp else 3.dp
    val barW = if (wide) 9.dp else 6.dp
    val barR = if (wide) 4.dp else 3.dp
    val chartSmall = if (wide) 11.sp else 10.sp
    val chartGap = if (wide) 5.dp else 3.dp
    val mixTop = if (wide) 14.dp else 10.dp
    val legend = if (wide) 11.5.sp else 10.5.sp
    val art = if (wide) .64f else .72f
}

private const val DEAL_MS = 1000f

/**
 * Each screen's numbers for this copy of your games and this day, worked out once: a tab switch draws its card from
 * here instead of redoing the rankings while the slash plays.
 */
private object Numbers {
    var lib: Library? = null
    val got = HashMap<String, Shx?>()
}

private fun numbers(kind: String, lib: Library, now: Long, zone: ZoneId, day: String): Shx? = synchronized(Numbers) {
    if (Numbers.lib !== lib) { Numbers.lib = lib; Numbers.got.clear() }
    Numbers.got.getOrPut("$kind@$day") { runCatching { Shx.of(kind, lib, now, zone) }.getOrNull() }
}

/** Each screen's cover set when its card was last drawn (`SHX_SEEN`): the deal plays only when it changes. */
private val SEEN = HashMap<String, String>()

/**
 * A screen's header card (v194 + v210): its title, live numbers, a fanned hand of that screen's covers and a glow in
 * the first game's colour, plus the extras you keep on (`S.shxX`): the spotlight line, top genres, the 6-month chart,
 * the mix bar and the backdrop art. Off (`S.shxOff`), a plain title. The covers deal in and the chart grows only when
 * the screen's covers changed (never when calm), on transform and opacity.
 */
@Composable
fun HeaderCard(kind: String, title: String, lib: Library, screen: Dp) {
    val prefs = LocalPrefs.current
    if (prefs.shxOff) {
        PlainTitle(title, screen >= RAIL_FROM)
        return
    }
    val now = LocalNow.current()
    val zone = remember { ZoneId.systemDefault() }
    val day = JsDate.todayLocal(now, zone)
    // a record the maths can't read leaves a plain title, as the web's try/catch around shxData does
    val shx = remember(kind, lib, day) { numbers(kind, lib, now, zone, day) }
    if (shx == null) {
        PlainTitle(title, screen >= RAIL_FROM)
        return
    }
    val d = remember(screen) { Dims(screen) }
    val look = LocalLook.current
    val calm = LocalCalm.current
    val on = prefs::shxOn
    val covers = if (on("fan")) shx.covers.take(4) else emptyList()
    val sig = shx.covers.joinToString("|") { it.id + ":" + Backup.str(it["img"]) }
    val deal = remember(kind, sig) { (SEEN[kind] != sig).also { SEEN[kind] = sig } } && !calm
    // one clock for the whole card: the covers deal (70ms apart, 550ms each) and the bars grow (from 120ms, 50ms apart)
    val t = remember(kind, sig) { Animatable(if (deal) 0f else DEAL_MS) }
    LaunchedEffect(kind, sig) { if (t.value < DEAL_MS) t.animateTo(DEAL_MS, tween(DEAL_MS.toInt(), easing = LinearEasing)) }
    val shc = shx.covers.firstOrNull()?.let { Game.hex(Backup.str(it["colour"])) }?.let { Color(it) } ?: look.volt
    val line = shx.line?.takeIf { on("line") }
    val tags = shx.tags.takeIf { on("tags") && it.isNotEmpty() }
    val chart = shx.chart?.takeIf { on("chart") }
    val bar = shx.bar?.takeIf { on("bar") }
    val art = shx.art?.takeIf { on("art") }
    val shape = RoundedCornerShape(26.dp)
    val minH = if (d.wide) 172.dp else if (chart != null) 190.dp else 138.dp

    BoxWithConstraints(
        Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .heightIn(min = minH)
            .shadow(14.dp, shape, ambientColor = Color.Transparent, spotColor = Pn.shade)
            .clip(shape)
            .cardGround(art != null, shc)
            .border(1.dp, Color(Web.SHX_EDGE), shape)
            .semantics { contentDescription = title },
    ) {
        val inner = maxWidth - d.padSide * 2
        art?.let { a ->
            Box(Modifier.matchParentSize()) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(d.art)) {
                    // a backdrop that can't load shows nothing, as on the web
                    PicBox(a, null, Modifier.fillMaxSize().clipToBounds(), alpha = .4f)
                    Box(Modifier.fillMaxSize().artFade())
                }
            }
        }
        Box(Modifier.matchParentSize().glow(shc, if (art != null) .55f else 1f))
        if (covers.isNotEmpty()) {
            Box(Modifier.matchParentSize()) {
                Fan(covers, d, t, Modifier.align(Alignment.BottomEnd).offset(x = -d.fanRight, y = d.fanBottom))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minH)
                .padding(start = d.padSide, end = d.padSide, top = d.padTop, bottom = d.padBottom),
        ) {
            // the web's grid: five rows (title, line, genres, numbers, mix bar) 4 apart, empty ones too; the room a
            // short card has left goes above the numbers, which sit at the foot
            val gap = @Composable { Spacer(Modifier.height(4.dp)) }
            val titleText = @Composable { m: Modifier ->
                BasicText(title, style = Type.title(d.wide).copy(fontSize = d.title, lineHeight = d.title * 1.1f), modifier = m)
            }
            val lineAndTags = @Composable { full: Boolean ->
                // phones: with a row below them (genres, the mix bar) the line and genres can run the full width
                val lineMax = when {
                    !d.wide && chart != null -> inner
                    !d.wide && (tags != null || bar != null) -> inner
                    !d.wide -> inner - d.sideCut
                    tags != null || bar != null -> inner - 30.dp
                    else -> inner - d.sideCut
                }
                val tagMax = if (!d.wide && (chart != null || bar != null)) inner else inner - d.sideCut
                Box(Modifier.then(if (full) Modifier.fillMaxWidth() else Modifier.widthIn(max = lineMax))) { line?.let { Spotlight(it, d, look.volt) } }
                gap()
                Box(Modifier.then(if (full) Modifier.fillMaxWidth() else Modifier.widthIn(max = tagMax))) { tags?.let { Genres(it, d) } }
            }
            if (!d.wide && chart != null) {
                // phones: no room beside the title, so the chart sits beside the line and genres, above the fan
                titleText(Modifier)
                gap()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { lineAndTags(true) }
                    Chart(chart, d, look.volt, t, Modifier.padding(top = 6.dp, end = 4.dp))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    titleText(Modifier.weight(1f))
                    if (chart != null) Chart(chart, d, look.volt, t, Modifier.padding(top = 4.dp, end = 14.dp))
                }
                gap()
                lineAndTags(false)
            }
            gap()
            Spacer(Modifier.weight(1f))
            Stats(shx.stats, d, look.volt, Modifier.padding(top = if (line != null || tags != null || chart != null) 12.dp else 16.dp).widthIn(max = inner - d.statCut))
            gap()
            Box(Modifier.widthIn(max = if (d.wide) minOf(420.dp, inner - d.sideCut) else inner - d.sideCut)) { bar?.let { MixBar(it, d, look.volt) } }
        }
    }
}

/** A screen's plain title (`.tabbar h3`), for header cards off and the screens without one. */
@Composable
fun PlainTitle(title: String, wide: Boolean) {
    BasicText(title, style = Type.title(wide), modifier = Modifier.padding(bottom = 14.dp))
}

/** `linear-gradient(120deg, --u-pan 38%, mix(--shc 16%, --u-pan))`, or plain `--u-pan` under backdrop art. */
private fun Modifier.cardGround(art: Boolean, shc: Color) = drawWithCache {
    val w = size.width
    val h = size.height
    // CSS's 120deg line: through the centre, as long as the box's corners need
    val dx = .8660254f
    val dy = .5f
    val half = (w * dx + h * dy) / 2
    val c = Offset(w / 2, h / 2)
    val brush = if (art) null else Brush.linearGradient(
        0f to Pn.pan, .38f to Pn.pan, 1f to mix(Pn.pan, shc, .16f),
        start = c - Offset(dx * half, dy * half), end = c + Offset(dx * half, dy * half),
    )
    onDrawBehind { if (brush != null) drawRect(brush) else drawRect(Pn.pan) }
}

/** `.shx-glow`: a circle 78% of the card wide, 18% past its right edge and 55% above its top, in the first cover's colour. */
private fun Modifier.glow(shc: Color, a: Float) = drawWithCache {
    val s = size.width * .78f
    val centre = Offset(size.width * 1.18f - s / 2, -.55f * size.height + s / 2)
    // farthest-corner of a circle in its square box is s/√2; the colour is gone at 66% of it
    val r = s / 1.4142135f * .66f
    val brush = Brush.radialGradient(0f to shc.copy(alpha = .42f), 1f to shc.copy(alpha = 0f), center = centre, radius = r)
    onDrawBehind { drawCircle(brush, r, centre, alpha = a) }
}

/** `.shx-art::after`: the picture fades into the card from the left and towards the foot. */
private fun Modifier.artFade() = drawWithCache {
    val across = Brush.horizontalGradient(0f to Pn.pan, .4f to Pn.pan.copy(alpha = .55f), .75f to Color.Transparent)
    val up = Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Pn.pan.copy(alpha = .7f))
    onDrawBehind {
        drawRect(across)
        drawRect(up)
    }
}

/** POS: each cover's place in the fan (k), its turn and how far it drops; the first, most relevant, sits in front on the right. */
private val POS = listOf(Triple(3, 14f, 10f), Triple(2, 4f, 1f), Triple(1, -6f, 4f), Triple(0, -15f, 14f))

@Composable
private fun Fan(covers: List<Game>, d: Dims, t: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    val n = covers.size
    val open = LocalOpenGame.current
    // each cover where it's drawn (turned and dealt): a tap flies that copy onto the game's page
    val at = remember { arrayOfNulls<LayoutCoordinates>(4) }
    Box(modifier.size(d.fw, d.fanH)) {
        // drawn back to front: the first cover last, on top (z-index 9 - i)
        for (i in covers.indices.reversed()) {
            val (k, r, y) = POS[i]
            // a phone holds three covers; the fourth joins on wider screens
            if (!d.wide && k == 0) continue
            val left = if (d.wide) (d.fw - d.cw) * k / 3 else (d.fw - d.cw) * (k - 1) / 2
            val g = covers[i]
            val delay = (n - 1 - i) * 70f
            val shape = RoundedCornerShape(d.coverR)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = left)
                    .size(d.cw, d.cw * 4 / 3)
                    .graphicsLayer {
                        val p = Ease.house.transform(((t.value - delay) / 550f).coerceIn(0f, 1f))
                        val yPx = y.dp.toPx()
                        translationX = 40.dp.toPx() * (1 - p)
                        translationY = 50.dp.toPx() + (yPx - 50.dp.toPx()) * p
                        rotationZ = r + 16f * (1 - p)
                        alpha = p
                        transformOrigin = TransformOrigin(.5f, 1.1f)
                    }
                    .onGloballyPositioned { at[i] = it }
                    .clickable(role = Role.Button) { open(g.id, at[i]?.takeIf { it.isAttached }?.let { FlyFrom(it.boxInWindow(), g) }) }
                    .semantics { contentDescription = g.title }
                    .shadow(12.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
                    .clip(shape)
                    .border(1.dp, Pn.white.copy(alpha = .09f), shape),
            ) {
                PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Color(Web.SHX_COVER))
            }
        }
    }
}

/** The numbers: the first in your accent with its glow; a small grey unit after a number ("h", "d"). */
@Composable
private fun Stats(stats: List<Shx.Stat>, d: Dims, volt: Color, modifier: Modifier) {
    val px = LocalDensity.current.density
    ShrinkRow(d.statGap, modifier) {
        stats.forEachIndexed { i, s ->
            Column {
                val big = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.Bold, fontSize = d.statB, lineHeight = d.statB, letterSpacing = (-0.02).em, color = if (i == 0) volt else Pn.ice)
                BasicText(
                    buildAnnotatedString {
                        append(s.value)
                        s.unit?.let { u ->
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = d.statB * .56f, fontWeight = FontWeight.SemiBold, color = Pn.mute))
                            append(u)
                            pop()
                        }
                    },
                    style = if (i == 0) big.copy(shadow = Shadow(volt.copy(alpha = .38f), Offset(0f, 6f * px), 24f * px)) else big,
                    softWrap = false, maxLines = 1,
                )
                BasicText(
                    s.label,
                    style = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.Medium, fontSize = d.statSmall, lineHeight = d.statSmall * 1.2f, color = Pn.mute),
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

/**
 * A flex row (`display: flex` with `min-width: 0` items): each item at its natural width; when they don't fit, each
 * gives back in proportion to its width, so a long label wraps instead of pushing the numbers under the fan.
 */
@Composable
private fun ShrinkRow(gap: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->
        val g = gap.roundToPx()
        val natural = ms.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val total = natural.sum() + g * (ms.size - 1).coerceAtLeast(0)
        val over = if (c.hasBoundedWidth) (total - c.maxWidth).coerceAtLeast(0) else 0
        val sum = natural.sum().coerceAtLeast(1)
        val ps = ms.mapIndexed { i, m ->
            val w = (natural[i] - over.toLong() * natural[i] / sum).toInt().coerceAtLeast(0)
            m.measure(Constraints(maxWidth = w))
        }
        val width = (ps.sumOf { it.width } + g * (ps.size - 1).coerceAtLeast(0)).coerceIn(c.minWidth, if (c.hasBoundedWidth) c.maxWidth else Int.MAX_VALUE)
        val height = ps.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            for (p in ps) { p.place(x, 0); x += p.width + g }
        }
    }
}

/** The spotlight line (`.shx-ln`): a kicker in your accent, the game's name cut short if it must, and a grey tail. */
@Composable
private fun Spotlight(l: Shx.Line, d: Dims, volt: Color) {
    val base = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.Medium, fontSize = d.line, lineHeight = d.line * 1.3f, color = Pn.ice)
    val open = LocalOpenGame.current
    // a line, not a cover: the page grows in without a flight (the web finds no cover-shaped picture in it)
    Row(
        Modifier.heightIn(min = 26.dp).padding(top = d.lineTop).clickable(role = Role.Button) { open(l.g.id, null) }.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(l.k, style = base.copy(fontSize = d.lineSmall, fontWeight = FontWeight.SemiBold, color = volt), softWrap = false, maxLines = 1, modifier = Modifier.alignByBaseline())
        BasicText(l.g.title, style = base.copy(fontWeight = FontWeight.SemiBold), softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
        if (l.x.isNotEmpty()) BasicText(l.x, style = base.copy(fontSize = d.lineEm, color = Pn.mute), softWrap = false, maxLines = 1, modifier = Modifier.alignByBaseline())
    }
}

/** Top genres (`.shx-tg`): each with how many of the screen's games have it. */
@Composable
private fun Genres(tags: List<Pair<String, Int>>, d: Dims) {
    val shape = RoundedCornerShape(99.dp)
    FlowRow(Modifier.padding(top = d.tagTop), horizontalArrangement = Arrangement.spacedBy(d.tagGap), verticalArrangement = Arrangement.spacedBy(d.tagGap)) {
        for ((name, n) in tags) {
            Row(
                Modifier
                    .background(Color(Web.SHX_TAG), shape)
                    .border(1.dp, Color(Web.SHX_TAG_EDGE), shape)
                    .padding(horizontal = d.tagH, vertical = d.tagV),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val s = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.Medium, fontSize = d.tagFont, lineHeight = d.tagFont * 1.5f, color = Pn.ice)
                BasicText(name, style = s, softWrap = false, maxLines = 1)
                BasicText(n.toString(), style = s.copy(color = Pn.mute), softWrap = false, maxLines = 1)
            }
        }
    }
}

/** Six months of counts as bars (the last highest in your accent), growing from the foot when the covers deal. */
@Composable
private fun Chart(c: Shx.Chart, d: Dims, volt: Color, t: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    val mx = c.v.max().coerceAtLeast(1)
    val last = c.v.lastIndexOf(c.v.max())
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(d.chartGap)) {
        Row(Modifier.height(d.chartH), horizontalArrangement = Arrangement.spacedBy(d.barGap), verticalAlignment = Alignment.Bottom) {
            c.v.forEachIndexed { i, v ->
                val h = maxOf(.08f, v.toFloat() / mx)
                Box(
                    Modifier
                        .width(d.barW)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val p = Ease.house.transform(((t.value - 120f - i * 50f) / 600f).coerceIn(0f, 1f))
                            scaleY = h * p
                            transformOrigin = TransformOrigin(.5f, 1f)
                        }
                        .background(if (i == last) volt else Color(Web.SHX_BAR), RoundedCornerShape(d.barR)),
                )
            }
        }
        BasicText(c.cap, style = TextStyle(fontFamily = Fonts.poppins, fontSize = d.chartSmall, lineHeight = d.chartSmall, color = Pn.mute), softWrap = false, maxLines = 1)
    }
}

/** The mix bar (`.shx-mx`): one segment per part, sized by its count, and a legend line with coloured dots. */
@Composable
private fun MixBar(segs: List<Shx.Seg>, d: Dims, volt: Color) {
    val colour = { c: Int ->
        when (c) {
            0 -> volt
            1 -> volt.copy(alpha = .55f)
            2 -> volt.copy(alpha = .28f)
            3 -> Color(Web.SHX_MIX3)
            else -> Color(Web.SHX_MIX4)
        }
    }
    Column(Modifier.padding(top = d.mixTop), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (s in segs) Box(Modifier.weight(s.n.toFloat()).fillMaxHeight().background(colour(s.c)))
        }
        // each dot 6 wide with 4 after it, in the line's own text so the line still ends in "…" when it's too long
        val dots = segs.associate { s ->
            "d${s.c}" to InlineTextContent(Placeholder(10.sp, 6.sp, PlaceholderVerticalAlign.Center)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { Box(Modifier.size(6.dp).background(colour(s.c), CircleShape)) }
            }
        }
        BasicText(
            buildAnnotatedString {
                segs.forEachIndexed { i, s ->
                    appendInlineContent("d${s.c}")
                    append("${s.n} ${s.label}")
                    if (i < segs.size - 1) append("\u2003") // an em space: the web's 9px between parts
                }
            },
            inlineContent = dots,
            style = TextStyle(fontFamily = Fonts.poppins, fontSize = d.legend, lineHeight = d.legend * 1.3f, color = Pn.mute),
            softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
