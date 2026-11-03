package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * Your own lists (`S.lists`, index.html 12860-12906, the v182 shelves you name yourself): making one (`listNew`),
 * renaming one, deleting one (`listDrop`). Pure: each gives the new `S.lists`, which the Lists sheet hands to
 * [Games.customise], so the change lives in memory only (a read-only practice copy until switch day).
 *
 * A deleted list's id stays on the games that had it (the web also takes it off them): in this copy nothing reads an
 * id without its list (the counts and tabs go through [Queue.lists]), and a new list never gets an old id.
 */
object Lists {
    /** `LIST_EMO`: the emoji a list can wear; a new one gets the next in turn. */
    val EMO = listOf("⭐", "🛋️", "🤝", "⚡", "🌙", "🔥", "🎯", "📚", "🧩", "👻", "🏆", "💤")

    /** A list's name is at most 32 characters (`maxlength="32"`, `.slice(0, 32)`). */
    const val MAX = 32

    /** `S.lists`: your lists, each `{id, n, e}` (name, emoji), in their order. */
    fun of(lib: Library): List<Queue.QList> = (lib["lists"] as? JsonArray).orEmpty().mapNotNull { e ->
        (e as? JsonObject)?.let { Queue.QList(Backup.str(it["id"]), Backup.str(it["n"]), Backup.str(it["e"])) }
    }

    /** `LIST_EMO[L.length % LIST_EMO.length]`: the emoji picked when the sheet opens. */
    fun nextEmo(lists: List<Queue.QList>) = EMO[lists.size % EMO.size]

    /** `listNew`: [name] trimmed and cut to 32 ("My list" when empty), [emo] ("⭐" when empty), at the end. */
    fun made(lists: List<Queue.QList>, name: String, emo: String, id: String): List<Queue.QList> =
        lists + Queue.QList(id, name.trim().take(MAX).ifEmpty { "My list" }, emo.ifEmpty { "⭐" })

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

    /** `S.lists` as the backup writes it: `[{id, n, e}]`. */
    fun json(lists: List<Queue.QList>) = JsonArray(lists.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "n" to JsonPrimitive(it.n), "e" to JsonPrimitive(it.e))) })
}
