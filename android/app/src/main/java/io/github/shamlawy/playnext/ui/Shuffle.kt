package io.github.shamlawy.playnext.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Lists
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Shuffler
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * The Game Shuffler (`openShuffle`, index.html 15915; Sort › Game shuffler, or a hold on "Shuffle the queue", as the
 * web's hold on Shuffle): your waiting games shuffled into a deck, one at a time, each drawn as the open Queue card
 * (the owner, Nov 2: "yeah queue card is better", not the web's v181 look of serif capitals, emoji and tilted cards).
 * Swipe right (or the star) if it tempts you, left (or the cross) for not now; Back one undoes a swipe, Shuffle again
 * deals a new deck, Your picks shows what tempted you. Everything waiting or only the games out now. The picks can open
 * their game page or become a list (a practice copy, like every list until switch day); the web's "Play now" waits for
 * Phase 2's saving.
 */
@Composable
fun ShuffleDeck(modifier: Modifier, games: Games.State, pick: QueuePick?, close: () -> Unit) {
    val ready = games as? Games.State.Ready ?: return
    val lib = ready.lib
    val p = pick ?: remember(lib) { QueuePick(lib) }
    // the Queue the day build made off Main, as the Queue page uses it (else the deck's first frame builds a Rank)
    val v = rememberQueueView(lib, p, ready.cards?.queue)
    val q = v.q
    val calm = LocalCalm.current
    val scope = rememberCoroutineScope()
    val byId = remember(q) { q.lib.games.associateBy { it.id } }
    // each game's place in your order (the card's "3rd in queue"), as the Queue's All tab shows it
    val spots = remember(q, v.sort, p.moved) {
        val moved = if (p.movedFor === lib) p.moved else emptyMap()
        q.sort(q.list("all"), v.sort, moved).withIndex().associate { (i, g) -> g.id to i + 1 }
    }
    var deck by rememberSaveable(stateSaver = DeckSaver) {
        val mode = Backup.str(lib["shufMode"]).takeIf { it == Shuffler.OUT } ?: Shuffler.ALL
        mutableStateOf(Shuffler.Deck(mode, Shuffler.deck(q, mode)))
    }
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) Sounds.play(Sfx.Open)
        opened = true
    }
    // the card's swipe: its place (px) while a finger has it or it flies off; one swipe at a time
    val x = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    var cardW by remember { mutableStateOf(1f) }
    val decide: (Boolean) -> Unit = decide@{ yes ->
        if (busy || deck.done || deck.id == null) return@decide
        busy = true
        Sounds.play(if (yes) Sfx.Add else Sfx.Tap)
        if (Buzz.swipes) Buzz.ms(if (yes) 14L else 6L)
        Crumbs.tap(if (yes) "tempted" else "not now", "shuffle")
        scope.launch {
            // a fly cut short (a new deal snaps the card back) decides nothing, but never leaves the deck stuck busy
            val from = deck
            try {
                if (!calm) x.animateTo((if (yes) 1f else -1f) * cardW * FLY, tween(FLY_MS, easing = FLY_EASE))
                if (deck === from) deck = deck.swipe(yes)
                x.snapTo(0f)
            } finally {
                busy = false
            }
        }
    }
    val deal = { mode: String ->
        deck = deck.again(mode, Shuffler.deck(q, mode))
        scope.launch { x.snapTo(0f) }
        busy = false
    }
    // a kept deck can name a game a newer backup took away: skip to the next card that's still there
    LaunchedEffect(deck, byId) {
        if (!deck.done && deck.id != null && deck.id !in byId) deck = deck.past(byId.keys)
    }
    val guard = LocalSwipeGuard.current
    Box(
        modifier.fillMaxSize().background(LocalLook.current.bgBase).ownTaps()
            // a swipe on the deck is the deck's (a card, or nothing): never a view swipe on the tab under it
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); guard.inScroller = true } }
            .semantics { paneTitle = "Game shuffler" }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = overNavBar(LocalBottomRoom.current)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // unfolded, the rail stays over the deck (as over every sheet): the deck keeps clear of its room
            // (on the side it's on, none while it's hidden behind Nexi)
            val at = LocalRailAt.current
            val wide = maxWidth >= RAIL_FROM && at.shown
            val railEnd = if (wide && !at.left) railRoom(maxWidth, at) else 0.dp
            val railStartPad = if (wide && at.left) railStart(maxWidth, at) else 0.dp
            val rail = railEnd + railStartPad
            // the big card (from 620 wide) only when the screen is tall enough for it and the words under it
            val big = maxWidth - rail - 32.dp >= 620.dp && maxHeight >= queueFaceHeight(true) + ROOM_UNDER
            Column(Modifier.fillMaxSize().padding(start = railStartPad, end = railEnd)) {
                TopBar(deck.mode, choose = { m ->
                    if (m != deck.mode) {
                        Sounds.play(Sfx.Tap)
                        Crumbs.tap("deck $m", "shuffle")
                        deal(m)
                    }
                }, close = close)
                val g = deck.id?.let { byId[it] }
                if (deck.done || g == null) {
                    Picks(
                        q, deck, byId, lib, Modifier.weight(1f),
                        again = {
                            Sounds.play(Sfx.Open)
                            Crumbs.tap("shuffle again", "shuffle")
                            deal(deck.mode)
                        },
                        resume = {
                            Sounds.play(Sfx.Tap)
                            deck = deck.copy(done = false)
                        },
                    )
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.widthIn(max = CARD_MAX).fillMaxWidth()) {
                            Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Tag("${deck.at + 1} / ${deck.ids.size}", false)
                                if (g.id in deck.picks) Tag("Tempted", true)
                            }
                            key(deck.ids, deck.at) {
                                DeckCard(q, g, spots[g.id] ?: 1, big, x, { cardW = it }, decide, { busy })
                            }
                            Strip(deck, byId) { i ->
                                if (!busy && i != deck.at) {
                                    Sounds.play(Sfx.Tap)
                                    deck = deck.go(i)
                                }
                            }
                            BasicText("Why play " + Shuffler.short(g.title) + " next", style = DType.head.fixed(), modifier = Modifier.padding(top = 4.dp))
                            BasicText(
                                Shuffler.why(q, g), style = DType.why.fixed(), maxLines = 4, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                            )
                        }
                    }
                    BottomBar(
                        deck,
                        undo = {
                            if (!busy && deck.steps.isNotEmpty()) {
                                Sounds.play(Sfx.Back)
                                Crumbs.tap("back one", "shuffle")
                                deck = deck.undo()
                            }
                        },
                        decide = decide,
                        again = {
                            if (!busy) {
                                Sounds.play(Sfx.Open)
                                Crumbs.tap("shuffle again", "shuffle")
                                deal(deck.mode)
                            }
                        },
                        picks = {
                            if (!busy) {
                                Sounds.play(Sfx.Tap)
                                Crumbs.tap("your picks", "shuffle")
                                deck = deck.copy(done = true)
                            }
                        },
                    )
                }
            }
        }
    }
}

