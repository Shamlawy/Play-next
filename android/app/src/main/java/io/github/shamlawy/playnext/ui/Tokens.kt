package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.gen.Web

/**
 * The web app's colour tokens (index.html `:root` and `html.ui2`), generated from index.html at build time
 * (tools/android-gen.mjs → gen/Web.kt), so the two apps can't drift. Screens take colours only from here, never a
 * literal. The accent follows your theme: read it from [LocalLook], not [Pn.volt] (Midnight's, for before your games load).
 */
object Pn {
    // surfaces (Clean)
    val pan = Color(Web.U_PAN) // --u-pan: panels, sheets, rows, inputs
    val card = Color(Web.U_CARD) // --u-card: cards, chips, Settings groups
    val card2 = Color(Web.U_CARD2) // --u-card2: a raised card on a card
    val line = Color(Web.U_LINE) // --u-line: rgba(234,242,255,.08)
    val line2 = Color(Web.U_LINE2) // --u-line2: rgba(234,242,255,.14)
    val ink = Color(Web.INK) // --ink: the rail
    val steel = Color(Web.STEEL) // --steel: the rail's round buttons
    val edge = Color(Web.EDGE) // --edge: the rail's border and separators

    // text and accents
    val ice = Color(Web.ICE) // --ice: main text
    val mute = Color(Web.MUTE) // --mute: labels, muted text
    val hot = Color(Web.HOT) // --hot: danger, errors
    val volt = Color(Web.VOLT) // --volt: Midnight's accent
    val volt2 = Color(Web.VOLT2) // --volt2: Midnight's second accent
    val void = Color(Web.VOID) // --void: text on an accent fill
    val white = Color.White // the folded tab bar's current label (#fff)

    // the background defaults (S.bgBase / S.bgTint / S.bgGrad)
    val bgBase = Color(Web.BG_BASE)
    val bgTint = Color(Web.BG_TINT)
    const val BG_GRAD = Web.BG_GRAD

    /** PALETTE (a game's own colour comes from these). */
    val palette = Web.PALETTE.map { Color(it) }

    // shapes
    val r = RoundedCornerShape(Web.U_R.dp) // --u-r: cards, rows, sheets
    val rs = RoundedCornerShape(Web.U_RS.dp) // --u-rs: buttons, fields, small things
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

/**
 * How the whole app looks, from your saved settings (`applyLook`, index.html 3817): the theme's two accents and the
 * background's base colour, tint and amount. Midnight on black until your games load.
 */
@Immutable
data class Look(
    val volt: Color = Pn.volt,
    val volt2: Color = Pn.volt2,
    val bgBase: Color = Pn.bgBase,
    val bgTint: Color = Pn.bgTint,
    val bgGrad: Int = Pn.BG_GRAD,
) {
    companion object {
        fun of(lib: Library?): Look {
            if (lib == null) return Look()
            val hex = { s: String -> Game.hex(s)?.let { Color(it) } }
            val (a, b) = when (lib.theme) {
                "chameleon" -> chameleon(lib)
                else -> (Web.THEMES.firstOrNull { it.key == lib.theme } ?: Web.THEMES.first { it.key == "midnight" }).let { Color(it.a) to Color(it.b) }
            }
            return Look(a, b, hex(lib.bgBase) ?: Pn.bgBase, hex(lib.bgTint) ?: Pn.bgTint, lib.bgGrad)
        }

        /**
         * "Now playing" (v220): the accents from the game you're playing (the newest one you started, while sessions
         * and On deck arrive with Phase 1 and 5), else Midnight's.
         */
        private fun chameleon(lib: Library): Pair<Color, Color> {
            val g = lib.games
                .filter { it.state == "playing" && !Backup.truthy(it["comp"]) && Game.hex(Backup.str(it["colour"])) != null }
                .maxByOrNull { Backup.num(it["plAt"], 0.0, 0.0, Double.MAX_VALUE) }
                ?: return Pn.volt to Pn.volt2
            return Color(chamTone(g.colour, .64)) to Color(chamTone(g.colour, .42))
        }

        /** `chamTone` (index.html 20820): the colour's hue at saturation .55-.95 and lightness [l]. */
        fun chamTone(argb: Long, l: Double): Long {
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val d = mx - mn
            var h = 0.0
            if (d != 0.0) h = if (mx == r) ((g - b) / d).rem(6.0) else if (mx == g) (b - r) / d + 2 else (r - g) / d + 4
            h = (h * 60 + 360).rem(360.0)
            val s = maxOf(.55, minOf(.95, if (d != 0.0) d / (1 - Math.abs(mx + mn - 1)) else 0.0))
            val sat = if (d != 0.0) s else 0.0
            val c = (1 - Math.abs(2 * l - 1)) * sat
            val x = c * (1 - Math.abs((h / 60).rem(2.0) - 1))
            val m = l - c / 2
            val (rr, gg, bb) = when {
                h < 60 -> Triple(c, x, 0.0)
                h < 120 -> Triple(x, c, 0.0)
                h < 180 -> Triple(0.0, c, x)
                h < 240 -> Triple(0.0, x, c)
                h < 300 -> Triple(x, 0.0, c)
                else -> Triple(c, 0.0, x)
            }
            fun ch(v: Double) = Math.round((v + m) * 255).coerceIn(0, 255)
            return 0xFF000000L or (ch(rr) shl 16) or (ch(gg) shl 8) or ch(bb)
        }
    }
}

val LocalLook = staticCompositionLocalOf { Look() }
