package io.github.shamlawy.playnext.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The restore code and the sealed copy, against answers the web app's own functions gave
 * (`node android/parity/0-map/backup-vector.mjs`: index.html's cbNorm / cbKeys / cbSeal with a made-up code and IV 01..0c).
 * tools/golden.mjs checks the same with fresh codes on every android-check run.
 */
class VaultTest {
    private val code = "PN-AB2C-DEF3-GH4J-KLM5-NPQ6"
    private val text = "{\"rk\":\"\"}\n{\"games\":[{\"id\":\"g0\",\"title\":\"Test Game\",\"date\":\"2026-01-15\",\"state\":\"backlog\"}],\"played\":[],\"duels\":[],\"lastV\":\"327\"}"
    private val sealedZ1 = "01010102030405060708090a0b0cd697cba6838ca8352968cb5f161d55a8d49e74d4e926e6dfbd0dcd91f42c07f5a86b025d71a6ee18cdfe2918375501db80789cf2d7216bf138317437f3877458c4960a7d4117515916f6151a5cc66934944bf32f462147679964c7d3523ff69d34724cb2cf7bea9a0aa98fa422aa088d4820f5e20c2095e5830c15b39bcc0913a07e4d9a3a6322e6134fc44cb5e7b2"
    private val sealedZ0 = "01000102030405060708090a0b0cb23eb1cda1b68a17546195b53076fafdb7fc9f32359430f53eba4766cf762aa68fb86efc04ce872eaab57e88f930bd8bbbbf0dc013ad35dd4c26d2b4f1f240dc33c694295145a8199e09d8e41a6eaedd01d444b169793c34f0e219dfad76a8f73c64d23ae813d47ea7cdd5529a4e1e137b08d97021a8e01869f2e200a9fb2b1ec32ec7459362c17d6a1f19785821050b"

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun normalizesLikeCbNorm() {
        assertEquals("AB2CDEF3GH4JKLM5NPQ6", Code.normalize(code))
        assertEquals("with spaces, lower case, no PN", "AB2CDEF3GH4JKLM5NPQ6", Code.normalize("ab2c def3-gh4j klm5 npq6"))
        assertEquals("PN + 20 without dashes", "AB2CDEF3GH4JKLM5NPQ6", Code.normalize("pnab2cdef3gh4jklm5npq6"))
        assertEquals("21 symbols", "", Code.normalize("AB2CDEF3GH4JKLM5NPQ6A"))
        assertEquals("a 0 isn't in the alphabet", "", Code.normalize("AB2CDEF3GH4JKLM5NPQ0"))
        assertEquals("an O isn't either", "", Code.normalize("AB2CDEF3GH4JKLM5NPQO"))
        assertEquals("", Code.normalize(null))
        assertEquals(code, Code.format("ab2cdef3gh4jklm5npq6"))
    }

    @Test
    fun derivesTheSameKeysAsTheWebApp() {
        val k = Code.keys(code)
        assertEquals("a2ef69c79f794293faa4577b", k.id)
        assertEquals("85dd99e5170ce73ed61716fe31a4db70bf63da27c1796b35504c1232d1d6b850", k.tok)
        assertEquals("7c13739db5b2760dc20f150c1b314ba661e20afbe1a4a973ff69555e79113411", Code.hex(k.key))
    }

    @Test
    fun opensTheWebAppsSealedCopies() {
        val key = Code.keys(code).key
        assertEquals("gzipped (how the web app seals)", text, Seal.open(bytes(sealedZ1), key))
        assertEquals("plain", text, Seal.open(bytes(sealedZ0), key))
        val parts = Seal.split(text)
        assertEquals("", parts.rawgKey)
        assertTrue(parts.state.startsWith("{\"games\""))
    }

    @Test
    fun sealsTheWayTheWebAppDoes() {
        val key = Code.keys(code).key
        val iv = ByteArray(12) { (it + 1).toByte() }
        // AES-GCM with the same key, IV and data is the same bytes: the plain copy must match the web app's exactly
        assertArrayEquals(bytes(sealedZ0), Seal.seal(text, key, iv, gzip = false))
        assertEquals(text, Seal.open(Seal.seal(text, key, iv), key))
    }

    @Test
    fun refusesWhatDoesntOpen() {
        val key = Code.keys(code).key
        fun why(b: ByteArray, k: ByteArray = key) = try {
            Seal.open(b, k); fail("opened"); ""
        } catch (e: SealError) {
            e.why
        }
        assertEquals("A newer kind of backup: update the app first.", why(bytes(sealedZ1).also { it[0] = 2 }))
        assertEquals("That copy didn't open with this code.", why(bytes(sealedZ1), Code.keys("PN-AB2C-DEF3-GH4J-KLM5-NPQ7").key))
        assertEquals("a changed header byte", "That copy didn't open with this code.", why(bytes(sealedZ1).also { it[1] = 0 }))
        assertEquals("cut short", "That copy didn't open with this code.", why(bytes(sealedZ1).copyOf(40)))
    }

    @Test
    fun readsTheRawgKeyFromTheFirstLine() {
        val p = Seal.split("{\"rk\":\"abc\\\"123\"}\n{\"games\":[]}")
        assertEquals("abc\"123", p.rawgKey)
        assertEquals("{\"games\":[]}", p.state)
    }
}
