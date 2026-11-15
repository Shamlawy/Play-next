package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

/**
 * Similar games (index.html 13460-13590, 20139-20170, 22239-22440; android/parity/2-map/2-edit.md §7e-7g): the games in
 * your library most like one (`simLocal`), how alike two names are (`simSame`), and Steam players' "More like this"
 * (`stMore`, the helper's `POST /steam {more}`). Read only: the web keeps Steam's list in `S.stMore` for 14 days; here a
 * fresh answer lives in memory until the app is opened again, and the backup's copy is read as it is.
 */
object Similar {
    /** One game in your library like another: how alike (`s`, 0-1) and how many tags they share. */
    class Hit(val g: Game, val s: Double, val n: Int)

    private val GENERIC: Set<String> by lazy { Web.ST_GENERIC.toSet() }
    private val BROAD: Set<String> by lazy { Web.ST_BROAD.toSet() }

    private fun strs(e: Any?): List<String> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    /**
     * `simTags`: a game's genres (`g:`), what got you (`g.why`, `w:`) and Steam players' first 12 tags (`stSimTags`: not
     * the generic ones unless they're broad), lower case.
     */
    fun tags(g: Game): Set<String> {
        val out = LinkedHashSet<String>()
        Rank.platList(g["genres"]).forEach { out += "g:" + it.lowercase() }
        // `whyOf(g)`: only an array counts (its entries as JS turns them into text)
        (g["why"] as? JsonArray)?.forEach { w -> out += "w:" + ((w as? JsonPrimitive)?.content ?: w.toString()) }
        strs(g["stTags"]).take(12).forEach { t -> val l = t.lowercase(); if (l !in GENERIC || l in BROAD) out += "g:$l" }
        return out
    }

    /**
     * `simLocal` over every game in the backup: a tag counts for more the rarer it is (ln((N+1)/(df+.5))), the match is the
     * shared weight over the union's, and a game needs a match over .22 and one shared tag; best first.
     */
    class Index(private val games: List<Game>) {
        private val all: List<Pair<Game, Set<String>>> by lazy { games.map { it to tags(it) } }
        private val df: Map<String, Int> by lazy {
            val m = HashMap<String, Int>()
            all.forEach { (_, t) -> t.forEach { k -> m[k] = (m[k] ?: 0) + 1 } }
            m
        }
        private val memo = ConcurrentHashMap<String, List<Hit>>()

        // each tag's weight once, so a card's first flip doesn't take a log per tag per game on the frame
        private val wt: Map<String, Double> by lazy { df.mapValues { (_, n) -> ln((all.size + 1.0) / (n + .5)) } }
        private fun w(k: String) = wt[k] ?: ln((all.size + 1.0) / .5)

        /** The [n] games most like [g] (3 on the card back, 8 in the sheet, every one for the Queue's tab). */
        fun like(g: Game, n: Int): List<Hit> = memo.getOrPut(g.id) { score(g) }.take(n)

        private fun score(g: Game): List<Hit> {
            val a = all.firstOrNull { it.first.id == g.id }?.second ?: tags(g)
            if (a.isEmpty()) return emptyList()
            return all.filter { it.first.id != g.id }.map { (x, b) ->
                var inter = 0.0
                var uni = 0.0
                var shared = 0
                (a + b).forEach { k -> val v = w(k); uni += v; if (k in a && k in b) { inter += v; shared++ } }
                Hit(x, if (uni != 0.0) inter / uni else 0.0, shared)
            }.filter { it.s > .22 && it.n >= 1 }.sortedByDescending { it.s }
        }
    }

    // ---- names: is it the same game? (`simSame`, v236: numbers decide, III = 3) ----

