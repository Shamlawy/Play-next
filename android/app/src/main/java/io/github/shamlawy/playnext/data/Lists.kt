package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.gen.WebListMark
import kotlin.random.Random

/**
 * Your own lists (`S.lists`, index.html 12860-12906, the v182 shelves you name yourself): making one (`listNew`),
 * renaming one, deleting one (`listDrop`). Pure: each gives the new `S.lists`, which the Lists sheet hands to
 * [Games.customise], so the change lives in memory only (a read-only practice copy until switch day).
 *
 * Which lists a game is in (`g.lists`, `openLists(gid)`'s ticks) changes through [IN]: a practice key holding
 * `{gameId: [listIds]}` for the games you changed, which [laid] puts over the backup's games whenever Games builds a
 * copy. A deleted list comes off every game too ([without], as `listDrop` does).
 */
object Lists {
    /**
     * `LIST_EMO`: the marks a list can wear, a new one gets the next in turn. The web stores an emoji (`e`); the app
     * never shows emoji (T56) and draws each as its icon (android-gen's LIST_MARKS), so this holds the web's values only.
     */
    val MARKS: List<WebListMark> = Web.LIST_MARKS

    /** A list's name is at most 32 characters (`maxlength="32"`, `.slice(0, 32)`). */
    const val MAX = 32

    /** `S.lists`: your lists, each `{id, n, e}` (name, the web's emoji), in their order. */
    fun of(lib: Library): List<Queue.QList> = (lib["lists"] as? JsonArray).orEmpty().mapNotNull { e ->
        (e as? JsonObject)?.let { Queue.QList(Backup.str(it["id"]), Backup.str(it["n"]), Backup.str(it["e"])) }
    }

    /** `LIST_EMO[L.length % LIST_EMO.length]`: the mark picked when the sheet opens (the web's emoji). */
    fun nextEmo(lists: List<Queue.QList>) = MARKS[lists.size % MARKS.size].emo

    /** The drawn icon for a list's stored emoji (with or without its FE0F); one the app doesn't know gets the list icon. */
    fun icon(e: String): String = e.replace("\uFE0F", "").let { k -> MARKS.firstOrNull { it.emo.replace("\uFE0F", "") == k }?.icon } ?: "ui:list"

    /** What TalkBack calls a list's mark ("Star"), or null for one the app doesn't know. */
    fun markName(e: String): String? = e.replace("\uFE0F", "").let { k -> MARKS.firstOrNull { it.emo.replace("\uFE0F", "") == k }?.label }

    /** `listNew`: [name] trimmed and cut to 32 ("My list" when empty), [emo] (the first mark when empty), at the end. */
    fun made(lists: List<Queue.QList>, name: String, emo: String, id: String): List<Queue.QList> =
        lists + Queue.QList(id, name.trim().take(MAX).ifEmpty { "My list" }, emo.ifEmpty { MARKS[0].emo })

    /** The Save of a rename: a blank name changes nothing (`if (l && v)`). */
    fun renamed(lists: List<Queue.QList>, id: String, name: String): List<Queue.QList> {
        val v = name.trim()
        if (v.isEmpty()) return lists
        return lists.map { if (it.id == id) it.copy(n = v.take(MAX)) else it }
    }

    /** `listDrop`: the list goes; the games stay. */
    fun dropped(lists: List<Queue.QList>, id: String): List<Queue.QList> = lists.filter { it.id != id }

    /** `"l" + Date.now().toString(36) + Math.random().toString(36).slice(2, 5)`. */
    fun newId(now: Long, rnd: Random = Random.Default): String =
        "l" + now.toString(36) + (1..3).map { "0123456789abcdefghijklmnopqrstuvwxyz"[rnd.nextInt(36)] }.joinToString("")

    /** How many of your games are in the list (`count(l)`). */
    fun count(lib: Library, id: String): Int = lib.games.count { g -> (g["lists"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.content == id } }

    /** The words under a list's name: "3 games · show in queue". */
    fun countLine(n: Int) = "$n game${if (n == 1) "" else "s"} · show in queue"

    /** The practice key for the games whose lists you changed: `{gameId: [listIds]}`, laid over the backup by [laid]. */
    const val IN = "pn:in"

    private fun ids(e: JsonElement?): List<String> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
    private fun arr(l: List<String>) = JsonArray(l.map(::JsonPrimitive))

    /** The lists [g] is in: your change ([over], the current [IN]) when there is one, else the backup's `g.lists`. */
    fun inLists(over: JsonObject?, g: Game): List<String> = over?.get(g.id)?.let(::ids) ?: ids(g["lists"])

    /** A tap in the picker (`data-lt`): [g] goes into the list, or out when it was in. The new [IN]. */
    fun flipped(over: JsonObject?, g: Game, listId: String): JsonObject {
        val cur = inLists(over, g)
        return JsonObject(over.orEmpty() + (g.id to arr(if (listId in cur) cur - listId else cur + listId)))
    }

    /** `listDrop`'s second half: the list comes off every game that had it. The new [IN]. */
    fun without(over: JsonObject?, games: List<Game>, listId: String): JsonObject =
        JsonObject(over.orEmpty() + games.filter { listId in inLists(over, it) }.associate { it.id to arr(inLists(over, it) - listId) })

    /** The web's `S` with [IN] laid over its games (each changed game's `lists`) and the key itself gone. */
    fun laid(s: JsonObject): JsonObject {
        val over = s[IN] as? JsonObject ?: return s
        val games = (s["games"] as? JsonArray)?.let { a ->
            JsonArray(a.map { e -> (e as? JsonObject)?.let { o -> over[Backup.str(o["id"])]?.let { JsonObject(o + ("lists" to it)) } } ?: e })
        }
        return JsonObject(s.filterKeys { it != IN } + (games?.let { mapOf("games" to it) } ?: emptyMap()))
    }

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

    /** `S.lists` as the backup writes it: `[{id, n, e}]`. */
    fun json(lists: List<Queue.QList>) = JsonArray(lists.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "n" to JsonPrimitive(it.n), "e" to JsonPrimitive(it.e))) })
}
