package io.github.shamlawy.playnext.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.profileinstaller.ProfileVerifier
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.UpdatePlan
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings, until its own phase (7) brings the real one: your backup, updates, the one-time install switch, the 120 Hz
 * test and Report a problem (the web app keeps these under Your data and This build). A sheet over the tabs: `--u-pan`,
 * round 28 at the top (all four corners when wide), the grab handle; back or Done closes it.
 */
@Composable
fun SettingsSheet(games: Games.State, wide: Boolean, close: () -> Unit, openProbe: () -> Unit, askCode: () -> Unit) {
    val shape = if (wide) RoundedCornerShape(28.dp) else RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)
    Box(Modifier.fillMaxSize().background(Pn.void.copy(alpha = 0.6f)).clickable(onClick = close, indication = null, interactionSource = null)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp, bottom = if (wide) 12.dp else 0.dp)
                // unfolded: the rail's 96 stays clear, as the page leaves it (the sheet ran under the rail's edge)
                .padding(end = if (wide) 96.dp else 0.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(shape) // the cards scroll under the rounded corners, never past them
                .background(Pn.pan, shape)
                .border(1.dp, Pn.line2, shape)
                .clickable(onClick = {}, indication = null, interactionSource = null),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(Pn.line2, RoundedCornerShape(4.dp)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Settings", style = Type.title(wide), modifier = Modifier.weight(1f))
                Box(
                    Modifier.sizeIn(minWidth = Pn.touch, minHeight = Pn.touch).clickable(role = Role.Button, onClick = close).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText("Done", style = Type.button.copy(color = LocalLook.current.volt)) }
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BackupCard(games, askCode)
                BuildCards(openProbe)
            }
        }
    }
}

/** Your games: where they come from, how fresh, and the code (only its last group shows). */
@Composable
private fun BackupCard(games: Games.State, askCode: () -> Unit) {
    val ctx = LocalContext.current
    Card {
        BasicText("Your games", style = Type.head)
        when (games) {
            is Games.State.Ready -> {
                BasicText(asOfLine(games.copyAt, LocalNow.current()), style = Type.note)
                BasicText(
                    "This app is a practice copy until switch day: it reads your games from the web app's cloud backup and changes nothing there.",
                    style = Type.note,
                )
                games.note?.let { BasicText(it, style = Type.note.copy(color = Pn.hot)) }
                Btn(if (games.checking) "Looking for a newer copy…" else "Check for a newer copy") { if (!games.checking) Games.checkNow(ctx) }
                Btn("Use a different restore code") { Games.forget(ctx); askCode() }
            }
            is Games.State.Fetching -> {
                BasicText(games.why ?: "Fetching your games…", style = Type.note)
                Btn("Try again", primary = true) { Games.checkNow(ctx) }
                Btn("Use a different restore code") { Games.forget(ctx); askCode() }
            }
            Games.State.Starting -> BasicText("Opening your games…", style = Type.note)
            Games.State.NoCode -> {
                BasicText("Type your restore code once and your games come from the web app's cloud backup.", style = Type.note)
                Btn("Type my restore code", primary = true, onClick = askCode)
            }
        }
    }
}

