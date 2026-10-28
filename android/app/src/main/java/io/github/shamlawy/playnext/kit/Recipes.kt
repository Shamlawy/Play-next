package io.github.shamlawy.playnext.kit

import io.github.shamlawy.playnext.kit.Synth.Filter
import kotlin.random.Random

/**
 * Every sound the app can make, named as on the web (android/parity/0-map/3-kit.md §1): the UI ticks (`beep`), the
 * effects (`sfx`), the big moments of the FX pack, and Nexi's chime. [seconds] is how long its samples run.
 */
enum class Sfx(val seconds: Double, val label: String) {
    Tap(.07, "Tap"), Open(.16, "Open"), Add(.3, "Add"), Swipe(.14, "Swipe"), Nudge(.06, "Nudge"), Back(.08, "Back"),
    Hit(.31, "Hit"), Slash(.31, "Slash"), Tick(.09, "Tick"), Slam(.53, "Slam"), Crack(.13, "Crack"), Shatter(.58, "Shatter"),
    Cheer(.57, "Cheer"), CardOpen(1.53, "Card opens"), CardTear(1.79, "Stub tears"), CardFlip(.65, "Card turns"),
    CardFlipBack(.65, "Card turns back"), CardShut(.31, "Card shuts"), CardBack(1.25, "Card's back"),
    Zing(1.02, "Duel pick"), Boom(.56, "VS"), Clang(1.0, "CLEAR!"), Fanfare(1.4, "Out now"), Day(1.62, "New day"),
    Chime(.58, "Nexi's chime"),
}

/** The recipes, sample for sample the web app's (index.html 3886 `blip`/`beep`, 11646 `sfx`, the FX pack, `pnChime`). */
object Recipes {
    const val RATE = 48000

    /** [s] as samples from -1 to 1; the random parts (noise, shatter's sparkles, a zing's pitch) come from [seed]. */
    fun render(s: Sfx, seed: Int = 7): FloatArray {
        val syn = Synth(RATE, s.seconds)
        val rnd = Random(seed)
        val out = syn.track()
        when (s) {
            Sfx.Tap -> beep(syn, out, listOf(N(520.0, .045, Wave.Triangle, .05)))
            Sfx.Open -> beep(syn, out, listOf(N(420.0, .07, Wave.Triangle, .06), N(630.0, .09, Wave.Triangle, .05)))
            Sfx.Add -> beep(syn, out, listOf(N(523.0, .08, Wave.Triangle, .06), N(659.0, .08, Wave.Triangle, .06), N(880.0, .16, Wave.Triangle, .05)))
            Sfx.Swipe -> beep(syn, out, listOf(N(300.0, .06, Wave.Sine, .04), N(200.0, .07, Wave.Sine, .03)))
            Sfx.Nudge -> beep(syn, out, listOf(N(760.0, .035, Wave.Square, .025)))
            Sfx.Back -> beep(syn, out, listOf(N(300.0, .06, Wave.Triangle, .04)))
            Sfx.Zing -> zing(syn, out, rnd)
            Sfx.Boom -> boom(syn, out, .01)
            Sfx.Clang -> clang(syn, out)
            Sfx.Fanfare -> fanfare(syn, out)
            Sfx.Day -> day(syn, out)
            Sfx.Chime -> for ((i, f) in listOf(880.0, 1175.0, 1568.0).withIndex()) {
                val t = i * .09
                syn.osc(out, Wave.Sine, Param.of(f), Param(1.0).set(.06, t).exp(.0001, t + .4), t, t + .4)
            }
            else -> Fx(syn, out, rnd).play(s)
        }
        for (i in out.indices) out[i] = out[i].coerceIn(-1f, 1f)
        return out
    }

    private class N(val f: Double, val dur: Double, val wave: Wave, val vol: Double)

    /** `beep`: each note rises in 5ms and falls away by its length; the next starts at 72% of it. Straight out, no master. */
    private fun beep(syn: Synth, out: FloatArray, notes: List<N>) {
        var t = 0.0
        for (n in notes) {
            syn.osc(out, n.wave, Param.of(n.f), Param(1.0).set(0.0, t).lin(n.vol, t + .005).exp(.0001, t + n.dur), t, t + n.dur + .02)
            t += n.dur * .72
        }
    }

    /** One second of white noise, played from its start (the web's `pnNoise`). */
    private fun noiseOf(rnd: Random, seconds: Double) = FloatArray((RATE * seconds).toInt()) { rnd.nextFloat() * 2 - 1 }

