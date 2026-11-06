package io.github.shamlawy.playnext.data

import androidx.compose.runtime.Immutable
import kotlin.random.Random

/**
 * The Game Shuffler's numbers (`openShuffle`, index.html 15915; Sort › Game shuffler, a hold on "Shuffle the queue"):
 * which games are in the deck, the words under a card, and the deck's own memory (what you swiped, so Back one undoes
 * it). The deck itself is drawn in ui/Shuffle.kt.
 */
object Shuffler {
    /** Everything waiting (`S.shufMode` "all") or only the games that are out ("out"). */
    const val ALL = "all"
    const val OUT = "out"

    /** Where the deck's shuffles come from (the screenshot tests set a seed, so their pictures stay the same). */
    @Volatile var rnd: Random = Random.Default

    /**
     * `shDeck(mode)`: the games not played or dropped, only those out now for [OUT], shuffled. "Out now" is the Queue's
     * own ([Queue.isOutNow], as Shuffle's tap reads it), so the deck and the Out tab always agree.
     */
    fun deck(q: Queue, mode: String, rnd: Random = this.rnd): List<String> =
        q.lib.games.filter { q.waiting(it) && (mode != OUT || q.isOutNow(it)) }.map { it.id }.shuffled(rnd)

    /** `shShort(t)`: a title up to its subtitle ("Name: The subtitle" → "Name"). */
    fun short(t: String): String = t.split(Regex("[:–—-]\\s"))[0].trim().ifEmpty { t }

    /**
     * `shWhy(g)`: your own notes on the game, else what the deck knows: out or when it lands, how many evenings at your
     * pace (`S.evening`, 4 when not set), how much you hyped it.
     */
    fun why(q: Queue, g: Game): String {
        val own = Backup.str(g["desc"]).ifEmpty { Backup.str(g["about"]) }
        if (own.isNotBlank()) return own.trim()
        val bits = ArrayList<String>()
        if (g.date.isNotEmpty()) {
            val n = q.countdown(g)
            bits += if (n == null) "It's out and waiting for you."
            else "It lands " + q.shortDate(g.date) + ", " + (if (n == 1) "tomorrow." else "in $n days.")
        }
        val hrs = q.rk.hrsOf(g)
        if (hrs > 0) {
            val pace = Backup.num(q.lib["evening"], 4.0, 1.0, 12.0)
            val ev = maxOf(1, Rank.jsRound(hrs / pace).toInt())
            bits += "About $ev evening" + (if (ev == 1) "" else "s") + " at your usual pace."
        }
        val hype = Backup.num(g["hype"], 0.0, 0.0, 10.0)
        if (hype > 0) bits += "You hyped it " + Backup.jsNumber(hype).content + "/10."
        return bits.joinToString(" ").ifEmpty { "No notes on this one yet. Add some in Edit and they'll show here." }
    }

    /**
     * Where the deck is and what you swiped: the shuffled [ids], the card on show [at], the games you're tempted by
     * ([picks], in the order you picked them) and [steps] for Back one. Plain values, so the screen keeps it while a game
     * page is open over the deck.
     */
    @Immutable
    data class Deck(
        val mode: String,
        val ids: List<String>,
        val at: Int = 0,
        val picks: List<String> = emptyList(),
        val steps: List<Step> = emptyList(),
        /** The picks are showing (Your picks, or the deck ran out). */
        val done: Boolean = false,
    ) {
        val id get() = ids.getOrNull(at)

        /** Right ([yes]: tempted) or left (not now) on the card on show; the next one comes up. */
        fun swipe(yes: Boolean): Deck {
            val g = id ?: return this
            val had = g in picks
            val p = if (yes) (if (had) picks else picks + g) else picks - g
            val next = at + 1
            return copy(at = next, picks = p, steps = steps + Step(at, g, yes, had), done = next >= ids.size)
        }

        /** Back one: the last swipe undone (its card back on show, its pick as it was). */
        fun undo(): Deck {
            val s = steps.lastOrNull() ?: return this
            val p = when {
                s.yes && !s.had -> picks - s.id
                !s.yes && s.had -> picks + s.id
                else -> picks
            }
            return copy(at = s.at, picks = p, steps = steps.dropLast(1), done = false)
        }

        /** A fresh shuffle of [mode] (the picks start over too, as on the web). */
        fun again(mode: String, ids: List<String>) = Deck(mode, ids)

        /**
         * Past the cards whose game is gone from [have] (a newer backup took it away while the deck was kept): the next
         * card that's still there, or the picks when none is left.
         */
        fun past(have: Set<String>): Deck {
            if (done) return this
            val i = (at until ids.size).firstOrNull { ids[it] in have } ?: return copy(at = ids.size, done = true)
            return if (i == at) this else copy(at = i)
        }

        /** To card [i] (a tap on the strip of covers). */
        fun go(i: Int) = if (i in ids.indices) copy(at = i, done = false) else this

        fun save(): List<String> = listOf(mode, ids.joinToString(","), at.toString(), picks.joinToString(","), steps.joinToString(";") { it.save() }, done.toString())

        companion object {
            fun restore(v: List<String>): Deck = Deck(
                v[0],
                v[1].split(',').filter { it.isNotEmpty() },
                v[2].toIntOrNull() ?: 0,
                v[3].split(',').filter { it.isNotEmpty() },
                v[4].split(';').mapNotNull { Step.restore(it) },
                v.getOrNull(5) == "true",
            )
        }
    }

    /** One swipe: the card [at], its game [id], which way, and whether it was a pick already. */
    @Immutable
    data class Step(val at: Int, val id: String, val yes: Boolean, val had: Boolean) {
        fun save() = "$at|$id|$yes|$had"

        companion object {
            fun restore(s: String): Step? {
                val p = s.split('|')
                if (p.size != 4) return null
                return Step(p[0].toIntOrNull() ?: return null, p[1], p[2] == "true", p[3] == "true")
            }
        }
    }
}