private val DeckSaver: Saver<Shuffler.Deck, Any> = listSaver(save = { it.save() }, restore = { Shuffler.Deck.restore(it) })

/** A card flies a little past its own width off the side (`translateX(±130%)`), in 300 ms, fading as it goes. */
private const val FLY = 1.3f
private const val FLY_MS = 300
private val FLY_EASE = CubicBezierEasing(.4f, 0f, .6f, 1f)

/** Let go before [SWIPE_AT] and it springs back (`cubic-bezier(.34,1.4,.64,1)`, 350 ms). */
private val SWIPE_AT = 90.dp
private val BACK_EASE = CubicBezierEasing(.34f, 1.4f, .64f, 1f)

/** How the next card comes up (`translateY(26px) scale(.95)` → none, 420 ms). */
private val IN_EASE = CubicBezierEasing(.16f, 1f, .3f, 1f)

/** The widest the card gets, and the room under the big one for the strip, the words and the buttons. */
private val CARD_MAX = 720.dp
private val ROOM_UNDER = 360.dp

// ---------------------------------------------------------------- the card

/**
 * The card on show: the open Queue card's front ([QueueFace]), following the finger sideways and leaning with it
 * (`rotate(dx/22deg)`), "Tempted" or "Not now" showing as it goes; let go past [SWIPE_AT] and it's decided. It comes up
 * from a little below as the last one flies off.
 */
