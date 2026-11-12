package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Big
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The moment a game becomes played (android/parity/2-map/3-critic.md M2, the owner's Nov 10 answer: now, with the page):
 * `syncPlayed` to played shows, 150 ms later, the "PLAYED ✓" stamp (`pnStampShow`, index.html 3545: it lands, the page
 * shakes 330 ms in and the phone buzzes, gone at 1.7 s) and the burst (`celebrate`, 11791: 28 bits in the game's colour
 * and three others fly out round a "Played!" card, with the cheer). None of it when calm. Nexi's words for it wait for
 * Phase 8a. Reached from "I finished it", a score on an unplayed game and Edit's Save to Played.
 */
object Moment {
    class Fin(val id: String, val title: String, val colour: Long?, val n: Int)

    /** The moment showing, or null. */
    var fin by mutableStateOf<Fin?>(null)
        private set

    /** The game page's shake (px), read only in its layer: none at rest. */
    val shake = mutableStateOf(Offset.Zero)
    private var n = 0

    fun finished(g: Game) {
        n += 1
        fin = Fin(g.id, g.title, Game.hex(Backup.str(g["colour"])), n)
    }

    internal fun done(f: Fin) {
        if (fin === f) fin = null
    }

    /** Tests start from nothing showing. */
    internal fun reset() {
        fin = null
        shake.value = Offset.Zero
    }
}

/** `#pnstamp b`'s easing, applied per keyframe step as CSS does. */
private val STAMP_EASE = CubicBezierEasing(.2f, 1.4f, .4f, 1f)

/** [p] (0..1 of the whole run) through keyframes [at] (fractions) → [v], each step eased by [e]. */
private fun keys(p: Float, at: FloatArray, v: FloatArray, e: androidx.compose.animation.core.Easing): Float {
    if (p <= at.first()) return v.first()
    for (i in 1 until at.size) if (p <= at[i]) {
        val f = if (at[i] == at[i - 1]) 1f else (p - at[i - 1]) / (at[i] - at[i - 1])
        return v[i - 1] + (v[i] - v[i - 1]) * e.transform(f)
    }
    return v.last()
}

/** The stamp and the burst over everything, with their sound and buzz; [calm] shows nothing (as the web's `calm()`). */
@Composable
fun FinishMoment(calm: Boolean) {
    val f = Moment.fin ?: return
    if (calm) {
        LaunchedEffect(f) {
            Moment.shake.value = Offset.Zero
            Moment.done(f)
        }
        return
    }
    key(f.n) {
        // a moment that leaves mid-shake (calm turned on, the screen gone) never leaves the page shifted
        DisposableEffect(Unit) { onDispose { Moment.shake.value = Offset.Zero } }
        val stamp = remember { Animatable(0f) }
        val burst = remember { Animatable(0f) }
        val card = remember { Animatable(0f) }
        var on by remember { mutableStateOf(false) }
        // each bit's way out and its small head start (`--x`, `--y`, `animation-delay`), drawn once per moment
        val bits = remember {
            val r = java.util.Random()
            List(28) { i ->
                val a = i / 28.0 * 2 * PI
                val d = 120 + r.nextDouble() * 110
                Triple(Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()), (r.nextDouble() * .12).toFloat(), i % 4)
            }
        }
        LaunchedEffect(Unit) {
            delay(150)
            on = true
            Sounds.play(Sfx.Cheer)
            launch { stamp.animateTo(1f, tween(1600, easing = LinearEasing)) }
            launch { burst.animateTo(1f, tween(1200 + 120, easing = LinearEasing)) }
            launch { card.animateTo(1f, tween(1800, easing = LinearEasing)) }
            launch {
                delay(330)
                Buzz.big(Big.Clear)
                // `translate(-6px,3px)` → `translate(5px,-2px)` → none over 240 ms
                val s = Animatable(0f)
                s.animateTo(1f, tween(240, easing = LinearEasing)) {
                    val p = value
                    Moment.shake.value = Offset(
                        keys(p, SHAKE_AT, floatArrayOf(0f, -6f, 5f, 0f), LinearEasing),
                        keys(p, SHAKE_AT, floatArrayOf(0f, 3f, -2f, 0f), LinearEasing),
                    )
                }
                Moment.shake.value = Offset.Zero
            }
            // the stamp goes at 1.7 s and the burst at 1.9 s (both counted from when they show)
            delay(1900)
            Moment.shake.value = Offset.Zero
            Moment.done(f)
        }
        // no early return inside key { } (the compiler named a method "<anonymous>": ClassFormatError at load)
        if (on) {
            val c0 = f.colour?.let { Color(it) } ?: Color(Web.CELE[0])
            val cols = listOf(c0, Color(Web.CELE[1]), Color(Web.CELE[2]), Color(Web.CELE[3]))
            Box(Modifier.fillMaxSize().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                // the bits: from the middle, out to their spot as they grow from .3 and fade (`pnBurst`, 1.2 s, `--ease`)
                Canvas(Modifier.fillMaxSize()) {
                    val ms = burst.value * 1320f
                    val mid = center
                    for ((to, lag, c) in bits) {
                        val p = ((ms - lag * 1000f) / 1200f).coerceIn(0f, 1f)
                        if (p <= 0f || p >= 1f) continue
                        val e = Ease.house.transform(p)
                        drawCircle(cols[c].copy(alpha = 1f - e), radius = 5.dp.toPx() * (.3f + .7f * e), center = mid + Offset(to.x.dp.toPx(), to.y.dp.toPx()) * e)
                    }
                }
                // the card: "Played!" and the game (`pnMsg`, 1.8 s), the game's colour round it
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .graphicsLayer {
                            val p = card.value
                            val e = Ease.house
                            alpha = keys(p, MSG_AT, floatArrayOf(0f, 1f, 1f, 1f, 0f), e)
                            val s = keys(p, MSG_AT, floatArrayOf(.6f, 1.05f, 1f, 1f, .96f), e)
                            scaleX = s
                            scaleY = s
                        }
                        .widthIn(max = 320.dp)
                        .shadow(40.dp, shape, ambientColor = c0, spotColor = c0)
                        .background(Color(Web.CELE_BG), shape)
                        .border(2.dp, c0, shape)
                        .padding(horizontal = 26.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText("Played!", style = Type.stat.copy(fontSize = 24.sp, lineHeight = 30.sp, textAlign = TextAlign.Center).fixed())
                    BasicText(f.title, style = Type.note.copy(textAlign = TextAlign.Center).fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // the stamp, over the card: lands big and tilting, settles at -12°, fades at the end (`pnStamp`, 1.6 s)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shape2 = RoundedCornerShape(16.dp)
                    val ink = Color(Web.STAMP)
                    BasicText(
                        "PLAYED ✓",
                        Modifier
                            .graphicsLayer {
                                val p = stamp.value
                                alpha = keys(p, STAMP_AT, floatArrayOf(0f, 1f, 1f, 1f, 0f), STAMP_EASE).coerceIn(0f, 1f)
                                val s = keys(p, STAMP_AT, floatArrayOf(2.6f, .95f, 1f, 1f, 1f), STAMP_EASE)
                                scaleX = s
                                scaleY = s
                                rotationZ = keys(p, STAMP_AT, floatArrayOf(-4f, -12f, -12f, -12f, -12f), STAMP_EASE)
                            }
                            .shadow(40.dp, shape2, ambientColor = ink, spotColor = ink)
                            .background(Color(Web.STAMP_BG), shape2)
                            .border(5.dp, ink, shape2)
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        style = Type.stat.copy(
                            fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, letterSpacing = .08.em, color = ink,
                            shadow = Shadow(LocalLook.current.volt2, blurRadius = 20f),
                        ).fixed(),
                        maxLines = 1, softWrap = false,
                    )
                    BasicText(
                        f.title,
                        Modifier.padding(top = 4.dp).widthIn(max = 300.dp),
                        style = Type.note.copy(fontSize = 13.sp, letterSpacing = .1.em, color = Pn.ice, textAlign = TextAlign.Center).fixed(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val SHAKE_AT = floatArrayOf(0f, 1f / 3, 2f / 3, 1f)
private val MSG_AT = floatArrayOf(0f, .18f, .28f, .8f, 1f)
private val STAMP_AT = floatArrayOf(0f, .22f, .3f, .82f, 1f)
