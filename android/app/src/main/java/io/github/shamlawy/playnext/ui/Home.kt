package io.github.shamlawy.playnext.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Updater
import io.github.shamlawy.playnext.update.WhatsNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val title = TextStyle(color = Pn.ice, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em, lineBreak = LineBreak.Heading)
private val head = TextStyle(color = Pn.ice, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
private val body = TextStyle(color = Pn.ice, fontSize = 14.5.sp, lineHeight = 21.sp)
private val muted = TextStyle(color = Pn.mute, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)

/**
 * Piece 1's home: the app is on the phone and keeps itself up to date. Updates (and the one-time "Install unknown
 * apps" switch), the 120 Hz test and Report a problem. The real tabs replace it from Piece 2 on.
 */
@Composable
fun Home(onProbe: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val upd by Updater.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(Updater.canInstall(ctx)) }
    LifecycleResumeEffect(Unit) {
        canInstall = Updater.canInstall(ctx)
        onPauseOrDispose {}
    }
    val updated by produceState<String?>(null) { value = withContext(Dispatchers.IO) { updatedLine(ctx) } }
    val probeLast = remember { Store.prefs.getString("probeLast", null) }
    var problem by remember { mutableStateOf("") }
    var problemSaid by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Screen.set(ctx, "home") }
    val conf = LocalConfiguration.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().imePadding()
                .widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("Play next", style = title)
            BasicText("The Android app. Your games arrive in the next update; for now it shows it can update itself and run smoothly.", style = muted)
            updated?.let { BasicText(it, style = body.copy(color = Pn.volt, fontWeight = FontWeight.SemiBold)) }

            Card("Updates") {
                BasicText(updateLine(upd), style = body.copy(color = if (upd is Updater.State.Blocked) Pn.hot else Pn.ice))
                if (!canInstall) {
                    BasicText("Allow Play next to install its own updates. You only do this once.", style = muted)
                    PnButton("Allow", Modifier.fillMaxWidth(), main = true) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${ctx.packageName}".toUri()))
                    }
                }
                val tap = upd as? Updater.State.NeedsTap
                if (tap != null) {
                    PnButton("Install v${tap.code}", Modifier.fillMaxWidth(), main = canInstall) { scope.launch { Updater.run(ctx, now = true) } }
                } else {
                    PnButton("Check now", Modifier.fillMaxWidth()) { scope.launch { Updater.checkInBackground(ctx, force = true) } }
                }
            }

            Card("120 Hz test") {
                BasicText(probeLast ?: "Checks the app draws 120 frames a second on this phone. About 16 seconds; don't touch it while it runs.", style = body)
                PnButton("Run the test", Modifier.fillMaxWidth(), onClick = onProbe)
            }

            Card("Report a problem") {
                BasicTextField(
                    problem,
                    onValueChange = { problem = it.take(800); problemSaid = "" },
                    textStyle = body,
                    cursorBrush = SolidColor(Pn.volt),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(Pn.rs)).background(Pn.pan)
                        .border(1.dp, Pn.line2, RoundedCornerShape(Pn.rs)).padding(12.dp),
                    decorationBox = { field ->
                        Box {
                            if (problem.isEmpty()) BasicText("What went wrong? Claude reads it.", style = body.copy(color = Pn.mute))
                            field()
                        }
                    },
                )
                if (problemSaid.isNotEmpty()) BasicText(problemSaid, style = muted)
                PnButton("Send", Modifier.fillMaxWidth(), main = problem.isNotBlank()) {
                    val text = problem
                    if (text.isBlank()) return@PnButton
                    scope.launch {
                        val sent = Reporter.user(text)
                        problem = ""
                        problemSaid = if (sent) "Sent. Thanks!" else "Saved. It sends when you're back online."
                    }
                }
            }

            BasicText(
                "Version ${BuildConfig.VERSION_CODE} · ${if (conf.smallestScreenWidthDp >= 600) "unfolded" else "folded"} · ${conf.screenWidthDp} dp wide",
                style = muted,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Card(name: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Pn.r)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Pn.card, shape).border(1.dp, Pn.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(name, style = head)
        content()
    }
}

private fun updateLine(s: Updater.State): String = when (s) {
    Updater.State.Idle -> "Checks by itself when you open the app and every 6 hours."
    Updater.State.Checking -> "Looking for an update…"
    Updater.State.UpToDate -> "You have the newest version (v${BuildConfig.VERSION_CODE})."
    is Updater.State.Downloading -> "Downloading v${s.code} · ${s.pct}%"
    is Updater.State.Waiting -> "v${s.code} is ready. It installs by itself when you leave the app."
    is Updater.State.NeedsTap -> "v${s.code} is ready. Android wants one tap to install it."
    is Updater.State.Blocked -> s.why
    is Updater.State.Failed -> "Couldn't update: ${s.why}"
}

/** "Updated to vN: <its what's new line>", once, on the first open after an update (G15: no notification until Phase 9). */
private fun updatedLine(ctx: Context): String? {
    val to = Store.prefs.getInt("updatedTo", 0)
    if (to != BuildConfig.VERSION_CODE || Store.prefs.getInt("updatedSeen", 0) == to) return null
    Store.prefs.edit().putInt("updatedSeen", to).apply()
    val say = runCatching { WhatsNew.parse(ctx.assets.open("whatsnew.txt").bufferedReader().use { it.readText() }) }
        .getOrNull()?.firstOrNull { it.v == to }?.say
    return "Updated to v$to" + (say?.let { ": $it" } ?: "")
}
