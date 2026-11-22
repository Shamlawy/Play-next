package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Home
import io.github.shamlawy.playnext.data.JsDate
import io.github.shamlawy.playnext.data.Nexi
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import io.github.shamlawy.playnext.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home's top (Phase 3 piece 1; index.html `renderHome` 11250, `dxHome` 11115, `dxWire` 11222, Nexi's line 13316 +
 * 13747): Nexi's line in his voice with "Your top N", then the panel (`.dxw`): the Play next / Played tabs with their
 * counts, the search, Shuffle and Customise, the big card (Play this next, the dice, Hype, "#1 of N") and the next
 * four, then the middle tiles (HomeTiles.kt: the queue orb, Playing now, the donut, Next out), and under the panel More for you's
 * two stacks (HomeStacks.kt). The
 * dice plays Pick for me's reveal (Reveal.kt, piece 2), which opens the game; calm opens it straight away.
 */
@Composable
internal fun HomeTop(ready: Games.State.Ready, wide: Boolean, screen: Dp) {
    val h = rememberHome(ready)
    val calm = LocalCalm.current
    // the first open per app run: the panel's pieces rise in one after another (`html.first .dxw > *`, pnRise .6 s,
    // .05 s apart); calm, or any later open: they're simply there
    val rise = remember { if (HomeRise.done || calm) null else Animatable(0f) }
    LaunchedEffect(rise) {
        HomeRise.done = true
        rise?.animateTo(RISE_MS + 5 * RISE_STEP, tween((RISE_MS + 5 * RISE_STEP).toInt(), easing = LinearEasing))
    }
    Column(Modifier.fillMaxWidth()) {
        if (!Backup.truthy(h.q.lib["nxHomeOff"])) NexiLine(h, wide, screen)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp)
                .background(Pn.pan, RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = .04f), RoundedCornerShape(28.dp))
                .padding(start = if (wide) 22.dp else 14.dp, end = if (wide) 22.dp else 14.dp, top = if (wide) 22.dp else 16.dp, bottom = if (wide) 22.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 16.dp),
        ) {
            HomeHead(h, wide, Modifier.zIndex(1f).rise(rise, 0))
            val heroH = if (wide) (LocalConfiguration.current.screenHeightDp * .32f).coerceIn(250f, 380f).dp else 0.dp
            if (wide) {
                // `"p p p p p l l l"`: the big card five eighths, the next four in the rest, as tall as the taller
                val n = h.next.size
                val rowH = maxOf(heroH, (n * ROW_H + (n - 1).coerceAtLeast(0) * 4).dp)
                Row(Modifier.fillMaxWidth().height(rowH), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Hero(h, wide, screen, Modifier.weight(5f).fillMaxHeight().rise(rise, 1))
                    NextFour(h, wide, Modifier.weight(3f).fillMaxHeight().rise(rise, 2))
                }
            } else {
                Hero(h, wide, screen, Modifier.rise(rise, 1))
                NextFour(h, wide, Modifier.rise(rise, 2))
            }
            // the middle tiles (piece 3): the queue, Playing now, the donut and Next out
            HomeTiles(h, wide, screen) { n -> Modifier.rise(rise, n) }
        }
        // under the panel: More for you's two stacks (piece 4, HomeStacks.kt)
        MoreForYou(h, wide, Modifier.padding(bottom = 22.dp).rise(rise, 5))
    }
}

/** Home's numbers for this copy: the day build's (off Main) when it's for this copy and today, else made here once. */
@Composable
internal fun rememberHome(ready: Games.State.Ready): Home {
    val lib = ready.lib
    val now = LocalNow.current()
    val zone = java.time.ZoneId.systemDefault()
    val made = ready.cards?.home?.takeIf { it.q.lib === lib && it.q.covers(now, zone) }
    val day = JsDate.todayLocal(now, zone)
    return remember(lib, made, day) { made ?: HomeMade.get(lib, day) { Home(Queue(lib, Rank(lib, now, zone), now, zone), now, zone) } }
}

/** The Home made on Main for a copy and day, shared by Home's screen and the unfolded rail, so it's made once. */
private object HomeMade {
    private var lib: Library? = null
    private var day: Any? = null
    private var home: Home? = null

    @Synchronized
    fun get(lib: Library, day: Any, make: () -> Home): Home {
        home?.let { if (this.lib === lib && this.day == day) return it }
        return make().also { this.lib = lib; this.day = day; home = it }
    }
}

