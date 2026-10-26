package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale

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
    val t = mix(tint, Color.Black, 0.35f)
    val a = grad.coerceIn(0, 100) / 100f
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            // radial-gradient(130% 75% at 50% 118%, tint .9 0%, tint 0 62%)
            val low = Glow(Offset(w * 0.5f, h * 1.18f), w * 1.3f, h * 0.75f, t, 0.9f * a, 0.62f)
            // radial-gradient(80% 55% at 104% -6%, tint .35 0%, tint 0 70%)
            val high = Glow(Offset(w * 1.04f, h * -0.06f), w * 0.8f, h * 0.55f, t, 0.35f * a, 0.70f)
            onDrawBehind {
                drawRect(base)
                if (a > 0f) for (g in listOf(low, high)) {
                    // Compose gradients are circles: draw one of radius rx and squash it to ry about its centre.
                    scale(1f, g.ry / g.rx, pivot = g.c) {
                        drawRect(g.brush, topLeft = Offset(g.c.x - g.rx, g.c.y - g.rx), size = Size(g.rx * 2, g.rx * 2))
                    }
                }
            }
        },
        content = content,
    )
}

private class Glow(val c: Offset, val rx: Float, val ry: Float, tint: Color, alpha: Float, end: Float) {
    val brush = Brush.radialGradient(
        0f to tint.copy(alpha = alpha),
        end to tint.copy(alpha = 0f),
        center = c,
        radius = rx,
    )
}
