package io.github.shamlawy.playnext.probe

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.metrics.performance.JankStats
import androidx.profileinstaller.ProfileVerifier
import io.github.shamlawy.playnext.Device
import io.github.shamlawy.playnext.R
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.ui.Pn
import io.github.shamlawy.playnext.ui.mix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val CARDS = 40
private const val FLING_MS = 10_000L
private const val REST_MS = 5_000L

/**
 * The 120 Hz test (Phase 0 gate 2): the real background, 40 full-picture cards with a see-through strap, one endless
 * loop and a scripted 10 s fling, then 5 s at rest. It times every frame and the screen's refresh rate. A fail tries
 * again asking for high frame rates (Compose's preferredFrameRate), then for the highest display mode.
 */
@Composable
fun ProbeScreen(done: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val list = rememberLazyListState()
    val decay = rememberSplineBasedDecay<Float>()
    var attempt by remember { mutableIntStateOf(1) }
    var phase by remember { mutableStateOf("Getting ready") }
    var lines by remember { mutableStateOf(listOf<String>()) }
    var verdict by remember { mutableStateOf<String?>(null) }
    val pictures by produceState<List<ImageBitmap>?>(null) { value = withContext(Dispatchers.Default) { decodePictures(activity) } }
    val rec = remember { ProbeRecorder(activity) }

    DisposableEffect(Unit) {
        rec.start()
        onDispose {
            rec.stop()
            Modes.reset(activity)
        }
    }
    LaunchedEffect(pictures) {
        if (pictures == null) return@LaunchedEffect
        val results = mutableListOf<ProbeMath.Result>()
        for (a in 1..3) {
            attempt = a
            if (a == 3) Modes.highest(activity)
            phase = "Warming up"
            list.scrollToItem(0)
            delay(1_500)
            phase = "Flinging"
            rec.begin(a)
            fling(list, decay, FLING_MS)
            val r = rec.end()
            results += r
            lines = results.map { it.line() }
            phase = "Resting"
            delay(REST_MS)
            if (r.pass) break
        }
        val ok = results.last().pass
        verdict = if (ok) "Passed: smooth at 120 Hz" + (if (results.size > 1) " (attempt ${results.size})" else "")
        else "Not smooth at 120 Hz yet: sent to Claude"
        phase = "Done"
        send(activity, results)
    }

    val blink = rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink",
    )
    Box(Modifier.fillMaxSize()) {
        val pics = pictures
        LazyColumn(
            state = list,
            userScrollEnabled = verdict != null,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .then(if (attempt >= 2) Modifier.preferredFrameRate(FrameRateCategory.High) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(CARDS, key = { it }) { i -> PictureCard(i, pics?.let { it[i % it.size] }) }
        }
        // the endless loop: alpha only, read in the layer phase (no recomposition per frame)
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .align(Alignment.TopEnd)
                .size(14.dp)
                .graphicsLayer { alpha = blink.value }
                .background(Pn.volt, CircleShape),
        )
        Panel(phase, attempt, lines, verdict, done, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PictureCard(i: Int, picture: ImageBitmap?) {
    val colour = Pn.palette[i % Pn.palette.size]
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().height(220.dp).clip(Pn.r).background(Pn.card)) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // the strap: a slanted band in the card's own colour, darkened so white words read, 85% opaque
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 14.dp)
                .background(mix(colour, androidx.compose.ui.graphics.Color.Black, 0.35f).copy(alpha = 0.85f), Strap)
                .padding(start = 18.dp, end = 26.dp, top = 8.dp, bottom = 8.dp),
        ) {
            BasicText(
                "Test card ${i + 1}",
                style = TextStyle(color = Pn.ice, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                softWrap = false, maxLines = 1,
            )
        }
    }
}

