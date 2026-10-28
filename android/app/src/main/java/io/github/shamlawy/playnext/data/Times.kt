package io.github.shamlawy.playnext.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The app's one way of writing a day and a time, as the web app does (12-hour, short US month names, the phone's zone). */
object Times {
    private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** "Oct 27" (the web app's `toLocaleDateString(undefined, { day: "numeric", month: "short" })`). */
    fun day(d: LocalDate): String = d.format(DAY)

    /** [lead] + "2:05 PM" when [at] is on [now]'s day, else "Oct 25" + [join] + "2:05 PM". */
    fun at(at: Long, now: Long, zone: ZoneId, join: String = ", ", lead: String = ""): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val time = t.format(TIME)
        return if (t.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) lead + time else t.format(DAY) + join + time
    }
}
