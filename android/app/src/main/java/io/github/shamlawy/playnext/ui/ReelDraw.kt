package io.github.shamlawy.playnext.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "One pixel plays everything", the 15 s opening reel (`pnReel.draw`, index.html 2929; android/parity/3-map/
 * 2-rail-reel-widgets.md §2), line for line on Android's canvas: every pixel comes from t alone (no state between
 * frames), units are the screen's short side = 1000 with 0,0 in the middle. Blue, dark blue, pink, white and black; the
 * eases are the web's exponential ones and its "springs" settle without a bounce. The one change: a cover lands on its
 * tilted card with a true perspective map ([Matrix.setPolyToPoly]) where the web needs two flat halves.
 */
object ReelDraw {
    const val LEN = 15f

    /** What the app passes: up to 9 small covers card by card (4 = the centre), the game the centre becomes, its hours. */
    class Art(val covers: List<Bitmap?>, val title: String, val hours: Double)

    private val B = floatArrayOf(77f, 163f, 255f)
    private val D = floatArrayOf(47f, 107f, 255f)
    private val P = floatArrayOf(255f, 84f, 112f)
    private val W = floatArrayOf(255f, 255f, 255f)
    private val K = floatArrayOf(0f, 0f, 0f)

    private fun cl(x: Float, a: Float = 0f, b: Float = 1f) = if (x < a) a else if (x > b) b else x
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun seg(t: Float, a: Float, d: Float) = cl((t - a) / d)
    private val E10 = 2.0.pow(-10).toFloat()
    private fun eo(x: Float) = if (x <= 0f) 0f else if (x >= 1f) 1f else (1 - 2.0.pow(-10.0 * x).toFloat()) / (1 - E10)
    private fun ei(x: Float) = if (x <= 0f) 0f else if (x >= 1f) 1f else (2.0.pow(10.0 * x - 10).toFloat() - E10) / (1 - E10)
    private fun eio(x: Float) = if (x < .5f) ei(x * 2) / 2 else .5f + eo(x * 2 - 1) / 2

    /** A critically damped spring from rest at time [a] (no overshoot: nothing bounces). */
    private fun spr(t: Float, a: Float, w: Float = 16f): Float {
        val x = t - a
        return if (x <= 0f) 0f else 1 - (1 + w * x) * exp(-w * x)
    }

    private fun mix(a: FloatArray, b: FloatArray, k: Float) = floatArrayOf(lerp(a[0], b[0], k), lerp(a[1], b[1], k), lerp(a[2], b[2], k))
    private fun argb(c: FloatArray, al: Float) = android.graphics.Color.argb((cl(al) * 255).roundToInt(), c[0].toInt(), c[1].toInt(), c[2].toInt())

    /** Points are x,y pairs in one array. */
    private fun rect5(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, (y0 + y1) / 2, x1, y1, x0, y1)
    private fun lp(a: FloatArray, b: FloatArray, k: Float) = FloatArray(a.size) { lerp(a[it], b[it], k) }
    private fun rot(p: FloatArray, a: Float, cx: Float = 0f, cy: Float = 0f): FloatArray {
        val c = cos(a)
        val s = sin(a)
        return FloatArray(p.size).also { o ->
            for (i in 0 until p.size / 2) {
                val x = p[2 * i] - cx
                val y = p[2 * i + 1] - cy
                o[2 * i] = cx + x * c - y * s
                o[2 * i + 1] = cy + x * s + y * c
            }
        }
    }

    /** The ▶ in three slices (so the three queue lines can fold into it), 5 points each: TL TR RM BR BL. */
    private val TRI = arrayOf(
        floatArrayOf(-170f, -200f, -170f, -200f, -37f, -133.5f, 96f, -67f, -170f, -67f),
        floatArrayOf(-170f, -67f, 96f, -67f, 230f, 0f, 96f, 67f, -170f, 67f),
        floatArrayOf(-170f, 67f, 96f, 67f, -37f, 133.5f, -170f, 200f, -170f, 200f),
    )

