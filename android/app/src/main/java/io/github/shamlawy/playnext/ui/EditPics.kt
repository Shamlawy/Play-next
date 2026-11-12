package io.github.shamlawy.playnext.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.shamlawy.playnext.data.Art
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Edit › Look's pictures on the phone: the colour read from a picture (`artColour`, index.html 26839: a 48x64 copy,
 * then [Art.colourFrom]) and a photo from your gallery made small enough to keep (`readFile` / `readArt`, 5067-5093).
 */
object EditPics {
    /** The swatch read from [src] (a link or an uploaded `data:` picture), null when it can't be read. */
    suspend fun colour(ctx: Context, src: String): String? {
        if (src.isEmpty()) return null
        val r = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(picUrl(src)).size(96, 128).allowHardware(false).build())
        val img = (r as? SuccessResult)?.image ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                // stretched to 48x64 like the web's canvas, so a wide picture votes the same way
                val b = Bitmap.createScaledBitmap(img.toBitmap(), 48, 64, true)
                val px = IntArray(48 * 64)
                b.getPixels(px, 0, 48, 0, 0, 48, 64)
                Art.colourFrom(px, 48, 64)
            }.getOrNull()
        }
    }

    /** An upload ready for the draft: the picture as a `data:` address, its width, its weight, its colour (covers only). */
    class Up(val data: String, val w: Int, val kb: Int, val colour: String?)

    /** Why an upload failed, in the web's words (`BIG_IMG`): the step it stopped at. */
    fun bigImg(note: String) = "Couldn't use that image — every route failed, last at \"$note\". Tell Claude that line and it'll know exactly where it broke."

    /**
     * A photo from the picker at most [maxW] wide, as WebP ([q] quality; WebP keeps a cut-out's see-through parts), with
     * its colour read when [colour]. A failure names its step.
     */
    suspend fun upload(ctx: Context, uri: Uri, maxW: Int, q: Int, colour: Boolean): Result<Up> = withContext(Dispatchers.IO) {
        val bmp = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                if (w > maxW) dec.setTargetSize(maxW, (info.size.height * maxW / w.toDouble()).roundToInt().coerceAtLeast(1))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException(bigImg("decode")))
        }
        try {
            val out = ByteArrayOutputStream()
            if (!bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, out)) return@withContext Result.failure(IllegalStateException(bigImg("encode:decode")))
            val data = "data:image/webp;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val col = if (colour) runCatching {
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                Art.colourFrom(px, bmp.width, bmp.height)
            }.getOrNull() else null
            Result.success(Up(data, bmp.width, Math.round(data.length * .75 / 1024).toInt(), col))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(bigImg("encode:decode")))
        }
    }

    /** How wide an uploaded cover is kept (`coverW`: Settings › soft 320, sharp 720, max 1100). */
    fun coverW(q: String): Int = when (q) { "soft" -> 320; "max" -> 1100; else -> 720 }
}
