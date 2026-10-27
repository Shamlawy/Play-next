package io.github.shamlawy.playnext.kit

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's sounds, made from the web app's recipes ([Recipes]): each one rendered once into a small WAV in the cache
 * (in the background, the first time Sounds is on or the try card opens) and played through SoundPool, so a tap costs
 * nothing on the main thread. Sounds (`S.sound`, off unless you switched it on) gates every one, the duel, CLEAR! and
 * Out now ones too (the owner, Oct 26, answer 3); Nexi's chime follows his own switch (`S.pnVoice`). They play on the
 * media volume, as the web app's do.
 */
object Sounds {
    /** Settings › Sounds (`S.sound`). */
    @Volatile var on = false

    /** Nexi's blips (`S.pnVoice !== "mute"`, and the older `S.pnMute`). */
    @Volatile var nexi = true

    /** Bump when a recipe changes: the cached WAVs are made again. */
    private const val VERSION = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dir: File? = null
    private var pool: SoundPool? = null
    private val ids = ConcurrentHashMap<Sfx, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()

    /** Every sound made and loaded (the try card says "getting them ready" until then). */
    val ready = MutableStateFlow(false)

    fun init(ctx: Context) {
        dir = File(ctx.cacheDir, "sfx-$VERSION")
    }

    /** A sound the app makes as you use it: only with Sounds on. */
    fun play(s: Sfx) {
        if (on) now(s)
    }

    /** One of Nexi's: only with his voice on. */
    fun nexi(s: Sfx) {
        if (nexi) now(s)
    }

    /** Plays [s] whatever the switches say (the try card); false when it isn't made yet (that starts the making). */
    fun now(s: Sfx): Boolean {
        val id = ids[s]
        val p = pool
        if (id == null || id !in loaded || p == null) {
            warm()
            return false
        }
        p.play(id, 1f, 1f, 1, 0, 1f)
        return true
    }

    /** Makes every sound that isn't in the cache yet and loads them all; again and again is fine. */
    @Synchronized
    fun warm() {
        val d = dir ?: return
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        p.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded += id
            if (loaded.size == Sfx.entries.size) ready.value = true
        }
        pool = p
        scope.launch {
            runCatching {
                d.parentFile?.listFiles { f -> f.name.startsWith("sfx-") && f != d }?.forEach { it.deleteRecursively() }
                d.mkdirs()
                for (s in Sfx.entries) {
                    val f = File(d, s.name + ".wav")
                    if (!f.exists()) {
                        val tmp = File(d, s.name + ".tmp")
                        tmp.writeBytes(Wav.of(Recipes.render(s), Recipes.RATE))
                        tmp.renameTo(f)
                    }
                    ids[s] = p.load(f.path, 1)
                }
            }
        }
    }
}

/** 16-bit mono PCM in a WAV file, the shape SoundPool reads. */
object Wav {
    fun of(samples: FloatArray, rate: Int): ByteArray {
        val data = samples.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (x in samples) b.putShort((x.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        return b.array()
    }
}
