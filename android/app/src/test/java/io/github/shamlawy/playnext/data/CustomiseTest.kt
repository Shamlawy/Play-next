package io.github.shamlawy.playnext.data

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
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
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Customise (Phase 1, M1): a change shows at once (your games and their numbers rebuilt with it), lasts through a new
 * day, and is never written anywhere (a read-only practice copy until switch day). Made-up games, a fake clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class CustomiseTest {
    @Volatile private var now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>

    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = """
        {"rk":""}
        {"games":[
          {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"genres":["Action"],"added":"2026-10-01"},
          {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-10-28","ord":2,"genres":["Action"],"hours":12,"added":"2026-06-14"}
        ],"duels":[],"played":[],"rowArtW":150}
    """.trimIndent()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Games.clock = { now }
        Games.zone = { ZoneId.of("Asia/Dubai") }
    }

    @After
    fun tearDown() {
        (field("tick") as Job?)?.cancel()
        put("day", null)
        put("practice", emptyMap<String, Any>())
        state.value = Games.State.Starting
        Games.clock = System::currentTimeMillis
        Games.zone = ZoneId::systemDefault
    }

    private fun open(): Games.State.Ready {
        val lib = Games::class.java.getDeclaredMethod("build", String::class.java).apply { isAccessible = true }.invoke(Games, backup) as Library
        val ready = Games::class.java.getDeclaredMethod("ready", Library::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(Games, lib, now - 3_600_000L) as Games.State.Ready
        state.value = ready
        return ready
    }

    private val ready get() = state.value as Games.State.Ready

    private fun waitFor(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!ok()) {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(what, System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
    }

    private fun files(): List<String> {
        val root: File = ApplicationProvider.getApplicationContext<Application>().filesDir
        return root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path + ":" + it.length() }.sorted().toList()
    }

    @Test fun aChangeShowsAtOnceAndKeepsTheCopysOtherSettings() {
        val first = open()
        Games.customise("deckOff", JsonPrimitive(true))
        waitFor("the games come back with the change") { ready.lib !== first.lib }
        assertEquals(JsonPrimitive(true), ready.lib["deckOff"])
        assertEquals("the backup's own settings stay", JsonPrimitive(150), ready.lib["rowArtW"])
        assertSame("the numbers are for the new games", ready.lib, ready.cards!!.lib)
        assertEquals("the copy's time stays", first.copyAt, ready.copyAt)

        Games.customise("rowArtW", JsonPrimitive(90))
        waitFor("a second change") { ready.lib["rowArtW"] == JsonPrimitive(90) }
        assertEquals("the first change stays too", JsonPrimitive(true), ready.lib["deckOff"])
    }

    @Test fun aNewDayKeepsYourChanges() {
        open()
        Games.customise("curPlat", JsonPrimitive("PC"))
        waitFor("the change") { ready.lib["curPlat"] == JsonPrimitive("PC") }
        (field("tick") as Job?)?.cancel()
        now = Instant.parse("2026-10-27T20:30:00Z").toEpochMilli() // 12:30 AM in Dubai: a game comes out
        assertTrue("a new day rebuilds", runBlocking { Games.rollover() })
        assertEquals("out on its release day", "backlog", ready.lib.games.first { it.id == "b" }.state)
        assertEquals("your change is still there", JsonPrimitive("PC"), ready.lib["curPlat"])
    }

    @Test fun nothingIsWritten() {
        open()
        val prefs = Store.prefs.all.toString()
        val before = files()
        Games.customise("cardBg", JsonPrimitive("plain"))
        waitFor("the change") { ready.lib["cardBg"] == JsonPrimitive("plain") }
        assertEquals("no setting saved on the phone", prefs, Store.prefs.all.toString())
        assertEquals("no file written", before, files())
        assertFalse("the backup text itself is untouched", backup.contains("cardBg"))
    }
}
