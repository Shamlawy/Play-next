package io.github.shamlawy.playnext

import android.content.Intent
import io.github.shamlawy.playnext.kit.SharedName
import io.github.shamlawy.playnext.ui.Route
import java.net.URI
import java.net.URLDecoder

/**
 * The one deep-link router: `playnext://open?nudge=<what>&g=<game>&m=<month>`, the web app's `?nudge=` table
 * (android/parity/0-map/3-kit.md §5), used by the icon shortcuts now and by widgets and notifications later. A screen
 * the app doesn't have yet opens the nearest one it has; a link it doesn't know opens Home (a newer link on an older
 * app must never crash it). Pure (java.net.URI), so JVM tests check it.
 */
object Links {
    /** The link's query, decoded; null when it isn't a playnext://open link. */
    fun query(link: String?): Map<String, String>? {
        if (link.isNullOrBlank()) return null
        val u = runCatching { URI(link) }.getOrNull() ?: return null
        if (u.scheme != "playnext" || u.host != "open") return null
        return u.rawQuery.orEmpty().split('&')
            .map { it.split('=', limit = 2) }
            .filter { it[0].isNotEmpty() }
            .associate { it[0] to runCatching { URLDecoder.decode(it.getOrElse(1) { "" }, Charsets.UTF_8) }.getOrDefault("") }
    }

    fun nudge(link: String?): String? = query(link)?.get("nudge")?.takeIf { it.isNotBlank() }

    /** Where a link opens. */
    fun route(link: String?): Route? {
        val q = query(link) ?: return null
        val g = q["g"]?.takeIf { it.isNotBlank() }
        return when (q["nudge"]?.takeIf { it.isNotBlank() }) {
            null -> null
            "probe" -> Route.Probe
            "add" -> Route.Tab("add")
            "queue" -> Route.Tab("games", "queue")
            // "What should I play?" (the shortcut, Tonight's pick, the weekly and comeback pings): On deck's first game
            "next" -> Route.Game(null)
            // a game's own link (its release, countdown, memory, price, the calendar feed); finish, reviews and a Steam
            // session open its page until the page's own parts arrive
            "release", "game", "finish", "reviews", "steam" -> g?.let { Route.Game(it) } ?: Route.Tab("home")
            // Quick duel comes with Taste's phase, the quick score picker with Played's: their tabs until then
            "duel" -> Route.Tab("taste", "duels")
            "badge", "verdict" -> Route.Tab("games", "played")
            else -> Route.Tab("home")
        }
    }

    /** Something shared into the app (Android's share sheet, the web's share target): Add, with the game it names. */
    fun shared(intent: Intent?): Route? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return null
        // styled text (a SpannableString) comes as a CharSequence, which getStringExtra reads as nothing
        fun text(key: String) = intent.getCharSequenceExtra(key)?.toString()
        val s = SharedName.of(text(Intent.EXTRA_SUBJECT) ?: text(Intent.EXTRA_TITLE), text(Intent.EXTRA_TEXT))
        return Route.Add(s)
    }

    /**
     * What an intent that opened the app asks for: a link, or something shared. A web page's link (BROWSABLE) can't
     * start the 120 Hz test's 20 seconds of flinging; Settings, the release check's adb and the profile walk still can.
     */
    fun of(intent: Intent?): Route? = shared(intent)
        ?: route(intent?.dataString)?.takeUnless { it == Route.Probe && intent?.hasCategory(Intent.CATEGORY_BROWSABLE) == true }
}
