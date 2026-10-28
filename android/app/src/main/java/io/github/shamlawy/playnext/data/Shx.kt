package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** A picture: an address (a link, a repo file, an uploaded data: picture) or the drawn cover a game without one gets. */
sealed interface Pic {
    data class Url(val url: String) : Pic
    data class Drawn(val g: Game) : Pic

    companion object {
        /** `cover(g)`: the box art, else the drawn one (index.html `placeholder`). */
        fun cover(g: Game): Pic = Backup.str(g["img"]).ifEmpty { null }?.let { Url(it) } ?: Drawn(g)

        /** `bgSource(g)` (index.html 8594): the wide picture behind a game, null when it's switched off. */
        fun backdrop(g: Game): Pic? {
            if (Backup.truthy(g["bgOff"])) return null
            Backup.str(g["hero"]).ifEmpty { null }?.let { return Url(it) }
            if (Backup.truthy(g["steamId"])) return Url(Web.STEAM_CDN + Backup.str(g["steamId"]) + "/library_hero.jpg")
            for (k in listOf("bg", "art")) Backup.str(g[k]).ifEmpty { null }?.let { return Url(it) }
            return cover(g)
        }
    }
}

/**
 * A header card's contents (index.html `shxData` + `shxExtra`, v194/v210), for one of the seven screens that have one
 * (queue, lib, played, replay, plan, taste, add; Home has its own top). Built from your games, the clock and the zone.
 */
