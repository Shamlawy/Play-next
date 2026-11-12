package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.net.Net
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Edit's calls for pictures and facts: SteamGridDB through the helper (`/sgdb/...`, the helper holds the key), the
 * stores' art (`POST /art`, kept for the session per title, a failure not kept) and RAWG with your key (screenshots and
 * Pull). Every call reads; none changes a record, so the practice copy lets them all through ([io.github.shamlawy.playnext.net.Routes]).
 */
object ArtNet {
    private val json = Json { ignoreUnknownKeys = true }
    private const val RAWG = "https://api.rawg.io/api/"

    /** A call that answered with a message for the person (the web's thrown `Error(text)`). */
    class Said(text: String) : IOException(text)

    /** The two ways out (a test hands in its own answers): a GET and a JSON POST, each giving the status and the body. */
    @Volatile internal var get: suspend (String) -> Pair<Int, String> = { url -> Net.call(Request.Builder().url(url).build()) { it.code to it.body.string() } }
    @Volatile internal var post: suspend (String, String) -> Pair<Int, String> = { url, body -> Net.call(Net.postJson(url, body)) { it.code to it.body.string() } }

    /** Forget the stores' answers kept this session (tests). */
    internal fun forget() = storesKept.clear()

    private fun parse(body: String?): JsonElement? = runCatching { json.parseToJsonElement(body.orEmpty()) }.getOrNull()

    /** `sgdbGet(path)`: SteamGridDB's `data` list. */
    suspend fun sg(path: String): JsonArray {
        val r = try {
            get(Net.helper("/sgdb$path"))
        } catch (e: IOException) {
            if (e is io.github.shamlawy.playnext.net.WriteBlocked) throw e
            throw Said("Couldn't reach your SteamGridDB helper. Check it's deployed on Cloudflare.")
        }
        if (r.first !in 200..299) throw Said(if (r.first == 401 || r.first == 403) "Key rejected — check the SGDB_KEY secret on your Cloudflare helper." else "SteamGridDB said ${r.first}")
        return ((parse(r.second) as? JsonObject)?.get("data") as? JsonArray) ?: JsonArray(emptyList())
    }

    private val storesKept = ConcurrentHashMap<String, JsonObject>()

    /**
     * `artStores`: the stores' pictures for [title] (`{found, pics}`), or `{err}`; "helper-old" when the helper predates
     * `/art`. Kept for the session per title, Steam id and wants.
     */
    suspend fun stores(title: String, st: Long, cc: String, pscc: String, sw2: Boolean): JsonObject {
        val key = Art.normT(title) + "|" + st + "|"
        storesKept[key]?.let { return it }
        val body = JsonObject(mapOf("t" to JsonPrimitive(title), "st" to JsonPrimitive(st), "cc" to JsonPrimitive(cc), "pscc" to JsonPrimitive(pscc), "sw2" to JsonPrimitive(sw2)))
        return try {
            val (code, text) = post(Net.helper("/art"), body.toString())
            val j = parse(text) as? JsonObject ?: JsonObject(emptyMap())
            val err = Backup.str(j["error"])
            if (code == 404 && err.contains("not found", ignoreCase = true)) return err("helper-old")
            if (code !in 200..299 || err.isNotEmpty()) return err(err.ifEmpty { "The helper said $code" })
            j.also { storesKept[key] = it }
        } catch (e: IOException) {
            err(e.message ?: "network")
        }
    }

    private fun err(t: String) = JsonObject(mapOf("err" to JsonPrimitive(t), "pics" to JsonArray(emptyList())))

    private suspend fun rawg(key: String, path: String): JsonObject {
        val url = (RAWG + path).toHttpUrl().newBuilder().addQueryParameter("key", key).build()
        val (code, text) = get(url.toString())
        if (code !in 200..299) throw Said(if (code == 401) "Key rejected." else "RAWG said $code")
        return parse(text) as? JsonObject ?: JsonObject(emptyMap())
    }

