package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonObject

/**
 * What your scores say about you, ported line by line from the web app so both apps draw the same conclusions
 * (android/parity/3-map/3-critic.md M3): `analysisReady` (index.html 3587), `hypeBias` (7947), `categoryPull` (8355),
 * `genrePattern` (8369), `expectedInfo` (3690), `relationship` (3739) and Home's `tonight` (3651). Home's taste cards
 * read it now; Played's player read (phase 5) and the Taste tab (phase 6) read the same. Pure: the clock is passed in.
 */
class Taste(private val q: Queue) {
    private val rk = q.rk
    private val lib = q.lib

    /** `playedRows()`: every played record whose game exists and is scored, with its game (replays count again). */
    val rows: List<Pair<Played, Game>> by lazy { rk.played.mapNotNull { p -> rk.find(p.id)?.takeIf { p.scored }?.let { p to it } } }

    /** `analysisReady()`: [have] games scored, [need] before anything is read from them (`S.analysisAt`, 3, 1..20). */
    class Ready(val ok: Boolean, val have: Int, val need: Int)

    val ready: Ready by lazy {
        val need = Backup.num(lib["analysisAt"], 3.0, 1.0, 20.0).toInt()
        Ready(rows.size >= need, rows.size, need)
    }

    /** `hypeBias()`: over [n] scored games with a hype, how far the verdict lands from it on average, and its spread. */
    class Bias(val n: Int, val mean: Double, val spread: Double, val hits: Int)

    /** Your hype as saved (`g.hype`, unclamped, as the web's `x.g.hype > 0` reads it). */
    private fun hype(g: Game): Double = q.hype(g)

    /** The scored rows with a hype: what every hype reading is drawn from. */
    private val hyped: List<Pair<Played, Game>> by lazy { rows.filter { hype(it.second) > 0 } }

    val bias: Bias by lazy {
        if (hyped.isEmpty()) return@lazy Bias(0, 0.0, 0.0, 0)
        val d = hyped.map { (p, g) -> p.real!! - hype(g) }
        val mean = d.sum() / d.size
        Bias(d.size, mean, Math.sqrt(d.sumOf { (it - mean) * (it - mean) } / d.size), d.count { Math.abs(it) <= 1 })
    }

    /** One of `categoryPull()`'s eight: how closely area [k] tracks your final score ([r], null under three pairs). */
    class Pull(val k: String, val label: String, val n: Int, val r: Double?)

    val pull: List<Pull> by lazy {
        val r = rows.filter { (it.second["mine"] as? JsonObject).orEmpty().isNotEmpty() }
        Web.RATINGS.map { (k, label) ->
            val pairs = r.mapNotNull { (p, g) ->
                Backup.num((g["mine"] as? JsonObject)?.get(k), 0.0, -Double.MAX_VALUE, Double.MAX_VALUE).takeIf { it > 0 }?.let { it to p.real!! }
            }
            if (pairs.size < 3) return@map Pull(k, label, pairs.size, null)
            val mx = pairs.sumOf { it.first } / pairs.size
            val my = pairs.sumOf { it.second } / pairs.size
            var top = 0.0
            var bx = 0.0
            var by = 0.0
            for ((x, y) in pairs) {
                top += (x - mx) * (y - my)
                bx += (x - mx) * (x - mx)
                by += (y - my) * (y - my)
            }
            Pull(k, label, pairs.size, if (bx != 0.0 && by != 0.0) top / Math.sqrt(bx * by) else null)
        }
    }

    /** What decides it on Home: the area with the biggest pull either way, or null. */
    val topPull: Pull? by lazy { pull.filter { it.r != null }.sortedByDescending { Math.abs(it.r!!) }.firstOrNull() }

    /** One genre of `genrePattern()`: across [n] games (two or more), the verdict's mean distance from your hype. */
    class Lean(val name: String, val n: Int, val mean: Double)

    /** `genrePattern()`: the genres you run cold on first, hot on last. */
    val genres: List<Lean> by lazy {
        val by = LinkedHashMap<String, MutableList<Double>>()
        for ((p, g) in hyped) for (gn in Rank.platList(g["genres"])) by.getOrPut(gn) { mutableListOf() } += p.real!! - hype(g)
        by.map { (name, d) -> Lean(name, d.size, d.sum() / d.size) }.filter { it.n >= 2 }.sortedBy { it.mean }
    }

    /** `expectedInfo(g)`: what your hype for an unplayed game is worth, moved by your record (and its genres'). */
    class Expected(val score: Double, val drift: Double, val n: Int, val genre: String?, val genreN: Int, val spread: Double)

    fun expected(g: Game): Expected? {
        val b = bias
        val h = Backup.num(g["hype"], 0.0, 0.0, 10.0)
        if (h == 0.0 || b.n == 0 || !ready.ok) return null
        val mine = Rank.platList(g["genres"]).mapNotNull { n -> genres.firstOrNull { it.name == n } }
        // a genre with its own record overrides the general drift for that share of it
        val drift = if (mine.isEmpty()) b.mean else (b.mean + mine.sumOf { it.mean } / mine.size) / 2
        return Expected(maxOf(1.0, minOf(10.0, h + drift)), drift, b.n, mine.firstOrNull()?.name, mine.firstOrNull()?.n ?: 0, b.spread)
    }

