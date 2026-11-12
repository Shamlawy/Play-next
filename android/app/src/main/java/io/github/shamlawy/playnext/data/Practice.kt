package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneId

/**
 * The one practice copy every screen writes to (android/parity/2-map/3-critic.md M7). Until switch day the app never
 * writes the backup: what you change (a game you start playing or finish, a game you delete from the Queue) lives in
 * practice keys next to Customise's ([Games.change]), and [laid] puts them over the backup whenever Games builds a copy,
 * so the tiles, the Queue's rows and counts, the card back, the header numbers and the page's swipe order all follow at
 * once. Reopening the app starts from the backup again ("Practice copy: changes reset when the app reopens").
 *
 * Pure: each op gives the practice keys' new values; [Games.change] keeps them in memory only.
 */
object Practice {
    /** `{gameId: {field: value}}`: the fields you changed on a game; a JsonNull value takes the field away. */
    const val GAMES = "pn:g"

    /** `{gameId: record | null}`: a game's `S.played` record as you left it (null: it has none any more). */
    const val RECS = "pn:p"

    /** `[gameId]`: games you deleted (the Queue's swipe, with Undo), gone with their finishes and duels (`qdel`). */
    const val GONE = "pn:gone"

    /** The keys that change your games (not Customise's): a newer backup drops them ([Games] build). */
    val KEYS = setOf(GAMES, RECS, GONE)

    /** Left in the built copy (never in a backup): the games a practice change touched, for the page's thin line. */
    const val CHANGED = "pn:changed"

    private fun obj(e: JsonElement?): Map<String, JsonElement> = (e as? JsonObject) ?: emptyMap()
    private fun ids(e: JsonElement?): List<String> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    /** The games a practice change touched in [lib] (fields or a record; a deleted game is gone from it). */
    fun changed(lib: Library): Set<String> = ids(lib[CHANGED]).toSet()

    /** The web's `S` with the practice keys laid over its games, finishes and duels, and the keys themselves gone. */
    fun laid(s: JsonObject): JsonObject {
        val edits = obj(s[GAMES])
        val recs = obj(s[RECS])
        val gone = ids(s[GONE]).toSet()
        if (edits.isEmpty() && recs.isEmpty() && gone.isEmpty()) return JsonObject(s.filterKeys { it != GAMES && it != RECS && it != GONE })
        val out = LinkedHashMap(s.filterKeys { it != GAMES && it != RECS && it != GONE })
        (s["games"] as? JsonArray)?.let { a ->
            out["games"] = JsonArray(a.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull e
                val id = Backup.str(o["id"])
                if (id in gone) return@mapNotNull null
                val f = obj(edits[id])
                if (f.isEmpty()) o else JsonObject(LinkedHashMap(o).apply { for ((k, v) in f) if (v is JsonNull) remove(k) else put(k, v) })
            })
        }
        val played = (s["played"] as? JsonArray).orEmpty().filter { e ->
            val id = (e as? JsonObject)?.get("id")?.let(Backup::str)
            id == null || (id !in gone && id !in recs)
        } + recs.filter { (id, r) -> r is JsonObject && id !in gone }.values
        if (s["played"] != null || recs.isNotEmpty()) out["played"] = JsonArray(played)
        (s["duels"] as? JsonArray)?.let { a ->
            out["duels"] = JsonArray(a.filter { e -> (e as? JsonObject)?.let { d -> Backup.str(d["a"]) !in gone && Backup.str(d["b"]) !in gone } ?: true })
        }
        out[CHANGED] = JsonArray((edits.keys + recs.keys - gone).distinct().map(::JsonPrimitive))
        return JsonObject(out)
    }

    /** [cur] (the practice [GAMES] now) with [fields] set on game [id]; a JsonNull takes a field away. */
    fun fields(cur: JsonElement?, id: String, fields: Map<String, JsonElement>): JsonObject {
        val all = obj(cur)
        return JsonObject(all + (id to JsonObject(obj(all[id]) + fields)))
    }

    /** [cur] (the practice [RECS] now) with game [id]'s record set, or taken away when [rec] is null. */
    fun record(cur: JsonElement?, id: String, rec: JsonObject?): JsonObject = JsonObject(obj(cur) + (id to (rec ?: JsonNull)))

    /** [cur] (the practice [GONE] now) with [id] deleted, or back when [on] is false. */
    fun gone(cur: JsonElement?, id: String, on: Boolean): JsonArray =
        ids(cur).filter { it != id }.let { l -> JsonArray((if (on) l + id else l).map(::JsonPrimitive)) }

    /**
     * "Start playing" / "Pick it up again" (index.html 22117): `syncPlayed(g, "playing")` (its v218 hook sets `g.plAt`
     * when it's empty; leaving played takes the record away) then `g.state = "playing"`. The practice keys' new values.
     */
    fun start(lib: Library, g: Game, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val f = LinkedHashMap<String, JsonElement>()
        f["state"] = JsonPrimitive("playing")
        if (!Backup.truthy(g["plAt"])) f["plAt"] = JsonPrimitive(now)
        val out = linkedMapOf<String, JsonElement>(GAMES to fields(cur(GAMES), g.id, f))
        if (lib.played.any { it.id == g.id }) out[RECS] = record(cur(RECS), g.id, null)
        return out
    }

    /**
     * "I finished it" (`fnDone`, index.html 20359): `g.state = "played"` first (so the v218 hook keeps `g.plAt`), then
     * `syncPlayed` adds the record `{id, real: 0, t}` with today's date on the phone (en-CA). The practice keys' new
     * values, with Then & now's snapshot. (The Score sheet opens 1200 ms later, from the page; Drifting's way back is 3b.)
     */
    fun finish(lib: Library, g: Game, now: Long, zone: ZoneId, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val f = linkedMapOf<String, JsonElement>("state" to JsonPrimitive("played"))
        // Then & now's snapshot (v220), on the way to played
        if (lib.played.none { it.id == g.id }) Score.capOnFinish(lib, g, now, f)
        val out = linkedMapOf<String, JsonElement>(GAMES to fields(cur(GAMES), g.id, f))
        if (lib.played.none { it.id == g.id }) {
            out[RECS] = record(cur(RECS), g.id, JsonObject(mapOf("id" to JsonPrimitive(g.id), "real" to JsonPrimitive(0), "t" to JsonPrimitive(JsDate.todayLocal(now, zone)))))
        }
        return out
    }
}
