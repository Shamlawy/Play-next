package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * Edit › Scores › Critics › "Pull score, date, tags and blurb" (`rawgLookup` 7540, `applyLookup` 5773, `mergeTags` 7885;
 * android/parity/2-map/2-edit.md §6): what RAWG has for the game, whether it's really this game, and what that puts in
 * the draft. Nothing reaches the game until Save.
 *
 * The web asks Steam too (`steamLook`), but only when a "boost" address is set, and none is by default, so it never
 * calls; and since v-hours (23440) RAWG players' hours are dropped. So Pull is RAWG only and never fills the hours.
 */
object Lookup {
    sealed interface Found {
        data class Err(val text: String) : Found

        /** RAWG's game: its id, name, metacritic, backdrop, tags, description, release, users' rating; [sure] it's this game, else [why]. */
        data class Game(
            val id: JsonElement, val name: String, val critic: Int, val bg: String, val tags: JsonArray, val about: String,
            val released: String, val users: Double, val count: Int, val sure: Boolean, val why: String,
        ) : Found
    }

    /** `nkey`: words in lower case, anything else a space. */
    fun nkey(x: String): String = x.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** `simScore`: how alike two titles are, by shared letter pairs (0 to 1). */
    fun simScore(a0: String, b0: String): Double {
        val a = nkey(a0)
        val b = nkey(b0)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        fun bg(x: String) = (0 until x.length - 1).map { x.substring(it, it + 2) }
        val aa = bg(a)
        val bb = bg(b)
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        val seen = HashMap<String, Int>()
        aa.forEach { seen[it] = (seen[it] ?: 0) + 1 }
        var hit = 0
        bb.forEach { val n = seen[it] ?: 0; if (n > 0) { seen[it] = n - 1; hit++ } }
        return 2.0 * hit / (aa.size + bb.size)
    }

    /** `titleMatch`: the same words, or nearly (0.9). */
    fun titleMatch(mine: String, theirs: String): Boolean = nkey(mine) == nkey(theirs) || simScore(mine, theirs) >= 0.9

    /** RAWG's answer read as the web does; [today] decides whether a release date is already past. */
    fun read(d: JsonObject, g: io.github.shamlawy.playnext.data.Game, today: LocalDate = LocalDate.now()): Found {
        val name = Backup.str(d["name"])
        val confirmed = Backup.truthy(g["rawgOk"])
        val named = titleMatch(g.title, name)
        val released = Backup.str(d["released"])
        // a game you have as upcoming should not suddenly have shipped years ago
        val old = released.isNotEmpty() && (runCatching { LocalDate.parse(released) < today }.getOrDefault(false)) && Backup.str(g["state"]).ifEmpty { "upcoming" } == "upcoming"
        val who = name.ifEmpty { "?" }
        return Found.Game(
            id = d["id"] ?: JsonPrimitive(0),
            name = name,
            critic = Backup.num(d["metacritic"], 0.0, 0.0, 100.0).toInt(),
            bg = Backup.str(d["background_image"]),
            tags = d["tags"] as? JsonArray ?: JsonArray(emptyList()),
            about = Backup.str(d["description_raw"]).trim(),
            released = released,
            users = Backup.num(d["rating"], 0.0, 0.0, 5.0),
            count = Backup.num(d["ratings_count"], 0.0, 0.0, 1e9).toInt(),
            sure = confirmed || (named && !old),
            why = if (confirmed) "" else if (!named) "it came back with “$who”" else if (old) "“$who” is already out — that looks like an older game in the series" else "",
        )
    }

    /** The line when RAWG found another game: nothing changed, and how to use it anyway. */
    fun notSure(f: Found.Game): String = "Nothing changed — ${f.why}, which isn't this game. If it really is, use it anyway and it'll remember the match."

    private fun norm(x: String) = x.lowercase().replace(Regex("[^a-z0-9]"), "")
    private val GENRE_BY_NORM: Map<String, String> by lazy { Web.GENRE_GROUPS.flatMap { it.second }.associateBy { norm(it) } }

    /** `mapTags`: RAWG's English tags as our names (an alias, a genre of ours, or left out). */
    fun mapTags(tags: JsonArray): List<String> = tags.mapNotNull { t ->
        val o = t as? JsonObject
        val lang = o?.get("language")?.let(Backup::str).orEmpty()
        if (lang.isNotEmpty() && lang != "eng") return@mapNotNull null
        val n = norm(if (o != null) Backup.str(o["name"]) else Backup.str(t))
        (Web.TAG_ALIAS[n] ?: GENRE_BY_NORM[n] ?: "").ifEmpty { null }
    }

    private fun list(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** `mergeTags`: yours first, then RAWG's, no repeats by letters, at most 8. */
    fun mergeTags(genres: String, tags: JsonArray): String {
        val out = ArrayList<String>()
        for (n in list(genres) + mapTags(tags)) if (n.isNotEmpty() && out.none { norm(it) == norm(n) }) out += n
        return out.take(8).joinToString(", ")
    }

    /**
     * `applyLookup`: the draft with RAWG's game in it and the line to say. Its RAWG id; its backdrop and description only
     * when the game has none; new tags merged in; the critic score; the release date when it differs.
     */
    fun apply(g: io.github.shamlawy.playnext.data.Game, d0: Edit.Draft, f: Found.Game): Pair<Edit.Draft, String> {
        val m = LinkedHashMap<String, JsonElement?>()
        m["rawgId"] = f.id
        if (f.bg.isNotEmpty() && !Backup.truthy(g["bg"])) m["bg"] = JsonPrimitive(f.bg)
        if (f.about.isNotEmpty() && !Backup.truthy(g["about"])) m["in:about"] = JsonPrimitive(f.about.take(600))
        val cur = d0.str("genres")
        val merged = mergeTags(cur, f.tags)
        val added = list(merged).size - list(cur).size
        val got = ArrayList<String>()
        if (added > 0) {
            m["genres"] = JsonPrimitive(merged)
            got += "$added tag" + (if (added == 1) "" else "s")
        }
        if (f.critic > 0) {
            m["in:critic"] = JsonPrimitive(f.critic.toString())
            got += "critic ${f.critic}"
        }
        var d = d0.setAll(m)
        if (f.released.isNotEmpty() && f.released != d.str("date")) {
            d = d.set("date", JsonPrimitive(f.released))
            got += "date ${f.released}"
        }
        val users = if (f.users > 0) " RAWG users rate it ${String.format(java.util.Locale.ROOT, "%.1f", f.users)}/5 from ${f.count} votes." else ""
        return d to (if (got.isNotEmpty()) "Got " + got.joinToString(" and ") + " — tap Save." + users else "No critic score yet and the date is unchanged.$users")
    }

    /** "Use it anyway": remember the match, then apply it. */
    fun force(g: io.github.shamlawy.playnext.data.Game, d: Edit.Draft, f: Found.Game): Pair<Edit.Draft, String> = apply(g, d.set("rawgOk", JsonPrimitive(true)), f)
}
