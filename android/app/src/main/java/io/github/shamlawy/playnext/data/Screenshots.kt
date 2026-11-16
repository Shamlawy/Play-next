package io.github.shamlawy.playnext.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * A game page's screenshots fetched by themselves (`shotsFetch`, android/parity/2-map/3-critic.md M5; index.html 22628,
 * rewritten by v240 at 22754): the stores' (`POST /art`: Steam, else PlayStation, else Nintendo, Nintendo first for a
 * game only on Nintendo machines, `gpPickShots`) and RAWG's when the stores have fewer than 4, five at most. Read only:
 * the web writes `g.shots`, `g.shotsSrc`, `g.shotsAt`, `g.shotsNo`; here the answer lives in memory until the app is
 * opened again, and the backup's copy is read as it is.
 */
object Screenshots {
    /** One screenshot: its address, the big copy for the viewer, the small one for the strip, and where it's from. */
    class Shot(val u: String, val b: String, val t: String, val s: String)

    /** An answer: the shots (none: the stores and RAWG had nothing, `g.shotsNo`), where from, when. */
    class Got(val l: List<Shot>, val src: String, val at: Long)

    private val mem = ConcurrentHashMap<String, Got>()
    private val busy = ConcurrentHashMap.newKeySet<String>()
    private val tried = ConcurrentHashMap<String, Long>()
    private val rawgLook = ConcurrentHashMap<String, Long>()

    /** `SHOT_ERR`: the last failure for a game (a failed call is never "this game has none"). */
    val err = ConcurrentHashMap<String, String>()

    /** What changed (a counter), so the page's strip redraws when an answer comes in. */
    val seen = MutableStateFlow(0)

    private const val DAY = JsDate.DAY

    // ---- pure rules ----

    /**
     * `gpPickShots`: the stores' screenshots, Steam first, then PlayStation, Nintendo, Xbox (Nintendo first when every
     * platform is Nintendo's): the first store with 3 or more gives up to 5; else all of them in that order, up to 5.
     * Returns the store whose shots lead ("" for none) and the shots.
     */
    fun pick(pics: List<Art.Pic>, plats: List<String>): Pair<String, List<Art.Pic>> {
        val shots = pics.filter { it.r == "shot" && it.u.isNotEmpty() }
        val nOnly = plats.isNotEmpty() && plats.all { NINTENDO.containsMatchIn(it) }
        val order = if (nOnly) listOf("ns", "st", "ps", "xb") else listOf("st", "ps", "ns", "xb")
        for (s in order) { val l = shots.filter { it.s == s }; if (l.size >= 3) return s to l.take(5) }
        val any = order.flatMap { s -> shots.filter { it.s == s } }
        return (any.firstOrNull()?.s ?: "") to any.take(5)
    }

    private val NINTENDO = Regex("nintendo|switch|wii|3ds", RegexOption.IGNORE_CASE)
    private val STEAM_SS = Regex("steamstatic\\.com/.*/ss_[0-9a-f]+\\.jpg(\\?|$)")

    /** `gpBig`: a size that suits a phone: Steam's 1920 copy, the stores' scaled ones. */
    fun big(u: String): String = when {
        STEAM_SS.containsMatchIn(u) -> u.replace(Regex("\\.jpg(?=\\?|$)"), ".1920x1080.jpg")
        u.contains("image.api.playstation.com") -> u.substringBefore("?") + "?w=1280&h=1280"
        u.contains("store-images.s-microsoft.com") -> u.substringBefore("?") + "?w=1280&h=720"
        else -> u
    }

    /** `shotThumb`: the strip's small copy (a store's own thumbnail, else its scaled copy). */
    fun thumb(t: String, u: String): String {
        if (t.isNotEmpty()) return t
        return when {
            STEAM_SS.containsMatchIn(u) -> u.replace(Regex("\\.jpg(?=\\?|$)"), ".600x338.jpg")
            u.contains("image.api.playstation.com") -> u.substringBefore("?") + "?w=640&h=640"
            u.contains("store-images.s-microsoft.com") -> u.substringBefore("?") + "?w=640&h=360"
            u.contains("assets.nintendo.com/image/upload/") -> u.replace("/image/upload/", "/image/upload/c_scale,w_640/")
            u.contains("media.rawg.io/media/screenshots/") -> u.replace("/media/screenshots/", "/media/resize/640/-/screenshots/")
            else -> u
        }
    }

    /**
     * `shotsDue` (v240): a game with shots is asked again after 60 days; one without is asked unless the last answer
     * (made by this rule, `shotsNoV` 240) said "none" under 3 days ago. This run's answer counts before the backup's.
     */
    fun due(g: Game, now: Long): Boolean {
        val m = mem[g.id]
        val has = if (m != null) m.l.isNotEmpty() else (g["shots"] as? JsonArray).orEmpty().isNotEmpty()
        val at = m?.at ?: Backup.num(g["shotsAt"], 0.0, 0.0, 1e15).toLong()
        if (has) return now - at > 60 * DAY
        val none = if (m != null) true else Backup.truthy(g["shotsNo"]) && Backup.num(g["shotsNoV"], 0.0, 0.0, 1e6).toInt() == 240
        return !(none && now - at < 3 * DAY)
    }

    /** `artBad`: no answer, an error, or every store that was asked failed. */
    fun bad(a: JsonObject?): Boolean {
        if (a == null || Backup.str(a["err"]).isNotEmpty()) return true
        val f = a["found"] as? JsonObject ?: return false
        return f.isNotEmpty() && f.values.all { v -> (v as? JsonObject)?.let { Backup.truthy(it["err"]) } == true }
    }