/** A parallelogram: the right edge leans 12dp. */
private val Strap = GenericShape { size, _ ->
    val lean = size.height * 0.35f
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - lean, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun Panel(phase: String, attempt: Int, lines: List<String>, verdict: String?, done: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(Pn.pan.copy(alpha = 0.94f), Pn.r)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText("120 Hz test", style = TextStyle(color = Pn.ice, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
        BasicText(
            verdict ?: "$phase · attempt $attempt of 3. Don't touch the screen.",
            style = TextStyle(color = if (verdict == null) Pn.mute else Pn.ice, fontSize = 14.sp),
        )
        for (l in lines) BasicText(l, style = TextStyle(color = Pn.mute, fontSize = 12.sp))
        if (verdict != null) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .sizeIn(minHeight = Pn.touch)
                    .background(Pn.card, Pn.rs)
                    .clickable(onClick = done)
                    .semantics { contentDescription = "Done" },
                contentAlignment = Alignment.Center,
            ) { BasicText("Done", style = TextStyle(color = Pn.ice, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
        }
    }
}

/**
 * Flings the list the whole time: a new fling starts whenever the last one slows down, and the direction turns at
 * either end, so every frame of the 10 s moves.
 */
private suspend fun fling(list: LazyListState, decay: DecayAnimationSpec<Float>, ms: Long) {
    val end = SystemClock.uptimeMillis() + ms
    var dir = 1f
    list.scroll {
        while (SystemClock.uptimeMillis() < end) {
            var last = 0f
            AnimationState(0f, 9_000f * dir).animateDecay(decay) {
                val d = value - last
                last = value
                val used = scrollBy(d)
                if (abs(used) < abs(d) * 0.5f) {
                    dir = -dir
                    cancelAnimation()
                } else if (abs(velocity) < 2_500f || SystemClock.uptimeMillis() >= end) {
                    cancelAnimation()
                }
            }
        }
    }
}

private fun decodePictures(a: Activity): List<ImageBitmap> = listOf(R.drawable.probe_art_1, R.drawable.probe_art_2).mapNotNull { id ->
    runCatching { BitmapFactory.decodeResource(a.resources, id)?.also { it.prepareToDraw() }?.asImageBitmap() }.getOrNull()
}

/** Asking the window for the highest refresh rate at the current resolution, and giving the choice back afterwards. */
private object Modes {
    fun highest(a: Activity) {
        val d = a.display ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        a.window.attributes = a.window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    fun reset(a: Activity) {
        if (a.window.attributes.preferredDisplayModeId != 0) a.window.attributes = a.window.attributes.also { it.preferredDisplayModeId = 0 }
    }
}

/** Times frames during the fling: Choreographer for the rhythm, JankStats for how long each frame took. */
private class ProbeRecorder(private val activity: Activity) : Choreographer.FrameCallback {
    private val lock = Any()
    private var on = false
    private var attempt = 1
    private var last = 0L
    private var n = 0
    private val intervals = ArrayList<Long>(2_000)
    private val durations = ArrayList<Long>(2_000)
    private var jank = 0
    private var minHz = Float.MAX_VALUE
    private var maxHz = 0f
    private var stats: JankStats? = null

    fun start() {
        stats = runCatching {
            JankStats.createAndTrack(activity.window) { fd ->
                synchronized(lock) {
                    if (on) {
                        durations += fd.frameDurationUiNanos
                        if (fd.isJank) jank++
                    }
                }
            }
        }.getOrNull()
    }

    fun begin(attempt: Int) {
        synchronized(lock) {
            this.attempt = attempt
            intervals.clear(); durations.clear(); jank = 0; minHz = Float.MAX_VALUE; maxHz = 0f; last = 0L; n = 0
            on = true
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        synchronized(lock) {
            if (!on) return
            if (last != 0L) intervals += frameTimeNanos - last
            last = frameTimeNanos
            if (n++ % 30 == 0) activity.display?.refreshRate?.let { hz -> minHz = minOf(minHz, hz); maxHz = maxOf(maxHz, hz) }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun end(): ProbeMath.Result = synchronized(lock) {
        on = false
        activity.display?.refreshRate?.let { hz -> minHz = minOf(minHz, hz); maxHz = maxOf(maxHz, hz) }
        ProbeMath.result(attempt, intervals.toList(), durations.toList(), jank, minHz, maxHz)
    }

    fun stop() {
        synchronized(lock) { on = false }
        Choreographer.getInstance().removeFrameCallback(this)
        stats?.isTrackingEnabled = false
    }
}

/**
 * The result goes to Claude as a `probe` report (log-only in bugsync): each attempt, the display's modes, whether the
 * build ran compiled, and the text calibration facts (G4: dp width vs the web app's CSS px, density, font scale).
 */
private suspend fun send(a: Activity, results: List<ProbeMath.Result>) {
    val compiled = withContext(Dispatchers.IO) {
        runCatching {
            val s = ProfileVerifier.getCompilationStatusAsync().get()
            "profile ${s.profileInstallResultCode}, compiled ${s.isCompiledWithProfile}, queued ${s.hasProfileEnqueuedForCompilation()}"
        }.getOrDefault("profile unknown")
    }
    val d = a.display
    val modes = d?.supportedModes?.joinToString(" ") { "${it.physicalWidth}x${it.physicalHeight}@${Math.round(it.refreshRate)}" }.orEmpty()
    val top = d?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f
    val c = a.resources.configuration
    val fold = if (Device.unfolded(a)) "unfolded" else "folded"
    val ok = results.last().pass
    val msg = "120 Hz test " + (if (ok) "passed" else "failed") + " $fold" + (if (top < 90f) " (no mode above 60 Hz: Motion smoothness may be Standard)" else "")
    val stack = (results.map { it.line() } + listOf(
        "modes $modes",
        compiled,
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) build ${Build.DISPLAY}",
    )).joinToString("\n")
    val note = "screenWidthDp ${c.screenWidthDp} screenHeightDp ${c.screenHeightDp} densityDpi ${c.densityDpi} fontScale ${c.fontScale} $fold"
    Store.prefs.edit().putString("probeLast", "${if (ok) "Passed" else "Failed"} $fold · " + results.last().line()).apply()
    Reporter.report("probe", msg, stack = stack, note = note)
}