@Composable
private fun DeckCard(q: Queue, g: Game, n: Int, big: Boolean, x: Animatable<Float, *>, width: (Float) -> Unit, decide: (Boolean) -> Unit, busy: () -> Boolean) {
    val calm = LocalCalm.current
    val scope = rememberCoroutineScope()
    val rise = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(Unit) {
        // the last card's spring-back can be cut short (a tap on the strip, Back one): this one starts straight
        x.snapTo(0f)
        if (rise.value < 1f) rise.animateTo(1f, tween(420, easing = IN_EASE))
    }
    val volt = LocalLook.current.volt
    Box(
        Modifier.fillMaxWidth()
            .onSizeChanged { width(it.width.toFloat()) }
            .graphicsLayer {
                val dx = x.value
                val r = rise.value
                translationX = dx
                translationY = 26.dp.toPx() * (1 - r)
                scaleX = .95f + .05f * r
                scaleY = scaleX
                rotationZ = dx / density / 22f
                // fading only as it flies off past the swipe line
                val past = (abs(dx) - SWIPE_AT.toPx()).coerceAtLeast(0f) / (size.width * FLY - SWIPE_AT.toPx()).coerceAtLeast(1f)
                alpha = r * (1f - past).coerceIn(0f, 1f)
            }
            .pointerInput(g.id) {
                val line = SWIPE_AT.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (busy()) return@detectHorizontalDragGestures
                        if (abs(x.value) > line) decide(x.value > 0)
                        else scope.launch { x.animateTo(0f, tween(350, easing = BACK_EASE)) }
                    },
                    onDragCancel = { if (!busy()) scope.launch { x.animateTo(0f, tween(350, easing = BACK_EASE)) } },
                ) { change, dx ->
                    if (busy()) return@detectHorizontalDragGestures
                    change.consume()
                    scope.launch { x.snapTo(x.value + dx) }
                }
            },
    ) {
        QueueFace(q, g, n, big, true, Modifier)
        // what letting go now would do, stronger the further it goes
        Stamp("Tempted", volt, Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 18.dp).graphicsLayer { alpha = (x.value / SWIPE_AT.toPx()).coerceIn(0f, 1f) })
        Stamp("Not now", Pn.hot, Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 18.dp).graphicsLayer { alpha = (-x.value / SWIPE_AT.toPx()).coerceIn(0f, 1f) })
    }
}

/** "Tempted" / "Not now" on the card while it's being swiped (sentence case, straight: not the web's tilted capitals). */
@Composable
private fun Stamp(word: String, tint: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    BasicText(
        word, style = DType.stamp.copy(color = tint).fixed(), maxLines = 1, softWrap = false,
        modifier = modifier.background(Pn.void.copy(alpha = .72f), shape).border(2.dp, tint, shape).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** A small round label over the card: where you are in the deck, or that this one tempted you. */
@Composable
private fun Tag(word: String, lit: Boolean) {
    val volt = LocalLook.current.volt
    BasicText(
        word, style = DType.tag.copy(color = if (lit) Pn.void else Pn.ice).fixed(), maxLines = 1, softWrap = false,
        modifier = Modifier.background(if (lit) volt else Pn.card, Pn.pill).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Six covers of the deck around the one on show (`.sh-row`): a tap jumps to that card. */
@Composable
private fun Strip(deck: Shuffler.Deck, byId: Map<String, Game>, go: (Int) -> Unit) {
    val volt = LocalLook.current.volt
    val from = maxOf(0, minOf(deck.at - 2, deck.ids.size - 6))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        for (i in from until minOf(from + 6, deck.ids.size)) {
            val g = byId[deck.ids[i]] ?: continue
            val on = i == deck.at
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Tab) { go(i) }
                    .semantics { contentDescription = g.title; selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(42.dp).graphicsLayer { alpha = if (on) 1f else .55f }
                        .then(if (on) Modifier.border(2.dp, volt, CircleShape) else Modifier).padding(3.dp).clip(CircleShape),
                ) { PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Pn.card) }
            }
        }
    }
}

// ---------------------------------------------------------------- the bars

/** Everything waiting / Out now only (`.sh-mode`, words instead of the web's two colour dots), and Close. */
@Composable
private fun TopBar(mode: String, choose: (String) -> Unit, close: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Everything waiting", mode == Shuffler.ALL) { choose(Shuffler.ALL) }
            ModeChip("Out now only", mode == Shuffler.OUT) { choose(Shuffler.OUT) }
        }
        Box(
            Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp).background(Pn.card, CircleShape), contentAlignment = Alignment.Center) { DeckIcon("x", Pn.ice, 22.dp) }
        }
    }
}

