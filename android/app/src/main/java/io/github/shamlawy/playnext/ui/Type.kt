package io.github.shamlawy.playnext.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.R
import io.github.shamlawy.playnext.gen.Web

/**
 * The web app's fonts, bundled (they never wait on the network): Poppins for everything (Clean, 400/500/600/700),
 * Archivo for the few big italic display words (the tab-switch slash, celebrations) and Playfair Display for the
 * Game Shuffler's card. 800/900 on Poppins draw as 700, as the web app's browsers do (no files for them).
 */
object Fonts {
    val poppins = FontFamily(
        Font(R.font.poppins_400, FontWeight.Normal),
        Font(R.font.poppins_500, FontWeight.Medium),
        Font(R.font.poppins_600, FontWeight.SemiBold),
        Font(R.font.poppins_700, FontWeight.Bold),
        Font(R.font.poppins_700, FontWeight.ExtraBold),
        Font(R.font.poppins_700, FontWeight.Black),
    )

    /**
     * Archivo italic 900, the "Persona" display face, at its default width as `.pnw-slab b` draws it (the web's other
     * Archivo rules set their own `wdth`, 60-84: their screens bring their own faces).
     */
    val archivo = FontFamily(
        Font(R.font.archivo_italic, weight = FontWeight.Black, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.width(100f))),
        Font(R.font.archivo, weight = FontWeight.ExtraBold, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(100f))),
    )

    /**
     * Reads the fonts the first screens draw, once, off the main thread (in Compose's shared cache): the first tab switch
     * read the 741 KB Archivo italic on the main thread mid-slash.
     */
    fun warm(ctx: Context) {
        val r = createFontFamilyResolver(ctx)
        r.resolve(archivo, FontWeight.Black, FontStyle.Italic)
        for (w in listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) r.resolve(poppins, w)
    }

    val playfair = FontFamily(
        Font(R.font.playfair, weight = FontWeight.SemiBold, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.playfair, weight = FontWeight.Bold, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
}

/**
 * The Clean type scale (android/parity/0-map/2-look.md §2): sentence case, upright, 500/600/700. One web px = one app
 * sp at the Fold's settings (gate 4 calibration). Colours are applied where they're used.
 */
object Type {
    private val base = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)

    /** Body: 15/1.45. */
    val body = base.copy(fontSize = 15.sp, lineHeight = 21.75.sp)

    /** Screen title `.tabbar h3`: 26 (30 from 620 wide), 700, -.015em, 1.1. */
    fun title(wide: Boolean) = base.copy(
        fontSize = if (wide) 30.sp else 26.sp, lineHeight = if (wide) 33.sp else 28.6.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em, lineBreak = LineBreak.Heading,
    )

    /** A sheet's title (`.ehead h3`, Settings): 22, 700. */
    val sheetTitle = base.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, lineBreak = LineBreak.Heading)

    /** A card's heading (Settings group head): 14.5 600. */
    val head = base.copy(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading)

    /** Small grey lines (`.gdesc`, notes): 12.5/13, 400. */
    val note = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Pn.mute)

    /** Labels: 12.5 600 mute, .01em. */
    val label = base.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute, letterSpacing = 0.01.em)

    /** Kickers: 600, .08em tracking (sentence case, never upper). */
    val kicker = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = Pn.mute, letterSpacing = 0.08.em)

    /** A row's name: 600. */
    val name = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading)

    /** Stat numbers: 700, -.01em. */
    val stat = base.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)

    /** `.btn`: 600, tracking 0. */
    val button = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    /** The folded tab bar's current label: 600 10/1, .01em, white. */
    val tab = base.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em, color = Pn.white)
}

/** The most a fixed-size label grows with the phone's font size (critic G4): body text scales freely. */
const val FIXED_SCALE_MAX = 1.3f

/**
 * This style for a fixed-size label (a tab's name, the pill's views, header numbers, straps, tickets): it grows with
 * the phone's font size up to [FIXED_SCALE_MAX], then stays, so a big font setting can't push it out of its tile.
 */
@Composable
fun TextStyle.fixed(): TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= FIXED_SCALE_MAX) return this
    val k = FIXED_SCALE_MAX / scale
    return copy(fontSize = fontSize * k, lineHeight = if (lineHeight.isSp) lineHeight * k else lineHeight)
}

/**
 * The web app's drawn icons (v247 style: 24x24, one colour, one part at .35), generated from index.html. A tint keeps
 * each part's alpha, so the .35 part stays faint in any colour.
 */
object Icons {
    private val cache = HashMap<String, ImageVector>()

    fun of(name: String): ImageVector = cache.getOrPut(name) {
        val parts = Web.ICONS[name] ?: Web.ICONS.getValue("settings")
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in parts) {
                addPath(
                    pathData = PathParser().parsePathString(p.d).toNodes(),
                    pathFillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                    fill = SolidColor(Color.Black),
                    fillAlpha = p.alpha,
                )
            }
        }.build()
    }
}

/** A drawn icon in [tint]. [label] is what TalkBack says (gate 10); null when the words next to it already say it. */
@Composable
fun PnIcon(name: String, tint: Color, size: Dp, label: String?, modifier: Modifier = Modifier) {
    val v = remember(name) { Icons.of(name) }
    Image(rememberVectorPainter(v), contentDescription = label, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}
