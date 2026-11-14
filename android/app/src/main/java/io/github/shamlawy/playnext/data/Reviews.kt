package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Reviews (index.html 20410-20556, android/parity/2-map/2-edit.md §7a): critics from RAWG (your platform's Metacritic
 * when RAWG has one) and Steam's players through the helper's `POST /reviews` (read only). The web keeps them in the
 * backup (`g.rv`); here a new answer lives in memory while the app runs, over the backup's copy ([of]), so the page's
 * card and Nexi's guess ([Queue.rvFor]) read the newest.
 *
 * When to ask: the web sweeps up to 10 fresh games 30 s after it opens and saves; this copy can't save, so the page asks
 * for the game it shows, 900 ms after it opens, when that game is fresh (out within the last 60 days) and due (8 hours
 * in its first two weeks, then 2 days). A critic score found for a game with none fills `g.critic` through the practice
 * copy, as the web does. No network records nothing. "Reviews are in" toasts and Nexi's line wait for Phase 9.
 */
object Reviews {
    const val MAX = 60
    const val SOON = 3

    fun off(lib: Library) = Backup.truthy(lib["rvOff"])

    private val mem = ConcurrentHashMap<String, JsonObject>()
    private val busy = ConcurrentHashMap.newKeySet<String>()

    /** What changed (a counter): the page's card reads it so an answer shows. */
    val seen = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The helper's call and RAWG's GET; tests swap them. */
    @Volatile internal var post: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/reviews"), body)) { r ->
            if (!r.isSuccessful) throw Recap.HelperError("HTTP ${r.code}")
            r.body.string()
        }
    }
    @Volatile internal var get: suspend (String) -> Pair<Int, String> = { url ->
        io.github.shamlawy.playnext.net.Net.call(okhttp3.Request.Builder().url(url).build()) { it.code to it.body.string() }
    }

    /** `g.rv`: this run's answer, else the backup's. */
    fun of(g: Game): JsonObject? = mem[g.id] ?: (g["rv"] as? JsonObject)

    fun busy(g: Game) = g.id in busy

    private fun n(e: JsonElement?) = Backup.num(e, 0.0, -1e15, 1e15)

    /** `stIdOf`: the game's Steam id (`stId`, `pxSt`, `steamId`), or one this run's answer found; 0 for none. */
    fun stId(g: Game): Long = listOf(of(g)?.get("stId"), g["stId"], g["pxSt"], g["steamId"]).map { n(it) }.firstOrNull { it > 0 }?.toLong() ?: 0

    /** `rvAge`: days from the release to today on the phone (negative: still to come); null without a date. */
    fun age(g: Game, now: Long, zone: ZoneId): Int? {
        val d = runCatching { LocalDate.parse(g.date) }.getOrNull() ?: return null
        return ChronoUnit.DAYS.between(d, Instant.ofEpochMilli(now).atZone(zone).toLocalDate()).toInt()
    }

    /** `rvWant`: worth watching: not played, dropped or competitive, dated (not TBA), from 3 days before release to 60 after. */
    fun want(lib: Library, rk: Rank, g: Game, now: Long, zone: ZoneId): Boolean {
        if (off(lib) || g.comp || rk.isPlayed(g) || g.rawState == "dropped" || g.date.isEmpty() || Backup.str(g["status"]) == "tba") return false
        val a = age(g, now, zone) ?: return false
        return a in -SOON..MAX
    }

    /** `rvSteamOk`: on Steam, or maybe (not marked "not on Steam", and no platforms or a PC one). */
    fun steamOk(g: Game): Boolean {
        if (stId(g) > 0) return true
        val no = n(of(g)?.get("noSt")) == 1.0 || n(g["rvSt"]) == -1.0
        if (no) return false
        val pl = Rank.platList(g["platforms"])
        return pl.isEmpty() || pl.any { Regex("pc|steam|windows|deck", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    }

    /** `rvDue`: 8 hours in its first two weeks, then 2 days. */
    fun due(g: Game, now: Long, zone: ZoneId): Boolean {
        val at = n(of(g)?.get("at")).toLong()
        val a = age(g, now, zone) ?: 0
        return now - at > if (a <= 14) 8 * 3_600_000L else 2 * 86_400_000L
    }

    /** `wiAgo`: "today", "yesterday", "3 days ago", "2 weeks ago", "4 months ago". */
    fun ago(t: Long, now: Long): String {
        val d = Math.floorDiv(now - t, 86_400_000L)
        return when {
            d <= 0 -> "today"
            d == 1L -> "yesterday"
            d < 14 -> "$d days ago"
            d < 60 -> "${Math.round(d / 7.0)} weeks ago"
            else -> "${Math.round(d / 30.0)} months ago"
        }
    }

    /** `pn`: a platform name as RAWG writes it ("PS5" = "playstation 5", Steam and Windows = "pc"). */
    private fun pn(x: String) = x.lowercase().replace(Regex("\\bps\\s?(\\d)\\b"), "playstation $1").replace(Regex("\\bsteam\\b|\\bwindows\\b"), "pc").trim()

    /** RAWG's game answer into [rv]: your platform's Metacritic first (your order), else its own; players' rating with 20+. */
    internal fun rawgInto(g: Game, d: JsonObject, rv: MutableMap<String, JsonElement>) {
        val mine = Rank.platList(g["platforms"]).map(::pn)
        val mp = (d["metacritic_platforms"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { n(it["metascore"]) > 0 }
        var pick: JsonObject? = null
        for (p in mine) {
            pick = mp.firstOrNull { x -> val nm = pn(Backup.str((x["platform"] as? JsonObject)?.get("name"))); nm.contains(p) || p.contains(nm) }
            if (pick != null) break
        }
        val mc = if (pick != null) n(pick["metascore"]) else n(d["metacritic"])
        if (mc > 0) {
            rv["mc"] = Backup.jsNumber(mc)
            rv["mcP"] = JsonPrimitive(if (pick != null) Backup.str((pick["platform"] as? JsonObject)?.get("name")) else "")
        }
        if (n(d["ratings_count"]) >= 20 && n(d["rating"]) > 0) {
            rv["us"] = Backup.jsNumber(Rank.jsRound(n(d["rating"]) * 20) / 10.0)
            rv["un"] = Backup.jsNumber(n(d["ratings_count"]))
        }
    }

    private suspend fun rawg(key: String, path: String): JsonObject? {
        val url = ("https://api.rawg.io/api/$path").toHttpUrl().newBuilder().addQueryParameter("key", key).build()
        val (code, text) = get(url.toString())
        if (code == 401) throw Recap.HelperError("HTTP 401")
        if (code !in 200..299) return null
        return Json.parseToJsonElement(text) as? JsonObject
    }

    /**
     * `rvWatch(false, g)` for one game: RAWG (with your key) and Steam's players (when [steamOk]), then the time it was
     * checked. [fill] gets the practice fields for an empty `g.critic`. Never throws; no answer at all records nothing.
     */
    suspend fun fetch(lib: Library, g: Game, rawgKey: String, cc: String, now: () -> Long, fill: (Map<String, JsonElement>) -> Unit) {
        if (off(lib) || g.comp) return
        if (!busy.add(g.id)) return
        seen.value++
        try {
            val rv = LinkedHashMap<String, JsonElement>(of(g) ?: emptyMap())
            var reached = false
            if (rawgKey.isNotEmpty()) {
                try {
                    val id = Backup.str(g["rawgId"])
                    val d = if (id.isNotEmpty()) rawg(rawgKey, "games/" + java.net.URLEncoder.encode(id, "UTF-8")) else {
                        val r = rawg(rawgKey, "games?search=" + java.net.URLEncoder.encode(g.title, "UTF-8") + "&search_precise=true&page_size=3")
                        val best = (r?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { Lookup.titleMatch(g.title, Backup.str(it["name"])) }
                        best?.let { rawg(rawgKey, "games/" + Backup.str(it["id"])) }
                    }
                    reached = true
                    if (d != null) rawgInto(g, d, rv)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is Recap.HelperError) reached = true
                }
            }
            if (steamOk(g)) {
                try {
                    val body = JsonObject(mapOf("cc" to JsonPrimitive(cc), "items" to JsonArray(listOf(JsonObject(mapOf("k" to JsonPrimitive(g.id), "t" to JsonPrimitive(g.title), "st" to JsonPrimitive(stId(g))))))))
                    val j = Json.parseToJsonElement(post(body.toString())) as? JsonObject
                    reached = true
                    val r = (j?.get("res") as? JsonObject)?.get(g.id) as? JsonObject
                    if (r != null) {
                        if (Backup.truthy(r["none"])) rv["noSt"] = JsonPrimitive(1)
                        else {
                            if (n(r["st"]) > 0 && stId(g) == 0L) rv["stId"] = Backup.jsNumber(n(r["st"]))
                            if (r["n"] != null && r["n"] !is kotlinx.serialization.json.JsonNull && !Backup.truthy(r["err"])) {
                                rv["st"] = JsonObject(mapOf("d" to JsonPrimitive(Backup.str(r["d"])), "p" to (r["p"] ?: kotlinx.serialization.json.JsonNull), "n" to Backup.jsNumber(n(r["n"]))))
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is Recap.HelperError) reached = true
                }
            }
            if (!reached) return
            rv["at"] = JsonPrimitive(now())
            mem[g.id] = JsonObject(rv)
            seen.value++
            val mc = n(rv["mc"])
            if (mc > 0 && !(n(g["critic"]) > 0)) fill(mapOf("critic" to Backup.jsNumber(mc), "criticFrom" to JsonPrimitive("RAWG")))
        } finally {
            busy.remove(g.id)
            seen.value++
        }
    }

    internal fun forget() { mem.clear(); busy.clear() }
}