@Composable
private fun ModeChip(word: String, on: Boolean, tap: () -> Unit) {
    val volt = LocalLook.current.volt
    Box(
        Modifier.height(Pn.touch).semantics { selected = on }.clickable(role = Role.RadioButton, onClick = tap),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            word, style = DType.chip.copy(color = if (on) Pn.void else Pn.ice).fixed(), maxLines = 1, softWrap = false,
            modifier = Modifier.height(36.dp).background(if (on) volt else Pn.card, Pn.pill)
                .then(if (on) Modifier else Modifier.border(1.dp, Pn.line2, Pn.pill))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Back one · Not now · Tempted (the big one) · Shuffle again · Your picks (with how many) (`.sh-bar`). */
@Composable
private fun BottomBar(deck: Shuffler.Deck, undo: () -> Unit, decide: (Boolean) -> Unit, again: () -> Unit, picks: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("back", "Back one", false, deck.steps.isNotEmpty(), undo)
        RoundButton("x", "Not now", false, true) { decide(false) }
        RoundButton("star", "Tempted", true, true) { decide(true) }
        RoundButton("again", "Shuffle again", false, true, again)
        Box {
            RoundButton("list", "Your picks" + if (deck.picks.isNotEmpty()) ", ${deck.picks.size}" else "", false, true, picks)
            if (deck.picks.isNotEmpty()) {
                BasicText(
                    deck.picks.size.toString(), style = DType.badge.copy(textAlign = TextAlign.Center).fixed(), maxLines = 1, softWrap = false,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).widthIn(min = 20.dp)
                        .background(LocalLook.current.volt, Pn.pill).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundButton(icon: String, label: String, big: Boolean, on: Boolean, tap: () -> Unit) {
    val volt = LocalLook.current.volt
    val s = if (big) 64.dp else 52.dp
    Box(
        Modifier.size(s).clip(CircleShape).background(if (big) volt else Pn.card)
            .then(if (big) Modifier else Modifier.border(1.dp, Pn.line2, CircleShape))
            .clickable(enabled = on, role = Role.Button, onClick = tap)
            .semantics { contentDescription = label; if (!on) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        DeckIcon(icon, if (big) Pn.void else Pn.ice.copy(alpha = if (on) 1f else .35f), if (big) 30.dp else 24.dp)
    }
}

// ---------------------------------------------------------------- your picks

/**
 * What tempted you (`results()`): each pick with how it stands, a tap opens its page; Save as a list (two or more),
 * Shuffle again, Keep going while the deck has cards left.
 */
@Composable
private fun Picks(q: Queue, deck: Shuffler.Deck, byId: Map<String, Game>, lib: Library, modifier: Modifier, again: () -> Unit, resume: () -> Unit) {
    val open = LocalOpenGame.current
    val picks = deck.picks.mapNotNull { byId[it] }
    var saved by rememberSaveable(deck.ids, deck.picks) { mutableStateOf<String?>(null) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("${deck.ids.size} shuffled", style = Type.label.fixed())
            BasicText(if (picks.isNotEmpty()) "Tempted by ${picks.size}" else "Nothing grabbed you", style = DType.title.fixed())
            BasicText(
                if (picks.isNotEmpty()) "Open one to have a look."
                else if (deck.mode == Shuffler.OUT) "Shuffle again, or switch to everything waiting."
                else "Shuffle again, or switch to games that are out.",
                style = DType.why.fixed(), modifier = Modifier.padding(bottom = 6.dp),
            )
            for (g in picks) PickRow(q, g) {
                Crumbs.tap("open pick", "shuffle")
                open(g.id, null)
            }
            Spacer(Modifier.height(6.dp))
            if (picks.size > 1) {
                val s = saved
                if (s == null) {
                    WideButton("Save as a list", false) {
                        Sounds.play(Sfx.Add)
                        Crumbs.tap("save as list", "shuffle")
                        saved = saveAsList(lib, picks)
                    }
                } else {
                    BasicText("Saved as “$s”: it's a chip in the Queue's bar", style = Type.note.fixed())
                }
            }
            WideButton("Shuffle again", true, again)
            if (deck.ids.subList(minOf(deck.at, deck.ids.size), deck.ids.size).any { it in byId }) WideButton("Keep going", false, resume)
        }
    }
}

/** One pick (`.sh-pk`): the game's colour down its side, its box art, its name and how it stands. */
@Composable
private fun PickRow(q: Queue, g: Game, tap: () -> Unit) {
    val st = remember(g, q) { q.stat(g) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Pn.card).border(1.dp, Pn.line, shape)
            .clickable(role = Role.Button, onClick = tap)
            .semantics(mergeDescendants = true) { contentDescription = g.title + ", " + st.head + ", open its page" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(72.dp).background(Color(g.colour)))
        Box(Modifier.padding(start = 10.dp).width(40.dp).height(60.dp).clip(RoundedCornerShape(8.dp))) {
            PicBox(Pic.cover(g), g, Modifier.fillMaxSize(), ground = Pn.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            BasicText(g.title, style = Type.name.fixed(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(st.head, style = Type.note.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WideButton(word: String, main: Boolean, tap: () -> Unit) {
    val volt = LocalLook.current.volt
    Box(
        Modifier.fillMaxWidth().height(Pn.touch).clip(Pn.rs).background(if (main) volt else Pn.card)
            .then(if (main) Modifier else Modifier.border(1.dp, Pn.line2, Pn.rs))
            .clickable(role = Role.Button, onClick = tap),
        contentAlignment = Alignment.Center,
    ) { BasicText(word, style = Type.button.copy(color = if (main) Pn.void else Pn.ice).fixed(), maxLines = 1) }
}

/**
 * `data-shlist`: a new list "Tempted · 6 Nov" (the target mark) holding the picks, in the practice copy every list
 * change goes to until switch day. Gives back the list's name.
 */
internal fun saveAsList(lib: Library, picks: List<Game>, today: LocalDate = LocalDate.now()): String {
    val lists = Lists.of(lib)
    val id = Lists.newId(System.currentTimeMillis())
    val name = "Tempted · " + today.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
    val mark = Lists.MARKS.firstOrNull { it.icon == "list:target" }?.emo ?: Lists.nextEmo(lists)
    val made = Lists.made(lists, name, mark, id)
    Games.customise("lists", Lists.json(made))
    for (g in picks) {
        val over = Games.practiced(Lists.IN) as? kotlinx.serialization.json.JsonObject
        if (id !in Lists.inLists(over, g)) Games.customise(Lists.IN, Lists.flipped(over, g, id))
    }
    return made.last().n
}

// ---------------------------------------------------------------- icons and type

/** The deck's 24x24 icons in the v247 style: a soft disc at .35 with the sign on it (Android's own, for `SH_ICO`). */
private val DECK_IC = mapOf(
    "x" to listOf("M7.1 8.5 8.5 7.1 12 10.6l3.5-3.5 1.4 1.4-3.5 3.5 3.5 3.5-1.4 1.4-3.5-3.5-3.5 3.5-1.4-1.4 3.5-3.5z" to 1f),
    "back" to listOf(DISC to .35f, "M13.6 6.9 15 8.3 11.3 12l3.7 3.7-1.4 1.4L8.5 12z" to 1f),
    "star" to listOf("M12 5.8L13.7 10.05L18.28 10.36L14.76 13.3L15.88 17.74L12 15.3L8.12 17.74L9.24 13.3L5.72 10.36L10.3 10.05z" to 1f),
    "again" to listOf(DISC to .35f, "M16.11 7.1A6.4 6.4 0 1 1 7.89 7.1L9.04 8.48A4.6 4.6 0 1 0 14.96 8.48z" to 1f, "M13.86 9.79L17.21 5.79L12.57 6.53z" to 1f),
    "list" to listOf(DISC to .35f, "M7.5 8h9v1.8h-9zM7.5 11.1h9v1.8h-9zM7.5 14.2h6v1.8h-6z" to 1f),
)
private const val DISC = "M12 1.8a10.2 10.2 0 1 1 0 20.4a10.2 10.2 0 1 1 0-20.4z"
private val deckVectors = HashMap<String, ImageVector>()

@Composable
private fun DeckIcon(name: String, tint: Color, size: Dp) {
    val v = remember(name) {
        deckVectors.getOrPut(name) {
            ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
                for ((d, a) in DECK_IC.getValue(name)) addPath(PathParser().parsePathString(d).toNodes(), PathFillType.NonZero, fill = SolidColor(Color.Black), fillAlpha = a)
            }.build()
        }
    }
    Image(rememberVectorPainter(v), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
}

private object DType {
    private val base = Type.body
    val head = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val why = base.copy(fontSize = 13.5.sp, lineHeight = 20.sp, color = Pn.ice.copy(alpha = .78f))
    val title = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val stamp = base.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val tag = base.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    val chip = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val badge = base.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = Pn.void)
}
