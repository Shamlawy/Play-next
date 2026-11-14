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
class Queue(val lib: Library, val rk: Rank, private val now: Long, val zone: ZoneId) {
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

    /**
     * Shuffle's pool (`qdShuffle`, index.html 8810): the games not played or dropped, only those out now when any are,
     * and never [last] twice running unless it's the only one.
     */
    fun shufflePool(last: String?): List<Game> {
        val live = games.filter(::waiting)
        val base = live.filter(::isOutNow).ifEmpty { live }
        return base.filter { it.id != last || base.size < 2 }
    }

    /**
     * The Shadow of the Backlog (`bossNow`, index.html 13912): its HP is the hours (20 when not known, `+g.hours || 20`)
     * of every game that isn't competitive, played or dropped and is in the backlog, playing or out; its bar's top is
     * the most it has been (`S.fxBossMax`), at least 1 (`window.bossHP`, 13916).
     */
    val boss: Pair<Double, Double> by lazy {
        val hp = games.filter { !it.comp && !rk.isPlayed(it) && it.rawState != "played" && !rk.isDropped(it) && (it.state == "backlog" || it.rawState == "playing" || isOutNow(it)) }
            .sumOf { g -> Backup.num(g["hours"], 0.0, -1e9, 1e9).let { if (it == 0.0) 20.0 else it } }
        hp to maxOf(hp, Backup.num(lib["fxBossMax"], 0.0, 0.0, 1e9)).let { if (it == 0.0) 1.0 else it }
    }

    /** `qNow()`: the saved tab when it still exists, else All (a genre or "like" tab comes back with the game page, Phase 2). */
    fun tabNow(saved: String): String =
        if (saved in TABS || (saved.startsWith("L:") && lists.any { "L:" + it.id == saved }) || (saved.startsWith("G:") && gdKey(saved.drop(2)).isNotEmpty())) saved else "all"

    /** How many games [tab] holds: [counts], and a genre tab (`G:`, the game page's genre tap) counted when asked. */
    fun count(tab: String): Int = counts[tab] ?: if (tab.startsWith("G:")) genreCounts.getOrPut(tab) { list(tab).size } else 0

    /** A genre tab's count, worked out once per Queue (the bar asks on every redraw). */
    private val genreCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

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
        // `G:<genre>` (v234's `extend("queueList")`): every game, in any state, whose genres or Steam tags hold it
        tab.startsWith("G:") -> gdKey(tab.drop(2)).let { k -> games.filter { k in genreSet(it) } }
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

    private fun critic(g: Game) = Backup.num(g["critic"], 0.0, -1e9, 1e9)

    /** `rvBias()` (index.html 20441): your score minus the critics', over your scored games with a critic score. */
    private val rvRows: List<Pair<Double, Game>> by lazy { playedRows.filter { critic(it.second) > 0 }.map { (p, g) -> p.real!! - critic(g) / 10 to g } }
    private val rvMean: Double by lazy { if (rvRows.isEmpty()) 0.0 else rvRows.sumOf { it.first } / rvRows.size }
    private val rvGenre: Map<String, Double> by lazy {
        val by = LinkedHashMap<String, MutableList<Double>>()
        for ((d, g) in rvRows) for (gn in Rank.platList(g["genres"])) by.getOrPut(gn) { mutableListOf() } += d
        by.filter { it.value.size >= 2 }.mapValues { it.value.sum() / it.value.size }
    }

    /** `rvFor(g)`: the guess [v], the critics' score [mc] (0: none), Steam's players [st] (30+ reviews), the reasons and how sure. */
    class RvFor(val v: Double, val mc: Double, val st: JsonObject?, val why: List<String>, val sure: String)

    /**
     * `rvFor(g)` (index.html 20450): Nexi's guess from the critics (`g.rv.mc` or `g.critic`, Steam's players `g.rv.st`
     * with 30+ reviews, the answer this run's [Reviews] fetched first), moved by how you land against them, blended with
     * [expected], and why in plain words. Null for a played or competitive game, or one with no critic score.
     */
    fun rvFor(g: Game): RvFor? {
        if (g.comp || rk.isPlayed(g)) return null
        val rv = Reviews.of(g)
        val mc = Backup.num(rv?.get("mc"), 0.0, -1e9, 1e9).takeIf { it != 0.0 } ?: critic(g)
        val st = (rv?.get("st") as? JsonObject)?.takeIf { Backup.num(it["n"], 0.0, 0.0, 1e12) >= 30 && it["p"] != null && it["p"] !is kotlinx.serialization.json.JsonNull }
        val stTen = st?.let { maxOf(1.0, minOf(10.0, 1 + .085 * Backup.num(it["p"], 0.0, -1e9, 1e9))) }
        if (mc == 0.0 && stTen == null) return null
        val crit = if (mc != 0.0 && stTen != null) mc / 10 * .7 + stTen * .3 else if (mc != 0.0) mc / 10 else stTen!!
        val n = rvRows.size
        var shift = 0.0
        var how = ""
        if (n >= 3) {
            val gs = Rank.platList(g["genres"]).mapNotNull { rvGenre[it] }
            val gm = if (gs.isNotEmpty()) gs.sum() / gs.size else null
            shift = (if (gm == null) rvMean else .5 * rvMean + .5 * gm) * (n / (n + 3.0))
            how = if (kotlin.math.abs(shift) >= .3) "you usually score ${String.format(java.util.Locale.ROOT, "%.1f", kotlin.math.abs(shift))} ${if (shift < 0) "below" else "above"} the critics${if (gm != null) " on games like this" else ""}"
            else "you usually agree with the critics"
        }
        val e = expected(g)
        val v1 = crit + shift
        val v = maxOf(1.0, minOf(10.0, Rank.jsRound((if (e != null) v1 * .65 + e * .35 else v1) * 10) / 10))
        // a genre shared with one of your top 6 ranked games
        val tg = Rank.platList(g["genres"]).map { it.lowercase() }.toSet()
        // lazily: only the first six ranked games are ever looked up (second-look on #747)
        val like = rk.table.order.asSequence().mapNotNull { id -> lib.games.firstOrNull { it.id == id } }.take(6)
            .firstOrNull { f -> Rank.platList(f["genres"]).any { it.lowercase() in tg } }
        val why = listOfNotNull(
            how.ifEmpty { null },
            e?.let { "your hype points to ${String.format(java.util.Locale.ROOT, "%.1f", it)}" },
            like?.let { "it shares a genre with your #${(rk.table.pos[it.id] ?: 0) + 1} ${it.title}" },
        )
        val sure = if (n >= 6) "" else if (n >= 3) "a rough guess" else "a first guess (score a few games that have critic scores and I'll get sharper)"
        return RvFor(v, mc, st, why, sure)
    }

