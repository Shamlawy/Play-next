package io.github.shamlawy.playnext.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import io.github.shamlawy.playnext.R
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.kit.ReelMusic
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/**
 * The opening reel (`#pnboot`, index.html 2799 and 8137; android/parity/3-map/2-rail-reel-widgets.md §2, 3-critic.md
 * (e), M7): on a cold launch a black layer over everything, the reel's first frame (the glowing pixel) with a soft glow
 * breathing behind it and "Tap to play" under it. A tap plays the 15 s reel ([ReelDraw]) with its music ([ReelMusic],
 * only with Sounds on); a tap while it plays, Skip or back ends it, and it fades out over 300 ms. Never when calm,
 * never with Settings › Opening reel off, never on a return from the background, a fold or a turn: [arm] is called
 * once a process, by the launch itself.
 */
object Reel {
    /** The reel is up (waiting or playing): what's new waits for it. */
    var up by mutableStateOf(false)
        private set
    private var armed = false

    /** A cold launch (MainActivity with no saved state): the reel shows, once in this run of the app. */
    fun arm() {
        if (armed) return
        armed = true
        up = true
    }

    internal fun gone() {
        up = false
        forced = false
    }

    /** Played by hand (What's new's Show me): even with the switch Off, never when calm. */
    var forced by mutableStateOf(false)
        private set

    fun replay() {
        forced = true
        up = true
    }

    /** Tests start with no reel, and can arm it again. */
    @androidx.annotation.VisibleForTesting
    internal fun reset() {
        armed = false
        up = false
        forced = false
        choice = null
    }

    private const val KEY = "reelOff"

    /** Settings › Opening reel, kept on this phone: true = Off, false = Every launch, null = as your backup says (`S.reelOff`). */
    var choice by mutableStateOf(runCatching { if (Store.ready() && Store.prefs.contains(KEY)) Store.prefs.getBoolean(KEY, false) else null }.getOrNull())
        private set

    fun pick(off: Boolean) {
        choice = off
        runCatching { if (Store.ready()) Store.prefs.edit().putBoolean(KEY, off).apply() }
    }

    fun off(prefs: Prefs) = choice ?: prefs.reelOff

    /** `pnreelArt`: Playing now first, then the Queue's waiting games that are out in its order, then any others; only real pictures; 9 at most. */
    fun pick9(lib: Library, now: Long, zone: ZoneId): List<Game> {
        fun ok(g: Game) = Backup.str(g["img"]).let { it.isNotEmpty() && !it.startsWith("data:image/svg") }
        val q = runCatching {
            val qu = Queue(lib, Rank(lib, now, zone), now, zone)
            qu.sort(lib.games.filter { qu.waiting(it) && qu.isOutNow(it) }, Backup.str(lib["sort"]).ifEmpty { "manual" })
        }.getOrDefault(emptyList())
        val seen = HashSet<String>()
        val out = ArrayList<Game>(9)
        for (g in lib.games.filter { it.rawState == "playing" } + q + lib.games) {
            if (ok(g) && seen.add(g.id)) {
                out += g
                if (out.size >= 9) break
            }
        }
        return out
    }

    /** The centre first, then the corners (they're on screen longest), then the rest. */
    val SLOT = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7)
}

/** The reel's covers as they load (one at a time, only while the pixel waits): the ones ready at the tap play. */
private class Covers {
    val pics = arrayOfNulls<Bitmap>(9)
    var title = ""
    var hours = 0.0

    fun art(): ReelDraw.Art? = if (title.isEmpty() && pics.all { it == null }) null else ReelDraw.Art(pics.toList(), title, hours)
}

/** A small copy (120x156, cropped like a card) of [url]; null when it can't load. */
private suspend fun small(ctx: android.content.Context, url: String): Bitmap? {
    val r = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(picUrl(url)).size(120, 156).scale(Scale.FILL).allowHardware(false).build())
    val img = (r as? SuccessResult)?.image ?: return null
    return withContext(Dispatchers.Default) {
        runCatching {
            val b = img.toBitmap()
            val s = maxOf(120f / b.width, 156f / b.height)
            val w = (120 / s).toInt().coerceIn(1, b.width)
            val h = (156 / s).toInt().coerceIn(1, b.height)
            Bitmap.createScaledBitmap(Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h), 120, 156, true)
        }.getOrNull()
    }
}

