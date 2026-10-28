package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A card (`--u-card`, round 20, 16 in). */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Pn.card, Pn.r).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * A flat button (`html.ui2 .btn`, index.html 16450): as wide as its words (12/20 in), at least 48dp tall, round
 * `--u-rs`. The primary one is your theme's accent with its soft glow under it; the others (`.btn.ghost`) are
 * `--u-card` with a `--u-line2` line. Nothing moves or glows when pressed (#309).
 */
@Composable
fun Btn(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        modifier
            .padding(top = 4.dp)
            .sizeIn(minHeight = Pn.touch)
            .then(if (primary) Modifier.shadow(10.dp, Pn.rs, ambientColor = Color.Transparent, spotColor = look.volt.copy(alpha = .8f)) else Modifier)
            .background(if (primary) look.volt else Pn.card, Pn.rs)
            .then(if (primary) Modifier else Modifier.border(1.dp, Pn.line2, Pn.rs))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.button.copy(color = if (primary) Pn.void else Pn.ice), softWrap = false, maxLines = 1)
    }
}
