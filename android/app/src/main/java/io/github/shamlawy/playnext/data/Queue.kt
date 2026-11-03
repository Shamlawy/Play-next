package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The Queue's list maths, ported line by line from index.html (web v327, android/parity/1-map/1-list.md §2-§5, §8), so
 * both apps put the same games under the same tab in the same order with the same words: which games each tab shows
 * (`qNow`, `qCounts`, `queueList`, `isOutNow`), the orders (`sortGames`, `SORTS`, `QD_GROUPS`, the sort pill), a row's
 * headline, stub and body (`qkStat`), its progress line (`qsProg`), the dates (`shortDate`, `countdown`), On deck under a
 * tab (`deckShown`, `deckHead`, `deckWhy`, `dzMeta`) and the Dusty mark (`dustDays`, `dustLvl`). Pure: the clock and the
 * zone are passed in; GoldenTest checks it against the web app's own answers.
 */
class Queue(val lib: Library, val rk: Rank, private val now: Long, private val zone: ZoneId) {
    private val games = lib.games

    /** `S.lists`: your lists, each `{id, n, e}` (name, emoji). */
    val lists: List<QList> = Lists.of(lib)

    /** `new Date(new Date().toDateString())`: today's midnight on the phone. */
    /** True while [t] is in this Queue's stretch: the same phone day and the same UTC day, in the same zone. */
    fun covers(t: Long, z: ZoneId) = z == zone && JsDate.todayLocal(t, z) == today && Math.floorDiv(t, JsDate.DAY) == Math.floorDiv(now, JsDate.DAY)

    /** Works out what every Queue screen needs first (the counts, On deck, the rank table), so drawing it only reads. */
    fun warm() { counts; deck("all"); rk.elo; rk.table }

    private val day0 = JsDate.today0(now, zone)
    private val today = JsDate.todayLocal(now, zone)

    /** `isOutNow` (index.html 7343): backlog or playing, or a date (UTC midnight) before today's local midnight. */
    fun isOutNow(g: Game): Boolean {
        if (g.state == "backlog" || g.rawState == "playing") return true
        return g.date.isNotEmpty() && (JsDate.utcMidnight(g.date)?.let { it < day0} ?: false)
    }

    /** `qWaiting()`: not played and not dropped. */
    fun waiting(g: Game) = !rk.isPlayed(g) && !rk.isDropped(g)

    /** `qNow()`: the saved tab when it still exists, else All (a genre or "like" tab comes back with the game page, Phase 2). */
    fun tabNow(saved: String): String =
        if (saved in TABS || (saved.startsWith("L:") && lists.any { "L:" + it.id == saved })) saved else "all"

    /** `qCounts()`: how many games each tab holds. */
    val counts: Map<String, Int> by lazy {
        val w = games.filter(::waiting)
        val c = LinkedHashMap<String, Int>()
        c["all"] = games.size
        c["out"] = w.count(::isOutNow)
        c["soon"] = w.count { !isOutNow(it) }
        c["done"] = games.count(rk::isPlayed)
        for (l in lists) c["L:" + l.id] = games.count { l.id in listsOf(it) }
        c
    }

    /** `queueList()`: the games under [tab] (as [tabNow] gives it), in the backup's order. */
    fun list(tab: String): List<Game> = when {
        tab.startsWith("L:") -> games.filter { tab.removePrefix("L:") in listsOf(it) }
        tab == "all" -> games
        tab == "done" -> games.filter(rk::isPlayed)
        tab == "soon" -> games.filter { waiting(it) && !isOutNow(it) }
        else -> games.filter { waiting(it) && isOutNow(it) }
    }