    /** Half the opening pixel. */
    private const val PIX = 22f

    private const val N = 120
    private fun rBox(hx: Float, hy: Float, a: Float) = min(hx / max(1e-6f, abs(cos(a))), hy / max(1e-6f, abs(sin(a))))
    private fun circ(r: Float): (Float) -> Float = { r }
    private fun box(s: Float): (Float) -> Float = { a -> rBox(s, s, a) }
    private fun plus(l: Float, w: Float): (Float) -> Float = { a -> max(rBox(l, w, a), rBox(w, l, a)) }
    private fun radial(f: (Float) -> Float, g: (Float) -> Float, k: Float, spin: Float, cx: Float, cy: Float) = FloatArray(N * 2).also { o ->
        for (i in 0 until N) {
            val a = i.toFloat() / N * PI.toFloat() * 2
            val r = lerp(f(a), g(a), k)
            o[2 * i] = cx + r * cos(a + spin)
            o[2 * i + 1] = cy + r * sin(a + spin)
        }
    }

    private val WORDS = arrayOf("What", "do I", "play", "next?")

    // ---- 3D ----
    /** Focal length: a point at z = 0 is drawn 1:1. */
    private const val F3 = 1500f
    private fun proj(p: FloatArray, dz: Float = 0f): FloatArray? {
        val z = p[2] + F3 - dz
        return if (z < 40f) null else floatArrayOf(p[0] * F3 / z, p[1] * F3 / z, z)
    }
    private fun rx(p: FloatArray, a: Float): FloatArray { val c = cos(a); val s = sin(a); return floatArrayOf(p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c) }
    private fun ry(p: FloatArray, a: Float): FloatArray { val c = cos(a); val s = sin(a); return floatArrayOf(p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c) }
    private val CARDC = arrayOf(arrayOf(B, W, P), arrayOf(P, D, B), arrayOf(D, W, P))

    /** Where everything sits for this frame: one per typeface, kept between frames (nothing new each frame), its canvas set per [frame] call. */
    private class Pen(var c: Canvas, val face: Typeface) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        val pic = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val path = Path()
        val m = Matrix()
        val r = RectF()

        fun poly(p: FloatArray, col: FloatArray, al: Float = 1f) {
            if (al <= 0f) return
            path.rewind()
            path.moveTo(p[0], p[1])
            var i = 2
            while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
            path.close()
            fill.color = argb(col, al)
            c.drawPath(path, fill)
        }

        fun rr(x0: Float, y0: Float, w: Float, h: Float, rad: Float, col: FloatArray, al: Float = 1f) {
            val k = max(0f, min(rad, min(w / 2, h / 2)))
            r.set(x0, y0, x0 + w, y0 + h)
            fill.color = argb(col, al)
            c.drawRoundRect(r, k, k, fill)
        }

        fun size(f: Float) { text.textSize = max(f, .01f) }
        fun measure(s: String) = text.measureText(s)

        fun say(s: String, x: Float, y: Float, col: FloatArray, al: Float = 1f) {
            text.color = argb(col, al)
            c.drawText(s, x, y, text)
        }

