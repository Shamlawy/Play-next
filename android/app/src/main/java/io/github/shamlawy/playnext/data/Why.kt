package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Why you'll love it (v163, index.html 14916-14985; android/parity/2-map/2-edit.md §7d): your scored games and this one
 * go to the helper (`POST /why`, read only, so allowed before switch day), which says how it fits your taste. The web
 * keeps the answer in the backup (`S.aiWhy[id] = {text, n, k}`) while your scores ([stamp]) stay the same; here a new
 * answer is kept in memory while the app runs, and one already in the backup is used as is. A game whose scores you
 * reset ([Practice.WIPED]) never uses the backup's.
 */
object Why {
    class Answer(val text: String, val n: Int, val k: String)

    /** What the sheet shows: the verdict pill, up to 5 lines, and how many rated games it was based on. */
    class Shown(val verdict: String, val points: List<String>, val n: Int)

    /** The helper said 404: it's older than the trick (the web's "old"). */
    class Old : Exception("old")

    private val mem = ConcurrentHashMap<String, Answer>()

    /** The helper's call; tests swap it. Returns the body of a 2xx answer, else throws (HTTP 404 as [Old]). */
    @Volatile internal var post: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/why"), body)) { r ->
            val s = r.body.string()
            if (r.code == 404) throw Old()
            if (!r.isSuccessful) {
                val e = runCatching { Backup.str((kotlinx.serialization.json.Json.parseToJsonElement(s) as? JsonObject)?.get("error")) }.getOrDefault("")
                throw Exception(e.ifEmpty { "HTTP ${r.code}" })
            }
            s
        }
    }

    private val LABEL: Map<String, String> = Web.WHY_GROUPS.flatMap { it.second }.toMap()

    /** `rated(skip)`: each scored finish but this game's, as the helper reads it, best first (a replay counts again). */
    fun rated(lib: Library, skip: String): List<JsonObject> {
        val byId = lib.games.associateBy { it.id }
        return lib.played.filter { it.scored && it.id != skip }.mapNotNull { p ->
            val g = byId[p.id] ?: return@mapNotNull null
            val m = g["mine"] as? JsonObject
            val why = (g["why"] as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonPrimitive)?.content?.let { LABEL[it] } }.take(6)
            (p.real ?: 0.0) to JsonObject(
                linkedMapOf(
                    "title" to JsonPrimitive(g.title),
                    "score" to Backup.jsNumber(p.real ?: 0.0),
                    "again" to JsonPrimitive(p.again),
                    "genres" to JsonArray(Rank.platList(g["genres"]).take(3).map(::JsonPrimitive)),
                    "liked" to JsonArray(why.map(::JsonPrimitive)),
                    "parts" to JsonArray(Web.RATINGS.mapNotNull { (k, l) ->
                        val v = Backup.num(m?.get(k), 0.0, -1e9, 1e9)
                        if (v > 0) JsonPrimitive(l + " " + Backup.jsNumber(v).content) else null
                    }),
                ),
            )
        }.sortedByDescending { it.first }.map { it.second }
    }

    /** `stamp()`: every scored finish and its score; a change asks again. */
    fun stamp(lib: Library): String = lib.played.filter { it.scored }.joinToString("|") { (it.id ?: "undefined") + ":" + Backup.jsNumber(it.real ?: 0.0).content }

    /** The request: the game, and your taste (all of it, or the top 12 and bottom 6 past 18). */
    fun body(lib: Library, g: Game, taste: List<JsonObject>): String {
        val pick = if (taste.size > 18) taste.take(12) + taste.takeLast(6) else taste
        val me = lib.played.firstOrNull { it.id == g.id }
        val real = me?.real?.takeIf { it != 0.0 }
        return JsonObject(
            linkedMapOf(
                "game" to JsonObject(
                    linkedMapOf(
                        "title" to JsonPrimitive(g.title),
                        "genres" to JsonArray(Rank.platList(g["genres"]).take(4).map(::JsonPrimitive)),
                        "platforms" to JsonArray(Rank.platList(g["platforms"]).take(4).map(::JsonPrimitive)),
                        "about" to JsonPrimitive(Backup.str(g["desc"]).ifEmpty { Backup.str(g["about"]) }.take(500)),
                        "played" to JsonPrimitive(me?.scored == true),
                        "myScore" to (real?.let { Backup.jsNumber(it) } ?: JsonNull),
                    ),
                ),
                "taste" to JsonArray(pick),
            ),
        ).toString()
    }

    /** The kept answer for [g] while your scores are the same: this run's, else the backup's (not after a reset). */
    fun kept(lib: Library, g: Game): Answer? {
        val k = stamp(lib)
        mem[g.id]?.takeIf { it.k == k }?.let { return it }
        if (g.id in Practice.wiped(lib)) return null
        val o = (lib["aiWhy"] as? JsonObject)?.get(g.id) as? JsonObject ?: return null
        val text = Backup.str(o["text"]).ifEmpty { return null }
        return Answer(text, Backup.num(o["n"], 0.0, 0.0, 1e9).toInt(), Backup.str(o["k"])).takeIf { it.k == k }
    }

    /** Asks the helper (the web's `ask`); throws [Old], or the error the web would show under its line. */
    suspend fun ask(lib: Library, g: Game): Answer {
        val taste = rated(lib, g.id)
        val d = kotlinx.serialization.json.Json.parseToJsonElement(post(body(lib, g, taste))) as? JsonObject
        val text = Backup.str(d?.get("text")).ifEmpty { throw Exception("empty") }
        return Answer(text, taste.size, stamp(lib)).also { mem[g.id] = it }
    }

    /** `show(r)`: the line after "VERDICT:" (no `*` or `_`), then up to 5 lines without their bullets or `**`. */
    fun shown(a: Answer): Shown {
        val lines = a.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val vl = lines.firstOrNull { VERDICT.containsMatchIn(it) }
        val verdict = vl?.replace(VERDICT, "")?.replace(Regex("[*_]"), "").orEmpty()
        val pts = lines.filter { it != vl }.map { it.replace(BULLET, "").replace("**", "") }.filter { it.isNotEmpty() }.take(5)
        return Shown(verdict, pts, a.n)
    }

    private val VERDICT = Regex("^verdict\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("^[-*•\\d.)\\s]+")

    /** Forgets [id]'s answer (its scores were reset). */
    fun drop(id: String) { mem.remove(id) }

    internal fun forget() = mem.clear()
}
