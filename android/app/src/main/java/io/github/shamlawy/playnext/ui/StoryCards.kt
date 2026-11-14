package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Drift
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Notes
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.data.Recap
import io.github.shamlawy.playnext.data.Story
import io.github.shamlawy.playnext.data.ThenNow
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.delay

/**
 * Phase 2 piece 3b's cards under Your progress (android/parity/2-map/1-page.md §5.4-5.7). Inside the section the
 * forecast and Then & now lose their own box and title (`.gd-sec .eta, .gd-sec .cap`); the story so far and the
 * Drifting card keep theirs on the section's lift.
 */

/** The Finish forecast (`etaCard`): "Done around 14 Nov · 12h left", what it goes by, and a clash in its red box. */
@Composable
internal fun ForecastCard(words: Triple<List<Pair<String, Boolean>>, String, String?>) {
    val (line, by, clash) = words
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PnIcon("ui:flag", LocalLook.current.volt, 17.dp, null, Modifier.padding(top = 1.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString { for ((t, b) in line) if (b) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t) },
                    style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp).fixed(),
                )
                BasicText(by, style = Type.note.copy(fontSize = 12.sp, lineHeight = 17.sp).fixed())
            }
        }
        if (clash != null) {
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().background(Color(Web.ETA_CLASH), shape).border(1.dp, Color(Web.ETA_CLASH_EDGE), shape).padding(horizontal = 11.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PnIcon("ui:warn", Color(Web.ETA_CLASH_EDGE).copy(alpha = 1f), 16.dp, null, Modifier.padding(top = 2.dp))
                BasicText(clash, style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp).fixed(), modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Then & now (`capCard`): its cells in a grid (`repeat(auto-fit, minmax(110px, 1fr))`, gap 8), each a big value over a
 * small caption, then your first note and your last one in quotes.
 */
@Composable
internal fun ThenNowCard(c: ThenNow.Card, now: Long) {
    Column(Modifier.padding(top = 10.dp).fillMaxWidth()) {
        if (c.cells.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // auto-fit: as many 110 columns as fit, never more than there are cells (empty tracks collapse)
                val cols = ((maxWidth.value + 8) / 118).toInt().coerceIn(1, c.cells.size)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in c.cells.chunked(cols)) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((v, l) in row) {
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().background(Color(Web.CAP_CELL), RoundedCornerShape(14.dp)).padding(10.dp)
                                        .semantics(mergeDescendants = true) { },
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    BasicText(v, style = Type.head.copy(fontSize = 18.sp, lineHeight = 19.8.sp, fontWeight = FontWeight.Bold, color = Pn.ice).fixed())
                                    BasicText(l, style = Type.note.copy(fontSize = 11.5.sp, lineHeight = 15.sp).fixed())
                                }
                            }
                            // a short last row keeps the columns' width (the grid's empty cells)
                            repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        for ((n, head) in listOfNotNull(c.first?.let { it to "Your first note" }, c.last?.let { it to "Your last note" })) {
            Column(Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) { }) {
                BasicText(head + " · " + Notes.ago(n.t, now), style = Type.note.copy(fontSize = 11.5.sp, lineHeight = 15.sp).fixed(), modifier = Modifier.padding(bottom = 2.dp))
                BasicText("“" + n.x + "”", style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp).fixed())
            }
        }
    }
}

/**
 * The story so far (`wiStoryHtml`): under the note, "The story so far · <where>" and the recap in four lines with Read
 * more when it's cut (a tap on the words also opens and closes it, v244); while it's being written, three bars that
 * pulse (opacity only, still when calm). Asked for 600 ms after the page shows a note with none yet.
 */
