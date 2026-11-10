package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A game's page in its New style (`gdMarkup`, index.html 22054, android/parity/2-map/1-page.md §2-4): what each part
 * says, worked out from your games, the clock and the zone, so the screen only draws. Pure (tested in PageTest).
 */
class Page(val q: Queue, private val now: Long, private val zone: ZoneId, private val locale: Locale = Locale.getDefault()) {
    private val rk = q.rk
    private val lib = q.lib

    /** One of the four tiles: [value] big, [label] under it; [none] when the value is the dash (drawn in `--mute`). */
    data class Tile(val value: String, val label: String, val none: Boolean = value == Queue.DASH)

    /** A main-row button (`gdNext`): [kind] says what it does, [label] the web's words without the emoji. */
    data class Next(val kind: String, val label: String, val soft: Boolean = false)

    /** One of Your progress's three steps: name, small line, and its class (done / now / drop / ""). */
    data class Step(val name: String, val sub: String, val c: String)

    /** The tag row's genres: the first three, the rest and Steam players' tags behind +N, and how many of your games share each. */
    class Tags(val base: List<String>, val st: List<String>, val n: (String) -> Int) {
        val more: Int get() = (base.size - 3).coerceAtLeast(0) + st.size
    }

    /** `gdState` (index.html 21927): played → dropped → playing → upcoming → backlog. */
    fun state(g: Game): String = when {
        rk.isPlayed(g) -> "played"
        rk.isDropped(g) -> "dropped"
        g.rawState == "playing" -> "playing"
        g.isUpcoming(now) -> "upcoming"
        else -> "backlog"
    }

    /** `GD_STATE`: Your progress's header meta. */
    fun stateWord(st: String): String = GD_STATE[st] ?: ""

    /** The state chip in the tag row (`STATES`' long name; a missing state reads as upcoming). */
    fun stateChip(g: Game): String = io.github.shamlawy.playnext.gen.Web.STATES.firstOrNull { it.first == g.state }?.second ?: ""

    /** `days(d)` (index.html 7314): "N day(s) away", "Out today" or "Out now", by the date's UTC midnight. */
    fun days(d: String): String {
        val at = JsDate.utcMidnight(d) ?: return ""
        val n = Math.ceil((at - now) / JsDate.DAY.toDouble()).toInt()
        return if (n > 0) n.toString() + (if (n == 1) " day away" else " days away") else if (n == 0) "Out today" else "Out now"
    }

    /** `gdMonth(t)`: "Nov 2026" in the phone's language, "" when the date doesn't read. */
    fun month(t: String): String {
        val d = runCatching { LocalDate.parse(t.take(10)) }.getOrNull() ?: return ""
        return d.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
    }

    /** The kicker over the name: "Finished · Mon YYYY", "Dropped", the days to go / out words, or "No date yet". */
    fun kicker(g: Game): String = when (state(g)) {
        "played" -> "Finished" + (rk.playedRec(g)?.t?.takeIf { it.isNotEmpty() }?.let { month(it).takeIf { m -> m.isNotEmpty() }?.let { m -> " · $m" } } ?: "")
        "dropped" -> "Dropped"
        else -> if (g.date.isNotEmpty()) days(g.date).ifEmpty { "Out now" } else "No date yet"
    }

    /** `gdGuess` without the helper's reviews (piece 4): Nexi's expected score, 0 for a played or competitive game. */
    fun guess(g: Game): Double = if (rk.isPlayed(g) || g.comp) 0.0 else q.expected(g) ?: 0.0

