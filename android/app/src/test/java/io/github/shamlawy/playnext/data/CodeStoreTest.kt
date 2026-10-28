package io.github.shamlawy.playnext.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The restore code's lock and the offline copy (Perfect run basics 11, speed 4, bugs 6), with a stand-in key store
 * (Robolectric has no AndroidKeyStore): software AES keys, and one that stumbles. Made-up codes only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class CodeStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val code = "PN-ABCD-EFGH-JKMN-PQRS-TVWX"
    private val real = CodeStore.keys

    /** Keys in a map; [stumble] makes every read of the key store throw, as a busy one does. */
    private class Soft : CodeStore.Keys {
        val map = mutableMapOf<String, SecretKey>()
        var stumble = false
        override fun get(alias: String): SecretKey? = if (stumble) throw ProviderException("key store busy") else map[alias]
        override fun make(alias: String): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { map[alias] = it }
        override fun delete(alias: String) {
            map.remove(alias)
        }
    }

    private val soft = Soft()
    private val file get() = File(ctx.filesDir, "code.bin")

    @Before
    fun setUp() {
        Store.init(ctx)
        CodeStore.keys = soft
        file.delete()
    }

    @After
    fun tearDown() {
        CodeStore.keys = real
        file.delete()
    }

    /** The file as android-v6 to v9 wrote it: IV, then the code sealed with [key]. */
    private fun lockWith(key: SecretKey) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        file.writeBytes(c.iv + c.doFinal(Code.normalize(code).toByteArray()))
    }

    @Test fun savedThenReadWithTheTeeKey() {
        CodeStore.save(ctx, code)
        assertEquals(CodeStore.Read.Got(Code.normalize(code)), CodeStore.read(ctx))
        assertEquals(setOf("pn-restore-code-tee"), soft.map.keys)
    }

    /** A code the old StrongBox key locked opens once with it, is locked again with the TEE key, and the old key goes. */
    @Test fun aStrongBoxCodeMovesToTheTeeKeyOnce() {
        lockWith(soft.make("pn-restore-code"))
        val before = file.readBytes()
        assertEquals(CodeStore.Read.Got(Code.normalize(code)), CodeStore.read(ctx))
        assertEquals("the old key is gone, the new one holds it", setOf("pn-restore-code-tee"), soft.map.keys)
        assertFalse("the file was locked again", before.contentEquals(file.readBytes()))
        assertEquals(CodeStore.Read.Got(Code.normalize(code)), CodeStore.read(ctx))
    }

    /** A move that stopped after the new key was made but before the file was swapped still opens, and finishes. */
    @Test fun aMoveCutShortStillOpens() {
        lockWith(soft.make("pn-restore-code"))
        soft.make("pn-restore-code-tee")
        assertEquals(CodeStore.Read.Got(Code.normalize(code)), CodeStore.read(ctx))
        assertEquals(setOf("pn-restore-code-tee"), soft.map.keys)
    }

    /** If the new lock can't be written, the code still opens and the old key stays for the next try. */
    @Test fun aMoveThatCantSaveKeepsTheOldKey() {
        lockWith(soft.make("pn-restore-code"))
        val tmp = File(ctx.filesDir, "code.bin.new").apply { mkdirs() } // a folder where the new file goes: the write fails
        try {
            assertEquals(CodeStore.Read.Got(Code.normalize(code)), CodeStore.read(ctx))
            assertTrue("the old key stays", "pn-restore-code" in soft.map)
        } finally {
            tmp.delete()
        }
    }

    @Test fun nothingStoredOrAKeyGoneIsNone() {
        assertEquals(CodeStore.Read.None, CodeStore.read(ctx))
        lockWith(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        assertEquals("no key at all (a lock screen reset)", CodeStore.Read.None, CodeStore.read(ctx))
        soft.make("pn-restore-code-tee")
        assertEquals("a key that didn't lock it", CodeStore.Read.None, CodeStore.read(ctx))
    }

    /** A busy key store is "later", never "none": the old code read any exception as no code. */
    @Test fun aStumbleIsLater() {
        CodeStore.save(ctx, code)
        soft.stumble = true
        assertEquals(CodeStore.Read.Later, CodeStore.read(ctx))
        assertTrue("the code file stays", file.isFile)
    }

    @Suppress("UNCHECKED_CAST")
    private val games = Games::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(null) as MutableStateFlow<Games.State>

    /** At start, a key store hiccup keeps the offline copy (it used to be deleted) and offers Try again. */
    @Test fun aHiccupAtStartKeepsTheOfflineCopy() {
        CodeStore.save(ctx, code)
        val copy = File(ctx.filesDir, "backup/latest.bin").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        Store.prefs.edit().putLong("copyT", 1L).apply()
        soft.stumble = true
        try {
            Games.start(ctx)
            val st = runBlocking { withTimeout(5_000) { Games.state.first { it is Games.State.Fetching && it.why == Games.KEY_LATER } } }
            assertEquals(Games.State.Fetching(Games.KEY_LATER), st)
            assertTrue("the offline copy stays", copy.isFile)
            assertTrue("and the code", file.isFile)
        } finally {
            soft.stumble = false
            Games.forget(ctx)
            runBlocking { withTimeout(5_000) { Games.state.first { it is Games.State.NoCode } } }
            games.value = Games.State.Starting
            Store.prefs.edit().remove("copyT").apply()
        }
    }

    /** A full phone: the copy isn't kept, nothing throws, nothing half-written stays (bugs 6). */
    @Test fun aCopyThatCantBeWrittenDoesntThrow() {
        val blocked = File(ctx.filesDir, "full").apply { deleteRecursively(); writeText("a file where the folder should be") }
        assertFalse(Games.keep(File(blocked, "latest.bin"), byteArrayOf(1, 2, 3)))
        val ok = File(ctx.filesDir, "fine/latest.bin")
        assertTrue(Games.keep(ok, byteArrayOf(4, 5, 6)))
        assertArrayEquals(byteArrayOf(4, 5, 6), ok.readBytes())
        assertNull(ok.parentFile?.list()?.firstOrNull { it == "latest.new" })
        blocked.delete()
        ok.parentFile?.deleteRecursively()
    }
}