@Composable
internal fun StoryCard(lib: Library, g: Game, still: Boolean) {
    val tick by Recap.seen.collectAsState()
    val n = Notes.last(g) ?: return
    val now = LocalNow.current
    // before the error check: an error 6 hours old is asked again (wiStoryGet on every page open), and [tick]
    // re-asks when a fetch for an older note held this one off
    LaunchedEffect(g.id, n.t, tick) {
        delay(600)
        Recap.fetch(lib, g, now)
    }
    if (!Recap.shows(lib, g)) return
    val c = remember(tick, g, n.t) { Recap.of(lib, g) }
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().background(Color(Web.GD_LIFT), shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.padding(bottom = 6.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PnIcon("ui:book", volt, 15.dp, null)
            val head = if (c != null && c.x.isNotEmpty()) "The story so far" + (if (c.w.isNotEmpty()) " · " + c.w else "") else "Remembering the story so far…"
            BasicText(head, style = Type.label.copy(fontSize = 12.sp, lineHeight = 16.sp, color = volt).fixed(), modifier = Modifier.weight(1f))
        }
        if (c == null || c.x.isEmpty()) {
            Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                val pulse = if (still) null else rememberInfiniteTransition(label = "story")
                for ((i, w) in listOf(1f, .86f, .6f).withIndex()) {
                    val a = pulse?.animateFloat(.35f, 1f, infiniteRepeatable(tween(1200, delayMillis = 0), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(150 * i)), label = "bar$i")
                    Box(Modifier.fillMaxWidth(w).height(10.dp).graphicsLayer { alpha = a?.value ?: 1f }.background(Color(Web.WIS_DOT), RoundedCornerShape(6.dp)))
                }
            }
        } else {
            var open by remember(c.x) { mutableStateOf(false) }
            var cut by remember(c.x) { mutableStateOf(false) }
            val fade = remember(c.x) { Animatable(1f) }
            LaunchedEffect(open) { if (open && !still) { fade.snapTo(.45f); fade.animateTo(1f, tween(420)) } }
            val flip = {
                if (cut || open) {
                    Sounds.play(Sfx.Tap)
                    open = !open
                }
            }
            BasicText(
                c.x,
                style = Type.body.copy(fontSize = 14.sp, lineHeight = 21.7.sp, color = Color(Web.WIS_INK)).fixed(),
                maxLines = if (open) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Clip,
                onTextLayout = { if (!open) cut = it.hasVisualOverflow },
                modifier = Modifier.graphicsLayer { alpha = fade.value }.clickable(enabled = cut || open, onClickLabel = if (open) "Read less" else "Read more", onClick = flip),
            )
            if (c.sure < .5) BasicText("I'm not sure exactly where that is in the story, so I kept to what I know for certain.", style = Type.note.copy(fontSize = 12.sp, lineHeight = 17.sp).fixed(), modifier = Modifier.padding(top = 6.dp))
            if (cut || open) {
                Box(Modifier.padding(top = 4.dp).sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = flip), contentAlignment = Alignment.CenterStart) {
                    BasicText(if (open) "Read less" else "Read more", style = Type.label.copy(fontSize = 13.sp, lineHeight = 16.sp, color = volt).fixed())
                }
            }
        }
    }
}

/** What the Drifting card's three buttons do; the page hands them in. */
internal class DriftActs(val short: () -> Unit, val park: () -> Unit, val ok: () -> Unit)