    /** `gdGlance` (index.html 21936): release, length, hype, score, in that order for every game. */
    fun tiles(g: Game): List<Tile> {
        val p = rk.playedRec(g)
        val h = rk.hrsOf(g)
        val ev = Backup.num(lib["evening"], 4.0, 1.0, 12.0)
        val gs = guess(g)
        val critic = Backup.num(g["critic"], 0.0, -1e9, 1e9)
        val score = when {
            p != null && p.scored -> Tile(Queue.jsNum(p.real!!), "your score")
            g.comp -> Tile(Queue.DASH, "competitive")
            gs != 0.0 -> Tile(String.format(Locale.ROOT, "%.1f", gs), "likely score")
            critic != 0.0 && !critic.isNaN() -> Tile(Backup.str(g["critic"]).ifEmpty { Queue.jsNum(critic) }, "critics")
            else -> Tile(Queue.DASH, "not scored")
        }
        val hype = Backup.num(g["hype"], 0.0, -1e9, 1e9)
        return listOf(
            release(g),
            if (h != 0.0) Tile(Queue.jsNum(h) + "h", maxOf(1L, Rank.jsRound(h / ev).toLong()).toString() + " evenings")
            else Tile(Queue.DASH, if (g.comp) "no end" else "length"),
            Tile(if (hype != 0.0 && !hype.isNaN()) Queue.jsNum(hype) + "/10" else Queue.DASH, "hype"),
            score,
        )
    }

    /** The release tile: "TBA"; this year "14 Mar" + release/released; another year the year big and "14 Mar" under it. */
    private fun release(g: Game): Tile {
        val d = runCatching { LocalDate.parse(g.date) }.getOrNull() ?: return Tile("TBA", "no date yet")
        val dm = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
        val year = Instant.ofEpochMilli(now).atZone(zone).year
        return if (d.year == year) Tile(dm, if (g.isUpcoming(now)) "release" else "released") else Tile(d.year.toString(), dm)
    }

    /**
     * `gdNext` (index.html 22045): the one thing worth doing next. Score it, Leave a note and Duel come with piece 3 and
     * Phase 6 (no button that does nothing, the owner's Nov 10 answer), so a played or competitive game shows none yet.
     */
    fun next(g: Game): List<Next> {
        if (g.comp) return emptyList()
        return when (state(g)) {
            "playing" -> listOf(Next("finish", "I finished it"))
            "dropped" -> listOf(Next("start", "Pick it up again"))
            "backlog" -> listOf(Next("start", "Start playing"))
            "upcoming" -> listOf(Next("trailer", "Watch the trailer"))
            else -> emptyList()
        }
    }

    /** `shortDate` of a moment (`new Date(t).toLocaleDateString("en-CA")` first, as Your progress does). */
    private fun shortOf(t: Long): String = q.shortDate(JsDate.todayLocal(t, zone))

    /** `gdProgress`'s three steps (index.html 21951): out → playing → finished (or dropped). */
    fun steps(g: Game): List<Step> {
        val st = state(g)
        val logged = Backup.num(g["logged"], 0.0, 0.0, 100000.0)
        val plAt = Backup.num(g["plAt"], 0.0, 0.0, Double.MAX_VALUE).toLong()
        val p = rk.playedRec(g)
        val s1 = Step(if (st == "upcoming") "Coming" else "Out", if (g.date.isNotEmpty()) q.shortDate(g.date) else "TBA", if (st == "upcoming") "now" else "done")
        val s2 = Step(
            "Playing",
            when (st) {
                "playing" -> if (logged != 0.0) Queue.jsNum(logged) + "h in" else if (plAt != 0L) "since " + shortOf(plAt) else "now"
                "played", "dropped" -> if (logged != 0.0) Queue.jsNum(logged) + "h" else ""
                else -> ""
            },
            when (st) { "playing" -> "now"; "played", "dropped" -> "done"; else -> "" },
        )
        // the forecast's "~14 Mar" under Finished comes with Finish forecast (piece 3)
        val s3 = if (st == "dropped") Step("Dropped", "", "drop") else Step("Finished", if (st == "played") p?.t?.takeIf { it.isNotEmpty() }?.let(::month) ?: "" else "", if (st == "played") "done" else "")
        return listOf(s1, s2, s3)
    }

    /** How far along the track is lit (the steps done or now, 0-3): Your progress draws the line between two of them (v271). */
    fun lit(g: Game): Int = steps(g).count { it.c == "done" || it.c == "now" || it.c == "drop" }