/** Piece 1's cards: updates, the one-time install switch, the 120 Hz test, Report a problem and the build line. */
@Composable
private fun BuildCards(openProbe: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by Updater.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(Updater.canInstall(ctx)) }
    LifecycleResumeEffect(Unit) {
        canInstall = Updater.canInstall(ctx)
        onPauseOrDispose { }
    }
    val code = BuildConfig.VERSION_CODE
    val compiled by produceState("") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val s = ProfileVerifier.getCompilationStatusAsync().get()
                when {
                    s.isCompiledWithProfile -> "compiled ahead"
                    s.hasProfileEnqueuedForCompilation() -> "compiles tonight"
                    else -> "not compiled yet"
                }
            }.getOrDefault("")
        }
    }

    // Check now always shows that it did something (the owner on v5: "when I press check for update it doesn't do it"):
    // "Looking…" for at least HOLD even when the answer comes back at once, then when it looked; "Already checking…"
    // when another run holds the lock
    var looking by remember { mutableStateOf(false) }
    var already by remember { mutableStateOf(false) }
    val now = LocalNow.current
    Card {
        BasicText("Updates", style = Type.head)
        val line = when {
            already -> "Already checking…"
            looking -> updateLine(Updater.State.Checking, code)
            else -> updateLine(update, code, Store.prefs.getLong("updChecked", 0L), now())
        }
        BasicText(line, style = Type.note)
        when (val s = update) {
            is Updater.State.NeedsTap -> Btn("Install v${s.code}", primary = true) { scope.launch { Updater.run(ctx, UpdatePlan.Ask.Install) } }
            is Updater.State.Failed, is Updater.State.Blocked, Updater.State.UpToDate, Updater.State.Idle -> if (!looking) Btn("Check now") {
                if (Updater.running) {
                    already = true
                    scope.launch { delay(HOLD); already = false }
                } else {
                    looking = true
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        try {
                            Updater.run(ctx, UpdatePlan.Ask.Check)
                        } finally {
                            delay((HOLD - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
                            looking = false
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    if (!canInstall) {
        Card {
            BasicText("One switch for updates", style = Type.head)
            BasicText("Allow Play next to install apps once, so its updates can install themselves.", style = Type.note)
            Btn("Open the switch", primary = true) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    Card {
        BasicText("120 Hz test", style = Type.head)
        val last = remember { Store.prefs.getString("probeLast", null) }
        BasicText(
            last ?: "About 20 seconds of flinging pictures. Run it folded and again unfolded; the result goes to Claude by itself.",
            style = Type.note,
        )
        Btn("Run the test", primary = last == null, onClick = openProbe)
    }

    ReportCard()

    BasicText(
        listOf("v$code", compiled, "Android ${Build.VERSION.RELEASE}").filter { it.isNotBlank() }.joinToString(" · "),
        style = Type.note.copy(fontSize = Type.kicker.fontSize),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ReportCard() {
    val scope = rememberCoroutineScope()
    val look = LocalLook.current
    var text by remember { mutableStateOf("") }
    var said by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    Card {
        BasicText("Report a problem", style = Type.head)
        BasicText("Say what happened. It goes to Claude with the version and the screen, never your games.", style = Type.note)
        Box(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 96.dp)
                .background(Pn.pan, Pn.rs)
                .border(1.dp, Pn.line2, Pn.rs)
                .padding(12.dp),
        ) {
            if (text.isEmpty()) BasicText("What went wrong?", style = Type.body.copy(color = Pn.mute))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(800); said = null },
                textStyle = Type.body,
                cursorBrush = SolidColor(look.volt),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What went wrong?" },
            )
        }
        said?.let { BasicText(it, style = Type.note) }
        Btn(if (sending) "Sending…" else "Send", primary = text.isNotBlank()) {
            if (text.isBlank() || sending) return@Btn
            sending = true
            scope.launch {
                val sent = Reporter.user(text)
                sending = false
                said = if (sent) "Sent. Thanks!" else "Saved. It goes as soon as you're online."
                if (sent) text = ""
            }
        }
    }
}

/** How long Check now's "Looking…" stays up at least, so a quick answer still reads as a tap that did something. */
private const val HOLD = 700L

/** The Updates card's line; [checkedAt] = the last look (Store "updChecked"), 0 when it never looked. */
internal fun updateLine(s: Updater.State, code: Int, checkedAt: Long = 0L, now: Long = 0L, zone: ZoneId = ZoneId.systemDefault()): String = when (s) {
    Updater.State.Idle -> if (checkedAt > 0) "You have v$code. Last looked ${atTime(checkedAt, now, zone)}; it checks by itself." else "You have v$code. It checks for a new version by itself."
    Updater.State.Checking -> "Looking for a new version…"
    Updater.State.UpToDate -> if (checkedAt > 0) "Checked ${atTime(checkedAt, now, zone)}: v$code is the newest." else "You have the newest version, v$code."
    is Updater.State.Downloading -> "Downloading v${s.code} · ${s.pct}%"
    is Updater.State.Waiting -> "v${s.code} is ready. It installs by itself when you leave the app."
    is Updater.State.NeedsTap -> "v${s.code} is ready. Tap Install, then Install on Android's screen."
    is Updater.State.Installing -> "Installing v${s.code}…"
    is Updater.State.Blocked -> s.why
    is Updater.State.Failed -> "Couldn't update: ${s.why}"
}

/** "at 2:54 PM" today, "Oct 26 at 2:54 PM" on another day (the phone's zone, 12-hour, as the web app). */
private fun atTime(at: Long, now: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val time = "at " + t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    return if (t.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) time else t.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + " " + time
}
