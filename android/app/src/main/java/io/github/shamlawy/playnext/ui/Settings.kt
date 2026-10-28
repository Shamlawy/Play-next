package io.github.shamlawy.playnext.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.shamlawy.playnext.data.Times
import io.github.shamlawy.playnext.kit.Big
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.UpdatePlan
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Settings, until its own phase (7) brings the real one: your backup, updates, the one-time install switch, the 120 Hz
 * test and Report a problem (the web app keeps these under Your data and This build). A sheet like the web's
 * `.sheet-in` (index.html 1561, 16531): over a `--void` .86 scrim, at most 620 wide and centred, 4% of the screen down
 * from the top, `--u-pan` and round 28 at the top (all four corners from 660 wide), the grab handle, a 22/700 title
 * and Close at its foot. It scrolls as one, scrim and all. Back, Close, a tap on the scrim or a swipe down from its top
 * closes it; the sheet follows the finger down (its layer only, nothing shrinks: #309) and springs back when let go
 * early.
 */
@Composable
fun SettingsSheet(modifier: Modifier, games: Games.State, wide: Boolean, close: () -> Unit, openProbe: () -> Unit, askCode: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(Pn.void.copy(alpha = .86f))) {
        val round = maxWidth >= 660.dp
        val shape = if (round) RoundedCornerShape(28.dp) else RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)
        val gap = maxHeight * .04f
        val pull = rememberPull(close, constraints.maxHeight.toFloat())
        Column(
            Modifier
                .fillMaxSize()
                .scrimTap(close)
                .nestedScroll(pull.connection)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(top = gap, bottom = gap)
                .graphicsLayer { translationY = pull.offset },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .background(Pn.pan, shape)
                    .border(1.dp, Pn.line2, shape)
                    // taps on the sheet are the sheet's: only the scrim round it closes
                    .ownTaps(),
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding()
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText("Settings", style = Type.sheetTitle, modifier = Modifier.padding(bottom = 2.dp))
                    BackupCard(games, askCode)
                    SoundsCard()
                    BuildCards(openProbe)
                    Btn("Close", onClick = close)
                }
                // the grab handle (`.sheet-in::before`): 40x4, 8 from the top
                Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(40.dp, 4.dp).background(Pn.line2, RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** How far the sheet is pulled down, and the scroll hook that pulls it: past [PULL_SHUT] of the screen, or flung, it closes. */
private class Pull(val connection: NestedScrollConnection) {
    var offset by mutableFloatStateOf(0f)
}

@Composable
private fun rememberPull(close: () -> Unit, height: Float): Pull {
    val shut by rememberUpdatedState(close)
    return remember(height) {
        lateinit var pull: Pull
        pull = Pull(
            object : NestedScrollConnection {
                // back up while pulled down: the pull gives way before the sheet scrolls
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f || pull.offset <= 0f) return Offset.Zero
                    val used = maxOf(available.y, -pull.offset)
                    pull.offset += used
                    return Offset(0f, used)
                }

                // down from the top (nothing left to scroll): the sheet follows the finger
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    pull.offset += available.y
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (pull.offset <= 0f) return Velocity.Zero
                    if (pull.offset > height * PULL_SHUT || available.y > 1800f) shut()
                    else animate(pull.offset, 0f, animationSpec = tween(220, easing = Ease.house)) { v, _ -> pull.offset = v }
                    return available
                }
            },
        )
        pull
    }
}

/** A quarter of the screen down closes the sheet (the web's sheets close on a swipe past about a quarter). */
private const val PULL_SHUT = .25f

/**
 * A tap on the scrim closes; a drag that starts there doesn't (a plain clickable took a moved press as a tap), nor
 * does a tap the sheet itself took.
 */
private fun Modifier.scrimTap(close: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (!c.pressed) {
                if (!moved && !c.isConsumed) close()
                break
            }
        }
    }
}

/**
 * A tap on a sheet or page itself (between its buttons) is taken here, so nothing under it (Settings' scrim, a page's
 * tab) takes it. Not a clickable: that merges every word inside into one node, read by TalkBack as one long button.
 */