    /** `rawgShots(title)`: RAWG's screenshots (the draft's RAWG id while the title is still its own), none without a key. */
    suspend fun rawgShots(key: String, title: String, rawgId: String): List<Art.Pic> {
        if (key.isEmpty() || title.isEmpty()) return emptyList()
        return try {
            var id = rawgId
            if (id.isEmpty()) {
                val r = rawg(key, "games?search=" + java.net.URLEncoder.encode(title, "UTF-8") + "&search_precise=true&page_size=1")
                id = ((r["results"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("id")?.let(Backup::str).orEmpty()
            }
            if (id.isEmpty()) emptyList() else Art.rawgShots(rawg(key, "games/$id/screenshots?page_size=20"))
        } catch (e: IOException) {
            emptyList()
        }
    }

    /** Where the stores' search runs: your regions, the draft's Steam id, Switch 2 when the draft has it. */
    class Ask(val title: String, val st: Long, val cc: String, val pscc: String, val sw2: Boolean, val rawgKey: String, val rawgId: String)

    private fun sgTop(hits: JsonArray, logos: JsonArray, heroes: JsonArray, grids: JsonArray): Art.Top? {
        val first = hits.firstOrNull() as? JsonObject ?: return null
        return Art.sgTop(Backup.str(first["name"]), Art.sg(logos, "art", "Logo"), Art.sg(heroes, "hero", "Hero"), Art.sg(grids, "cover", "Grid"))
    }

    /** Auto-pick all (`sgdbAuto` + the stores): the picks and what to say; a SteamGridDB failure with no stores is said. */
    suspend fun auto(a: Ask): Pair<Art.Top?, String?> = coroutineScope {
        val stores = async { stores(a.title, a.st, a.cc, a.pscc, a.sw2) }
        var err: String? = null
        val t = try {
            val hits = sg("/search/autocomplete/" + enc(a.title))
            val id = (hits.firstOrNull() as? JsonObject)?.get("id")?.let(Backup::str).orEmpty()
            if (id.isEmpty()) null else {
                val one = { p: String -> async { runCatching { sg(p) }.getOrElse { JsonArray(emptyList()) } } }
                val l = one("/logos/game/$id")
                val h = one("/heroes/game/$id")
                val g = one("/grids/game/$id?dimensions=600x900,660x930")
                sgTop(hits, l.await(), h.await(), g.await())
            }
        } catch (e: IOException) {
            err = e.message
            null
        }
        val s = stores.await()
        val out = Art.withStores(t, a.title, s)
        (if (out == null && t == null && err != null && Art.stores(s).isEmpty()) null to err else out to null)
    }

    /** Browse and choose (`sgdbFind`): every picture from every place, and SteamGridDB's failure if it had one. */
    class Found(val pics: List<Art.Pic>, val sgErr: String?, val old: Boolean)

    suspend fun browse(a: Ask): Found = coroutineScope {
        val shots = async { rawgShots(a.rawgKey, a.title, a.rawgId) }
        val stores = async { stores(a.title, a.st, a.cc, a.pscc, a.sw2) }
        var sgErr: String? = null
        val hits = try { sg("/search/autocomplete/" + enc(a.title)) } catch (e: IOException) { sgErr = e.message; JsonArray(emptyList()) }
        val id = (hits.firstOrNull() as? JsonObject)?.get("id")?.let(Backup::str).orEmpty()
        val one = { p: String -> async { if (id.isEmpty()) JsonArray(emptyList()) else runCatching { sg(p) }.getOrElse { JsonArray(emptyList()) } } }
        val logos = one("/logos/game/$id")
        val heroes = one("/heroes/game/$id")
        val grids = one("/grids/game/$id?dimensions=600x900,660x930")
        val s = stores.await()
        val pics = Art.sg(grids.await(), "cover", "Grid") + Art.sg(heroes.await(), "hero", "Hero") + Art.sg(logos.await(), "art", "Logo") +
            shots.await() + Art.stores(s)
        Found(pics, sgErr, Backup.str(s["err"]) == "helper-old")
    }

    /** Pull score, date, tags and blurb (`rawgLookup`): RAWG's game by its id, else the closest title, with its description. */
    suspend fun pull(key: String, g: Game): Lookup.Found {
        if (key.isEmpty()) return Lookup.Found.Err("No RAWG key saved — add one in Settings.")
        val rawgId = Backup.str(g["rawgId"])
        return try {
            val o: JsonObject = if (rawgId.isNotEmpty()) rawg(key, "games/" + enc(rawgId)) else {
                val r = rawg(key, "games?search=" + java.net.URLEncoder.encode(g.title, "UTF-8") + "&page_size=1")
                var hit = (r["results"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return Lookup.Found.Err("RAWG has nothing matching that title.")
                val id = Backup.str(hit["id"])
                if (Backup.str(hit["description_raw"]).isEmpty() && id.isNotEmpty()) {
                    runCatching { rawg(key, "games/$id") }.getOrNull()?.let { hit = JsonObject(hit + it) }
                }
                hit
            }
            Lookup.read(o, g)
        } catch (e: Said) {
            Lookup.Found.Err(e.message ?: "RAWG said no")
        } catch (e: IOException) {
            Lookup.Found.Err("Couldn't reach RAWG. Check you're online.")
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
