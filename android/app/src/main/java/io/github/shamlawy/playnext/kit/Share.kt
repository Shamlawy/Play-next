package io.github.shamlawy.playnext.kit

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLDecoder

/**
 * Sharing out, the web app's `navigator.share` (android/parity/0-map/3-kit.md §4c): words, or a picture a screen drew
 * (the calling card, a wrapped card, your top 5), through Android's share sheet. Backing out of the sheet is fine, as
 * the web ignores its AbortError. The screens that share come with their phases.
 */
object Share {
    fun text(ctx: Context, text: String, title: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (title != null) send.putExtra(Intent.EXTRA_TITLE, title)
        open(ctx, send, title)
    }

    /** [bitmap] as a PNG named [name] in the cache, handed out through the app's FileProvider. */
    fun image(ctx: Context, bitmap: Bitmap, name: String, title: String? = null) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the newest one is ever waiting to be picked up
        val f = File(dir, name.replace(Regex("[^\\w.-]"), "-").ifEmpty { "play-next.png" })
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        if (title != null) send.putExtra(Intent.EXTRA_TITLE, title)
        open(ctx, send, title)
    }

    private fun open(ctx: Context, send: Intent, title: String?) {
        val chooser = Intent.createChooser(send, title)
        if (ctx !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(chooser) }
    }
}

/** A game named in something shared into the app: its name, and its Steam id when the link was a Steam page. */
data class Shared(val name: String, val steam: Int = 0)

/**
 * `shName` (index.html 22511): the game in a shared Steam / PlayStation / Nintendo / Xbox / YouTube / RAWG link or a plain
 * name. A Steam or RAWG link names it by its address; otherwise the words, without links, store and trailer tails and
 * hashtags.
 */
object SharedName {
    private val steam = Regex("store\\.steampowered\\.com/app/(\\d+)(?:/([^/?#\\s]+))?", RegexOption.IGNORE_CASE)
    private val rawg = Regex("rawg\\.io/games/([a-z0-9-]+)", RegexOption.IGNORE_CASE)
    private val eshop = Regex("nintendo\\.com/[^\\s]*/(?:products?|store/products)/([a-z0-9-]+?)(?:-switch|-\\d{3,})?/?(?:[?#\\s]|$)", RegexOption.IGNORE_CASE)
    private val link = Regex("https?://\\S+")
    private val tail = Regex("\\s*[|–—-]\\s*(PS[45] Games|PlayStation.*|Xbox.*|Nintendo.*|Steam.*|YouTube|Official.*|Launch Trailer.*|Trailer.*|Gameplay.*)$", RegexOption.IGNORE_CASE)
    private val trailer = Regex("\\b(official|launch|announce(ment)?|reveal|story|gameplay|game ?play)?\\s*trailer\\b.*$", RegexOption.IGNORE_CASE)
    private val onSteam = Regex("\\bon steam\\b", RegexOption.IGNORE_CASE)
    private val tags = Regex("[#@][\\w-]+")

    fun of(title: String?, text: String?, url: String? = null): Shared? {
        val all = listOfNotNull(title, text, url).filter { it.isNotEmpty() }.joinToString(" ")
        val st = steam.find(all)
        if (st != null && st.groupValues[2].isNotEmpty()) {
            // decodeURIComponent: a "+" stays a "+" (URLDecoder would make it a space)
            val slug = runCatching { URLDecoder.decode(st.groupValues[2].replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(st.groupValues[2])
            return Shared(slug.replace('_', ' ').trim(), st.groupValues[1].toIntOrNull() ?: 0)
        }
        rawg.find(all)?.let { return Shared(it.groupValues[1].replace('-', ' ')) }
        eshop.find(all)?.let { return Shared(it.groupValues[1].replace('-', ' ')) }
        var t = (title?.takeIf { it.isNotEmpty() } ?: text ?: "").replace(link, " ")
        t = t.replace(tail, "").replace(trailer, "").replace(onSteam, "").replace(tags, "").replace(Regex("\\s+"), " ").trim()
        return if (t.isEmpty()) null else Shared(t, st?.groupValues?.get(1)?.toIntOrNull() ?: 0)
    }
}
