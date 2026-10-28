package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The web app's own maths for what you've played and what to play next, ported line by line so both apps give the same
 * answers (GoldenTest checks them against index.html): played records, duels, the Elo table, the ranking (`rankTable`),
 * On deck (`onDeckScores`), hours to beat (`hrsOf`) and the tiers. Pure: the clock and the zone are passed in.
 */
class Played(val o: JsonObject) {
    // each field read once, as the record comes in: the rank table goes over them 90 times
    /** `p.id`, or null when the record has none (a missing id never equals a game's, as in JavaScript). */
    val id: String? = (o["id"] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
    val real: Double? = (o["real"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    val again: Boolean = Backup.truthy(o["again"])
    val t: String = Backup.str(o["t"])

    /** `scored(p)` (index.html 3504): a real score, or a deliberate 0 (`z: 1`), and not "Don't remember". */
    val scored: Boolean = run {
        if (Backup.truthy(o["dk"])) return@run false
        val r = real ?: return@run false
        r > 0 || (r == 0.0 && (o["z"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull == 1.0)
    }
}

class Duel(val o: JsonObject) {
    val a: String? = (o["a"] as? JsonPrimitive)?.content
    val b: String? = (o["b"] as? JsonPrimitive)?.content
    val w: String = Backup.str(o["w"])
    val t: String = Backup.str(o["t"])

    /** `duelResult(d)` (index.html 13029): for side a, a win 1, a slight edge .75, even .5. */
    val result: Double = run {
        if (w.isEmpty() || w == "draw") return@run .5
        val s = if (Backup.truthy(o["e"])) .75 else 1.0
        if (w == a) s else 1 - s
    }
}

/** `rankTable()`: the order, each game's position, its pulled score [r], its duel count [n] and its own score [sc]. */
class RankTable(val order: List<String>, val pos: Map<String, Int>, val r: Map<String, Double>, val n: Map<String, Int>, val sc: Map<String, Double>)

class Rank(val lib: Library, private val now: Long, private val zone: ZoneId) {
    private val games = lib.games
    val played: List<Played> = lib.played
    val duels: List<Duel> = lib.duels

    /** Each game by its id and each id's played record, looked up instead of searched (the first wins, as a search finds). */
    private val byId = HashMap<String, Game>().also { m -> for (g in games) m.putIfAbsent(g.id, g) }
    private val recs = HashMap<String, Played>().also { m -> for (p in played) p.id?.let { m.putIfAbsent(it, p) } }

    fun isPlayed(g: Game) = g.id in recs
    fun playedRec(g: Game) = recs[g.id]
    fun isDropped(g: Game) = g.rawState == "dropped"
    fun find(id: String?) = if (id == null) null else byId[id]

    /** `eloTable()` (index.html 7928): everyone starts at 1500, each decided duel moves the two by K 24. */
    val elo: Map<String, Double> by lazy {
        val t = LinkedHashMap<String, Double>()
        for (g in games) t[g.id] = 1500.0
        for (d in duels) {
            val a = d.a
            val b = d.b
            if (d.w.isEmpty() || a == null || b == null || t[a] == null || t[b] == null) continue
            val ea = 1 / (1 + StrictMath.pow(10.0, (t[b]!! - t[a]!!) / 400))
            val sa = d.result
            t[a] = t[a]!! + ELO_K * (sa - ea)
            t[b] = t[b]!! + ELO_K * ((1 - sa) - (1 - ea))
        }
        t
    }

    /** `rankTable()` (index.html 13046): each scored game starts at its score, duels pull, the score pulls back. */
    val table: RankTable by lazy {
        val pool = games.filter { g -> !g.comp && playedRec(g)?.scored == true }
        // each game a place in plain arrays: 90 rounds of sums with no map lookups (the same sums in the same order)
        val ix = HashMap<String, Int>()
        for (g in pool) ix.getOrPut(g.id) { ix.size }
        val at = IntArray(pool.size) { ix[pool[it].id]!! }
        val sc = DoubleArray(ix.size)
        val r = DoubleArray(ix.size)
        val n = IntArray(ix.size)
        pool.forEachIndexed { i, g ->
            val v = playedRec(g)!!.real!!
            sc[at[i]] = v
            r[at[i]] = v
        }
        val ds = duels.filter { d -> d.w.isNotEmpty() && d.a?.let { it in ix } == true && d.b?.let { it in ix } == true }
        val da = IntArray(ds.size) { ix[ds[it].a!!]!! }
        val db = IntArray(ds.size) { ix[ds[it].b!!]!! }
        val dy = DoubleArray(ds.size) { ds[it].result }
        for (j in ds.indices) {
            n[da[j]]++
            n[db[j]]++
        }
        val temp = .6
        val lambda = .4
        val lr = .1
        val gr = DoubleArray(ix.size)
        repeat(90) {
            for (i in gr.indices) gr[i] = -2 * lambda * (r[i] - sc[i])
            for (j in ds.indices) {
                val p = 1 / (1 + StrictMath.exp(-(r[da[j]] - r[db[j]]) / temp))
                val y = dy[j]
                gr[da[j]] = gr[da[j]] + (y - p) / temp
                gr[db[j]] = gr[db[j]] - (y - p) / temp
            }
            // per game in the pool, as the web's loop: a game listed twice moves twice
            for (i in pool.indices) r[at[i]] = r[at[i]] + lr * gr[at[i]]
        }
        val coll = Collator.getInstance(Locale.US)
        val order = pool.indices.sortedWith { ia, ib ->
            val ra = r[at[ia]]
            val rb = r[at[ib]]
            val d = rb.compareTo(ra).let { if (rb == ra) 0 else it }
            if (d != 0) d else {
                val sa = sc[at[ia]]
                val sb = sc[at[ib]]
                val e = if (sb == sa) 0 else sb.compareTo(sa)
                if (e != 0) e else coll.compare(pool[ia].title, pool[ib].title)
            }
        }.map { pool[it].id }
        val pos = HashMap<String, Int>()
        order.forEachIndexed { i, id -> pos[id] = i }
        val rm = HashMap<String, Double>()
        val nm = HashMap<String, Int>()
        val sm = HashMap<String, Double>()
        for ((id, i) in ix) {
            rm[id] = r[i]
            nm[id] = n[i]
            sm[id] = sc[i]
        }
        RankTable(order, pos, rm, nm, sm)
    }

    /** `rankedGames()`: the ranking as games. */
    val ranked: List<Game> by lazy { table.order.mapNotNull { find(it) } }

    /** `ranked()` inside the header card: the ranking, else your played games in played order. */
    val rankedOrPlayed: List<Game> get() = ranked.ifEmpty { played.mapNotNull { find(it.id) } }

    /** `S.curPlat || "Switch"`, and `onPlat(g)`. */
    private val curPlat: String = Backup.str(lib.state["curPlat"]).ifEmpty { "Switch" }

    fun onPlat(g: Game): Boolean {
        val p = curPlat.lowercase()
        if (p == "any") return true
        return platList(g["platforms"]).any { it.lowercase().contains(p) }
    }

    /** `onDeckScores()` (index.html 13260): genre likeness to your favourites, Elo, the machine you're on, hype, playing, out. */
    val deck: Map<String, Double> by lazy {
        val live = games.filter { !isPlayed(it) && !isDropped(it) && !it.comp }
        val t = table
        val fav = ranked
        val tags = { g: Game -> platList(g["genres"]).map { it.lowercase() }.toSet() }
        class Fav(val tg: Set<String>, val w: Double)
        val favW = fav.mapIndexed { i, g -> Fav(tags(g), maxOf(.15, 1 - i.toDouble() / maxOf(4, fav.size)) * (t.sc[g.id]!! / 10)) }
        val sim = { g: Game ->
            val a = tags(g)
            if (a.isEmpty() || favW.isEmpty()) 0.0 else {
                var s = 0.0
                var w = 0.0
                for (f in favW) {
                    val inter = a.count { it in f.tg }
                    val uni = (a + f.tg).size.let { if (it == 0) 1 else it }
                    s += f.w * inter / uni
                    w += f.w
                }
                if (w != 0.0) s / w else 0.0
            }
        }
        val sims = live.map(sim)
        val smax = maxOf(.001, sims.maxOrNull() ?: Double.NEGATIVE_INFINITY)
        val el = live.map { elo[it.id].let { v -> if (v == null || v == 0.0) 1500.0 else v } }
        val emin = minOf(el.minOrNull() ?: 1500.0, 1500.0)
        val emax = maxOf(el.maxOrNull() ?: 1500.0, 1500.0)
        val s = LinkedHashMap<String, Double>()
        live.forEachIndexed { i, g ->
            val out = g.rawState == "backlog" || g.rawState == "playing" || g.isFresh(now, zone) ||
                (g.date.isNotEmpty() && (JsDate.localMidnight(g.date, zone)?.let { it <= now } ?: false))
            s[g.id] = .45 * (sims[i] / smax) + .2 * (if (emax > emin) (el[i] - emin) / (emax - emin) else .5) + .2 * (if (onPlat(g)) 1 else 0) +
                .1 * Backup.num(g["hype"], 5.0, 0.0, 10.0) / 10 + (if (g.rawState == "playing") .08 else 0.0) + (if (out) 1 else 0)
        }
        s
    }

    /** `onDeck(n)`: the top [n] (JavaScript's key order, then a stable sort by score). */
    fun onDeck(n: Int = 3): List<Game> {
        val s = deck
        return jsKeys(s.keys).sortedWith { a, b -> s[b]!!.compareTo(s[a]!!).let { if (s[b]!! == s[a]!!) 0 else it } }
            .take(n).mapNotNull { find(it) }
    }

    private val style = Backup.str(lib.state["playStyle"])

    /** `hrsOf(g)` (index.html 9246): hours to beat by how you play (main story, + extras, everything). */
    fun hrsOf(g: Game): Double {
        if (g.comp) return 0.0
        val m = Backup.num(g["hours"], 0.0, 0.0, 1000.0)
        val x = Backup.num(g["hoursX"], 0.0, 0.0, 3000.0)
        val a = Backup.num(g["hours100"], 0.0, 0.0, 5000.0)
        return when (style) {
            "extras" -> if (x != 0.0) x else jsRound(m * 1.5)
            "all" -> if (a != 0.0) a else if (x != 0.0) jsRound(x * 1.6) else jsRound(m * 2.4)
            else -> if (m != 0.0) m else if (x != 0.0) x else a
        }
    }

    companion object {
        const val ELO_K = 24

        /** `TIERS`: S from 9, A from 7.5, B from 6, else C. */
        fun tierOf(v: Double): String = when {
            v >= 9 -> "S"
            v >= 7.5 -> "A"
            v >= 6 -> "B"
            else -> "C"
        }

        /** `platList(str)`: `String(str || "")` split on commas, trimmed, empties dropped (a list joins with commas, as JS does). */
        fun platList(e: JsonElement?): List<String> {
            val s = when (e) {
                is JsonArray -> e.joinToString(",") { (it as? JsonPrimitive)?.content ?: "" }
                else -> if (Backup.truthy(e)) Backup.str(e) else ""
            }
            return s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        /** `Math.round`: halves go up. */
        fun jsRound(v: Double): Double = Math.floor(v + .5)

        private val INDEX = Regex("^(0|[1-9]\\d{0,9})$")

        /** The order `Object.keys` gives: whole-number keys first, smallest first, then the rest as they were added. */
        fun jsKeys(keys: Collection<String>): List<String> {
            val (ints, rest) = keys.partition { INDEX.matches(it) && it.toLong() < 4294967295L }
            return ints.sortedBy { it.toLong() } + rest
        }
    }
}

private val YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** Dates the header card reads as the phone's own midnight (`new Date(d + "T00:00:00")`). */
fun JsDate.localMidnight(s: String?, zone: ZoneId): Long? {
    if (s == null || !YMD.matches(s)) return null
    return runCatching { LocalDate.parse(s).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
}

/** `new Date(new Date().toDateString())`: today's midnight on the phone. */
fun JsDate.today0(now: Long, zone: ZoneId): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
