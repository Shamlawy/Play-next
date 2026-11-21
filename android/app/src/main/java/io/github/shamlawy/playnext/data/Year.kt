package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId

/** One play session the timer kept (`S.sessions`: `{g, s, e}`, the game's id and its start and end in ms). */
class Session(val g: String, val s: Long, val e: Long) {
    /** `sessH`: its hours, never under 0. */
    val h: Double get() = maxOf(0.0, (e - s) / 3.6e6)
}

/**
 * Your year and month in numbers (index.html `wrData` 12652, `sessStats` 12603, `fmtH` 12572): what Home's year and
 * month cards say now, and what the wrapped slides and the timer (phase 5) read later. Pure: the clock and the zone are
 * passed in.
 */
object Year {
    /** `WR_MONTHS`. */
    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** The sessions in your copy (`S.sessions`), skipping any the phone can't read. */
    fun sessions(lib: Library): List<Session> = (lib["sessions"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val s = (o["s"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
        val en = (o["e"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
        Session(Backup.str(o["g"]), s, en)
    }

    /** `fmtH`: "45m", "2h", "2h 05m". */
    fun fmtH(h: Double): String {
        val m = Math.floor(h * 60 + .5).toLong()
        return if (m < 60) "${m}m" else "${m / 60}h" + if (m % 60 != 0L) " " + (m % 60).toString().padStart(2, '0') + "m" else ""
    }

    /** `sessStats`: how many, the hours in all, the longest and the average. */
    class Stats(val n: Int, val tot: Double, val best: Double, val avg: Double)

    fun stats(list: List<Session>): Stats {
        val tot = list.sumOf { it.h }
        return Stats(list.size, tot, list.maxOfOrNull { it.h }?.coerceAtLeast(0.0) ?: 0.0, if (list.isEmpty()) 0.0 else tot / list.size)
    }

    /** `sessDay`: the local date of a moment, "2026-11-21". */
    fun day(t: Long, zone: ZoneId): String = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toString()

    /**
     * What a record's date field reads as for `inY` (`typeof t === "number" ? sessDay(t) : t`): a number is a moment,
     * read as its local date; text is kept as it is.
     */
    fun dateOf(e: JsonElement?, zone: ZoneId): String {
        val p = e as? JsonPrimitive ?: return ""
        if (!p.isString) p.longOrNull?.let { return day(it, zone) }
        return Backup.str(p)
    }

    /** The numbers Home's year and month cards show (a part of `wrData`; the slides' other numbers come in phase 5). */
    class Wr(
        val y: Int,
        /** 0-11 for a month, null for the whole year. */
        val m: Int?,
        val ses: List<Session>,
        val hours: Double,
        /** Under three sessions: too few to count hours or name the most played. */
        val thin: Boolean,
        /** The game you played most (by session hours) and its hours. */
        val top: Pair<Game, Double>?,
        /** Every game you finished in it, in the order you finished them (a replay counts again). */
        val fin: List<Game>,
        /** The best-scored of those. */
        val best: Game?,
        /** Duels you settled (a winner, not a draw). */
        val duels: Int,
        /** Games you added. */
        val added: Int,
    )

    /** `wrData(Y, M)`: [m] 0-11 narrows the year [y] to one month. */
    fun wr(lib: Library, y: Int, m: Int?, zone: ZoneId): Wr {
        val pref = if (m == null) y.toString() else "$y-" + (m + 1).toString().padStart(2, '0')
        val byId = HashMap<String, Game>().also { h -> for (g in lib.games) h.putIfAbsent(g.id, g) }
        val ses = sessions(lib).filter { day(it.s, zone).startsWith(pref) && it.g in byId }
        val hours = ses.sumOf { it.h }
        val byG = LinkedHashMap<String, Double>()
        for (s in ses) byG[s.g] = (byG[s.g] ?: 0.0) + s.h
        val topId = byG.entries.sortedByDescending { it.value }.firstOrNull()?.key
        val fin = lib.played.filter { p -> dateOf(p.o["t"], zone).startsWith(pref) && p.id != null && p.id in byId }
        val best = fin.filter { it.scored }.sortedByDescending { it.real }.firstOrNull()
        val duels = lib.duels.count { d -> dateOf(d.o["t"], zone).startsWith(pref) && d.w.isNotEmpty() && d.w != "draw" }
        val added = lib.games.count { dateOf(it["added"], zone).let { a -> a.isNotEmpty() && a.startsWith(pref) } }
        return Wr(
            y, m, ses, hours, ses.size < 3,
            topId?.let { byId.getValue(it) to byG.getValue(it) },
            fin.map { byId.getValue(it.id!!) },
            best?.let { byId[it.id] },
            duels, added,
        )
    }
}
