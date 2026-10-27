package io.github.shamlawy.playnext.kit

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The big moments' vibrations (the owner, Oct 26: big moments only, never on every line Nexi says). Each is built from
 * the phone's own haptic primitives, richer than the web's single buzz; [fallback] is the web's `navigator.vibrate`
 * pattern (buzz, pause, buzz… in ms) for a phone without them.
 */
enum class Big(val label: String, val fallback: LongArray) {
    /** A game marked played: a thud and two settling ticks (the web's PLAYED stamp, [30, 40, 20]). */
    Clear("CLEAR!", longArrayOf(30, 40, 20)),

    /** The duel's winner picked: a quick rise landing on a firm click (the web's quick duel, 18). */
    DuelWin("Duel win", longArrayOf(18)),

    /** The Queue card turning over: a lift and a light landing (the web's card turn, 8). */
    CardTurn("Card turns", longArrayOf(8)),

    /** RANK UP and Nexi's level up: a slow rise and two clicks (the web's 40). */
    LevelUp("Level up", longArrayOf(40)),
}

/**
 * Vibrations, the web app's way (`navigator.vibrate`, android/parity/0-map/3-kit.md §2): [ms] buzzes, pauses, buzzes…;
 * the three swipe ticks only while "Buzz as each cover lands" is on (`S.buzz`, [swipes]); the [Big] moments richer.
 * They go through the phone's touch-feedback setting, so turning that off there quiets the app too.
 */
object Buzz {
    /** Library › "Buzz as each cover lands" (`S.buzz !== false`): only the swipe ticks. */
    @Volatile var swipes = true

    private var vib: Vibrator? = null
    private val touch = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    fun init(ctx: Context) {
        vib = ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
    }

    /** The web's `navigator.vibrate(pattern)`: buzz, pause, buzz… in ms. */
    fun ms(vararg pattern: Long) {
        val v = vib ?: return
        if (pattern.isEmpty()) return
        val effect = if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
        else VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1)
        runCatching { v.vibrate(effect, touch) }
    }

    /** The 9ms tick as a cover lands in a swipe (Library's reel, a game page to the next). */
    fun swipeTick() {
        if (swipes) ms(9)
    }

    fun big(m: Big) {
        val v = vib ?: return
        val effect = compose(m, v) ?: VibrationEffect.createWaveform(longArrayOf(0) + m.fallback, -1)
        runCatching { v.vibrate(effect, touch) }
    }

    /** [m] from the phone's primitives, or null when this phone lacks one of them. */
    private fun compose(m: Big, v: Vibrator): VibrationEffect? = when (m) {
        Big.Clear ->
            if (!v.areAllPrimitivesSupported(Composition.PRIMITIVE_THUD, Composition.PRIMITIVE_CLICK, Composition.PRIMITIVE_TICK)) null
            else VibrationEffect.startComposition().addPrimitive(Composition.PRIMITIVE_THUD, 1f)
                .addPrimitive(Composition.PRIMITIVE_CLICK, .8f, 70).addPrimitive(Composition.PRIMITIVE_TICK, .6f, 50).compose()
        Big.DuelWin ->
            if (!v.areAllPrimitivesSupported(Composition.PRIMITIVE_QUICK_RISE, Composition.PRIMITIVE_CLICK)) null
            else VibrationEffect.startComposition().addPrimitive(Composition.PRIMITIVE_QUICK_RISE, .6f)
                .addPrimitive(Composition.PRIMITIVE_CLICK, 1f).compose()
        Big.CardTurn ->
            if (!v.areAllPrimitivesSupported(Composition.PRIMITIVE_LOW_TICK, Composition.PRIMITIVE_TICK)) null
            else VibrationEffect.startComposition().addPrimitive(Composition.PRIMITIVE_LOW_TICK, .8f)
                .addPrimitive(Composition.PRIMITIVE_TICK, .6f, 120).compose()
        Big.LevelUp ->
            if (!v.areAllPrimitivesSupported(Composition.PRIMITIVE_SLOW_RISE, Composition.PRIMITIVE_CLICK)) null
            else VibrationEffect.startComposition().addPrimitive(Composition.PRIMITIVE_SLOW_RISE, .7f)
                .addPrimitive(Composition.PRIMITIVE_CLICK, 1f).addPrimitive(Composition.PRIMITIVE_CLICK, .7f, 80).compose()
    }
}