    /** One pick of `tonight(hrs)`: its game, its points, why, and its hours to beat as written. */
    class Pick(val g: Game, val pts: Double, val why: List<String>, val len: Double)

    /** `tonight(hrs)`: the best of what's out and waiting for the hours you have, and the next two. */
    class Tonight(val top: Pick, val rest: List<Pick>, val n: Int)

    fun tonight(hrs: Double, now: Long): Tonight? {
        val live = lib.games.filter { !rk.isPlayed(it) && !rk.isDropped(it) && !upcoming(it, now) }
        if (live.isEmpty()) return null
        val elo = { g: Game -> rk.elo[g.id].let { if (it == null || it == 0.0 || it.isNaN()) 1500.0 else it } }
        val hi = live.maxOf(elo)
        val lo = live.minOf(elo)
        val span = maxOf(1.0, hi - lo)
        val scored = live.map { g ->
            val len = Backup.num(g["hours"], 0.0, 0.0, 1000.0)
            val x = expected(g)
            var pts = (elo(g) - lo) / span * 40 + Backup.num(g["hype"], 0.0, 0.0, 10.0) * 2.5
            val why = ArrayList<String>()
            // already started beats anything not started
            if (g.rawState == "playing") { pts += 26; why += "you're already in it" }
            if (len != 0.0 && len <= hrs) { pts += 22; why += "short enough to finish tonight" }
            else if (len != 0.0 && len <= hrs * 3) { pts += 12; why += "about " + Math.ceil(len / hrs).toLong() + " sittings like tonight" }
            else if (len != 0.0 && len > hrs * 10) {
                // the further past a sensible run of evenings, the worse a call tonight
                val over = len / (hrs * 10)
                pts -= minOf(38.0, 14 + (over - 1) * 22)
                why += "a " + Queue.jsNum(len) + " hour commitment — not a tonight decision"
            }
            if (len == 0.0) why += "no length on it yet"
            if (x != null && x.drift >= 1) { pts += 6; why += "you usually end up rating these higher than you expect" }
            if (x != null && x.drift <= -1) { pts -= 6; why += "your record says this one may land under your hype" }
            Pick(g, pts, why, len)
        }.sortedByDescending { it.pts }
        return Tonight(scored[0], scored.drop(1).take(2), live.size)
    }

    companion object {
        /** `isUpcoming(g)`: saved as upcoming with no date, or a date (UTC midnight) still after [now]. */
        fun upcoming(g: Game, now: Long): Boolean =
            g.rawState == "upcoming" && (g.date.isEmpty() || (JsDate.utcMidnight(g.date)?.let { it > now } ?: false))

        /**
         * `relationship(g, rec)`: what a finished game turned out to be, read from your hype against the verdict and
         * what you did after ([dropped]: you walked away from it). One label and its line, or null when unscored.
         */
        fun relationship(g: Game, rec: Played, dropped: Boolean): Pair<String, String>? {
            val real = rec.real ?: return null
            if (!(real > 0)) return null
            val hype = Backup.num(g["hype"], 0.0, 0.0, 10.0)
            val d = if (hype != 0.0) real - hype else 0.0
            val runs = Backup.num(rec.o["runs"], 1.0, 1.0, 12.0)
            return when {
                dropped -> if (hype >= 8) "The one that got away" to "Everything about it should have been yours, and you still walked away."
                else "Left on the shelf" to "You gave it a go and it didn't hold you."
                runs >= 3 -> "The obsession" to "Three runs or more. This one isn't a game to you any more, it's a place you go back to."
                hype != 0.0 && d <= -3 -> "The heartbreak" to "You wanted this more than almost anything, and it didn't land. Those sting longest."
                hype != 0.0 && d >= 3 -> "The hidden gem" to "You barely expected anything from it. It repaid you " + fixed(d, 0) + " points over."
                real >= 9 && rec.again -> "The attachment" to "Top marks and you still want another run. You didn't just rate this one, you kept it."
                real >= 9 -> "The benchmark" to "One of your highest. This is the bar everything else gets measured against now."
                hype != 0.0 && Math.abs(d) <= 1 && real >= 7 -> "Called it" to "You read this one exactly right before you ever played it."
                real <= 4 -> "The write-off" to "It didn't work, and you said so plainly."
                else -> "The steady one" to "No drama in either direction. Fine, and finished."
            }
        }

        /** JavaScript's `v.toFixed(n)`: the exact value rounded half up, a minus kept on a negative (−0.04 → "-0.0"). */
        fun fixed(v: Double, n: Int): String {
            val s = java.math.BigDecimal(Math.abs(v)).setScale(n, java.math.RoundingMode.HALF_UP).toPlainString()
            return if (v < 0) "-$s" else s
        }
    }
}
