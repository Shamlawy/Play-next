package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A touch that starts on something that scrolls sideways is that thing's own (the web's `sideScroller`): the tab
 * swipes leave it alone. The shell clears it at each first touch; a side scroller marks it in the same pass.
 */
class SwipeGuard {
    var inScroller = false

    /** When the last finger lifted, and when what's open last changed (that lift's time): see Shell's settle. */
    var lastUp = Long.MIN_VALUE / 2
    var settle = Long.MIN_VALUE / 2
}

val LocalSwipeGuard = staticCompositionLocalOf { SwipeGuard() }

/**
 * The open tab's views where the rail isn't shown (folded, the owner's Option B, Nov 6): a segmented bar at the top of
 * the page, sticky while the page scrolls (nothing at the bottom, where Samsung's taskbar sits). The lit view is filled
 * with your accent, the others mute. The segments share the width; when their words don't fit it scrolls sideways
 * (a touch on it is its own then, not a view swipe). Nothing slides or fades: a pick lights the new one at once.
 */
@Composable
fun ViewBar(views: List<Pair<String, String>>, on: String, pick: (String) -> Unit, modifier: Modifier = Modifier) {
    val volt = LocalLook.current.volt
    val guard = LocalSwipeGuard.current
    val scroll = rememberScrollState()
    val shape = RoundedCornerShape(999.dp)
    val style = BAR.fixed()
    Box(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
            .background(Pn.pan, shape)
            .border(1.dp, Pn.line2, shape)
            .pointerInput(scroll) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (scroll.maxValue > 0 && e.changes.any { it.pressed && !it.previousPressed }) guard.inScroller = true
                    }
                }
            }
            .padding(4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val avail = constraints.maxWidth
            Layout(
                content = {
                    for ((k, label) in views) {
                        key(k) {
                            val sel = k == on
                            Box(
                                Modifier
                                    .then(if (sel) Modifier.background(volt, shape) else Modifier)
                                    .clickable(role = Role.Tab) { if (!sel) pick(k) }
                                    .semantics { selected = sel; contentDescription = label }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(label, style = style.copy(color = if (sel) Pn.void else Pn.mute), softWrap = false, maxLines = 1)
                            }
                        }
                    }
                },
                modifier = Modifier.horizontalScroll(scroll),
            ) { parts, _ ->
                val h = SEG_H.roundToPx()
                val gap = 2.dp.roundToPx()
                val natural = parts.map { it.maxIntrinsicWidth(h) }
                val sum = natural.sum() + gap * (parts.size - 1).coerceAtLeast(0)
                // room to spare: every segment gets an equal share of it (the bar fills the width); else they keep their
                // own widths and the bar scrolls
                val extra = if (sum < avail && parts.isNotEmpty()) (avail - sum) / parts.size else 0
                val placed = parts.mapIndexed { i, m -> m.measure(Constraints.fixed(natural[i] + extra, h)) }
                val w = placed.sumOf { it.width } + gap * (placed.size - 1).coerceAtLeast(0)
                layout(w, h) {
                    var x = 0
                    for (pl in placed) {
                        pl.place(x, 0)
                        x += pl.width + gap
                    }
                }
            }
        }
    }
}

/** A segment's height: 40, so with the bar's 4 round it the touch is 48. */
private val SEG_H = 40.dp

/** The segments' words: Poppins 600 13.5, sentence case. */
private val BAR = androidx.compose.ui.text.TextStyle(fontFamily = Fonts.poppins, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