    private val ROMAN = mapOf("ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10, "xi" to 11, "xii" to 12, "xiii" to 13, "xiv" to 14, "xv" to 15, "xvi" to 16)
    private val STOP = setOf("the", "of", "a", "and")

    /** `normT`: lower case, letters and digits only. */
    fun normT(t: String) = t.lowercase().replace(Regex("[^a-z0-9]+"), "")

    /** `simNum`: Roman numerals as digits (a lone "i" stays a word). */
    private fun simNum(t: String) = t.lowercase().replace(Regex("[a-z]+")) { m -> ROMAN[m.value]?.let { " $it " } ?: m.value }

    /** `simNums`: the numbers in a name, sorted, as text. */
    fun simNums(t: String) = Regex("\\d+").findAll(simNum(t)).map { it.value.toBigInteger().toString() }.sorted().joinToString(",")

    private fun words(t: String) = t.lowercase().replace(Regex("[^a-z0-9 ]+"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOP }.toSet()

    /** `simSame`: 1 for the same name, at most .85 when one holds the other, at most .3 when the numbers differ, else shared words. */
    fun same(a: String, b: String): Double {
        val x = normT(simNum(a))
        val y = normT(simNum(b))
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val cap = if (simNums(a) != simNums(b)) .3 else 1.0
        if (x.length > 4 && y.length > 4 && (x.contains(y) || y.contains(x))) return minOf(.85, cap)
        val wa = words(simNum(a))
        val wb = words(simNum(b))
        val inter = wa.count { it in wb }
        return minOf(cap, inter.toDouble() / ((wa + wb).size.takeIf { it > 0 } ?: 1))
    }

    /** `sgK`: the key a "not for me" is kept under. */
    fun key(t: String) = normT(simNum(t))

    /** `sgNo`: you said "not for me" to it on the web (`S.simNo`). */
    fun nope(lib: Library, name: String) = (lib["simNo"] as? JsonObject)?.containsKey(key(name)) == true

    /** `sgHave`: the game in your library this suggestion is (a name .9 alike). */
    fun have(lib: Library, name: String): Game? = lib.games.firstOrNull { same(it.title, name) >= .9 }

    // ---- Steam players also like (`stMore`) ----

    /** One of Steam's "More like this" games. */
    class St(val id: Long, val name: String, val tags: List<String>, val desc: String)

    /** A card in the sheet (`sgFromSteam`): its picture (and the fallback), why it's here and its first tags. */
    class Card(val name: String, val why: String, val img: String, val img2: String, val meta: String)

    /** This run's answers: Steam id, when, the list. An empty one is asked again after [EMPTY_FOR] (the web keeps only a non-empty list). */
    private val mem = ConcurrentHashMap<String, Triple<Long, Long, List<St>>>()
    private const val EMPTY_FOR = 10 * 60_000L
    private val busy = ConcurrentHashMap.newKeySet<String>()

    /** What changed (a counter), so the sheet redraws when Steam answers. */
    val seen = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The last ask's failure for a game ("helper-old" for a helper from before Steam's list), empty when none. */
    val err = ConcurrentHashMap<String, String>()

    private val realPost: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/steam"), body)) { r ->
            if (r.code == 404) throw Recap.HelperError("helper-old")
            if (!r.isSuccessful) throw Recap.HelperError("HTTP ${r.code}")
            r.body.string()
        }
    }
    @Volatile internal var post = realPost

    private fun st(o: JsonObject): St? {
        val id = Backup.num(o["id"], 0.0, 0.0, 1e12).toLong()
        val name = Backup.str(o["name"])
        if (name.isEmpty()) return null
        return St(id, name, strs(o["tags"]), Backup.str(o["desc"]))
    }

    /** `S.stMore[g.id]` when it's for this Steam id and under 14 days old (this run's answer first). */
    fun cached(lib: Library, g: Game, now: Long): List<St>? {
        val id = Reviews.stId(g)
        if (id == 0L) return null
        mem[g.id]?.let { (sid, t, l) -> if (sid == id && (l.isNotEmpty() || now - t < EMPTY_FOR)) return l }
        val c = (lib["stMore"] as? JsonObject)?.get(g.id) as? JsonObject ?: return null
        if (Backup.num(c["id"], 0.0, 0.0, 1e12).toLong() != id || now - Backup.num(c["t"], 0.0, 0.0, 1e15) >= 14 * JsDate.DAY) return null
        return (c["l"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::st) }
    }

    fun busy(g: Game) = g.id in busy

    /** Asks Steam's list for [g] (`stMore`): `{cc, items: [], more: <Steam id>}`; the first 14 games are kept. Never throws. */
    suspend fun more(lib: Library, g: Game, cc: String, now: Long) {
        val id = Reviews.stId(g)
        if (id == 0L || cached(lib, g, now) != null || !busy.add(g.id)) return
        err.remove(g.id)  // a failure from an earlier open never shows while this ask runs
        seen.value++
        try {
            val body = buildJsonObject { put("cc", cc); putJsonArray("items") {}; put("more", id) }.toString()
            val j = Json.parseToJsonElement(post(body)) as? JsonObject
            val l = (((j?.get("more") as? JsonObject)?.get("games")) as? JsonArray).orEmpty().take(14).mapNotNull { (it as? JsonObject)?.let(::st) }
            mem[g.id] = Triple(id, now, l)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            err[g.id] = (if (e is Recap.HelperError) e.message ?: "" else e.toString()).take(80)
        } finally {
            busy.remove(g.id)
            seen.value++
        }
    }

    /** `sgFromSteam`: Steam's capsule picture (its header as the fallback), why (up to 3 shared tags) and the first 3 tags. */
    fun card(x: St, g: Game): Card {
        val mine = strs(g["stTags"]).map { it.lowercase() }.toSet()
        val both = x.tags.filter { t -> val l = t.lowercase(); l in mine && (l !in GENERIC || l in BROAD) }.take(3)
        val base = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/${x.id}/"
        return Card(
            x.name,
            if (both.isNotEmpty()) "Steam players who like ${g.title} love this · both ${both.joinToString(", ")}" else "Steam players also like it",
            base + "capsule_616x353.jpg", base + "header.jpg", x.tags.take(3).joinToString(", "),
        )
    }

    internal fun forget() { mem.clear(); busy.clear(); err.clear(); post = realPost }
}
