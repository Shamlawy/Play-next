package io.github.shamlawy.playnext.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Reviews
import io.github.shamlawy.playnext.data.Similar
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs

/** `trailer(title)`: a YouTube search, so it always finds the latest one (the web's counters are left out). */
internal fun openTrailer(ctx: Context, title: String) {
    val u = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode("$title trailer"))
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * The open card's back, at its foot (`simBack`, index.html 13570; Phase 1's check C3): "Like this", up to three of your
 * games most like it (each opens its page), then "More like it" (the Similar sheet) and "Trailer". With none alike the
 * row is just its name and the two chips. Taps only count while the back is showing ([on]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LikeThis(q: Queue, g: Game, on: Boolean) {
    val hits = remember(q, g) { q.sim.like(g, 3) }
    val open = LocalOpenGame.current
    val acts = LocalEditActs.current
    val ctx = LocalContext.current
    Column(Modifier.padding(top = 4.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Like this", style = Type.note.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp).fixed())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            for (h in hits) {
                Column(
                    Modifier.width(64.dp).then(if (on) Modifier.clickable(role = Role.Button) { Crumbs.tap("card back like this", "queue"); Sounds.play(Sfx.Open); open(h.g.id, null) } else Modifier)
                        .semantics { contentDescription = h.g.title },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    PicBox(Pic.cover(h.g), h.g, Modifier.width(52.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)))
                    BasicText(h.g.title, style = Type.note.copy(fontSize = 10.sp, lineHeight = 13.sp, color = Pn.ice).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SimChip("ui:search", "More like it", on) { Crumbs.tap("card back more like it", "queue"); Sounds.play(Sfx.Open); acts?.similar?.invoke(g.id) }
                SimChip("ui:play", "Trailer", on) { Crumbs.tap("card back trailer", "queue"); Sounds.play(Sfx.Tap); openTrailer(ctx, g.title) }
            }
        }
    }
}

/** A small chip with its drawn icon first (the web's `.chip` with "🔍 More like it" / "▶ Trailer"). */
@Composable
private fun SimChip(icon: String, text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.sizeIn(minHeight = Pn.touch).then(if (on) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.background(Pn.card, Pn.pill).border(1.dp, Pn.line2, Pn.pill).padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PnIcon(icon, Pn.ice, 14.dp, null)
            BasicText(text, style = Type.button.copy(fontSize = 12.5.sp, lineHeight = 16.sp, color = Pn.ice).fixed(), maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The Similar sheet (`openSimilar`, the v236 rebuild at index.html 22331): "Like <title>", your games most like it as a
 * strip of covers with how alike (each opens its page; "show them in the Queue" lights the Queue's "Like" tab), Steam
 * players' "More like this" when the game is on Steam, and the trailer. Nexi's own picks (`/similar`, checked on RAWG)
 * come in the next release; Add and Not for me write your games, so they wait for switch day.
 */
@Composable
internal fun SimilarSheet(q: Queue, g: Game, now: () -> Long, close: () -> Unit, toQueue: (String) -> Unit, openGame: (String) -> Unit) {
    val lib = q.lib
    val ctx = LocalContext.current
    val volt = LocalLook.current.volt
    val tick by Similar.seen.collectAsState()
    val loc = remember(q, g) { q.sim.like(g, 8) }
    val onSteam = Reviews.stId(g) != 0L
    val cc = shopCC(lib)
    // a failure shows only once this open's own ask has run (an earlier open's never flashes for a frame)
    var asked by remember(g.id) { mutableStateOf(false) }
    LaunchedEffect(g.id) { if (onSteam) Similar.more(lib, g, cc, now()); asked = true }
    val st = tick.let { Similar.cached(lib, g, now()) }
    val busy = tick.let { Similar.busy(g) }
    val err = tick.let { if (asked) Similar.err[g.id].orEmpty() else "" }
    DyFrame(Modifier, "Like ${g.title}", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:search", volt, 21.dp, null)
            BasicText("Like ${g.title}", style = DyType.head, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { PnIcon("ui:close", Pn.mute, 18.dp, null) }
        }
        if (loc.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("In your library", style = DyType.q.copy(fontWeight = FontWeight.Bold, color = Pn.ice))
                BasicText(" · ", style = DyType.q)
                Box(
                    Modifier.sizeIn(minHeight = Pn.touch).clickable(role = Role.Button) { Crumbs.tap("similar show in queue", "similar sheet"); toQueue("S:" + g.id) },
                    contentAlignment = Alignment.CenterStart,
                ) { BasicText("show them in the Queue", style = DyType.q.copy(color = volt, fontWeight = FontWeight.SemiBold)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (h in loc) {
                    Column(
                        Modifier.width(84.dp).clickable(role = Role.Button) { Crumbs.tap("similar library game", "similar sheet"); openGame(h.g.id) },
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        PicBox(Pic.cover(h.g), h.g, Modifier.width(84.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)))
                        BasicText("${h.g.title} · ${Math.round(h.s * 100)}%", style = DyType.small.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            BasicText("Nothing in your library is close yet.", style = DyType.q, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        }
        if (onSteam) {
            Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Steam players also like", style = DyType.q.copy(fontWeight = FontWeight.Bold, color = Pn.ice))
                BasicText("  Steam's own “More like this”", style = DyType.small.copy(fontSize = 11.5.sp))
            }
            val l = st?.filter { !Similar.nope(lib, it.name) }
            when {
                l == null && (busy || err.isEmpty()) -> BasicText("Asking Steam…", style = DyType.q)
                l == null -> BasicText(if (err == "helper-old") "This switches on once Nexi's helper is updated." else "Couldn't reach Steam right now.", style = DyType.q)
                l.isEmpty() -> BasicText("Steam had nothing to add.", style = DyType.q)
                else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // `.sgl`: columns at least 220 wide, 12 apart
                    val cols = maxOf(1, ((maxWidth.value + 12) / (220 + 12)).toInt())
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (row in l.chunked(cols)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (x in row) SteamCard(lib, Similar.card(x, g), Modifier.weight(1f), openGame)
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        // `.dy-more`: a dashed button the width of the sheet
        Box(
            Modifier.padding(top = 14.dp).fillMaxWidth().sizeIn(minHeight = Pn.touch)
                .drawBehind {
                    drawRoundRect(Pn.steel, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
                .clickable(role = Role.Button) { Crumbs.tap("similar trailer", "similar sheet"); Sounds.play(Sfx.Tap); openTrailer(ctx, g.title) },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PnIcon("ui:play", Pn.mute, 16.dp, null)
                BasicText("Watch the ${g.title} trailer", style = DyType.q.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One of Steam's picks (`sgCards`' card): its picture 16:9 (Steam's capsule, its header when that fails), a round ▶ for
 * its trailer or "In your library ›" when it's yours, the name, why (in your accent) and its first tags.
 */
@Composable
private fun SteamCard(lib: Library, c: Similar.Card, modifier: Modifier, openGame: (String) -> Unit) {
    val ctx = LocalContext.current
    val volt = LocalLook.current.volt
    val have = remember(lib, c.name) { Similar.have(lib, c.name) }
    val shape = RoundedCornerShape(18.dp)
    var src by remember(c.img) { mutableStateOf(c.img) }
    var gone by remember(c.img) { mutableStateOf(false) }
    Column(modifier.clip(shape).background(Pn.card, shape).border(1.dp, Pn.line, shape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Pn.void)) {
            if (!gone) AsyncImage(
                model = src, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onError = { if (src != c.img2) src = c.img2 else gone = true },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.55f to Color.Transparent, 1f to Pn.void.copy(alpha = .7f))))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                if (have != null) {
                    Box(
                        Modifier.sizeIn(minHeight = Pn.touch).clickable(role = Role.Button) { Crumbs.tap("similar in your library", "similar sheet"); openGame(have.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "In your library ›", style = DyType.small.copy(fontWeight = FontWeight.SemiBold, color = Pn.ice),
                            modifier = Modifier.background(Pn.void.copy(alpha = .72f), Pn.pill).border(1.dp, Pn.ice.copy(alpha = .18f), Pn.pill).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    Box(
                        Modifier.size(Pn.touch).clickable(role = Role.Button) { Crumbs.tap("similar card trailer", "similar sheet"); Sounds.play(Sfx.Tap); openTrailer(ctx, c.name) }
                            .semantics { contentDescription = "Trailer for ${c.name}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(40.dp).background(Pn.void.copy(alpha = .72f), Pn.pill).border(1.dp, Pn.ice.copy(alpha = .18f), Pn.pill), contentAlignment = Alignment.Center) {
                            PnIcon("ui:play", Pn.ice, 18.dp, null)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(c.name, style = DyType.name.copy(fontSize = 15.sp, lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (c.why.isNotEmpty()) BasicText(c.why, style = DyType.q.copy(color = volt), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (c.meta.isNotEmpty()) BasicText(c.meta, style = DyType.small.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