    /**
     * The line under the track (`gdProgress`'s body), as [bold] parts: upcoming, backlog, played and dropped have one;
     * a playing game's Where was I card and Finish forecast come with piece 3. Null when there's none.
     */
    fun line(g: Game): List<Pair<String, Boolean>>? {
        val p = rk.playedRec(g)
        return when (state(g)) {
            "upcoming" -> {
                val d = runCatching { LocalDate.parse(g.date) }.getOrNull()
                if (g.date.isNotEmpty() && d != null) listOf("Comes out " to false, d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)) to true, " · " + days(g.date).lowercase(locale) to false)
                else listOf("No release date yet. I'll tell you when one appears." to false)
            }
            "backlog" -> if (g.date.isNotEmpty() && g.date <= JsDate.todayLocal(now, zone)) listOf("Out since " to false, q.shortDate(g.date) to true, ". Ready when you are." to false) else listOf("Ready when you are." to false)
            "played" -> {
                val m = p?.t?.takeIf { it.isNotEmpty() }?.let(::month).orEmpty()
                (if (m.isNotEmpty()) listOf("Finished in " to false, m to true, ". " to false) else listOf("Finished. " to false)) +
                    (if (p != null && p.scored) listOf("You gave it " to false, Queue.jsNum(p.real!!) to true, "." to false) else listOf("Not scored yet." to false))
            }
            "dropped" -> listOf("You put this one down." to false)
            else -> null
        }
    }

    /** `gdTagData` (index.html 22003): genres without repeats, then Steam players' tags (not generic, not a genre), 8 at most. */
    fun tags(g: Game): Tags {
        val base = Rank.platList(g["genres"]).let { l -> l.filterIndexed { i, t -> l.indexOfFirst { Queue.gdKey(it) == Queue.gdKey(t) } == i } }
        val seen = HashSet(base.map { Queue.gdKey(it) })
        val st = (g["stTags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.filter { t ->
            val k = Queue.gdKey(t)
            if (k.isEmpty() || k in seen || k in GENERIC) false else { seen.add(k); true }
        }.take(8)
        return Tags(base, st) { t -> val k = Queue.gdKey(t); sets.count { k in it } }
    }

    private val sets: List<Set<String>> by lazy { lib.games.map(Queue::genreSet) }

    /** The About text (`g.desc || g.about`), shown as plain words. */
    fun about(g: Game): String = Backup.str(g["desc"]).ifEmpty { Backup.str(g["about"]) }

    /** The screenshots (`g.shots`, five at most): each one's small copy and big one. */
    fun shots(g: Game): List<Pair<String, String>> = (g["shots"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val u = Backup.str(o["u"])
        val big = Backup.str(o["b"]).ifEmpty { u }
        val small = Backup.str(o["t"]).ifEmpty { u }
        if (big.isEmpty() && small.isEmpty()) null else small.ifEmpty { big } to big.ifEmpty { small }
    }.take(5)

    companion object {
        val GD_STATE = mapOf("upcoming" to "Coming", "backlog" to "Waiting", "playing" to "Playing now", "played" to "Finished", "dropped" to "Dropped")

        /** en-GB's short months cut to three letters, as the release tile's `.slice(0, 3)` does ("Sep"). */
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        /**
         * `ST_GENERIC` (index.html 20085, from android-gen): it holds the words as written and is checked against
         * `gdKey(t)`, so only one-word entries ever match (a web quirk kept on purpose: multi-word ones never hide a tag).
         */
        private val GENERIC: Set<String> by lazy { io.github.shamlawy.playnext.gen.Web.ST_GENERIC.toSet() }

        /** The swipe's order (`sortGames(allGames())`): every game, played and dropped too, in the Queue's order. */
        fun order(q: Queue, sort: String, moved: Map<String, Double>): List<Game> = q.sort(q.lib.games, sort, moved)
    }
}