    /** The page's strip: this run's shots when it has some, else the backup's (`g.shots`); each one's small and big copy. */
    fun of(g: Game): List<Pair<String, String>>? = mem[g.id]?.l?.takeIf { it.isNotEmpty() }?.map { thumb(it.t, it.u) to it.b.ifEmpty { it.u } }

    /** Where the strip's shots are from ("Steam", "Steam and RAWG"), this run's first. */
    fun src(g: Game): String = mem[g.id]?.takeIf { it.l.isNotEmpty() }?.src ?: Backup.str(g["shotsSrc"])

    // ---- the calls ----

    /** The stores' pictures (`artStores` through [ArtNet.stores]): a test hands in its own. */
    @Volatile internal var stores: suspend (title: String, st: Long, cc: String, pscc: String) -> JsonObject = { t, st, cc, pscc -> ArtNet.stores(t, st, cc, pscc, false) }
    @Volatile internal var rawgGet: suspend (String) -> Pair<Int, String> = { url ->
        io.github.shamlawy.playnext.net.Net.call(okhttp3.Request.Builder().url(url).build()) { it.code to it.body.string() }
    }
    /** The pause before asking the stores again after a failure (4 s on the web). */
    @Volatile internal var retryWait = 4_000L

    private suspend fun rawg(key: String, path: String): JsonObject? {
        val url = ("https://api.rawg.io/api/$path").toHttpUrl().newBuilder().addQueryParameter("key", key).build()
        val (code, text) = rawgGet(url.toString())
        if (code !in 200..299) return null
        return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    /** `shotRawgId`: the game's RAWG id, else RAWG's closest title (.85 alike), looked up at most every 14 days. */
    private suspend fun rawgId(g: Game, key: String, now: Long): String {
        if (Backup.truthy(g["rawgId"])) return Backup.str(g["rawgId"])
        if (key.isEmpty() || now - (rawgLook[g.id] ?: Long.MIN_VALUE / 2) < 14 * DAY) return ""
        rawgLook[g.id] = now
        return try {
            val r = rawg(key, "games?search=" + java.net.URLEncoder.encode(g.title, "UTF-8") + "&search_precise=true&page_size=6")
            val best = (r?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { it to Similar.same(Backup.str(it["name"]), g.title) }.maxByOrNull { it.second }
            if (best != null && best.second >= .85) Backup.str(best.first["id"]).also { rawgLook.remove(g.id) } else ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * `shotsFetch(g, force)`: when [due] (or [force]), and not asked in the last minute unless forced: the stores (asked
     * again once after [retryWait] when they failed), RAWG's to fill up to 4, five kept. A failure with nothing found is
     * kept in [err] and asked again on the next open; never throws.
     */
    suspend fun fetch(lib: Library, g: Game, rawgKey: String, cc: String, now: () -> Long, force: Boolean = false) {
        val t0 = now()
        if (!force && (!due(g, t0) || t0 - (tried[g.id] ?: Long.MIN_VALUE / 2) < 60_000)) return
        if (!busy.add(g.id)) return
        tried[g.id] = t0
        try {
            val plats = Rank.platList(g["platforms"])
            val st = Reviews.stId(g)
            val pscc = Art.psCC(lib, cc)
            var art = stores(g.title, st, cc, pscc)
            if (bad(art) && Backup.str(art["err"]) != "helper-old") {
                ArtNet.drop(g.title)
                delay(retryWait)
                art = stores(g.title, st, cc, pscc)
            }
            val (s, picked) = pick(Art.stores(art), plats)
            // a store's own thumbnail only (Art.stores puts the address there when it has none)
            var l = picked.map { p -> Shot(p.u, big(p.u), thumb(if (p.th != p.u) p.th else "", p.u), p.s) }
            var src = if (s.isNotEmpty()) Art.srcName(s) else ""
            if (l.size < 4) {
                val id = rawgId(g, rawgKey, t0)
                val rs = if (id.isNotEmpty() && rawgKey.isNotEmpty()) {
                    try { rawg(rawgKey, "games/" + java.net.URLEncoder.encode(id, "UTF-8") + "/screenshots?page_size=6") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
                } else null
                val r = (rs?.get("results") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> Backup.str(o["image"]).takeIf(String::isNotEmpty) } }.take(5 - l.size).map { u ->
                    Shot(u, u.replace("/media/screenshots/", "/media/resize/1280/-/screenshots/"), u.replace("/media/screenshots/", "/media/resize/640/-/screenshots/"), "rw")
                }
                if (r.isNotEmpty()) src = if (l.isNotEmpty()) "$src and RAWG" else "RAWG"
                l = l + r
            }
            if (l.isEmpty() && bad(art)) {
                ArtNet.drop(g.title)
                val e = Backup.str(art["err"])
                err[g.id] = (if (e.isNotEmpty()) e else "every store failed").take(80)
                return
            }
            err.remove(g.id)
            mem[g.id] = Got(l.take(5), src, now())
        } catch (e: kotlinx.coroutines.CancellationException) {
            // a neighbour page swiped away mid-fetch: coming back to it asks again, not after the minute's wait
            tried.remove(g.id)
            throw e
        } catch (e: Exception) {
            err[g.id] = (e.message ?: e.toString()).take(80)
        } finally {
            busy.remove(g.id)
            seen.value++
        }
    }

    internal fun forget() {
        mem.clear(); busy.clear(); tried.clear(); rawgLook.clear(); err.clear(); retryWait = 4_000L
        stores = { t, st, cc, pscc -> ArtNet.stores(t, st, cc, pscc, false) }
        rawgGet = { url -> io.github.shamlawy.playnext.net.Net.call(okhttp3.Request.Builder().url(url).build()) { it.code to it.body.string() } }
    }
}
