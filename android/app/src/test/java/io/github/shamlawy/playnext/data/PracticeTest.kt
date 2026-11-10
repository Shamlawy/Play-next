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
 * The one practice copy (Phase 2, 3-critic.md M7) through Games: a game started on its page shows everywhere at once,
 * stays through a Customise change and a new day, keeps the Queue's drags and deletes (they hold on to the backup, not
 * to one copy of it), and is never written anywhere. Made-up games, a fake clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PracticeTest {
    @Volatile private var now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Suppress("UNCHECKED_CAST")
    private val state = field("_state") as MutableStateFlow<Games.State>

    private fun field(name: String): Any? = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.get(Games)
    private fun put(name: String, v: Any?) = Games::class.java.getDeclaredField(name).apply { isAccessible = true }.set(Games, v)

    private val backup = """
        {"rk":""}
        {"games":[
          {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"genres":["Action"]},
          {"id":"b","title":"Copper Meadow","state":"upcoming","date":"2026-10-28","ord":2,"genres":["Action"],"hours":12}
        ],"duels":[],"played":[]}
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

    private fun open(text: String = backup): Games.State.Ready {
        val lib = Games::class.java.getDeclaredMethod("build", String::class.java).apply { isAccessible = true }.invoke(Games, text) as Library
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

    @Test fun aGameStartedShowsEverywhereAndLastsThroughOtherChanges() {
        val first = open()
        val a = first.lib.games.first { it.id == "a" }
        Games.change(Practice.start(first.lib, a, now) { Games.practiced(it) })
        waitFor("the games come back started") { ready.lib.games.first { it.id == "a" }.rawState == "playing" }
        assertSame("the same backup underneath", first.lib.from, ready.lib.from)
        assertSame("the header numbers are for the new games", ready.lib, ready.cards!!.lib)
        assertEquals(setOf("a"), Practice.changed(ready.lib))
        Games.customise("deckOff", JsonPrimitive(true))
        waitFor("a Customise change") { ready.lib["deckOff"] == JsonPrimitive(true) }
        assertEquals("the start stays", "playing", ready.lib.games.first { it.id == "a" }.rawState)
        (field("tick") as Job?)?.cancel()
        now = Instant.parse("2026-10-27T20:30:00Z").toEpochMilli() // 12:30 AM in Dubai: b comes out
        assertTrue("a new day rebuilds", runBlocking { Games.rollover() })
        assertEquals("playing", ready.lib.games.first { it.id == "a" }.rawState)
        assertEquals("backlog", ready.lib.games.first { it.id == "b" }.state)
        assertSame("still the same backup", first.lib.from, ready.lib.from)
    }

    @Test fun aDeleteGoesEverywhereAndUndoBringsItBack() {
        val first = open()
        Games.change(mapOf(Practice.GONE to Practice.gone(Games.practiced(Practice.GONE), "b", true)))
        waitFor("b is gone") { ready.lib.games.none { it.id == "b" } }
        assertEquals("the header numbers follow", 1, ready.cards!!.queue!!.lib.games.size)
        Games.change(mapOf(Practice.GONE to Practice.gone(Games.practiced(Practice.GONE), "b", false)))
        waitFor("b is back") { ready.lib.games.any { it.id == "b" } }
        assertSame(first.lib.from, ready.lib.from)
    }

    @Test fun aNewerBackupStartsYourGamesWholeAgain() {
        val first = open()
        Games.change(Practice.start(first.lib, first.lib.games.first { it.id == "a" }, now) { Games.practiced(it) })
        Games.change(mapOf(Practice.GONE to Practice.gone(Games.practiced(Practice.GONE), "b", true)))
        Games.customise("deckOff", JsonPrimitive(true))
        waitFor("the practice changes") { ready.lib.games.none { it.id == "b" } && ready.lib["deckOff"] == JsonPrimitive(true) }
        open(backup) // the same backup again (a check that found nothing new): the changes stay
        assertEquals("playing", ready.lib.games.first { it.id == "a" }.rawState)
        assertTrue(ready.lib.games.none { it.id == "b" })
        // a newer backup where a was finished for real: its finish shows, b is back, Customise's change stays
        open(backup.replace("\"played\":[]", "\"played\":[{\"id\":\"a\",\"real\":8.5,\"t\":\"2026-10-26\"}]").replace("\"state\":\"backlog\"", "\"state\":\"played\""))
        assertEquals("played", ready.lib.games.first { it.id == "a" }.rawState)
        assertEquals(1, ready.lib.played.size)
        assertTrue("the delete was on the older backup", ready.lib.games.any { it.id == "b" })
        assertEquals(JsonPrimitive(true), ready.lib["deckOff"])
        assertEquals(emptySet<String>(), Practice.changed(ready.lib))
    }

    @Test fun nothingIsWritten() {
        val first = open()
        val prefs = Store.prefs.all.toString()
        val before = files()
        Games.change(Practice.finish(first.lib, first.lib.games[0], now, ZoneId.of("Asia/Dubai")) { Games.practiced(it) })
        waitFor("the finish") { ready.lib.played.isNotEmpty() }
        assertEquals("no setting saved on the phone", prefs, Store.prefs.all.toString())
        assertEquals("no file written", before, files())
    }
}
