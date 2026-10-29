package io.github.shamlawy.playnext.data

import android.content.Context
import android.provider.Settings
import io.github.shamlawy.playnext.Device
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A made-up backup for CI's emulators, so the Baseline Profile and the release smoke test walk real screens (Home,
 * every tab, a view swipe, a game page, Settings) instead of the restore screen. On only on an emulator, and only when
 * the shell has set `adb shell settings put global playnext_demo 1`: writing a global setting needs
 * WRITE_SECURE_SETTINGS, which no app can get, and a key Android itself doesn't define stays readable to apps at
 * target 37 (SettingsProvider's checkReadableAnnotation only locks Android's own @hide keys). It never touches the
 * phone's code, its copy or the network. Made-up titles only (the repo is public).
 */
object Demo {
    const val KEY = "playnext_demo"

    fun on(ctx: Context): Boolean =
        Device.emulator && runCatching { Settings.Global.getInt(ctx.contentResolver, KEY, 0) == 1 }.getOrDefault(false)

    internal val FIRST = listOf("Starfall", "Hollow", "Copper", "Ninefold", "Quiet", "Paper", "Lumen", "Ember", "Glass", "Velvet", "Iron", "Moss")
    private val SECOND = listOf("Lantern", "Tide", "Meadow", "Gate", "Orbit", "Kingdom", "Drift", "Harbor", "Circuit", "Vigil")
    private val GENRES = listOf("Action", "RPG", "Adventure", "Strategy", "Puzzle", "Platformer", "Shooter", "Racing")
    private val PLATS = listOf("Switch", "PS5", "PC", "Xbox")
    private val COLOURS = listOf("#3A6FD8", "#C2413A", "#2E9E6A", "#B06AD8", "#D89A3A", "#5AB3C9", "#E0617F", "#7A8B3A")

    /** 40 games in every state, with genres, hours, release and added dates around today, 14 finishes and 24 duels. */
    fun text(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun day(d: Long): String = today.plusDays(d).toString()
        val games = (0 until 40).map { i ->
            val state = when {
                i < 12 -> "backlog"
                i < 15 -> "playing"
                i < 26 -> "upcoming"
                else -> "played"
            }
            val date = when (state) {
                // coming: some this week, some months out, two with no date yet
                "upcoming" -> if (i % 5 == 0) "" else day(3L + (i - 16) * 17)
                else -> day(-20L - i * 23)
            }
            buildJsonObject {
                put("id", "d$i")
                put("title", "${FIRST[i % FIRST.size]} ${SECOND[(i * 7) % SECOND.size]}" + if (i >= FIRST.size * 2) " ${i / FIRST.size + 1}" else "")
                put("state", state)
                put("date", date)
                put("ord", i + 1)
                put("colour", COLOURS[i % COLOURS.size])
                put("genres", JsonArray(listOf(GENRES[i % GENRES.size], GENRES[(i * 3 + 1) % GENRES.size]).distinct().map(::JsonPrimitive)))
                put("platforms", PLATS[i % PLATS.size])
                if (state != "upcoming") put("hours", 6 + (i * 13) % 70)
                put("hype", (i * 7) % 11)
                put("added", (if (date.isNotEmpty() && date < day(-30)) LocalDate.parse(date).plusDays(9) else today.minusDays(5L + i)).toString())
            }
        }
        val played = (26 until 40).map { i ->
            buildJsonObject {
                put("id", "d$i")
                put("real", 5.5 + (i * 37 % 45) / 10.0)
                put("t", day(-10L - (i - 26) * 19))
                if (i % 4 == 0) put("again", true)
            }
        }
        val duels = (0 until 24).map { n ->
            val a = 26 + n % 14
            val b = 26 + (n * 5 + 3) % 14
            buildJsonObject {
                put("a", "d$a")
                put("b", "d${if (b == a) 26 + (b - 25) % 14 else b}")
                put("w", "d${if (n % 3 == 0) b else a}")
                put("t", day(-2L - n * 4))
            }
        }
        return JsonObject(
            mapOf(
                "theme" to JsonPrimitive("midnight"),
                "bgGrad" to JsonPrimitive(30),
                "games" to JsonArray(games),
                "played" to JsonArray(played),
                "duels" to JsonArray(duels),
            ),
        ).toString()
    }
}
