package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What the web app does to a backup on its way in (index.html `readBackup` 10225, `applyBackup` 10247, `load` 4252,
 * `dedupePlayed` 3510; android/parity/0-map/1-data.md §1.8, §5.3). The state stays the raw JSON tree, so every key the
 * app doesn't know survives; only what these steps change is changed. Pure: the clock and zone come in, so the golden
 * file (tools/golden.mjs, the web app's own answers) checks every step.
 */
object Backup {
    private val json = Json { isLenient = false }

    class Read(val ok: Boolean, val msg: String, val data: JsonObject? = null)

    /** `readBackup`: the same checks and words. */
    fun read(raw: String?): Read {
        val t = (raw ?: "").trim()
        if (t.isEmpty()) return Read(false, "Nothing there to restore.")
        if (t.startsWith("GAME QUEUE", ignoreCase = true)) return Read(false, "That's the Claude export, not the backup — wrong box.")
        if (t[0] != '{') return Read(false, "Doesn't start with { — wrong text, or the start got cut off.")
        if (t.last() != '}') return Read(false, "Cut off in transit — ${t.length} characters, no ending. Send it as a file.")
        val d = try {
            json.parseToJsonElement(t)
        } catch (e: Exception) {
            return Read(false, "Damaged. Send it as a file.")
        }
        val games = (d as? JsonObject)?.get("games") as? JsonArray ?: return Read(false, "Read fine, but there are no games in it.")
        val good = games.count { complete(it) }
        if (good == 0) return Read(false, "Read fine, but none of the games look complete.")
        val skipped = games.size - good
        return Read(
            true,
            "Restored $good" + (if (good == 1) " game" else " games") + (if (skipped > 0) " ($skipped skipped)" else "") +
                (if (truthy(d["rawgKey"])) " and a RAWG key" else ""),
            d,
        )
    }

    /**
     * `applyBackup` onto an empty phone: the secrets the web phone keeps for itself go, incomplete games go, the three
     * lists exist, one played record per game, and `v` / `rawgKey` go.
     */
    fun apply(d: JsonObject): JsonObject {
        val s = LinkedHashMap(d)
        Web.BK_SECRET.forEach { s.remove(it) }
        s["games"] = JsonArray(((s["games"] as? JsonArray) ?: JsonArray(emptyList())).filter { complete(it) })
        if (!truthy(s["duels"])) s["duels"] = JsonArray(emptyList())
        if (!truthy(s["played"])) s["played"] = JsonArray(emptyList())
        dedupePlayed(s)
        s.remove("v")
        s.remove("rawgKey")
        return JsonObject(s)
    }

    /**
     * `load()`'s changes to the data, in memory (the practice copy never saves them): the three lists exist, one played
     * record per game, a played game's state is "played", and an upcoming game that came out in the last 30 days is
     * "backlog". No made-up games on an empty backup (the web app's seed()).
     */
    fun load(state: JsonObject, now: Long, zone: ZoneId): JsonObject {
        val s = LinkedHashMap(state)
        for (k in listOf("games", "duels", "played")) if (!truthy(s[k])) s[k] = JsonArray(emptyList())
        dedupePlayed(s)
        val played = ((s["played"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? JsonObject)?.get("id") }.toSet()
        val td = JsDate.todayLocal(now, zone)
        val games = (s["games"] as? JsonArray)?.map { el ->
            val g = el as? JsonObject ?: return@map el
            var out = g
            if (g["id"] != null && g["id"] in played) out = JsonObject(LinkedHashMap(out).apply { put("state", JsonPrimitive("played")) })
            val st = str(out["state"]).ifEmpty { "upcoming" }
            val date = str(out["date"])
            val at = JsDate.utcMidnight(date)
            if (st == "upcoming" && date.isNotEmpty() && date <= td && at != null && now - at < 30 * JsDate.DAY) {
                out = JsonObject(LinkedHashMap(out).apply { put("state", JsonPrimitive("backlog")) })
            }
            out
        }
        if (games != null) s["games"] = JsonArray(games)
        return JsonObject(s)
    }

    /**
     * `dedupePlayed`: one record per game id. Duplicates merge (best score, most runs, any replay, earliest date);
     * records with no id go. Nothing changes when there's nothing to merge.
     */
    private fun dedupePlayed(s: MutableMap<String, JsonElement>) {
        val list = s["played"] as? JsonArray ?: return
        val by = LinkedHashMap<String, MutableMap<String, JsonElement>>()
        var dupes = 0
        var junk = 0
        for (el in list) {
            val p = el as? JsonObject
            val id = p?.get("id")
            if (p == null || !truthy(id)) { junk++; continue }
            val key = id.toString()
            val cur = by[key]
            if (cur == null) { by[key] = LinkedHashMap(p); continue }
            dupes++
            cur["real"] = jsNumber(maxOf(num(cur["real"], 0.0, 0.0, 10.0), num(p["real"], 0.0, 0.0, 10.0)))
            cur["runs"] = jsNumber(maxOf(num(cur["runs"], 1.0, 1.0, 12.0), num(p["runs"], 1.0, 1.0, 12.0)))
            // `cur.again || p2.again`: undefined drops the key when JSON.stringify writes it
            val again = if (truthy(cur["again"])) cur["again"] else p["again"]
            if (again == null) cur.remove("again") else cur["again"] = again
            val pt = p["t"]
            if (truthy(pt) && (!truthy(cur["t"]) || str(pt) < str(cur["t"]))) cur["t"] = pt!!
        }
        if (dupes > 0 || junk > 0) s["played"] = JsonArray(by.values.map { JsonObject(it) })
    }

    // ---- JavaScript's rules, for the few values these steps read ----

    /** `g && g.id && g.title` */
    private fun complete(el: JsonElement) = el is JsonObject && truthy(el["id"]) && truthy(el["title"])

    fun truthy(e: JsonElement?): Boolean = when (e) {
        null, JsonNull -> false
        is JsonPrimitive -> when {
            e.isString -> e.content.isNotEmpty()
            e.booleanOrNull != null -> e.booleanOrNull!!
            else -> e.doubleOrNull.let { it != null && it != 0.0 && !it.isNaN() }
        }
        else -> true // arrays and objects, even empty ones
    }

    /** A value as JavaScript's String() would print a string or number ("" for missing). */
    fun str(e: JsonElement?): String = when (e) {
        null, JsonNull -> ""
        is JsonPrimitive -> e.content
        else -> ""
    }

    /** `num(v, d, lo, hi)` (index.html 8393). */
    fun num(e: JsonElement?, d: Double, lo: Double, hi: Double): Double {
        val n: Double = when (e) {
            null, JsonNull -> return d
            is JsonPrimitive -> when {
                e.isString -> if (e.content.isEmpty()) return d else e.content.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
                e.booleanOrNull != null -> if (e.booleanOrNull!!) 1.0 else 0.0
                else -> e.doubleOrNull ?: Double.NaN
            }
            else -> Double.NaN
        }
        if (!n.isFinite()) return d
        return minOf(hi, maxOf(lo, n))
    }

    /** A number the way JSON.stringify writes it: whole numbers without ".0". */
    fun jsNumber(d: Double): JsonPrimitive =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) JsonPrimitive(d.toLong()) else JsonPrimitive(d)
}

/**
 * JavaScript's two kinds of dates, chosen per call site as the web code does (1-data.md §5.1): `new Date("YYYY-MM-DD")`
 * is UTC midnight; "today" for comparisons is the local date (`toLocaleDateString("en-CA")`), new records use the UTC
 * date (`toISOString().slice(0, 10)`).
 */
object JsDate {
    const val DAY = 86_400_000L
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** `new Date(s)` for the stored forms (ms), or null where JavaScript gets NaN. */
    fun utcMidnight(s: String?): Long? {
        if (s.isNullOrEmpty()) return null
        if (DATE.matches(s)) return runCatching { LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
    }

    fun todayLocal(now: Long, zone: ZoneId): String = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
    fun todayUtc(now: Long): String = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString()

    /** `days(d)` (index.html 7314): "3 days away", "1 day away", "Out today", "Out now" (also for a date JS can't read). */
    fun days(d: String?, now: Long): String {
        if (d.isNullOrEmpty()) return ""
        val at = utcMidnight(d) ?: return "Out now"
        val n = Math.ceil((at - now) / DAY.toDouble()).toLong()
        return if (n > 0) "$n" + (if (n == 1L) " day away" else " days away") else if (n == 0L) "Out today" else "Out now"
    }
}
