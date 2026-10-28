package io.github.shamlawy.playnext.ui

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.JsDate
import io.github.shamlawy.playnext.kit.Shared
import io.github.shamlawy.playnext.update.WhatsNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What each tab shows until its own phase builds it: Home your games at a glance; every view of Games, Plan and Taste,
 * and Add, its header card and what's coming (the Queue, until phase 1, your games as they came from the backup).
 * Every tab and view already has its place, its icon and its scroll spot.
 */
@Composable
fun TabPage(tab: String, view: String?, games: Games.State, wide: Boolean, screen: Dp, askCode: () -> Unit, pickView: (String) -> Unit) {
    if (tab == "home") {
        HomePage(games, wide, askCode)
        return
    }
    val prefs = LocalPrefs.current
    val views = prefs.views(tab)
    val v = view ?: tab
    val title = if (tab == "add") "Add" else views.firstOrNull { it.first == v }?.second ?: v
    // the header card's screen (`SHX_PANELS`): each Games view its own, Plan and Taste one each; the book has none
    val kind = when (tab) {
        "games" -> v
        "taste" -> if (v == "book") null else "taste"
        else -> tab
    }
    val ready = games as? Games.State.Ready
    if (ready != null && kind != null) HeaderCard(kind, title, ready.lib, screen) else PlainTitle(title, wide)
    if (!prefs.pill && views.isNotEmpty()) ViewChips(views, v, pickView)
    if (ready == null) {
        Card { if (games is Games.State.NoCode) NoGames(askCode) else Waiting((games as? Games.State.Fetching)?.why, askCode) }
        return
    }
    if (tab == "add") LocalShared.current?.let { SharedCard(it) }
    if (v == "queue") QueueSoon(ready) else Card { BasicText(SOON[v] ?: "", style = Type.note) }
}

/** Something shared into the app (a store page, a trailer, a name): the game it names; adding it comes with Add's phase. */
@Composable
private fun SharedCard(s: Shared) {
    Card(Modifier.padding(bottom = 12.dp)) {
        BasicText("From your share", style = Type.kicker)
        BasicText(s.name, style = Type.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText("Finding it and adding it from here comes in phase 4, with Search.", style = Type.note)
    }
}

/** Where each view is built (docs/ANDROID-PLAN.md, the phases). */
private val SOON = mapOf(
    "lib" to "Your library arrives in phase 5.",
    "played" to "Played arrives in phase 5.",
    "replay" to "Replay arrives in phase 5.",
    "month" to "The month calendar arrives in phase 5.",
    "flow" to "The timeline arrives in phase 5.",
    "plan" to "Plan arrives in phase 5.",
    "timer" to "The timer arrives in phase 5.",
    "insights" to "Insights arrive in phase 6.",
    "duels" to "Duels arrive in phase 6.",
    "friend" to "Friend arrives in phase 6.",
    "book" to "The skills book arrives in phase 6.",
    "add" to "Search, Discover and the art pickers arrive in phase 4.",
)

/** Home's panel (`.dxw`: `--u-pan`, round 28): the folded tab bar's open tile runs into its top. */
@Composable
private fun HomePage(games: Games.State, wide: Boolean, askCode: () -> Unit) {
    val code = BuildConfig.VERSION_CODE
    // "Updated to vN" once, at the first open after an update (no notification until Phase 9, G15)
    val updated = remember {
        val to = Store.prefs.getInt("updatedTo", 0)
        (to == code && Store.prefs.getInt("updatedSeen", 0) != code).also { if (it) Store.prefs.edit().putInt("updatedSeen", code).apply() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .background(Pn.pan, RoundedCornerShape(28.dp))
            .padding(start = if (wide) 22.dp else 14.dp, end = if (wide) 22.dp else 14.dp, top = if (wide) 22.dp else 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (updated) {
            val ctx = LocalContext.current
            val say by produceState(News.said) { value = News.say(ctx.applicationContext, code) }
            Card {
                BasicText("Updated to v$code", style = Type.head)
                say?.takeIf { it.isNotEmpty() }?.let { BasicText(it, style = Type.note) }
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

/** This version's whatsnew.txt line for "Updated to vN": read once, off the main thread, never in composition (speed 7). */
private object News {
    @Volatile var said: String? = null
        private set

    suspend fun say(ctx: Context, code: Int): String = said ?: withContext(Dispatchers.IO) {
        runCatching { ctx.assets.open("whatsnew.txt").bufferedReader().use { WhatsNew.parse(it.readText()) } }.getOrDefault(emptyList())
            .lastOrNull { it.v == code }?.say?.replace(Regex("<[^>]+>"), "").orEmpty()
    }.also { said = it }
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

/** The Queue arrives in phase 1; until then, every game in the backup by state, in your order. */
@Composable
private fun QueueSoon(ready: Games.State.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("The Queue arrives in phase 1. Until then, your games as they came from the backup:", style = Type.note)
        val now = LocalNow.current()
        for (group in ready.lib.byState) {
            Card {
                BasicText("${group.label} · ${group.games.size}", style = Type.kicker)
                for (g in group.games) GameLine(g, if (group.state == "upcoming") JsDate.days(g.date, now).ifEmpty { "No date yet" } else "")
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

