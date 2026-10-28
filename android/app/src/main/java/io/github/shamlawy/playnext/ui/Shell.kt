package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlin.math.abs

/** Below this width the tab bar sits at the top of the page; from it Nexi's rail replaces it (the web app's 620px). */
val RAIL_FROM = 620.dp

/**
 * The tabs: the page in the web app's `.wrap` (max 880 wide, centred), with the tab bar at its top when folded, or the
 * rail on the right when unfolded. Each tab keeps its own scroll spot, also across a fold or unfold. A tab with views
 * (Games, Plan, Taste) has the view pill floating at the foot; a sideways swipe with one finger walks the views, with
 * two the tabs. Switching tabs plays your Switching tabs choice (the slash by default), never when calm.
 */
@Composable
fun Shell(nav: Nav, games: Games.State, go: (Nav) -> Unit) {
    val holder = rememberSaveableStateHolder()
    // each open step's place (Settings, game pages) by its Nav.key, and which keys it holds: saved too, so a step closed
    // after Android recreated the app, or while the 120 Hz test covered the tabs, still forgets its own
    val pages = rememberSaveableStateHolder()
    val pagesKept = rememberSaveable(saver = listSaver<MutableSet<String>, String>({ it.toList() }, { it.toMutableSet() })) { mutableSetOf() }
    val prefs = LocalPrefs.current
    val calm = LocalCalm.current
    val guard = remember { SwipeGuard() }
    val view = prefs.viewOf(nav.tab, nav.views)
    // the tab drawn last time (none before the first draw), read while composing: a switch shows from its very first
    // frame (the slab over the new tab, the page at the start of its entrance), never a frame later from an effect
    val shown = remember { arrayOf<String?>(null) }
    val from = shown[0]
    SideEffect { shown[0] = nav.tab }
    // Games swaps its whole page per view (the web's four panels); Plan and Taste redraw in place
    val page = nav.tab + "/" + if (nav.tab == "games") view else ""
    // made here, above the tabs' saved state (a new tab starts its own fresh, so a run kept in there never played)
    val enter = remember(page) {
        Enter(
            when {
                from == null || calm -> null
                prefs.tabFade == TabFade.Slash -> Entrance.Slash
                prefs.tabFade == TabFade.Fade -> Entrance.Cut
                // Instant still slides 26 by tab order (the web's v110 slide); a view change inside a tab doesn't
                from != nav.tab -> if (TAB_ORDER.indexOf(nav.tab) >= TAB_ORDER.indexOf(from)) Entrance.Right else Entrance.Left
                else -> null
            },
        )
    }
    LaunchedEffect(enter) { enter.play() }
    val wipe = remember(nav.tab) { from != null && from != nav.tab && !calm && prefs.tabFade == TabFade.Slash }
    var wiped by remember(nav.tab) { mutableStateOf(false) }
    val here = nav.tab + (view?.let { "/$it" } ?: "")
    // one finger walks the open tab's views (none past either end), two fingers or a tab without views the tabs
    val swipe by rememberUpdatedState { two: Boolean, dir: Int ->
        val set = prefs.views(nav.tab).map { it.first }
        if (nav.overlay == null) {
            val to = if (!two && set.isNotEmpty()) set.getOrNull(set.indexOf(view) + dir)?.let { nav.view(nav.tab, it) }
            else TAB_ORDER.getOrNull(TAB_ORDER.indexOf(nav.tab) + dir)?.let { nav.go(it) }
            if (to != null) {
                Sounds.play(Sfx.Swipe)
                Crumbs.add("swipe " + (if (to.tab != nav.tab) "tabs" else "views") + " on $here")
                go(to)
            }
        }
    }
    // where the last finger came down (a game's page grows from it), and the cover a page was opened from: both for the
    // one step they opened, so a page a link opens later never replays them
    val down = remember { arrayOf(Offset.Unspecified) }
    var launch by remember { mutableStateOf<Launch?>(null) }
    // the game page that's shrinking away (its step's key): the rail is over it, so a tap there takes it off first
    var shutting by remember { mutableStateOf<String?>(null) }
    // a tap that opens or closes something settles the screen for a moment: a quick second tap (a double tap) lands on
    // nothing, instead of closing what the first opened or opening what it closed (a game page closing after its shrink
    // needs none: it holds every touch while it shrinks)
    val step = { n: Nav ->
        if (n.stack != nav.stack) guard.settle = guard.lastUp
        go(n)
    }
    val openGame by rememberUpdatedState { id: String, from: FlyFrom? ->
        Sounds.play(Sfx.Open)
        Crumbs.tap(if (from != null) "cover" else "game", here)
        val n = nav.open("game:$id")
        launch = Launch(n.key(n.stack.lastIndex), down[0], from)
        step(n)
    }
    val opener = remember { { id: String, from: FlyFrom? -> openGame(id, from) } }
    CompositionLocalProvider(LocalSwipeGuard provides guard, LocalOpenGame provides opener) {
        BoxWithConstraints(Modifier.fillMaxSize().swipes(guard, { down[0] = it }) { two, dir -> swipe(two, dir) }) {
            val w = maxWidth
            val wide = w >= RAIL_FROM
            val pick = { t: String ->
                if (t != nav.tab) Sounds.play(Sfx.Tap)
                Crumbs.tap("tab", here)
                go(nav.go(t))
            }
            val pickView = { v: String ->
                Sounds.play(Sfx.Tap)
                Crumbs.tap("view", here)
                go(nav.view(nav.tab, v))
            }
            val settings = {
                Sounds.play(Sfx.Open)
                Crumbs.tap("settings", here)
                // over a page that's closing: it goes first, so Settings never opens under a page about to close itself
                val i = shutting?.let { k -> nav.stack.indices.firstOrNull { nav.key(it) == k } }
                step((if (i != null) nav.closeAt(i) else nav).open("settings"))
            }
            holder.SaveableStateProvider(nav.tab) {
                Page(w) {
                    if (!wide) TabBar(nav.tab, pick, settings)
                    Column(Modifier.entrance(enter)) {
                        TabPage(nav.tab, view, games, wide, w, askCode = { step(nav.open("code")) }, pickView = pickView)
                    }
                }
            }
            val views = prefs.views(nav.tab)
            if (prefs.pill && view != null && views.isNotEmpty()) {
                ViewPill(
                    views, view, w, pickView,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 18.dp)
                        // unfolded the web's pill sits 13 right of the middle (`html.rail.railoff .vpill`)
                        .offset(x = if (wide) 13.dp else 0.dp),
                )
            }
            // each step keeps its place while it's open; a closed step forgets its own, so opening it again starts fresh
            val keys = nav.stack.indices.map { nav.key(it) }
            LaunchedEffect(keys) {
                (pagesKept - keys.toSet()).forEach { pages.removeState(it) }
                pagesKept.retainAll(keys.toSet())
            }
            // Settings stays under what you open from it, so back finds it where you left it
            val s = nav.stack.lastIndexOf("settings")
            if (s >= 0) {
                val k = keys[s]
                pagesKept += k
                BackHandler(enabled = nav.overlay == "settings") { go(nav.closeAt(s)) }
                key(k) {
                    pages.SaveableStateProvider(k) {
                        SettingsSheet(games, wide, close = { step(nav.closeAt(s)) }, openProbe = { step(nav.open("probe")) }, askCode = { step(nav.open("code")) })
                    }
                }
            }
            // the newest game page, when nothing is open over it; one you go back to comes back as you left it (scroll
            // spot, no second opening)
            val at = nav.stack.indexOfLast { it.startsWith("game:") }
            val game = if (at <= s) null else nav.stack[at].removePrefix("game:").let { id -> (games as? Games.State.Ready)?.lib?.games?.firstOrNull { it.id == id } }
            if (game != null) {
                val k = keys[at]
                pagesKept += k
                val l = launch?.takeIf { it.key == k }
                key(k) { pages.SaveableStateProvider(k) { GamePage(game, l?.tap ?: Offset.Unspecified, l?.from?.takeIf { it.g.id == game.id }, wide, shrinking = { shutting = k }, close = { go(nav.closeAt(at)) }) } }
            }
            // unfolded, the rail stays over Settings and game pages, as on the web (`#hrail`): your tabs are always a
            // tap away
            if (wide) Rail(nav.tab, pick, settings, Modifier.align(Alignment.CenterEnd))
            if (wipe && !wiped) {
                key(nav.tab) { SlashWipe(Web.TABS.firstOrNull { it.key == nav.tab }?.label ?: nav.tab, nav.tab.hashCode()) { wiped = true } }
            }
        }
    }
}

