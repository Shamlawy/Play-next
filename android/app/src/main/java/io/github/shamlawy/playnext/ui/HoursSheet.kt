package io.github.shamlawy.playnext.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Hours
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Lookup
import io.github.shamlawy.playnext.data.Practice
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.text.NumberFormat
import java.util.Locale

/** HowLongToBeat's fields into the practice copy for game [id] (its hours, `hrsSrc`): every screen follows at once. */
internal fun hoursFill(id: String): (Map<String, JsonElement>) -> Unit = { f ->
    Games.change(mapOf(Practice.GAMES to Practice.fields(Games.practiced(Practice.GAMES), id, f)))
}

/**
 * "How long to beat" (`hlSheet`, index.html 23456): the game's name, then HowLongToBeat's three numbers with the one
 * that matches how you play lit, how many players' times they come from (and HowLongToBeat's own name for the game when
 * it differs), your own numbers when they differ with Use HowLongToBeat's / Keep mine, or a line saying your plans
 * follow it; else asking / not found / couldn't reach / not checked yet. Then the game on howlongtobeat.com and Check
 * again. It asks by itself when there's no answer yet. [said] puts the toast at the foot ("Kept yours").
 */
@Composable
fun HoursSheet(lib: Library, g: Game, now: () -> Long, close: () -> Unit, said: (String) -> Unit) {
    val tick by Hours.seen.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val off = Hours.off(lib)
    val fill = hoursFill(g.id)
    LaunchedEffect(g.id) {
        if (!off && Hours.of(g) == null && !Hours.busy(g)) Hours.fetch(lib, g, now, force = true, fill = fill)
    }
    // read through the counter, so an answer or a busy state redraws the sheet
    val h = tick.let { Hours.of(g) }
    val busy = tick.let { Hours.busy(g) }
    val volt = LocalLook.current.volt
    DyFrame(Modifier, "How long to beat", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:clock", volt, 21.dp, null)
            BasicText("How long to beat", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        BasicText(g.title, style = DyType.q, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        val line = DyType.q.copy(lineHeight = 19.sp)
        when {
            off -> BasicText("Hours from HowLongToBeat are off (Settings → Extra features).", style = line)
            busy && h?.has != true -> BasicText("Asking HowLongToBeat…", style = line)
            h != null && h.has -> {
                Numbers(h, Backup.str(lib["playStyle"]))
                val by = (if (h.cnt > 0) "From ${NumberFormat.getIntegerInstance(Locale.getDefault()).format(h.cnt)} players' times" else "Players' times") +
                    " on HowLongToBeat" + (if (h.n.isNotEmpty() && !Lookup.titleMatch(g.title, h.n)) " · as “${h.n}”" else "")
                BasicText(by, style = line, modifier = Modifier.padding(top = 10.dp))
                val src = Hours.src(g)
                val mine = Backup.num(g["hours"], 0.0, -1e15, 1e15) > 0 && !Hours.same(g, h)
                if (mine) {
                    Column(
                        Modifier.padding(top = 12.dp).fillMaxWidth().background(Pn.card, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BasicText(
                            buildAnnotatedString {
                                append("Yours: ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) {
                                    append(listOf("hours", "hoursX", "hours100").joinToString(" · ") { Hours.fmt(Backup.num(g[it], 0.0, -1e15, 1e15)) } + " h")
                                }
                                if (src == "me") append(" · typed by you")
                            },
                            style = line,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HBtn("Use HowLongToBeat's", main = true, modifier = Modifier.weight(1f)) {
                                Crumbs.tap("use hltb hours", "hours sheet")
                                Hours.use(g, h)?.let(fill)
                                Sounds.play(Sfx.Add)
                                said("Using HowLongToBeat's hours")
                            }
                            if (Hours.canKeep(g, h)) HBtn("Keep mine", modifier = Modifier.weight(1f)) {
                                Crumbs.tap("keep my hours", "hours sheet")
                                fill(Hours.keep(h))
                                Sounds.play(Sfx.Tap)
                                said("Kept yours")
                            }
                        }
                    }
                } else if (src == "hltb") {
                    BasicText("✓ Your plans use these numbers and follow HowLongToBeat by themselves.", style = line.copy(color = Pn.ice), modifier = Modifier.padding(top = 10.dp))
                }
            }
            h != null && h.none -> BasicText("HowLongToBeat doesn't have this game yet (or not under this name). I'll look again in two weeks.", style = line)
            h != null && h.err.isNotEmpty() -> BasicText("Couldn't reach HowLongToBeat just now. I'll try again soon.", style = line)
            else -> BasicText("Not checked yet.", style = line)
        }
        Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HBtn(if (h != null && h.id > 0) "HowLongToBeat ↗" else "Search it ↗", modifier = Modifier.weight(1f)) {
                Crumbs.tap("open hltb", "hours sheet")
                Sounds.play(Sfx.Tap)
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Hours.url(g))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            if (!off) HBtn("Check again", enabled = !busy, modifier = Modifier.weight(1f)) {
                Crumbs.tap("check hltb again", "hours sheet")
                Sounds.play(Sfx.Tap)
                scope.launch { Hours.fetch(lib, g, now, force = true, fill = fill) }
            }
        }
    }
}

/** `.hl-n`: three cards (Main story / Story + extras / Everything), the one that matches how you play ringed in your accent. */
@Composable
private fun Numbers(h: Hours.Hl, style: String) {
    val st = Web.PLAY_STYLES.firstOrNull { it.first == style }?.first ?: "main"
    val volt = LocalLook.current.volt
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, s, l) in listOf(Triple(h.m, "main", "Main story"), Triple(h.x, "extras", "Story + extras"), Triple(h.c, "all", "Everything"))) {
            val on = st == s
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).background(Pn.card, shape).border(if (on) 2.dp else 1.dp, if (on) volt else Pn.line, shape)
                    .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)
                    .semantics(mergeDescendants = true) { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    buildAnnotatedString {
                        if (v > 0) {
                            append(Hours.fmt(v))
                            withStyle(SpanStyle(fontSize = 14.sp, color = Pn.mute)) { append("h") }
                        } else append("—")
                    },
                    style = Type.stat.copy(fontSize = 24.sp, lineHeight = 26.sp, color = Pn.white),
                    maxLines = 1, softWrap = false,
                )
                BasicText(l, style = Type.note.copy(fontSize = 11.5.sp, lineHeight = 15.sp, color = Pn.ice), maxLines = 1, softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.5.sp, stepSize = .5.sp),
                    modifier = Modifier.padding(top = 4.dp))
                if (on) BasicText("how you play", style = Type.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp, color = volt), maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** `.btn` / `.btn.ghost` in the sheet: 48 tall, round; the accent for Use HowLongToBeat's. */
@Composable
private fun HBtn(text: String, modifier: Modifier = Modifier, main: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val volt = LocalLook.current.volt
    val ink = when { !enabled -> Pn.mute; main -> Pn.void; else -> Pn.ice }
    Box(
        modifier.sizeIn(minHeight = Pn.touch).background(if (main) volt else Pn.card, Pn.pill).then(if (main) Modifier else Modifier.border(1.dp, Pn.line2, Pn.pill))
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.semantics { disabled() })
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.button.copy(color = ink), maxLines = 1, softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = .5.sp))
    }
}
