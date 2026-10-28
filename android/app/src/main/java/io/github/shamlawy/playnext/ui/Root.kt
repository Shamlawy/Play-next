package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shamlawy.playnext.Screen
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Shared
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.probe.ProbeScreen
import io.github.shamlawy.playnext.report.Crumbs
import java.time.ZoneId

/** Where a link opens (Links): a tab and its view, a game's page, Add with something shared, or the 120 Hz test. */
sealed interface Route {
    data object Probe : Route

    data class Tab(val tab: String, val view: String? = null) : Route

    /** A game's page; [id] null = On deck's first game ("What should I play?"). */
    data class Game(val id: String?) : Route

    /** Add, with the game named in what was shared into the app (null when it named none). */
    data class Add(val shared: Shared?) : Route
}

/**
 * Which tab is open, what's open on top of it (in the order you opened it) and the view you picked in each tab (Queue,
 * Library…; until you pick one, a tab opens on the view the web app last had, see [Prefs.viewOf]). Saved with the
 * screen, so a fold, a rotation or Samsung closing the app in the background never loses it.
 *
 * Back (the owner, Oct 28, replacing Oct 22's "1a", which also walked back through the tabs: "the back should return
 * me back till I reach the tab I was in … not from taste to home … the back should have a memory from where I was"):
 * each back closes the newest thing you opened and shows the one you opened it from (Settings after its 120 Hz test, a
 * game's page after the page you opened it from), until you're on your tab; there back leaves the app. Switching tabs
 * and views aren't steps back.
 */
