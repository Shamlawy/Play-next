package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Where was I (v212, `wi*`, index.html 19254-19366; android/parity/2-map/1-page.md §5.3): short notes about where you
 * stopped in a game, `g.wasI = [{t, x}]`, newest last, [Web.WI_MAX] kept. The newest shows on the game's page. Pure:
 * each op gives the practice keys' new values (a practice copy until switch day). Nexi's "Played X since last time?"
 * (`wiAsk`) and "Finished X?" after a note that sounds like the end (`fnAsk`) are his, Phase 9.
 */
object Notes {
    /** One note: when ([t], ms) and what ([x], 240 characters at most); [at] is its place in `g.wasI`. */
    data class Note(val t: Long, val x: String, val at: Int)

    const val LONGEST = 240

    /** The game's notes, oldest first (entries without a time or words are skipped, as the web never shows them). */
    fun of(g: Game): List<Note> = (g["wasI"] as? JsonArray).orEmpty().mapIndexedNotNull { i, e ->
        val o = e as? JsonObject ?: return@mapIndexedNotNull null
        val t = (o["t"] as? JsonPrimitive)?.longOrNull ?: (o["t"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: return@mapIndexedNotNull null
        val x = Backup.str(o["x"]).ifEmpty { return@mapIndexedNotNull null }
        Note(t, x, i)
    }

    /** `wiLast(g)`: the newest note. */
    fun last(g: Game): Note? = of(g).lastOrNull()

    /** `wiAgo(t)`: today, yesterday, N days ago (under 14), N weeks ago (under 60 days), else N months ago. */
    fun ago(t: Long, now: Long): String {
        val d = Math.floorDiv(now - t, JsDate.DAY)
        return when {
            d <= 0 -> "today"
            d == 1L -> "yesterday"
            d < 14 -> "$d days ago"
            d < 60 -> "${Rank.jsRound(d / 7.0).toLong()} weeks ago"
            else -> "${Rank.jsRound(d / 30.0).toLong()} months ago"
        }
    }

    /** The card's bold line: "Where you left off · <ago>", or after two days away "Welcome back · last time, <ago>". */
    fun head(n: Note, now: Long): String = if (back(n, now)) "Welcome back · last time, " + ago(n.t, now) else "Where you left off · " + ago(n.t, now)

    /** A note over two days old makes the card a welcome back (`.wi.back`). */
    fun back(n: Note, now: Long): Boolean = now - n.t > 2 * JsDate.DAY

    /** `wiAdd`'s clean-up: runs of spaces and new lines become one space, trimmed, 240 characters at most. */
    fun clean(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(LONGEST)

    /** A quick chip (`[data-wiq]`): fills an empty box, or goes after what's there as ", <chip in lower case>". */
    fun chip(box: String, chip: String): String {
        val b = box.trim()
        return if (b.isNotEmpty()) b.replace(Regex("[.,;]?$"), "") + ", " + chip.lowercase() else chip
    }

    /**
     * The notes as they stand right now: a practice write not yet in the library (it's rebuilt off the main thread, so a
     * quick second tap would still see the old [g]) wins over the game's own `wasI`, as the web's splice is at once.
     */
    private fun current(g: Game, cur: (String) -> JsonElement?): JsonArray? =
        ((cur(Practice.GAMES) as? JsonObject)?.get(g.id) as? JsonObject)?.get("wasI") as? JsonArray ?: g["wasI"] as? JsonArray

    /** `wiAdd`: the note at the end, the oldest dropped past [Web.WI_MAX]; nothing when the words are blank. */
    fun add(g: Game, text: String, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val x = clean(text)
        if (x.isEmpty()) return emptyMap()
        val all = current(g, cur).orEmpty() + JsonObject(mapOf("t" to JsonPrimitive(now), "x" to JsonPrimitive(x)))
        return mapOf(Practice.GAMES to Practice.fields(cur(Practice.GAMES), g.id, mapOf("wasI" to JsonArray(all.takeLast(Web.WI_MAX)))))
    }

    /** An earlier note's delete (`[data-widel]`): the entry at [at] in `g.wasI` goes. */
    fun drop(g: Game, at: Int, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val all = current(g, cur).orEmpty()
        if (at !in all.indices) return emptyMap()
        return mapOf(Practice.GAMES to Practice.fields(cur(Practice.GAMES), g.id, mapOf("wasI" to JsonArray(all.filterIndexed { i, _ -> i != at }))))
    }
}
