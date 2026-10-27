package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The restore code, exactly as the web app reads it (index.html v211, `cbNorm` / `cbFmt` / `cbKeys`;
 * android/parity/0-map/1-data.md §1.2-1.3). The code is 100 random bits, so plain SHA-256 hashes make the vault id,
 * the token and the AES key; there is no salt or KDF to match.
 */
object Code {
    /** `cbNorm`: upper case, letters and digits only, a leading PN dropped from 22, then exactly 20 alphabet symbols. */
    fun normalize(input: String?): String {
        var t = (input ?: "").uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }
        if (t.length == 22 && t.startsWith("PN")) t = t.drop(2)
        return if (t.length == 20 && t.all { it in Web.CB_ALPHA }) t else ""
    }

    /** `cbFmt`: PN-XXXX-XXXX-XXXX-XXXX-XXXX ("" for a code that isn't one). */
    fun format(input: String?): String = normalize(input).takeIf { it.isNotEmpty() }?.chunked(4)?.joinToString("-", prefix = "PN-") ?: ""

    /** What a code opens: the vault id (24 hex), the helper token (64 hex) and the AES-256 key (never sent). */
    class Keys(val id: String, val tok: String, val key: ByteArray)

    fun keys(code: String): Keys {
        val c = normalize(code)
        require(c.isNotEmpty()) { "not a code" }
        return Keys(id = hex(sha("pn-vault-id:$c")).take(24), tok = hex(sha("pn-vault-tok:$c")), key = sha("pn-vault-key:$c"))
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

/** A sealed copy that didn't open: [why] is the line the person sees (the web app's words). */
class SealError(val why: String) : Exception(why)

/**
 * The sealed copy (`cbSeal` / `cbOpen`): [1][z][12-byte IV][AES-GCM ciphertext + 16-byte tag], the two first bytes as
 * extra authenticated data, gzip inside when z = 1.
 */
object Seal {
    fun open(b: ByteArray, key: ByteArray): String {
        if (b.isEmpty() || b[0] != 1.toByte()) throw SealError("A newer kind of backup: update the app first.")
        if (b.size < 14 + 16) throw SealError("That copy didn't open with this code.")
        val pt = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b, 2, 12))
                updateAAD(byteArrayOf(1, b[1]))
                doFinal(b, 14, b.size - 14)
            }
        } catch (e: AEADBadTagException) {
            throw SealError("That copy didn't open with this code.")
        }
        val text = if (b[1].toInt() == 0) pt else try {
            GZIPInputStream(pt.inputStream()).use { it.readBytes() }
        } catch (e: java.io.IOException) {
            throw SealError("That copy is damaged.")
        }
        return text.toString(Charsets.UTF_8)
    }

    /** The web app's seal (z = 1, a fresh IV): for tests now, and for the app's own backups after switch day. */
    fun seal(text: String, key: ByteArray, iv: ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }, gzip: Boolean = true): ByteArray {
        val raw = text.toByteArray(Charsets.UTF_8)
        val data = if (gzip) ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(raw) } }.toByteArray() else raw
        val z: Byte = if (gzip) 1 else 0
        val ct = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(byteArrayOf(1, z))
            doFinal(data)
        }
        return byteArrayOf(1, z) + iv + ct
    }

    /** `cbText`'s two parts: the header line `{"rk": RAWG key}` and the saved state exactly as the web phone stores it. */
    class Parts(val rawgKey: String, val state: String)

    fun split(text: String): Parts {
        val cut = text.indexOf('\n')
        if (cut < 0) return Parts("", text)
        val head = text.substring(0, cut)
        val rk = runCatching { ((Json.parseToJsonElement(head) as? JsonObject)?.get("rk") as? JsonPrimitive)?.content }.getOrNull().orEmpty()
        return Parts(rk, text.substring(cut + 1))
    }
}
