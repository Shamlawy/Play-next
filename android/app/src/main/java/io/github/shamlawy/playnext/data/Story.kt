package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 2 piece 3b (android/parity/2-map/1-page.md §5.4-5.7): the cards under Your progress that work things out for you.
 * The Finish forecast and its clash line, Then & now, and the Drifting card are pure functions of your games; the
 * story so far ([Recap]) asks the helper. Each mirrors the web function it names, with its line in index.html.
 */
object Story {
    /** `new Date(d + "T12:00:00")`: the day's local noon, null when JavaScript would get NaN. */
    fun noon(d: String?, zone: ZoneId): Long? {
        if (d.isNullOrEmpty()) return null
        return runCatching { LocalDate.parse(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    /**
     * The phone's own order for a day and short month (`toLocaleDateString(undefined, {day, month: "short"})`):
     * "14 Nov" in the UK, "Nov 14" in the US, as NoteSheet's noteDate; [fallback] off a phone (plain unit tests).
     */
    private fun pattern(locale: Locale, skel: String, fallback: String): String =
        runCatching { android.text.format.DateFormat.getBestDateTimePattern(locale, skel) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback

    /** `etaNice(at)` (20786): "14 Nov" in the phone's language, kept on one line (a date never breaks: the design eye). */
    fun nice(at: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        whole(Instant.ofEpochMilli(at).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern(pattern(locale, "dMMM", "d MMM"), locale)))

    /** Spaces that never break, so "27 Feb" stays on one line. */
    fun whole(s: String): String = s.replace(' ', '\u00A0')

    /** `rdNice(d)` (20201): "14 Nov", with the year when it isn't this one; "TBA" for no date. */
    fun rdNice(d: String, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        if (d.isEmpty()) return "TBA"
        val at = noon(d, zone) ?: return d
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val year = Instant.ofEpochMilli(now).atZone(zone).year
        val p = if (d.take(4) == year.toString()) pattern(locale, "dMMM", "d MMM") else pattern(locale, "dMMMy", "d MMM y")
        return whole(day.format(DateTimeFormatter.ofPattern(p, locale)))
    }

    /** `smMed`: the middle value (the mean of the two middle ones), 0 for none. */
    fun median(a: List<Double>): Double {
        if (a.isEmpty()) return 0.0
        val s = a.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /** `smTags(g)`: the game's genres in lower case. */
    fun tags(g: Game): Set<String> = Rank.platList(g["genres"]).map { it.lowercase() }.toSet()

    /** `smJac(a, b)`: how alike two sets are (shared over all), 0 when either is empty. */
    fun jac(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else a.count { it in b }.toDouble() / (a + b).size

    /** `smNames(L)`: "A", "A and B", "A, B and C". */
    fun names(l: List<Game>): String = if (l.size == 1) l[0].title else l.dropLast(1).joinToString(", ") { it.title } + " and " + l.last().title

    /** `smH(h)`: "1.5h" from an hour (rounded to halves), else minutes rounded to 5 (at least 5): "25 min". */
    fun smH(h: Double): String = if (h >= 1) Queue.jsNum(Rank.jsRound(h * 2) / 2) + "h" else maxOf(5.0, Rank.jsRound(h * 60 / 5) * 5).toLong().toString() + " min"

    /** `loggedOf(g)` and `dailyOf(g)` (9240). */
    fun logged(g: Game): Double = Backup.num(g["logged"], 0.0, 0.0, 100000.0)
    fun daily(g: Game): Double = Backup.num(g["daily"], 0.0, 0.0, 12.0)

    /** `pnName()`: what you call Nexi (`S.bubName`), "Nexi" by default. */
    fun nexi(lib: Library): String = Backup.str(lib["bubName"]).ifEmpty { "Nexi" }
}

/** The Finish forecast (`etaOf` 20774, `etaClashes` 20787, `etaCard` 20798). */
object Forecast {
    data class Eta(val need: Double, val have: Double, val left: Double, val pace: Double, val days: Int, val at: Long, val src: String, val date: String)

    /** A game you hyped coming out before you'll finish [g]: [until] days away, [per] hours a day to make it. */
    data class Clash(val g: Game, val r: Game, val until: Int, val per: Double)

    /** Settings › Extra features › Finish forecast off (`S.etaOff`): no card (the track's "~14 Nov" stays, as on the web). */
    fun off(lib: Library) = Backup.truthy(lib["etaOff"])

    /** `etaOf(g)`: a playing, non-competitive game with hours to beat; pace from your own, your plan, or your evenings. */
    fun of(lib: Library, rk: Rank, g: Game, now: Long, zone: ZoneId): Eta? {
        if (g.comp || rk.isPlayed(g) || g.rawState != "playing") return null
        val need = rk.hrsOf(g)
        if (!(need > 0)) return null
        val have = Story.logged(g)
        val left = maxOf(0.0, need - have)
        val plAt = Backup.num(g["plAt"], 0.0, 0.0, Double.MAX_VALUE)
        val days = if (plAt != 0.0) (now - plAt) / JsDate.DAY else 0.0
        val pace: Double
        val src: String
        if (have > 0 && days >= 3) {
            pace = have / days; src = "your pace so far"
        } else if (Story.daily(g) > 0) {
            pace = Story.daily(g); src = "your plan of ${Queue.jsNum(Story.daily(g))}h a day"
        } else {
            val ev = Backup.num(lib["evening"], 4.0, 1.0, 12.0)
            pace = ev * 4 / 7; src = "about ${Queue.jsNum(ev)}h an evening, 4 evenings a week"
        }
        if (!(pace > .05)) return null
        val d = Math.ceil(left / pace).toInt()
        val at = now + d * JsDate.DAY
        return Eta(need, have, left, pace, d, at, src, JsDate.todayLocal(at, zone))
    }

    /** `etaClashes()` for one game: the soonest game you hyped (7+) that's out within 60 days and before [e]'s date. */
    fun clash(lib: Library, rk: Rank, g: Game, e: Eta, now: Long, zone: ZoneId): Clash? {
        if (e.left <= 0) return null
        val td = JsDate.todayLocal(now, zone)
        val r = lib.games.filter { r ->
            !rk.isPlayed(r) && !rk.isDropped(r) && !r.comp && r.date.isNotEmpty() && r.date > td && Backup.str(r["status"]) != "tba" &&
                Backup.num(r["hype"], 0.0, 0.0, 10.0) >= 7 && r.date < e.date
        }.minByOrNull { it.date } ?: return null
        val at = Story.noon(r.date, zone) ?: return null
        val until = maxOf(1.0, Rank.jsRound((at - now).toDouble() / JsDate.DAY)).toInt()
        return if (until <= 60) Clash(g, r, until, e.left / until) else null
    }

    /** `etaFix(c)`: what to do about it (more than 5h a day isn't advice anyone can take). */
    fun fix(c: Clash): String = if (c.per <= 5) "About ${fixed1(c.per)}h a day finishes it in time"
    else "You'd need ${Rank.jsRound(c.per).toLong()}h a day, so you'll probably switch to ${c.r.title} and come back"

    /** The card's words: the line as [bold] parts, "Going by ….", and the clash line or null. */
    fun words(c: Eta, clash: Clash?, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): Triple<List<Pair<String, Boolean>>, String, String?> {
        val line = if (c.left <= 0) listOf("By the hours, you should be about done!" to false)
        else listOf("Done around " to false, Story.nice(c.at, zone, locale) to true, " · ${Rank.jsRound(c.left).toLong()}h left" to false)
        return Triple(line, "Going by ${c.src}.", clash?.let { "${it.r.title} comes out ${Story.rdNice(it.r.date, now, zone, locale)}, before you'll finish. ${fix(it)}." })
    }

    /**
     * The first forecast per game while the app runs (the web keeps it as `g.eta0` the first time `etaOf` gives one with
     * hours left, 21160): Then & now's "vs my finish forecast" reads it as the snapshot's `e0`. Kept off the practice
     * copy, so opening a page never counts as a change; a game whose backup has `eta0` already keeps that one.
     * Its time is kept (the web's `eta0.at`): Android drops it on every restart, so one first seen on finish day
     * would be a forecast from minutes before and "N days early" against it means nothing.
     */
    private val firsts = ConcurrentHashMap<String, Pair<String, Long>>()

    fun seen(g: Game, e: Eta?, now: Long) {
        if (e == null || e.left <= 0 || Backup.truthy(g["eta0"])) return
        firsts.putIfAbsent(g.id, e.date to now)
    }

    /**
     * The first forecast's date for [g]: the backup's `eta0.d`, else one seen while the app runs, if it's at least
     * a day older than [now] (a younger one isn't a forecast worth measuring against).
     */
    fun first(g: Game, now: Long): JsonElement? = (g["eta0"] as? JsonObject)?.get("d")?.takeIf { Backup.truthy(it) }
        ?: firsts[g.id]?.takeIf { now - it.second >= JsDate.DAY }?.let { JsonPrimitive(it.first) }

    internal fun forget() = firsts.clear()
}

/** Then & now (`capCard` 20890, its forecast cell 21181): a finished game against what you expected. */
object ThenNow {
    class Note(val t: Long, val x: String)
    class Card(val cells: List<Pair<String, String>>, val first: Note?, val last: Note?)

    fun off(lib: Library) = Backup.truthy(lib["capOff"])

    private fun note(e: JsonElement?): Note? {
        val o = e as? JsonObject ?: return null
        val t = (o["t"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: return null
        return Note(t, Backup.str(o["x"]))
    }

    fun of(lib: Library, rk: Rank, g: Game, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): Card? {
        if (off(lib) || !rk.isPlayed(g) || g.comp) return null
        val p = rk.playedRec(g)
        val c = (g["cap"] as? JsonObject) ?: JsonObject(emptyMap())
        val sc = p?.takeIf { it.scored }?.real
        val h = Backup.num(c["h"], 0.0, -1e9, 1e9)
        val hype = if (h != 0.0) h else Backup.num(g["hype"], 0.0, 0.0, 10.0)
        val cells = ArrayList<Pair<String, String>>()
        if (sc != null && hype != 0.0) {
            val d = sc - hype
            cells += "${Queue.jsNum(hype)} → ${Queue.jsNum(sc)}" to (if (d == 0.0) "hype → score: spot on" else "hype → score (${if (d > 0) "+" else "−"}${Queue.jsNum(Math.abs(d))})")
        }
        val pr = Backup.num(c["pr"], 0.0, -1e9, 1e9)
        if (sc != null && pr != 0.0) cells += fixed1(pr) to "${Story.nexi(lib)}'s guess (off by ${fixed1(Math.abs(sc - pr))})"
        val start = Backup.num(c["st"], 0.0, 0.0, Double.MAX_VALUE).toLong()
        val t = p?.t.orEmpty()
        val end: Long? = if (t.isNotEmpty()) Story.noon(t, zone) else Backup.num(c["at"], 0.0, 0.0, Double.MAX_VALUE).toLong()
        if (start != 0L && end != null && end > start) {
            cells += "${maxOf(1.0, Rank.jsRound((end - start).toDouble() / JsDate.DAY)).toLong()} days" to "from ${Story.nice(start, zone, locale)} to ${Story.nice(end, zone, locale)}"
        } else {
            val ad = Backup.str(c["ad"])
            val a = Story.noon(ad, zone)
            if (ad.isNotEmpty() && end != null && end != 0L && a != null && end > a) cells += "${Rank.jsRound((end - a).toDouble() / JsDate.DAY).toLong()} days" to "in your library before you finished it"
        }
        val cr = Backup.num(c["cr"], 0.0, -1e9, 1e9)
        if (cr != 0.0 && sc != null) cells += Queue.jsNum(cr) to "critics · you ${if (sc * 10 >= cr) "liked it more" else "liked it less"}"
        val notes = Notes.of(g)
        val first = note(c["n1"]) ?: notes.firstOrNull()?.let { Note(it.t, it.x) }
        val last = notes.lastOrNull()?.let { Note(it.t, it.x) }
        if (cells.size < 2 && first == null) return null
        // the finish forecast against the real finish, first in the row (only when there's a row)
        val e0 = Backup.str(c["e0"])
        if (cells.isNotEmpty() && e0.isNotEmpty() && t.isNotEmpty()) {
            val a = Story.noon(t, zone)
            val b = Story.noon(e0, zone)
            if (a != null && b != null) {
                val dd = Rank.jsRound((a - b).toDouble() / JsDate.DAY).toLong()
                cells.add(0, (if (dd == 0L) "On the day" else "${Math.abs(dd)} days " + (if (dd < 0) "early" else "late")) to "vs my finish forecast (${Story.rdNice(e0, now, zone, locale)})")
            }
        }
        return Card(cells, first, last?.takeIf { first != null && it.t != first.t })
    }
}

/** The Drifting card (`dwRisk` 23969, `dwCard` 23994, `dwAct` 24004, `dwShort` 24012). */
object Drift {
    class Day(val t: Long, val h: Double)
    class Risk(val g: Game, val gapNow: Double, val typ: Double, val why: List<String>, val like: List<Game>)

    fun off(lib: Library) = Backup.truthy(lib["dwOff"])

    /** `dwDays(g)`: the days you played it (timer sessions, logged-hours changes, notes, the day it started), oldest first. */
    fun days(lib: Library, g: Game, zone: ZoneId): List<Day> {
        val m = LinkedHashMap<String, DoubleArray>()
        fun add(t: Double, h: Double, k: Int) {
            if (!(t > 0)) return
            val d = JsDate.todayLocal(t.toLong(), zone)
            m.getOrPut(d) { DoubleArray(2) }[k] += h
        }
        fun n(e: JsonElement?) = (e as? JsonPrimitive)?.content?.toDoubleOrNull() ?: Double.NaN
        (lib["sessions"] as? JsonArray).orEmpty().forEach { e ->
            val s = e as? JsonObject ?: return@forEach
            if (Backup.str(s["g"]) == g.id) add(n(s["s"]), maxOf(0.0, (n(s["e"]) - n(s["s"])) / 3.6e6).let { if (it.isNaN()) 0.0 else it }, 0)
        }
        (g["lgH"] as? JsonArray).orEmpty().forEach { e ->
            val x = e as? JsonArray ?: return@forEach
            add(n(x.getOrNull(0)), maxOf(0.0, n(x.getOrNull(1)).let { if (it.isNaN()) 0.0 else it }), 1)
        }
        Notes.of(g).forEach { add(it.t.toDouble(), 0.0, 0) }
        val pl = Backup.num(g["plAt"], 0.0, 0.0, Double.MAX_VALUE)
        if (pl != 0.0) add(pl, 0.0, 0)
        return m.entries.mapNotNull { (d, o) -> Story.noon(d, zone)?.let { Day(it, maxOf(o[0], o[1])) } }.sortedBy { it.t }
    }

    /** `dwZone()`: how far in you usually are when you drop a game (two drops at least). */
    fun zone(lib: Library, rk: Rank): Double? {
        val p = lib.games.filter { rk.isDropped(it) }.mapNotNull { x ->
            val dp = Backup.num(x["dropP"], 0.0, -1e9, 1e9)
            val h = rk.hrsOf(x)
            val l = Story.logged(x)
            if (dp > 0) dp else if (h > 0 && l > 0) minOf(1.0, l / h) else null
        }
        return if (p.size >= 2) Story.median(p) else null
    }

    private class Worst(val name: String, val n: Int, val of: Int, val r: Double)

    /** `dwGenre(g)`: a genre of [g]'s where you dropped 2+ and at least half of those you finished or dropped. */
    private fun genre(lib: Library, rk: Rank, g: Game): Worst? {
        var worst: Worst? = null
        for (n in Rank.platList(g["genres"])) {
            val k = n.lowercase()
            val all = lib.games.filter { it.id != g.id && (rk.isPlayed(it) || rk.isDropped(it)) && k in Story.tags(it) }
            val dr = all.count { rk.isDropped(it) }
            if (dr >= 2 && dr.toDouble() / all.size >= .5 && (worst == null || dr.toDouble() / all.size > worst.r)) worst = Worst(n, dr, all.size, dr.toDouble() / all.size)
        }
        return worst
    }

    /** `dwRisk(g)`: a playing game you haven't come back to for over twice your usual gap, with one more sign. */
    fun risk(lib: Library, rk: Rank, g: Game, now: Long, zone: ZoneId): Risk? {
        if (off(lib) || g.comp || rk.isPlayed(g) || g.rawState != "playing") return null
        val d = days(lib, g, zone)
        if (d.size < 3) return null
        val gapNow = (now - d.last().t).toDouble() / JsDate.DAY
        if (gapNow >= 14) return null // "Still playing?" asks from here
        val gaps = d.drop(1).mapIndexed { i, x -> (x.t - d[i].t).toDouble() / JsDate.DAY }
        val early = if (gaps.size > 2) gaps.dropLast(2) else gaps.dropLast(1).ifEmpty { gaps }
        val typ = maxOf(1.0, Story.median(early))
        if (gapNow < maxOf(4.0, typ * 2.2)) return null
        val why = arrayListOf("you haven't played it in ${Math.floor(gapNow).toLong()} days, and you usually come back " + if (typ < 1.6) "every day or two" else "every ${Rank.jsRound(typ).toLong()} days")
        val h = d.filter { it.h > 0 }.map { it.h }
        if (h.size >= 4) {
            val a = h.dropLast(2)
            val ma = a.sum() / a.size
            val mb = (h[h.size - 1] + h[h.size - 2]) / 2
            if (mb < ma * .5) why += "your sessions shrank from about ${Story.smH(ma)} to ${Story.smH(mb)}"
        }
        if (gaps.size >= 3 && gaps.takeLast(2).all { it >= typ * 1.8 }) why += "the gaps between sessions keep getting longer"
        val z = zone(lib, rk)
        val need = rk.hrsOf(g)
        val have = Story.logged(g)
        val prog = if (need > 0 && have > 0) minOf(1.0, have / need) else null
        if (z != null && prog != null && Math.abs(prog - z) <= .15) why += "you're ${Rank.jsRound(prog * 100).toLong()}% in, about where you usually drop games (${Rank.jsRound(z * 100).toLong()}%)"
        genre(lib, rk, g)?.let { why += "you dropped ${it.n} of the ${it.of} ${it.name} games you started" }
        if (why.size < 2) return null
        val tg = Story.tags(g)
        val like = lib.games.filter { rk.isDropped(it) && Story.jac(Story.tags(it), tg) >= .34 }.takeLast(2)
        return Risk(g, gapNow, typ, why, like)
    }

    /** The card's sentence under its title. */
    fun line(r: Risk): String = if (r.like.isNotEmpty()) "It's going the way ${Story.names(r.like)} went before you dropped ${if (r.like.size == 1) "it" else "them"}."
    else "It looks like the way games go before you drop them."

    /** `dwCleanse(g)`: up to 3 short games from your queue that are out, least like [g] first, then On deck's order. */
    fun cleanse(q: Queue, g: Game): List<Game> {
        val rk = q.rk
        val tg = Story.tags(g)
        val cap = maxOf(4.0, minOf(12.0, rk.hrsOf(g).let { if (it != 0.0) it else 30.0 } * .4))
        val s = rk.deck
        return q.lib.games.filter { x ->
            x.id != g.id && !rk.isPlayed(x) && !rk.isDropped(x) && !x.comp && x.rawState != "playing" && q.isOutNow(x) && rk.hrsOf(x) > 0 && rk.hrsOf(x) <= cap
        }.map { it to Story.jac(Story.tags(it), tg) }
            .sortedWith { a, b -> a.second.compareTo(b.second).let { c -> if (c != 0) c else (s[b.first.id] ?: 0.0).compareTo(s[a.first.id] ?: 0.0) } }
            .take(3).map { it.first }
    }

    /** `S.dwAsked` with [id] asked [now]. */
    private fun asked(cur: (String) -> JsonElement?, lib: Library, id: String, now: Long): JsonObject {
        val o = (cur("dwAsked") as? JsonObject) ?: (lib["dwAsked"] as? JsonObject) ?: JsonObject(emptyMap())
        return JsonObject(o + (id to JsonPrimitive(now)))
    }

    /** "I'm into it": `S.dwAsked[id]` and `g.sgOk`. (The card is gone from the open page; the web's comes back next open.) */
    fun ok(lib: Library, g: Game, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> = mapOf(
        "dwAsked" to asked(cur, lib, g.id, now),
        Practice.GAMES to Practice.fields(cur(Practice.GAMES), g.id, mapOf("sgOk" to JsonPrimitive(now))),
    )

    /** "Park it": back to the backlog (`g.state = "backlog"` first, so `syncPlayed` keeps `g.plAt`), `g.parked`. */
    fun park(g: Game, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> =
        mapOf(Practice.GAMES to Practice.fields(cur(Practice.GAMES), g.id, mapOf("state" to JsonPrimitive("backlog"), "parked" to JsonPrimitive(now))))

    /** "Start it" in Something short in between: [x] is playing, [g] remembers it (`g.dwBack`) and that it was asked. */
    fun startShort(lib: Library, g: Game, x: Game, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val out = LinkedHashMap(Practice.start(lib, x, now, cur))
        out[Practice.GAMES] = Practice.fields(out[Practice.GAMES], g.id, mapOf("dwBack" to JsonPrimitive(x.id)))
        out["dwAsked"] = asked(cur, lib, g.id, now)
        return out
    }
}

/**
 * The story so far (v216, `wiStory*` 20025-20082): under your newest note, the helper's short recap of the plot up to
 * where the note says you are (`POST /recap`, read only, so allowed before switch day). The web caches it in the backup
 * (`g.wiStory`); here it's kept in memory while the app runs, keyed by the note's time, and a cache already in the
 * backup is used as is. An error waits 6 hours before asking again ([RETRY]).
 */
object Recap {
    class Entry(val t: Long, val x: String, val w: String, val sure: Double, val err: Boolean, val at: Long)

    const val RETRY = 6 * 3_600_000L

    fun off(lib: Library) = Backup.truthy(lib["wiStoryOff"])

    private val mem = ConcurrentHashMap<String, Entry>()
    private val busy = ConcurrentHashMap.newKeySet<String>()

    /** Games whose fetch found another one busy (a note saved mid-call): asked again when that one ends. */
    private val again = ConcurrentHashMap.newKeySet<String>()

    /** The helper answered, but not with a recap (its HTTP error): kept as an error, unlike no network. */
    class HelperError(msg: String) : Exception(msg)

    /** What changed (a counter): the page's card reads it so a recap that arrives shows. */
    val seen = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The helper's call; tests swap it. */
    @Volatile internal var post: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/recap"), body)) { r ->
            if (!r.isSuccessful) throw HelperError("HTTP ${r.code}")
            r.body.string()
        }
    }

    private fun backup(g: Game): Entry? {
        val o = g["wiStory"] as? JsonObject ?: return null
        val t = (o["t"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: return null
        val x = Backup.str(o["x"])
        return Entry(t, x, Backup.str(o["w"]), Backup.num(o["sure"], 0.0, -1e9, 1e9), x.isEmpty() && Backup.truthy(o["err"]), Backup.num(o["at"], 0.0, 0.0, Double.MAX_VALUE).toLong())
    }

    /** The recap for [g]'s newest note (null: none yet). A game without a note, a competitive one, or off: nothing. */
    fun of(lib: Library, g: Game): Entry? {
        val n = Notes.last(g) ?: return null
        if (off(lib) || g.comp) return null
        return mem[g.id]?.takeIf { it.t == n.t } ?: backup(g)?.takeIf { it.t == n.t }
    }

    /** Whether the card shows at all: a note, not competitive, not off, and not an error. */
    fun shows(lib: Library, g: Game): Boolean {
        if (Notes.last(g) == null || off(lib) || g.comp) return false
        return of(lib, g)?.let { !it.err || it.x.isNotEmpty() } ?: true
    }

    /** The request (`wiStoryGet`): the game, its first platform, 600 characters of its blurb, hours, the note and the older ones. */
    fun body(g: Game): String {
        val notes = Notes.of(g)
        return JsonObject(
            linkedMapOf(
                "title" to JsonPrimitive(g.title),
                "year" to JsonPrimitive(g.date.take(4)),
                "platform" to JsonPrimitive(Rank.platList(g["platforms"]).firstOrNull().orEmpty()),
                "about" to JsonPrimitive(Backup.str(g["desc"]).ifEmpty { Backup.str(g["about"]) }.take(600)),
                "hours" to Backup.jsNumber(Backup.num(g["logged"], 0.0, -1e15, 1e15)),
                "note" to JsonPrimitive(notes.last().x),
                "earlier" to JsonArray(notes.dropLast(1).map { JsonPrimitive(it.x) }),
            ),
        ).toString()
    }

    /**
     * Asks the helper when [g]'s newest note has no recap yet (or its error is 6 hours old). Never throws. No network
     * records nothing (the web skips while `!navigator.onLine`): the card keeps its bars and the next page open asks.
     */
    suspend fun fetch(lib: Library, g: Game, now: () -> Long) {
        val n = Notes.last(g) ?: return
        if (off(lib) || g.comp) return
        val c = of(lib, g)
        if (c != null && (c.x.isNotEmpty() || now() - c.at < RETRY)) return
        if (!busy.add(g.id)) { again.add(g.id); return }
        try {
            val e = try {
                val j = kotlinx.serialization.json.Json.parseToJsonElement(post(body(g))) as? JsonObject
                val x = Backup.str(j?.get("recap"))
                if (x.isEmpty()) Entry(n.t, "", "", 0.0, true, now())
                else Entry(n.t, x, Backup.str(j?.get("where")), Backup.num(j?.get("sure"), 0.0, -1e9, 1e9), false, now())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                return
            } catch (e: Exception) {
                Entry(n.t, "", "", 0.0, true, now())
            }
            // a newer note came in meanwhile: this recap is for the old one, keep it only under its own note's time
            mem[g.id] = e
            seen.value++
        } finally {
            busy.remove(g.id)
            // also when cancelled or offline: a card whose fetch found this one busy (a note saved mid-call) asks again
            if (again.remove(g.id)) seen.value++
        }
    }

    internal fun forget() { mem.clear(); busy.clear(); again.clear() }
}
