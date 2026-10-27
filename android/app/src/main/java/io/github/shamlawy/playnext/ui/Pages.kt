package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.JsDate
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.update.WhatsNew
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What each tab shows until its own phase builds it: Home your games at a glance, Games the list as it came from the
 * backup, the others what's coming. Every tab already has its place, its icon and its scroll spot.
 */
@Composable
fun TabPage(tab: String, games: Games.State, wide: Boolean, askCode: () -> Unit) {
    when (tab) {
        "home" -> HomePage(games, wide, askCode)
        "games" -> GamesPage(games, wide, askCode)
        "plan" -> Later("Plan", "Month, Timeline, Plan and Timer arrive in phase 5.", wide)
        "taste" -> Later("Taste", "Insights, Duels, Friend and the skills book arrive in phase 6.", wide)
        "add" -> Later("Add a game", "Search, Discover and the art pickers arrive in phase 4.", wide)
    }
}

/** Home's panel (`.dxw`: `--u-pan`, round 28): the folded tab bar's open tile runs into its top. */
@Composable
private fun HomePage(games: Games.State, wide: Boolean, askCode: () -> Unit) {
    val ctx = LocalContext.current
    val code = BuildConfig.VERSION_CODE
    // "Updated to vN" once, at the first open after an update (no notification until Phase 9, G15)
    val updated = remember {
        val to = Store.prefs.getInt("updatedTo", 0)
        (to == code && Store.prefs.getInt("updatedSeen", 0) != code).also { if (it) Store.prefs.edit().putInt("updatedSeen", code).apply() }
    }
    val news = remember { runCatching { ctx.assets.open("whatsnew.txt").bufferedReader().use { WhatsNew.parse(it.readText()) } }.getOrDefault(emptyList()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .background(Pn.pan, RoundedCornerShape(28.dp))
            .padding(start = if (wide) 22.dp else 14.dp, end = if (wide) 22.dp else 14.dp, top = if (wide) 22.dp else 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (updated) {
            Card {
                BasicText("Updated to v$code", style = Type.head)
                news.lastOrNull { it.v == code }?.let { BasicText(it.say.replace(Regex("<[^>]+>"), ""), style = Type.note) }
            }
        }
        when (games) {
            is Games.State.Ready -> {
                val all = games.lib.games
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(if (all.size == 1) "1 game" else "${all.size} games", style = Type.title(wide))
                    AsOf(games)
                }
                Counts(games.lib.counts())
                BasicText("Home itself arrives in phase 3. This app is a practice copy: it reads your games and changes nothing.", style = Type.note)
            }
            is Games.State.Fetching -> Waiting(games.why, askCode)
            Games.State.Starting -> BasicText("Opening your games…", style = Type.note)
            Games.State.NoCode -> NoGames(askCode)
        }
    }
}

/** "Your games as of 2:05 PM", and a warning once the copy is over a day old. */
@Composable
private fun AsOf(r: Games.State.Ready) {
    val now = LocalNow.current()
    BasicText(asOfLine(r.copyAt, now), style = Type.note)
    if (now - r.copyAt > JsDate.DAY) {
        BasicText(
            "The web app last backed up over a day ago. If you changed something there since, open it once and it backs up by itself.",
            style = Type.note.copy(color = Pn.hot),
        )
    }
    r.note?.let { BasicText(it, style = Type.note) }
}

/** "Your games as of 2:05 PM" today, "as of Oct 25, 2:05 PM" before (the phone's own time, 12-hour). */
fun asOfLine(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    return "Your games as of " + if (t.toLocalDate() == today) time else t.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + ", " + time
}

/** How many in each state (the web app's STATES words), as stat tiles. */
@Composable
private fun Counts(counts: List<Pair<String, Int>>) {
    val shown = counts.filter { it.second > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in shown.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, n) in row) {
                    Column(Modifier.weight(1f).background(Pn.card, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        BasicText(n.toString(), style = Type.stat, softWrap = false, maxLines = 1)
                        BasicText(label, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Waiting(why: String?, askCode: () -> Unit) {
    val ctx = LocalContext.current
    BasicText("Your games", style = Type.title(false))
    BasicText(why ?: "Fetching your games from the cloud backup…", style = Type.note)
    if (why != null) {
        Btn("Try again", primary = true) { Games.checkNow(ctx) }
        Btn("Use a different restore code") { Games.forget(ctx); askCode() }
    }
}

@Composable
private fun NoGames(askCode: () -> Unit) {
    BasicText("Your games aren't here yet", style = Type.title(false))
    BasicText("Type your restore code once and they come from the web app's cloud backup.", style = Type.note)
    Btn("Type my restore code", primary = true, onClick = askCode)
}

/** Games: the Queue arrives in phase 1; until then, every game in the backup by state, in your order. */
@Composable
private fun GamesPage(games: Games.State, wide: Boolean, askCode: () -> Unit) {
    val ready = games as? Games.State.Ready
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Games", style = Type.title(wide))
            if (ready != null) BasicText(ready.lib.games.size.toString(), style = Type.label.copy(fontWeight = Type.label.fontWeight), modifier = Modifier.padding(bottom = 4.dp))
        }
        if (ready == null) {
            Card { if (games is Games.State.NoCode) NoGames(askCode) else Waiting((games as? Games.State.Fetching)?.why, askCode) }
            return@Column
        }
        BasicText("The Queue arrives in phase 1. Until then, your games as they came from the backup:", style = Type.note)
        val now = LocalNow.current()
        for ((key, label) in Web.STATES) {
            val list = ready.lib.games.filter { it.state == key }.sortedWith(compareBy<Game> { it.ord }.thenBy { it.title })
            if (list.isEmpty()) continue
            Card {
                BasicText("$label · ${list.size}", style = Type.kicker)
                for (g in list) GameLine(g, if (key == "upcoming") JsDate.days(g.date, now).ifEmpty { "No date yet" } else "")
            }
        }
    }
}

/** One game: its colour, its name (two lines at most) and, for coming games, how far away it is. */
@Composable
private fun GameLine(g: Game, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(4.dp, 28.dp).background(Color(g.colour), RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp)))
        Column(Modifier.weight(1f)) {
            BasicText(g.title, style = Type.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) BasicText(sub, style = Type.note, softWrap = false, maxLines = 1)
        }
    }
}

@Composable
private fun Later(title: String, what: String, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, style = Type.title(wide))
        Card { BasicText(what, style = Type.note) }
    }
}
