package io.github.shamlawy.playnext.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.data.Games
import kotlinx.coroutines.delay
import io.github.shamlawy.playnext.data.Edit
import io.github.shamlawy.playnext.data.Drift
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.data.Score
import kotlinx.coroutines.launch
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlin.math.abs

/** Below this width the tab bar sits at the top of the page; from it Nexi's rail replaces it (the web app's 620px). */
val RAIL_FROM = 620.dp

/** The rail's width: wide enough for its tab's views as words (the owner's Option B, Nov 6; the web's is 72). */
val RAIL_W = 112.dp

/** From the screen's right edge to the rail's inner side (12 of air on its right). */
val RAIL_OUT = RAIL_W + 12.dp

/**
 * The tabs: the page in the web app's `.wrap` (max 880 wide, centred), with the tab bar at its top when folded, or the
 * rail on the right when unfolded. Each tab keeps its own scroll spot, also across a fold or unfold. A tab with views
 * (Games, Plan, Taste) lists them in the rail under its icon unfolded, and as a sticky bar at the top of the page folded
 * (the owner's Option B, Nov 6: nothing at the foot, where Samsung's taskbar sits); a sideways swipe with one finger walks
 * the views, with two the tabs. Pages, the rail and the sheets end above the taskbar ([Taskbar]). Switching tabs plays your Switching tabs choice (the slash by default), never when calm.
 */
