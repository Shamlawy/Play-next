package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The header cards' numbers on a big made-up library (seeded, so it's the same every run): the same as the code gave
 * before they moved off Main and shared one Rank. GoldenTest checks them against the web app on its own small set.
 */
class HeaderNumbersTest {
    private val zone = ZoneId.of("Asia/Dubai")

    /** Noon, 11:30 PM and 12:30 AM in Dubai (the golden clocks), on a made-up day. */
    private val clocks = listOf("2026-10-27T08:00:00Z", "2026-10-27T19:30:00Z", "2026-10-27T20:30:00Z").map { Instant.parse(it).toEpochMilli() }

    /**
     * What the code gave before (each screen its own Rank, the table in composition), per library and clock: a digest of
     * every card's covers, numbers, line, genres, mix bar, chart and backdrop. The midnight column differs on purpose.
     */
    private val before = mapOf(
        60 to listOf("c90b2c1d550e9cd9", "c90b2c1d550e9cd9", "ff0e93bb6fba50d5"),
        200 to listOf("65c09fff19c06ab6", "65c09fff19c06ab6", "25b3c4dc6d80864f"),
        400 to listOf("91c0643ae8787f5a", "91c0643ae8787f5a", "846af9312e9359c2"),
    )

    @Test fun sameNumbersAsBefore() {
        for ((n, d) in listOf(60 to 40, 200 to 300, 400 to 1000)) {
            val state = madeUp(n, d, seed = n)
            clocks.forEachIndexed { c, now ->
                val lib = Library(Backup.load(Backup.apply(state), now, zone))
                val t0 = System.nanoTime()
                val cards = Shx.all(lib, now, zone)
                val ms = (System.nanoTime() - t0) / 1e6
                val all = Shx.KINDS.joinToString("\n") { k -> k + ": " + render(cards[k]) }
                val one = Shx.KINDS.joinToString("\n") { k -> k + ": " + render(runCatching { Shx.of(k, lib, now, zone) }.getOrNull()) }
                println("$n games, $d duels, clock $c: all seven in ${"%.1f".format(ms)} ms")
                assertEquals("$n games at clock $c, all at once:\n$all", before[n]!![c], sha(all))
                assertEquals("$n games at clock $c, one by one", all, one)
            }
        }
    }

    companion object {
        /** One card as a line: covers, numbers, the spotlight, genres, the mix bar, the chart and the backdrop. */
        fun render(s: Shx?): String = if (s == null) "none" else listOf(
            s.covers.joinToString(",") { it.id },
            s.stats.joinToString(",") { "${it.value}${it.unit ?: ""} ${it.label}" },
            s.line?.let { "${it.g.id}/${it.k}/${it.x}" } ?: "-",
            s.tags.joinToString(",") { "${it.first}=${it.second}" },
            s.bar?.joinToString(",") { "${it.n} ${it.label} ${it.c}" } ?: "-",
            s.chart?.let { it.v.joinToString(",") + " " + it.cap } ?: "-",
            when (val a = s.art) { null -> "-"; is Pic.Drawn -> "drawn:" + a.g.id; is Pic.Url -> a.url },
        ).joinToString(" | ")

        fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

        private val WORDS = listOf("Ember", "Hollow", "Copper", "Lumen", "Quiet", "Paper", "Ninefold", "Star", "Tide", "Meadow", "Orbit", "Gate", "Lantern", "Drift")
        private val GENRES = listOf("Action", "RPG", "Strategy", "Puzzle", "Adventure", "Racing", "Shooter")
        private val STATES = listOf("backlog", "backlog", "playing", "upcoming", "upcoming", "played", "played", "dropped", "")

        /** [n] made-up games (about a third played and scored) and [duels] duels between the played ones. */
        fun madeUp(n: Int, duels: Int, seed: Int): JsonObject {
            val r = Random(seed)
            val day0 = LocalDate.parse("2026-10-27")
            fun day(spread: Int) = day0.plusDays((r.nextInt(spread * 2) - spread).toLong()).toString()
            fun p(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is List<*> -> JsonArray(v.map { p(it) })
                else -> kotlinx.serialization.json.JsonNull
            }
            val games = (0 until n).map { i ->
                val m = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
                m["id"] = p("g$i")
                m["title"] = p(WORDS[r.nextInt(WORDS.size)] + " " + WORDS[r.nextInt(WORDS.size)] + " " + i)
                m["state"] = p(STATES[r.nextInt(STATES.size)])
                // around today, so release days, the 30-day flip and "this month" all happen
                m["date"] = p(if (r.nextInt(8) == 0) "" else day(if (r.nextBoolean()) 40 else 700))
                m["ord"] = p(i)
                m["colour"] = p("#%06X".format(r.nextInt(0x1000000)))
                val gs = GENRES.shuffled(r).take(r.nextInt(4))
                m["genres"] = if (r.nextBoolean()) p(gs) else p(gs.joinToString(", "))
                if (r.nextInt(3) > 0) m["hours"] = p(r.nextInt(80))
                if (r.nextInt(3) == 0) m["hoursX"] = p(r.nextInt(120))
                if (r.nextInt(4) == 0) m["hours100"] = p(r.nextInt(200))
                m["added"] = p(day(200))
                if (r.nextInt(3) == 0) m["platforms"] = p(listOf("Switch", "PC", "PS5").shuffled(r).take(1 + r.nextInt(2)).joinToString(", "))
                if (r.nextInt(3) == 0) m["hype"] = p(r.nextInt(11))
                if (r.nextInt(25) == 0) m["comp"] = p(true)
                if (r.nextInt(5) == 0) m["img"] = p("https://example.com/c$i.jpg")
                if (r.nextInt(6) == 0) m["hero"] = p("https://example.com/h$i.jpg")
                JsonObject(m)
            }
            val played = games.filter { (it["state"] as JsonPrimitive).content == "played" || r.nextInt(12) == 0 }.map { g ->
                val m = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
                m["id"] = g["id"]!!
                m["real"] = p(if (r.nextInt(10) == 0) 0 else (r.nextInt(100) + 1) / 10.0)
                if (r.nextInt(10) == 0) m["z"] = p(1)
                if (r.nextInt(15) == 0) m["dk"] = p(true)
                if (r.nextInt(4) == 0) m["again"] = p(true)
                m["t"] = p(day(200))
                JsonObject(m)
            }
            val ids = played.map { (it["id"] as JsonPrimitive).content }
            val ds = (0 until duels).map {
                val a = ids[r.nextInt(ids.size)]
                val b = ids[r.nextInt(ids.size)]
                val w = when (r.nextInt(5)) { 0 -> "draw"; 1 -> ""; 2, 3 -> a; else -> b }
                JsonObject(mapOf("a" to p(a), "b" to p(b), "w" to p(w), "t" to p(day(200))) + (if (r.nextInt(4) == 0) mapOf("e" to p(1)) else emptyMap()))
            }
            return JsonObject(
                mapOf(
                    "games" to JsonArray(games), "played" to JsonArray(played), "duels" to JsonArray(ds),
                    "curPlat" to p(if (seed % 2 == 0) "PC" else "Switch"), "playStyle" to p(listOf("", "extras", "all")[seed % 3]),
                ),
            )
        }
    }
}
