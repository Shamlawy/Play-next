package io.github.shamlawy.playnext.report

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The web app's breadcrumbs (`bugCrumb`, index.html 17243): the screens you went through and what you tapped, by what
 * it is, never by what it says ("21:40:12 tap [tab] on games/queue", "21:40:12 screen plan/month"). 14 are kept and
 * a report carries the last 10, so a problem comes with the steps that led to it.
 */
object Crumbs {
    private val list = ArrayDeque<String>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun add(what: String, at: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) = synchronized(list) {
        list.addLast(clock.format(Instant.ofEpochMilli(at).atZone(zone)) + " " + what)
        while (list.size > 14) list.removeFirst()
    }

    /** A tap on [what] (a kind of thing: tab, view, cover, settings…) while [screen] was open. */
    fun tap(what: String, screen: String) = add("tap [$what] on $screen")

    fun screen(name: String) = add("screen $name")

    fun last(n: Int = 10): List<String> = synchronized(list) { list.toList().takeLast(n) }

    fun clear() = synchronized(list) { list.clear() }
}