@Composable
fun Shell(nav: Nav, games: Games.State, queue: QueueHold = remember { QueueHold() }, go: (Nav) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val clock = LocalNow.current
    // each open step's place (Settings, game pages) by its Nav.key, and which keys it holds: saved too, so a step closed
    // after Android recreated the app, or while the 120 Hz test covered the tabs, still forgets its own
    val pages = rememberSaveableStateHolder()
    val pagesKept = rememberSaveable(saver = listSaver<MutableSet<String>, String>({ it.toList() }, { it.toMutableSet() })) { mutableSetOf() }
    val prefs = LocalPrefs.current
    val calm = LocalCalm.current
    val guard = remember { SwipeGuard() }
    // where the rail and floating Nexi are (yours from the backup until you move them here) and Nexi himself
    val spot = remember(prefs.railHide, prefs.railLeft, prefs.bubY) { RailSpot(prefs) }
    val nxScope = rememberCoroutineScope()
    val nx = remember { NexiState(nxScope) }
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
    // a game page has fully grown over everything: what's under it (the tab, the pill, Settings) stops drawing until it
    // starts to close (read only while drawing, so it changes no layout and keeps every scroll spot)
    var covered by remember { mutableStateOf(false) }
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
    val openCustomise by rememberUpdatedState { scope: String ->
        Sounds.play(Sfx.Open)
        Crumbs.tap("customise", here)
        step(nav.open("customise:$scope"))
    }
    val customiser = remember { { scope: String -> openCustomise(scope) } }
    // a game's Edit (the page's pencil, the card back's): a step over what's open, so back returns there
    val openEdit by rememberUpdatedState { id: String ->
        Sounds.play(Sfx.Open)
        step(nav.open("edit:$id"))
    }
    // the line Edit leaves at the foot once it closes ("Saved")
    var edited by remember { mutableStateOf<PageToast?>(null) }
    LaunchedEffect(edited) { val t = edited ?: return@LaunchedEffect; delay(2_400); if (edited === t) edited = null }
    // the Score sheet and Where was I's note sheet (piece 3): steps over the page, as Edit is
    val openScore by rememberUpdatedState { id: String -> step(nav.open("score:$id")) }
    val openNote by rememberUpdatedState { id: String -> step(nav.open("note:$id")) }
    val openShort by rememberUpdatedState { id: String -> step(nav.open("short:$id")) }
    val editActs = remember {
        EditActs(
            score = { id -> openScore(id) },
            note = { id -> openNote(id) },
            short = { id -> openShort(id) },
            finished = { g ->
                Moment.finished(g)
                // straight on to the verdict while it's fresh (`fnDone`: the Score sheet 1200 ms later)
                nxScope.launch {
                    delay(1_200)
                    openScore(g.id)
                }
            },
            open = { id -> openEdit(id) },
            say = { t -> edited = PageToast(t, (edited?.n ?: 0) + 1) },
            comp = { g ->
                Sounds.play(Sfx.Tap)
                Games.change(Edit.comp(g, !g.comp) { Games.practiced(it) })
                val t = if (!g.comp) g.title + " is competitive — immune ∞" else g.title + " counts again"
                queue.made?.say(t) ?: run { edited = PageToast(t, (edited?.n ?: 0) + 1) }
            },
        )
    }
    // the Queue's tab and order stay yours while the app is open (the web saves `S.qFilter`), held by the root ([queue])
    // so the 120 Hz test or the restore screen never resets them, and the lists sheet can show a list in it too
    // Your lists and the Game shuffler, from Sort's menu
    val openSheet = { o: String -> step(nav.open(o)) }
    val rail = spot.at
    CompositionLocalProvider(LocalSwipeGuard provides guard, LocalOpenGame provides opener, LocalEditActs provides editActs, LocalCustomise provides customiser, LocalRailAt provides rail) {
        BoxWithConstraints(Modifier.fillMaxSize().swipes(guard, { down[0] = it }) { two, dir -> swipe(two, dir) }.watchFinger(nx, rail.shown, calm, nxScope)) {
            val w = maxWidth
            val wide = w >= RAIL_FROM
            // the room kept for Samsung's taskbar at the foot (Settings › Taskbar room)
            val room = Taskbar.room(Taskbar.choice, reportedBottom(), wide)
            // one list per tab and prefs, so the rail and the view bar (which compare it by identity) skip when nothing changed
            val views = remember(prefs, nav.tab) { prefs.views(nav.tab) }
            // folded, the views sit in a sticky bar at the top of the page
            val topBar = !wide && view != null && views.isNotEmpty()
            val pick = { t: String ->
                if (t != nav.tab) Sounds.play(Sfx.Tap)
                Crumbs.tap("tab", here)
                go(nav.go(t))
            }
            val pickView = { v: String ->
                // over a game page or Settings (the rail stays in sight), a view closes them like a tab does; the lit
                // view with nothing over it is a no-op
                if (v != view || nav.overlay != null) {
                    Sounds.play(Sfx.Tap)
                    Crumbs.tap("view", here)
                    go(nav.go(nav.tab).view(nav.tab, v))
                }
            }
            val settings = {
                Sounds.play(Sfx.Open)
                Crumbs.tap("settings", here)
                // over a page that's closing: it goes first, so Settings never opens under a page about to close itself
                val i = shutting?.let { k -> nav.stack.indices.firstOrNull { nav.key(it) == k } }
                step((if (i != null) nav.closeAt(i) else nav).open("settings"))
            }
            val viewBar: LazyListScope.(Modifier) -> Unit = { cap ->
                if (topBar && view != null) stickyHeader("views") { ViewBarBand(cap) { ViewBar(views, view, pickView) } }
            }
            CompositionLocalProvider(LocalBottomRoom provides room) {
            holder.SaveableStateProvider(nav.tab) {
                val ready = games as? Games.State.Ready
                if (nav.tab == "games" && view == "queue" && ready != null) {
                    // the Queue's rows are many: a lazy page draws only the ones on screen
                    val qpick = queue.pick(ready.lib)
                    val qv = rememberQueueView(ready.lib, qpick, ready.cards?.queue)
                    val folded = w <= 500.dp
                    // Character select's covers per line: the page's width inside its gutters and the rail's room
                    val qcols = selCols(minOf(880.dp, pageRoom(w, rail)))
                    val qcolsNow by rememberUpdatedState(qcols)
                    val qlist = rememberLazyListState()
                    val sel = ready.lib.charSelect()
                    // back closes what the Queue has open before it leaves the app (Phase 1 check A1): a row swiped to
                    // its Delete, then a card you opened (the one open at start isn't yours: back leaves from the tab)
                    val f = qpick.focus
                    BackHandler(enabled = nav.overlay == null && (qpick.swiped != null || f != null && qpick.openIn(qv.shown) == f)) {
                        if (qpick.swiped != null) qpick.swiped = null
                        else if (f != null) {
                            Sounds.play(Sfx.CardShut)
                            Crumbs.add("back closed a card on $here")
                            // as the card's own tap: in Character select it closes into the panel
                            if (sel) { qpick.sel = f; qpick.close() } else qpick.close(f)
                        }
                    }
                    // Character select (M78): a row swiped open stays shut behind the covers, and the pick follows the
                    // tab: one the tab doesn't show gives way to its first game, as the web's `qfSel` (index.html 8954)
                    LaunchedEffect(qpick, sel) { if (sel) qpick.swiped = null }
                    LaunchedEffect(qpick, sel, qv.shown) {
                        if (sel && qv.shown.isNotEmpty() && qv.shown.none { it.id == qpick.sel }) qpick.sel = qv.shown[0].id
                    }
                    // Shuffle's pick: once the list shows it (its tab may have just gone back to All), bring it to the middle
                    val qvNow by rememberUpdatedState(qv)
                    // then the pick is done: cleared once its ring has run, or as soon as the Queue goes, so coming back
                    // never scrolls to an old pick again and its row doesn't light up a second time
                    LaunchedEffect(qpick, qlist) {
                        snapshotFlow { qpick.lit }.collectLatest { l ->
                            if (l != null) try {
                                withFrameNanos { }
                                if (qvNow.q.lib.charSelect()) qlist.centerCover(l.id, { qvNow }, qcolsNow, calm)
                                else qlist.centerRow(l.id, { qvNow.shown }, calm)
                                kotlinx.coroutines.delay(LIT_MS.toLong())
                            } finally {
                                if (qpick.lit === l) qpick.lit = null
                            }
                        }
                    }
                    LazyPage(w, rail, Modifier.graphicsLayer { alpha = if (covered) 0f else 1f }, qlist, room, topBar) { cap ->
                        if (!wide) item("tabbar") { Box(cap) { TabBar(nav.tab, pick, settings) } }
                        viewBar(cap)
                        item("top") { Column(cap.entrance(enter)) { TabTop(nav.tab, view, games, wide, w) } }
                        queueItems(qv, qpick, qlist, folded, qcols, calm, cap.entrance(enter), openSheet) { id -> opener(id, null) }
                    }
                } else if (view != null && views.isNotEmpty()) {
                    // a page with views is a lazy list at both widths (folded, the sticky view bar is its sticky header),
                    // so folding or unfolding mid-scroll keeps the scroll spot: the same state in the same place
                    LazyPage(w, rail, Modifier.graphicsLayer { alpha = if (covered) 0f else 1f }, rememberLazyListState(), room, topBar) { cap ->
                        if (!wide) item("tabbar") { Box(cap) { TabBar(nav.tab, pick, settings) } }
                        viewBar(cap)
                        item("page") {
                            Column(cap.entrance(enter)) { TabPage(nav.tab, view, games, wide, w, askCode = { step(nav.open("code")) }) }
                        }
                    }
                } else {
                    Page(w, rail, Modifier.graphicsLayer { alpha = if (covered) 0f else 1f }, room) {
                        if (!wide) TabBar(nav.tab, pick, settings)
                        Column(Modifier.entrance(enter)) {
                            TabPage(nav.tab, view, games, wide, w, askCode = { step(nav.open("code")) })
                        }
                    }
                }
            }
            // the Queue's notes sit over the middle of the page, just above the taskbar
            val noteAt = Modifier
                .graphicsLayer { alpha = if (covered) 0f else 1f }
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = overNavBar(room) + 18.dp)
                .offset(x = if (wide) -pageShift(w, rail) else 0.dp)
            // the Queue's Undo bar after a delete, above the view pill (and Shuffle's "Tonight: …" there when none shows)
            val qp = queue.made
            // their time runs wherever you are, so they never come back later ([QueueClock])
            if (qp != null) QueueClock(qp)
            if (nav.tab == "games" && view == "queue" && qp != null) {
                QueueNote(qp, noteAt)
                QueueUndo(qp, noteAt)
            }
            // each step keeps its place while it's open; a closed step forgets its own, so opening it again starts fresh
            val keys = nav.stack.indices.map { nav.key(it) }
            LaunchedEffect(keys) {
                (pagesKept - keys.toSet()).forEach { pages.removeState(it) }
                pagesKept.retainAll(keys.toSet())
            }
            // Settings (or a screen's Customise, your lists or the Game shuffler) stays under what you open from it, so
            // back finds it where you left it
            val s = nav.stack.indexOfLast { it == "settings" || it == "lists" || it == "shuffle" || it.startsWith("customise:") }
            if (s >= 0) {
                val k = keys[s]
                val sheet = nav.stack[s]
                pagesKept += k
                BackHandler(enabled = nav.overlay == sheet) { go(nav.closeAt(s)) }
                key(k) {
                    pages.SaveableStateProvider(k) {
                        val m = Modifier.graphicsLayer { alpha = if (covered) 0f else 1f }
                        if (sheet == "settings") {
                            SettingsSheet(
                                m, games, wide,
                                close = { step(nav.closeAt(s)) }, openProbe = { step(nav.open("probe")) }, askCode = { step(nav.open("code")) },
                            )
                        } else if (sheet == "lists") {
                            ListsSheet(
                                m, games,
                                show = { t ->
                                    (games as? Games.State.Ready)?.let { queue.pick(it.lib).show(t) }
                                    step(nav.closeAt(s))
                                },
                                close = { step(nav.closeAt(s)) },
                                top = nav.overlay == sheet,
                            )
                        } else if (sheet == "shuffle") {
                            ShuffleDeck(m, games, queue.made, close = { step(nav.closeAt(s)) })
                        } else {
                            CustomiseSheet(m, sheet.removePrefix("customise:"), games, close = { step(nav.closeAt(s)) })
                        }
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
                val ready = games as Games.State.Ready
                val pv = rememberPageView(ready, queue.pick(ready.lib))
                key(k) {
                    pages.SaveableStateProvider(k) {
                        GamePage(
                            game, pv, l?.tap ?: Offset.Unspecified, l?.from?.takeIf { it.g.id == game.id }, wide,
                            shrinking = { shutting = k }, covers = { covered = it },
                            // a genre's tap (`gtagFilter`): the Queue shows that genre's games, with how many there are
                            toQueue = { t ->
                                val qp = queue.pick(ready.lib)
                                qp.show(t)
                                val c = pv.page.q.count(t)
                                qp.say("$c ${if (c == 1) "game" else "games"} tagged ${t.removePrefix("G:")} · tap All to see everything again")
                                go(nav.go("games").view("games", "queue"))
                            },
                            close = { go(nav.closeAt(at)) },
                        )
                    }
                }
            }
            // a game's Edit, over the page or the Queue it opened from (newer than both)
            val ed = nav.stack.indexOfLast { it.startsWith("edit:") }
            if (ed >= 0 && ed > at && ed > s) {
                val k = keys[ed]
                pagesKept += k
                val eid = nav.stack[ed].removePrefix("edit:")
                key(k) {
                    pages.SaveableStateProvider(k) {
                        EditSheet(
                            Modifier, games, eid,
                            close = { step(nav.closeAt(ed)) },
                            end = { e ->
                                val lib = (games as? Games.State.Ready)?.lib
                                when (e) {
                                    EditEnd.Saved -> {
                                        Sounds.play(Sfx.Add)
                                        step(nav.closeAt(ed))
                                        edited = PageToast("Saved", (edited?.n ?: 0) + 1)
                                    }
                                    // the Queue's practice delete, with its Undo (3-critic.md X3): it shows in the Queue
                                    is EditEnd.Removed -> {
                                        if (lib != null) queue.pick(lib).delete(lib, e.g)
                                        go(nav.go("games").view("games", "queue"))
                                    }
                                    is EditEnd.Unplayed -> {
                                        Sounds.play(Sfx.Add)
                                        Games.change(Edit.unplay(e.g, clock()) { Games.practiced(it) })
                                        if (lib != null) queue.pick(lib).say(e.g.title + " is back in the queue")
                                        go(nav.go("games").view("games", "queue"))
                                    }
                                }
                            },
                        )
                    }
                }
            }
            // the Score sheet and the note sheet, over the page they came from (newer than it, Edit and Settings)
            val sc = nav.stack.indexOfLast { it.startsWith("score:") || it.startsWith("note:") || it.startsWith("short:") }
            val scLib = (games as? Games.State.Ready)?.lib
            if (sc >= 0 && sc > at && sc > s && sc > ed && scLib != null) {
                val k = keys[sc]
                pagesKept += k
                val what = nav.stack[sc]
                val gid = what.substringAfter(':')
                val sg = scLib.games.firstOrNull { it.id == gid }
                BackHandler(enabled = nav.overlay == what) { step(nav.closeAt(sc)) }
                if (sg == null) {
                    LaunchedEffect(what) { step(nav.closeAt(sc)) }
                } else key(k) {
                    pages.SaveableStateProvider(k) {
                        if (what.startsWith("score:")) {
                            ScoreSheet(scLib, sg, (games as? Games.State.Ready)?.cards?.queue?.rk, clock, close = { step(nav.closeAt(sc)) }) { had, v, first ->
                                // scoring a game you hadn't marked played marks it: the stamp and the burst
                                if (first) Moment.finished(sg)
                                // where it landed, once the redraw has settled (`qScoreSet`: 3 s, 400 ms when calm)
                                if (v != null && v != had) nxScope.launch {
                                    delay(if (calm) 400 else 3_000)
                                    val lib2 = (Games.state.value as? Games.State.Ready)?.lib ?: return@launch
                                    val g2 = lib2.games.firstOrNull { it.id == sg.id } ?: return@launch
                                    val r = Score.rankOf(Rank(lib2, clock(), java.time.ZoneId.systemDefault()), g2) ?: return@launch
                                    edited = PageToast("${g2.title}: #$r · ${Rank.tierOf(v)} tier", (edited?.n ?: 0) + 1)
                                }
                            }
                        } else if (what.startsWith("short:")) {
                            val sq = (games as? Games.State.Ready)?.cards?.queue?.takeIf { it.lib === scLib }
                                ?: remember(scLib) { Queue(scLib, Rank(scLib, clock(), java.time.ZoneId.systemDefault()), clock(), java.time.ZoneId.systemDefault()) }
                            ShortSheet(sq, sg, close = { step(nav.closeAt(sc)) }, start = { x ->
                                // `[data-dwgo]`: it's playing now, and the drifting one remembers it
                                Crumbs.tap("drifting: start a short one", "short sheet")
                                Games.change(Drift.startShort(scLib, sg, x, clock()) { Games.practiced(it) })
                                Sounds.play(Sfx.Open)
                                step(nav.closeAt(sc))
                                edited = PageToast("${x.title} is in Playing now. Finish it and I'll bring you back to ${sg.title}.", (edited?.n ?: 0) + 1)
                            }) { x ->
                                step(nav.closeAt(sc))
                                nxScope.launch { delay(320); opener(x.id, null) }
                            }
                        } else {
                            NoteSheet(sg, clock, close = { step(nav.closeAt(sc)) }) {
                                step(nav.closeAt(sc))
                                edited = PageToast("Note saved — it'll be here next time", (edited?.n ?: 0) + 1)
                            }
                        }
                    }
                }
            }
            // the moment a game becomes played: over everything
            FinishMoment(calm)
            edited?.let { t -> PageNote(t, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = overNavBar(room) + 20.dp)) }
            // unfolded, the rail stays over Settings and game pages, as on the web (`#hrail`): your tabs are always a
            // tap away
            // Nexi sits at its foot (Settings › Extra features › Nexi sits in the rail), or floats when it's hidden
            val seated = !prefs.nxSeatOff
            if (wide) Rail(nav.tab, views, view, pick, pickView, settings, rail, seated, calm, Modifier.align(if (rail.left) Alignment.CenterStart else Alignment.CenterEnd))
            // the Game shuffler is his place to stay out of (`html.shuffling #pnbub`)
            if (wide && "shuffle" !in nav.stack) {
                NexiSeat(nx, spot, seated, w, maxHeight, room, (games as? Games.State.Ready)?.cards?.nexi, prefs, calm)
            }
            if (wipe && !wiped) {
                key(nav.tab) { SlashWipe(Web.TABS.firstOrNull { it.key == nav.tab }?.label ?: nav.tab, nav.tab.hashCode()) { wiped = true } }
            }
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
 * A page's room at its foot: the bars Android reports or the taskbar's [room], whichever is taller, plus 40 (60 from
 * 700 wide). The page itself runs to the foot of the screen, so what scrolls shows under the floating taskbar as it did
 * before android-v21 (the owner, Nov 6: "I hate the my apps have a black bar under it"); this padding only lets the
 * last card scroll clear of it.
 */
private fun foot(bars: PaddingValues, room: Dp, big: Boolean) = maxOf(bars.calculateBottomPadding(), room) + if (big) 60.dp else 40.dp

/** The page's side gutters: 16, or 22 from 700 wide. */
private fun side(w: Dp) = if (w >= 700.dp) 22.dp else 16.dp

/**
 * The page's room on the right: the rail's, from 620 wide until the 880 page's own margin clears it with the same 12 of
 * air ([RAIL_CLEAR], 1152: the 112 rail made 1100 too early; wider, it floats over the page's margin), while it's shown
 * on the right; else the gutter. [railStart] is the same on the left (the rail opens on Nexi's side, v221).
 */
internal fun railRoom(w: Dp, at: RailAt) = if (at.shown && !at.left && w >= RAIL_FROM && w < RAIL_CLEAR) RAIL_OUT + 12.dp else side(w)

internal fun railStart(w: Dp, at: RailAt) = if (at.shown && at.left && w >= RAIL_FROM && w < RAIL_CLEAR) RAIL_OUT + 12.dp else side(w)

/** From this width the centred 880 page leaves the rail and its 12 of air free on both sides. */
private val RAIL_CLEAR = 880.dp + (RAIL_OUT + 12.dp) * 2

/** How far the page's middle sits left of the screen's (the rail's room is wider than the gutter; right of it, the rail on the left). */
internal fun pageShift(w: Dp, at: RailAt) = (railRoom(w, at) - railStart(w, at)) / 2

/** The page's room between its gutters and the rail's room: what a sheet over it may fill without meeting the rail. */
internal fun pageRoom(w: Dp, at: RailAt) = w - railStart(w, at) - railRoom(w, at)

/** The rail as the screen's layout sees it ([RailSpot.at]): Settings, the shuffler and game pages keep clear of it. */
val LocalRailAt = androidx.compose.runtime.compositionLocalOf { RailAt() }

/** Floating Nexi's eyes follow every finger on the screen (`watch`, index.html 12043), only while he floats. */
private fun Modifier.watchFinger(nx: NexiState, railShown: Boolean, calm: Boolean, scope: kotlinx.coroutines.CoroutineScope) =
    if (railShown) this else pointerInput(nx, calm) {
        val px = NX_PX.toPx()
        val most = 5.dp.toPx()
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.firstOrNull { it.pressed }?.let { nx.lookAt(it.position, px, most, calm, scope) }
            }
        }
    }

private val NX_PX = 62.dp

/**
 * [Page] as a lazy list: the same paddings and 880 cap ([content] gets the cap as each item's modifier). With a sticky
 * view bar ([stick]) the list starts under the status bar, so the bar sticks just below it. It runs to the foot of the
 * screen, under the taskbar ([foot]).
 */
@Composable
private fun LazyPage(w: Dp, rail: RailAt, modifier: Modifier, state: LazyListState, room: Dp, stick: Boolean, content: LazyListScope.(Modifier) -> Unit) {
    val big = w >= 700.dp
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val dir = LocalLayoutDirection.current
    val cap = Modifier.widthIn(max = 880.dp).fillMaxWidth()
    LazyColumn(
        modifier
            .fillMaxSize()
            .then(if (stick) Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)) else Modifier),
        state = state,
        contentPadding = PaddingValues(
            start = bars.calculateStartPadding(dir) + railStart(w, rail),
            end = bars.calculateEndPadding(dir) + railRoom(w, rail),
            top = (if (stick) 0.dp else bars.calculateTopPadding()) + if (big) 26.dp else 20.dp,
            bottom = foot(bars, room, big),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content(cap) }
}

/**
 * The sticky view bar's band: the bar with a little air round it on the background's own piece ([backdropBand]: the
 * glow carries on behind it), fading out at its foot, so what scrolls under it never shows through behind the words.
 */
@Composable
private fun ViewBarBand(cap: Modifier, bar: @Composable () -> Unit) {
    Box(
        cap
            // the background's own piece (its glow too), not a flat strip of the base colour over the glow
            .backdropBand(.78f)
            .padding(top = 6.dp, bottom = 14.dp),
    ) { bar() }
}

/**
 * `.wrap`: padding 20/16/40 (26/22/60 from 700 wide), at most 880 wide and centred; from 620 wide to [RAIL_CLEAR] it leaves
 * room on the right for the rail, which floats over the wider screens. Under the status bar and the cutout, edge to
 * edge, under the taskbar too ([foot]).
 */
@Composable
private fun Page(w: Dp, rail: RailAt, modifier: Modifier, room: Dp, content: @Composable () -> Unit) {
    val big = w >= 700.dp
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val dir = LocalLayoutDirection.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                PaddingValues(
                    start = bars.calculateStartPadding(dir) + railStart(w, rail),
                    end = bars.calculateEndPadding(dir) + railRoom(w, rail),
                    top = bars.calculateTopPadding() + if (big) 26.dp else 20.dp,
                    bottom = foot(bars, room, big),
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
 * The unfolded rail (`#hrail`, index.html 2132, 2250): [RAIL_W] wide, 12 in from the edges, round 30, `--ink` with an
 * `--edge` line: the four tabs, a line, Add and Settings. Under the open tab's icon its views as words (the owner's
 * Option B, Nov 6, in place of the floating pill) in a tray, centred: the open one bright on a soft pill of your accent
 * ([RailViews]). It ends above the taskbar ([LocalBottomRoom]). With Nexi seated ([seat]) it keeps [SEAT_ROOM] at its
 * foot for him. Hidden ([RailAt.shown] false: one tap on him), it slides 130% off its side and fades (`#hrail`'s
 * transform .45s `--ease`, opacity .35s), taking no touches; it opens on his side ([RailAt.left], v221). The dice and the
 * live queue come with Home's phase (3).
 */
@Composable
fun Rail(
    tab: String,
    views: List<Pair<String, String>>,
    view: String?,
    pick: (String) -> Unit,
    pickView: (String) -> Unit,
    settings: () -> Unit,
    at: RailAt,
    seat: Boolean,
    calm: Boolean,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(30.dp)
    val d = LocalDensity.current
    val room = LocalBottomRoom.current
    // the bottom: the taskbar's room when that's more than the bars Android reports
    val bottom = maxOf(room, with(d) { WindowInsets.steady.getBottom(d).toDp() })
    val out by androidx.compose.animation.core.animateFloatAsState(if (at.shown) 0f else 1f, if (calm) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(450, easing = Ease.house), label = "rail out")
    val fade by androidx.compose.animation.core.animateFloatAsState(if (at.shown) 1f else 0f, if (calm) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "rail fade")
    val left = at.left
    BoxWithConstraints(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + if (left) WindowInsetsSides.Start else WindowInsetsSides.End))
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottom + 12.dp)
            .fillMaxHeight()
            .width(RAIL_W)
            .graphicsLayer {
                translationX = out * 1.3f * size.width * if (left) -1f else 1f
                alpha = fade
            }
            // hidden (or sliding away), its buttons take no touches and TalkBack doesn't find them
            .then(
                if (at.shown) Modifier
                else Modifier.clearAndSetSemantics { }.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } },
            ),
    ) {
        // a short window (folded on its side, a split screen) brings the buttons closer and then lets the rail scroll,
        // so Settings is never squashed to a sliver
        val shown = if (view != null) views else emptyList()
        // the views are one more item in the column, so one more 10 gap; Nexi's seat takes its room in place of the 14 at the foot
        val roomy = maxHeight >= RAIL_TALL + (if (seat) SEAT_ROOM - 14.dp else 0.dp) + if (shown.isEmpty()) 0.dp else RAIL_VIEW_H * shown.size + RAIL_TRAY_PAD * 2 + 10.dp
        RailColumn(tab, shown, view, pick, pickView, settings, look.volt, shape, roomy, if (seat) SEAT_ROOM else 0.dp)
    }
}

