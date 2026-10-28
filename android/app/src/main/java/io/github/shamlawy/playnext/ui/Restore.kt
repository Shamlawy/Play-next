package io.github.shamlawy.playnext.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.data.Code
import io.github.shamlawy.playnext.data.Games
import kotlinx.coroutines.launch

/**
 * The first screen, once: your restore code brings your games from the web app's cloud backup. The field takes your
 * password manager's fill (the web app saves the code there) and never suggests or learns what's typed.
 */
@Composable
fun RestoreScreen(onSkip: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    // not saved with the screen: the code never sits in Android's saved state
    var text by remember { mutableStateOf("") }
    var why by rememberSaveable { mutableStateOf<String?>(null) }
    // not saved either: the restore that set it doesn't outlive the screen, so a saved "busy" would stick forever
    var busy by remember { mutableStateOf(false) }
    val ok = Code.normalize(text).isNotEmpty()
    val go = {
        if (!ok) why = Games.NOT_A_CODE
        else if (!busy) {
            busy = true
            why = null
            scope.launch {
                why = Games.restore(ctx, text)
                busy = false
                if (why == null) onDone()
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText("Bring your games", style = Type.title(false))
            BasicText(
                "Type your restore code once. Find it in the web app at Settings › Your data › Cloud backup: it starts with PN-.",
                style = Type.body.copy(color = Pn.mute),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 52.dp)
                    .background(Pn.pan, Pn.rs)
                    .border(1.dp, if (why != null) Pn.hot else Pn.line2, Pn.rs)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) BasicText("PN-XXXX-XXXX-XXXX-XXXX-XXXX", style = Type.body.copy(color = Pn.mute), softWrap = false, maxLines = 1)
                // a copied code carries Android's sensitive mark, so the clipboard's preview doesn't show it
                val clip = LocalClipboard.current
                CompositionLocalProvider(LocalClipboard provides remember(clip) { SensitiveClipboard(clip) }) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(40); why = null },
                        singleLine = true,
                        textStyle = Type.body.copy(letterSpacing = Type.label.letterSpacing),
                        cursorBrush = SolidColor(look.volt),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { go() }),
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Restore code"
                            contentType = ContentType.Password
                        },
                    )
                }
            }
            why?.let { BasicText(it, style = Type.note.copy(color = Pn.hot)) }
            Btn(if (busy) "Bringing your games…" else "Bring my games", primary = ok, modifier = Modifier.fillMaxWidth()) { go() }
            Box(
                Modifier.fillMaxWidth().sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = onSkip),
                contentAlignment = Alignment.Center,
            ) { BasicText("Not now", style = Type.button.copy(color = Pn.mute)) }
            BasicText(
                "The app keeps the code locked on this phone. Until switch day it only reads your backup: nothing you do here changes your games in the web app.",
                style = Type.note,
            )
        }
    }
}

/** The platform clipboard, with every clip it's given marked sensitive (ClipDescription.EXTRA_IS_SENSITIVE). */
internal class SensitiveClipboard(private val base: Clipboard) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = base.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry?.clipData?.description?.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        base.setClipEntry(clipEntry)
    }

    override val nativeClipboard: ClipboardManager get() = base.nativeClipboard
}