/** The opening reel over everything, while [Reel.up]: [games] gives the covers, [prefs] Sounds and the backup's Off. */
@Composable
fun BoxScope.OpeningReel(games: Games.State, prefs: Prefs, calm: Boolean) {
    if (!Reel.up) return
    if (calm || Reel.off(prefs) && !Reel.forced) {
        // calm, or Off in Settings or your backup (read once your games open, under the splash): no reel this launch
        LaunchedEffect(Unit) { Reel.gone() }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val face = remember { runCatching { ResourcesCompat.getFont(ctx, R.font.poppins_700) }.getOrNull() ?: Typeface.DEFAULT_BOLD }
    var playing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var t by remember { mutableFloatStateOf(0f) }
    var art by remember { mutableStateOf<ReelDraw.Art?>(null) }
    val covers = remember { Covers() }
    val fade = remember { Animatable(1f) }
    var music by remember { mutableStateOf<ReelMusic.Playing?>(null) }

    // the soundtrack is made while the pixel waits, only with Sounds on (a moment, once: later launches read the file)
    val track = remember(prefs.sound) { if (prefs.sound) ReelMusic.warm(ctx.applicationContext) else null }
    // the covers, from 500 ms: the game to play next in the middle, one at a time
    val lib = (games as? Games.State.Ready)?.lib
    LaunchedEffect(lib) {
        val l = lib ?: return@LaunchedEffect
        delay(500)
        val list = withContext(Dispatchers.Default) { Reel.pick9(l, System.currentTimeMillis(), ZoneId.systemDefault()) }
        if (list.isEmpty()) return@LaunchedEffect
        covers.title = list[0].title
        covers.hours = Backup.num(list[0]["hours"], 0.0, 0.0, 100000.0)
        for ((i, g) in list.withIndex()) {
            if (playing || leaving) break
            covers.pics[Reel.SLOT[i]] = runCatching { small(ctx, Backup.str(g["img"])) }.getOrNull()
        }
    }

    val leave = {
        if (!leaving) {
            leaving = true
            music?.fadeOut()
            scope.launch {
                fade.animateTo(0f, tween(300, easing = FastOutLinearInEasing))
                Reel.gone()
            }
        }
    }
    // your settings (Sounds, the backup's Off) are known once your games open: a tap before that plays when they are
    val starting = games is Games.State.Starting
    var want by remember { mutableStateOf(false) }
    val play = {
        if (starting) want = true
        else if (!playing && !leaving) {
            playing = true
            Crumbs.tap("opening reel", "reel")
            // the covers ready at the tap play; the loader stops, so nothing loads under the reel
            art = covers.art()
            scope.launch {
                val f = track?.let { d -> withTimeoutOrNull(1500) { d.await() } }
                if (leaving) return@launch
                // the picture waits for the sound to come out
                val lead = if (f != null) ReelMusic.play(f)?.also { music = it }?.let { .06f } ?: 0f else 0f
                val start = withFrameNanos { it }
                while (!leaving) {
                    val now = withFrameNanos { it }
                    t = ((now - start) / 1e9f - lead).coerceAtLeast(0f)
                    if (t >= ReelDraw.LEN) {
                        t = 0f
                        music?.release()
                        leave()
                        break
                    }
                }
            }
        }
    }
    // the touch and TalkBack handlers are made once: they call the newest play and leave (Sounds may have come on since)
    val playNow by rememberUpdatedState(play)
    val leaveNow by rememberUpdatedState(leave)
    LaunchedEffect(starting, want) { if (!starting && want) playNow() }
    DisposableEffect(Unit) { onDispose { music?.release() } }
    // leaving the app ends it (the music would play on from a hidden app, and the clock would jump on return)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        music?.release()
        Reel.gone()
    }
    BackHandler { leaveNow() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(androidx.compose.ui.graphics.Color.Black)
            // the whole layer takes every touch (`touch-action: none`): a tap plays, a tap while it plays ends it
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    if (up != null) { if (playing) leaveNow() else playNow() }
                }
            }
            .drawBehind { drawIntoCanvas { ReelDraw.frame(it.nativeCanvas, t, size.width, size.height, face, art) } }
            .semantics { contentDescription = if (playing) "The opening reel" else "Play the opening reel"; role = Role.Button; onClick { if (playing) leaveNow() else playNow(); true } },
    ) {
        if (!playing) Waiting()
        Skip(playing, Modifier.align(Alignment.BottomCenter)) { leaveNow() }
    }
}

/** The pixel's soft glow breathing (`pnreelGlow`, 2.4 s) and "Tap to play" fading in under it. */
@Composable
private fun BoxScope.Waiting() {
    val volt = LocalLook.current.volt
    val breath = rememberInfiniteTransition(label = "reel glow")
    val k by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "reel glow k")
    Box(
        Modifier
            .align(Alignment.Center)
            .size(200.dp)
            .graphicsLayer {
                alpha = .45f + .55f * k
                val s = .82f + .26f * k
                scaleX = s
                scaleY = s
            }
            .background(Brush.radialGradient(0f to volt.copy(alpha = .6f), .45f to volt.copy(alpha = .2f), 1f to volt.copy(alpha = 0f)), CircleShape)
            .clearAndSetSemantics { },
    )
    val say = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(300)
        say.animateTo(1f, tween(500, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    BasicText(
        "Tap to play",
        style = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Pn.ice, textAlign = TextAlign.Center),
        modifier = Modifier.align(Alignment.Center).offset(y = 70.dp).graphicsLayer { alpha = say.value },
    )
}

/** Skip at the foot: there while the pixel waits, faded in 2 s into the reel (`.pnreel-skip`). */
@Composable
private fun Skip(playing: Boolean, modifier: Modifier, skip: () -> Unit) {
    val a = remember { Animatable(1f) }
    LaunchedEffect(playing) {
        if (playing) {
            a.snapTo(0f)
            delay(2_000)
            a.animateTo(1f, tween(400, easing = LinearEasing))
        }
    }
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 18.dp)
            .heightIn(min = 44.dp)
            .graphicsLayer { alpha = a.value }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    if (up != null) skip()
                }
            }
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { skip(); true } }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Skip", style = TextStyle(fontFamily = Fonts.poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Pn.mute))
    }
}