/** The rail's height with its usual gaps: four tabs, the gap, a line, Add and Settings. */
private val RAIL_TALL = 14.dp * 2 + 48.dp * 6 + 1.dp + 10.dp * 7

/** One view's line in the rail (a 48 touch). */
private val RAIL_VIEW_H = 48.dp

@Composable
private fun RailColumn(
    tab: String,
    views: List<Pair<String, String>>,
    view: String?,
    pick: (String) -> Unit,
    pickView: (String) -> Unit,
    settings: () -> Unit,
    volt: Color,
    shape: RoundedCornerShape,
    roomy: Boolean,
    seat: Dp,
) {
    Column(
        Modifier
            .fillMaxSize()
            .shadow(20.dp, shape, ambientColor = Pn.shade, spotColor = Pn.shade)
            .background(Pn.ink, shape)
            .border(1.dp, Pn.edge, shape)
            .clip(shape)
            // Nexi's seat stays clear of what scrolls in a short rail
            .padding(bottom = seat)
            .then(if (roomy) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(top = if (roomy) 14.dp else 8.dp, bottom = if (seat > 0.dp) 0.dp else if (roomy) 14.dp else 8.dp),
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
            if (on && views.isNotEmpty()) RailViews(views, view, volt, pickView)
        }
        if (roomy) Spacer(Modifier.weight(1f))
        Box(Modifier.size(36.dp, 1.dp).background(Pn.edge))
        RailButton("add", "Add a game", on = tab == "add") { pick("add") }
        RailButton("settings", "Settings", on = false, onClick = settings)
    }
}

