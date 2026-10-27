package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.gen.Web
import java.util.Locale

/**
 * A picture's address as the web app loads it: `http://` becomes https (Chrome does it silently), a repo file
 * (`fefw-key-art.jpg`) comes from the web app's own site, links and uploaded `data:` pictures stay as they are.
 */
fun picUrl(u: String): String = when {
    u.startsWith("https://") || u.startsWith("data:") -> u
    u.startsWith("http://") -> "https://" + u.removePrefix("http://")
    u.startsWith("//") -> "https:$u"
    u.startsWith("/") -> BuildConfig.WEB_ORIGIN + u
    else -> BuildConfig.WEB_ORIGIN + "/Play-next/" + u.removePrefix("./")
}

/**
 * A picture filling its box ([ContentScale.Crop], `object-fit: cover`): a link loads through the app's own client on
 * [ground] (the web's `#1b2445` under a cover); one that can't load shows the game's drawn cover instead of a hole.
 */
@Composable
fun PicBox(pic: Pic, g: Game?, modifier: Modifier = Modifier, alpha: Float = 1f, ground: Color? = null) {
    when (pic) {
        is Pic.Drawn -> DrawnCover(pic.g, modifier, alpha)
        is Pic.Url -> {
            var failed by remember(pic.url) { mutableStateOf(false) }
            Box(modifier.then(if (ground != null) Modifier.background(ground) else Modifier)) {
                if (failed && g != null) {
                    DrawnCover(g, Modifier.fillMaxSize(), alpha)
                } else {
                    AsyncImage(
                        model = picUrl(pic.url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = alpha,
                        onState = { if (it is AsyncImagePainter.State.Error) failed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * The cover a game without box art gets (`placeholder(g)`, a 300x400 picture): the dark ground, two slanted bands in
 * the game's colour, its initials big and italic, and a line along the foot. Drawn natively, cropped like `cover`.
 * The web draws it as an image, so its initials use the phone's sans-serif (an image can't reach the page's fonts).
 */
@Composable
fun DrawnCover(g: Game, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val c = Color(g.colour)
    val ini = remember(g.title) { g.title.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT) }
    val measurer = rememberTextMeasurer()
    Box(
        modifier.drawWithCache {
            val s = maxOf(size.width / 300f, size.height / 400f)
            val ox = (size.width - 300f * s) / 2
            val oy = (size.height - 400f * s) / 2
            val band1 = Path().apply { moveTo(-60f, 400f); lineTo(140f, 0f); lineTo(215f, 0f); lineTo(15f, 400f); close() }
            val band2 = Path().apply { moveTo(60f, 400f); lineTo(260f, 0f); lineTo(300f, 0f); lineTo(100f, 400f); close() }
            // 120 picture units tall, whatever the phone's density and font scale
            val text = measurer.measure(
                ini,
                TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = (120f / (density * fontScale)).sp, color = c),
                softWrap = false,
            )
            onDrawBehind {
                withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
                    drawRect(Color(Web.COVER_BG), size = androidx.compose.ui.geometry.Size(300f, 400f), alpha = alpha)
                    drawPath(band1, c, alpha = .28f * .9f * alpha)
                    drawPath(band2, c, alpha = .16f * .9f * alpha)
                    translate(150f - text.size.width / 2f, 232f - text.firstBaseline) { drawText(text, alpha = alpha) }
                    drawRect(c, topLeft = Offset(0f, 392f), size = androidx.compose.ui.geometry.Size(300f, 8f), alpha = alpha)
                }
            }
        },
    )
}
