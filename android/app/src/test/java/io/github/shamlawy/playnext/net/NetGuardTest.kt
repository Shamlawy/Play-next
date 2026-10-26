package io.github.shamlawy.playnext.net

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The switch sits inside the one client: a refused call never reaches the network. */
class NetGuardTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun readsGoOut() = runTest {
        server.enqueue(MockResponse.Builder().body("ok").build())
        val got = Net.call(Request.Builder().url(server.url("/x")).build()) { it.body.string() }
        assertEquals("ok", got)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun writesToOtherHostsNeverLeave() = runTest {
        try {
            Net.call(Net.postJson(server.url("/u/abc").toString(), "{}")) { it.code }
            fail("a POST to another host must be refused")
        } catch (e: WriteBlocked) {
            assertEquals(0, server.requestCount)
        }
    }
}
