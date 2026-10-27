package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

/** tan(12°): the slab's lean (`skewX(-12deg)`). */
private const val LEAN = 0.21255656f

/**
 * The Persona slash between tabs (`pnWipe`, v142; index.html 2674-2680): a black slab, 160% of the screen wide and
 * leaning 12°, covers the screen with the new tab's name popping in (Archivo italic 900, turned -7°); after 120ms it
 * sweeps off to the left in 360ms, trailing an accent stripe and a thin ice one. It goes once it has swept off (the
 * web removes it at 560ms, by then off screen), so it keeps pace with the phone's animation speed. Only transform and
 * opacity move: the slab is drawn once and its layer slides.
 */
@Composable
fun SlashWipe(label: String, key: Int, done: () -> Unit) {
    val look = LocalLook.current
    val slide = remember(key) { Animatable(0f) }
    val pop = remember(key) { Animatable(0f) }
    val end by rememberUpdatedState(done)
    LaunchedEffect(key) {
        val name = launch { pop.animateTo(1f, tween(220, easing = Ease.pop)) }
        slide.animateTo(1f, tween(360, delayMillis = 120, easing = Ease.wipe))
        name.join()
        end()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val big = (maxWidth.value * .17f).coerceIn(56f, 112f).sp // clamp(56px, 17vw, 112px)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -2.24f * size.width * slide.value }
                .drawBehind { slab(look.volt) },
        ) {
            BasicText(
                label.uppercase(Locale.ROOT),
                style = TextStyle(
                    fontFamily = Fonts.archivo, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                    fontSize = big, lineHeight = big, letterSpacing = (-0.03).em, color = Pn.white, textAlign = TextAlign.Center,
                ),
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    // pnwT animates opacity and scale on the same overshooting curve: opacity stops at 1
                    val p = pop.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 1.3f - .3f * p
                    scaleY = 1.3f - .3f * p
                    rotationZ = -7f
                },
            )
        }
    }
}

/** The slab (left -30%, right -30%, top/bottom -10%) leaning 12°, its accent stripe (6vw) and the ice line beyond it. */
private fun DrawScope.slab(volt: Color) {
    val w = size.width
    val h = size.height
    val cy = h / 2
    fun band(l: Float, r: Float, c: Color, a: Float = 1f) {
        val t = -.1f * h
        val b = 1.1f * h
        val p = Path().apply {
            moveTo(l - LEAN * (t - cy), t)
            lineTo(r - LEAN * (t - cy), t)
            lineTo(r - LEAN * (b - cy), b)
            lineTo(l - LEAN * (b - cy), b)
            close()
        }
        drawPath(p, c, alpha = a)
    }
    val right = 1.3f * w
    band(-.3f * w, right, Pn.slab)
    band(right, right + .06f * w, volt)
    band(right + .078f * w, right + .09f * w, Pn.ice, .8f)
}

/** How a screen comes in, by [TabFade]: the slash's slide from the left, the fade's rise, or the v110 sideways slide. */
enum class Entrance(val ms: Int) { Slash(380), Cut(280), Right(420), Left(420) }

/**
 * One page entrance: its [kind] (none for the first draw, calm, or Instant inside a tab) and how far it has come. The
 * shell makes one each time the page changes, while composing and above the tabs' saved state: a new tab starts its
 * own state fresh, and the run must outlive that and draw its very first frame at the start.
 */
class Enter(val kind: Entrance?) {
    private val p = Animatable(if (kind == null) 1f else 0f)

    suspend fun play() {
        if (kind != null) p.animateTo(1f, tween(kind.ms, easing = Ease.enter))
    }

    /**
     * `slashin` (-24 → 0 and a fade), `cut` (14 up, .99 → 1 and a fade) or the v110 slide (±26 and a fade). Transform
     * and opacity only, on the entrance curve.
     */
    fun GraphicsLayerScope.draw() {
        val v = p.value
        if (v >= 1f) return
        alpha = v.coerceIn(0f, 1f)
        val left = 1f - v
        when (kind) {
            Entrance.Slash -> translationX = -24.dp.toPx() * left
            Entrance.Cut -> {
                translationY = 14.dp.toPx() * left
                scaleX = .99f + .01f * v
                scaleY = .99f + .01f * v
            }
            Entrance.Right -> translationX = 26.dp.toPx() * left
            Entrance.Left -> translationX = -26.dp.toPx() * left
            null -> Unit
        }
    }
}

/** Draws [run] on this page's layer; the shell plays it. */
fun Modifier.entrance(run: Enter): Modifier = graphicsLayer { with(run) { draw() } }