/** A game page's opening: the step it opened ([Nav.key]), where the finger was and the cover it flies from. */
private class Launch(val key: String, val tap: Offset, val from: FlyFrom?)

/** How long a change of what's open keeps the next tap off (Android's own double-tap window is 300ms). */
private const val SETTLE_MS = 250L

/** The tabs in the web app's order (`TABS`), for the swipes and the slide's direction. */
private val TAB_ORDER = Web.TABS.map { it.key }

/**
 * The web's touchend swipe (index.html 11003): from the first finger's start to where it lifts, at least 70 across and
 * at most 45 up or down. It watches (scrolling works as before) until the lift; a lift that makes a swipe is taken here,
 * before the touched things see it, so the button the finger slid across isn't tapped too (a browser drops that click).
 * A touch that started on something scrolling sideways is left alone.
 */
private fun Modifier.swipes(guard: SwipeGuard, touched: (Offset) -> Unit, act: (two: Boolean, dir: Int) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touched(down.position)
        // cleared here, before the touched things see it: a side scroller sets it in this same pass
        guard.inScroller = false
        // a touch right after what's open changed (a double tap's second): its lift is taken here, so it taps nothing
        val settling = down.uptimeMillis - guard.settle < SETTLE_MS
        var end = down.position
        var two = false
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.count { it.pressed } > 1) two = true
            e.changes.firstOrNull { it.id == down.id }?.let { end = it.position }
            if (e.changes.any { it.pressed }) continue
            // the lift that may have opened or closed something: a change it makes settles from here
            guard.lastUp = e.changes.maxOf { it.uptimeMillis }
            if (settling) {
                e.changes.forEach { it.consume() }
                break
            }
            val dx = end.x - down.position.x
            val dy = end.y - down.position.y
            if (!guard.inScroller && abs(dx) >= 70.dp.toPx() && abs(dy) <= 45.dp.toPx()) {
                e.changes.forEach { it.consume() }
                act(two, if (dx < 0) 1 else -1)
            }
            break
        }
    }
}

