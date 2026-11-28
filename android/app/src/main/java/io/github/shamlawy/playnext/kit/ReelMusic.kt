package io.github.shamlawy.playnext.kit

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import io.github.shamlawy.playnext.kit.Synth.Filter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * The opening reel's soundtrack (`pnReel.audio`, index.html 3258): 120 BPM, 30 beats over the reel's 15 s. A kick on
 * every beat until 14.5, a hat between them (double time in the last full bar), a filtered saw bass A F C G a bar each,
 * a chord hit at each bar's start, a riser from 10 to 12, a bigger hit on the title at 14 and a short drop as it folds
 * back into the pixel. Made once with the app's synth (the web's Web Audio graph, node for node), then kept as a WAV in
 * the cache ([VERSION]), so a later launch only reads the file. Not an [Sfx]: it's 15 s long and plays with the
 * picture, through a [MediaPlayer] at .75, fading out over 250 ms when the reel is skipped.
 */
object ReelMusic {
    /** Bump when the recipe changes: the cached file is made again. */
    private const val VERSION = 1
    const val SECONDS = 15.0
    private const val RATE = Recipes.RATE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var making: Deferred<File?>? = null

    /** The file, made in the background the first time it's asked for (null when it couldn't be made). */
    @Synchronized
    fun warm(ctx: Context): Deferred<File?> = making ?: scope.async {
        runCatching {
            val f = File(ctx.cacheDir, "reel-$VERSION.wav")
            ctx.cacheDir.listFiles { x -> x.name.startsWith("reel-") && x != f }?.forEach { it.delete() }
            if (!f.exists()) {
                val tmp = File(ctx.cacheDir, "reel-$VERSION.tmp")
                tmp.writeBytes(Wav.of(render(), RATE))
                tmp.renameTo(f)
            }
            f
        }.getOrNull()
    }.also { making = it }

    /** The music playing (with [play]'s own fade). */
    class Playing internal constructor(private val mp: MediaPlayer) {
        private var done = false

        /** The 250 ms fade the web's skip does, then it stops. */
        fun fadeOut() {
            if (done) return
            done = true
            scope.launch(Dispatchers.Main) {
                runCatching {
                    for (i in 9 downTo 0) {
                        val v = .75f * i / 10
                        mp.setVolume(v, v)
                        delay(25)
                    }
                }
                runCatching { mp.stop() }
                runCatching { mp.release() }
            }
        }

        /** Played through (or the screen went): let it go. */
        fun release() {
            if (done) return
            done = true
            runCatching { mp.release() }
        }
    }

    /** Starts [f] at .75 now; null when the phone can't play it. */
    fun play(f: File): Playing? = runCatching {
        val mp = MediaPlayer()
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        mp.setDataSource(f.path)
        mp.prepare()
        mp.setVolume(.75f, .75f)
        mp.start()
        Playing(mp)
    }.getOrNull()

    // ---- the recipe ----

    /** The 3 s of white noise every part reads from (the web's `s = s * 16807 % 2147483647`, from 7). */
    private fun noise(): FloatArray {
        val d = FloatArray(RATE * 3)
        var s = 7L
        for (i in d.indices) {
            s = s * 16807 % 2147483647
            d[i] = (s / 1073741823.5 - 1).toFloat()
        }
        return d
    }

    /** `env`: from 0 at [t] up to [peak] in [a], then away to .0001 over [dec]. */
    private fun env(t: Double, a: Double, peak: Double, dec: Double) = Param(1.0).set(0.0, t).lin(peak, t + a).exp(.0001, t + a + dec)

    /** One sound made on its own short track, added into [into] at [at] seconds. */
    private fun part(into: FloatArray, at: Double, seconds: Double, make: (Synth, FloatArray) -> Unit) {
        val syn = Synth(RATE, seconds)
        val x = syn.track()
        make(syn, x)
        val from = (at * RATE).toInt()
        for (i in x.indices) {
            val j = from + i
            if (j >= into.size) break
            into[j] += x[i]
        }
    }

    /** `n.start(t, offset, dur)`: the noise from [offset] for [dur] seconds. */
    private fun slice(n: FloatArray, offset: Double, dur: Double): FloatArray {
        val a = (offset * RATE).toInt().coerceIn(0, n.size)
        return n.copyOfRange(a, (a + (dur * RATE).toInt()).coerceAtMost(n.size))
    }

    /** An oscillator through a filter, then a gain (the web's osc → biquad → gain), from 0 to [stop] on [syn]'s track. */
    private fun filtered(syn: Synth, x: FloatArray, wave: Wave, freq: Param, type: Filter, cut: Param, q: Double, gain: Param, stop: Double) {
        val raw = syn.track()
        syn.osc(raw, wave, freq, Param.of(1.0), 0.0, stop)
        syn.filter(raw, type, cut, q)
        for (i in raw.indices) x[i] += (raw[i] * gain.at(i.toDouble() / RATE)).toFloat()
    }