data class Nav(
    val tab: String = "home",
    /** What's open on top of the tab, oldest first: "settings", "code", "probe", "game:<id>". */
    val stack: List<String> = emptyList(),
    val views: Map<String, String> = emptyMap(),
    /** Steps let go off the bottom past [DEEP], so the steps still open keep their [key]s. */
    val dropped: Int = 0,
) {
    /** What's on top (null: the tab itself). */
    val overlay get() = stack.lastOrNull()

    /** A tab, with nothing open on it (a tap on the open tab closes what's open, as before). */
    fun go(t: String) = Nav(t, emptyList(), views)
    fun back() = copy(stack = stack.dropLast(1))

    /** Closes step [i] and anything opened over it (a page closing itself closes itself, whatever's on top). */
    fun closeAt(i: Int) = if (i in stack.indices) copy(stack = stack.take(i)) else this

    /** Opens [o] on top of what's open (the same thing twice in a row is one step); at most [DEEP] steps are kept. */
    fun open(o: String): Nav {
        if (o == overlay) return this
        val cut = (stack.size + 1 - DEEP).coerceAtLeast(0)
        return copy(stack = (stack + o).drop(cut), dropped = dropped + cut)
    }
    fun view(t: String, v: String) = copy(views = views + (t to v))
    val canGoBack get() = stack.isNotEmpty()

    /** Step [i]'s own name for its saved place (scroll spot, already open): the same for as long as it's open. */
    fun key(i: Int) = "${dropped + i}:${stack[i]}"

    companion object {
        const val DEEP = 24

        val Saver = listSaver<Nav, String>(
            save = { listOf(it.tab, it.stack.joinToString("\n"), it.views.entries.joinToString(";") { (t, v) -> "$t=$v" }, it.dropped.toString()) },
            restore = {
                // a list saved by an older version can be shorter (no views) or longer (its tab history, gone since
                // android-v9); its one overlay reads as a stack of one
                val views = it.getOrNull(2).orEmpty().split(';').filter { p -> '=' in p }.associate { p -> p.substringBefore('=') to p.substringAfter('=') }
                Nav(it[0], it[1].split('\n').filter { o -> o.isNotEmpty() }, views, it.getOrNull(3)?.toIntOrNull() ?: 0)
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

    // something shared into the app: Add shows the game it names (adding it comes with Add's phase); kept when Android
    // recreates the app, since the link that brought it is read only once
    var shared by rememberSaveable(stateSaver = SharedSaver) { mutableStateOf<Shared?>(null) }

    // the switches the kit reads away from the screens: Sounds, Nexi's voice, the swipe buzz
    LaunchedEffect(prefs) {
        Sounds.on = prefs.sound
        Sounds.nexi = prefs.nexiVoice
        Buzz.swipes = prefs.buzz
        if (prefs.sound) Sounds.warm()
    }

    // reports and Android's exit records name the open screen as the web app does ("games/queue", "settings")
    val ctx = LocalContext.current
    val screen = nav.overlay?.let { if (it.startsWith("game:")) "game page" else it }
        ?: (nav.tab + (prefs.viewOf(nav.tab, nav.views)?.let { "/$it" } ?: ""))
    LaunchedEffect(screen) {
        Screen.set(ctx, screen)
        Crumbs.screen(screen)
    }

    LaunchedEffect(link, games) {
        val r = link ?: return@LaunchedEffect
        val st = games
        // a game's link waits for your games (a moment at start), unless there are none to wait for
        if (r is Route.Game && (st is Games.State.Starting || st is Games.State.Fetching && st.why == null)) return@LaunchedEffect
        nav = when (r) {
            Route.Probe -> nav.open("probe")
            is Route.Tab -> nav.go(r.tab).let { n -> r.view?.let { n.view(r.tab, it) } ?: n }
            is Route.Add -> {
                shared = r.shared
                nav.go("add")
            }
            is Route.Game -> {
                val l = (st as? Games.State.Ready)?.lib
                val id = r.id ?: l?.let { Rank(it, System.currentTimeMillis(), ZoneId.systemDefault()).onDeck(1).firstOrNull()?.id }
                // on top of where you were: back returns there
                if (l != null && l.games.any { it.id == id }) nav.open("game:$id") else nav.go("home")
            }
        }
        // named by hand: a class name can change in the release build
        Crumbs.add("link " + when (r) {
            Route.Probe -> "probe"
            is Route.Tab -> "tab"
            is Route.Game -> "game"
            is Route.Add -> "add"
        })
        linkDone()
    }

    // the tabs and Settings keep their place (scroll spots, the open sheet) while the 120 Hz test or the restore code
    // covers them, so back finds them as you left them
    val kept = rememberSaveableStateHolder()
    Frame(look, prefs) {
            when {
                nav.overlay == "probe" -> {
                    BackHandler { nav = nav.back() }
                    ProbeScreen(done = { nav = nav.back() })
                }
                nav.overlay == "code" || games is Games.State.NoCode && !skipped -> {
                    // closing the code step counts as "Not now" too: after "Use a different restore code" there's no
                    // code any more, and the restore screen would stay up with nothing left to close (the next back
                    // would leave the app)
                    val close = {
                        if (nav.overlay == "code") nav = nav.back()
                        skipped = true
                    }
                    BackHandler(enabled = nav.overlay == "code") { close() }
                    RestoreScreen(
                        onSkip = close,
                        onDone = { if (nav.overlay == "code") nav = nav.back() },
                    )
                }
                // this phone's copy is still opening (a moment, under the splash): the tabs wait for it, so a scroll spot
                // Android kept for them isn't cut to fit a page that's still short
                games is Games.State.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText("Opening your games…", style = Type.note)
                }
                else -> {
                    BackHandler(enabled = nav.canGoBack) { nav = nav.back() }
                    CompositionLocalProvider(LocalShared provides shared) {
                        kept.SaveableStateProvider("shell") { Shell(nav, games, go = { nav = it }) }
                    }
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

/** What was shared, kept with the screen: the game's name and its Steam id. */
private val SharedSaver = listSaver<Shared?, String>(
    save = { s -> if (s == null) emptyList() else listOf(s.name, s.steam.toString()) },
    restore = { l -> if (l.size < 2) null else Shared(l[0], l[1].toIntOrNull() ?: 0) },
)

/** The time "now" for what a screen says (as of, days away): the phone's clock, a fixed one in the screenshot tests. */
val LocalNow = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** The game named in what was last shared into the app, for Add (null: nothing shared, or it named no game). */
val LocalShared = staticCompositionLocalOf<Shared?> { null }

/** An indication that draws nothing: a tap shows only its result. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}
