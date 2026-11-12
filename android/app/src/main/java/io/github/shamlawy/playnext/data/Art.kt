package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Edit › Look's pictures from SteamGridDB, the stores and RAWG (index.html 4646-4745 `sgdbTop` / `sgdbAuto`, 21700-21790
 * `artStores` / `sgdbFind`; android/parity/2-map/2-edit.md §4), and the colour read from a game's art (`colourFrom` /
 * `palNear` 4876-4912, `colKey` / `colourAuto` 26837). Pure: [ArtNet] fetches, this decides what's picked and what's said.
 */
object Art {
    /** One picture: where it's from ([s]: sg, st, xb, ps, ns, rw), its job ([r]: cover, hero, art, shot), address, thumbnail, size, kind. */
    @androidx.compose.runtime.Immutable
    data class Pic(val s: String, val r: String, val u: String, val th: String, val w: Int, val h: Int, val k: String) {
        val area: Long get() = w.toLong() * h
        /** What a tap on it sets: a screenshot is a backdrop. */
        val role: String get() = if (r == "shot") "hero" else r
    }

    /** `ART_SRC`: every place, in the chips' order. */
    val SRC = listOf("sg" to "SteamGridDB", "st" to "Steam", "xb" to "Xbox", "ps" to "PlayStation", "ns" to "Nintendo", "rw" to "RAWG")
    fun srcName(s: String): String = SRC.firstOrNull { it.first == s }?.second ?: s

    /** Browse's rows, in order: the job, its heading, its shape. */
    val ROWS = listOf(
        Triple("cover", "Cover — the box art", "tall"),
        Triple("hero", "Backdrop — the wide scene behind the page", "wide"),
        Triple("art", "Logo — the cut-out that floats on the card", "logo"),
        Triple("shot", "Screenshots — more wide backdrops", "wide"),
    )

    const val NOTHING = "Nothing there under that name. Try a shorter one."
    const val NO_TITLE = "Give the game a title first."
    const val LOOKING = "Looking on SteamGridDB, Steam, Xbox, PlayStation, Nintendo and RAWG…"
    const val PICKING = "Picking…"
    const val OLD = " Steam, Xbox, PlayStation and Nintendo pictures arrive once the helper updates."
    const val PICKED = "Picked — tap Save to keep it."
    const val NOTE = "Auto-pick fills the logo, backdrop and cover with the sharpest of SteamGridDB's top picks, or the stores' own art when that's much sharper or SteamGridDB has none. Browse shows every picture from SteamGridDB, Steam, Xbox, PlayStation, Nintendo and RAWG with its size, sharpest first, so you can choose each one yourself. Tap Save to keep either."

    private fun int(e: JsonElement?): Int = Backup.num(e, 0.0, 0.0, 1e6).toInt()

