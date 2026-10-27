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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val prefs = LocalPrefs.current
    val calm = LocalCalm.current
    val guard = remember { SwipeGuard() }
    val view = prefs.viewOf(nav.tab, nav.views)
    // the tab this screen showed last time it was drawn: a different one now means a tab switch
    val last = remember { arrayOf(nav.tab) }
    val from = last[0]
    val switched = from != nav.tab
    var wipeFor by remember { mutableStateOf<String?>(null) }
    SideEffect {
        if (switched && !calm && prefs.tabFade == TabFade.Slash) wipeFor = nav.tab
        last[0] = nav.tab
    }
    val entrance = when {
        calm -> null
        prefs.tabFade == TabFade.Slash -> Entrance.Slash
        prefs.tabFade == TabFade.Fade -> Entrance.Cut
        // Instant still slides 26 by tab order (the web's v110 slide); a view change inside a tab doesn't
        switched -> if (TAB_ORDER.indexOf(nav.tab) >= TAB_ORDER.indexOf(from)) Entrance.Right else Entrance.Left
        else -> null
    }
    // one finger walks the open tab's views (none past either end), two fingers or a tab without views the tabs
    val swipe by rememberUpdatedState { two: Boolean, dir: Int ->
        val set = prefs.views(nav.tab).map { it.first }
        if (nav.overlay == null) {
            if (!two && set.isNotEmpty()) {
                set.getOrNull(set.indexOf(view) + dir)?.let { go(nav.view(nav.tab, it)) }
            } else {
                TAB_ORDER.getOrNull(TAB_ORDER.indexOf(nav.tab) + dir)?.let { go(nav.go(it)) }
            }
        }
    }
    CompositionLocalProvider(LocalSwipeGuard provides guard) {
        BoxWithConstraints(Modifier.fillMaxSize().swipes(guard) { two, dir -> swipe(two, dir) }) {
            val w = maxWidth
            val wide = w >= RAIL_FROM
            val pick = { t: String -> go(nav.go(t)) }
            val pickView = { v: String -> go(nav.view(nav.tab, v)) }
            val settings = { go(nav.open("settings")) }
            holder.SaveableStateProvider(nav.tab) {
                Page(w) {
                    if (!wide) TabBar(nav.tab, pick, settings)
                    // Games swaps its whole page per view (the web's four panels); Plan and Taste redraw in place
                    val key = nav.tab + "/" + if (nav.tab == "games") view else ""
                    Column(Modifier.entrance(key, entrance)) {
                        TabPage(nav.tab, view, games, wide, w, askCode = { go(nav.open("code")) }, pickView = pickView)
                    }
                }
            }
            if (wide) Rail(nav.tab, pick, settings, Modifier.align(Alignment.CenterEnd))
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
            if (nav.overlay == "settings") {
                BackHandler { go(nav.back()) }
                SettingsSheet(games, wide, close = { go(nav.back()) }, openProbe = { go(nav.open("probe")) }, askCode = { go(nav.open("code")) })
            }
            wipeFor?.let { t ->
                key(t) { SlashWipe(Web.TABS.firstOrNull { it.key == t }?.label ?: t, t.hashCode()) { if (wipeFor == t) wipeFor = null } }
            }
        }
    }
}

/** The tabs in the web app's order (`TABS`), for the swipes and the slide's direction. */
private val TAB_ORDER = Web.TABS.map { it.key }

/**
 * The web's touchend swipe (index.html 11003): from the first finger's start to where it lifts, at least 70 across and
 * at most 45 up or down. Only watches (nothing is consumed, scrolling and taps work as before); a touch that started on
 * something scrolling sideways is left alone.
 */
private fun Modifier.swipes(guard: SwipeGuard, act: (two: Boolean, dir: Int) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // cleared here, before the touched things see it: a side scroller sets it in this same pass
        guard.inScroller = false
        var end = down.position
        var two = false
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.count { it.pressed } > 1) two = true
            e.changes.firstOrNull { it.id == down.id }?.let { end = it.position }
            if (e.changes.none { it.pressed }) break
        }
        if (guard.inScroller) return@awaitEachGesture
        val dx = end.x - down.position.x
        val dy = end.y - down.position.y
        if (abs(dx) < 70.dp.toPx() || abs(dy) > 45.dp.toPx()) return@awaitEachGesture
        act(two, if (dx < 0) 1 else -1)
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
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = if (home) 0.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        for (t in Web.TABS) {
            val on = t.key == tab
            Box(
                Modifier
                    .then(if (on) Modifier.size(66.dp, if (home) 70.dp else 62.dp) else Modifier.size(52.dp))
                    .then(if (on && home) Modifier.notch(Pn.pan) else Modifier)
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
                    if (on) BasicText(t.label, style = Type.tab, softWrap = false, maxLines = 1)
                }
            }
        }
        Box(
            Modifier.size(52.dp).clickable(role = Role.Button, onClick = settings).semantics { contentDescription = "Settings" },
            contentAlignment = Alignment.Center,
        ) { PnIcon("settings", Pn.mute, 21.dp, null) }
    }
}

/** The two 20dp inner corners beside Home's open tab that join it to the panel below (`::before` / `::after`). */
private fun Modifier.notch(fill: Color) = drawBehind {
    val r = 20.dp.toPx()
    val y = size.height - r
    for (left in listOf(true, false)) {
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
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
            .padding(12.dp)
            .fillMaxHeight()
            .width(72.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Pn.ink, shape)
            .border(1.dp, Pn.edge, shape)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Box(Modifier.size(48.dp, 44.dp).background(if (on) look.volt else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    PnIcon(t.key, if (on) Pn.void else Pn.mute, 20.dp, null)
                }
            }
        }
        Spacer(Modifier.weight(1f))
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
