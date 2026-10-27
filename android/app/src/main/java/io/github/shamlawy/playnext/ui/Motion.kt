package io.github.shamlawy.playnext.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** The web app's easings (android/parity/0-map/2-look.md §5). */
object Ease {
    /** `--ease`: the house curve (the header card's deal, the rail). */
    val house = CubicBezierEasing(.22f, 1f, .36f, 1f)

    /** Entrances: a screen coming in (`slashin`, `cut`, the v110 slide). */
    val enter = CubicBezierEasing(.16f, 1f, .3f, 1f)

    /** The slash's slab leaving (`pnwGo`). */
    val wipe = CubicBezierEasing(.75f, 0f, .25f, 1f)

    /** The slash's word popping in, a little past its size and back (`pnwT`). */
    val pop = CubicBezierEasing(.2f, 1.5f, .4f, 1f)

    /** The view pill's lit segment sliding across. */
    val slide = CubicBezierEasing(.4f, 0f, .2f, 1f)
}

/** Settings › Switching tabs (`S.tabFade`): the Persona slash (the default), Fade in, or Instant. */
enum class TabFade { Slash, Fade, Instant }

/**
 * The settings from your backup that shape the shared kit: motion, the tab switch, the view pill, the header cards and
 * which view each tab opens on. Read only: the app never writes them back (a practice copy until switch day).
 */
@Immutable
data class Prefs(
    /** Animations off (`S.motion === false`). */
    val motionOff: Boolean = false,
    /** Battery saver doesn't calm the app (`S.batOff`). */
    val batOff: Boolean = false,
    val tabFade: TabFade = TabFade.Slash,
    /** The floating view pill (`S.pill !== false`); off, a row of chips stands in. */
    val pill: Boolean = true,
    /** Header cards off: a plain title (`S.shxOff`). */
    val shxOff: Boolean = false,
    /** What's on the header cards (`S.shxX`, every extra when it was never set). */
    val shx: List<String> = SHX_FULL,
    /** The Timer view in Plan (`S.timerOn`). */
    val timerOn: Boolean = false,
    /** The view each tab was last on in the web app (`S.gamesView`, `S.calView`, `S.tasteView`). */
    val saved: Map<String, String> = emptyMap(),
    /** Settings › Sounds (`S.sound`, off unless switched on). */
    val sound: Boolean = false,
    /** Nexi's blips (`S.pnVoice !== "mute"`; before it, `S.pnMute`). */
    val nexiVoice: Boolean = true,
    /** "Buzz as each cover lands" (`S.buzz !== false`): only the swipe ticks. */
    val buzz: Boolean = true,
    /** Customise › A game's own page › Motion on this page (`S.hubAnim !== false`): the cover's flight onto it. */
    val hubAnim: Boolean = true,
) {
    /** A tab's views in the pill's order (Plan's Timer only while it's switched on); empty for Home and Add. */
    fun views(tab: String): List<Pair<String, String>> = Web.VIEWS[tab].orEmpty().filter { it.first != "timer" || timerOn }

    /** `subOf(tab)`: the view open in [tab]: yours in this app, else the web app's saved one, else the tab's default. */
    fun viewOf(tab: String, picked: Map<String, String>): String? {
        val set = views(tab).map { it.first }
        if (set.isEmpty()) return null
        return picked[tab]?.takeIf { it in set } ?: saved[tab]?.takeIf { it in set } ?: Web.VIEW_DEF[tab]?.takeIf { it in set } ?: set.first()
    }

    fun shxOn(k: String) = !shxOff && k in shx

    companion object {
        /** `SHX_X` in its fixed order: covers, spotlight line, top genres, 6-month chart, mix bar, backdrop art. */
        val SHX_FULL = listOf("fan", "line", "tags", "chart", "bar", "art")

        fun of(lib: Library?): Prefs {
            if (lib == null) return Prefs()
            val fade = when (val t = lib["tabFade"]) {
                null -> TabFade.Slash // applyLook sets "slash" when it was never chosen
                is JsonPrimitive -> if (!t.isString && t.content == "true") TabFade.Fade else if (t.isString && t.content == "slash") TabFade.Slash else TabFade.Instant
                else -> TabFade.Instant
            }
            val x = lib["shxX"] as? JsonArray
            return Prefs(
                motionOff = lib.off("motion"),
                batOff = Backup.truthy(lib["batOff"]),
                tabFade = fade,
                pill = !lib.off("pill"),
                shxOff = Backup.truthy(lib["shxOff"]),
                shx = x?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: SHX_FULL,
                timerOn = Backup.truthy(lib["timerOn"]),
                saved = Web.VIEW_KEY.mapNotNull { (tab, key) -> Backup.str(lib[key]).ifEmpty { null }?.let { tab to it } }.toMap(),
                sound = Backup.truthy(lib["sound"]),
                nexiVoice = when (Backup.str(lib["pnVoice"])) {
                    "mute" -> false
                    "" -> !Backup.truthy(lib["pnMute"])
                    else -> true
                },
                buzz = !lib.off("buzz"),
                hubAnim = !lib.off("hubAnim"),
            )
        }
    }
}

val LocalPrefs = staticCompositionLocalOf { Prefs() }

/**
 * `calm()`: true while nothing should move. Animations off in the backup, the phone's Remove animations (its animator
 * scale at 0), or Battery saver / 15% or less and not charging (unless "battery saver doesn't calm the app").
 */
val LocalCalm = compositionLocalOf { false }

@Composable
fun rememberCalm(prefs: Prefs): Boolean {
    val ctx = LocalContext.current
    var still by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    var low by remember { mutableStateOf(false) }
    DisposableEffect(ctx) {
        val scale = ValueAnimator.DurationScaleChangeListener { still = it == 0f }
        ValueAnimator.registerDurationScaleChangeListener(scale)
        val power = ctx.getSystemService(PowerManager::class.java)
        var level = 100
        var plugged = true
        fun read(i: Intent) {
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) level = l * 100 / s
            plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
        fun update() { low = power?.isPowerSaveMode == true || (!plugged && level <= 15) }
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_BATTERY_CHANGED) read(i)
                update()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // the battery broadcast is sticky: registering hands back the latest one at once
        ContextCompat.registerReceiver(ctx, rx, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let(::read)
        update()
        onDispose {
            ValueAnimator.unregisterDurationScaleChangeListener(scale)
            runCatching { ctx.unregisterReceiver(rx) }
        }
    }
    return prefs.motionOff || still || (!prefs.batOff && low)
}
