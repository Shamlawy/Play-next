package io.github.shamlawy.playnext.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.shamlawy.playnext.R
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.kit.CallingCard
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Share
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The calling card over the page (`.ccov`, index.html 8320-8333): "Making your calling card…", then the card rises in
 * (quicker on a reshuffle; straight there when calm), Share (Android's share sheet with the PNG), Reshuffle (a random
 * seed) and Close. A card that can't be made says "Couldn't make the card. Try again.".
 */
@Composable
internal fun CardSheet(lib: Library, g: Game, played: Boolean, now: () -> Long, close: () -> Unit) {
    val ctx = LocalContext.current
    val calm = LocalCalm.current
    // 0: the title's own seed, then each Reshuffle a random one
    var shuffles by remember(g.id) { mutableIntStateOf(0) }
    var card by remember(g.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(g.id) { mutableStateOf(false) }
    val rise = remember(g.id) { Animatable(0f) }
    LaunchedEffect(shuffles) {
        failed = false
        val seed = if (shuffles == 0) CallingCard.seedOf(g.title) else (Math.random() * 4294967296.0).toLong()
        val made = runCatching { make(ctx, lib, g, played, now(), seed) }.getOrNull()
        if (made == null) { failed = true; return@LaunchedEffect }
        card = made
        if (calm) rise.snapTo(1f) else { rise.snapTo(0f); rise.animateTo(1f, tween(if (shuffles == 0) 520 else 320, easing = Ease.house)) }
    }
    Box(
        Modifier.fillMaxSize().background(Pn.void.copy(alpha = .92f)).ownTaps().semantics { paneTitle = "Calling card" }
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val b = card
            Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                when {
                    b != null -> Image(
                        b.asImageBitmap(), "Calling card for ${g.title}", contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = 420.dp).aspectRatio(CallingCard.W / CallingCard.H.toFloat()).clip(RoundedCornerShape(16.dp)).graphicsLayer {
                            val v = rise.value
                            alpha = v
                            translationY = (1f - v) * 40.dp.toPx()
                            scaleX = .94f + .06f * v
                            scaleY = .94f + .06f * v
                        },
                    )
                    failed -> BasicText("Couldn't make the card. Try again.", style = DyType.q)
                    else -> BasicText("Making your calling card…", style = DyType.q)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HBtn("Share", main = true, enabled = b != null) {
                    val bm = card ?: return@HBtn
                    Crumbs.tap("calling card: share", "calling card")
                    Sounds.play(Sfx.Tap)
                    Share.image(ctx, bm, "calling-card-${now()}.png", "Calling card")
                }
                HBtn("Reshuffle", enabled = b != null || failed) {
                    Crumbs.tap("calling card: reshuffle", "calling card")
                    Sounds.play(Sfx.Tap)
                    shuffles++
                }
                HBtn("Close", onClick = close)
            }
        }
    }
}

/** Loads the cover through the app's picture cache and draws the card, off Main. */
private suspend fun make(ctx: Context, lib: Library, g: Game, played: Boolean, now: Long, seed: Long): Bitmap {
    val url = Backup.str(g["img"])
    val cover = if (url.isEmpty()) null else runCatching {
        val r = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(picUrl(url)).size(600, 800).allowHardware(false).build())
        (r as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()
    return withContext(Dispatchers.Default) {
        val note = CallingCard.note(played, g.date, Backup.num(g["hype"], 0.0, 0.0, 10.0), now)
        val signer = Backup.str(lib["userName"]).ifEmpty { "The Play Next crew" }
        CallingCard.draw(g.title, g.colour.toInt(), cover, note, signer, seed, faces(ctx))
    }
}

@Volatile private var kept: CallingCard.Faces? = null

/** The card's five faces: Archivo at 900 (upright and italic), Playfair bold, Android's bold mono and bold italic serif. */
private fun faces(ctx: Context): CallingCard.Faces = kept ?: run {
    fun variable(id: Int, name: String, wght: Int): Typeface = runCatching {
        // a variable font from res/font: copied once, then built at its weight
        val f = File(ctx.cacheDir, "fonts/$name.ttf")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            ctx.resources.openRawResource(id).use { i -> f.outputStream().use { i.copyTo(it) } }
        }
        Typeface.Builder(f).setFontVariationSettings("'wght' $wght").build()
    }.getOrDefault(Typeface.DEFAULT_BOLD)
    CallingCard.Faces(
        display = variable(R.font.archivo, "archivo", 900),
        displayItalic = variable(R.font.archivo_italic, "archivo_italic", 900),
        serif = variable(R.font.playfair, "playfair", 700),
        mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
        serifItalic = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC),
    ).also { kept = it }
}
