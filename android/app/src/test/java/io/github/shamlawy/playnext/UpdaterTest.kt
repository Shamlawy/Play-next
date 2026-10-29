package io.github.shamlawy.playnext

import android.app.Application
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Release
import io.github.shamlawy.playnext.update.ReleaseFeed
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.random.Random

/** The self-updater's download and its failures (fix list G). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UpdaterTest {
    private val apk = Random(7).nextBytes(300_000)
    private lateinit var dir: File

    @Before
    fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        Store.init(ctx)
        Store.prefs.edit().clear().commit()
        File(ctx.filesDir, "reports").deleteRecursively()
        Reporter.init(ctx)
        Reporter.send = { false }
        dir = File(ctx.cacheDir, "update-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun part() = File(dir, ReleaseFeed.apkName(99) + ".part")

    /** Serves [bytes] with Range support; the n-th answer stops after cutAfter[n] bytes, like a phone losing signal. */
    private class Server(private val bytes: ByteArray, private val cutAfter: List<Int?> = emptyList()) : AutoCloseable {
        val ranges = CopyOnWriteArrayList<String>()
        private val sock = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val url get() = "http://127.0.0.1:${sock.localPort}/${ReleaseFeed.apkName(99)}"

        init {
            thread(isDaemon = true) {
                var n = 0
                while (!sock.isClosed) {
                    val c = runCatching { sock.accept() }.getOrNull() ?: break
                    c.use {
                        val rd = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        var range = ""
                        while (true) {
                            val l = rd.readLine() ?: break
                            if (l.isEmpty()) break
                            if (l.startsWith("Range:", ignoreCase = true)) range = l.substringAfter(':').trim()
                        }
                        ranges += range
                        val from = Regex("bytes=(\\d+)-").find(range)?.groupValues?.get(1)?.toInt() ?: 0
                        val out = c.getOutputStream()
                        if (from >= bytes.size) {
                            out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        } else {
                            val rest = bytes.copyOfRange(from, bytes.size)
                            val head = if (from > 0) "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $from-${bytes.size - 1}/${bytes.size}\r\n" else "HTTP/1.1 200 OK\r\n"
                            out.write((head + "Content-Length: ${rest.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            out.write(rest, 0, minOf(cutAfter.getOrNull(n) ?: rest.size, rest.size))
                        }
                        out.flush()
                        n++
                    }
                }
            }
        }

        override fun close() = sock.close()
    }

    @Test
    fun aFinishedPartGoesStraightToTheCheck() {
        // a run stopped after the last byte, before the swap: asking for the bytes after it is a 416
        Server(apk).use { s ->
            part().writeBytes(apk)
            val got = runBlocking { Updater.fetch(dir, Release(99, s.url, apk.size.toLong(), null, null), sha(apk)) }
            assertArrayEquals(apk, got.readBytes())
            assertEquals("nothing left to download", emptyList<String>(), s.ranges.toList())
        }
    }

    @Test
    fun aDroppedLineResumesWhereItStopped() {
        Server(apk, cutAfter = listOf(50_000)).use { s ->
            part().writeBytes(apk.copyOf(100_000))
            val got = runBlocking { Updater.fetch(dir, Release(99, s.url, apk.size.toLong(), null, null), sha(apk)) }
            assertArrayEquals(apk, got.readBytes())
            assertEquals(2, s.ranges.size)
            assertEquals("bytes=100000-", s.ranges[0])
            assertNotEquals("the second try asks from where the first stopped", s.ranges[0], s.ranges[1])
        }
    }

    @Test
    fun anUnexpectedErrorIsAFailureNotACrash() {
        runBlocking { Updater.attempt { throw SerializationException("made up") } }
        assertTrue(Updater.state.value is Updater.State.Failed)
        waitFor { Reporter.queued() == 1 }
        assertEquals(1, Reporter.queued())
    }

    @Test
    fun aFailureThatKeepsComingBackIsToldOnce() {
        repeat(5) { runBlocking { Updater.attempt { throw IOException("download 416") } } }
        waitFor { Reporter.queued() >= 1 }
        Thread.sleep(300)
        assertEquals(1, Reporter.queued())
    }

    private fun waitFor(ms: Long = 3_000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!ok() && System.currentTimeMillis() < end) Thread.sleep(20)
    }
}