    private fun listsOf(g: Game): List<String> = (g["lists"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    /** A tab's word (`qdTabName`): All / Out / Coming / Played, a list's name. */
    fun tabName(tab: String): String = TAB_WORD[tab] ?: lists.firstOrNull { "L:" + it.id == tab }?.n ?: tab.replace(Regex("^\\w:"), "")

    // ---- the orders ----

    /** `g.ord || 0` (a missing or zero place sorts first, as the web's manual order does). */
    fun ord(g: Game, moved: Map<String, Double> = emptyMap()): Double = moved[g.id] ?: Backup.num(g["ord"], 0.0, -Double.MAX_VALUE, Double.MAX_VALUE)

    /** `sortGames(list)` with `S.sort` = [key] (unknown keys are your own order). [moved] holds the practice copy's places. */
    fun sort(list: List<Game>, key: String, moved: Map<String, Double> = emptyMap()): List<Game> {
        val o = { g: Game -> ord(g, moved) }
        fun byNum(f: (Game) -> Double): List<Game> = list.sortedWith { x, y ->
            val d = f(y) - f(x)
            if (d != 0.0 && !d.isNaN()) sign(d) else sign(o(x) - o(y))
        }
        return when {
            key == "hype" -> byNum { hype(it) }
            key == "expect" -> byNum { expected(it) ?: (hype(it) - 0.01) }
            key == "duel" -> byNum { rk.elo[it.id].let { v -> if (v == null || v == 0.0) 1500.0 else v } }
            key == "ondeck" -> byNum { rk.deck[it.id] ?: -1.0 }
            key == "overall" -> byNum(::avgScore)
            AREAS.any { it.first == key } -> byNum { mine(it, key).takeIf { v -> v > 0 } ?: -1.0 }
            key == "longest" -> byNum { rk.hrsOf(it) }
            key == "shortest" -> byNum { rk.hrsOf(it).let { h -> if (h != 0.0) -h else -99999.0 } }
            key == "date" -> {
                val tier = { g: Game -> if (g.date.isEmpty()) 1 else if ((JsDate.utcMidnight(g.date) ?: Long.MIN_VALUE) >= now) 0 else 2 }
                list.sortedWith { x, y ->
                    val tx = tier(x)
                    val ty = tier(y)
                    if (tx != ty) return@sortedWith tx - ty
                    if (tx == 1) return@sortedWith 0
                    // a date it can't read sits in the past tier (as the web's) as the oldest, so the order stays total
                    // (a 0 against everything breaks TimSort's contract: it can throw on 32+ games)
                    val dx = JsDate.utcMidnight(x.date) ?: Long.MIN_VALUE
                    val dy = JsDate.utcMidnight(y.date) ?: Long.MIN_VALUE
                    if (tx == 0) dx.compareTo(dy) else dy.compareTo(dx)
                }
            }
            else -> list.sortedWith { x, y -> sign(o(x) - o(y)) }
        }
    }

    private fun sign(d: Double) = if (d > 0) 1 else if (d < 0) -1 else 0

    fun hype(g: Game): Double = Backup.num(g["hype"], 0.0, -Double.MAX_VALUE, Double.MAX_VALUE)

    /** `g.mine[key]`, 0 when missing. */
    fun mine(g: Game, key: String): Double = Backup.num((g["mine"] as? JsonObject)?.get(key), 0.0, -Double.MAX_VALUE, Double.MAX_VALUE)

    /** `avgScore`: the mean of your positive area scores, -1 when there are none. */
    fun avgScore(g: Game): Double = AREAS.map { mine(g, it.first) }.filter { it > 0 }.let { if (it.isEmpty()) -1.0 else it.sum() / it.size }

    /** `myScore` in renderQueue: the same mean × 10, rounded, 0 when none. */
    fun myScore(g: Game): Int = avgScore(g).let { if (it < 0) 0 else Rank.jsRound(it * 10).toInt() }

    /** `playedRows()`: every played record whose game exists and is scored, with its game. */
    private val playedRows: List<Pair<Played, Game>> by lazy { rk.played.mapNotNull { p -> rk.find(p.id)?.takeIf { p.scored }?.let { p to it } } }

    /** `hypeBias()` and `genrePattern()` → `expected(g)`: your hype, moved by how far off you've been before. */
    private val hypeRows by lazy { playedRows.filter { hype(it.second) > 0 } }
    private val biasMean: Double? by lazy { hypeRows.takeIf { it.isNotEmpty() }?.map { it.first.real!! - hype(it.second) }?.average() }
    private val genreMeans: Map<String, Double> by lazy {
        val by = LinkedHashMap<String, MutableList<Double>>()
        for ((p, g) in hypeRows) for (gn in Rank.platList(g["genres"])) by.getOrPut(gn) { mutableListOf() } += p.real!! - hype(g)
        by.filter { it.value.size >= 2 }.mapValues { it.value.sum() / it.value.size }
    }

    fun expected(g: Game): Double? {
        val b = biasMean ?: return null
        val h = hype(g)
        if (h == 0.0) return null
        val hit = Rank.platList(g["genres"]).mapNotNull { genreMeans[it] }
        val shift = if (hit.isNotEmpty()) hit.sum() / hit.size else b
        return maxOf(1.0, minOf(10.0, Rank.jsRound((h + shift) * 10) / 10))
    }

    /** The small pill a scored order adds to each row (`sortVal`): null when the order has none. */
    fun sortValue(g: Game, key: String): String? {
        val trim = { v: Double -> jsNum(Rank.jsRound(v * 10) / 10) }
        return when {
            AREAS.any { it.first == key } -> mine(g, key).let { if (it > 0) trim(it) else DASH }
            key == "overall" -> avgScore(g).let { if (it > 0) trim(it) else DASH }
            key == "hype" -> hype(g).let { if (it != 0.0) trim(it) else DASH }
            key == "expect" -> expected(g)?.let(trim) ?: DASH
            key == "duel" -> jsNum(Rank.jsRound(rk.elo[g.id].let { v -> if (v == null || v == 0.0) 1500.0 else v }))
            else -> null
        }
    }

    // ---- a row's words ----

    /** `countdown(g)`: days until the date (a number), "TBA" without one, else out. */
    fun countdown(g: Game): Int? {
        val at = JsDate.utcMidnight(g.date) ?: return null
        val n = Math.ceil((at - now) / JsDate.DAY.toDouble()).toInt()
        return if (n > 0) n else null
    }

    /** `shortDate(d)`: "13 Sept", with "27" when the year isn't this one; "" for no date or one JavaScript can't read. */
    fun shortDate(d: String): String {
        val at = JsDate.utcMidnight(d) ?: return ""
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val s = "${day.dayOfMonth} ${MONTHS[day.monthValue - 1]}"
        return if (day.year != Instant.ofEpochMilli(now).atZone(zone).year) s + " " + (day.year % 100).toString().padStart(2, '0') else s
    }

    private fun year(d: String): String = JsDate.utcMidnight(d)?.let { Instant.ofEpochMilli(it).atZone(zone).year.toString() } ?: ""

    /** `qkStat(g, countdown, myScore, n)`: the kind, its word, the headline, the ticket's two halves and whether it tore. */
    fun stat(g: Game): Stat {
        val hrs = rk.hrsOf(g)
        val lg = Backup.num(g["logged"], 0.0, 0.0, 1e5)
        val day = shortDate(g.date)
        val yr = year(g.date)
        val hb = (if (hrs != 0.0) jsNum(hrs) + " h" else DASH) to "to beat"
        val strip = { s: String -> s.replace(Regex(" \\d+$"), "") }
        rk.playedRec(g)?.let { p ->
            val runs = Backup.num(p.o["runs"], 1.0, 1.0, 99.0).toInt()
            val v = myScore(g).takeIf { it != 0 } ?: if (p.scored) Rank.jsRound(p.real!! * 10).toInt() else 0
            val head = when (runs) { 1 -> "Finished"; 2 -> "Played twice"; else -> "Played $runs times" }
            return Stat(Kind.Played, "Played", head, (if (v != 0) v.toString() else DASH) to "my score", "$runs×" to "played", true)
        }
        if (g.rawState == "playing") {
            val pc = if (hrs != 0.0) minOf(99, Rank.jsRound(lg / hrs * 100).toInt()) else 0
            val left = if (hrs != 0.0) maxOf(0, Rank.jsRound(hrs - lg).toInt()) else 0
            val head = if (hrs != 0.0) (if (left != 0) "$left h left" else "Nearly done") else "Playing now"
            val stub = if (hrs != 0.0) "$pc%" to "done" else "${Rank.jsRound(lg).toInt()} h" to "played"
            return Stat(Kind.Playing, "Playing", head, stub, if (hrs != 0.0) "$left h" to "left" else hb, true)
        }
        if (g.rawState == "dropped") return Stat(Kind.Dropped, "Dropped", "Set aside", "${Rank.jsRound(lg).toInt()} h" to "played", hb, true)
        if (g.date.isEmpty()) return Stat(Kind.Coming, "Coming", "No date yet", "TBA" to "date", hb, false)
        countdown(g)?.let { n ->
            return Stat(Kind.Coming, "Coming", if (n == 1) "Out tomorrow" else "Out in $n days", strip(day) to yr, "$n" + (if (n == 1) " day" else " days") to "to go", false)
        }
        val head = if (g.isFresh(now, zone)) "Just out" else "Ready to play"
        return Stat(Kind.Out, "Out now", head, if (day.isNotEmpty()) strip(day) to yr else "Out" to "now", hb, true)
    }

    /** `qsProg(g, st)`: how far along the foot's line is (0-1). */
    fun progress(g: Game, st: Stat): Float {
        val hrs = rk.hrsOf(g)
        return when (st.kind) {
            Kind.Played -> 1f
            Kind.Playing, Kind.Dropped -> if (hrs != 0.0) fixed3(minOf(1.0, Backup.num(g["logged"], 0.0, 0.0, 1e5) / hrs)) else 0f
            Kind.Coming -> JsDate.utcMidnight(g.date)?.let { fixed3(maxOf(.04, minOf(1.0, 1 - (it - now) / (182 * 864e5)))) } ?: 0f
            Kind.Out -> 0f
        }
    }

    private fun fixed3(v: Double) = (Math.round(v * 1000) / 1000.0).toFloat()

    /** A game still `upcoming` whose date has passed: its date is struck through (folded rows). */
    fun lateDate(g: Game): Boolean = g.state == "upcoming" && (JsDate.utcMidnight(g.date)?.let { it < day0 } ?: false)

    // ---- On deck ----

    /** `deckShown(n)`: On deck's top [n] under [tab] (All: the whole On deck; other tabs: the same scores among its games). */
    fun deck(tab: String, n: Int = 3): List<Game> = deckMemo.getOrPut(tab to n) { deckOf(tab, n) }

    /** One list per tab while this Queue lives, so On deck gets the same object back and Compose can skip it. */
    private val deckMemo = HashMap<Pair<String, Int>, List<Game>>()

    private fun deckOf(tab: String, n: Int): List<Game> {
        if (tab == "all") return rk.onDeck(n)
        val ids = list(tab).map { it.id }.toHashSet()
        val s = rk.deck
        return Rank.jsKeys(s.keys).filter { it in ids }.sortedWith { a, b -> s[b]!!.compareTo(s[a]!!).let { if (s[b]!! == s[a]!!) 0 else it } }
            .take(n).mapNotNull { rk.find(it) }
    }

    /** `deckHead()`: "On deck", " · <tab>" for any tab but All. */
    fun deckHead(tab: String) = "On deck" + if (tab == "all") "" else " · " + tabName(tab)

    /** `deckWhy(g)`: why this one, in a few words (or the first two genres). */
    fun deckWhy(g: Game): String {
        val tg = Rank.platList(g["genres"]).map { it.lowercase() }.toSet()
        val like = rk.ranked.take(6).firstOrNull { f -> Rank.platList(f["genres"]).any { it.lowercase() in tg } }
        val bits = mutableListOf<String>()
        if (like != null) bits += "like your #" + ((rk.table.pos[like.id] ?: 0) + 1) + " " + like.title
        val plat = Backup.str(lib["curPlat"]).ifEmpty { "Switch" }
        if (rk.onPlat(g) && plat != "any") bits += "on $plat"
        if (g.rawState == "playing") bits += "you've started it"
        else if (!(g.rawState == "backlog" || g.isFresh(now, zone))) bits += if (g.date.isNotEmpty()) "out " + shortDate(g.date) else "not out yet"
        return bits.joinToString(" · ").ifEmpty { Rank.platList(g["genres"]).take(2).joinToString(" · ") }
    }

    /** `dzMeta(g)`: "≈ 12h", "on Switch", "out 13 Sept" / "Out now" (each [lead word, value]). */
    fun deckMeta(g: Game): List<Pair<String, String>> {
        val m = mutableListOf<Pair<String, String>>()
        rk.hrsOf(g).takeIf { it != 0.0 }?.let { m += "≈" to jsNum(minOf(1000.0, maxOf(0.0, it))) + "h" }
        Rank.platList(g["platforms"]).firstOrNull()?.let { m += "on" to it }
        if (g.date.isNotEmpty()) {
            if (JsDate.days(g.date, now).startsWith("Out")) m += "" to "Out now" else m += "out" to shortDate(g.date)
        }
        return m
    }

    // ---- the Dusty mark ----

    /** `dustDays(g)`: how long an out game has sat untouched (0 for anything that can't gather dust). */
    fun dustDays(g: Game): Int {
        if (Backup.truthy(lib["dustOff"]) || g.comp || rk.isPlayed(g) || rk.isDropped(g) || g.rawState == "playing") return 0
        val out = g.rawState == "backlog" || (g.date.isNotEmpty() && g.date <= today)
        if (!out) return 0
        var since = noon(Backup.str(g["added"]))
        if (g.date.isNotEmpty() && g.date <= today) since = maxOf(since, noon(g.date))
        // the last Steam session (`sgLast`) comes with Phase 9
        since = maxOf(since, Backup.num(g["dustOff"], 0.0, 0.0, Double.MAX_VALUE).toLong())
        return if (since != 0L) Math.floorDiv(now - since, JsDate.DAY).toInt() else 0
    }

    /** `Date.parse(d + "T12:00:00")`: the day's local noon, 0 for anything else. */
    private fun noon(d: String): Long = if (!YMD.matches(d)) 0 else runCatching { LocalDate.parse(d).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli() }.getOrDefault(0)

    /** `dustLvl(g)`: 60 days 1, 120 days 2, 240 days 3 (the cobweb). */
    fun dustLevel(g: Game): Int = dustDays(g).let { if (it >= 240) 3 else if (it >= 120) 2 else if (it >= 60) 1 else 0 }

    enum class Kind { Played, Playing, Dropped, Coming, Out }

    /** `qkStat`'s answer: [stub] and [body] are the ticket's two halves (value, label); [torn] its stub hangs torn. */
    data class Stat(val kind: Kind, val word: String, val head: String, val stub: Pair<String, String>, val body: Pair<String, String>, val torn: Boolean)

    data class QList(val id: String, val n: String, val e: String)

    companion object {
        const val DASH = "—"
        val TABS = listOf("all", "out", "soon", "done")
        val TAB_WORD = mapOf("all" to "All", "out" to "Out", "soon" to "Coming", "done" to "Played")

        /** `RATINGS`: the eight areas. */
        val AREAS = listOf(
            "story" to "Story", "music" to "Music", "gameplay" to "Gameplay", "replay" to "Replayability",
            "visuals" to "Visuals", "world" to "World", "chars" to "Characters", "pace" to "Pacing",
        )

        /** `SORTS` (index.html 7323): each order's name. */
        val SORTS = linkedMapOf(
            "date" to "Soonest", "hype" to "Most hyped", "expect" to "What I'd expect", "ondeck" to "On deck (my pick)",
            "duel" to "Duel order", "overall" to "My rating", "story" to "Story", "music" to "Music", "gameplay" to "Gameplay",
            "replay" to "Replayability", "visuals" to "Visuals", "world" to "World", "chars" to "Characters", "pace" to "Pacing",
            "longest" to "Longest", "shortest" to "Shortest",
        )

        /** `QD_GROUPS`: the Order menu's three groups. */
        val GROUPS = listOf(
            "Order" to listOf("manual", "date", "hype", "ondeck", "shortest", "longest"),
            "Your scores" to listOf("overall", "expect", "duel"),
            "One area" to listOf("story", "music", "gameplay", "replay", "visuals", "world", "chars", "pace"),
        )

        /** An order's name: "My order", else its SORTS name ("Sorted" for one this app doesn't know). */
        fun sortName(k: String) = if (k == "manual") "My order" else SORTS[k] ?: "Sorted"

        /** en-GB's short months as Chrome writes them ("Sept", checked against Chromium). */
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
        private val YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        /** A number as JavaScript writes it: 12 not 12.0, 6.5 as is. */
        fun jsNum(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    }
}