/** The first Home of this app run has risen (the web's `html.first`, dropped after the first open). */
internal object HomeRise {
    @Volatile var done = false
}

private const val RISE_MS = 600f
private const val RISE_STEP = 50f

/** The [n]th piece's rise: from 18 below, .98 and clear to its place, on the house curve, [n] × 50 ms late. */
private fun Modifier.rise(a: Animatable<Float, *>?, n: Int): Modifier = if (a == null) this else graphicsLayer {
    val k = Ease.house.transform(((a.value - n * RISE_STEP) / RISE_MS).coerceIn(0f, 1f))
    if (k < 1f) {
        alpha = k
        translationY = 18.dp.toPx() * (1 - k)
        val s = .98f + .02f * k
        scaleX = s
        scaleY = s
    }
}

/** What Home's buttons open in the shell: a sheet over the page ("shuffle") or another tab ("add"). */
@Immutable
class HomeActs(val sheet: (String) -> Unit, val tab: (String) -> Unit)

val LocalHomeActs = staticCompositionLocalOf<HomeActs?> { null }

/** One line at the foot for a part of the app a later update builds (`SOON`): the line Edit leaves, or nothing in tests. */
@Composable
internal fun rememberSay(): (String) -> Unit {
    val acts = LocalEditActs.current
    return remember(acts) { { t: String -> acts?.say?.invoke(t) } }
}

// ---------------------------------------------------------------- Nexi's line

/**
 * `.nxhome`: his face (52, round, two blinking eyes; hidden unfolded while he sits in the rail, so there's one Nexi)
 * and his speech bubble: Play X next in his voice, why in small grey, and "Your top N" pills of your favourite finished
 * games. A tap on the game opens it; a tap on his face would open his chat, which comes with his phase (8b).
 */
@Composable
private fun NexiLine(h: Home, wide: Boolean, screen: Dp) {
    val pick = h.nexiPick
    val top = h.top
    if (pick == null && top.isEmpty()) return
    val prefs = LocalPrefs.current
    val volt = LocalLook.current.volt
    val open = LocalOpenGame.current
    val say = rememberSay()
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        // unfolded he sits in the rail, so Home's face steps aside; with the rail hidden he floats and the face is back
        // (the web's seatOn(): wide, the seat on, and the rail shown)
        val seated = wide && !prefs.nxSeatOff && LocalRailAt.current.shown
        if (!seated) {
            HomeFace(Modifier.clickable(role = Role.Button, onClickLabel = "Chat with Nexi") {
                Crumbs.tap("nexi face", "home")
                say(soon("chat"))
            }.semantics { contentDescription = "Chat with Nexi" })
        }
        Column(
            Modifier
                .weight(1f)
                .background(Pn.ink, BUBBLE)
                .border(1.dp, volt.copy(alpha = .28f), BUBBLE)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val base = SAY.fixed()
            if (pick != null) {
                val voice = remember(h) { Nexi.homeLine(Nexi.persona(h.q.lib), Backup.str(h.q.lib["userName"]).trim()) }
                val why = remember(h, pick) { h.q.deckWhy(pick) }
                val text = buildAnnotatedString {
                    append(voice.before)
                    withLink(
                        LinkAnnotation.Clickable(
                            "game",
                            TextLinkStyles(SpanStyle(color = volt, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)),
                        ) { open(pick.id, null) },
                    ) { append(pick.title) }
                    append(voice.after)
                    if (why.isNotEmpty()) withStyle(SpanStyle(color = Pn.mute, fontSize = 12.sp)) { append(" ($why)") }
                }
                BasicText(text, style = base)
            } else {
                BasicText(Nexi.NO_PICK, style = base)
            }
            if (top.isNotEmpty()) TopPills(h, top, screen < 520.dp)
        }
    }
}

private val BUBBLE = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)

