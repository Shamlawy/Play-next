package io.github.shamlawy.playnext.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The web app's colour tokens (index.html `:root` and `html.ui2`, android/parity/0-map/2-look.md §1) and the default
 * background (black base, the #2F6BFF tint darkened 35% toward black, at 30%). Screens use these, never a colour literal.
 * Piece 2's generator (tools/android-gen.mjs) reads them from index.html instead, with the themes and BG_BASES.
 */
object Pn {
    val void = Color(0xFF04060E)
    val volt = Color(0xFF4DA3FF)
    val ice = Color(0xFFEAF2FF)
    val mute = Color(0xFF7C92C4)
    val hot = Color(0xFFFF5470)
    val green = Color(0xFF3DDC84)

    val pan = Color(0xFF0A0E1B)
    val card = Color(0xFF131A32)
    val card2 = Color(0xFF19213D)
    val line = ice.copy(alpha = .08f)
    val line2 = ice.copy(alpha = .14f)

    val bgBase = Color(0xFF000000)
    val bgTint = Color(0xFF1F46A6) // 31,70,166
    const val bgGrad = .30f

    /** PALETTE's first eight (index.html 3784): the probe's made-up pictures and straps. */
    val palette = listOf(
        Color(0xFF4DA3FF), Color(0xFF2E6BFF), Color(0xFF5AE0FF), Color(0xFF3FD9A4),
        Color(0xFF5BD75B), Color(0xFFC8E04A), Color(0xFFF0A63C), Color(0xFFFF7A3C),
    )

    val r = 20.dp
    val rs = 14.dp
}
