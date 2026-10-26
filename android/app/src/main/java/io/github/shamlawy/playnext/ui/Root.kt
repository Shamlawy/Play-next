package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Nothing draws under the finger (#309, v280): every clickable gets this instead of a ripple or a press overlay. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode() = -1
    override fun equals(other: Any?) = other === this
}

/** The app's root: no touch effects, no overscroll stretch, the one still background behind everything. */
@Composable
fun PnRoot(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides NoIndication, LocalOverscrollFactory provides null) {
        Box(Modifier.fillMaxSize().appBackground()) { content() }
    }
}

/**
 * index.html's `html.paper::before`: two elliptical glows of the tint over the base colour, at bgGrad. CSS ellipses
 * become circles scaled on one axis; the brushes are made once per size and nothing here ever animates.
 */
fun Modifier.appBackground(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val t = Pn.bgTint
    // radial-gradient(130% 75% at 50% 118%, tint .9 0%, tint 0 62%)
    val c1 = Offset(w * .5f, h * 1.18f)
    val rx1 = w * 1.3f
    val sy1 = h * .75f / rx1
    val b1 = Brush.radialGradient(0f to t.copy(alpha = .9f), .62f to t.copy(alpha = 0f), center = c1, radius = rx1)
    // radial-gradient(80% 55% at 104% -6%, tint .35 0%, tint 0 70%)
    val c2 = Offset(w * 1.04f, h * -.06f)
    val rx2 = w * .8f
    val sy2 = h * .55f / rx2
    val b2 = Brush.radialGradient(0f to t.copy(alpha = .35f), .7f to t.copy(alpha = 0f), center = c2, radius = rx2)
    onDrawBehind {
        drawRect(Pn.bgBase)
        if (Pn.bgGrad <= 0f) return@onDrawBehind
        // Scaling y about the centre squashes the circle into the CSS ellipse; the rect is stretched back to cover the screen.
        scale(1f, sy1, pivot = c1) { drawRect(b1, topLeft = Offset(0f, c1.y - c1.y / sy1), size = size.copy(height = h / sy1), alpha = Pn.bgGrad) }
        scale(1f, sy2, pivot = c2) { drawRect(b2, topLeft = Offset(0f, c2.y - c2.y / sy2), size = size.copy(height = h / sy2), alpha = Pn.bgGrad) }
    }
}

/** A plain Clean-style button: a card surface, 48dp tall at least, no press effect. [main] = the accent fill. */
@Composable
fun PnButton(label: String, modifier: Modifier = Modifier, main: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Pn.rs)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (main) Pn.volt else Pn.card, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(color = if (main) Pn.void else Pn.ice, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
    }
}
