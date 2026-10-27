package io.github.shamlawy.playnext.kit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The browser's oscillator shapes. */
enum class Wave { Sine, Triangle, Square, Saw }

/**
 * An AudioParam's timeline, with the three calls the web app's recipes make (`setValueAtTime`,
 * `linearRampToValueAtTime`, `exponentialRampToValueAtTime`): a ramp runs from the event before it, and after the
 * last event the value holds. [start] is the value before any event (the param's default).
 */
class Param(private val start: Double) {
    private val kinds = ArrayList<Int>()
    private val times = ArrayList<Double>()
    private val values = ArrayList<Double>()

    fun set(v: Double, at: Double) = add(0, v, at)
    fun lin(v: Double, at: Double) = add(1, v, at)
    fun exp(v: Double, at: Double) = add(2, v, at)

    private fun add(kind: Int, v: Double, at: Double): Param {
        kinds += kind
        times += at
        values += v
        return this
    }

    fun at(t: Double): Double {
        var v = start
        var from = 0.0
        for (i in times.indices) {
            if (times[i] <= t) {
                v = values[i]
                from = times[i]
                continue
            }
            val k = (t - from) / (times[i] - from)
            return when (kinds[i]) {
                1 -> v + (values[i] - v) * k
                2 -> v * (values[i] / v).pow(k)
                else -> v
            }
        }
        return v
    }

    companion object {
        fun of(v: Double) = Param(v)
    }
}

/**
 * Plays the web app's Web Audio recipes into plain samples ahead of time (index.html `blip`, `sfx`, the FX pack,
 * `pnBeep`): oscillators with exponential pitch slides, gains that rise in a straight line and fall away
 * exponentially, white noise through the browser's biquad filters. Waves are band-limited like the browser's (each
 * harmonic below half the sample rate, the peak scaled to 1). Pure maths, so JVM tests check the recipes.
 */
class Synth(val rate: Int, seconds: Double) {
    val size = max(1, (rate * seconds).roundToInt())

    fun track() = FloatArray(size)

    private fun frame(t: Double) = (t * rate).roundToInt().coerceIn(0, size)

    /** An oscillator from [start] to [stop] seconds, pitch from [freq], through [gain], added into [into]. */
    fun osc(into: FloatArray, wave: Wave, freq: Param, gain: Param, start: Double, stop: Double) {
        var phase = 0.0
        for (i in frame(start) until frame(stop)) {
            val t = i.toDouble() / rate
            val f = freq.at(t)
            into[i] += (shape(wave, phase, f) * gain.at(t)).toFloat()
            phase += f / rate
            phase -= floor(phase)
        }
    }

    /** One cycle of [wave] at [phase] (0 to 1), with only the harmonics below half the sample rate. */
    private fun shape(wave: Wave, phase: Double, f: Double): Double {
        val x = 2 * PI * phase
        if (wave == Wave.Sine) return sin(x)
        val top = min(256, (rate / 2.0 / max(f, 1.0)).toInt())
        // sin(k x) for k = 1, 2, 3… by the Chebyshev step: sin((k+1)x) = 2 cos x sin(kx) - sin((k-1)x)
        val c2 = 2 * cos(x)
        var prev = 0.0
        var cur = sin(x)
        var sum = 0.0
        for (k in 1..top) {
            sum += when (wave) {
                Wave.Square -> if (k % 2 == 1) cur / k else 0.0
                Wave.Saw -> if (k % 2 == 1) cur / k else -cur / k
                Wave.Triangle -> if (k % 2 == 1) (if (k % 4 == 1) cur else -cur) / (k.toDouble() * k) else 0.0
                Wave.Sine -> 0.0
            }
            val next = c2 * cur - prev
            prev = cur
            cur = next
        }
        return when (wave) {
            Wave.Square -> sum * 4 / PI * GIBBS
            Wave.Saw -> sum * 2 / PI * GIBBS
            else -> sum * 8 / (PI * PI)
        }
    }

    /**
     * White noise from [noise] (played from its start, not looped: a 1 s buffer runs out after 1 s, as on the web)
     * from [start] to [stop], through a biquad of [type] whose cutoff follows [freq], then [gain], added into [into].
     */
    fun noise(into: FloatArray, noise: FloatArray, type: Filter, freq: Param, q: Double, gain: Param, start: Double, stop: Double) {
        val a = frame(start)
        val b = min(frame(stop), a + noise.size)
        val x = FloatArray(size)
        for (i in a until b) x[i] = noise[i - a]
        filter(x, type, freq, q, a)
        for (i in a until size) into[i] += (x[i] * gain.at(i.toDouble() / rate)).toFloat()
    }

    /** The browser's BiquadFilterNode over [x] from [from] on, in place; Q is in dB for lowpass and highpass. */
    fun filter(x: FloatArray, type: Filter, freq: Param, q: Double, from: Int = 0) {
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in from until x.size) {
            val f = freq.at(i.toDouble() / rate).coerceIn(1.0, rate / 2.0 - 1)
            val w = 2 * PI * f / rate
            val cw = cos(w)
            val sw = sin(w)
            val alpha = if (type == Filter.Bandpass) sw / (2 * q) else sw / (2 * 10.0.pow(q / 20))
            val b0: Double
            val b1: Double
            val b2: Double
            when (type) {
                Filter.Lowpass -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = b0 }
                Filter.Highpass -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = b0 }
                Filter.Bandpass -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
            }
            val a0 = 1 + alpha
            val a1 = -2 * cw
            val a2 = 1 - alpha
            val x0 = x[i].toDouble()
            val y0 = (b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
            x[i] = y0.toFloat()
        }
    }

    enum class Filter { Lowpass, Highpass, Bandpass }

    companion object {
        /** The browser scales its square and sawtooth so the full-band wave peaks at 1 (their sums overshoot to ~1.179). */
        private const val GIBBS = 1 / 1.1789797

        /** Adds [part] into [into] times [gain]. */
        fun mix(into: FloatArray, part: FloatArray, gain: Double) {
            for (i in into.indices) into[i] += (part[i] * gain).toFloat()
        }
    }
}
