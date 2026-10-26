package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.ui.Route
import java.net.URI
import java.net.URLDecoder

/**
 * The one deep-link router: `playnext://open?nudge=<what>`, the web app's `?nudge=` (shortcuts, widgets and
 * notifications use it in later phases). Pure (java.net.URI), so JVM tests check it.
 */
object Links {
    fun nudge(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val u = runCatching { URI(link) }.getOrNull() ?: return null
        if (u.scheme != "playnext" || u.host != "open") return null
        return u.rawQuery.orEmpty().split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it[0] == "nudge" }
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?.takeIf { it.isNotBlank() }
    }

    /** Where a link opens. Unknown nudges open Home (a newer link on an older app must never crash it). */
    fun route(link: String?): Route? = when (nudge(link)) {
        null -> null
        "probe" -> Route.Probe
        else -> Route.Home
    }
}
