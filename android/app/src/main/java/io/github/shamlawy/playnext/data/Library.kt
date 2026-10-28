package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.ZoneId

/**
 * Your games as the backup holds them: the web app's saved state `S`, kept whole as JSON (unknown keys survive), with
 * typed reads for what the screens show. Read-only until switch day.
 */
class Library(val state: JsonObject) {
    val games: List<Game> = (state["games"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::Game) }

    /** `S.played` (one record per finish; a replay adds another) and `S.duels`. */
    val played: List<Played> = (state["played"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::Played) }
    val duels: List<Duel> = (state["duels"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::Duel) }

    /** A saved setting as the web app reads it (`S[key]`): absent → null. */
    operator fun get(key: String): JsonElement? = state[key]

    /** `S[key] === false`: a switch you turned off (absent means it was never touched, so it's on). */
    fun off(key: String): Boolean = (state[key] as? JsonPrimitive)?.let { !it.isString && it.content == "false" } ?: false

    /** Settings that change how the whole app looks (Settings › Look of the app). */
    val theme: String get() = Backup.str(state["theme"])
    val bgBase: String get() = Backup.str(state["bgBase"])
    val bgTint: String get() = Backup.str(state["bgTint"])
    val bgGrad: Int get() = Backup.num(state["bgGrad"], Web.BG_GRAD.toDouble(), 0.0, 100.0).toInt()

    /** Words that must never reach a problem report: game titles and your list names. */
    val privateNames: List<String>
        get() = games.map { it.title } + (state["lists"] as? JsonArray).orEmpty().mapNotNull { Backup.str((it as? JsonObject)?.get("n")).ifEmpty { null } }

    /** How many games are in each state, in STATES' order (a missing state counts as upcoming, as `load()` reads it). */
    fun counts(): List<Pair<String, Int>> = Web.STATES.map { (k, label) -> label to games.count { it.state == k } }

    /**
     * Every game by state (the STATES words, empty ones left out), each in your order: the Queue's placeholder. Worked
     * out once per backup, not on every visit or every "checking" flip.
     */
    val byState: List<Group> by lazy {
        Web.STATES.mapNotNull { (key, label) ->
            games.filter { it.state == key }.sortedWith(compareBy<Game> { it.ord }.thenBy { it.title }).takeIf { it.isNotEmpty() }?.let { Group(key, label, it) }
        }
    }

    class Group(val state: String, val label: String, val games: List<Game>)

    companion object {
        val EMPTY = Library(JsonObject(mapOf("games" to JsonArray(emptyList()), "duels" to JsonArray(emptyList()), "played" to JsonArray(emptyList()))))
    }
}

class Game(val o: JsonObject) {
    val id: String get() = Backup.str(o["id"])
    val title: String get() = Backup.str(o["title"])

    /** The state as saved, "" when there's none (the web app's raw `g.state`). */
    val rawState: String get() = Backup.str(o["state"])

    /** Competitive games are never finished (`g.comp`). */
    val comp: Boolean get() = Backup.truthy(o["comp"])

    /** `g.state || "upcoming"` */
    val state: String get() = Backup.str(o["state"]).ifEmpty { "upcoming" }
    val date: String get() = Backup.str(o["date"])
    val ord: Double get() = (o["ord"] as? JsonPrimitive)?.doubleOrNull ?: Double.MAX_VALUE

    /** The game's own colour (`#RRGGBB`), else PALETTE's first, as the web app falls back. */
    val colour: Long get() = hex(Backup.str(o["colour"])) ?: Web.PALETTE[0]

    operator fun get(key: String): JsonElement? = o[key]

    /** `isUpcoming` (index.html 3729): the state as saved (a missing one isn't upcoming here); no date counts as coming. */
    fun isUpcoming(now: Long): Boolean {
        if (Backup.str(o["state"]) != "upcoming") return false
        if (date.isEmpty()) return true
        val at = JsDate.utcMidnight(date) ?: return false
        return at > now
    }

    /** `isFresh` (index.html 3727): out, not played, and out for less than 14 days. */
    fun isFresh(now: Long, zone: ZoneId): Boolean {
        if (Backup.str(o["state"]) != "backlog" || date.isEmpty() || date > JsDate.todayLocal(now, zone)) return false
        val at = JsDate.utcMidnight(date) ?: return false
        return now - at < 14 * JsDate.DAY
    }

    companion object {
        private val HEX = Regex("^#([0-9A-Fa-f]{6})$")
        fun hex(s: String): Long? = HEX.find(s)?.groupValues?.get(1)?.toLong(16)?.let { it or 0xFF000000L }
    }
}