/** "Your top N" and up to three pills (rank, tier, title); under 520 wide each takes its own row. */
@Composable
private fun TopPills(h: Home, top: List<Game>, stacked: Boolean) {
    val open = LocalOpenGame.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(if (stacked) 4.dp else 6.dp)) {
        BasicText("YOUR TOP ${top.size}", style = TOP_HEAD.fixed(), maxLines = 1, modifier = Modifier.semantics { heading() })
        val pill = @Composable { i: Int, g: Game, mod: Modifier ->
            Row(
                mod
                    .background(Pn.void, Pn.pill)
                    .border(1.dp, Pn.steel, Pn.pill)
                    .clickable(role = Role.Button) { open(g.id, null) }
                    .semantics(mergeDescendants = true) { contentDescription = "Number ${i + 1}, ${g.title}" }
                    .sizeIn(minHeight = 32.dp)
                    .padding(start = 5.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                BasicText("${i + 1}", style = TOP_RANK.fixed(), modifier = Modifier.padding(start = 3.dp))
                h.score(g)?.let { TierBadge(it) }
                BasicText(g.title, style = TOP_NAME.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
        if (stacked) {
            top.forEachIndexed { i, g -> pill(i, g, Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { top.forEachIndexed { i, g -> pill(i, g, Modifier.weight(1f)) } }
        }
    }
}

/**
 * His Home face (`.nxh-face`): a 52 round ground lit from the top left, an accent ring, two eyes that blink every 5 s
 * (scaleY to .1 at 95%: a timed loop, so between blinks nothing asks for frames). Calm: no blink.
 */
@Composable
private fun HomeFace(modifier: Modifier) {
    val volt = LocalLook.current.volt
    val calm = LocalCalm.current
    val blink = remember { Animatable(1f) }
    LaunchedEffect(calm) {
        blink.snapTo(1f)
        if (calm) return@LaunchedEffect
        while (true) {
            delay(4600)
            blink.animateTo(.1f, tween(150, easing = LinearEasing))
            blink.animateTo(1f, tween(250, easing = LinearEasing))
        }
    }
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(FACE_GROUND)
            .border(2.dp, volt.copy(alpha = .55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.offset(y = (-2).dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Box(Modifier.size(8.dp, 12.dp).graphicsLayer { scaleY = blink.value }.background(Color(Web.NXH_EYE), CircleShape))
            }
        }
    }
}

/** `radial-gradient(circle at 35% 30%, near, far 70%)` over the 52 face. */
private val FACE_GROUND: Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
        val c = androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .3f)
        // the farthest corner from that point is what the CSS circle's 100% reaches
        val r = maxOf(c.getDistance(), (androidx.compose.ui.geometry.Offset(size.width, size.height) - c).getDistance(),
            (androidx.compose.ui.geometry.Offset(size.width, 0f) - c).getDistance(), (androidx.compose.ui.geometry.Offset(0f, size.height) - c).getDistance())
        return androidx.compose.ui.graphics.RadialGradientShader(c, r, listOf(Color(Web.NXH_FACE), Color(Web.NXH_FACE_FAR), Color(Web.NXH_FACE_FAR)), listOf(0f, .7f, 1f))
    }
}

// ---------------------------------------------------------------- the head

/**
 * `.dxhead`: "Play next (N)" and the Played tab (its screen comes in phase 5), the search, and the round Shuffle (2+
 * games) and Customise buttons (icons only: the web never shows their words). Folded the search is a row of its own
 * under the rest; unfolded it sits between, at most 320 wide.
 */
@Composable
private fun HomeHead(h: Home, wide: Boolean, modifier: Modifier) {
    val cnt = !h.q.lib.off("homeCount")
    val say = rememberSay()
    val acts = LocalHomeActs.current
    val custom = LocalCustomise.current
    val volt = LocalLook.current.volt
    val tabs = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BasicText("Play next" + if (cnt) " (${h.live.size})" else "", style = TAB_ON.fixed(), maxLines = 1, softWrap = false, modifier = Modifier.semantics { heading() })
            Box(
                Modifier
                    .height(44.dp)
                    .clickable(role = Role.Button) {
                        Sounds.play(Sfx.Open)
                        Crumbs.tap("played tab", "home")
                        say(soon("played"))
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Played" + if (cnt) " (${h.playedCount})" else "", style = TAB_OFF.fixed(), maxLines = 1, softWrap = false)
            }
        }
    }
    val pills = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (h.live.size > 1) {
                HeadPill("ui:shuffle", "Shuffle your games", volt) {
                    Crumbs.tap("shuffle", "home")
                    acts?.sheet?.invoke("shuffle")
                }
            }
            HeadPill("ui:sliders", "Customise Home", volt) { custom?.invoke("home") }
        }
    }
    // what you typed, kept here above the split: the field sits in a different place folded and unfolded, so its own
    // saved state would be dropped by every fold
    var text by rememberSaveable { mutableStateOf("") }
    if (wide) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs()
            // the tabs keep their words; the search takes the room left, at most 320, against the buttons
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { HomeSearch(h, text, { text = it }, Modifier.widthIn(max = 320.dp).fillMaxWidth()) }
            pills()
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                tabs()
                Spacer(Modifier.weight(1f))
                pills()
            }
            HomeSearch(h, text, { text = it }, Modifier.fillMaxWidth())
        }
    }
}

