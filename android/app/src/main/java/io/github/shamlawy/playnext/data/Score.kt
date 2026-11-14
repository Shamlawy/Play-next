package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneId

/**
 * Score it (the Score sheet, `qScore`, index.html 13142; android/parity/2-map/1-page.md §5.1): one number, 0 to 10 in
 * halves, or "Don't remember". Scoring a game you haven't marked played marks it played. Pure: each op gives the
 * practice keys' new values, as [Practice] does (a practice copy until switch day).
 */
object Score {
    /** The top row: whole points `SCORE_LO`..10. */
    val WHOLE: List<Double> = (0..10).map { it.toDouble() }

    /** The row under it: the halves .5..9.5, each sitting between its two whole points. */
    val HALVES: List<Double> = (0 until 10).map { it + .5 }

    /** A button's words: "7", or a half as "7½" (the web's `String(v).replace(/^(\d+)\.5$/, "$1½")`). */
    fun label(v: Double): String = if (v % 1.0 == .5) "${v.toInt()}½" else Queue.jsNum(v)

    /** The score a record holds (`scored(rec) ? rec.real : null`), or null. */
    fun current(rk: Rank, g: Game): Double? = rk.playedRec(g)?.takeIf { it.scored }?.real

    /** `rankOf(id)` (13064): where the game sits in your ranked table (scored, played, not competitive), from 1. */
    fun rankOf(rk: Rank, g: Game): Int? = rk.table.pos[g.id]?.let { it + 1 }

    /** The small line under the game's name in the sheet (`.qs-g small`). */
    fun line(rk: Rank, g: Game): String {
        val rec = rk.playedRec(g)
        val cur = current(rk, g)
        val first = when {
            rec != null && Backup.truthy(rec.o["dk"]) -> "Unscored (you don't remember)"
            cur != null -> (rankOf(rk, g)?.let { "#$it · " } ?: "") + "tap to change"
            rec != null -> "Played, not scored yet"
            else -> "Scoring it marks it played"
        }
        val hype = Backup.num(g["hype"], 0.0, -1e9, 1e9)
        return first + if (hype > 0) " · hype was " + Queue.jsNum(hype) else ""
    }

    /**
     * Then & now's snapshot (index.html 20887), taken the moment a game first becomes played and never written again:
     * its hype, Nexi's guess ([pr]: `expected(g)` until the helper's reviews come in piece 4), the critics, when you
     * started, when you added it, the hours you'd logged and your first Where was I note.
     */
    fun cap(g: Game, pr: Double, now: Long): JsonObject {
        val n0 = (g["wasI"] as? JsonArray)?.firstOrNull() as? JsonObject
        val added = g["added"]?.takeIf { Backup.truthy(it) }
        return JsonObject(
            linkedMapOf(
                "h" to Backup.jsNumber(Backup.num(g["hype"], 0.0, 0.0, 10.0)),
                "pr" to Backup.jsNumber(if (pr.isNaN()) 0.0 else pr),
                "cr" to Backup.jsNumber(Backup.num(g["critic"], 0.0, -1e9, 1e9)),
                "st" to Backup.jsNumber(Backup.num(g["plAt"], 0.0, 0.0, Double.MAX_VALUE)),
                "ad" to (added ?: JsonPrimitive("")),
                "hrs" to Backup.jsNumber(Backup.num(g["logged"], 0.0, 0.0, 100000.0)),
                "n1" to (n0?.let { JsonObject(mapOf("t" to (it["t"] ?: JsonNull), "x" to (it["x"] ?: JsonNull))) } ?: JsonNull),
                "at" to JsonPrimitive(now),
            ),
        )
    }

    /** Nexi's guess for [g] as the snapshot keeps it: `rvFor(g).v` (the critics, from the backup), else `expected(g) || 0`. */
    fun guess(lib: Library, g: Game, now: Long): Double {
        val utc = ZoneId.of("UTC")
        val q = Queue(lib, Rank(lib, now, utc), now, utc)
        return q.rvGuess(g) ?: q.expected(g) ?: 0.0
    }

    /**
     * [f] with the snapshot added when the game had none (`!g.cap`), as `syncPlayed`'s v220 hook does on the way to played;
     * [g] is the game with [f] already laid over it (the web's Save copies the draft onto the game first). The crystal
     * ball's hook (21161) adds the first finish forecast, `g.eta0.d`, as `e0`.
     */
    fun capOnFinish(lib: Library, g: Game, now: Long, f: MutableMap<String, JsonElement>) {
        if (Backup.truthy(g["cap"])) return
        val at = Game(JsonObject(g.o + f))
        var c = cap(at, guess(lib, at, now), now)
        val e0 = Forecast.first(at)
        if (e0 != null) c = JsonObject(c + ("e0" to e0))
        f["cap"] = c
    }

    /**
     * The crystal ball (`scoreWrite`'s hook, 21162): the first real score after a snapshot with Nexi's guess is kept as
     * `cap.got`. [cap] is the snapshot as it stands (one just taken, or the game's); null when nothing changes.
     */
    fun got(cap: JsonElement?, v: Double?): JsonObject? {
        val c = cap as? JsonObject ?: return null
        if (v == null || Backup.num(c["pr"], 0.0, -1e9, 1e9) <= 0 || (c["got"] != null && c["got"] !is JsonNull)) return null
        return JsonObject(c + ("got" to Backup.jsNumber(v)))
    }

    /**
     * A tap in the sheet (`qScoreSet`, 13176): [v] a score 0..10, or null for "Don't remember". A game without a record
     * becomes played first (its record `{id, real: 0, t}` with today's UTC date, and the snapshot), then `scoreWrite`: a
     * real 0 is kept as a score with `z: 1`, Don't remember is `real: 0, dk: 1`. The first real score after a snapshot
     * with Nexi's guess is kept as `cap.got` (21162).
     */
    fun set(lib: Library, g: Game, v: Double?, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val rec0 = lib.played.firstOrNull { it.id == g.id }
        val f = LinkedHashMap<String, JsonElement>()
        val r: LinkedHashMap<String, JsonElement>
        if (rec0 == null) {
            f["state"] = JsonPrimitive("played")
            capOnFinish(lib, g, now, f)
            r = linkedMapOf("id" to JsonPrimitive(g.id), "real" to JsonPrimitive(0), "t" to JsonPrimitive(JsDate.todayUtc(now)))
        } else {
            r = LinkedHashMap(rec0.o)
        }
        if (v == null) {
            r["real"] = JsonPrimitive(0); r["dk"] = JsonPrimitive(1); r.remove("z")
        } else {
            r["real"] = Backup.jsNumber(v); r.remove("dk")
            if (v == 0.0) r["z"] = JsonPrimitive(1) else r.remove("z")
        }
        if (!Backup.truthy(r["t"])) r["t"] = JsonPrimitive(JsDate.todayUtc(now))
        got(f["cap"] ?: g["cap"], v)?.let { f["cap"] = it }
        val out = LinkedHashMap<String, JsonElement>()
        if (f.isNotEmpty()) out[Practice.GAMES] = Practice.fields(cur(Practice.GAMES), g.id, f)
        out[Practice.RECS] = Practice.record(cur(Practice.RECS), g.id, JsonObject(r))
        return out
    }
}
