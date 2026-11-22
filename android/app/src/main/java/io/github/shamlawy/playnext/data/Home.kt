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

    // ------------------------------------------------------------ More for you (piece 4, `renderHome` 11309-11469)

    /**
     * One card in More for you's stacks. [id] is how the stack's order and hidden list name it (`pnCardId`: its `k-`
     * class, else its title), [name] the title it shows and the stack editor lists, [bg] the game whose picture it sits
     * on (`.dxbg`, 11476), [go] the "which update" key for a card whose tap leads to a screen still to come.
     */
    sealed class More(val id: String, val name: String, val bg: Game?, val go: String?) {
        /** A number or a word over a line (`.big`, `em` after it), with a meter (0..1) or a warning tint. */
        class Big(
            id: String, name: String, bg: Game?, go: String?,
            val big: String, val em: String, val word: Boolean, val sub: String,
            val meter: Float? = null, val warn: Boolean = false,
            /** The game a tap opens (`data-home-open`: the swing card, the hype trap), else null. */
            val open: Game? = null,
        ) : More(id, name, bg, go)

        /**
         * Tonight (`k-tonight`): for [hrs] hours ([chips], the one you're on lit), the best of what's out ([top]), why
         * ([why]), and the next two ([rest], "" when none). It sits on [top]'s picture under its own shade (`.imgcard`).
         */
        class Tonight(val hrs: Double, val chips: List<Int>, val top: Game, val why: String, val rest: String) :
            More("k-tonight", "With " + Queue.jsNum(hrs) + "h tonight", top, null)

        /** Furthest from your hype (`k-far`): your best surprise and your biggest letdown, each a row that opens it. */
        class Far(bg: Game?, val rows: List<Offer>) : More("k-far", "Furthest from your hype", bg, null)

        /** Play sessions: this week's count, a bar per day (0..1 of the busiest) with its weekday letter, and a line. */
        class Week(bg: Game?, val n: Int, val bars: List<Float>, val days: List<String>, val sub: String) : More("k-sess", "Play sessions", bg, "timer")

        /** Standings: the top of [ranked] with each one's Elo as a bar (0..1) and its colour when tinted. */
        class Stand(bg: Game?, val rows: List<Pair<Game, Float>>, val tint: Boolean) : More("k-stand", "Standings", bg, null)

        /** Where your hours go: a note, a pill bar per genre, and the biggest share's line. */
        class Hours(bg: Game?, val note: String, val bars: List<Bar>, val foot: String) : More("k-hours", "Where your hours go", bg, null)

        /** Where the year bunches up: how many dated games fall in each month, the busiest lit. */
        class Months(bg: Game?, val counts: List<Int>, val peak: Int) : More("k-year", "Where the year bunches up", bg, "month")
    }

    /** `offerRow`: a game, your hype for it ([was]) crossed out, the score it got ([now]) and a line ([cap]). */
    class Offer(val g: Game, val was: String, val now: String, val cap: String)

    /** What your scores say about you (`hypeBias`, `relationship`, `tonight`…), shared with the screens to come. */
    val taste = Taste(q)

    /** One of [More.Hours]' pill bars: [h] 0..1 of the bar's column, [kind] hot (your top three), cool, or other. */
    class Bar(val label: String, val value: String, val h: Float, val kind: Int) {
        companion object { const val HOT = 0; const val COOL = 1; const val OTHER = 2 }
    }

    /** The day in your zone. */
    private val today: java.time.LocalDate get() = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /** The Elo each standing reads (`elo[x.id] || 1500`). */
    private fun eloOf(g: Game): Double = rk.elo[g.id].let { if (it == null || it == 0.0 || it.isNaN()) 1500.0 else it }

    /** Live games by the month (0-11) their date falls in, read as JavaScript's `new Date(date).getMonth()`. */
    private val months: IntArray by lazy {
        val m = IntArray(12)
        for (g in live) JsDate.utcMidnight(g.date)?.let { m[java.time.Instant.ofEpochMilli(it).atZone(zone).monthValue - 1]++ }
        m
    }

    private fun monthOf(g: Game): Int? = JsDate.utcMidnight(g.date)?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).monthValue - 1 }

    /** `genreSplit(games, key)`: hours (or games) by genre, a game in three genres lending each a third; top five + Other. */
    private fun genreSplit(games: List<Game>, hours: Boolean): List<Triple<String, Double, Boolean>> {
        val by = LinkedHashMap<String, Double>()
        for (g in games) {
            val v = if (hours) total(g) else 1.0
            if (v == 0.0) continue
            val ls = Rank.platList(g["genres"])
            if (ls.isEmpty()) { by["Unsorted"] = (by["Unsorted"] ?: 0.0) + v; continue }
            for (n in ls) by[n] = (by[n] ?: 0.0) + v / ls.size
        }
        val all = by.entries.sortedByDescending { it.value }
        val top = all.take(5).map { Triple(it.key, it.value, false) }
        val rest = all.drop(5).sumOf { it.value }
        return if (rest > 0) top + Triple("Other", rest, true) else top
    }

    /** The card for Where your hours go (`genreChart("home")` 8446), or null with fewer than two bars. */
    private fun hoursCard(): More.Hours? {
        val byH = genreSplit(live, true)
        val useH = byH.size >= 2 && hours > 0
        val bars = if (useH) byH else genreSplit(lib.games, false)
        if (bars.size < 2) return null
        val ev = lib.evening
        val other = bars.firstOrNull { it.third }
        val otherN = if (other != null) genreCount(if (useH) live else lib.games, useH) - 5 else 0
        val fmt = { v: Double -> (if (v >= 10) Rank.jsRound(v).toLong().toString() else fixed1(v)) + if (useH) "h" else "" }
        val max = maxOf(1.0, bars.maxOf { it.second })
        val hot = bars.filter { !it.third }.sortedByDescending { it.second }.take(3).map { it.first }
        val top = bars.filter { !it.third }.maxByOrNull { it.second }
        val sum = bars.sumOf { it.second }
        val note = (if (useH) Queue.jsNum(hours) + "h waiting · about " + maxOf(1L, Rank.jsRound(hours / ev).toLong()) + " evenings at " + Queue.jsNum(ev) + "h"
            else lib.games.size.toString() + " games, split across the genres on each one") +
            if (other != null) " · Other = $otherN smaller genre" + (if (otherN == 1) "" else "s") else ""
        // `want["Where your hours go"]`: the live game with the most hours written on it
        val mostH = live.sortedByDescending { Backup.num(it["hours"], 0.0, 0.0, 1000.0) }.firstOrNull()
        return More.Hours(
            mostH ?: pick, note,
            bars.map { (l, v, o) ->
                Bar(l, fmt(v), (maxOf(9L, Rank.jsRound(v / max * 76).toLong()) / 100f), if (o) Bar.OTHER else if (l in hot) Bar.HOT else Bar.COOL)
            },
            if (top != null) top.first + " takes the biggest share of what you have queued — " + Rank.jsRound(top.second / sum * 100).toLong() + "% of it." else "",
        )
    }

    /** How many genres [genreSplit] found before it cut to five (`all.length`). */
    private fun genreCount(games: List<Game>, hours: Boolean): Int {
        val names = LinkedHashSet<String>()
        for (g in games) {
            if ((if (hours) total(g) else 1.0) == 0.0) continue
            val ls = Rank.platList(g["genres"])
            if (ls.isEmpty()) names += "Unsorted" else names += ls
        }
        return names.size
    }

    /** The cards before your order and hidden list: the upright stack (`sB`) and the sideways one (`sC`). */
    private val stacks: Pair<List<More>, List<More>> by lazy {
        val b = ArrayList<More>()
        val c = ArrayList<More>()
        val runner = ranked.getOrNull(1)
        val t = today
        val timerOn = Backup.truthy(lib["timerOn"])
        if (on("wrapped")) {
            val y = t.year
            val wd = Year.wr(lib, y, null, zone)
            val late = t.monthValue == 12
            b += More.Big(
                "k-wrap", "$y " + (if (late) "wrapped" else "so far"), wd.top?.first ?: wd.best ?: wd.fin.firstOrNull() ?: pick, "wrapped",
                if (late) "Your year in games" else "Your year so far", "", true,
                "${wd.fin.size} finished" + (if (wd.thin) "" else " · " + Year.fmtH(wd.hours) + " played") + " · ${wd.duels} duels — tap to watch",
            )
        }
        if (on("monthly")) {
            // the first week of a month looks back at the one that just ended; after that, this month so far
            val back = t.dayOfMonth <= 7
            val dt = if (back) t.minusMonths(1) else t
            val md = Year.wr(lib, dt.year, dt.monthValue - 1, zone)
            val name = Year.MONTHS[dt.monthValue - 1] + if (back) " recap" else " so far"
            val hero = md.top?.first ?: md.best ?: md.fin.firstOrNull() ?: pick
            b += if (md.thin) More.Big("k-month", name, hero, "wrapped", "${md.fin.size}", " finished", false, "${md.duels} duels · ${md.added} added — tap to watch")
            else More.Big(
                "k-month", name, hero, "wrapped", Year.fmtH(md.hours), " played", false,
                "${md.ses.size} sessions · ${md.fin.size} finished" + (md.top?.let { " · most: " + it.first.title } ?: "") + " — tap to watch",
            )
        }
        if (on("sessions") && timerOn) {
            val mid = t.atStartOfDay(zone).toInstant().toEpochMilli()
            val from = mid - 6 * JsDate.DAY
            val wk = Year.sessions(lib).filter { it.s >= from }
            val per = DoubleArray(7)
            for (x in wk) {
                val k = Math.floorDiv(java.time.Instant.ofEpochMilli(x.s).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() - from, JsDate.DAY).toInt()
                if (k in 0..6) per[k] += x.h
            }
            val top = maxOf(.5, per.max())
            val st = Year.stats(wk)
            val byG = LinkedHashMap<String, Double>()
            for (x in wk) byG[x.g] = (byG[x.g] ?: 0.0) + x.h
            val gid = byG.entries.sortedByDescending { it.value }.firstOrNull()?.key
            b += More.Week(
                rk.find(gid) ?: pick, st.n, per.map { (it / top).toFloat() },
                (0..6).map { java.time.Instant.ofEpochMilli(from + it * JsDate.DAY).atZone(zone).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.UK) },
                if (st.n > 0) Year.fmtH(st.tot) + " played · longest " + Year.fmtH(st.best) else "No sessions in the last 7 days — start the timer and they land here",
            )
        }
        val tr = taste.ready
        val b0 = taste.bias
        // the scored games with a hype, each with how far the verdict landed from it (`rows` in renderHome)
        val off = taste.rows.filter { q.hype(it.second) > 0 }.map { (p, g) -> Triple(g, p.real!! - q.hype(g), p.real) }
        val best = off.sortedByDescending { it.second }.firstOrNull()
        val worst = off.sortedBy { it.second }.firstOrNull()
        val topReal = rk.played.filter { it.scored }.sortedByDescending { it.real!! }.firstOrNull()?.let { rk.find(it.id) }
        if (on("read")) {
            // `want["Your read"]`: the game furthest from its hype, else your top-scored one
            val bg = off.sortedByDescending { Math.abs(it.second) }.firstOrNull()?.first ?: topReal ?: pick
            b += if (!tr.ok) More.Big("Your read", "Your read", bg, "played", "${tr.have}", "/${tr.need}", false, "games scored. This opens at ${tr.need}.")
            else if (b0.n > 0) More.Big(
                "Your read", "Your read", bg, "taste",
                (if (b0.mean > 0) "+" else if (b0.mean < 0) "−" else "") + Taste.fixed(Math.abs(b0.mean), 1), "", false,
                if (Math.abs(b0.mean) < .5) "You call it about right." else if (b0.mean < 0) "You run hot — games land below your hype." else "You undersell them.",
            )
            else More.Big("Your read", "Your read", bg, "played", "", "", false, "Score a finished game to start this.")
        }
        if (on("cat") && tr.ok) {
            val top = taste.topPull
            b += if (top != null) More.Big(
                "What decides it", "What decides it", topReal ?: pick, "taste", top.label, "", true, "tracks your final score closest",
                meter = (Rank.jsRound(Math.abs(top.r!!) * 100) / 100).toFloat(),
            )
            else More.Big("What decides it", "What decides it", topReal ?: pick, "played", "", "", false, "Rate the categories on three games and this works itself out.")
        }
        if (on("duels")) {
            val last = rk.duels.lastOrNull { it.w.isNotEmpty() && it.w != "draw" }
            b += More.Big(
                "Duels", "Duels", rk.find(last?.w) ?: pick, "duels", "$settled", "", false,
                if (settled > 0) "settled · ${live.size} still in the running" else "none yet — go settle a few",
            )
        }
        if (on("coverage") && live.size > 1) {
            val pairs = live.size * (live.size - 1) / 2
            val judged = rk.duels.filter { it.w.isNotEmpty() }.map { listOf(it.a ?: "", it.b ?: "").sorted().joinToString("|") }.toSet().size
            val covered = minOf(100L, Rank.jsRound(judged.toDouble() / pairs * 100).toLong())
            b += More.Big(
                "Settle a duel", "Settle a duel", runner ?: pick, "duels", "$covered", "% judged", true,
                "$judged of $pairs pairs · tap to rank another", meter = covered / 100f,
            )
        }
        val unrated = rk.played.filter { !it.scored }.mapNotNull { rk.find(it.id) }
        if (on("unrated") && unrated.isNotEmpty()) {
            b += More.Big(
                "Needs a verdict", "Needs a verdict", unrated[0], "played", "${unrated.size}", "", false,
                unrated.take(2).joinToString(", ") { it.title } + (if (unrated.size > 2) " +${unrated.size - 2} more" else "") + " — unscored, so they teach me nothing",
                warn = true,
            )
        }
        if (on("swing")) {
            // the last game you scored, and what it turned out to be (`relationship`)
            val last = rk.played.lastOrNull { it.scored }
            val lg = rk.find(last?.id)
            val rel = if (lg != null) Taste.relationship(lg, last!!, rk.isDropped(lg)) else null
            if (rel != null) b += More.Big("k-swing", lg!!.title, lg, null, rel.first, "", true, rel.second, open = lg)
        }
        val hrs = Backup.num(lib["tonightH"], lib.evening, 1.0, 12.0)
        val ton = if (on("tonight")) taste.tonight(hrs, now) else null
        // with nothing out, Tonight is a plain card (no `k-` class), so it sits in the upright stack
        if (on("tonight") && ton == null) {
            b += More.Big("Tonight", "Tonight", pick, null, "", "", false, "Nothing in the queue is out yet. This wakes up when a game you own is waiting.")
        }
        if (on("trap")) {
            val cand = live.mapNotNull { g -> taste.expected(g)?.let { g to it } }
                .filter { (g, x) -> q.hype(g) >= 7 && Math.abs(x.drift) >= 1 }
                .sortedByDescending { Math.abs(it.second.drift) }.firstOrNull()
            if (cand != null) {
                val (g, x) = cand
                val down = x.drift < 0
                b += More.Big(
                    "k-trap", if (down) "Hype trap" else "You're underselling it", g, null,
                    Queue.jsNum(q.hype(g)), " → " + Taste.fixed(x.score, 1), false,
                    g.title + " — " + (
                        if (down) "on ${x.n} finished games you tend to land " + Taste.fixed(Math.abs(x.drift), 1) + " under your hype"
                        else "you usually end up " + Taste.fixed(x.drift, 1) + " above what you expected"
                        ) + (if (x.genre != null && x.genreN >= 2) ", and more so with " + x.genre else "") + ". Give or take " + Taste.fixed(x.spread, 1) + ".",
                    warn = down, open = g,
                )
            }
        }
        val gen = taste.genres
        val up = gen.lastOrNull()
        if (on("genre") && tr.ok && up != null) {
            val dn = if (gen.size > 1) gen[0] else null
            b += More.Big(
                "k-lean", "Genre lean", lib.games.firstOrNull { up.name in Rank.platList(it["genres"]) } ?: pick, null, up.name, "", true,
                "beats your hype by " + (if (up.mean > 0) "+" else "") + Taste.fixed(up.mean, 1) + " across ${up.n} game" + (if (up.n == 1) "" else "s") +
                    (if (dn != null) " · ${dn.name} lands " + Taste.fixed(dn.mean, 1) else ""),
            )
        }
        hoursCard()?.let { c += it }
        if (on("stand") && ranked.size > 1) {
            val top = ranked.take(Backup.num(lib["standN"], 4.0, 2.0, 10.0).toInt())
            val hi = top.maxOf { eloOf(it) }
            val lo = minOf(1400.0, top.minOf { eloOf(it) })
            c += More.Stand(pick, top.map { it to (Rank.jsRound((eloOf(it) - lo) / maxOf(1.0, hi - lo) * 100) / 100).toFloat() }, !lib.off("homeTint"))
        }
        if (ton != null) {
            val t0 = ton.top
            c += More.Tonight(
                hrs, TONIGHT_H, t0.g, t0.why.take(2).joinToString(" · ").ifEmpty { "top of your ranking" },
                if (ton.rest.isNotEmpty()) "Otherwise: " + ton.rest.joinToString(", ") { it.g.title } else "",
            )
        }
        if (on("swing") && tr.ok && best != null && worst != null && best.first.id != worst.first.id) {
            val said = { g: Game -> "you said " + Queue.jsNum(q.hype(g)) + " · " }
            c += More.Far(
                best.first,
                listOf(
                    Offer(best.first, Queue.jsNum(q.hype(best.first)), Queue.jsNum(best.third!!), said(best.first) + "+" + Taste.fixed(best.second, 1) + " better"),
                    Offer(
                        worst.first, Queue.jsNum(q.hype(worst.first)), Queue.jsNum(worst.third!!),
                        said(worst.first) + if (worst.second < 0) Taste.fixed(worst.second, 1) + " short" else "+" + Taste.fixed(worst.second, 1) + " better",
                    ),
                ),
            )
        }
        if (on("year")) {
            val peakM = months.indices.maxBy { months[it] }
            c += More.Months(live.firstOrNull { it.date.isNotEmpty() && monthOf(it) == peakM } ?: pick, months.toList(), maxOf(1, months.max()))
        }
        b to c
    }

    /** Your order and hidden cards for a stack (`S.stackCfg[key]`). */
    private fun cfg(key: String): Pair<List<String>, Set<String>> {
        val o = (lib["stackCfg"] as? kotlinx.serialization.json.JsonObject)?.get(key) as? kotlinx.serialization.json.JsonObject
        val ids = { k: String -> (o?.get(k) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } }
        return ids("order") to ids("hidden").toSet()
    }

    /** Every card of stack [key] ("sB" or "sC") in your order, hidden ones too; one new since you ordered goes first. */
    fun stackAll(key: String): List<More> {
        val all = if (key == "sB") stacks.first else stacks.second
        val ord = cfg(key).first
        return all.sortedBy { ord.indexOf(it.id).let { i -> if (i < 0) -1 else i } }
    }

    /** The cards stack [key] shows. */
    fun stack(key: String): List<More> = cfg(key).second.let { hid -> stackAll(key).filter { it.id !in hid } }

    /** The cards stack [key] hides. */
    fun stackHidden(key: String): Set<String> = cfg(key).second

    /** Worked out off Main, so the first draw only reads. */
    /** The unfolded rail's live strip at this copy's clock ([Strip.items]); the rail works it out again each minute. */
    val strip: List<Strip.Tile> by lazy { Strip.items(this, now, zone) }

    fun warm() { ranked; dated; fut; hours; nexiPick; top; next; queueCovers; playing; donut; stacks; strip }

    companion object {
        /** Tonight's hours to pick from (`[1, 2, 3, 4, 6]`). */
        val TONIGHT_H = listOf(1, 2, 3, 4, 6)

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
