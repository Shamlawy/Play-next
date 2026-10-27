package io.github.shamlawy.playnext.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.shamlawy.playnext.crash.swapIn
import java.io.File
import java.security.KeyStore
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
    private const val ALIAS = "pn-restore-code"
    private fun file(ctx: Context) = File(ctx.filesDir, "code.bin")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun make(strong: Boolean): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setIsStrongBoxBacked(strong)
                    .build(),
            )
            generateKey()
        }
        // no StrongBox, or a key store that refuses it (a ProviderException on some builds): the phone's ordinary secure key
        return try { make(true) } catch (e: Exception) { make(false) }
    }

    fun save(ctx: Context, code: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(Code.normalize(code).toByteArray(Charsets.UTF_8))
        val tmp = File(ctx.filesDir, "code.bin.new")
        tmp.writeBytes(out)
        swapIn(tmp, file(ctx))
    }

    /** The code, or null when there's none or it can't be opened any more. */
    fun read(ctx: Context): String? = runCatching {
        val b = file(ctx).takeIf { it.isFile }?.readBytes() ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)) }
        Code.normalize(c.doFinal(b, 12, b.size - 12).toString(Charsets.UTF_8)).ifEmpty { null }
    }.getOrNull()

    fun clear(ctx: Context) {
        file(ctx).delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }
}