/**
 * `.wrap`: padding 20/16/40 (26/22/60 from 700 wide), at most 880 wide and centred; from 620 to 1099 wide it leaves
 * 96 on the right for the rail, which floats over the wider screens. Under the status bar and the cutout, edge to edge.
 */
@Composable
private fun Page(w: Dp, content: @Composable () -> Unit) {
    val big = w >= 700.dp
    val side = if (big) 22.dp else 16.dp
    val railRoom = if (w >= RAIL_FROM && w < 1100.dp) 96.dp else side
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val dir = LocalLayoutDirection.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                PaddingValues(
                    start = bars.calculateStartPadding(dir) + side,
                    end = bars.calculateEndPadding(dir) + railRoom,
                    top = bars.calculateTopPadding() + if (big) 26.dp else 20.dp,
                    bottom = bars.calculateBottomPadding() + if (big) 60.dp else 40.dp,
                ),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 880.dp).fillMaxWidth()) { content() }
    }
}

/**
 * The folded tab bar (index.html 15583): five tabs and Settings as icons, 52 square, mute; the open one a 66x62 tile on
 * `--u-pan` in the accent with its name in white. On Home the tile is 70 tall and runs into the Home panel below through
 * two rounded inner corners (the notch). It scrolls away with the page, as on the web.
 */