/**
 * The open tab's views under its icon in the rail: a tray on `--u-pan` (8 in from the rail's sides) holding one 48 line
 * per view, each word centred under the icon (the owner, Nov 6: the words sat off-centre, left-aligned with a bar on one
 * side). The open one is a centred pill in a soft wash of your accent, its word bright; the others mute.
 */
@Composable
private fun RailViews(views: List<Pair<String, String>>, on: String?, volt: Color, pickView: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // the tray is drawn 8 in from the rail's sides, but each line takes taps across the rail's whole width
            .drawBehind {
                val inset = 8.dp.toPx()
                val r = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                val at = Offset(inset, 0f)
                val sz = Size(size.width - inset * 2, size.height)
                drawRoundRect(Pn.pan, at, sz, r)
                val hw = .5.dp.toPx()
                drawRoundRect(Pn.line, Offset(inset + hw, hw), Size(sz.width - hw * 2, sz.height - hw * 2), androidx.compose.ui.geometry.CornerRadius(r.x - hw), style = androidx.compose.ui.graphics.drawscope.Stroke(hw * 2))
            }
            .padding(vertical = RAIL_TRAY_PAD)
            .semantics { contentDescription = "Views" },
    ) {
        for ((k, label) in views) {
            val sel = k == on
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RAIL_VIEW_H)
                    .clickable(role = Role.Tab) { pickView(k) }
                    .semantics { selected = sel; contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(38.dp)
                        .then(if (sel) Modifier.background(volt.copy(alpha = .18f), RoundedCornerShape(13.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = RAIL_VIEW.copy(
                            color = if (sel) Pn.ice else Pn.mute,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ).fixed(),
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

/** The air above and below the lines in the views' tray (round 18, 8 in from the rail's sides). */
private val RAIL_TRAY_PAD = 4.dp

/** A view's word in the rail: Poppins 13. */
private val RAIL_VIEW = androidx.compose.ui.text.TextStyle(fontFamily = Fonts.poppins, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun RailButton(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).background(if (on) look.volt else Pn.steel, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            PnIcon(icon, if (on) Pn.void else Pn.ice, 22.dp, null)
        }
    }
}
