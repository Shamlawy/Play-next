package io.github.shamlawy.playnext

import android.app.Application
import android.content.Context
import io.github.shamlawy.playnext.report.Report
import io.github.shamlawy.playnext.report.Reporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** The problem-report queue (fix list E and F): nothing sent twice, nothing lost, sent at every start, a full phone survives. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ReporterTest {
    private lateinit var ctx: Context
    private val sent = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        Store.init(ctx)
        Store.prefs.edit().clear().commit()
        File(ctx.filesDir, "reports").deleteRecursively()
        Reporter.init(ctx)
        sent.clear()
        Reporter.send = { body -> sent += body; true }
    }

    private fun r(i: Int) = Report(sig = "s$i", kind = "error", msg = "made up problem")

    private fun sigsSent() = sent.flatMap { b -> Regex("\"sig\":\"(s\\d+)\"").findAll(b).map { it.groupValues[1] }.toList() }

    private fun waitFor(ms: Long = 8_000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!ok() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    /** A first batch that's slow to answer, then [rest] for every later call. */
    private fun slowFirst(rest: Boolean): Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>> {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        Reporter.send = { body ->
            sent += body
            if (calls.getAndIncrement() == 0) { started.complete(Unit); gate.await(); true } else rest
        }
        return started to gate
    }

    @Test
    fun aBatchOnItsWayIsNeverSentTwice() {
        val (started, gate) = slowFirst(rest = true)
        Reporter.enqueue(listOf(r(1)))
        Reporter.flushSoon(0)
        runBlocking { withTimeout(5_000) { started.await() } }
        // a newer report while the first batch waits for the helper's answer
        Reporter.enqueue(listOf(r(2)))
        Reporter.flushSoon(0)
        Thread.sleep(300)
        gate.complete(Unit)
        waitFor { Reporter.queued() == 0 && "s2" in sigsSent() }
        Thread.sleep(200)
        assertEquals("each report reaches the helper once: ${sigsSent()}", listOf("s1", "s2"), sigsSent().sorted())
    }

    @Test
    fun reportsQueuedWhileABatchIsOutAreKept() {
        val (started, gate) = slowFirst(rest = false) // the signal goes after the first batch
        Reporter.enqueue((1..10).map(::r))
        Reporter.flushSoon(0)
        runBlocking { withTimeout(5_000) { started.await() } }
        // 25 more while the first 10 are out: the queue's cap of 30 lets the oldest 5 go
        Reporter.enqueue((11..35).map(::r))
        gate.complete(Unit)
        waitFor { sent.size >= 2 }
        Thread.sleep(200)
        assertEquals("only the 10 that went are gone", 25, Reporter.queued())
    }

    @Test
    fun everyStartSendsWhatTheLastRunLeft() {
        Reporter.send = { false }
        Reporter.enqueue(listOf(r(8))) // a "Report a problem" with no signal
        Thread.sleep(100)
        Reporter.send = { body -> sent += body; true }
        val crash = File(ctx.filesDir, "made-up-crash.json").apply { writeText(Json.encodeToString(Report.serializer(), r(7))) }
        Reporter.adopt(listOf(crash))
        waitFor(10_000) { sigsSent().containsAll(listOf("s7", "s8")) }
        assertEquals(listOf("s7", "s8"), sigsSent().sorted())
        assertTrue(!crash.exists())
    }

    @Test
    fun twoReleasesOnOneDayBothSayTheyRan() {
        Reporter.send = { false }
        Reporter.ranOnce(9)
        Reporter.ranOnce(10)
        waitFor(2_000) { Reporter.queued() == 2 }
        assertEquals(2, Reporter.queued())
        assertEquals(10, Store.prefs.getInt("ranV", 0))
    }

    @Test
    fun aFullPhoneDoesntCrashAndTheReportStillGoes() {
        // the queue's file can't be written (a directory sits where it goes), as on a full phone
        File(ctx.filesDir, "reports/queue.tmp").mkdirs()
        Reporter.enqueue(listOf(r(1)))
        assertEquals(1, Reporter.queued())
        assertTrue(runBlocking { Reporter.flush() })
        assertEquals(listOf("s1"), sigsSent())
        assertEquals(0, Reporter.queued())
    }
}