        /** A picture on the projected quad [q] (TL TR BR BL), in true perspective. */
        fun tex(im: Bitmap, q: FloatArray, al: Float) {
            val w = im.width.toFloat()
            val h = im.height.toFloat()
            if (!m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, q, 0, 4)) return
            pic.alpha = (cl(al) * 255).roundToInt()
            c.drawBitmap(im, m, pic)
        }
    }

    private class Letter(val ch: String, val x: Float)
    private class Word(val full: Float, val out: List<Letter>)

    /** The x of each letter of [w] drawn centred at the pen's size. */
    private fun letters(p: Pen, w: String): Word {
        val full = p.measure(w)
        return Word(full, w.indices.map { i -> Letter(w[i].toString(), -full / 2 + p.measure(w.substring(0, i))) })
    }

    private fun wordF(p: Pen, hw: Float, hh: Float): Float {
        p.size(100f)
        var m = 0f
        for (w in WORDS) m = max(m, p.measure(w) / 100f)
        return min(hh * .76f, hw * 2 * .84f / m)
    }

    private class Card(val centre: Boolean, val col: FloatArray, val pr: List<FloatArray>, val w3: (Float, Float) -> FloatArray?, val im: Bitmap?, val depth: Float, val face: Float)
    private class Col(val x: Float, val z: Float, val w: Float, val h: Float, val col: FloatArray, val al: Float)
    private class Block(val col: Col, val pts: List<FloatArray>, val faces: List<Pair<IntArray, Float>>, val dep: Float)

    private val FACES = listOf(intArrayOf(0, 1, 2, 3) to .88f, intArrayOf(4, 5, 1, 0) to 1f, intArrayOf(1, 5, 6, 2) to .5f, intArrayOf(3, 2, 6, 7) to .35f, intArrayOf(7, 4, 0, 3) to .5f)
    private val HS = floatArrayOf(.42f, .7f, .5f, .95f, .62f, .36f, .8f, .55f, .66f)

    private var pen: Pen? = null

    /** Paints the frame at [t0] seconds on [c] ([wPx] x [hPx], 0,0 top left) in [face] (Poppins 700). */
    fun frame(c: Canvas, t0: Float, wPx: Float, hPx: Float, face: Typeface, art: Art? = null) {
        val t = ((t0 % LEN) + LEN) % LEN
        val u = min(wPx, hPx) / 1000f
        val hw = wPx / 2 / u
        val hh = hPx / 2 / u
        val p = pen?.takeIf { it.face === face }?.also { it.c = c } ?: Pen(c, face).also { pen = it }
        // lay: the phone panel the centre card becomes
        val ar = 200f / 260f
        val ph = min(hh * 2 * .8f, hw * 2 * .86f / ar)
        val pw = ph * ar
        val px = -pw / 2
        val py = -ph / 2
        c.save()
        c.drawColor(android.graphics.Color.BLACK)
        c.translate(wPx / 2, hPx / 2)
        c.scale(u, u)

        // ===== bar 1 (0-2): the pixel → a bar → three queue lines → ▶ =====
        if (t < 2.2f) {
            val a = eo(seg(t, 0f, .4f)) * PI.toFloat() / 2
            val s1 = eo(seg(t, .5f, .32f))
            val s2 = eo(seg(t, 1f, .32f))
            val s3 = eio(seg(t, 1.5f, .36f))
            val s4 = eo(seg(t, 2f, .16f))
            val col = if (s3 > 0) mix(W, B, s3) else mix(B, W, s1)
            val bars = arrayOf(floatArrayOf(-380f, 380f, -1f), floatArrayOf(-380f, 220f, 0f), floatArrayOf(-380f, 60f, 1f))
            val f = wordF(p, hw, hh)
            p.size(f)
            val lw = letters(p, WORDS[0])
            val cur = floatArrayOf(-lw.full / 2 - f * .16f, -lw.full / 2 - f * .1f, f * .44f)
            bars.forEachIndexed { i, (x0, x1, k) ->
                val h = lerp(PIX * 2, 26f, s1)
                val y = lerp(0f, k * 70, s2)
                val l0 = lerp(-PIX, x0, s1)
                val r0 = lerp(PIX, 380f, s1)
                val r1 = lerp(r0, x1, s2)
                var q = rect5(l0, y - h / 2, r1, y + h / 2)
                if (s1 == 0f) q = rot(rect5(-PIX, -PIX, PIX, PIX), a)
                q = lp(q, TRI[i], s3)
                if (s4 > 0) {
                    val third = cur[2] * 2 / 3
                    q = lp(q, rect5(cur[0], -cur[2] + third * i, cur[1], -cur[2] + third * (i + 1)), s4)
                }
                if (i == 0 || s2 > 0) p.poly(q, if (s4 > 0) mix(col, B, s4) else col)
            }
        }

        // ===== bar 2 (2-4): kinetic type. The ▶ flattens into a cursor that wipes in "What", then a word a beat =====
        if (t >= 2.1f && t < 4.35f) {
            val f = wordF(p, hw, hh)
            val base = f * .35f
            val cols = arrayOf(W, W, B, W)
            p.size(f)
            for (wi in 0 until 4) {
                val tw = 2f + wi * .5f
                if (t < tw - .02f) continue
                val lw = letters(p, WORDS[wi])
                val outT = tw + .5f
                lw.out.forEachIndexed { li, l ->
                    var dy = 0f
                    if (wi > 0) dy = (1 - eo(seg(t, tw + .04f + li * .025f, .28f))) * f * 1.3f
                    if (t >= outT - .08f) dy -= ei(seg(t, outT - .08f + li * .015f, .16f)) * f * 1.3f
                    if (dy < -f * 1.3f + 1) return@forEachIndexed
                    c.save()
                    c.clipRect(-hw * 2, base - f * 1.02f, hw * 2, base - f * 1.02f + f * 1.3f)
                    if (wi == 0) {
                        val wipe = eo(seg(t, 2.1f, .26f))
                        val x0 = -lw.full / 2 - 10
                        c.clipRect(x0, -f, x0 + (lw.full + 20) * wipe, f)
                    }
                    if (l.ch == "?") c.clipRect(-hw * 2, -hh * 2, hw * 2, base - f * .2f)
                    p.size(f)
                    p.say(l.ch, l.x, base + dy, if (l.ch == "?") P else cols[wi])
                    c.restore()
                }
                if (wi == 0) {
                    // the cursor rides the wipe, then folds away
                    val wipe = eo(seg(t, 2.1f, .26f))
                    val g = eo(seg(t, 2.36f, .12f))
                    if (t > 2.16f && g < 1) {
                        val cx = -lw.full / 2 - f * .13f + (lw.full + f * .2f) * wipe
                        p.poly(rect5(cx, -f * .44f * (1 - g), cx + f * .06f, f * .44f * (1 - g)), B)
                    }
                }
            }
        }

        // the ?'s dot (bar 2) → square → circle → plus (bar 3)
        val fw = wordF(p, hw, hh)
        p.size(fw)
        val nw = p.measure("next?")
        val qx = -nw / 2 + p.measure("next")
        val qw = p.measure("?")
        val dotX = qx + qw * .43f
        val dotY = fw * .35f - fw * .065f
        val dotR = fw * .07f
        if (t >= 3.5f && t < 6.1f) {
            val into = eo(seg(t, 3.5f + .04f + 4 * .025f, .28f))
            val k1 = eo(seg(t, 4f, .34f))
            val k2 = eio(seg(t, 4.5f, .34f))
            val k3 = eio(seg(t, 5f, .34f))
            val cx = lerp(dotX, 0f, k1)
            val cy = lerp(dotY, 0f, k1) + (1 - into) * fw * 1.3f
            val q = PI.toFloat() / 4
            val (pts, col) = when {
                t < 4.5f -> radial(circ(dotR), box(lerp(dotR, 150f, k1)), k1, k1 * q, cx, cy) to P
                t < 5f -> radial(box(150f), circ(190f), k2, q + k2 * q, cx, cy) to mix(P, B, k2)
                else -> radial(circ(190f), plus(195f, 65f), k3, 2 * q + k3 * 2 * q, cx, cy) to mix(B, W, k3)
            }
            if (t < 5.5f) p.poly(pts, col)
        }

        // ===== bar 3 → 4: the plus splits into four tiles that become the corner cards; the grid tilts into 3D =====
        val gx = 230f
        val gy = 290f
        val cw = 100f
        val ch = 130f
        if (t >= 5.5f && t < 8.1f) {
            val k = eio(seg(t, 5.5f, .36f))
            if (t < 6f) {
                // the centre square shrinks away; the arms (N E S W) slide to the corners and become cards
                p.poly(rect5(-65 * (1 - k), -65 * (1 - k), 65 * (1 - k), 65 * (1 - k)), W)
                for ((ax, ay, cc, r0) in listOf(floatArrayOf(0f, -130f, 0f, 0f), floatArrayOf(130f, 0f, 2f, 0f), floatArrayOf(0f, 130f, 2f, 2f), floatArrayOf(-130f, 0f, 0f, 2f))) {
                    val x = lerp(ax, (cc - 1) * gx, k)
                    val y = lerp(ay, (r0 - 1) * gy, k)
                    val hx = lerp(65f, cw, k)
                    val hy = lerp(65f, ch, k)
                    p.poly(rect5(x - hx, y - hy, x + hx, y + hy), mix(W, CARDC[r0.toInt()][cc.toInt()], k))
                }
            } else {
                val tilt = eio(seg(t, 6f, .4f)) * 1.1f
                val grow = eo(seg(t, 6f, .3f))
                val dolly = eio(seg(t, 7f, .45f)) * 700 + ei(seg(t, 7.5f, .5f)) * 1000
                val drop = eio(seg(t, 6f, .4f)) * 150 - eio(seg(t, 7f, .45f)) * 60
                val land = eio(seg(t, 7.5f, .5f))
                val tgt = floatArrayOf(px, py, px + pw, py, px + pw, py + ph, px, py + ph)
                val draw = ArrayList<Card>(9)
                for (r in 0 until 3) for (cc in 0 until 3) {
                    val corner = r != 1 && cc != 1
                    val centre = r == 1 && cc == 1
                    val s = if (corner) 1f else grow
                    val st = eio(seg(t, 6.5f + (2 - r) * .07f, .36f)) * 1.1f
                    val ox = (cc - 1) * gx
                    val oy = (r - 1) * gy
                    val w3 = { lx: Float, ly: Float ->
                        val dy = ly - ch
                        var v = floatArrayOf(lx * s + ox, (ch + dy * cos(st)) * s + oy, dy * sin(st) * s)
                        v = rx(v, -tilt)
                        proj(floatArrayOf(v[0], v[1] + drop, v[2]), dolly)
                    }
                    val pr = listOf(-cw to -ch, cw to -ch, cw to ch, -cw to ch).map { (a, b) -> w3(a, b) }
                    if (pr.any { it == null } || s <= 0) continue
                    val prs = pr.map { it!! }
                    draw += Card(centre, CARDC[r][cc], prs, w3, art?.covers?.getOrNull(r * 3 + cc), prs.sumOf { it[2].toDouble() }.toFloat() / 4, cos(tilt - st))
                }
                draw.sortByDescending { it.depth }
                for (d in draw) {
                    var q = FloatArray(8) { i -> d.pr[i / 2][i % 2] }
                    var al = 1f
                    if (d.centre) q = lp(q, tgt, land) else al = 1 - ei(seg(t, 7.6f, .3f))
                    if (al <= 0) continue
                    val shade = mix(K, d.col, .4f + .6f * max(0f, d.face))
                    p.poly(q, if (d.centre) mix(shade, D, land) else shade, al)
                    val im = d.im
                    if (im != null) {
                        // a real cover, shaded like the card it sits on; the centre one fades as the card becomes the page
                        val ca = al * (if (d.centre) 1 - land else 1f)
                        if (ca > 0) {
                            p.tex(im, q, ca)
                            p.poly(q, K, ca * (.6f - .6f * max(0f, d.face)))
                        }
                        continue
                    }
                    val bar = listOf(-70f to 70f, 30f to 70f, 30f to 90f, -70f to 90f).map { (a, b) -> d.w3(a, b) }
                    if (bar.none { it == null } && !(d.centre && land > 0)) {
                        p.poly(FloatArray(8) { i -> bar[i / 2]!![i % 2] }, if (d.col === W) K else W, al * .9f)
                    }
                }
            }
        }

        // ===== bar 5 (8-10): the card is a game page: hours, a week of play, progress, a list =====
        val pad = pw * .09f
        val cwid = pw - 2 * pad
        val bw = cwid / 9 * .62f
        val base = py + ph * .70f
        val top = py + pad
        val pitch = cwid / 9
        val barX = FloatArray(9) { i -> px + pad + pitch * (i + .5f) }
        val barH = FloatArray(9) { i -> HS[i] * ph * .3f }
        if (t >= 7.98f && t < 10.5f) {
            val out = eio(seg(t, 10f, .34f))
            val fall = ei(seg(t, 10f, .3f))
            // the panel sinks into the floor line under the bars
            if (out < 1) p.poly(rect5(px, lerp(py, base, out), px + pw, lerp(py + ph, base + 4, out)), D)
            val dx = -fall * (hw * 2 + pw)
            val real = art != null && art.title.isNotEmpty()
            val kc = eo(seg(t, 8f, .45f))
            val hrs = ((if (real && art!!.hours > 0) art.hours else 38.0) * kc).roundToInt()
            val fs = ph * .062f
            val fb = ph * .16f
            fun enter(k: Float) = (1 - k) * 200
            // the game to play next, cut to fit beside the ring, and its hours played
            p.size(fs)
            var lab = if (real) art!!.title else "This week"
            if (real) {
                val room = pw - 2 * pad - ph * .24f
                if (p.measure(lab) > room) {
                    while (lab.length > 1 && p.measure("$lab…") > room) lab = lab.dropLast(1)
                    lab = lab.trimEnd() + "…"
                }
            }
            p.say(lab, px + pad + dx, top + fs + enter(eo(seg(t, 8f, .25f))), W, .72f)
            p.size(fb)
            p.say("${hrs}h", px + pad + dx, top + fs * 1.4f + fb * .9f + enter(eo(seg(t, 8.03f, .3f))), W)
            // the progress ring
            val kr = eo(seg(t, 9f, .4f))
            val rad = ph * .1f
            val rcx = px + pw - pad - rad + dx
            val rcy = top + rad + fs * .3f
            if (t >= 9f) {
                val g = spr(t, 9f, 22f)
                val rg = rad * g
                p.line.strokeWidth = rad * .26f
                p.r.set(rcx - rg, rcy - rg, rcx + rg, rcy + rg)
                p.line.color = argb(K, .28f)
                c.drawArc(p.r, 0f, 360f, false, p.line)
                p.line.color = argb(W, 1f)
                c.drawArc(p.r, -90f, 360f * .72f * kr, false, p.line)
                p.size(rad * .5f * g)
                p.text.textAlign = Paint.Align.CENTER
                p.say("${(72 * kr).roundToInt()}%", rcx, rcy + rad * .18f * g, W)
                p.text.textAlign = Paint.Align.LEFT
            }
            // the list rows
            val wa = floatArrayOf(.52f, .4f, .6f)
            val wb = floatArrayOf(.3f, .36f, .24f)
            for (i in 0 until 3) {
                val k = eo(seg(t, 9.5f + i * .05f, .3f))
                if (k <= 0) continue
                val ry0 = base + ph * .06f + i * ph * .075f
                val rh = ph * .055f
                val ox = (1 - k) * (pw + hw) + dx
                p.rr(px + pad + ox, ry0, rh, rh, rh * .22f, if (i == 1) P else B)
                p.rr(px + pad + rh * 1.35f + ox, ry0 + rh * .14f, cwid * wa[i], rh * .3f, rh * .15f, W, .92f)
                p.rr(px + pad + rh * 1.35f + ox, ry0 + rh * .6f, cwid * wb[i], rh * .22f, rh * .11f, W, .45f)
            }
        }
        // the week's bars (bar 5), drawn flat until the field takes them over at 10
        if (t >= 8.5f && t < 10f) {
            for (i in 0 until 9) {
                val h = barH[i] * spr(t, 8.5f + i * .035f, 15f)
                p.poly(rect5(barX[i] - bw / 2, base - h, barX[i] + bw / 2, base), if (i == 3) P else W)
            }
        }

        // ===== bars 6-7 (10-14): the bars become a 9 x 9 field of columns; the camera climbs; the tops spell ▶ =====
        if (t >= 10f && t < 14.2f) {
            val fieldD = pitch * 9
            val phi = eio(seg(t, 10f, .4f)) * .42f + eio(seg(t, 11f, .5f)) * .3f + eio(seg(t, 12f, .5f)) * .25f + eio(seg(t, 12.5f, .7f)) * (PI.toFloat() / 2 - .97f)
            val psi = -eio(seg(t, 11f, .5f)) * .5f + eio(seg(t, 12f, .9f)) * .5f
            val sz = min(hw * 2 * .86f, hh * 2 * .86f) / (pitch * 9)
            val zoom = lerp(lerp(1f, min(1.2f, hw * 2 * .62f / (pitch * 9)), eio(seg(t, 10f, 1.6f))), sz, eio(seg(t, 12.5f, 1f)))
            // everything slams flat, the gaps close
            val flat = eio(seg(t, 13.5f, .33f))
            val pv0 = barX[4]
            val pv1 = base
            val pv2 = fieldD / 2
            val lift = eio(seg(t, 10f, .4f))
            fun inTri(cc: Int, r: Int): Boolean { val xm = 9 * (1 - abs(r + .5f - 4.5f) / 4.5f); return cc + .5f < xm }
            val cols = ArrayList<Col>(81)
            for (r in 0 until 9) for (cc in 0 until 9) {
                var col = if (r == 0) (if (cc == 3) P else W) else W
                var al = 1f
                val data = barH[(cc + r * 4) % 9] * (.55f + .45f * sin(r * 1.7f + cc * .9f).let { it * it })
                var h = if (r == 0) barH[cc] else data * eo(seg(t, 10.5f + r * .06f, .4f))
                // 11.5: a wave runs through the field on the beat
                h *= 1 + .35f * sin(cl((t - 11.5f - (r + cc) * .025f) / .45f) * PI.toFloat())
                // 12: they re-height into the ▶
                val kk = spr(t, 12f + (abs(r - 4) + (8 - cc)) * .022f, 14f)
                val tri = inTri(cc, r)
                h = lerp(h, if (tri) pitch * (1.8f + (cc / 8f) * 1.2f) else 0f, kk)
                col = mix(col, if (tri) (if (cc >= 7 - abs(r - 4) * 2 + 1) P else B) else mix(K, W, .16f), kk)
                if (r > 0 && t < 12f) col = if ((r * 3 + cc) % 5 == 0) W else if ((r + cc) % 3 == 0) D else B
                h = lerp(h, 0f, flat)
                if (!tri) al = 1 - eo(seg(t, 13.5f, .25f))
                val w = lerp(bw, pitch, flat * (if (tri) 1f else 0f)) * (1 - eo(seg(t, 14f + (8 - cc) * .008f, .14f)))
                cols += Col(barX[cc], r * pitch, w, h, col, al)
            }
            val back = 1 - eio(seg(t, 10f, .6f))
            val down = lerp(pv1, 70f, eio(seg(t, 10f, .6f))) - 70 * eio(seg(t, 12.3f, 1.1f))
            val deep = lerp(pv2, 0f, lift)
            fun tf(v: FloatArray): FloatArray {
                var q = floatArrayOf(v[0] - pv0, v[1] - pv1, v[2] - pv2)
                q = ry(q, psi)
                q = rx(q, phi)
                return floatArrayOf(q[0] * zoom + pv0 * back, q[1] * zoom + down, q[2] * zoom + deep)
            }
            val items = ArrayList<Block>(81)
            for (cd in cols) {
                if (cd.al <= .01f) continue
                val x0 = cd.x - cd.w / 2
                val x1 = cd.x + cd.w / 2
                val z0 = cd.z + (pitch - cd.w) / 2 - (pitch - bw) / 2
                val z1 = z0 + cd.w
                val y0 = base
                val y1 = base - max(cd.h, .5f)
                val v = listOf(
                    floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
                    floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1),
                ).map(::tf)
                val pp = v.map { proj(it) }
                if (pp.any { it == null }) continue
                val bc = FloatArray(3) { j -> v.sumOf { it[j].toDouble() }.toFloat() / 8 }
                val seen = FACES.filter { (f, _) ->
                    val fc = FloatArray(3) { j -> f.sumOf { v[it][j].toDouble() }.toFloat() / 4 }
                    (fc[0] - bc[0]) * -fc[0] + (fc[1] - bc[1]) * -fc[1] + (fc[2] - bc[2]) * (-F3 - fc[2]) > 0
                }
                items += Block(cd, pp.map { it!! }, seen, bc[2])
            }
            items.sortByDescending { it.dep }
            for (it in items) for ((f, sh) in it.faces) {
                p.poly(FloatArray(8) { i -> it.pts[f[i / 2]][i % 2] }, mix(K, it.col.col, sh), it.col.al)
            }
        }

        // ===== the end (14-15): the pixel ▶ becomes the title, then the title folds back into the pixel =====
        if (t >= 14f) {
            val s = min(hw * 2 * .86f, hh * 2 * .86f) / 400f
            p.size(100f)
            val ft = min(hh * .36f, hw * 2 * .84f / ((p.measure("Play next") + 110) / 100))
            p.size(ft)
            val tw = p.measure("Play next")
            val mark = ft * .78f
            val gap = ft * .32f
            val tot = mark + gap + tw
            val k = eio(seg(t, 14.1f, .3f))
            val back = eio(seg(t, 14.5f, .32f))
            val g0 = lerp(.6f, 1f, eo(seg(t, 14f, .16f)))
            val ms = lerp(s * g0, mark / 400, k)
            val mx = lerp(0f, -tot / 2 + mark * .5f, k)
            // the ▶ (in its three slices) → the pixel
            val thirds = arrayOf(rect5(-PIX, -PIX, PIX, -PIX / 3), rect5(-PIX, -PIX / 3, PIX, PIX / 3), rect5(-PIX, PIX / 3, PIX, PIX))
            TRI.forEachIndexed { i, sl ->
                val q = FloatArray(sl.size) { j -> if (j % 2 == 0) mx + (sl[j] - 30) * ms else sl[j] * ms }
                p.poly(lp(q, thirds[i], back), B)
            }
            // the letters come in from the right like bar 2, and leave into the mark
            val word = "Play next"
            val x0 = -tot / 2 + mark + gap
            for (i in word.indices) {
                if (word[i] == ' ') continue
                val lx = x0 + p.measure(word.substring(0, i))
                val kin = eo(seg(t, 14.14f + i * .022f, .26f))
                val kout = eio(seg(t, 14.5f + (word.length - i) * .012f, .26f))
                val x = lx + (1 - kin) * (hw + 300) - kout * (lx - (-PIX))
                val sc = 1 - kout
                if (sc <= .01f) continue
                c.save()
                c.translate(x, ft * .35f)
                c.scale(sc, sc)
                p.say(word[i].toString(), 0f, 0f, if (i < 4) W else B)
                c.restore()
            }
        }
        c.restore()
    }
}
