package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The web app's colour tokens (index.html `:root` and `html.ui2`, android/parity/0-map/2-look.md §1, web v327).
 * Screens take colours only from here, never a literal. Piece 2's generator (tools/android-gen.mjs) replaces this
 * hand copy with one made from index.html, so the two can't drift.
 */
object Pn {
    // surfaces (Clean)
    val pan = Color(0xFF0A0E1B) // --u-pan: panels, sheets, rows, inputs
    val card = Color(0xFF131A32) // --u-card: cards, chips, Settings groups
    val card2 = Color(0xFF19213D) // --u-card2: a raised card on a card
    val line = Color(0x14EAF2FF) // --u-line: rgba(234,242,255,.08)
    val line2 = Color(0x24EAF2FF) // --u-line2: rgba(234,242,255,.14)

    // text and accents
    val ice = Color(0xFFEAF2FF) // --ice: main text
    val mute = Color(0xFF7C92C4) // --mute: labels, muted text
    val hot = Color(0xFFFF5470) // --hot: danger, errors
    val volt = Color(0xFF4DA3FF) // --volt: the accent
    val volt2 = Color(0xFF2F6BFF) // --volt2: the second accent
    val void = Color(0xFF04060E) // --void: text on an accent fill

    // the background (S.bgBase / S.bgTint / S.bgGrad defaults)
    val bgBase = Color(0xFF000000) // BG_BASES[0], black
    val bgTint = Color(0xFF2F6BFF) // BG_TINT, darkened 35% toward black when drawn
    const val BG_GRAD = 30 // %

    /** PALETTE's first 16 (a game's own colour comes from these). */
    val palette = listOf(
        0xFF4DA3FF, 0xFF2E6BFF, 0xFF5AE0FF, 0xFF3FD9A4, 0xFF5BD75B, 0xFFC8E04A, 0xFFF0A63C, 0xFFFF7A3C,
        0xFFFF5470, 0xFFFF7AD9, 0xFFB45CFF, 0xFF7C5CFF, 0xFFC8A44A, 0xFF9AA7BF, 0xFFE8E2D0, 0xFFFF3355,
    ).map { Color(it) }

    // shapes
    val r = RoundedCornerShape(20.dp) // --u-r: cards, rows, sheets
    val rs = RoundedCornerShape(14.dp) // --u-rs: buttons, fields, small things
    val pill = RoundedCornerShape(999.dp)

    /** The smallest touch target (48dp), even when the drawn thing is smaller. */
    val touch = 48.dp
}

/** `mix(a, b, t)` from index.html: t of the way from a to b. */
fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
