package io.github.shamlawy.playnext.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.shamlawy.playnext.crash.swapIn
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The restore code, kept locked by Android's key store (critic G5): an AES-GCM key that never leaves the phone's secure
 * hardware locks the code in a small file. No fingerprint asked (not picked, Oct 23). If the key is ever gone (a lock
 * screen reset), the file can't open and the app asks for the code again. Android's own backup is off for the app, so
 * neither leaves the phone.
 */
object CodeStore {
    /** The key since android-v10: the phone's ordinary secure hardware (TEE), quick enough for the splash to wait on. */
    private const val ALIAS = "pn-restore-code-tee"

    /** The key before: StrongBox, a separate slow chip (speed 4); a code it locked moves to [ALIAS] at its first read. */
    private const val STRONGBOX = "pn-restore-code"

    private fun file(ctx: Context) = File(ctx.filesDir, "code.bin")

    /** What [read] found. */
    sealed interface Read {
        data class Got(val code: String) : Read

        /** Nothing stored, or a code that can never open again (its key is gone): ask for it. */
        data object None : Read

        /** The key store didn't answer just now: the code is still there, so nothing may be thrown away. */
        data object Later : Read
    }

    /** The key store, swappable so tests can make it stumble. */
    internal interface Keys {
        /** The key, or null when there is none; throws when the key store can't say. */
        fun get(alias: String): SecretKey?
        fun make(alias: String): SecretKey
        fun delete(alias: String)
    }

    internal var keys: Keys = AndroidKeys

    private object AndroidKeys : Keys {
        private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        override fun get(alias: String): SecretKey? = store().getKey(alias, null) as? SecretKey
        override fun delete(alias: String) = store().deleteEntry(alias)
        override fun make(alias: String): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setIsStrongBoxBacked(false)
                    .build(),
            )
            generateKey()
        }
    }

    fun save(ctx: Context, code: String) {
        val key = keys.get(ALIAS) ?: keys.make(ALIAS)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val out = c.iv + c.doFinal(Code.normalize(code).toByteArray(Charsets.UTF_8))
        val tmp = File(ctx.filesDir, "code.bin.new")
        tmp.writeBytes(out)
        swapIn(tmp, file(ctx))
    }

    /**
     * The code; [Read.None] when there's none or it can't be opened any more; [Read.Later] when the key store or the
     * file didn't answer (a hiccup at start must not cost the offline copy). Tries the TEE key, then the old StrongBox
     * one; a code the old key opened is locked again with the new one, and only then is the old key deleted.
     */
    fun read(ctx: Context): Read {
        val f = file(ctx)
        val b = try {
            if (!f.isFile) return Read.None
            f.readBytes()
        } catch (e: Exception) {
            return Read.Later
        }
        // the IV and GCM's tag alone are 28 bytes: anything shorter was never a code
        if (b.size < 28) return Read.None
        val found = try {
            listOf(ALIAS, STRONGBOX).map { it to keys.get(it) }
        } catch (e: Exception) {
            // an unrecoverable key counts here too: on Android 12+ that's also how a busy key store answers
            return Read.Later
        }
        for ((alias, key) in found) {
            if (key == null) continue
            val code = try {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b, 0, 12))
                    Code.normalize(doFinal(b, 12, b.size - 12).toString(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                // this key can't open it (not the one that locked it, or invalidated): the next one might; anything else
                // is the key store stumbling
                if (e is AEADBadTagException || e is KeyPermanentlyInvalidatedException) continue
                return Read.Later
            }
            if (code.isEmpty()) return Read.None
            if (alias == STRONGBOX) {
                // locked again with the TEE key first; if that fails the old key stays and the next read tries again
                if (runCatching { save(ctx, code) }.isSuccess) runCatching { keys.delete(STRONGBOX) }
            } else if (found.any { it.first == STRONGBOX && it.second != null }) {
                // a move that stopped after the new lock: only the old key is left over
                runCatching { keys.delete(STRONGBOX) }
            }
            return Read.Got(code)
        }
        return Read.None
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        for (a in listOf(ALIAS, STRONGBOX)) runCatching { keys.delete(a) }
    }
}
