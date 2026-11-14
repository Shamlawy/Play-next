package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Art
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Page
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Reviews
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import java.text.NumberFormat
import java.time.ZoneId

/**
 * Reviews on the game page (`gdReviews` + `rvCard`, index.html 20521, 21969): for a game you haven't played, the
 * critics (your platform's Metacritic when RAWG has one) and Steam's players (or RAWG's players), "checked N days ago",
 * and the line saying how Nexi's guess leans; fresh out with nothing in yet, "Not in yet". A played game shows the
 * critics against your score. In a page section the card's own head and its "You, probably" cell stay hidden (the
 * tile already says it, `.gd-sec .rv-c.me`). The page asks for a fresh game's reviews 900 ms after it opens.
 */
@Composable
internal fun ReviewsSec(g: Game, page: Page, lib: Library, modifier: Modifier) {
    val tick by Reviews.seen.collectAsState()
    val now = LocalNow.current
    val zone = ZoneId.systemDefault()
    val q = page.q
    val country = androidx.compose.ui.platform.LocalLocale.current.platformLocale.country
    LaunchedEffect(g.id) {
        delay(900)
        val t = now()
        if (askOnOpen && Reviews.want(lib, q.rk, g, t, zone) && Reviews.due(g, t, zone) && (Reviews.age(g, t, zone) ?: -1) >= 0) {
            Reviews.fetch(lib, g, Games.rawgKey, Art.cc(lib, java.util.TimeZone.getDefault().id, country), now, practiceFill(g.id))
        }
    }
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val fmt = { v: Double -> NumberFormat.getIntegerInstance(locale).format(v.toLong()) }
    // read through the counter, so an answer that comes in redraws the card
    val rv: JsonObject? = tick.let { Reviews.of(g) }
    val t = now()
    val at = Backup.num(rv?.get("at"), 0.0, 0.0, 1e15).toLong()
    val played = q.rk.isPlayed(g)
    val card = !(Reviews.off(lib) || g.comp || played || g.rawState == "dropped")
    val f = if (card) tick.let { q.rvFor(g) } else null
    val fresh = card && Reviews.want(lib, q.rk, g, t, zone)
    val waiting = card && f == null && fresh && (Reviews.age(g, t, zone) ?: -1) >= 0
    if (f != null || waiting) {
        Section("ui:star", "Reviews", if (at > 0) "checked " + Reviews.ago(at, t) else "", modifier) {
            if (f == null) {
                BasicText("Not in yet. I'm checking every few hours and I'll tell you the moment they land.", style = Type.note.copy(fontSize = 13.sp, lineHeight = 18.sp), modifier = Modifier.padding(top = 16.dp))
            } else {
                val cells = ArrayList<Pair<String, String>>()
                val mcP = Backup.str(rv?.get("mcP"))
                if (f.mc != 0.0) cells += Queue.jsNum(f.mc) to ("Critics" + if (mcP.isNotEmpty()) " · $mcP" else "")
                val st = f.st
                if (st != null) cells += Queue.jsNum(Backup.num(st["p"], 0.0, -1e9, 1e9)) + "%" to (Backup.str(st["d"]).ifEmpty { "Steam players" } + " · " + fmt(Backup.num(st["n"], 0.0, 0.0, 1e15)) + " on Steam")
                else {
                    val us = Backup.num(rv?.get("us"), 0.0, 0.0, 10.0)
                    val un = Backup.num(rv?.get("un"), 0.0, 0.0, 1e15)
                    if (us > 0 && un >= 20) cells += String.format(java.util.Locale.ROOT, "%.1f", us) to "Players · ${fmt(un)} ratings"
                }
                Cells(cells.map { Cell(it.first, it.second) })
                val why = f.why.joinToString("; ")
                val line = (if (why.isNotEmpty()) why.replaceFirstChar { it.titlecase() } + "." else "") +
                    (if (f.sure.isNotEmpty()) (if (why.isNotEmpty()) " Still " else "") + f.sure + "." else "")
                if (line.isNotBlank()) BasicText(line.trim(), style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 18.sp), modifier = Modifier.padding(top = 9.dp))
            }
        }
        return
    }
    // no card: the critics alone, and for a played game you against them
    val critic = Backup.num(g["critic"], 0.0, -1e9, 1e9)
    if (!(critic > 0)) return
    val p = q.rk.playedRec(g)
    Section("ui:star", "Reviews", "", modifier) {
        val cells = mutableListOf(Cell(Backup.str(g["critic"]).ifEmpty { Queue.jsNum(critic) }, "Critics"))
        if (p != null && p.scored) {
            val real = p.real!!
            cells += Cell(Queue.jsNum(real), "You · " + if (real * 10 >= critic) "liked it more" else "liked it less", you = true, tier = real)
        }
        Cells(cells)
    }
}

private class Cell(val big: String, val small: String, val you: Boolean = false, val tier: Double? = null)

/** `.rv-s`: the cells side by side, 10 under the head; `.rv-c` a soft lift (yours tinted in your accent). */
@Composable
private fun Cells(cells: List<Cell>) {
    val volt = LocalLook.current.volt
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in cells) {
            Column(
                Modifier.weight(1f).background(if (c.you) volt.copy(alpha = .1f) else Color.White.copy(alpha = .04f), RoundedCornerShape(14.dp))
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 9.dp).semantics(mergeDescendants = true) { },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(c.big, style = Type.stat.copy(fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Pn.ice), maxLines = 1, softWrap = false)
                    c.tier?.let { TierBadge(it, Modifier.padding(start = 4.dp)) }
                }
                BasicText(c.small, style = Type.note.copy(fontSize = 11.5.sp, lineHeight = 15.sp))
            }
        }
    }
}
