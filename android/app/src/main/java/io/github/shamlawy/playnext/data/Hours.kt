package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Hours to beat from HowLongToBeat (index.html 23339-23510, android/parity/2-map/2-edit.md §7c): the helper's
 * `POST /hours {src: "hltb"}` (read only, so allowed before switch day). The web keeps the answer in the backup
 * (`g.hl`); here a new answer lives in memory while the app runs and the backup's copy is the starting point.
 *
 * When to ask: the web sweeps every due game 20 s after it opens and saves what it learns. This copy can't save, so a
 * sweep would ask HowLongToBeat about every game again on every open; instead the page asks for the game you look at
 * when it's due ([due]), and the sheet asks when it has nothing or you tap Check again. An answer fills the game's hours
 * by itself when they're empty, Nexi's guess, RAWG's or already HowLongToBeat's (`hlAuto` + `hlUse`), through the
 * practice copy; numbers you typed are never touched.
 */
object Hours {
    /** One answer (`g.hl`): found ([m] > 0), not found ([none]) or an error ([err]). */
    class Hl(val id: Long, val n: String, val m: Double, val x: Double, val c: Double, val cnt: Long, val at: Long, val none: Boolean, val err: String) {
        val has get() = m > 0
    }

    private val HL_K = listOf("hours" to "m", "hoursX" to "x", "hours100" to "c")

    /** Settings › Extra features › hours from HowLongToBeat (`S.hbOff`). */
    fun off(lib: Library) = Backup.truthy(lib["hbOff"])

    private val mem = ConcurrentHashMap<String, Hl>()
    private val busy = ConcurrentHashMap.newKeySet<String>()

    /** When a helper from before v17 answered with AI guesses (`hlOld`): an automatic ask waits an hour. */
    @Volatile private var old = 0L