/** `.dxpill`: a 44 round button with an accent line and the accent's icon, its name for TalkBack only. */
@Composable
private fun HeadPill(icon: String, name: String, volt: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 44.dp)
            .height(44.dp)
            .border(1.5.dp, volt, Pn.pill)
            .clip(Pn.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = name }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { PnIcon(icon, volt, 18.dp, null) }
}

/**
 * The search (`dxWire`): every game, played too, as you type; Go opens the first. The results lie over the card under
 * it (not pushing it down), and what you typed stays when Home redraws (the web loses it, 3-critic.md M8).
 */
@Composable
private fun HomeSearch(h: Home, text: String, onText: (String) -> Unit, modifier: Modifier) {
    val volt = LocalLook.current.volt
    val focus = LocalFocusManager.current
    val open = LocalOpenGame.current
    var focused by remember { mutableStateOf(false) }
    val hits = remember(h, text) { h.search(text) }
    // Back hides the keyboard but keeps the field focused, so the results would stay over the card and the next Back
    // would leave Home: while it's focused, Back lets go of the field (the web hides its results on blur)
    BackHandler(enabled = focused) { focus.clearFocus() }
    val go = { g: Game ->
        focus.clearFocus()
        open(g.id, null)
    }
    Dropdown(
        modifier,
        field = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, if (focused) volt else Color(Web.DX_SEARCH_EDGE), Pn.pill)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PnIcon("ui:search", Pn.mute, 18.dp, null)
                val style = SEARCH.fixed()
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(volt),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { hits.firstOrNull()?.let(go) }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = "Search your games" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) BasicText("Game search…", style = style.copy(color = Pn.mute.copy(alpha = .8f)), maxLines = 1)
                            inner()
                        }
                    },
                )
            }
        },
        under = {
            if (focused && text.isNotBlank()) SearchResults(h, hits, go)
        },
    )
}

/** Lays [field] out and [under] 6 below it, over whatever follows: the box keeps only the field's size. */
@Composable
private fun Dropdown(modifier: Modifier, field: @Composable () -> Unit, under: @Composable () -> Unit) {
    Layout(contents = listOf(field, under), modifier = modifier) { (f, u), c ->
        val fp = f.first().measure(c)
        val gap = 6.dp.roundToPx()
        val up = u.map { it.measure(Constraints(minWidth = fp.width, maxWidth = fp.width)) }
        layout(fp.width, fp.height) {
            fp.place(0, 0)
            up.forEach { it.place(0, fp.height + gap) }
        }
    }
}