    /** `rvFor(g).v`: Nexi's guess from the critics, or null. */
    fun rvGuess(g: Game): Double? = rvFor(g)?.v

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
        val strip = { s: String -> s.replace(TRAIL_NUM, "") }
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

    // ---- the open card (v299/v300, android/parity/1-map/2-card.md) ----

    /** `countdown(g)` as the card writes it: [value, label] ("12", "days away" / "Out", "available now" / "TBA", "no date yet"). */
    fun countdownWords(g: Game): Pair<String, String> {
        if (g.date.isEmpty()) return "TBA" to "no date yet"
        // a date JavaScript can't read counts as out (NaN days), as the front's headline does
        val at = JsDate.utcMidnight(g.date) ?: return "Out" to "available now"
        val n = Math.ceil((at - now) / JsDate.DAY.toDouble()).toInt()
        return if (n > 0) "$n" to (if (n == 1) "day away" else "days away") else "Out" to (if (n == 0) "out today" else "available now")
    }

    /** The card's place words: "Next up", else "3rd in queue" (the front) or "3rd in the queue" (the back). */
    fun spot(n: Int, the: Boolean): String = if (n == 1) "Next up" else ordinal(n) + if (the) " in the queue" else " in queue"

    /** `ord(n)`: 1st, 2nd, 3rd, 4th, 11th, 21st. */
    fun ordinal(n: Int): String {
        val v = n % 100
        val s = listOf("th", "st", "nd", "rd")
        val a = (v - 20) % 10
        return n.toString() + (if (a in 1..3) s[a] else if (a >= 0) "th" else if (v in 1..3) s[v] else "th")
    }

    /** `S.evening || 2`: the back's hours a night (the rest of the app's default is 4: kept as the web has it). */
    val evening: Double get() = Backup.num(lib["evening"], 2.0, 0.0, 24.0).takeIf { it != 0.0 } ?: 2.0

    /** The back's "released 12 Mar" / "out 12 Mar" / "no date yet". */
    fun whenWords(g: Game): String = if (g.date.isEmpty()) "no date yet" else (if (countdown(g) == null) "released " else "out ") + shortDate(g.date)

    /** `qbAreas(g)`: your eight area scores, else Nexi's guess from the games you've scored (a shared genre counts 3×), else null. */
    class Areas(val vals: List<Double>, val guess: Boolean) {
        /** The controller's number: your score, or the mean × 10 (rounded) for a guess. */
        fun big(score: Int): Int {
            val on = vals.filter { it > 0 }
            val mean = if (on.isEmpty()) 0.0 else on.sum() / on.size
            return if (guess) Rank.jsRound(mean * 10).toInt() else score.takeIf { it != 0 } ?: Rank.jsRound(mean * 10).toInt()
        }
    }

    fun areas(g: Game): Areas? {
        val area = { x: Game, k: String -> Backup.num((x["mine"] as? JsonObject)?.get(k), 0.0, 0.0, 10.0) }
        val own = AREAS.map { area(g, it.first) }
        if (own.any { it > 0 }) return Areas(own, false)
        val gs = Rank.platList(g["genres"]).map { it.lowercase() }.toSet()
        val rated = games.filter { x -> x.id != g.id && x["mine"] is JsonObject && AREAS.any { (k) -> mine(x, k) > 0 } }
        if (rated.isEmpty()) return null
        val vals = AREAS.map { (k) ->
            var s = 0.0
            var w = 0
            for (x in rated) {
                val v = area(x, k)
                if (v == 0.0) continue
                val wt = if (Rank.platList(x["genres"]).any { it.lowercase() in gs }) 3 else 1
                s += v * wt
                w += wt
            }
            if (w != 0) Rank.jsRound(s / w * 10) / 10 else 0.0
        }
        return if (vals.any { it > 0 }) Areas(vals, true) else null
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
        /** A trailing " 2" (a version in a name), made once: [stat] runs per cover in Character select (Phase 1 check B4). */
        private val TRAIL_NUM = Regex(" \\d+$")

        /** `gdKey` (index.html 21999): a tag as the genre tap compares it, lower case, letters and digits only. */
        fun gdKey(t: String): String = t.lowercase(java.util.Locale.ROOT).replace(NOT_KEY, "")
        private val NOT_KEY = Regex("[^a-z0-9]+")

        /** `gdGenreSet` (index.html 22000): a game's genres and Steam players' tags (`g.stTags`), by [gdKey]. */
        fun genreSet(g: Game): Set<String> =
            (Rank.platList(g["genres"]) + (g["stTags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }).map(::gdKey).toSet()

        /** A number as JavaScript writes it: 12 not 12.0, 6.5 as is. */
        fun jsNum(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    }
}
