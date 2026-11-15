package io.github.shamlawy.playnext.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.update.WhatsNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What's new (android-v41, the owner, Nov 14: "add an update now to tell me what new thing you made and what to test
 * cuz I always dont know what you did"): the first open after an update brings up a sheet with every release since the
 * last one this phone showed, newest first, each with what changed and what to try (whatsnew.txt's say and tryIt; the
 * old Home card showed only the newest say). Settings › Updates › What's new shows every release again. The web's
 * nxTour walks the screens; here it's one sheet, and the tour waits for Nexi's phase (8a).
 */
internal object News {
    /** The newest release this phone has shown in the sheet (0 before android-v41). */
    private const val SEEN = "newsSeen"

    /** The releases once read (the sheet opens with them at once the next time). */
    @Volatile var all: List<WhatsNew.Entry>? = null
        private set

    /** Every release in whatsnew.txt, newest first: read once, off the main thread (speed 7). */
    suspend fun load(ctx: Context): List<WhatsNew.Entry> = all ?: withContext(Dispatchers.IO) {
        runCatching { ctx.assets.open("whatsnew.txt").bufferedReader().use { WhatsNew.parse(it.readText()) } }.getOrDefault(emptyList())
            .sortedByDescending { it.v }
    }.also { all = it }

    /**
     * The release the sheet starts after, or null when there's nothing to show: only after an update (a first install
     * has nothing new to tell), once per release. Before android-v41 the phone kept the last "Updated to" it showed.
     */
    fun since(code: Int = BuildConfig.VERSION_CODE): Int? {
        val p = Store.prefs
        val seen = p.getInt(SEEN, 0)
        if (seen >= code) return null
        if (seen == 0 && p.getInt("updatedTo", 0) != code) return null
        return when {
            seen > 0 -> seen
            p.getInt("updatedSeen", 0) in 1 until code -> p.getInt("updatedSeen", 0)
            else -> code - 1
        }
    }

    fun seen(code: Int = BuildConfig.VERSION_CODE) = Store.prefs.edit().putInt(SEEN, code).apply()

    /** whatsnew.txt's words with its `<b>` marks as bold, every other mark dropped. */
    fun words(s: String): AnnotatedString = buildAnnotatedString {
        var bold = false
        for (part in Regex("<[^>]+>|[^<]+").findAll(s).map { it.value }) {
            when {
                part.equals("<b>", true) -> bold = true
                part.equals("</b>", true) -> bold = false
                part.startsWith("<") -> Unit
                bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Pn.ice)) { append(part) }
                else -> append(part)
            }
        }
    }
}

/**
 * The sheet: "What's new", then one card per release (vN, what changed, Try it), Got it at its foot. [since] null shows
 * every release; else the ones after it, with Every release to see the rest.
 */
@Composable
internal fun WhatsNewSheet(modifier: Modifier, since: Int?, close: () -> Unit) {
    val ctx = LocalContext.current
    val list by produceState(News.all) { value = News.load(ctx.applicationContext) }
    var every by rememberSaveable { mutableStateOf(since == null) }
    val volt = LocalLook.current.volt
    SheetFrame(modifier, close) {
        BasicText("What's new", style = Type.sheetTitle, modifier = Modifier.padding(bottom = 2.dp).semantics { heading() })
        val shown = list?.let { l -> if (every || since == null) l else l.filter { it.v > since } }
        BasicText(
            when {
                shown == null -> "Opening…"
                every -> "Every release of the app, newest first."
                shown.size == 1 -> "This update, and what to try."
                else -> "${shown.size} updates since you last looked, newest first."
            },
            style = Type.note,
        )
        shown?.forEach { e ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        "v${e.v}",
                        style = Type.label.copy(fontSize = 12.sp, color = Pn.void),
                        softWrap = false, maxLines = 1,
                        modifier = Modifier.background(volt, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    if (e.v == BuildConfig.VERSION_CODE) BasicText("This version", style = Type.label, softWrap = false, maxLines = 1)
                }
                BasicText(News.words(e.say), style = Type.head)
                if (e.tryIt.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Pn.pan, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BasicText("Try it", style = Type.label.copy(color = volt))
                        BasicText(News.words(e.tryIt), style = Type.note.copy(color = Pn.ice))
                    }
                }
            }
        }
        if (!every && since != null && (list?.size ?: 0) > (shown?.size ?: 0)) Btn("Every release") { every = true }
        Btn("Got it", primary = true, onClick = close)
    }
}
