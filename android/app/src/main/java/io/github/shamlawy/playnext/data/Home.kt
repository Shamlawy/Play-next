package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import java.time.ZoneId

/**
 * Home's own numbers (`renderHome`, index.html 11250-11298, and its search `dxWire` 11222), worked out once per copy of
 * your games with the Queue's Rank, off Main with the header cards ([Shx.all]), so drawing Home only reads. Which list
 * where (android/parity/3-map/3-critic.md (b)): [ranked] feeds the big card, the next four, the dice and (later) the
 * queue covers, Playing now and Standings; Nexi's line takes On deck's #1 ([nexiPick]); "Your top N" the duel ranking.
 * Pure: the clock and the zone are passed in.
 */
class Home(val q: Queue, private val now: Long, private val zone: ZoneId) {
    private val rk = q.rk
    private val lib = q.lib

    /** Your games not played and not dropped (`live`). */
    val live: List<Game> = lib.games.filter { !rk.isPlayed(it) && !rk.isDropped(it) }

    /**
     * `ranked`: live, by On deck's score high to low (a competitive game has none: -1, so it sits at the end), then the
     * duel Elo (1500 when unset), then hype. A stable sort, as JavaScript's.
     */
    val ranked: List<Game> by lazy {
        val deck = rk.deck
        val elo = rk.elo
        val dv = { g: Game -> deck[g.id] ?: -1.0 }
        val ev = { g: Game -> elo[g.id].let { if (it == null || it == 0.0) 1500.0 else it } }
        val hy = { g: Game -> q.hype(g).let { if (it.isNaN()) 0.0 else it } }
        live.sortedWith(compareByDescending(dv).thenByDescending(ev).thenByDescending(hy))
    }

    /** The big card's game: the top of [ranked]. */
    val pick: Game? get() = ranked.firstOrNull()

    /** The next four under it. */
    val next: List<Game> by lazy { ranked.drop(1).take(4) }

    /** Duels with a winner (`settled`): the big card's "N duels". */
    val settled: Int = rk.duels.count { it.w.isNotEmpty() }

    /** Live games with a date, soonest first (`dated`; a date the phone can't read goes last). */
    val dated: List<Game> by lazy { live.filter { it.date.isNotEmpty() }.sortedBy { JsDate.utcMidnight(it.date) ?: Long.MAX_VALUE } }

    /** The dated ones still ahead (`fut`: their local midnight after now): Next out's count. */
    val fut: List<Game> by lazy { dated.filter { JsDate.localMidnight(it.date, zone)?.let { at -> at > now } == true } }

    /** The queue's hours to beat by how you play (`hours`; a competitive game counts 0). */
    val hours: Double by lazy { live.sumOf { rk.hrsOf(it).let { h -> if (h.isNaN()) 0.0 else h } } }

    /** How many you've played (`S.played.length`: every finish, replays too), the Played tab's count. */
    val playedCount: Int = rk.played.size

    /**
     * Pick for me (`[data-pickone]`, 11507): one of [ranked]'s first three, never [last] (the last pick), else the top.
     * [roll] is a number from 0 up to 1 (`Math.random()`).
     */
    fun pickOne(last: String?, roll: Double): Game? {
        val r2 = ranked.filter { it.id != last }
        return r2.getOrNull(Math.floor(roll * minOf(3, r2.size)).toInt()) ?: pick
    }

    /** Nexi's pick on Home (`onDeck(1)[0]`): the Queue's On deck #1, the same game the Play next widget shows. */
    val nexiPick: Game? by lazy { rk.onDeck(1).firstOrNull() }

    /** "Your top N" under his line: the duel ranking's first three (your favourite finished games). */
    val top: List<Game> by lazy { rk.ranked.take(3) }

    /** A game in [top]'s own score, for its tier badge (`tierBadge(t.sc[id])`). */
    fun score(g: Game): Double? = rk.table.sc[g.id]

    /**
     * The search (`dxWire`): every game, played too, whose title holds [text] (no case), where the text comes first
     * first, at most 6. Blank text finds nothing.
     */
    fun search(text: String): List<Game> {
        val s = text.trim().lowercase()
        if (s.isEmpty()) return emptyList()
        return lib.games.filter { it.title.lowercase().contains(s) }.sortedBy { it.title.lowercase().indexOf(s) }.take(6)
    }

    /** A search result's small words: "Played", how far its date is ("3 days away", "Out now"), or nothing. */
    fun found(g: Game): String = if (rk.isPlayed(g)) "Played" else if (g.date.isNotEmpty()) JsDate.days(g.date, now) else ""

    /** A row's time (`when`): out or the date, else "≈ Nh" to beat, else "No date yet". */
    fun whenOf(g: Game): String {
        if (g.date.isNotEmpty()) return JsDate.days(g.date, now).let { if (it.startsWith("Out")) it else q.shortDate(g.date) }
        val h = rk.hrsOf(g)
        return if (h != 0.0 && !h.isNaN()) "≈ " + Queue.jsNum(h.coerceIn(0.0, 1000.0)) + "h" else "No date yet"
    }

    /** A row's tag (`genre`): its first genre, else its state's word. */
    fun genreOf(g: Game): String = Rank.platList(g["genres"]).firstOrNull() ?: Web.STATES.firstOrNull { it.first == g.state }?.second ?: ""

    /** Worked out off Main, so the first draw only reads. */
    fun warm() { ranked; dated; fut; hours; nexiPick; top; next }
}
