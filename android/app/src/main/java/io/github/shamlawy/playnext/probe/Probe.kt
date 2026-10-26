package io.github.shamlawy.playnext.probe

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.view.Choreographer
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.profileinstaller.ProfileVerifier
import io.github.shamlawy.playnext.Device
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.ui.Pn
import io.github.shamlawy.playnext.ui.PnButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The 120 Hz test (plan "How we keep it free of lag", gate 2): the real background, 40 full-picture cards with a
 * see-through strap in their colour, one endless transform/opacity loop, then 10 s of scripted flings and 5 s at rest.
 * It times every frame and the screen's refresh rate. When it misses, it asks Android for a high frame rate, then for
 * the 120 Hz display mode. The result shows here, on Home, and goes to Claude as a `probe` report with the calibration
 * numbers (screen width in dp, density, font scale) that map web pixels to app dp.
 */
@Composable
fun ProbeScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val density = LocalDensity.current
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { Screen.set(ctx, "probe") }

    var status by remember { mutableStateOf("Getting ready…") }
    var result by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var runId by remember { mutableIntStateOf(0) }
    var pics by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    val list = rememberLazyListState()
    val fling = ScrollableDefaults.flingBehavior()

    DisposableEffect(Unit) {
        onDispose {
            view.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            activity?.window?.let { w -> w.attributes = w.attributes.also { it.preferredDisplayModeId = 0 } }
        }
    }

    LaunchedEffect(runId) {
        result = null
        snapshotFlow { pics }.first { it.isNotEmpty() }
        val rounds = if (Device.emulator) 1 else 3
        val asks = listOf("none", "high frame rate", "120 Hz display mode")
        var last: Measured? = null
        for (round in 1..rounds) {
            ask(round, view, activity)
            status = "Round $round of $rounds · hands off for 16 seconds"
            list.scrollToItem(0)
            delay(1000)
            val m = measure(activity) {
                val v = with(density) { 3600.dp.toPx() }
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var dir = 1f
                while (isActive && System.nanoTime() < end) {
                    if (!list.canScrollForward) dir = -1f else if (!list.canScrollBackward) dir = 1f
                    list.scroll { with(fling) { performFling(dir * v) } }
                    delay(80)
                }
            }
            status = "Round $round of $rounds · resting for 5 seconds"
            val idle = measure(activity) { delay(5000) }
            last = m.copy(idleP50 = ProbeMath.pct(idle.intervals, 50.0), round = round, ask = asks[round - 1])
            if (last.verdict.pass) break
        }
        val m = last ?: return@LaunchedEffect
        val info = withContext(Dispatchers.IO) { phoneInfo(activity) }
        details = m.lines() + "\n" + info
        result = ProbeMath.headline(m.verdict)
        status = if (m.verdict.pass) "Passed in round ${m.round}" else "Missed in all ${m.round} rounds"
        val folded = if (Device.unfolded(ctx)) "unfolded" else "folded"
        Store.prefs.edit().putString("probeLast", "$result ($folded, ${LocalDate.now()})").apply()
        withContext(Dispatchers.IO) {
            Reporter.report(
                "probe", "120 Hz test ${if (m.verdict.pass) "passed" else "failed"} $folded",
                note = (m.lines() + " · " + info).replace("\n", " · "),
            )
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // The panel over the list: what's happening, then the result and what to do next.
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(Pn.r)).background(Pn.pan).border(1.dp, Pn.line, RoundedCornerShape(Pn.r)).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("120 Hz test", Modifier.weight(1f), style = TextStyle(color = Pn.ice, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Blink()
            }
            Spacer(Modifier.height(4.dp))
            BasicText(status, style = TextStyle(color = Pn.mute, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            result?.let { r ->
                Spacer(Modifier.height(10.dp))
                BasicText(r, style = TextStyle(color = Pn.ice, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                BasicText(details, style = TextStyle(color = Pn.mute, fontSize = 12.sp, lineHeight = 17.sp))
                Spacer(Modifier.height(4.dp))
                BasicText("Sent to Claude with the numbers above.", style = TextStyle(color = Pn.mute, fontSize = 12.sp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (result != null) PnButton("Run again", main = true) { runId++ }
                PnButton("Close", onClick = onClose)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardW = with(density) { (maxWidth - 32.dp).roundToPx() }
            val cardH = with(density) { CARD_H.roundToPx() }
            LaunchedEffect(cardW) { pics = withContext(Dispatchers.Default) { pictures(cardW, cardH) } }
            LazyColumn(
                state = list,
                userScrollEnabled = false, // a finger mid-test would cancel the script; the test is hands off
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(40, key = { it }) { i -> ProbeCard(i, pics.getOrNull(i % PICS)) }
            }
        }
    }
}

private val CARD_H = 200.dp
private const val PICS = 6

/** A full-picture card with a slanted see-through strap in the card's colour, like a closed Queue row. */
@Composable
private fun ProbeCard(i: Int, pic: ImageBitmap?) {
    val colour = Pn.palette[i % Pn.palette.size]
    Box(Modifier.fillMaxWidth().height(CARD_H).clip(RoundedCornerShape(Pn.r)).background(Pn.card)) {
        if (pic != null) Image(pic, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(.78f).height(66.dp).drawWithCache {
                val slant = size.height * .32f
                val p = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width - slant, size.height); lineTo(0f, size.height); close()
                }
                val fill = lerp(colour, Color.Black, .35f).copy(alpha = .85f)
                onDrawBehind { drawPath(p, fill) }
            }.padding(start = 18.dp, top = 10.dp),
        ) {
            Column {
                BasicText(if (i == 0) "Up next" else "In the queue", style = TextStyle(color = Pn.ice.copy(alpha = .8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                BasicText("Test card ${i + 1}", style = TextStyle(color = Pn.ice, fontSize = 17.sp, fontWeight = FontWeight.Bold), softWrap = false, maxLines = 1)
            }
        }
    }
}

/** The endless loop the test keeps running: a dot that breathes, read only in the layer (no recomposition). */
@Composable
private fun Blink() {
    val t = rememberInfiniteTransition(label = "blink")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "k")
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(12.dp).graphicsLayer {
                scaleX = .8f + .35f * k; scaleY = scaleX; alpha = .5f + .5f * k
            }.background(Pn.volt, CircleShape),
        )
    }
}

/** Made-up pictures at the size they're shown: a dark gradient in two palette colours with soft spots of light. */
private fun pictures(w: Int, h: Int): List<ImageBitmap> = List(PICS) { i ->
    val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val a = lerp(Pn.palette[i % 8], Color.Black, .45f).toArgb()
    val b = lerp(Pn.palette[(i + 3) % 8], Color.Black, .6f).toArgb()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    val rnd = Random(i * 31 + 7)
    repeat(16) {
        val r = h * (.15f + rnd.nextFloat() * .5f)
        val spot = Pn.palette[rnd.nextInt(8)].copy(alpha = .1f + rnd.nextFloat() * .25f).toArgb()
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        p.shader = RadialGradient(x, y, r, spot, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
    bmp.prepareToDraw()
    bmp.asImageBitmap()
}

/** Round 1 asks for nothing, round 2 for a high frame rate, round 3 for the highest-refresh display mode. */
private fun ask(round: Int, view: View, activity: Activity?) {
    if (round >= 2) view.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
    if (round >= 3) {
        val w = activity?.window ?: return
        val d = activity.display
        val cur = d.mode
        val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate } ?: return
        w.attributes = w.attributes.also { it.preferredDisplayModeId = best.modeId }
    }
}

/** What one stretch of the test measured. */
private data class Measured(
    val intervals: List<Double>,
    val hzMin: Float,
    val hzMax: Float,
    val jank: Double,
    val overrun: Double,
    val totalP50: Double,
    val totalP90: Double,
    val totalP99: Double,
    val idleP50: Double = 0.0,
    val round: Int = 1,
    val ask: String = "none",
) {
    val verdict get() = ProbeMath.judge(intervals, hzMax)

    fun lines(): String {
        val v = verdict
        return "Round $round (asked for: $ask) · ${v.frames} frames, ${ProbeMath.share(v.onTime)} within ${ProbeMath.ms(ProbeMath.ON_TIME_MS)} ms\n" +
            "Gap between frames: middle ${ProbeMath.ms(v.p50)} ms, slowest 1% ${ProbeMath.ms(v.p99)} ms, at rest ${ProbeMath.ms(idleP50)} ms\n" +
            "Screen ${hzMin.roundToInt()}-${hzMax.roundToInt()} Hz while flinging · janky ${ProbeMath.share(jank)} · late ${ProbeMath.share(overrun)}\n" +
            "Frame work: middle ${ProbeMath.ms(totalP50)} ms, 90% ${ProbeMath.ms(totalP90)} ms, 99% ${ProbeMath.ms(totalP99)} ms"
    }
}

/**
 * Runs [block] while timing frames three ways: the gap between frames (Choreographer, the pass rule), the screen's
 * refresh rate every 200 ms, and JankStats (each frame's work and whether it ran late).
 */
private suspend fun measure(activity: Activity?, block: suspend () -> Unit): Measured {
    val gaps = ArrayList<Double>(2400)
    val choreo = Choreographer.getInstance()
    var lastT = 0L
    var on = true
    val cb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (lastT != 0L) gaps += (t - lastT) / 1e6
            lastT = t
            if (on) choreo.postFrameCallback(this)
        }
    }
    val totals = ArrayList<Double>(2400)
    var frames = 0
    var janky = 0
    var late = 0
    val lock = Any()
    // JankStats calls back on its own thread and reuses FrameData: copy the numbers out at once.
    val js = activity?.window?.let { w ->
        JankStats.createAndTrack(w) { fd ->
            synchronized(lock) {
                frames++
                if (fd.isJank) janky++
                if (fd is FrameDataApi31) {
                    totals += fd.frameDurationTotalNanos / 1e6
                    if (fd.frameOverrunNanos > 0) late++
                } else {
                    totals += fd.frameDurationUiNanos / 1e6
                }
            }
        }
    }
    val hz = ArrayList<Float>()
    choreo.postFrameCallback(cb)
    kotlinx.coroutines.coroutineScope {
        val sampler = launch {
            while (isActive) {
                activity?.display?.refreshRate?.let { hz += it }
                delay(200)
            }
        }
        try {
            block()
        } finally {
            sampler.cancel()
            on = false
            js?.isTrackingEnabled = false
        }
    }
    return synchronized(lock) {
        Measured(
            intervals = gaps.toList(),
            hzMin = hz.minOrNull() ?: 0f,
            hzMax = hz.maxOrNull() ?: 0f,
            jank = if (frames == 0) 0.0 else janky.toDouble() / frames,
            overrun = if (frames == 0) 0.0 else late.toDouble() / frames,
            totalP50 = ProbeMath.pct(totals, 50.0),
            totalP90 = ProbeMath.pct(totals, 90.0),
            totalP99 = ProbeMath.pct(totals, 99.0),
        )
    }
}

/** The phone side of the calibration: what one web pixel is here, the display modes, and whether the build is compiled. */
private fun phoneInfo(activity: Activity?): String {
    val ctx = activity ?: return ""
    val c = ctx.resources.configuration
    val sysFont = runCatching { Settings.System.getFloat(ctx.contentResolver, Settings.System.FONT_SCALE) }.getOrDefault(c.fontScale)
    val modes = ctx.display.supportedModes.map { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate.roundToInt()}" }.distinct().joinToString(" ")
    // Samsung's One UI number (e.g. 170000 = One UI 7); absent on other phones.
    val oneUi = runCatching { Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) }.getOrNull()
    val compiled = runCatching {
        val s = ProfileVerifier.getCompilationStatusAsync().get(3, TimeUnit.SECONDS)
        (if (s.isCompiledWithProfile) "yes" else "no") + " (${s.profileInstallResultCode})"
    }.getOrDefault("unknown")
    return "Width ${c.screenWidthDp} dp, height ${c.screenHeightDp} dp, smallest ${c.smallestScreenWidthDp} dp · " +
        "${c.densityDpi} dpi (×${ctx.resources.displayMetrics.density}) · font scale ${c.fontScale} (system $sysFont)\n" +
        "Modes $modes · Android ${Build.VERSION.RELEASE}${oneUi?.let { " · One UI $it" } ?: ""} · compiled with its profile: $compiled"
}
