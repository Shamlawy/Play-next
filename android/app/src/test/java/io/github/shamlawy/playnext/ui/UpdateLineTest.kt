package io.github.shamlawy.playnext.ui

import io.github.shamlawy.playnext.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The Updates card's words: after Check now it says when it looked, so a tap with nothing new still shows a result (v5). */
class UpdateLineTest {
    private val uae = ZoneId.of("Asia/Dubai")
    private val now = Instant.parse("2026-10-27T10:54:00Z").toEpochMilli() // 2:54 PM in the UAE

    @Test
    fun saysWhenItLooked() {
        assertEquals("Checked at 2:54 PM: v6 is the newest.", updateLine(Updater.State.UpToDate, 6, now, now, uae))
        assertEquals("Checked Oct 26 at 9:05 AM: v6 is the newest.", updateLine(Updater.State.UpToDate, 6, Instant.parse("2026-10-26T05:05:00Z").toEpochMilli(), now, uae))
        assertEquals("You have v6. Last looked at 2:54 PM; it checks by itself.", updateLine(Updater.State.Idle, 6, now, now, uae))
    }

    @Test
    fun neverLookedYet() {
        assertEquals("You have the newest version, v6.", updateLine(Updater.State.UpToDate, 6))
        assertEquals("You have v6. It checks for a new version by itself.", updateLine(Updater.State.Idle, 6))
    }
}
