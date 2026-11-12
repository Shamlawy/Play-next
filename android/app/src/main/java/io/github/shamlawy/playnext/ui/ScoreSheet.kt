package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.data.Score
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.delay

/**
 * Score it (`qScore`, index.html 13142; android/parity/2-map/1-page.md §5.1): the game (cover, name, a small line, the
 * score now with its tier), the whole points on top and the halves between them under it, "Don't remember". A tap
 * lights the button, writes the practice copy, and the sheet closes 260 ms later. [scored] gets the score you had and
 * the new one ([Score] null: Don't remember) for what comes after (the rank toast, the finish moment).
 */
@Composable
fun ScoreSheet(lib: Library, g: Game, now: () -> Long, close: () -> Unit, scored: (had: Double?, v: Double?, first: Boolean) -> Unit) {
    val rk = remember(lib) { Rank(lib, now(), java.time.ZoneId.systemDefault()) }
    val had = remember(lib, g) { Score.current(rk, g) }
    val dk = remember(lib, g) { rk.playedRec(g)?.let { io.github.shamlawy.playnext.data.Backup.truthy(it.o["dk"]) } == true }
    val line = remember(lib, g) { Score.line(rk, g) }
    // the tapped one lights at once and stays lit while the sheet closes (`b.classList.add("on")`)
    var picked by remember(g.id) { mutableStateOf<Pick?>(null) }
    val end by rememberUpdatedState(close)
    LaunchedEffect(picked) {
        if (picked == null) return@LaunchedEffect
        delay(260)
        end()
    }
    val pick = { v: Double? ->
        if (picked == null) {
            Crumbs.tap("score", "score sheet")
            Sounds.play(Sfx.Add)
            val first = rk.playedRec(g) == null
            Games.change(Score.set(lib, g, v, now()) { Games.practiced(it) })
            picked = Pick(v)
            scored(had, v, first)
        }
    }
    val on: Pick? = picked ?: if (dk) Pick(null) else had?.let { Pick(it) }
    val volt = LocalLook.current.volt
    DyFrame(androidx.compose.ui.Modifier, "Score it", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:star", volt, 21.dp, null)
            BasicText("Score it", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        // the game strip (`.qs-g`): its colour down the left edge
        val accent = Color(g.colour)
        val strip = RoundedCornerShape(14.dp)
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(strip).background(Pn.void)
                .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 11.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(46.dp, 61.dp).clip(RoundedCornerShape(8.dp))) { PicBox(Pic.cover(g), g, Modifier.matchParentSize(), ground = Color(Web.GD_BG)) }
            Column(Modifier.weight(1f)) {
                BasicText(g.title, style = DyType.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(line, style = DyType.small)
            }
            val show = picked?.let { it.v } ?: if (picked == null) had else null
            Row(Modifier.widthIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                BasicText(
                    show?.let { Queue.jsNum(it) } ?: "–",
                    style = Type.stat.copy(fontFamily = Fonts.archivo, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, color = volt).fixed(),
                    maxLines = 1, softWrap = false,
                )
                if (show != null) TierBadge(show, Modifier.padding(start = 6.dp), big = true)
            }
        }
        // the grid (`.qs-grid`): 22 columns, a whole point over two of them, each half over the two between
        BoxWithConstraints(Modifier.padding(top = 14.dp).fillMaxWidth()) {
            val unit = maxWidth / 22
            Box(Modifier.fillMaxWidth().height(44.dp + 6.dp + 34.dp)) {
                for ((i, v) in Score.WHOLE.withIndex()) ScoreKey(v, on == Pick(v), big = true, Modifier.offset(x = unit * (2 * i)).width(unit * 2)) { pick(v) }
                for ((i, v) in Score.HALVES.withIndex()) ScoreKey(v, on == Pick(v), big = false, Modifier.offset(x = unit * (2 * i + 1), y = 50.dp).width(unit * 2)) { pick(v) }
            }
        }
        BasicText(
            "Top row whole points, bottom row halves. 9+ is S tier, 7½+ A, 6+ B.",
            style = DyType.small, modifier = Modifier.padding(top = 8.dp),
        )
        // "Don't remember" (`.dy-more.qs-dk`): dashed in steel, solid in your accent when it's what you said
        val off = on == Pick(null)
        val shape = RoundedCornerShape(12.dp)
        val steel = Pn.steel
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().sizeIn(minHeight = Pn.touch)
                .then(if (off) Modifier.border(1.dp, volt, shape) else Modifier.drawBehind {
                    val w = 1.dp.toPx()
                    drawRoundRect(steel, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(12.dp.toPx()), Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                })
                .clip(shape)
                .clickable(role = Role.Button) { pick(null) }
                .semantics { selected = off }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            PnIcon("ui:unsure", if (off) Pn.ice else Pn.mute, 18.dp, null)
            BasicText("Don't remember", style = DyType.q.copy(color = if (off) Pn.ice else Pn.mute))
        }
    }
}

/** A value picked in the sheet ([v] null: Don't remember). */
private data class Pick(val v: Double?)

/** One key of the grid (`.qs-i` 44 tall, `.qs-h` 34 and quieter); lit in your accent when it's the score. */
@Composable
private fun ScoreKey(v: Double, on: Boolean, big: Boolean, modifier: Modifier, tap: () -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(if (big) 44.dp else 34.dp).padding(horizontal = 2.dp).clip(shape)
            .background(if (on) volt else Pn.void).border(1.dp, if (on) volt else Pn.steel, shape)
            .clickable(role = Role.Button, onClick = tap)
            .semantics { selected = on; contentDescription = Score.label(v) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            Score.label(v),
            style = Type.button.copy(
                fontSize = if (big) 16.sp else 12.sp, lineHeight = if (big) 20.sp else 16.sp, fontWeight = FontWeight.ExtraBold,
                color = if (on) Pn.void else if (big) Pn.ice else Pn.mute,
            ).fixed(),
            maxLines = 1, softWrap = false,
        )
    }
}

/**
 * The tier's lettered chip (`tierBadge`, `.tier.tS|tA|tB|tC`): S gold, A your accent, B a soft accent with its edge, C
 * just an edge. [big] is the sheet's (13, `.qs-v .tier`).
 */
@Composable
fun TierBadge(v: Double, modifier: Modifier = Modifier, big: Boolean = false) {
    val t = Rank.tierOf(v)
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(6.dp)
    val (bg, ink, edge) = when (t) {
        "S" -> Triple(Color(Web.TIER_S), Color(Web.TIER_S_INK), null)
        "A" -> Triple(volt, Pn.void, null)
        "B" -> Triple(volt.copy(alpha = .22f), Pn.ice, volt.copy(alpha = .5f))
        else -> Triple(Color.Transparent, Pn.mute, Pn.steel)
    }
    Box(
        modifier.sizeIn(minWidth = 20.dp).height(20.dp).background(bg, shape).then(if (edge != null) Modifier.border(1.dp, edge, shape) else Modifier)
            .padding(horizontal = 4.dp).semantics { contentDescription = "$t tier" },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(t, style = Type.label.copy(fontFamily = Fonts.archivo, fontSize = if (big) 13.sp else 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.sp, color = ink).fixed(), maxLines = 1, softWrap = false)
    }
}
