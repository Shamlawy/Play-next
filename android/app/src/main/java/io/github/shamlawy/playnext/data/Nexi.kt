package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import java.time.ZoneId

/**
 * What Nexi shows and says from your games (android/parity/1-map/2-card.md §6), worked out off Main with the header
 * cards ([Shx.all]), so drawing him only reads: the next release (`pnNext`, index.html 12099), his badge (`pnBubBadge`'s
 * countdown and queue hours, 12103), the lines he says when he leaves his seat (`pnBubShow`, 12475: the daily brief and a
 * release day's), whether today is a release-day party and the stage his ring has grown to (`pnEvo`, 14017). Pure: the
 * clock and the zone are passed in.
 */
class Nexi(private val lib: Library, private val rk: Rank, private val now: Long, private val zone: ZoneId) {
    private val today = JsDate.todayLocal(now, zone)

    /** `pnNext()`: the soonest game still to come (its date's local midnight after now, not played) and its days away. */
    val next: Pair<Game, Int>? = lib.games
        .filter { g -> !rk.isPlayed(g) && JsDate.localMidnight(g.date, zone)?.let { it > now } == true }
        .minWithOrNull { a, b -> a.date.compareTo(b.date) }
        ?.let { g -> g to Math.ceil((JsDate.localMidnight(g.date, zone)!! - now) / JsDate.DAY.toDouble()).toInt() }

    /** The games out today that you haven't played (a release-day party). */
    private val outToday = lib.games.filter { it.date == today && !rk.isPlayed(it) }

    /** It's a release day: his ring wears the party colours. */
    val party: Boolean = outToday.isNotEmpty()

    /**
     * His badge (`S.nxBadge`): the countdown by default ("TODAY" on a release day, else the next one's days), the queue's
     * hours, or nothing. Streak, level and to-score need his own records and come with his phase (8a): no badge till then.
     */
    fun badge(mode: String): String = when (mode) {
        "", "count" -> if (party) "TODAY" else next?.let { "${it.second}d" } ?: ""
        "hours" -> lib.games.filter { !rk.isPlayed(it) }.sumOf { rk.hrsOf(it).coerceIn(0.0, 1000.0) }.let { if (it > 0) "${Math.round(it)}h" else "" }
        else -> ""
    }

    /**
     * The daily brief, once a day as he leaves his seat (`pnBubShow`): this week's releases (a date from yesterday on to a
     * week ahead, not played) and the next one, or the backlog. The web's emoji stay out (the owner, Nov 3: no emoji in
     * the app).
     */
    fun brief(name: String): String {
        val wk = lib.games.count { g ->
            !rk.isPlayed(g) && JsDate.localMidnight(g.date, zone)?.let { (it - now) / JsDate.DAY.toDouble() }?.let { it > -1 && it <= 7 } == true
        }
        val n = next
        return when {
            wk > 0 -> "Today's brief" + (if (name.isNotEmpty()) ", $name" else "") + ": $wk release" + (if (wk == 1) "" else "s") + " this week" +
                (n?.let { ", next is ${it.first.title} in ${it.second}d" } ?: "")
            n != null -> "Nothing out this week. Next up: ${n.first.title} in ${n.second} days. Backlog time"
            else -> "Nothing on the calendar. Backlog time"
        }
    }

    /** A release day's line, once that day (`S.nxPartyDay`): the first game out today, and how many more. */
    val partyLine: String? = outToday.firstOrNull()?.let { g ->
        g.title + " is OUT today" + (if (outToday.size > 1) " (plus ${outToday.size - 1} more)" else "") + "! Party time!"
    }

    /** `pnEvo()`: 1-5, one more for each of [Web.PN_EVO]'s counts of finishes you've scored. */
    val stage: Int = lib.played.count { it.scored }.let { n -> Web.PN_EVO.count { n >= it }.coerceIn(1, 5) }
}
