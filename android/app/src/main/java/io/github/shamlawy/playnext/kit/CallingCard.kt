package io.github.shamlawy.playnext.kit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The calling card (`callingCard`, index.html 8238-8333): a 1080x1920 picture of a game to share, drawn as the web draws
 * it on its canvas: black, a halftone corner and three slanted slashes in the game's colour, "CALLING CARD" as a ransom
 * note (each letter on its own torn scrap of paper, turned a little, in one of five paper/ink pairs and five type
 * faces), the cover pinned at an angle on a white frame with a coloured shadow, the title as a ransom note (3 lines at
 * most), a typed note on a strip of paper, the signature and PLAY NEXT. The scraps are picked by a generator seeded by
 * the title ([seedOf], Mulberry32 as the web's), so a game always gets the same card; Reshuffle passes a random seed.
 * Pure drawing: the typefaces and the cover come from the caller (the web's Archivo, Georgia, Courier New, Arial Black
 * and Times are the app's Archivo, Playfair and Android's own mono and serif).
 */
object CallingCard {
    const val W = 1080
    const val H = 1920

    /** The five type faces, in the web's order: italic black display, bold serif, bold mono, black display, bold italic serif. */
    class Faces(val display: Typeface, val displayItalic: Typeface, val serif: Typeface, val mono: Typeface, val serifItalic: Typeface) {
        internal val list get() = listOf(displayItalic, serif, mono, display, serifItalic)
    }

    /** The web's seed: `[...title].reduce((a, ch) => (a * 31 + ch.charCodeAt(0)) >>> 0, 7)`. */
    fun seedOf(title: String): Long {
        var a = 7L
        var i = 0
        while (i < title.length) {
            val cp = title.codePointAt(i)
            a = (a * 31 + title[i].code) and 0xFFFFFFFFL
            i += Character.charCount(cp)
        }
        return a
    }

    /** Mulberry32 exactly as the web writes it: numbers in [0, 1). */
    class Rnd(seed: Long) {
        private var s = seed.toInt()
        fun next(): Double {
            s += 0x6D2B79F5
            var t = (s xor (s ushr 15)) * (1 or s)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    /** The note (`msg`): played, out later, or waiting; with your hype when there is one. */
    fun note(played: Boolean, date: String, hype: Double, now: Long): String {
        val hl = if (hype > 0) " Your hype stands at ${num(hype)}/10." else ""
        val at = io.github.shamlawy.playnext.data.JsDate.utcMidnight(date)
        return when {
            played -> "We have beaten you once before.$hl We are coming back for another run."
            at != null && at > now -> {
                val d = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate()
                "You arrive on ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.UK)}.$hl Clear your schedule. We are coming to play you."
            }
            else -> "You have waited in the queue long enough.$hl We are coming to play you next."
        }
    }

    private fun num(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /** Draws the card. [accent] is the game's colour (ARGB), [cover] its cover or null (a block of the colour then). */
    fun draw(title: String, accent: Int, cover: Bitmap?, note: String, signer: String, seed: Long, faces: Faces): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Rnd(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.BLACK)
        // the halftone corner
        p.color = accent
        p.alpha = (255 * .35f).roundToInt()
        var yy = 0
        while (yy < 760) {
            var xx = 480
            while (xx < W + 30) {
                val r = (xx - 480) / 600.0 * 10 - yy / 760.0 * 9
                if (r > .6) c.drawCircle(xx.toFloat(), yy.toFloat(), r.toFloat(), p)
                xx += 30
            }
            yy += 30
        }
        // the slashes, slanted as `transform(1, 0, -.22, 1, 0, 0)`
        c.save()
        c.concat(Matrix().apply { setValues(floatArrayOf(1f, -.22f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) })
        for ((a, w, al) in listOf(Triple(1090f, 70f, .9f), Triple(1180f, 18f, .6f), Triple(-40f, 26f, .5f))) {
            p.color = accent
            p.alpha = (255 * al).roundToInt()
            c.drawRect(a, 0f, a + w, H.toFloat(), p)
        }
        c.restore()
        p.alpha = 255

        val fonts = faces.list
        val paper = listOf(Color.WHITE to Color.BLACK, Color.BLACK to Color.WHITE, accent to Color.BLACK, Color.WHITE to accent, Color.WHITE to Color.BLACK)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(140, 255, 255, 255) }
        fun letter(ch: String, cx: Double, cy: Double, sz: Double) {
            val (bg, fg) = paper[(rnd.next() * paper.size).toInt()]
            c.save()
            c.translate(cx.toFloat(), cy.toFloat())
            c.rotate(Math.toDegrees((rnd.next() - .5) * .3).toFloat())
            ink.typeface = fonts[(rnd.next() * fonts.size).toInt()]
            ink.textSize = sz.roundToInt().toFloat()
            val pw = max(ink.measureText(ch).toDouble(), sz * .45) + sz * .26
            val ph = sz * 1.1
            val j = { (rnd.next() - .5) * sz * .14 }
            val path = Path().apply {
                moveTo((-pw / 2 + j()).toFloat(), (-ph / 2 + j()).toFloat())
                lineTo((pw / 2 + j()).toFloat(), (-ph / 2 + j()).toFloat())
                lineTo((pw / 2 + j()).toFloat(), (ph / 2 + j()).toFloat())
                lineTo((-pw / 2 + j()).toFloat(), (ph / 2 + j()).toFloat())
                close()
            }
            p.color = bg
            c.drawPath(path, p)
            if (bg == Color.BLACK) c.drawPath(path, edge)
            ink.color = fg
            // `textBaseline = "middle"` at sz * .05
            val fm = ink.fontMetrics
            c.drawText(ch, 0f, (sz * .05).toFloat() - (fm.ascent + fm.descent) / 2, ink)
            c.restore()
        }
        fun ransom(text: String, y0: Double, size0: Double, maxW: Double, maxLines: Int): Double {
            val words = text.uppercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val longest = max(1, words.maxOfOrNull { it.length } ?: 1)
            val size = min(size0, maxW / (longest * .8))
            val cw = size * .8
            val gap = size * .38
            val lines = mutableListOf(mutableListOf<String>())
            var w = 0.0
            for (wd in words) {
                val ww = wd.length * cw
                if (w > 0 && w + gap + ww > maxW) { lines += mutableListOf<String>(); w = 0.0 }
                lines.last() += wd
                w += (if (w > 0) gap else 0.0) + ww
            }
            if (lines.size > maxLines) { while (lines.size > maxLines) lines.removeAt(lines.size - 1); lines.last() += "…" }
            lines.forEachIndexed { li, ln ->
                val lw = ln.sumOf { it.length * cw } + gap * (ln.size - 1)
                var cx = (W - lw) / 2 + cw / 2
                for (wd in ln) {
                    for (ch in wd) {
                        letter(ch.toString(), cx, y0 + li * size * 1.25, size * (.86 + rnd.next() * .28))
                        cx += cw
                    }
                    cx += gap
                }
            }
            return y0 + lines.size * size * 1.25
        }
        ransom("Calling card", 150.0, 60.0, 800.0, 1)
        // the cover, pinned at an angle
        c.save()
        c.translate(W / 2f, 560f)
        c.rotate(Math.toDegrees(-.1).toFloat())
        val cw = 420f
        val ch = 560f
        p.color = accent
        c.drawRect(-cw / 2 + 22, -ch / 2 + 22, -cw / 2 + 22 + cw + 28, -ch / 2 + 22 + ch + 28, p)
        p.color = Color.WHITE
        c.drawRect(-cw / 2 - 14, -ch / 2 - 14, -cw / 2 - 14 + cw + 28, -ch / 2 - 14 + ch + 28, p)
        if (cover != null && cover.width > 0 && cover.height > 0) {
            val r = max(cw / cover.width, ch / cover.height)
            val sw = cw / r
            val sh = ch / r
            val src = Rect(((cover.width - sw) / 2).roundToInt(), ((cover.height - sh) / 2).roundToInt(), ((cover.width + sw) / 2).roundToInt(), ((cover.height + sh) / 2).roundToInt())
            c.drawBitmap(cover, src, RectF(-cw / 2, -ch / 2, cw / 2, ch / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            p.color = accent
            c.drawRect(-cw / 2, -ch / 2, cw / 2, ch / 2, p)
        }
        c.restore()
        var y = ransom(title, 1010.0, 120.0, 960.0, 3) + 30
        // the note, typed on a strip of paper
        val type = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = faces.mono; textSize = 40f; color = Color.BLACK }
        val lines = mutableListOf("")
        for (wd in note.split(" ")) {
            val t = (lines.last() + " " + wd).trim()
            if (type.measureText(t) > 800 && lines.last().isNotEmpty()) lines += wd else lines[lines.size - 1] = t
        }
        val sh2 = lines.size * 54f + 56
        c.save()
        c.translate(W / 2f, (y + sh2 / 2).toFloat())
        c.rotate(Math.toDegrees(-.02).toFloat())
        p.color = Color.WHITE
        c.drawRect(-440f, -sh2 / 2, 440f, sh2 / 2, p)
        lines.forEachIndexed { k, t -> c.drawText(t, -410f, -sh2 / 2 + 70 + k * 54, type) }
        c.restore()
        y += sh2 + 90
        val sign = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = faces.displayItalic; textSize = 54f; color = accent; textAlign = Paint.Align.RIGHT }
        c.drawText("— $signer", 980f, min(y, 1790.0).toFloat(), sign)
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = faces.displayItalic; textSize = 40f; color = Color.WHITE }
        c.drawText("PLAY NEXT", 70f, 1870f, brand)
        return bmp
    }
}