    /** `sfx(kind, x)`: everything through a .55 master, starting 5ms in. */
    private class Fx(val syn: Synth, val out: FloatArray, val rnd: Random) {
        val bus = syn.track()
        val buf = noiseOf(rnd, 1.0)
        val t = .005

        fun env(v: Double, at: Double, dur: Double, attack: Double) = Param(1.0).set(0.0, at).lin(v, at + attack).exp(.0001, at + dur)

        fun tone(w: Wave, f0: Double, f1: Double, v: Double, at: Double, dur: Double, attack: Double = .004) =
            syn.osc(bus, w, Param(f0).set(f0, at).exp(f1, at + dur), env(v, at, dur, attack), at, at + dur + .02)

        fun noise(type: Filter, f0: Double, f1: Double, q: Double, v: Double, at: Double, dur: Double, attack: Double = .004) =
            syn.noise(bus, buf, type, Param(f0).set(f0, at).exp(f1, at + dur), q, env(v, at, dur, attack), at, at + dur + .02)

        fun play(s: Sfx) {
            when (s) {
                Sfx.Hit -> { tone(Wave.Sine, 160.0, 40.0, .9, t, .28); noise(Filter.Highpass, 2000.0, 6000.0, .7, .5, t, .06) }
                Sfx.Slash -> noise(Filter.Bandpass, 600.0, 5000.0, 1.2, .9, t, .28, .06)
                Sfx.Tick -> tone(Wave.Triangle, 1500.0, 1200.0, .25, t, .06)
                Sfx.Slam -> {
                    tone(Wave.Sine, 110.0, 32.0, 1.0, t, .5)
                    noise(Filter.Lowpass, 1400.0, 200.0, .8, .7, t, .3)
                    tone(Wave.Square, 220.0, 110.0, .08, t, .12)
                }
                Sfx.Crack -> { noise(Filter.Highpass, 3500.0, 5000.0, 1.0, .9, t, .05); noise(Filter.Highpass, 3000.0, 4500.0, 1.0, .7, t + .05, .05) }
                Sfx.Shatter -> {
                    noise(Filter.Highpass, 2500.0, 7000.0, .6, .8, t, .55, .002)
                    repeat(12) { tone(Wave.Sine, 2600 + rnd.nextDouble() * 3800, 2400 + rnd.nextDouble() * 2000, .12, t + rnd.nextDouble() * .35, .18) }
                }
                Sfx.Cheer -> for ((i, f) in listOf(523.0, 659.0, 784.0, 1047.0).withIndex()) tone(Wave.Triangle, f, f, .22, t + i * .08, .3)
                Sfx.CardOpen, Sfx.CardTear -> {
                    tone(Wave.Sine, 1100.0, 260.0, .45, t, .22, .003)
                    noise(Filter.Lowpass, 800.0, 200.0, .5, .12, t + .02, .5, .05)
                    for (f in listOf(262.0, 330.0, 392.0, 494.0)) tone(Wave.Triangle, f, f, .05, t + .1, 1.4, .35)
                    for ((d, f) in listOf(.6 to 523.0, .7 to 659.0, .77 to 784.0, .84 to 880.0, .95 to 1047.0, 1.06 to 1319.0)) tone(Wave.Triangle, f, f, .09, t + d, .28, .003)
                    if (s == Sfx.CardTear) {
                        noise(Filter.Bandpass, 3200.0, 1200.0, 1.4, .3, t + 1.4, .16, .01)
                        tone(Wave.Triangle, 1568.0, 1175.0, .08, t + 1.56, .2, .003)
                    }
                }
                Sfx.CardFlip, Sfx.CardFlipBack -> {
                    noise(Filter.Bandpass, 1200.0, 500.0, .8, .3, t, .26, .05)
                    val notes = if (s == Sfx.CardFlipBack) listOf(784.0, 1047.0) else listOf(1047.0, 784.0)
                    for ((i, f) in notes.withIndex()) tone(Wave.Triangle, f, f, .08, t + .3 + i * .06, .26, .003)
                }
                Sfx.CardShut -> for ((i, f) in listOf(659.0, 494.0).withIndex()) tone(Wave.Triangle, f, f, .07, t + i * .06, .22, .003)
                Sfx.CardBack -> {
                    noise(Filter.Highpass, 2500.0, 3500.0, 1.0, .25, t + .2, .03, .001)
                    tone(Wave.Sine, 600.0, 400.0, .12, t + .2, .05)
                    repeat(7) { i ->
                        val f = 380 + rnd.nextDouble() * 520
                        tone(Wave.Sine, f, f * 1.9, .08, t + .35 + i * .06 + rnd.nextDouble() * .03, .07, .002)
                    }
                    for ((i, f) in listOf(523.0, 659.0, 784.0, 1047.0).withIndex()) tone(Wave.Square, f, f, .03, t + .9 + i * .06, .14, .003)
                }
                else -> Unit
            }
            Synth.mix(out, bus, .55)
        }
    }

