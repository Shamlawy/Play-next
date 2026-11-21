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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.JsDate
import io.github.shamlawy.playnext.data.Times
import io.github.shamlawy.playnext.kit.Shared
import java.time.ZoneId

/**
 * What each tab shows until its own phase builds it: Home your games at a glance; every view of Games, Plan and Taste,
 * and Add, its header card and what's coming (the Queue, until phase 1, your games as they came from the backup).
 * Every tab and view already has its place, its icon and its scroll spot.
 */
@Composable
fun TabPage(tab: String, view: String?, games: Games.State, wide: Boolean, screen: Dp, askCode: () -> Unit) {
    if (tab == "home") {
        HomePage(games, wide, screen, askCode)
        return
    }
    val v = view ?: tab
    // the header card's screen (`SHX_PANELS`): each Games view its own, Plan and Taste one each; the book has none
    val ready = games as? Games.State.Ready
    TabTop(tab, view, games, wide, screen)
    if (ready == null) {
        Card { if (games is Games.State.NoCode) NoGames(askCode) else Waiting(games as? Games.State.Fetching, askCode) }
        return
    }
    if (tab == "add") LocalShared.current?.let { SharedCard(it) }
    Card { BasicText(SOON[v] ?: "", style = Type.note) }
}

/** A tab's top: its header card (or plain title). Its views are in the rail unfolded, in the sticky bar above it folded. */
@Composable
fun TabTop(tab: String, view: String?, games: Games.State, wide: Boolean, screen: Dp) {
    val prefs = LocalPrefs.current
    val views = prefs.views(tab)
    val v = view ?: tab
    val title = if (tab == "add") "Add" else views.firstOrNull { it.first == v }?.second ?: v
    val kind = when (tab) {
        "games" -> v
        "taste" -> if (v == "book") null else "taste"
        else -> tab
    }
    val ready = games as? Games.State.Ready
    if (ready != null && kind != null) HeaderCard(kind, title, ready.lib, screen, ready.cards) else PlainTitle(title, wide)
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

/** The line for a part of the app a later update builds ([SOON]'s words, one place). */
fun soon(key: String): String = SOON[key] ?: "That arrives in a later update."

/** Where each view is built (docs/ANDROID-PLAN.md, the phases), and Home's taps into screens still to come. */
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
    "wrapped" to "Year wrapped and the monthly recaps arrive in phase 5.",
    "taste" to "Taste arrives in phase 6.",
    "chat" to "Chatting with Nexi arrives in phase 8.",
)

/**
 * Home (Phase 3): your games at the top ([HomeTop]); until they've come, the panel (`.dxw`: `--u-pan`, round 28) says
 * why. The folded tab bar's open tile runs into its top.
 */
@Composable
private fun HomePage(games: Games.State, wide: Boolean, screen: Dp, askCode: () -> Unit) {
    if (games is Games.State.Ready) {
        HomeTop(games, wide, screen)
        Card(Modifier.padding(bottom = 22.dp)) {
            AsOf(games)
            BasicText("More for you (the swipe stacks under these tiles) comes in the next update. This app is a practice copy: it reads your games and changes nothing.", style = Type.note)
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .background(Pn.pan, RoundedCornerShape(28.dp))
            .padding(start = if (wide) 22.dp else 14.dp, end = if (wide) 22.dp else 14.dp, top = if (wide) 22.dp else 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (games) {
            is Games.State.Ready -> Unit
            is Games.State.Fetching -> Waiting(games, askCode)
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
fun asOfLine(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String = "Your games as of " + Times.at(at, now, zone)

/** A copy that hasn't come yet ([f] null while starting): why, when it last tried, and Try again. */
@Composable
private fun Waiting(f: Games.State.Fetching?, askCode: () -> Unit) {
    val ctx = LocalContext.current
    val why = f?.why
    val (busy, again) = lookAgain(f?.checking == true)
    BasicText("Your games", style = Type.title(false))
    BasicText(if (busy || why == null) "Fetching your games from the cloud backup…" else why, style = Type.note)
    if (why != null) {
        lookedLine(ok = false)?.let { BasicText(it, style = Type.note) }
        Btn(if (busy) "Trying again…" else "Try again", primary = true, onClick = again)
        Btn("Use a different restore code") { Games.forget(ctx); askCode() }
    }
}

@Composable
private fun NoGames(askCode: () -> Unit) {
    BasicText("Your games aren't here yet", style = Type.title(false))
    BasicText("Type your restore code once and they come from the web app's cloud backup.", style = Type.note)
    Btn("Type my restore code", primary = true, onClick = askCode)
}
