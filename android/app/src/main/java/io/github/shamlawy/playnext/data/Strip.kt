package io.github.shamlawy.playnext.data

import java.time.ZoneId

/**
 * The unfolded rail's live strip (`rlvItems`, index.html 24307, v251): up to nine of your games as tiles, then your
 * backlog's hours. In this order, a game never twice: what you're playing (how far, the finish forecast's days left),
 * the next three releases (countdown; hours on the last day), price drops of 5% or more on watched games, On deck's #1,
 * then the rest of Home's ranking ("#N") until there are nine. Worked out from [Home] (its ranking is `pnLastRanked`)
 * and the clock; pure, so the rail only draws.
 */
object Strip {
    /**
     * One tile: its game (null for the backlog tile, which opens the Games tab), its kind (`k-…`: play, rel, sale, next,
     * q, bl), [soon] for a release a week or less away, the ring's fill (0 = no ring), the bold value and the caption.
     */
    data class Tile(val g: Game?, val k: String, val soon: Boolean, val p: Double, val v: String, val s: String)

    /** Most tiles before the backlog's. */
    const val MAX = 9

    fun items(h: Home, now: Long, zone: ZoneId): List<Tile> {
        val q = h.q
        val rk = q.rk
        val lib = q.lib
        val out = ArrayList<Tile>()
        val used = HashSet<String>()
        fun add(t: Tile) {
            out += t
            t.g?.let { used += it.id }
        }
        val td = JsDate.todayLocal(now, zone)
        // what you're playing (`playingNow()`: every game whose saved state is "playing")
        for (g in lib.games.filter { it.rawState == "playing" }.take(3)) {
            val need = rk.hrsOf(g)
            val have = Story.logged(g)
            val e = Forecast.of(lib, rk, g, now, zone)
            val p = if (need > 0) minOf(1.0, have / need) else 0.0
            val v = if (need > 0) Rank.jsRound(p * 100).toLong().toString() + "%" else if (have != 0.0) Rank.jsRound(have).toLong().toString() + "h" else "Now"
            val s = if (e == null) "playing" else if (e.left <= 0) "almost done" else if (e.days <= 1) "done soon" else "${e.days}d left"
            add(Tile(g, "play", false, p, v, s))
        }
        // the next releases: not played or dropped, not competitive or TBA, a full date today or later, earliest first
        val day0 = JsDate.localMidnight(td, zone) ?: now
        lib.games.filter {
            !rk.isPlayed(it) && !rk.isDropped(it) && !it.comp && Backup.str(it["status"]) != "tba" && DATE.matches(it.date) && it.date >= td
        }.sortedBy { it.date }.take(3).forEach { g ->
            val at = JsDate.localMidnight(g.date, zone) ?: return@forEach
            val d = Rank.jsRound((at - day0) / JsDate.DAY.toDouble()).toInt()
            val hrs = maxOf(1.0, Math.ceil((at - now) / 3_600_000.0)).toLong()
            val v = if (d <= 0) "Today" else if (d == 1) "${hrs}h" else if (d < 100) "${d}d" else "${Rank.jsRound(d / 30.0).toLong()}mo"
            add(Tile(g, "rel", d <= 7, maxOf(.04, 1 - minOf(1.0, d / 60.0)), v, if (d <= 0) "out now" else "until out"))
        }
        // price drops on watched games (`pxWatched()`): the best store's discount, 5% or more, biggest first
        lib.games.filter { !rk.isPlayed(it) && !rk.isDropped(it) && !it.comp && Prices.STORES.any { s -> Prices.want(lib, it, s) } }
            .map { g ->
                var best = 0L
                for (s in Prices.STORES) {
                    val p = Prices.px(g, s) ?: continue
                    val e = Prices.eff(lib, p)
                    val base = p.base
                    if (!Prices.incl(lib, p) && e != null && base != null && base > e) best = maxOf(best, Rank.jsRound((1 - e / base) * 100).toLong())
                }
                g to best
            }.filter { it.second >= 5 }.sortedByDescending { it.second }.take(3)
            .forEach { (g, best) -> add(Tile(g, "sale", false, best / 100.0, "−$best%", "on sale")) }
        // what's up next
        rk.onDeck(1).firstOrNull()?.let { nx -> if (nx.id !in used) add(Tile(nx, "next", false, 1.0, "Next", "up next")) }
        // the rest of your queue as Home ranks it, until there are nine
        h.ranked.forEachIndexed { n, g -> if (out.size < MAX && g.id !in used) add(Tile(g, "q", false, 0.0, "#${n + 1}", "in queue")) }
        // your backlog's hours (a game-less tile)
        val bl = lib.games.filter { it.rawState == "backlog" && !rk.isPlayed(it) && !rk.isDropped(it) }
        if (bl.isNotEmpty()) {
            val blh = Rank.jsRound(bl.sumOf { rk.hrsOf(it).let { x -> if (x.isNaN()) 0.0 else x } }).toLong()
            val v = if (blh != 0L) (if (blh >= 1000) fixed1(blh / 1000.0) + "k" else "$blh") + "h" else "${bl.size}"
            out += Tile(null, "bl", false, 0.0, v, if (blh != 0L) "backlog" else "in backlog")
        }
        return out
    }

    private val DATE = Regex("^\\d{4}-\\d\\d-\\d\\d$")
}