internal fun Modifier.ownTaps() = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        waitForUpOrCancellation()?.consume()
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
                val (busy, again) = lookAgain(games.checking)
                games.note?.let { BasicText(it, style = Type.note.copy(color = Pn.hot)) }
                lookedLine(ok = games.note == null)?.let { BasicText(it, style = Type.note) }
                Btn(if (busy) "Looking for a newer copy…" else "Check for a newer copy", onClick = again)
                Btn("Use a different restore code") { Games.forget(ctx); askCode() }
            }
            is Games.State.Fetching -> {
                val (busy, again) = lookAgain(games.checking)
                BasicText(if (busy || games.why == null) "Fetching your games…" else games.why, style = Type.note)
                if (games.why != null) lookedLine(ok = false)?.let { BasicText(it, style = Type.note) }
                Btn(if (busy) "Trying again…" else "Try again", primary = true, onClick = again)
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

/**
 * "Check for a newer copy" and "Try again" (L184): busy from the tap until the look is done, and for at least [HOLD],
 * so a quick answer, or the same error coming back, still reads as a tap that did something. The second is the tap.
 */
@Composable
internal fun lookAgain(checking: Boolean): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(tapped) {
        if (tapped) {
            delay(HOLD)
            tapped = false
        }
    }
    val busy = tapped || checking
    return busy to {
        if (!busy) {
            tapped = true
            Games.checkNow(ctx)
        }
    }
}

/** When it last looked for a copy (Store "copyLooked"), null before the first look: [ok] when nothing went wrong. */
@Composable
internal fun lookedLine(ok: Boolean): String? {
    val at = Store.prefs.getLong("copyLooked", 0L).takeIf { it > 0 } ?: return null
    val now = LocalNow.current()
    return if (ok) "Checked ${atTime(at, now, ZoneId.systemDefault())}: this is the newest copy." else "Tried ${atTime(at, now, ZoneId.systemDefault())}."
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
    // what you wrote survives a fold or Android recreating the app (sending doesn't: its coroutine ends with the screen)
    var text by rememberSaveable { mutableStateOf("") }
    var said by rememberSaveable { mutableStateOf<String?>(null) }
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

/**
 * Sounds and buzzes (Settings › Sound and reactions on the web): every sound the app makes and the big moments'
 * buzzes, each a tap away to try. They play here whatever your Sounds switch says; everywhere else Sounds decides
 * (the owner, Oct 26: off means silent, the duel, CLEAR! and Out now too). The switch itself moves over with Settings
 * (phase 7); until then it's the web app's.
 */
@Composable
private fun SoundsCard() {
    val prefs = LocalPrefs.current
    val ready by Sounds.ready.collectAsStateWithLifecycle()
    // a tap before the sounds are made (a second, the first time) says so instead of doing nothing
    var early by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Sounds.warm() }
    Card {
        BasicText("Sounds and buzzes", style = Type.head)
        BasicText(
            if (prefs.sound) "Sounds is on in your backup, so the app makes these as you use it. Tap one to hear it."
            else "Sounds is off in your backup, so the app stays quiet. Tap one to hear it anyway: it plays here only.",
            style = Type.note,
        )
        if (early && !ready) BasicText("Getting the sounds ready: try again in a moment.", style = Type.note)
        for ((group, list) in SOUND_GROUPS) {
            BasicText(group, style = Type.kicker, modifier = Modifier.padding(top = 4.dp))
            TryRow(list.map { s -> s.label to { if (!Sounds.now(s)) early = true } })
        }
        BasicText("Buzzes for the big moments", style = Type.kicker, modifier = Modifier.padding(top = 4.dp))
        TryRow(Big.entries.map { b -> b.label to { Buzz.big(b) } } + ("Swipe tick" to { Buzz.ms(9) }))
    }
}

/** The sounds by where the web app plays them. */
private val SOUND_GROUPS = listOf(
    "Taps" to listOf(Sfx.Tap, Sfx.Open, Sfx.Back, Sfx.Add, Sfx.Swipe, Sfx.Nudge),
    "Tonight's pick" to listOf(Sfx.Hit, Sfx.Slash, Sfx.Tick, Sfx.Slam, Sfx.Crack, Sfx.Shatter, Sfx.Cheer),
    "The Queue card" to listOf(Sfx.CardOpen, Sfx.CardTear, Sfx.CardFlip, Sfx.CardFlipBack, Sfx.CardShut, Sfx.CardBack),
    "Big moments" to listOf(Sfx.Zing, Sfx.Boom, Sfx.Clang, Sfx.Fanfare, Sfx.Day, Sfx.Chime),
)

@Composable
private fun TryRow(items: List<Pair<String, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, play) in items) {
            Box(
                Modifier
                    .sizeIn(minHeight = 40.dp)
                    .background(Pn.card2, Pn.pill)
                    .clickable(role = Role.Button, onClick = play)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(label, style = Type.label.copy(color = Pn.ice), softWrap = false, maxLines = 1) }
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

/** "at 2:54 PM" today, "Oct 26 at 2:54 PM" on another day. */
private fun atTime(at: Long, now: Long, zone: ZoneId): String = Times.at(at, now, zone, join = " at ", lead = "at ")