    /** A SteamGridDB list (`data`: `{url, thumb, width, height}`) as pictures. */
    fun sg(list: JsonArray?, r: String, k: String): List<Pic> = list.orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val u = Backup.str(o["url"]).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Pic("sg", r, u, Backup.str(o["thumb"]).ifEmpty { u }, int(o["width"]), int(o["height"]), k)
    }

    /** The helper's `POST /art` answer (`{found, pics: [{s, r, u, th, w, h, k}]}`) as pictures. */
    fun stores(a: JsonObject?): List<Pic> = (a?.get("pics") as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val u = Backup.str(o["u"]).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Pic(Backup.str(o["s"]), Backup.str(o["r"]), u, Backup.str(o["th"]).ifEmpty { u }, int(o["w"]), int(o["h"]), Backup.str(o["k"]))
    }

    /** RAWG's screenshots (`rawgShots`: not deleted, a 420 px copy for the tile). */
    fun rawgShots(r: JsonObject?): List<Pic> = (r?.get("results") as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val u = Backup.str(o["image"]).takeIf { it.isNotEmpty() && !Backup.truthy(o["is_deleted"]) } ?: return@mapNotNull null
        Pic("rw", "shot", u, u.replace("/media/screenshots/", "/media/resize/420/-/screenshots/").replace("/media/games/", "/media/resize/420/-/games/"), int(o["width"]), int(o["height"]), "Screenshot")
    }

    /** The first store that named the game (`found.st|xb|ps|ns.name`). */
    fun foundName(a: JsonObject?): String? {
        val f = a?.get("found") as? JsonObject ?: return null
        return SRC.firstNotNullOfOrNull { (k) -> (f[k] as? JsonObject)?.get("name")?.let(Backup::str)?.takeIf { it.isNotEmpty() } }
    }

    /** Auto-pick's answer: the name matched and a picture for each job ("" for none). */
    data class Top(val name: String, val art: String, val hero: String, val cover: String, val heroA: Long = 0)

    /** `sgdbTop`: the sharpest of the community's top five, for each job. */
    fun sgTop(name: String, logos: List<Pic>, heroes: List<Pic>, grids: List<Pic>): Top {
        fun top(l: List<Pic>): Pic? = l.take(5).fold(null as Pic?) { a, x -> if (a == null || x.area > a.area) x else a }
        return Top(name, top(logos)?.u.orEmpty(), top(heroes)?.u.orEmpty(), top(grids)?.u.orEmpty(), top(heroes)?.area ?: 0)
    }

    private fun portrait(p: Pic) = p.h > p.w * 1.3

    /** `artBest`: the largest of a job (with a size), its kind matching [kinds] when given, its shape when asked. */
    private fun best(pics: List<Pic>, r: String, kinds: List<String>? = null, shape: ((Pic) -> Boolean)? = null): Pic? =
        pics.filter { it.r == r && it.w > 0 && (kinds == null || kinds.any { k -> it.k.contains(k) }) && (shape == null || shape(it)) }.maxByOrNull { it.area }

    /**
     * The stores over SteamGridDB (the `sgdbTop` extend, 21770): a store fills whatever SteamGridDB has none of (a cover
     * only when it's shaped like a box), and its clean backdrop wins when it has at least 1.5x the pixels.
     */
    fun withStores(t0: Top?, title: String, a: JsonObject?): Top? {
        val pics = stores(a)
        if (pics.isEmpty()) return t0
        var t = t0 ?: Top(foundName(a) ?: title, "", "", "")
        val c = best(pics, "cover", shape = ::portrait)
        val h = best(pics, "hero", Web.ART_HERO_OK)
        val l = best(pics, "art")
        if (t.cover.isEmpty() && c != null) t = t.copy(cover = c.u)
        if (h != null && (t.hero.isEmpty() || h.area >= 1.5 * t.heroA)) t = t.copy(hero = h.u)
        if (t.art.isEmpty() && l != null) t = t.copy(art = l.u)
        return t
    }

    /** What Auto-pick says once it has its answer, and the jobs it filled ("art", "hero", "cover"). */
    fun autoSay(t: Top?): Pair<String, List<String>> {
        if (t == null) return NOTHING to emptyList()
        val got = listOf("art" to t.art, "hero" to t.hero, "cover" to t.cover).filter { it.second.isNotEmpty() }.map { it.first }
        return (if (got.isNotEmpty()) "Matched “${t.name}” and filled ${got.size} of 3. Tap Save to keep them." else "Matched “${t.name}” but it has no artwork yet.") to got
    }

    /** `sgdbPut(role, u)` on the draft: the picture and its link box (a typed link wins on Save), a backdrop back on. */
    fun put(role: String, u: String): Map<String, JsonElement?> = when (role) {
        "art" -> mapOf("art" to JsonPrimitive(u), "artCleared" to null, "in:art" to JsonPrimitive(u))
        "hero" -> mapOf("hero" to JsonPrimitive(u), "bgOff" to JsonPrimitive(false), "in:back" to JsonPrimitive(u))
        else -> mapOf("img" to JsonPrimitive(u), "in:img" to JsonPrimitive(u))
    }

    /** Auto-pick's pictures on the draft (`sgdbPut` for each job it filled). */
    fun putTop(t: Top): Map<String, JsonElement?> {
        val m = LinkedHashMap<String, JsonElement?>()
        if (t.art.isNotEmpty()) m += put("art", t.art)
        if (t.hero.isNotEmpty()) m += put("hero", t.hero)
        if (t.cover.isNotEmpty()) m += put("cover", t.cover)
        return m
    }

    /** Browse's rows (`sgdbFind`): each job sharpest first (covers shaped like a box first), at most 40, empty rows left out. */
    fun rows(pics: List<Pic>): List<Pair<Triple<String, String, String>, List<Pic>>> = ROWS.mapNotNull { row ->
        val r = row.first
        fun fit(p: Pic) = if (r != "cover" || p.w == 0 || portrait(p)) 1 else 0
        // a stable sort: equal sizes keep each place's own order
        val l = pics.filter { it.r == r }.sortedWith(compareByDescending<Pic> { fit(it) }.thenByDescending { it.area }).take(40)
        if (l.isEmpty()) null else row to l
    }

    /** The places that gave pictures, in [SRC]'s order, with how many each. */
    fun counts(pics: List<Pic>): List<Triple<String, String, Int>> = SRC.mapNotNull { (k, n) -> pics.count { it.s == k }.takeIf { it > 0 }?.let { Triple(k, n, it) } }

    /** Browse's line over the rows. */
    fun browseSay(pics: List<Pic>, sgErr: String?, old: Boolean): String {
        val tail = if (old) OLD else ""
        if (pics.isEmpty()) return (sgErr ?: NOTHING) + tail
        val names = counts(pics).map { it.second }
        val list = if (names.size > 1) names.dropLast(1).joinToString(", ") + " and " + names.last() else names.first()
        return "${pics.size} pictures from $list. Sharpest first, each with its size and where it's from. Tap one to use it.$tail"
    }

    /** A picture's size tag (`picTier`): 4K, HD, Low or none. */
    fun tier(w: Int, h: Int): String {
        val m = maxOf(w, h)
        return if (m == 0) "" else if (m >= 3000) "4K" else if (m >= 1500) "HD" else if (m < 700) "Low" else ""
    }

    // ---- the store regions the helper asks for (`pxCC` / `pxPsCC`, index.html 19425, 19490) ----

    private val CC = Regex("^[a-z]{2}$")

    /** Your shop region: the one picked in Settings, else your time zone's, else your language's, else "us". */
    fun cc(lib: Library, tz: String, country: String): String {
        Backup.str(lib["pxCC"]).takeIf { CC.matches(it) }?.let { return it }
        Web.PX_TZ[tz]?.takeIf { it.isNotEmpty() }?.let { return it }
        return country.lowercase().takeIf { CC.matches(it) } ?: "us"
    }

    /** The PlayStation Store's: yours when it has one, the UAE's across the Arab world, else the US one. */
    fun psCC(lib: Library, cc: String): String {
        Backup.str(lib["pxPsCC"]).takeIf { CC.matches(it) && it !in Web.PS_NONE }?.let { return it }
        return if (cc in Web.PS_NONE) (if (cc in Web.PS_ARAB) "ae" else "us") else cc
    }

    /** `normT`: a title as letters and digits, for "is the draft still about this game". */
    fun normT(t: String): String = t.lowercase().replace(Regex("[^a-z0-9]+"), "")

    /** `artStId`: the draft's Steam id, only while its title is still the one searched. */
    fun steamId(d: Edit.Draft, title: String): Long {
        val dt = d.str("title")
        if (dt.isNotEmpty() && normT(dt) != normT(title)) return 0
        return listOf("stId", "pxSt", "steamId").map { Backup.num(d[it], 0.0, 0.0, 1e12).toLong() }.firstOrNull { it > 0 } ?: 0
    }

    // ---- the colour from the art (`colourFrom`, `palNear`; v294) ----

    /** The swatch for grey or dark art (no real colour in it). */
    const val STEEL = "#9AA7BF"

    /**
     * `colourFrom(canvas)`: every vivid pixel of [px] (ARGB, [w] x [h]) votes for its hue (more saturated and nearer the
     * middle = a bigger vote), neighbouring hues pool, and the winner's average is matched to the nearest swatch.
     */
    fun colourFrom(px: IntArray, w: Int, h: Int): String? {
        if (w <= 0 || h <= 0) return null
        val step = maxOf(1, Math.floor(Math.sqrt(w * h / 6000.0)).toInt())
        val n0 = 36
        val wt = DoubleArray(n0)
        val sr = DoubleArray(n0)
        val sg = DoubleArray(n0)
        val sb = DoubleArray(n0)
        var n = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c0 = px[y * w + x]
                if ((c0 ushr 24) >= 128) {
                    val r = (c0 shr 16) and 0xFF
                    val g = (c0 shr 8) and 0xFF
                    val b = c0 and 0xFF
                    val mx = maxOf(r, g, b)
                    val c = mx - minOf(r, g, b)
                    n++
                    if (mx >= 50 && c >= 40) {
                        val hu = if (mx == r) ((g - b).toDouble() / c + 6) % 6 else if (mx == g) (b - r).toDouble() / c + 2 else (r - g).toDouble() / c + 4
                        val k = Math.floor(hu / 6 * n0).toInt() % n0
                        val dx = x.toDouble() / w - .5
                        val dy = y.toDouble() / h - .5
                        val q = (c.toDouble() / mx) * (c.toDouble() / mx) * (mx / 255.0) * (1.3 - (dx * dx + dy * dy) * 1.2)
                        wt[k] += q; sr[k] += r * q; sg[k] += g * q; sb[k] += b * q
                    }
                }
                x += step
            }
            y += step
        }
        if (n == 0) return null
        fun at(k: Int) = (k + n0) % n0
        var best = -1
        var bs = 0.0
        for (k in 0 until n0) {
            val s = wt[k] + .6 * (wt[at(k - 1)] + wt[at(k + 1)]) + .25 * (wt[at(k - 2)] + wt[at(k + 2)])
            if (s > bs) { bs = s; best = k }
        }
        if (best < 0 || bs / n < .02) return STEEL
        var rr = 0.0
        var gg = 0.0
        var bb = 0.0
        var ww = 0.0
        for (j in -1..1) { val k = at(best + j); rr += sr[k]; gg += sg[k]; bb += sb[k]; ww += wt[k] }
        return Edit.hex(palNear(rr / ww, gg / ww, bb / ww))
    }

    /** `palNear`: the swatch nearest an average colour, mostly by hue, then how vivid (lifted a quarter first). */
    fun palNear(r: Double, g: Double, b: Double): Long {
        val (l0, a0, b0) = okLab(r, g, b)
        val aa = a0 * 1.25
        val bb = b0 * 1.25
        val cc = Math.hypot(aa, bb)
        var best = Web.PALETTE[0]
        var bd = 1e9
        for (c in Web.PALETTE) {
            val (l, a, b2) = Edit.okLab(c)
            val dC = Math.hypot(a, b2) - cc
            val dH = maxOf(0.0, (a - aa) * (a - aa) + (b2 - bb) * (b2 - bb) - dC * dC)
            val d = .08 * (l - l0) * (l - l0) + .5 * dC * dC + 2 * dH
            if (d < bd) { bd = d; best = c }
        }
        return best
    }

    /** `okLab` of an average (not whole) colour. */
    private fun okLab(r0: Double, g0: Double, b0: Double): Triple<Double, Double, Double> {
        fun f(v0: Double): Double = (v0 / 255.0).let { if (it <= .04045) it / 12.92 else Math.pow((it + .055) / 1.055, 2.4) }
        val r = f(r0)
        val g = f(g0)
        val b = f(b0)
        val l = Math.cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b)
        val m = Math.cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b)
        val s = Math.cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b)
        return Triple(.2104542553 * l + .793617785 * m - .0040720468 * s, 1.9779984951 * l - 2.428592205 * m + .4505937099 * s, .0259040371 * l + .7827717662 * m - .808675766 * s)
    }

    /** `colKey(src)`: which picture a colour was read from (its length and its two ends). */
    fun colKey(s: String): String = if (s.isEmpty()) "" else "${s.length}:${s.take(60)}${s.takeLast(40)}"

    private val HEX = Regex("^#[0-9a-fA-F]{6}$")

    /** `colourAuto(g)`: the colour still comes from the art (no hand pick, and read before or never really set). */
    fun colourAuto(o: Map<String, JsonElement>): Boolean {
        if (Backup.truthy(o["colourHand"])) return false
        val c = Backup.str(o["colour"])
        return Backup.str(o["colourOf"]).isNotEmpty() || !HEX.matches(c) || c.uppercase() == Edit.hex(Web.PALETTE[0])
    }

    /** The ✨ swatch: the colour back to the art's, from the cover the form has now (nothing yet: read below). */
    fun autoSrc(g: Game, d: Edit.Draft): String {
        if (Backup.truthy(d["imgCleared"])) return ""
        val typed = d.typed("img", "").trim()
        return typed.ifEmpty { d.str("img").ifEmpty { Backup.str(g["img"]) } }
    }

    /** What the ✨ swatch writes once the colour is read ([c] null: the art couldn't be read, the first swatch). */
    fun autoColour(src: String, c: String?): Map<String, JsonElement?> = mapOf(
        "colourHand" to JsonPrimitive(false),
        "colour" to JsonPrimitive(c ?: Edit.hex(Web.PALETTE[0])),
        "colourOf" to JsonPrimitive(if (c != null) colKey(src) else ""),
    )
}