    /** The 15 s, mono, -1 to 1. */
    fun render(seed: Int = 7): FloatArray {
        val out = FloatArray((RATE * SECONDS).toInt())
        val n = noise()
        val rnd = Random(seed)
        fun kick(t: Double) {
            part(out, t, .5) { syn, x ->
                syn.osc(x, Wave.Sine, Param(140.0).set(140.0, 0.0).exp(42.0, .12), env(0.0, .002, 1.0, .38), 0.0, .5)
                syn.noise(x, slice(n, .3, .05), Filter.Highpass, Param.of(2500.0), 1.0, env(0.0, .001, .18, .02), 0.0, .05)
            }
        }
        fun hat(t: Double, v: Double) {
            val off = rnd.nextDouble() * 2
            part(out, t, .12) { syn, x -> syn.noise(x, slice(n, off, .1), Filter.Highpass, Param.of(8000.0), 1.0, env(0.0, .001, v, .05), 0.0, .1) }
        }
        fun bass(t: Double, hz: Double, d: Double) {
            part(out, t, d + .1) { syn, x ->
                filtered(syn, x, Wave.Saw, Param.of(hz), Filter.Lowpass, Param(900.0).set(900.0, 0.0).exp(140.0, d), 1.0, env(.02, .01, .22, d), d + .1)
            }
        }
        fun hit(t: Double, root: Double) {
            part(out, t, .5) { syn, x ->
                syn.noise(x, slice(n, 1.0, .4), Filter.Bandpass, Param.of(1800.0), .7, env(0.0, .001, .55, .28), 0.0, .4)
                listOf(1.0, 1.5, 2.0, 2.52).forEachIndexed { i, m ->
                    filtered(syn, x, if (i % 2 == 1) Wave.Square else Wave.Saw, Param.of(root * m * 2), Filter.Lowpass, Param(4000.0).set(4000.0, 0.0).exp(300.0, .3), 1.0, env(0.0, .003, .07, .34), .5)
                }
            }
        }
        // A F C G, a bar each
        val roots = doubleArrayOf(55.0, 43.65, 65.41, 49.0)
        for (b in 0 until 30) {
            val t = b * .5
            val root = roots[(b / 4) % 4]
            if (t < 14.5) kick(t)
            hat(t + .25, if (b % 4 == 3) .16 else .1)
            // double time in the last full bar
            if (b in 24 until 28) {
                hat(t + .125, .07)
                hat(t + .375, .07)
            }
            if (t < 14.5) bass(t, root, .42)
            if (b % 4 == 0) hit(t, root)
        }
        // the riser into the last full bar (10 → 12)
        part(out, 10.0, 2.05) { syn, x ->
            syn.noise(x, slice(n, 0.0, 2.05), Filter.Bandpass, Param(300.0).set(300.0, 0.0).exp(7000.0, 2.0), 2.0, Param(1.0).set(0.0, 0.0).lin(.32, 1.96).lin(0.0, 2.01), 0.0, 2.05)
            syn.osc(x, Wave.Saw, Param(110.0).set(110.0, 0.0).exp(880.0, 2.0), Param(1.0).set(0.0, 0.0).lin(.06, 1.96).lin(0.0, 2.01), 0.0, 2.05)
        }
        // the end: a bigger hit on the title, a short drop as it folds back into the pixel
        hit(14.0, 110.0)
        kick(14.0)
        part(out, 14.5, .5) { syn, x -> syn.osc(x, Wave.Sine, Param(600.0).set(600.0, 0.0).exp(60.0, .35), env(0.0, .01, .25, .35), 0.0, .5) }
        master(out)
        return out
    }

    /**
     * The web's master: a gain of .8 into a DynamicsCompressorNode (threshold -14 dB, ratio 4, its default 30 dB knee,
     * 3 ms attack, 250 ms release and its own make-up gain), then kept inside -1 to 1.
     */
    private fun master(x: FloatArray) {
        val th = -14.0
        val knee = 30.0
        val ratio = 4.0
        // the static curve (dB in → dB out): straight to the threshold, a soft knee, then 1/ratio
        fun curve(db: Double): Double = when {
            db <= th -> db
            db >= th + knee -> th + knee / 2 + (db - th - knee) / ratio + knee / 2 / ratio
            else -> db + (1 / ratio - 1) * (db - th).pow(2) / (2 * knee)
        }
        val makeup = 10.0.pow(-curve(0.0) / 20).pow(.6)
        val att = exp(-1.0 / (.003 * RATE))
        val rel = exp(-1.0 / (.25 * RATE))
        var envDb = -120.0
        for (i in x.indices) {
            val v = x[i] * .8
            val inDb = 20 * log10(max(abs(v), 1e-6))
            envDb = if (inDb > envDb) att * envDb + (1 - att) * inDb else rel * envDb + (1 - rel) * inDb
            val gainDb = curve(envDb) - envDb
            x[i] = (v * 10.0.pow(gainDb / 20) * makeup).toFloat()
        }
        val peak = x.maxOfOrNull { abs(it) } ?: 0f
        if (peak > .98f) for (i in x.indices) x[i] = x[i] / peak * .98f
    }
}
