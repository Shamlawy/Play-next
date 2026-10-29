package io.github.shamlawy.playnext.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A release the phone could install. */
@Serializable
data class Release(val code: Int, val apkUrl: String, val size: Long, val sha256: String?, val shaUrl: String?)

/** Reading GitHub's release list (or the fixed-name text asset when the API is busy). Pure, so JVM tests can check it. */
object ReleaseFeed {
    const val REPO_URL = "https://github.com/Shamlawy/Play-next"
    private val TAG = Regex("^android-v(\\d+)$")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private data class Asset(val name: String, val browser_download_url: String, val size: Long = 0, val digest: String? = null)

    @Serializable
    private data class Rel(val tag_name: String, val draft: Boolean = false, val prerelease: Boolean = false, val assets: List<Asset> = emptyList())

    fun apkName(code: Int) = "play-next-android-v$code.apk"

    /** The newest published android-vN above [current] that carries its APK, or null. */
    fun pick(body: String, current: Int): Release? = json.decodeFromString<List<Rel>>(body)
        .asSequence()
        .filter { !it.draft && !it.prerelease }
        .mapNotNull { r -> TAG.find(r.tag_name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to r } }
        .filter { (code, _) -> code > current }
        .sortedByDescending { it.first }
        .firstNotNullOfOrNull { (code, r) ->
            val apk = r.assets.firstOrNull { it.name == apkName(code) } ?: return@firstNotNullOfOrNull null
            val sha = r.assets.firstOrNull { it.name == apkName(code) + ".sha256" }
            Release(code, apk.browser_download_url, apk.size, apk.digest?.removePrefix("sha256:")?.takeIf { it.length == 64 }, sha?.browser_download_url)
        }

    /** The pick as kept for the next 304 ("" = nothing newer): a few hundred bytes, not the 95 KB list. */
    fun keep(r: Release?): String = if (r == null) "" else json.encodeToString(Release.serializer(), r)

    /** The kept pick, while it's still newer than [current] (the app may have updated to it since). */
    fun kept(s: String?, current: Int): Release? =
        s?.takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString(Release.serializer(), it) }.getOrNull() }?.takeIf { it.code > current }

    /** `android-latest.txt`: "<code> <sha256> <size> android-v<code>". */
    fun pickLatestTxt(text: String, current: Int): Release? {
        val p = text.trim().split(Regex("\\s+"))
        if (p.size < 3) return null
        val code = p[0].toIntOrNull() ?: return null
        val sha = p[1].lowercase().takeIf { Regex("[0-9a-f]{64}").matches(it) } ?: return null
        if (code <= current) return null
        return Release(code, "$REPO_URL/releases/download/android-v$code/${apkName(code)}", p[2].toLongOrNull() ?: 0, sha, null)
    }

    /** The hash out of a `.sha256` file ("<hex>  <name>"). */
    fun shaOf(text: String): String? = Regex("\\b[0-9a-fA-F]{64}\\b").find(text)?.value?.lowercase()
}

/** android/app/src/main/assets/whatsnew.txt: blocks of key=value lines (v, go, at, say, tryIt), # comments. */
object WhatsNew {
    data class Entry(val v: Int, val go: String, val at: String, val say: String, val tryIt: String)

    fun parse(text: String): List<Entry> = text.lines()
        .filterNot { it.trimStart().startsWith("#") }
        .joinToString("\n")
        .split(Regex("\\n\\s*\\n"))
        .mapNotNull { block ->
            val kv = block.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.take(it).trim() to l.drop(it + 1).trim() } }.toMap()
            val v = kv["v"]?.toIntOrNull() ?: return@mapNotNull null
            Entry(v, kv["go"].orEmpty(), kv["at"].orEmpty(), kv["say"].orEmpty(), kv["tryIt"].orEmpty())
        }
}