    /** What changed (a counter): the tile and the sheet read it so an answer or a busy state shows. */
    val seen = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The helper's call; tests swap it. */
    @Volatile internal var post: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/hours"), body)) { r ->
            if (!r.isSuccessful) throw Recap.HelperError("HTTP ${r.code}")
            r.body.string()
        }
    }

    private fun num(e: JsonElement?) = Backup.num(e, 0.0, 0.0, 1e9)

    /** Times, ids and counts: no small cap (a time is ~1.8e12). */
    private fun big(e: JsonElement?) = Backup.num(e, 0.0, 0.0, 1e15)

    private fun parse(o: JsonObject, at: Long? = null): Hl = Hl(
        big(o["id"]).toLong(), Backup.str(o["n"]), num(o["m"]), num(o["x"]), num(o["c"]), big(o["cnt"]).toLong(),
        at ?: big(o["at"]).toLong(), Backup.truthy(o["none"]), if (Backup.truthy(o["err"])) Backup.str(o["err"]) else "",
    )

    /** The answer for [g]: this run's, else the backup's (`g.hl`). */
    fun of(g: Game): Hl? = mem[g.id] ?: (g["hl"] as? JsonObject)?.let(::parse)

    fun has(g: Game) = of(g)?.has == true

    fun busy(g: Game) = g.id in busy

    /** `hlSrc`: whose number the game's main hours are. */
    fun src(g: Game): String {
        val h = Backup.num(g["hours"], 0.0, -1e15, 1e15)
        if (!(h > 0)) return "none"
        val s = Backup.str(g["hrsSrc"])
        if (s == "me") return "me"
        if (s == "hltb") return "hltb"
        val hb = g["hbV"] as? JsonObject
        if (hb != null && h == Backup.num(hb["m"], Double.NaN, -1e15, 1e15)) return "ai"
        if (s == "rawg") return "rawg"
        return "old"
    }

    /** `hlAuto`: follows HowLongToBeat by itself. */
    fun auto(g: Game) = src(g) in setOf("none", "ai", "rawg", "hltb")

    private fun plus(e: JsonElement?): Double = Backup.num(e, 0.0, -1e15, 1e15).let { if (it.isNaN()) 0.0 else it }

    /** `hlSame`: the game's hours are HowLongToBeat's (a missing extras / everything on its side matches anything). */
    fun same(g: Game, h: Hl): Boolean = HL_K.all { (k, kk) ->
        val v = when (kk) { "m" -> h.m; "x" -> h.x; else -> h.c }
        plus(g[k]) == v || (kk != "m" && !(v > 0))
    }

    /** `hlUse`: HowLongToBeat's three numbers as the game's (0 for none), `hrsSrc = "hltb"`; null when nothing changes. */
    fun use(g: Game, h: Hl): Map<String, JsonElement>? {
        if (!h.has) return null
        val f = LinkedHashMap<String, JsonElement>()
        for ((k, kk) in HL_K) {
            val v = when (kk) { "m" -> h.m; "x" -> h.x; else -> h.c }
            f[k] = Backup.jsNumber(if (v > 0) v else 0.0)
        }
        f["hrsSrc"] = JsonPrimitive("hltb")
        if (g["hbV"] != null) f["hbV"] = JsonNull
        if (g["hlKeep"] != null) f["hlKeep"] = JsonNull
        return f
    }

    /** Whether [use] would change any of the three numbers (`hlUse`'s return). */
    fun moves(g: Game, h: Hl): Boolean = HL_K.any { (k, kk) ->
        val v = when (kk) { "m" -> h.m; "x" -> h.x; else -> h.c }
        plus(g[k]) != (if (v > 0) v else 0.0)
    }

    /** "Keep mine": these numbers are yours from now on (`g.hlKeep = g.hl.m; g.hrsSrc = "me"`). */
    fun keep(h: Hl): Map<String, JsonElement> = mapOf("hlKeep" to Backup.jsNumber(h.m), "hrsSrc" to JsonPrimitive("me"))

    /** Whether "Keep mine" shows: an older number you haven't kept against this HowLongToBeat number. */
    fun canKeep(g: Game, h: Hl) = src(g) == "old" && Backup.num(g["hlKeep"], Double.NaN, -1e15, 1e15) != h.m

    /** `hlDue`: never asked; an error after 6 h; not found after 14 days; found after 7 days while new (90 days either side of its date, or coming), else 45. */
    fun due(lib: Library, g: Game, now: Long): Boolean {
        if (off(lib) || g.comp || g.title.isEmpty()) return false
        val h = of(g) ?: return true
        val age = now - h.at
        if (h.err.isNotEmpty()) return age > 6 * 3_600_000L
        if (h.none) return age > 14 * 86_400_000L
        val at = JsDate.utcMidnight(g.date)?.plus(12 * 3_600_000L)
        val fresh = at != null && abs(now - at) < 90 * 86_400_000L
        return age > (if (fresh || g.isUpcoming(now)) 7 else 45) * 86_400_000L
    }

    /** `hlFmt`: under 10 to the nearest half ("9½"), else whole; "—" for none. */
    fun fmt(h: Double): String = Edit.hlFmt(h)

    /** `hlUrl`: the game on howlongtobeat.com, or a search for its title. */
    fun url(g: Game): String = of(g)?.takeIf { it.id > 0 }?.let { "https://howlongtobeat.com/game/${it.id}" }
        ?: ("https://howlongtobeat.com/?q=" + java.net.URLEncoder.encode(g.title, "UTF-8").replace("+", "%20"))

    /** The request for these games (`hlRun`'s body): id, title, year and first platform. */
    fun body(l: List<Game>): String = JsonObject(
        mapOf(
            "src" to JsonPrimitive("hltb"),
            "items" to JsonArray(l.map { g ->
                JsonObject(
                    linkedMapOf(
                        "k" to JsonPrimitive(g.id), "t" to JsonPrimitive(g.title), "year" to JsonPrimitive(g.date.take(4)),
                        "plat" to JsonPrimitive(Rank.platList(g["platforms"]).firstOrNull().orEmpty()),
                    ),
                )
            }),
        ),
    ).toString()

    /**
     * `hlRun(force, g)`: asks HowLongToBeat about [g] (an error is asked once more 1.5 s later) and, when its hours follow
     * HowLongToBeat, hands [fill] the practice fields that change them. [force] (the sheet) skips the hour's wait after
     * an old helper. No network or a helper error records nothing (the web's `break`): the next ask tries again. Never throws.
     */
    suspend fun fetch(lib: Library, g: Game, now: () -> Long, force: Boolean, fill: (Map<String, JsonElement>) -> Unit) {
        if (off(lib) || g.comp || g.title.isEmpty()) return
        if (!force && now() - old < 3_600_000L) return
        if (!busy.add(g.id)) return
        seen.value++
        try {
            for (round in 0..1) {
                if (round == 1) kotlinx.coroutines.delay(1_500)
                val j = try {
                    Json.parseToJsonElement(post(body(listOf(g)))) as? JsonObject ?: return
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Recap.HelperError) {
                    if (e.message == "HTTP 404") old = now()
                    return
                } catch (e: Exception) {
                    return
                }
                // a helper from before v17 answers with the AI's guesses: don't use them, ask again in an hour
                if (Backup.str(j["src"]) != "hltb") { old = now(); return }
                val r = (j["res"] as? JsonObject)?.get(g.id) as? JsonObject ?: JsonObject(mapOf("err" to JsonPrimitive("no answer")))
                val at = now()
                val was = of(g)
                val h = when {
                    num(r["m"]) > 0 -> parse(JsonObject(r.filterKeys { it != "none" && it != "err" }), at)
                    Backup.truthy(r["none"]) -> Hl(big(r["id"]).toLong(), Backup.str(r["n"]), 0.0, 0.0, 0.0, 0, at, true, "")
                    was?.has == true -> Hl(was.id, was.n, was.m, was.x, was.c, was.cnt, at - 38 * 86_400_000L, false, "")
                    else -> Hl(0, "", 0.0, 0.0, 0.0, 0, at, false, Backup.str(r["err"]).ifEmpty { "error" }.take(80))
                }
                mem[g.id] = h
                seen.value++
                if (h.err.isNotEmpty() && round == 0) continue
                if (h.has && auto(g) && moves(g, h)) use(g, h)?.let(fill)
                return
            }
        } finally {
            busy.remove(g.id)
            seen.value++
        }
    }

    internal fun forget() { mem.clear(); busy.clear(); old = 0 }

    /** For tests: an answer as the backup would hold it. */
    internal fun put(id: String, o: JsonObject) { mem[id] = parse(o) }
}
