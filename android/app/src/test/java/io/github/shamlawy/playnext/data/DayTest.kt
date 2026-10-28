package io.github.shamlawy.playnext.data

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A new day without a new backup or a restart (Perfect run, basics 10): a coming game is out on its release day, and the
 * header numbers follow, at midnight, on coming back after the phone slept, and when the clock or zone changes. A fake
 * clock; made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DayTest {
    private val dubai = ZoneId.of("Asia/Dubai")
    @Volatile private var now = 0L
    @Volatile private var zone = dubai

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>

    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)

    /** One game out tomorrow (Dubai's tomorrow), one out already. */
    private val backup = """
        {"rk":""}
        {"games":[
          {"id":"a","title":"Starfall Lantern","state":"upcoming","date":"2026-10-28","ord":1,"genres":["Action"],"added":"2026-10-01"},
          {"id":"b","title":"Copper Meadow","state":"backlog","date":"2026-05-11","ord":2,"genres":["Action"],"hours":12,"added":"2026-06-14"}
        ],"duels":[],"played":[]}
    """.trimIndent()

    private fun at(s: String) = Instant.parse(s).toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Games.clock = { now }
        Games.zone = { zone }
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        Games::class.java.getDeclaredField("day").apply { isAccessible = true }.set(Games, null)
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
    }

    /** This phone's copy opened at [t], as Games opens it (build, then Ready). */
    private fun open(t: Long): Games.State.Ready {
        now = t
        val lib = Games::class.java.getDeclaredMethod("build", String::class.java).apply { isAccessible = true }.invoke(Games, backup) as Library
        val ready = Games::class.java.getDeclaredMethod("ready", Library::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(Games, lib, t - 3_600_000L) as Games.State.Ready
        state.value = ready
        return ready
    }

    private val ready get() = state.value as Games.State.Ready

    private fun stateOf(id: String) = ready.lib.games.first { it.id == id }.state
    private fun stat(kind: String, i: Int) = ready.cards!![kind]!!.stats[i].let { it.value + (it.unit ?: "") }

    private fun waitFor(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!ok()) {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(what, System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
    }

    @Test fun aNewDayRebuildsFromTheCopyInMemory() {
        val first = open(at("2026-10-27T19:30:00Z")) // 11:30 PM in Dubai
        assertSame("the cards come with the games", first.lib, first.cards!!.lib)
        assertEquals("coming tomorrow", "upcoming", stateOf("a"))
        assertEquals("Plan: a day to go", "1d", stat("plan", 0))
        assertEquals("Queue: one out now", "1", stat("queue", 2))

        // the midnight timer stays out of it (it has its own test): this one is the rebuild itself
        (field("tick") as Job?)?.cancel()
        now = at("2026-10-27T20:30:00Z") // 12:30 AM: release day
        assertTrue("a new day rebuilds", runBlocking { Games.rollover() })
        assertEquals("out on its release day", "backlog", stateOf("a"))
        assertEquals("Plan: today", "Today", stat("plan", 0))
        assertEquals("Queue: both out", "2", stat("queue", 2))
        assertSame("the new cards are for the new games", ready.lib, ready.cards!!.lib)
        assertEquals("the copy's time stays", first.copyAt, ready.copyAt)
        assertFalse("the same day again changes nothing", runBlocking { Games.rollover() })
    }

    @Test fun nextFlipIsTheFirstMidnightOfTwo() {
        // Dubai's midnight comes first in the evening; after it, UTC's midnight (4 AM there) is next
        assertEquals(at("2026-10-27T20:00:00Z"), Games.nextFlip(at("2026-10-27T19:30:00Z"), dubai))
        assertEquals(at("2026-10-28T00:00:00Z"), Games.nextFlip(at("2026-10-27T21:00:00Z"), dubai))
        assertEquals(at("2026-10-28T00:00:00Z"), Games.nextFlip(at("2026-10-27T08:00:00Z"), ZoneId.of("UTC")))
    }

    @Test fun theTimerWakesAtMidnight() {
        open(at("2026-10-27T20:00:00Z") - 200)
        now = at("2026-10-27T20:00:01Z")
        waitFor("midnight rebuilt your games by itself") { stateOf("a") == "backlog" }
        assertEquals("Today", stat("plan", 0))
    }

    @Test fun comingBackAfterSleepCatchesUp() {
        open(at("2026-10-27T19:30:00Z"))
        // the phone slept through midnight: its sleep pauses the timer, coming back to the app checks
        now = at("2026-10-28T03:00:00Z")
        Games.onOpen(ApplicationProvider.getApplicationContext())
        waitFor("coming back rebuilt your games") { stateOf("a") == "backlog" }
    }

    @Test fun aZoneChangeMovesTodayAtOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        (field("watching") as AtomicBoolean).set(false)
        Games.watchClock(app)
        open(at("2026-10-27T12:00:00Z")) // 4 PM in Dubai
        assertEquals("upcoming", stateOf("a"))
        // flown to UTC+14, where it's already 2 AM on release day
        zone = ZoneId.of("Pacific/Kiritimati")
        app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        waitFor("the zone change rebuilt your games") { stateOf("a") == "backlog" }
        assertEquals("Today", stat("plan", 0))
    }
}
