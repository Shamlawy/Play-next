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
    /** `p.id`, or null when the record has none (a missing id never equals a game's, as in JavaScript). */
    val id: String? get() = (o["id"] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
    val real: Double? get() = (o["real"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    val again: Boolean get() = Backup.truthy(o["again"])
    val t: String get() = Backup.str(o["t"])

    /** `scored(p)` (index.html 3504): a real score, or a deliberate 0 (`z: 1`), and not "Don't remember". */
    val scored: Boolean
        get() {
            if (Backup.truthy(o["dk"])) return false
            val r = real ?: return false
            return r > 0 || (r == 0.0 && (o["z"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull == 1.0)
        }
}

class Duel(val o: JsonObject) {
    val a: String? get() = (o["a"] as? JsonPrimitive)?.content
    val b: String? get() = (o["b"] as? JsonPrimitive)?.content
    val w: String get() = Backup.str(o["w"])
    val t: String get() = Backup.str(o["t"])

    /** `duelResult(d)` (index.html 13029): for side a, a win 1, a slight edge .75, even .5. */
    val result: Double
        get() {
            if (w.isEmpty() || w == "draw") return .5
            val s = if (Backup.truthy(o["e"])) .75 else 1.0
            return if (w == a) s else 1 - s
        }
}

/** `rankTable()`: the order, each game's position, its pulled score [r], its duel count [n] and its own score [sc]. */
class RankTable(val order: List<String>, val pos: Map<String, Int>, val r: Map<String, Double>, val n: Map<String, Int>, val sc: Map<String, Double>)

class Rank(private val lib: Library, private val now: Long, private val zone: ZoneId) {
    private val games = lib.games
    val played: List<Played> = lib.played
    val duels: List<Duel> = lib.duels

    fun isPlayed(g: Game) = played.any { it.id == g.id }
    fun playedRec(g: Game) = played.firstOrNull { it.id == g.id }
    fun isDropped(g: Game) = g.rawState == "dropped"
    fun find(id: String?) = if (id == null) null else games.firstOrNull { it.id == id }

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
        val pool = games.filter { g -> isPlayed(g) && !g.comp && playedRec(g)!!.scored }
        val ids = pool.map { it.id }.toSet()
        val sc = HashMap<String, Double>()
        val r = HashMap<String, Double>()
        val n = HashMap<String, Int>()
        for (g in pool) {
            val v = playedRec(g)!!.real!!
            sc[g.id] = v
            r[g.id] = v
            n[g.id] = 0
        }
        val ds = duels.filter { it.w.isNotEmpty() && it.a in ids && it.b in ids }
        for (d in ds) {
            n[d.a!!] = n[d.a!!]!! + 1
            n[d.b!!] = n[d.b!!]!! + 1
        }
        val temp = .6
        val lambda = .4
        val lr = .1
        repeat(90) {
            val gr = HashMap<String, Double>()
            for (g in pool) gr[g.id] = -2 * lambda * (r[g.id]!! - sc[g.id]!!)
            for (d in ds) {
                val p = 1 / (1 + StrictMath.exp(-(r[d.a!!]!! - r[d.b!!]!!) / temp))
                val y = d.result
                gr[d.a!!] = gr[d.a!!]!! + (y - p) / temp
                gr[d.b!!] = gr[d.b!!]!! - (y - p) / temp
            }
            for (g in pool) r[g.id] = r[g.id]!! + lr * gr[g.id]!!
        }
        val coll = Collator.getInstance(Locale.US)
        val order = pool.sortedWith { a, b ->
            val d = r[b.id]!!.compareTo(r[a.id]!!).let { if (r[b.id]!! == r[a.id]!!) 0 else it }
            if (d != 0) d else {
                val e = if (sc[b.id]!! == sc[a.id]!!) 0 else sc[b.id]!!.compareTo(sc[a.id]!!)
                if (e != 0) e else coll.compare(a.title, b.title)
            }
        }.map { it.id }
        val pos = HashMap<String, Int>()
        order.forEachIndexed { i, id -> pos[id] = i }
        RankTable(order, pos, r, n, sc)
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

    /** `hrsOf(g)` (index.html 9246): hours to beat by how you play (main story, + extras, everything). */
    fun hrsOf(g: Game): Double {
        if (g.comp) return 0.0
        val m = Backup.num(g["hours"], 0.0, 0.0, 1000.0)
        val x = Backup.num(g["hoursX"], 0.0, 0.0, 3000.0)
        val a = Backup.num(g["hours100"], 0.0, 0.0, 5000.0)
        return when (Backup.str(lib.state["playStyle"])) {
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

        /** The order `Object.keys` gives: whole-number keys first, smallest first, then the rest as they were added. */
        fun jsKeys(keys: Collection<String>): List<String> {
            val index = Regex("^(0|[1-9]\\d{0,9})$")
            val (ints, rest) = keys.partition { index.matches(it) && it.toLong() < 4294967295L }
            return ints.sortedBy { it.toLong() } + rest
        }
    }
}

/** Dates the header card reads as the phone's own midnight (`new Date(d + "T00:00:00")`). */
fun JsDate.localMidnight(s: String?, zone: ZoneId): Long? {
    if (s == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return null
    return runCatching { LocalDate.parse(s).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
}

/** `new Date(new Date().toDateString())`: today's midnight on the phone. */
fun JsDate.today0(now: Long, zone: ZoneId): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
