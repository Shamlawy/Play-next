package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The one still background (web v276/v277, html + html.paper::before): the base colour and two soft elliptical glows of
 * the tint, darkened 35% toward black, at bgGrad %. Drawn once, never animated; nothing else paints behind the screens.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    base: Color = Pn.bgBase,
    tint: Color = Pn.bgTint,
    grad: Int = Pn.BG_GRAD,
    content: @Composable BoxScope.() -> Unit,
) {
    val full = remember { mutableStateOf(Size.Zero) }
    val paint = remember(base, tint, grad) { Backdrop(base, tint, grad) }
    Box(
        modifier
            .onSizeChanged { full.value = Size(it.width.toFloat(), it.height.toFloat()) }
            .drawWithCache {
                val glows = paint.glows(size)
                onDrawBehind { paint.draw(this, glows, Offset.Zero) }
            },
    ) {
        val here = remember(paint) { BackdropAt(paint, full) }
        CompositionLocalProvider(LocalBackdrop provides here) { content() }
    }
}

/** The background's colours, and how to paint it (or the piece of it behind something drawn over it). */
class Backdrop(private val base: Color, tint: Color, grad: Int) {
    private val t = mix(tint, Color.Black, 0.35f)
    private val a = grad.coerceIn(0, 100) / 100f

    /** The two glows for a background of [full] size. */
    internal fun glows(full: Size): List<Glow> {
        val w = full.width
        val h = full.height
        // radial-gradient(130% 75% at 50% 118%, tint .9 0%, tint 0 62%)
        val low = Glow(Offset(w * 0.5f, h * 1.18f), w * 1.3f, h * 0.75f, t, 0.9f * a, 0.62f)
        // radial-gradient(80% 55% at 104% -6%, tint .35 0%, tint 0 70%)
        val high = Glow(Offset(w * 1.04f, h * -0.06f), w * 0.8f, h * 0.55f, t, 0.35f * a, 0.70f)
        return listOf(low, high)
    }

    /** Paints the background into [scope] as if its top-left sat [at] within the whole background. */
    internal fun draw(scope: DrawScope, glows: List<Glow>, at: Offset) = with(scope) {
        drawRect(base)
        if (a > 0f) translate(-at.x, -at.y) {
            for (g in glows) {
                // Compose gradients are circles: draw one of radius rx and squash it to ry about its centre.
                scale(1f, g.ry / g.rx, pivot = g.c) {
                    drawRect(g.brush, topLeft = Offset(g.c.x - g.rx, g.c.y - g.rx), size = Size(g.rx * 2, g.rx * 2))
                }
            }
        }
    }
}

/** The background as the screens see it: its paint and its size, so a band over it can repaint its own piece. */
class BackdropAt(val paint: Backdrop, val full: State<Size>)

/** The app's background ([AppBackground]); null outside it (the tests' bare screens). */
val LocalBackdrop = staticCompositionLocalOf<BackdropAt?> { null }

/**
 * Paints the background's own piece behind this element (where it sits in the window), fading out over its last
 * [fadeFrom]..1 of its height: a sticky band that hides what scrolls under it without a flat strip over the glow.
 */
@Composable
fun Modifier.backdropBand(fadeFrom: Float): Modifier {
    val bd = LocalBackdrop.current
    val at = remember { mutableStateOf(Offset.Zero) }
    if (bd == null) {
        // outside the app's background (a bare test screen): the base colour, fading the same way
        val ground = LocalLook.current.bgBase
        return drawBehind { drawRect(Brush.verticalGradient(0f to ground, fadeFrom to ground, 1f to ground.copy(alpha = 0f))) }
    }
    return this
        .onPlaced { at.value = it.positionInRoot() }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val glows = bd.paint.glows(bd.full.value)
            val mask = Brush.verticalGradient(0f to Color.Black, fadeFrom to Color.Black, 1f to Color.Transparent)
            onDrawBehind {
                bd.paint.draw(this, glows, at.value)
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }
}

internal class Glow(val c: Offset, val rx: Float, val ry: Float, tint: Color, alpha: Float, end: Float) {
    val brush = Brush.radialGradient(
        0f to tint.copy(alpha = alpha),
        end to tint.copy(alpha = 0f),
        center = c,
        radius = rx,
    )
}
