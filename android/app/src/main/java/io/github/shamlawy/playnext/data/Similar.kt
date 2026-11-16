package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    // ---- Picked by Nexi (`simFind`, index.html 13498: the helper names games, RAWG checks that each one is real) ----

    /** One of Nexi's picks as RAWG knows it (`simHit` + `why`): its RAWG id, name, why, picture, release date, genres, Metacritic. */
    class Pick(val id: Long, val name: String, val why: String, val img: String, val released: String, val genres: List<String>, val mc: Int)

    /** `simFind`'s answer: when, the picks and how they were found ("ai": the helper's names; "series": an old helper, the same series). */
    class Found(val t: Long, val l: List<Pick>, val how: String)

    /** This run's answers. A non-empty "ai" one holds 30 days (as the web's `S.simCache3`); any other one [EMPTY_FOR]. */
    private val found = ConcurrentHashMap<String, Found>()
    private val finding = ConcurrentHashMap.newKeySet<String>()
    private const val KEEP = 30 * JsDate.DAY

    /** The last ask's failure for a game ("Couldn't find new ones right now (<this>)."), empty for one without words. */
    val findErr = ConcurrentHashMap<String, String>()

    /** `nxPost("/similar")`: "helper-old" for a helper from before v187, the helper's own words for its other failures. */
    private val realAsk: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/similar"), body)) { r ->
            val text = r.body.string()
            val e = runCatching { Backup.str((Json.parseToJsonElement(text) as? JsonObject)?.get("error")) }.getOrDefault("")
            if (r.code == 404 && e.contains("not found", ignoreCase = true)) throw Recap.HelperError("helper-old")
            if (!r.isSuccessful || e.isNotEmpty()) throw Recap.HelperError(e.ifEmpty { "The helper said ${r.code}" })
            text
        }
    }
    @Volatile internal var ask = realAsk

    /** A GET to RAWG: the status and the body (a test hands in its own). */
    private val realGet: suspend (String) -> Pair<Int, String> = { url ->
        io.github.shamlawy.playnext.net.Net.call(okhttp3.Request.Builder().url(url).build()) { it.code to it.body.string() }
    }
    @Volatile internal var rawgGet = realGet

    /** `rawgApi(p)`: RAWG's answer with your key; "RAWG said <status>" when it says no. */
    private suspend fun rawg(key: String, path: String): JsonObject {
        val url = ("https://api.rawg.io/api/$path").toHttpUrl().newBuilder().addQueryParameter("key", key).build()
        val (code, text) = rawgGet(url.toString())
        if (code !in 200..299) throw java.io.IOException("RAWG said $code")
        return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** `simHit(h)` (+ its `why`): a RAWG result as a pick; null without a name. */
    fun pick(h: JsonObject, why: String = Backup.str(h["why"])): Pick? {
        val name = Backup.str(h["name"]).ifEmpty { return null }
        val genres = (h["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("name")?.let(Backup::str)?.takeIf(String::isNotEmpty) }
        return Pick(
            Backup.num(h["id"], 0.0, 0.0, 1e12).toLong(), name, why, Backup.str(h["background_image"]), Backup.str(h["released"]), genres,
            Backup.num(h["metacritic"], 0.0, 0.0, 100.0).toInt(),
        )
    }

    /** The closest of RAWG's results to [name] when `simSame` gives it .6 or more (numbers decide: "Star Rift" is never "Star Rift 3"). */
    fun best(results: JsonArray?, name: String): JsonObject? =
        results.orEmpty().mapNotNull { it as? JsonObject }.map { it to same(Backup.str(it["name"]), name) }.maxByOrNull { it.second }?.takeIf { it.second >= .6 }?.first

    /** What the sheet shows for [g]: this run's answer, else the backup's `S.simCache3` when it's under 30 days old. */
    fun found(lib: Library, g: Game, now: Long): Found? {
        found[g.id]?.let { f -> if (now - f.t < (if (f.how == "ai" && f.l.isNotEmpty()) KEEP else EMPTY_FOR)) return f }
        val c = (lib["simCache3"] as? JsonObject)?.get(g.id) as? JsonObject ?: return null
        val t = Backup.num(c["t"], 0.0, 0.0, 1e15).toLong()
        if (now - t >= KEEP) return null
        return Found(t, (c["l"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { h -> pick(h) } }, Backup.str(c["how"]).ifEmpty { "ai" })
    }

    fun finding(g: Game) = g.id in finding

    /** The body of `POST /similar`: the title, year, first 4 genres, your 8 best-ranked games and the first 80 you have. */
    fun askBody(lib: Library, rk: Rank, g: Game): String = buildJsonObject {
        put("title", g.title)
        put("year", g.date.take(4))
        put("genres", Rank.platList(g["genres"]).take(4).joinToString(", "))
        putJsonArray("favourites") { rk.ranked.take(8).forEach { add(it.title) } }
        putJsonArray("exclude") { lib.games.take(80).forEach { add(it.title) } }
    }.toString()

    /**
     * `simFind`: the helper names games like [g] (yours and [g] itself left out), each of the first 12 is checked on RAWG
     * (`search_precise`, the closest name at .6 or more) and up to 10 real ones are kept, one per RAWG game. Without a RAWG
     * key the names come without pictures. A helper from before `/similar` (or no network) gives the same series instead.
     * Never throws: a failure lands in [findErr]. Nothing is written (the web's `S.simCache3` stays the backup's).
     */
    suspend fun find(lib: Library, rk: Rank, g: Game, rawgKey: String, now: Long) {
        if (found(lib, g, now) != null || !finding.add(g.id)) return
        findErr.remove(g.id)  // an earlier open's failure never shows while this ask runs
        seen.value++
        try {
            val mine = lib.games.map { normT(it.title) }.toSet()
            var how = "ai"
            var names: List<Pair<String, String>> = emptyList()
            try {
                val j = ask(askBody(lib, rk, g)).let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                names = (j?.get("games") as? JsonArray).orEmpty().mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    Backup.str(o["name"]).takeIf { it.isNotEmpty() }?.let { it to Backup.str(o["why"]) }
                }.filter { (n) -> normT(n) !in mine && same(n, g.title) < .99 }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // the web's `e.message !== "helper-old" && !(e instanceof TypeError)`: an old helper or no network → the series
                if (!(e is Recap.HelperError && e.message == "helper-old") && e !is java.io.IOException) throw e
                how = "series"
            }
            val out: List<Pick> = if (how == "ai") {
                if (rawgKey.isEmpty()) names.take(10).map { (n, w) -> Pick(0, n, w, "", "", emptyList(), 0) }
                else {
                    val hits = coroutineScope {
                        names.take(12).map { (n, w) ->
                            async {
                                try {
                                    best(rawg(rawgKey, "games?search=" + enc(n) + "&search_precise=true&page_size=5")["results"] as? JsonArray, n)?.let { pick(it, w) }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }.awaitAll()
                    }
                    val ids = HashSet<Long>()
                    hits.filterNotNull().filter { normT(it.name) !in mine && ids.add(it.id) }.take(10)
                }
            } else if (rawgKey.isNotEmpty()) {
                var id = if (Backup.truthy(g["rawgId"])) Backup.str(g["rawgId"]) else ""
                if (id.isEmpty()) {
                    val r = rawg(rawgKey, "games?search=" + enc(g.title) + "&search_precise=true&page_size=1")
                    id = ((r["results"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("id")?.let(Backup::str).orEmpty()
                }
                if (id.isEmpty()) emptyList() else {
                    val r = rawg(rawgKey, "games/" + enc(id) + "/game-series?page_size=20")
                    (r["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { normT(Backup.str(it["name"])) !in mine }.take(8).mapNotNull { pick(it, "Same series") }
                }
            } else emptyList()
            found[g.id] = Found(now, out, how)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            findErr[g.id] = (e.message ?: "").take(120)
        } finally {
            finding.remove(g.id)
            seen.value++
        }
    }

    /** `sgFromRawg`: a pick as a card (its picture, why, and year · first 2 genres · Metacritic). */
    fun card(p: Pick): Card = Card(
        p.name, p.why, p.img, "",
        listOf(p.released.take(4), p.genres.take(2).joinToString(", "), if (p.mc > 0) "MC ${p.mc}" else "").filter { it.isNotEmpty() }.joinToString(" · "),
    )

    internal fun forget() {
        mem.clear(); busy.clear(); err.clear(); post = realPost
        found.clear(); finding.clear(); findErr.clear(); ask = realAsk; rawgGet = realGet
    }
}