    /** `zing` (a duel pick): a bright noise swipe, a struck bell's four partials and a thump; its pitch varies ±10%. */
    private fun zing(syn: Synth, out: FloatArray, rnd: Random) {
        val t = .01
        val j = .9 + rnd.nextDouble() * .2
        val m = syn.track()
        syn.noise(m, noiseOf(rnd, .35), Filter.Bandpass, Param(7000 * j).set(7000 * j, t).exp(600.0, t + .22), 9.0,
            Param(1.0).set(0.0, t).lin(.9, t + .02).exp(.001, t + .3), t, t + .35)
        for ((i, h) in listOf(1.0, 2.76, 5.4, 8.93).withIndex()) {
            syn.osc(m, Wave.Sine, Param.of(1250 * j * h), Param(1.0).set(0.0, t + .07).lin(.12 / (i + 1), t + .075).exp(.0005, t + .9 - i * .15), t + .07, t + 1)
        }
        syn.osc(m, Wave.Sine, Param(140.0).set(140.0, t + .07).exp(38.0, t + .25), Param(1.0).set(.7, t + .07).exp(.001, t + .3), t + .07, t + .32)
        Synth.mix(out, m, .5)
    }

    /** `boom` (the duel's VS, after the fanfare and the day card): a falling low triangle and three faint squares. */
    private fun boom(syn: Synth, out: FloatArray, t: Double) {
        val m = syn.track()
        syn.osc(m, Wave.Triangle, Param(110.0).set(110.0, t).exp(32.0, t + .35), Param(1.0).set(.9, t).exp(.001, t + .45), t, t + .5)
        for ((i, f) in listOf(520.0, 741.0, 1190.0).withIndex()) {
            syn.osc(m, Wave.Square, Param.of(f), Param(1.0).set(.05 / (i + 1), t).exp(.0005, t + .5), t, t + .55)
        }
        Synth.mix(out, m, .55)
    }

    /** `clang` (CLEAR!, a game marked played): a low hit, then four rising bell notes 70ms apart. */
    private fun clang(syn: Synth, out: FloatArray) {
        val t = .01
        val m = syn.track()
        syn.osc(m, Wave.Sine, Param(90.0).set(90.0, t).exp(40.0, t + .2), Param(1.0).set(.8, t).exp(.001, t + .25), t, t + .3)
        for ((i, f) in listOf(659.0, 784.0, 988.0, 1319.0).withIndex()) {
            val st = t + .12 + i * .07
            syn.osc(m, Wave.Triangle, Param.of(f), Param(1.0).set(0.0, st).lin(.12, st + .01).exp(.001, st + .6), st, st + .65)
        }
        Synth.mix(out, m, .5)
    }

    /** `fanfare` (OUT NOW): five brassy notes, each two slightly detuned saws, through a 2600 Hz lowpass; a boom at 420ms. */
    private fun fanfare(syn: Synth, out: FloatArray) {
        val t = .02
        val m = syn.track()
        for ((i, p) in listOf(392.0 to 0.0, 523.0 to .1, 659.0 to .2, 784.0 to .3, 1047.0 to .42).withIndex()) {
            val (f, d) = p
            val st = t + d
            val len = if (i == 4) .9 else .14
            for (dt in listOf(1.0, 1.005)) {
                syn.osc(m, Wave.Saw, Param.of(f * dt), Param(1.0).set(0.0, st).lin(.35, st + .02).exp(.001, st + len), st, st + len + .05)
            }
        }
        for (i in m.indices) m[i] *= .3f
        syn.filter(m, Filter.Lowpass, Param.of(2600.0), 1.0)
        Synth.mix(out, m, 1.0)
        boom(syn, out, .42 + .01)
    }

    /** The day card's three ticks (1800 Hz squares, 70ms apart, 750ms in) and its boom. */
    private fun day(syn: Synth, out: FloatArray) {
        val t = .75
        for (o in listOf(0.0, .07, .14)) {
            syn.osc(out, Wave.Square, Param.of(1800.0), Param(1.0).set(.06, t + o).exp(.001, t + o + .04), t + o, t + o + .05)
        }
        boom(syn, out, 1.05 + .01)
    }
}
