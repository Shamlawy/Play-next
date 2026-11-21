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

    /** The tiles you turned off in Customise › Home (`S.homeHide`). */
    private val hidden: Set<String> = (lib["homeHide"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet()

    /** Home's tile [k] is on (`on(k)`: not in [hidden]). */
    fun on(k: String): Boolean = k !in hidden

    // ------------------------------------------------------------ the middle tiles (piece 3, `dxHome` 11152-11214)

    /** Your queue's covers: [ranked]'s first seven, scattered round the orb (`SPOT`). */
    val queueCovers: List<Game> by lazy { ranked.take(Web.DX_SPOT.size) }

    /** What you're playing (`playingNow()` among [live]): the saved state is "playing". */
    private val playingNow: List<Game> by lazy { live.filter { it.rawState == "playing" } }

    /** Out and waiting: a backlog game, or one out in the last 14 days ([Game.isFresh]), not already playing. */
    private val ready: List<Game> by lazy { live.filter { it !in playingNow && (it.rawState == "backlog" || it.isFresh(now, zone)) } }

    /** Playing now's title: "Playing now", else "Out & waiting", else "Top of your list". */
    val playingTitle: String get() = if (playingNow.isNotEmpty()) "Playing now" else if (ready.isNotEmpty()) "Out & waiting" else "Top of your list"

    /** Its three cards: what you're playing, then what's out and waiting, then the rest of [ranked]. */
    val playing: List<Game> by lazy {
        val first = playingNow + ready
        (first + ranked.filter { it !in first }).take(3)
    }

    /** A game's hours to beat for these tiles (`num(hrsOf(g), 0, 0, 1000)`). */
    private fun total(g: Game): Double = rk.hrsOf(g).let { if (it.isNaN()) 0.0 else it.coerceIn(0.0, 1000.0) }

    /** Hours you've logged (`loggedOf`; the timer that would add to it comes in phase 5). */
    private fun done(g: Game): Double = Backup.num(g["logged"], 0.0, 0.0, 100000.0)

    /** One Playing now card's words: the New look's kicker and bar, the Classic look's small line, stars and progress. */
    class Card(
        /** New: "Playing · 40%", "Playing now", "Competitive", else the first genre, "Out now" or "Up next". */
        val kicker: String,
        /** New: how far through (0..1), when it's being played, has hours and isn't competitive; else null (no bar). */
        val bar: Float?,
        /** Classic: "12h left", the win rate, the diary count or the first genre. */
        val small: String,
        /** Classic: 0-5 stars of the hype. */
        val stars: Int,
        /** Classic: how far through (0..1) and its words ("40%", "—"). */
        val pct: Float,
        val pctText: String,
    )

    /** [g]'s card (`dzPlay` 24615 for the New look, `dxHome` 11161 for the Classic one). */
    fun card(g: Game): Card {
        val total = total(g)
        val done = done(g)
        val playing = g.rawState == "playing"
        val frac = if (total > 0) minOf(1.0, done / total) else 0.0
        val kicker = if (playing) {
            if (g.comp) "Competitive" else if (total > 0) "Playing · " + Rank.jsRound(frac * 100).toLong() + "%" else "Playing now"
        } else {
            Rank.platList(g["genres"]).firstOrNull() ?: if (g.isFresh(now, zone)) "Out now" else "Up next"
        }
        val pc = if (total > 0) minOf(100L, Rank.jsRound(done / total * 100).toLong()) else 0L
        val small = if (g.comp) {
            val dy = (g["diary"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            val sum = { k: String -> dy.sumOf { Backup.num(it[k], 0.0, -1e9, 1e9) } }
            val w = sum("w")
            val all = w + sum("l") + sum("d")
            if (all != 0.0) Rank.jsRound(w / all * 100).toLong().toString() + "% wins"
            else if (dy.isNotEmpty()) dy.size.toString() + " diary entr" + (if (dy.size == 1) "y" else "ies")
            else genreOf(g)
        } else if (total > 0) maxOf(0L, Rank.jsRound(total - done).toLong()).toString() + "h left" else genreOf(g)
        return Card(
            kicker = kicker,
            bar = if (playing && total > 0 && !g.comp) frac.toFloat() else null,
            small = small,
            stars = Rank.jsRound(Backup.num(g["hype"], 0.0, 0.0, 10.0) / 2).toInt(),
            pct = pc / 100f,
            pctText = if (total > 0) "$pc%" else "—",
        )
    }

    /** One slice of the hours donut: a game (or "Other") and its share of the queue's hours. */
    class Slice(val label: String, val hours: Double, val share: Double, val colour: Int)

    /** The donut (`dxHome` 11175): the three games holding the most hours, then the rest as "Other". */
    val donut: List<Slice> by lazy {
        val byH = live.map { it to total(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
        val segs = byH.take(3).mapIndexed { n, (g, h) -> Slice(g.title, h, if (hours > 0) h / hours else 0.0, n) }.toMutableList()
        val rest = byH.drop(3).sumOf { it.second }
        if (rest != 0.0) segs += Slice("Other", rest, if (hours > 0) rest / hours else 0.0, 3)
        segs
    }

    /** The donut's middle: "1,234h" (or "—h") and "≈ N evenings" (or "add hours to games"). */
    val donutHours: String get() = if (hours != 0.0) String.format(java.util.Locale.US, "%,d", Rank.jsRound(hours).toLong()) + "h" else "—h"
    val donutSmall: String get() = if (hours != 0.0) "≈ " + maxOf(1L, Rank.jsRound(hours / lib.evening).toLong()) + " evenings" else "add hours to games"

    /** "Queue hours", with " · Extras" or " · Completionist" when you don't play main story (`playStyle()`). */
    val donutHead: String get() {
        val s = Backup.str(lib["playStyle"])
        val st = Web.PLAY_STYLES.firstOrNull { it.first == s } ?: Web.PLAY_STYLES[0]
        return "Queue hours" + if (st.first == Web.PLAY_STYLES[0].first) "" else " · " + st.second
    }

    /** Next out's game: the next one still ahead, else the latest dated one (already out); null with nothing dated. */
    val ticket: Game? get() = fut.firstOrNull() ?: dated.lastOrNull()

    /** The ticket's game is still ahead (so it counts down). */
    val ticketAhead: Boolean get() = fut.isNotEmpty()

    /** "12 days to go · 13 Sept", or "It's out — go play it!" once out. */
    fun ticketLine(g: Game): String =
        if (g in fut) JsDate.days(g.date, now).removeSuffix(" away") + " to go · " + q.shortDate(g.date) else "It's out — go play it!"

    /** Worked out off Main, so the first draw only reads. */
    fun warm() { ranked; dated; fut; hours; nexiPick; top; next; queueCovers; playing; donut }

    companion object {
        /**
         * The countdown's four numbers to [date]'s local midnight from [now] (`cdTick`): days, hours, minutes, seconds,
         * all 0 once it's there (or for a date the phone can't read).
         */
        fun countdown(date: String, now: Long, zone: ZoneId): IntArray {
            val ms = ((JsDate.localMidnight(date, zone) ?: now) - now).coerceAtLeast(0L)
            return intArrayOf((ms / 86_400_000L).toInt(), (ms / 3_600_000L % 24).toInt(), (ms / 60_000L % 60).toInt(), (ms / 1000L % 60).toInt())
        }
    }
}
