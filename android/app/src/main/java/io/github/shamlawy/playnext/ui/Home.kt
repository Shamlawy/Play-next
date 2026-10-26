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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.profileinstaller.ProfileVerifier
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Updater
import io.github.shamlawy.playnext.update.WhatsNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Piece 1's only screen until the tabs arrive: whether the app is current, the one-time "Install unknown apps" switch,
 * the 120 Hz test and Report a problem.
 */
@Composable
fun HomeScreen(openProbe: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by Updater.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(Updater.canInstall(ctx)) }
    LifecycleResumeEffect(Unit) {
        canInstall = Updater.canInstall(ctx)
        onPauseOrDispose { }
    }
    val code = BuildConfig.VERSION_CODE
    // "Updated to vN" once, at the first open after an update (no notification until Phase 9, G15)
    val updated = remember {
        val to = Store.prefs.getInt("updatedTo", 0)
        (to == code && Store.prefs.getInt("updatedSeen", 0) != code).also { if (it) Store.prefs.edit().putInt("updatedSeen", code).apply() }
    }
    val news = remember { runCatching { ctx.assets.open("whatsnew.txt").bufferedReader().use { WhatsNew.parse(it.readText()) } }.getOrDefault(emptyList()) }
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("Play next", style = TextStyle(color = Pn.ice, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading))
            BasicText(
                "The Android app, v$code. Your games arrive in the next piece; for now it checks that it updates itself and runs smoothly.",
                style = TextStyle(color = Pn.mute, fontSize = 15.sp, lineHeight = 21.sp),
            )

            if (updated) {
                Card {
                    BasicText("Updated to v$code", style = title)
                    news.lastOrNull { it.v == code }?.let { BasicText(it.say.replace(Regex("<[^>]+>"), ""), style = body) }
                }
            }

            Card {
                BasicText("Updates", style = title)
                BasicText(updateLine(update, code), style = body)
                when (val s = update) {
                    is Updater.State.NeedsTap -> Button("Install v${s.code}", primary = true) { scope.launch { Updater.run(ctx, now = true) } }
                    is Updater.State.Failed, is Updater.State.Blocked, Updater.State.UpToDate, Updater.State.Idle ->
                        Button("Check now") { scope.launch { Updater.checkInBackground(ctx, force = true) } }
                    else -> Unit
                }
            }

            if (!canInstall) {
                Card {
                    BasicText("One switch for updates", style = title)
                    BasicText("Allow Play next to install apps once, so its updates can install themselves.", style = body)
                    Button("Open the switch", primary = true) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }

            Card {
                BasicText("120 Hz test", style = title)
                val last = remember { Store.prefs.getString("probeLast", null) }
                BasicText(
                    last ?: "About 20 seconds of flinging pictures. Run it folded and again unfolded; the result goes to Claude by itself.",
                    style = body,
                )
                Button("Run the test", primary = last == null, onClick = openProbe)
            }

            ReportCard()

            BasicText(
                listOf("v$code", compiled, "Android ${Build.VERSION.RELEASE}").filter { it.isNotBlank() }.joinToString(" · "),
                style = TextStyle(color = Pn.mute, fontSize = 12.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ReportCard() {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var said by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    Card {
        BasicText("Report a problem", style = title)
        BasicText("Say what happened. It goes to Claude with the version and the screen, never your games.", style = body)
        Box(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 96.dp)
                .background(Pn.pan, Pn.rs)
                .border(1.dp, Pn.line2, Pn.rs)
                .padding(12.dp),
        ) {
            if (text.isEmpty()) BasicText("What went wrong?", style = TextStyle(color = Pn.mute, fontSize = 15.sp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(800); said = null },
                textStyle = TextStyle(color = Pn.ice, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(Pn.volt),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What went wrong?" },
            )
        }
        said?.let { BasicText(it, style = body) }
        Button(if (sending) "Sending…" else "Send", primary = text.isNotBlank()) {
            if (text.isBlank() || sending) return@Button
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

private fun updateLine(s: Updater.State, code: Int): String = when (s) {
    Updater.State.Idle -> "You have v$code. It checks for a new version by itself."
    Updater.State.Checking -> "Looking for a new version…"
    Updater.State.UpToDate -> "You have the newest version, v$code."
    is Updater.State.Downloading -> "Downloading v${s.code} · ${s.pct}%"
    is Updater.State.Waiting -> "v${s.code} is ready. It installs by itself when you leave the app."
    is Updater.State.NeedsTap -> "v${s.code} is ready and Android wants one tap to install it."
    is Updater.State.Blocked -> s.why
    is Updater.State.Failed -> "Couldn't update: ${s.why}"
}

private val title = TextStyle(color = Pn.ice, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading)
private val body = TextStyle(color = Pn.mute, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Pn.card, Pn.r).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A flat button: nothing moves or glows when pressed (#309), at least 48dp tall. */
@Composable
private fun Button(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .sizeIn(minHeight = Pn.touch)
            .background(if (primary) Pn.volt else Pn.card2, Pn.rs)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(color = if (primary) Pn.void else Pn.ice, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            softWrap = false, maxLines = 1,
        )
    }
}

