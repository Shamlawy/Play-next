package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
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
 * A flat button (`.btn`): nothing moves or glows when pressed (#309), at least 48dp tall. The primary one is filled with
 * your theme's accent.
 */
@Composable
fun Btn(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .sizeIn(minHeight = Pn.touch)
            .background(if (primary) look.volt else Pn.card2, Pn.rs)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.button.copy(color = if (primary) Pn.void else Pn.ice), softWrap = false, maxLines = 1)
    }
}