/**
 * The Drifting card (`dwCard`): "You're drifting from this one", the look-alike line, the reasons, and Something short
 * in between / Park it / I'm into it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DriftCard(r: Drift.Risk, acts: DriftActs) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().background(Color(Web.DW_LIFT), shape).border(1.dp, volt.copy(alpha = .35f), shape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PnIcon("ui:drift", volt, 16.dp, null)
            BasicText("You're drifting from this one", style = Type.label.copy(fontSize = 12.5.sp, lineHeight = 16.sp, color = volt).fixed())
        }
        BasicText(Drift.line(r), style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp).fixed())
        Column {
            for (w in r.why) Row {
                BasicText("•", style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 18.sp).fixed(), modifier = Modifier.width(14.dp))
                BasicText(w, style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 18.sp).fixed(), modifier = Modifier.weight(1f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DwBtn("Something short in between", true, acts.short)
            DwBtn("Park it", false, acts.park)
            DwBtn("I'm into it", false, acts.ok)
        }
    }
}

/** A Drifting button (`.dwc .dwc-b button`): a 34 pill (48 to touch), the first in your accent. */
@Composable
private fun DwBtn(label: String, go: Boolean, tap: () -> Unit) {
    val volt = LocalLook.current.volt
    Box(Modifier.sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = tap), contentAlignment = Alignment.Center) {
        Box(
            Modifier.sizeIn(minHeight = 34.dp).clip(Pn.pill)
                .then(if (go) Modifier.background(volt) else Modifier.border(1.dp, Pn.line2, Pn.pill))
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = Type.label.copy(fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.sp, color = if (go) Pn.void else Pn.ice).fixed(), maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Something short in between (`dwShort`): a note, then up to 3 short games from your queue that are out and feel
 * different, each with its cover, "about 6h to beat · two genres", Start it and Open; or a line when there are none.
 */
@Composable
internal fun ShortSheet(q: Queue, g: Game, close: () -> Unit, start: (Game) -> Unit, open: (Game) -> Unit) {
    val list = remember(q, g) { Drift.cleanse(q, g) }
    val volt = LocalLook.current.volt
    DyFrame(Modifier, "Something short in between", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:leaf", volt, 21.dp, null)
            BasicText("Something short in between", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
                PnIcon("ui:close", Pn.mute, 16.dp, null)
            }
        }
        BasicText(
            "Short games from your queue that feel different from ${g.title}. Finish one and I'll bring you back to it, with where you left off.",
            style = DyType.q, modifier = Modifier.padding(top = 6.dp),
        )
        if (list.isEmpty()) {
            BasicText(
                "Your queue has no short games that are out yet (12 hours or less). Parking ${g.title} for a bit is fine too.",
                style = DyType.q.copy(color = Pn.ice), modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (x in list) {
                    Column(Modifier.fillMaxWidth().background(Pn.card, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PicBox(Pic.cover(x), x, Modifier.width(38.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(7.dp)), ground = Color(Web.COVER_BG))
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                                BasicText(x.title, style = DyType.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val gen = Rank.platList(x["genres"]).take(2)
                                BasicText("about ${Story.smH(q.rk.hrsOf(x))} to beat" + (if (gen.isNotEmpty()) " · " + gen.joinToString(", ") else ""), style = DyType.small)
                            }
                        }
                        Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DwBtn("Start it", true) { start(x) }
                            DwBtn("Open", false) { open(x) }
                        }
                    }
                }
            }
        }
    }
}

/** The Drifting card's buttons for [g] on the page: the practice writes, the sounds and the toasts (`dwAct`). */
@Composable
internal fun rememberDriftActs(lib: Library, g: Game, gone: () -> Unit): DriftActs {
    val edit = LocalEditActs.current
    val now = LocalNow.current
    return remember(lib, g, edit) {
        DriftActs(
            short = {
                Crumbs.tap("drifting: something short", "game page")
                Sounds.play(Sfx.Open)
                edit?.short(g.id)
            },
            park = {
                Crumbs.tap("drifting: park it", "game page")
                Games.change(Drift.park(g, now()) { Games.practiced(it) })
                Sounds.play(Sfx.Back)
                edit?.say("Parked ${g.title}. Your notes stay, and the story so far will catch you up when you're back.")
                gone()
            },
            ok = {
                Crumbs.tap("drifting: I'm into it", "game page")
                Games.change(Drift.ok(lib, g, now()) { Games.practiced(it) })
                Sounds.play(Sfx.Tap)
                edit?.say("Good to hear. Enjoy ${g.title}!")
                gone()
            },
        )
    }
}