/** `.dxres`: up to six rows (cover, title, "Played" / how far its date is), or "No game called that." */
@Composable
private fun SearchResults(h: Home, hits: List<Game>, go: (Game) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(Web.DX_RES), RoundedCornerShape(20.dp))
            .border(1.dp, Pn.line, RoundedCornerShape(20.dp))
            .semantics { isTraversalGroup = true; contentDescription = "Search results" }
            .padding(6.dp),
    ) {
        if (hits.isEmpty()) {
            BasicText("No game called that.", style = NONE.fixed(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        for (g in hits) {
            val small = h.found(g)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { go(g) }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PicBox(remember(g) { Pic.cover(g) }, g, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), ground = Pn.card)
                BasicText(g.title, style = RES_NAME.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (small.isNotEmpty()) BasicText(small, style = RES_SMALL.fixed(), maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------------------------------------------------------------- the big card

/**
 * `.dxhero`: the pick's wide picture under a shade, its cover as a round avatar, "Play this next" and its logo (or
 * name), then Hype, the dice (2+ games; it picks one of your top three, never the last pick, and plays the reveal,
 * Reveal.kt, which opens it; calm opens it straight away), the play mark, the duel button's place (Duels come in phase 6) and "#1 of N" or the
 * duels settled. A tap anywhere else opens the game. No games: "Add a game and this fills in" and a way to Add.
 */
@Composable
private fun Hero(h: Home, wide: Boolean, screen: Dp, modifier: Modifier) {
    val pick = h.pick
    val volt = LocalLook.current.volt
    val acts = LocalHomeActs.current
    val open = LocalOpenGame.current
    val calm = LocalCalm.current
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // folded 16:10, at least 220 tall; unfolded the row sets it
        val box = if (wide) Modifier.fillMaxSize() else Modifier.height(maxOf(220.dp, maxWidth / 1.6f))
        if (pick == null) {
            Column(
                box.fillMaxWidth().background(Brush.linearGradient(listOf(Color(Web.DX_EMPTY_A), Color(Web.DX_EMPTY_B)), start = EMPTY_FROM, end = EMPTY_TO), shape).padding(if (wide) 18.dp else 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    BasicText("PLAY THIS NEXT", style = KICK.fixed(), maxLines = 1)
                    BasicText("Add a game and this fills in", style = heroTitle(screen).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    PlayMark(volt, Modifier.clickable(role = Role.Button) {
                        Sounds.play(Sfx.Open)
                        Crumbs.tap("add", "home")
                        acts?.tab?.invoke("add")
                    }.semantics { contentDescription = "Add a game" })
                }
            }
        } else {
        val title = pick.title
        Box(
            box
                .fillMaxWidth()
                .clip(shape)
                .background(Pn.card)
                // TalkBack reads the card's own words once (Play this next, the title or its logo's name, the hype,
                // the place); a description on top read the title twice and hid the rest
                .clickable(role = Role.Button, onClickLabel = "Open $title") { open(pick.id, null) },
        ) {
            PicBox(remember(pick) { Pic.backdrop(pick) ?: Pic.cover(pick) }, pick, Modifier.fillMaxSize(), ground = Pn.card)
            Box(Modifier.fillMaxSize().background(SHADE))
            Column(Modifier.fillMaxSize().padding(if (wide) 18.dp else 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PicBox(remember(pick) { Pic.cover(pick) }, pick, Modifier.size(46.dp).clip(CircleShape).border(2.5.dp, Color.White, CircleShape), ground = Pn.card)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PnIcon("ui:flame", Pn.hot, 12.dp, null)
                            BasicText("PLAY THIS NEXT", style = KICK.fixed(), maxLines = 1, softWrap = false)
                        }
                        val art = Backup.str(pick["art"])
                        if (art.isNotEmpty()) HeroLogo(art, title) else {
                            BasicText(title, style = heroTitle(screen).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PnIcon("ui:flame", Pn.hot, 15.dp, null)
                        val hype = pick["hype"]
                        BasicText(if (Backup.truthy(hype)) "Hype " + Backup.str(hype) else "No hype yet", style = BAR.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (h.live.size > 1) {
                        RoundBtn("ui:dice", "I'll pick one") {
                            Crumbs.tap("pick one", "home")
                            h.pickOne(PickLast.get(), Math.random())?.let { g ->
                                PickLast.set(g.id)
                                // the reveal plays and opens it (Reveal.kt); calm opens it straight away
                                if (calm) open(g.id, null) else Reveal.start(g)
                            }
                        }
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    PlayMark(volt, Modifier.clearAndSetSemantics {})
                    // the duel button's place: Duels come in phase 6
                    Spacer(Modifier.size(40.dp))
                    val tm = if (h.settled > 0) "${h.settled} duel" + (if (h.settled == 1) "" else "s") else "#1 of ${h.live.size}"
                    BasicText(tm, style = BAR.copy(textAlign = TextAlign.End).fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        }
    }
}

/**
 * The last game Pick for me chose (`S.hintLast` on the web, a backup key; here the phone's own, so the practice copy
 * never writes the backup): the next pick skips it.
 */
internal object PickLast {
    private var mem: String? = null
    fun get(): String? = if (Store.ready()) Store.prefs.getString("pick:last", null) else mem
    fun set(id: String) {
        mem = id
        if (Store.ready()) Store.prefs.edit().putString("pick:last", id).apply()
    }
}

private val EMPTY_FROM = androidx.compose.ui.geometry.Offset(0f, 0f)
private val EMPTY_TO = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)

/** `.dxhero::after`: dark at the top, clear from 34 to 52%, darkest at the foot. */
private val SHADE = Brush.verticalGradient(
    0f to Color(Web.DX_SHADE_TOP), .34f to Color(Web.DX_SHADE_TOP).copy(alpha = 0f), .52f to Color(Web.DX_SHADE_FOOT).copy(alpha = 0f), 1f to Color(Web.DX_SHADE_FOOT),
)

/** The pick's logo (`.plogo`): at most 56 tall and 70% wide, from the left. */
@Composable
private fun HeroLogo(art: String, title: String) {
    val ctx = LocalContext.current
    val req = remember(art) { ImageRequest.Builder(ctx).data(picUrl(art)).build() }
    AsyncImage(
        model = req, contentDescription = title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth(.7f).heightIn(max = 56.dp),
    )
}

/** `.cb`: a 40 round button, white at .18, its icon white. */
@Composable
private fun RoundBtn(icon: String, name: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(Web.DX_CB)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) { PnIcon(icon, Color.White, 18.dp, null) }
}

/** `.play`: the 64 accent circle with the dark play mark (nudged 3 right, as the web's). */
@Composable
private fun PlayMark(volt: Color, modifier: Modifier) {
    Box(modifier.size(64.dp).clip(CircleShape).background(volt), contentAlignment = Alignment.Center) {
        PnIcon("ui:go", Color(Web.DX_PLAY_INK), 26.dp, null, Modifier.offset(x = 3.dp))
    }
}

/** The big card's name: 700, `clamp(18px, 5vw, 26px)`. */
private fun heroTitle(screen: Dp) = TextStyle(
    fontFamily = Fonts.poppins, fontSize = (screen.value * .05f).coerceIn(18f, 26f).sp, lineHeight = ((screen.value * .05f).coerceIn(18f, 26f) * 1.15f).sp,
    fontWeight = FontWeight.Bold, color = Color.White,
    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(0f, 2f), 14f),
)

// ---------------------------------------------------------------- the next four

/**
 * `.dxlist`: `ranked` 2-5 as rows (cover, name, the clock and when, the first genre on the game's colour), the first
 * lit. Unfolded they share the big card's height, at most 86 each. None: "Add more games and your line-up shows here."
 */
@Composable
private fun NextFour(h: Home, wide: Boolean, modifier: Modifier) {
    val open = LocalOpenGame.current
    val list = h.next
    Column(modifier.fillMaxWidth(), verticalArrangement = if (wide) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp)) {
        if (list.isEmpty()) {
            BasicText("Add more games and your line-up shows here.", style = NONE.fixed(), modifier = Modifier.padding(8.dp))
        }
        list.forEachIndexed { n, g ->
            val lit = n == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (wide) Modifier.weight(1f, fill = false).heightIn(min = ROW_H.dp, max = 86.dp) else Modifier)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (lit) Pn.card else Color.Transparent)
                    .clickable(role = Role.Button) { open(g.id, null) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PicBox(remember(g) { Pic.cover(g) }, g, Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)), ground = Pn.card)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    BasicText(g.title, style = ROW_NAME.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // the date keeps its whole words ("No date yet", "14 Nov"); a narrow row shortens the genre
                        // chip instead, which ends in "…"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            val ink = if (lit) Pn.ice else Pn.mute
                            PnIcon("ui:hour", ink, 12.dp, null)
                            BasicText(h.whenOf(g), style = ROW_WHEN.copy(color = ink).fixed(), maxLines = 1, softWrap = false)
                        }
                        val gp = h.genreOf(g)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            if (gp.isNotEmpty()) GenreTag(gp, Color(g.colour))
                        }
                    }
                }
            }
        }
    }
}

/** `.gp`: the genre on the game's colour under a light dark veil, 8 round, at most about half the row. */
@Composable
private fun GenreTag(text: String, tint: Color) {
    val shape = RoundedCornerShape(8.dp)
    BasicText(
        text, style = GP.fixed(), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
        modifier = Modifier
            .widthIn(max = 140.dp)
            .background(tint, shape)
            .background(Color(Web.DX_GP_VEIL), shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A next-four row's natural height: 8 + the 54 cover + 8. */
private const val ROW_H = 70

// ---------------------------------------------------------------- words

private val P = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)
private val SAY = P.copy(fontSize = 14.5.sp, lineHeight = 20.3.sp)
private val TOP_HEAD = P.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .1.em, color = Pn.mute)
private val TOP_RANK = TextStyle(fontFamily = Fonts.archivo, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, color = Pn.mute)
private val TOP_NAME = P.copy(fontSize = 12.5.sp, lineHeight = 17.sp)
private val TAB_ON = P.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val TAB_OFF = P.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = Pn.mute)
private val SEARCH = P.copy(fontSize = 14.sp, lineHeight = 20.sp)
private val RES_NAME = P.copy(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
private val RES_SMALL = P.copy(fontSize = 11.sp, lineHeight = 15.sp, color = Pn.mute)
private val NONE = P.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Pn.mute)
private val KICK = P.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .08.em, color = Color.White.copy(alpha = .88f))
private val BAR = P.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
private val ROW_NAME = P.copy(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
private val ROW_WHEN = P.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
private val GP = P.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
