package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.probe.ProbeScreen

/** Where a link opens (Links): the tabs at Home, or the 120 Hz test. */
enum class Route { Home, Probe }

/**
 * Which tab is open, what's open on top of it, the tabs you came through and the view you picked in each tab (Queue,
 * Library…; until you pick one, a tab opens on the view the web app last had, see [Prefs.viewOf]). Saved with the
 * screen, so a fold, a rotation or Samsung closing the app in the background never loses it.
 *
 * Back (the owner, Oct 22, "1a"): it closes what's open on top first, then goes to the last tab you were on (each tab
 * once, newest first), then leaves the app. Views aren't steps back, as on the web.
 */
data class Nav(
    val tab: String = "home",
    val history: List<String> = emptyList(),
    val overlay: String? = null,
    val views: Map<String, String> = emptyMap(),
) {
    fun go(t: String) = if (t == tab) copy(overlay = null) else Nav(t, (history - t - tab) + tab, null, views)
    fun back() = if (overlay != null) copy(overlay = null) else Nav(history.last(), history.dropLast(1), null, views)
    fun open(o: String) = copy(overlay = o)
    fun view(t: String, v: String) = copy(views = views + (t to v))
    val canGoBack get() = overlay != null || history.isNotEmpty()

    companion object {
        val Saver = listSaver<Nav, String>(
            save = { listOf(it.tab, it.overlay.orEmpty(), it.views.entries.joinToString(";") { (t, v) -> "$t=$v" }) + it.history },
            restore = {
                // a list saved by an older version can be shorter: no views then
                val views = it.getOrNull(2).orEmpty().split(';').filter { p -> '=' in p }.associate { p -> p.substringBefore('=') to p.substringAfter('=') }
                Nav(it[0], it.drop(3), it[1].ifEmpty { null }, views)
            },
        )
    }
}

/** The root: your games, your look, the open screen and the back swipe. */
@Composable
fun PlayNextRoot(link: Route?, linkDone: () -> Unit) {
    val games by Games.state.collectAsStateWithLifecycle()
    val lib = (games as? Games.State.Ready)?.lib
    val look = remember(lib) { Look.of(lib) }
    val prefs = remember(lib) { Prefs.of(lib) }
    var nav by rememberSaveable(stateSaver = Nav.Saver) { mutableStateOf(Nav()) }
    // "Not now" on the restore screen: the tabs without your games, until the next start
    var skipped by rememberSaveable { mutableStateOf(false) }

    // reports and Android's exit records name the open screen
    val ctx = LocalContext.current
    LaunchedEffect(nav.tab, nav.overlay) { Screen.set(ctx, nav.overlay ?: nav.tab) }

    LaunchedEffect(link) {
        when (link) {
            Route.Probe -> nav = nav.open("probe")
            Route.Home -> nav = nav.go("home")
            null -> return@LaunchedEffect
        }
        linkDone()
    }

    Frame(look, prefs) {
            when {
                nav.overlay == "probe" -> {
                    BackHandler { nav = nav.back() }
                    ProbeScreen(done = { nav = nav.back() })
                }
                nav.overlay == "code" || games is Games.State.NoCode && !skipped -> {
                    BackHandler(enabled = nav.overlay == "code") { nav = nav.back() }
                    RestoreScreen(
                        onSkip = { if (nav.overlay == "code") nav = nav.back() else skipped = true },
                        onDone = { if (nav.overlay == "code") nav = nav.back() },
                    )
                }
                else -> {
                    BackHandler(enabled = nav.canGoBack) { nav = nav.back() }
                    Shell(nav, games, go = { nav = it })
                }
            }
    }
}

/**
 * Every screen's surroundings: no touch effect anywhere (#309, v280: the root indication draws nothing), no overscroll
 * stretch or glow, your theme's accent, your settings and whether the app is calm, the clock, and the one still
 * background in your colours.
 */
@Composable
fun Frame(look: Look, prefs: Prefs = Prefs(), now: () -> Long = System::currentTimeMillis, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalOverscrollFactory provides null,
        LocalLook provides look,
        LocalPrefs provides prefs,
        LocalCalm provides rememberCalm(prefs),
        LocalNow provides now,
        LocalTextSelectionColors provides TextSelectionColors(handleColor = look.volt, backgroundColor = look.volt.copy(alpha = 0.3f)),
    ) {
        AppBackground(Modifier.fillMaxSize(), base = look.bgBase, tint = look.bgTint, grad = look.bgGrad, content = content)
    }
}

/** The time "now" for what a screen says (as of, days away): the phone's clock, a fixed one in the screenshot tests. */
val LocalNow = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** An indication that draws nothing: a tap shows only its result. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}