@Composable
fun TabBar(tab: String, pick: (String) -> Unit, settings: () -> Unit) {
    val look = LocalLook.current
    val home = tab == "home"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the open tile's left inner corner only where it clears the Home panel's 28 round corner by its own 20 (narrow, it
        // would hang off that curve as a thin spike): the row is centred, 66 open + 52 each other + Settings, 2 apart
        // the closed tabs and Settings are 52 square while the row fits (from 336 wide); narrower, they share what's
        // left, so Settings never gets squashed off the end
        val shut = ((maxWidth - 66.dp - 2.dp * Web.TABS.size) / Web.TABS.size).coerceIn(40.dp, 52.dp)
        val before = Web.TABS.indexOfFirst { it.key == tab }.coerceAtLeast(0)
        val left = (maxWidth - (66.dp + shut * Web.TABS.size + 2.dp * Web.TABS.size)) / 2 + (shut + 2.dp) * before >= 48.dp
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = if (home) 0.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
        ) {
            for (t in Web.TABS) {
                val on = t.key == tab
                Box(
                    Modifier
                        .then(if (on) Modifier.size(66.dp, if (home) 70.dp else 62.dp) else Modifier.size(shut, 52.dp))
                        .then(if (on && home) Modifier.notch(Pn.pan, left) else Modifier)
                        .background(
                            if (on) Pn.pan else Color.Transparent,
                            if (on && home) RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp) else RoundedCornerShape(if (on) 22.dp else 18.dp),
                        )
                        .clickable(role = Role.Tab) { pick(t.key) }
                        .semantics { selected = on; contentDescription = t.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        PnIcon(t.key, if (on) look.volt else Pn.mute, 21.dp, null)
                        if (on) BasicText(t.label, style = Type.tab.fixed(), softWrap = false, maxLines = 1)
                    }
                }
            }
            Box(
                Modifier.size(shut, 52.dp).clickable(role = Role.Button, onClick = settings).semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) { PnIcon("settings", Pn.mute, 21.dp, null) }
        }
    }
}

/** The two 20dp inner corners beside Home's open tab that join it to the panel below (`::before` / `::after`). */
private fun Modifier.notch(fill: Color, withLeft: Boolean) = drawBehind {
    val r = 20.dp.toPx()
    val y = size.height - r
    for (left in listOf(true, false)) {
        if (left && !withLeft) continue
        val x = if (left) -r else size.width
        val square = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset(x, y), Size(r, r))) }
        val hole = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(if (left) x - r else x, y - r), Size(2 * r, 2 * r)))
        }
        drawPath(Path.combine(PathOperation.Difference, square, hole), fill)
    }
}

/**
 * The unfolded rail (`#hrail`, index.html 2132, 2250): 72 wide, 12 in from the edges, round 30, `--ink` with an
 * `--edge` line: the four tabs, a line, Add and Settings. The dice, the live queue and hiding it come with Home's
 * phase (3); Nexi's seat with the Queue's (1).
 */
@Composable
fun Rail(tab: String, pick: (String) -> Unit, settings: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(30.dp)
    BoxWithConstraints(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
            .padding(12.dp)
            .fillMaxHeight()
            .width(72.dp),
    ) {
        // a short window (folded on its side, a split screen) brings the buttons closer and then lets the rail scroll,
        // so Settings is never squashed to a sliver
        val roomy = maxHeight >= RAIL_TALL
        RailColumn(tab, pick, settings, look.volt, shape, roomy)
    }
}

/** The rail's height with its usual gaps: four tabs, the gap, a line, Add and Settings. */
private val RAIL_TALL = 14.dp * 2 + 48.dp * 6 + 1.dp + 10.dp * 7

@Composable
private fun RailColumn(tab: String, pick: (String) -> Unit, settings: () -> Unit, volt: Color, shape: RoundedCornerShape, roomy: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .shadow(20.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
            .background(Pn.ink, shape)
            .border(1.dp, Pn.edge, shape)
            .then(if (roomy) Modifier else Modifier.clip(shape).verticalScroll(rememberScrollState()))
            .padding(vertical = if (roomy) 14.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (roomy) 10.dp else 4.dp),
    ) {
        for (t in Web.TABS.filter { it.key != "add" }) {
            val on = t.key == tab
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Tab) { pick(t.key) }
                    .semantics { selected = on; contentDescription = t.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(48.dp, 44.dp).background(if (on) volt else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    PnIcon(t.key, if (on) Pn.void else Pn.mute, 20.dp, null)
                }
            }
        }
        if (roomy) Spacer(Modifier.weight(1f))
        Box(Modifier.size(36.dp, 1.dp).background(Pn.edge))
        RailButton("add", "Add a game", on = tab == "add") { pick("add") }
        RailButton("settings", "Settings", on = false, onClick = settings)
    }
}

@Composable
private fun RailButton(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).background(if (on) look.volt else Pn.steel, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            PnIcon(icon, if (on) Pn.void else Pn.ice, 22.dp, null)
        }
    }
}