class Shx(
    val covers: List<Game>,
    val stats: List<Stat>,
    val line: Line?,
    val tags: List<Pair<String, Int>>,
    val bar: List<Seg>?,
    val chart: Chart?,
    val art: Pic?,
) {
    /** A number and its label; [unit] is the small grey letter after it ("h", "d"). */
    data class Stat(val value: String, val unit: String?, val label: String)

    /** The spotlight line: a kicker, the game and a short tail. */
    data class Line(val g: Game, val k: String, val x: String)

    /** One part of the mix bar; [c] is its colour slot (0 the accent … 4 the faintest). */
    data class Seg(val n: Int, val label: String, val c: Int)

    /** Six months of counts, oldest first (or this month first, ahead). */
    data class Chart(val v: List<Int>, val cap: String)

    companion object {
        val KINDS = listOf("queue", "lib", "played", "replay", "plan", "taste", "add")

        /** "Oct 27" (the web app's `toLocaleDateString(undefined, { day: "numeric", month: "short" })`). */
        val US_SHORT: (LocalDate) -> String = { it.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) }

        /** One screen's card, with its own Rank. */
        fun of(kind: String, lib: Library, now: Long, zone: ZoneId, short: (LocalDate) -> String = US_SHORT): Shx =
            Build(kind, Rank(lib, now, zone), now, zone, short).run()

        /**
         * Every screen's card at once, sharing one Rank (the rank table is worked out once, not per screen). Games does
         * this off Main with each copy and each new day, so a tab switch only draws.
         */
        fun all(lib: Library, now: Long, zone: ZoneId, short: (LocalDate) -> String = US_SHORT): Cards {
            val rk = Rank(lib, now, zone)
            // a record the maths can't read leaves that screen a plain title, as the web's try/catch around shxData does
            return Cards(lib, now, KINDS.associateWith { k -> runCatching { Build(k, rk, now, zone, short).run() }.getOrNull() })
        }

        /** How many cards have been worked out: a test checks that drawing a screen never does it. */
        internal val built = AtomicInteger()
    }

    /** Each screen's card for one copy of your games ([lib]) at one moment ([at]); null for a screen whose maths failed. */
    class Cards(val lib: Library, val at: Long, private val got: Map<String, Shx?>) {
        fun has(kind: String) = kind in got
        operator fun get(kind: String): Shx? = got[kind]
    }

    private class Build(val kind: String, val rk: Rank, val now: Long, val zone: ZoneId, val shortFmt: (LocalDate) -> String) {
        val lib = rk.lib
        val games = lib.games
        val day0 = JsDate.today0(now, zone)
        val waiting by lazy { games.filter { !rk.isPlayed(it) && !rk.isDropped(it) } }
        val at = { s: String -> JsDate.localMidnight(s, zone) }
        fun short(s: String) = at(s)?.let { shortFmt(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) } ?: "Invalid Date"

        /** `recent()`: the four added last (`added` strings, newest first; a stable sort, as JavaScript's). */
        fun recent() = games.sortedWith { a, b -> Backup.str(b["added"]).compareTo(Backup.str(a["added"])) }.take(4)

        /** `S.played.length`: every finish counts, replays too. */
        val playedN = lib.played.size

        fun run(): Shx {
            built.incrementAndGet()
            val (covers, stats) = data()
            val cv = covers.take(4)
            return extra(cv, stats, cv.firstOrNull())
        }

        private fun n(v: Int, label: String) = Stat(v.toString(), null, label)

        /** `shxData(kind)` (index.html 16830): the covers and the numbers. */
        fun data(): Pair<List<Game>, List<Stat>> {
            // the rank table only for the screens that show it
            val sTier by lazy { rk.table.sc.values.count { it >= 9 } }
            return when (kind) {
                "queue" -> {
                    val hrs = Rank.jsRound(waiting.sumOf { rk.hrsOf(it) }).toLong()
                    rk.onDeck(4) to listOf(n(waiting.size, "waiting"), Stat(hrs.toString(), "h", "to beat"), n(waiting.count { !it.isUpcoming(now) }, "out now"))
                }
                "lib" -> recent() to listOf(n(games.size, "games"), n(playedN, "played"), n(games.count { !rk.isPlayed(it) && it.isUpcoming(now) }, "coming"))
                "played" -> {
                    val sc = lib.played.filter { it.scored }.map { it.real!! }
                    val avg = if (sc.isEmpty()) "–" else fixed1(sc.sum() / sc.size)
                    rk.rankedOrPlayed.take(4) to listOf(n(playedN, "finished"), n(sTier, "S‑tier"), Stat(avg, null, "average score"))
                }
                "replay" -> {
                    val again = lib.played.filter { it.again }.mapNotNull { rk.find(it.id) }
                    (again.ifEmpty { rk.rankedOrPlayed }).take(4) to listOf(n(again.size, "to replay"), n(playedN, "finished"))
                }
                "plan" -> {
                    val next = upcomingDated()
                    val dd = next.firstOrNull()?.let { Rank.jsRound((at(it.date)!! - day0) / JsDate.DAY.toDouble()).toLong() }
                    next.take(4) to listOf(
                        when (dd) { null -> Stat("–", null, "nothing dated"); 0L -> Stat("Today", null, "to " + next[0].title); else -> Stat(dd.toString(), "d", "to " + next[0].title) },
                        n(next.count { at(it.date)!! - day0 < 31 * JsDate.DAY }, "in 30 days"),
                    )
                }
                "taste" -> rk.rankedOrPlayed.take(4) to listOf(n(rk.table.order.size, "ranked"), n(lib.duels.size, "duels"), n(sTier, "S‑tier"))
                else -> {
                    val ym = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone)).toString()
                    recent() to listOf(n(games.size, "in your library"), n(games.count { Backup.str(it["added"]).startsWith(ym) }, "added this month"))
                }
            }
        }

        /** Games with a date from today on, not played, soonest first. */
        fun upcomingDated() = games.filter { g -> g.date.isNotEmpty() && !rk.isPlayed(g) && (at(g.date)?.let { it >= day0 } ?: false) }
            .sortedWith { a, b -> a.date.compareTo(b.date) }

        /** `months(6, fwd)` as "YYYY-MM": the last six ending this month, or this month and five ahead. */
        fun months(fwd: Boolean): List<String> {
            val m0 = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            val a = (0 until 6).map { (if (fwd) m0.plusMonths(it.toLong()) else m0.minusMonths(it.toLong())).toString() }
            return if (fwd) a else a.reversed()
        }

        fun perMonth(dates: List<String>, fwd: Boolean, cap: String): Chart? {
            val v = months(fwd).map { k -> dates.count { it.startsWith(k) } }
            return if (v.any { it != 0 }) Chart(v, cap) else null
        }

        /** `shxExtra(kind, front)` (index.html 16867): the spotlight line, top genres, mix bar, 6-month chart and backdrop. */
        fun extra(cv: List<Game>, stats: List<Stat>, front: Game?): Shx {
            val playedG = lib.played.mapNotNull { rk.find(it.id) }
            val ranked = { rk.ranked.ifEmpty { playedG } }
            val added = { perMonth(games.map { Backup.str(it["added"]) }, false, "added") }
            var line: Line? = null
            var pool: List<Game> = emptyList()
            var bar: List<Seg>? = null
            var chart: Chart? = null
            fun segs(vararg s: Pair<Int, String>) = s.mapIndexed { i, (n, l) -> Seg(n, l, i) }
            when (kind) {
                "queue" -> {
                    rk.onDeck(1).firstOrNull()?.let { g ->
                        line = Line(g, "Up next", if (g.rawState == "playing") "playing" else if (g.isUpcoming(now)) (if (g.date.isNotEmpty()) "out " + short(g.date) else "coming") else "out now")
                    }
                    pool = waiting
                    val pl = waiting.count { it.rawState == "playing" }
                    val up = waiting.count { it.rawState != "playing" && it.isUpcoming(now) }
                    bar = segs(pl to "playing", (waiting.size - pl - up) to "out now", up to "coming")
                    chart = added()
                }
                "lib", "add" -> {
                    games.filter { Backup.truthy(it["added"]) }.sortedWith { a, b -> Backup.str(b["added"]).compareTo(Backup.str(a["added"])) }.firstOrNull()?.let { g ->
                        line = Line(g, if (kind == "add") "Last added" else "Newest", "added " + ago(Backup.str(g["added"])))
                    }
                    pool = games
                    if (kind == "lib") {
                        val pl = waiting.count { it.rawState == "playing" }
                        val up = waiting.count { it.rawState != "playing" && it.isUpcoming(now) }
                        bar = segs(playedN to "played", pl to "playing", (waiting.size - pl - up) to "waiting", up to "coming")
                    }
                    chart = added()
                }
                "played", "taste" -> {
                    val t = rk.table
                    ranked().firstOrNull()?.let { g ->
                        val sc = t.sc[g.id]
                        line = Line(g, "Your #1", if (kind == "taste") "${t.n[g.id] ?: 0} duels" else if (sc != null) fixed1(sc).removeSuffix(".0") + " / 10" else "")
                    }
                    pool = playedG
                    val c = linkedMapOf("S" to 0, "A" to 0, "B" to 0, "C" to 0)
                    for (p in lib.played) t.sc[p.id]?.let { v -> Rank.tierOf(v).let { k -> c[k] = c[k]!! + 1 } }
                    bar = segs(c["S"]!! to "S", c["A"]!! to "A", c["B"]!! to "B", c["C"]!! to "C", (playedN - c.values.sum()) to "unscored")
                    chart = if (kind == "taste") perMonth(lib.duels.map { it.t }, false, "duels") else perMonth(lib.played.map { it.t }, false, "finished")
                }
                "replay" -> {
                    val again = lib.played.filter { it.again }.mapNotNull { rk.find(it.id) }
                    (again.firstOrNull() ?: ranked().firstOrNull())?.let { g -> line = Line(g, if (again.isNotEmpty()) "Replay next" else "Your #1", "") }
                    pool = again.ifEmpty { playedG }
                    bar = segs(again.size to "to replay", (playedN - again.size) to "once was enough")
                    chart = perMonth(lib.played.map { it.t }, false, "finished")
                }
                "plan" -> {
                    val next = upcomingDated()
                    next.firstOrNull()?.let { line = Line(it, "Next out", short(it.date)) }
                    pool = next
                    val wk = next.count { at(it.date)!! - day0 < 7 * JsDate.DAY }
                    val mo = next.count { at(it.date)!! - day0 < 31 * JsDate.DAY }
                    bar = segs(wk to "this week", (mo - wk) to "this month", (next.size - mo) to "later")
                    chart = perMonth(next.map { it.date }, true, "releases ahead")
                }
            }
            val shownBar = bar?.takeIf { b -> b.any { it.n > 0 } }?.filter { it.n > 0 }
            val gc = LinkedHashMap<String, Int>()
            for (g in pool) for (n in Rank.platList(g["genres"])) gc[n] = (gc[n] ?: 0) + 1
            val tags = Rank.jsKeys(gc.keys).map { it to gc[it]!! }.filter { it.second > 1 }.sortedByDescending { it.second }.take(3)
            val ag = if (front != null && Pic.backdrop(front) != null) front else line?.g
            val art = ag?.let { Pic.backdrop(it) ?: Pic.cover(it) }
            return Shx(cv, stats, line, tags, shownBar, chart, art)
        }

        /** `ago(s)`: today, yesterday, N days ago (under 30), else "on Oct 27". */
        fun ago(s: String): String {
            val a = at(s) ?: return "on Invalid Date"
            val d = Rank.jsRound((day0 - a) / JsDate.DAY.toDouble()).toLong()
            return if (d <= 0) "today" else if (d == 1L) "yesterday" else if (d < 30) "$d days ago" else "on " + short(s)
        }
    }
}

/** `toFixed(1)`: the exact value rounded half up, one decimal. */
fun fixed1(v: Double): String = BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toPlainString()
