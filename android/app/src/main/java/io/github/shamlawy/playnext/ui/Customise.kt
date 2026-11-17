package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Rank
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * Opens a screen's Customise (`openSettings(scope, sub)`): null where nothing can (the tests' bare screens). Phase 1
 * has the Queue's ("queue"); each later screen brings its own groups with its phase.
 */
val LocalCustomise = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The screens whose Customise is built: their header card carries the chip, and nowhere else (no dead taps, M10). */
val CUSTOMISE = setOf("queue", "home")

/**
 * The header card's Customise chip (`html.ui2 .tabbar > .chip`, measured at 412 and 859): your accent's words and gear
 * (18 wide) on `--void` at .74 with an accent line at .55, 600 15, 8/14 in, round.
 */
@Composable
fun CustomiseChip(scope: String, modifier: Modifier = Modifier) {
    val open = LocalCustomise.current ?: return
    if (scope !in CUSTOMISE) return
    val volt = LocalLook.current.volt
    Row(
        modifier
            .background(Pn.void.copy(alpha = .74f), Pn.pill)
            .border(1.dp, volt.copy(alpha = .55f), Pn.pill)
            .clickable(role = Role.Button) { open(scope) }
            .semantics { contentDescription = "Customise" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PnIcon("settings", volt, 18.dp, null)
        Spacer(Modifier.width(4.dp))
        BasicText("Customise", style = TextStyle(fontFamily = Fonts.poppins, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = volt).fixed(), softWrap = false, maxLines = 1)
    }
}

/**
 * A screen's Customise (`openSettings(scope, sub)`: "Customise Queue"), in the Settings sheet's frame: only that
 * screen's groups, the live ones (Phase 1's answer, Nov 2: the web's controls that change nothing on its v299/v301
 * looks stay out). A change shows at once and lasts until the app closes; the backup never changes (a read-only
 * practice copy until switch day).
 */
@Composable
fun CustomiseSheet(modifier: Modifier, scope: String, games: Games.State, close: () -> Unit) {
    val lib = (games as? Games.State.Ready)?.lib
    SheetFrame(modifier, close) {
        BasicText("Customise " + (CUSTOMISE_NAME[scope] ?: scope), style = Type.sheetTitle, modifier = Modifier.padding(bottom = 2.dp))
        BasicText("Changes here show at once and last until the app closes. Your backup stays as it is until switch day.", style = Type.note)
        if (lib != null && scope == "queue") QueueGroups(lib)
        if (lib != null && scope == "home") HomeGroups(lib)
        Btn("Close", onClick = close)
    }
}

private val CUSTOMISE_NAME = mapOf("queue" to "Queue", "home" to "Home")

/** The Queue's groups (index.html 6230-6330), the live controls only. */
@Composable
private fun QueueGroups(lib: Library) {
    val set = Games::customise
    Card {
        BasicText("On deck", style = Type.head)
        BasicText("On deck is my top 3 from your queue: games like the ones you rank highest, on the machine you're playing on, and out now. Number 1 is the pick on Home.", style = Type.note)
        Label("Your platform right now")
        // `curPlat` choices (index.html 6244): Switch, your games' platforms, Any; at most 10, once per copy
        val plats = remember(lib) { (listOf("Switch") + lib.games.flatMap { Rank.platList(it["platforms"]) } + "any").distinct().take(10).map { it to if (it == "any") "Any" else it } }
        val cur = Backup.str(lib["curPlat"]).ifEmpty { "Switch" }
        Chips(plats, cur) { set("curPlat", JsonPrimitive(it)) }
        Label("On deck row in the queue")
        Chips(DECK_ROW, if (Backup.truthy(lib["deckOff"])) "off" else "on") { set("deckOff", JsonPrimitive(it == "off")) }
    }
    Card {
        BasicText("Queue cards", style = Type.head)
        Label("Closed games")
        Chips(Q_LOOK, if (lib.charSelect()) "select" else "strap") { set("qLook", JsonPrimitive(it)) }
        BasicText("Rows: each game on a strip in its colour, with a ticket and its logo. Character select: the picked game in a big panel over a grid of covers.", style = Type.note)
        Label("Card background")
        BasicText("A wide image bleeding in from the right, faded out before it reaches your text. Plain leaves the row in its colour only.", style = Type.note)
        Chips(CARD_BG, if (Backup.str(lib["cardBg"]) == "plain") "plain" else "hero") { set("cardBg", JsonPrimitive(it)) }
    }
    Card {
        BasicText("Character art", style = Type.head)
        BasicText("A game with character art set shows it upright in its own spot on the right of its row, every logo the same visual size and a dark one turned light so you can see it.", style = Type.note)
        Label("Show it on queue rows too")
        Chips(ROW_ART, if (lib.off("rowArt")) "no" else "yes") { set("rowArt", JsonPrimitive(it == "yes")) }
        Label("On a row — how big")
        Range(Backup.num(lib["rowArtW"], 150.0, 40.0, 200.0).roundToInt(), 40, 200, "px", "On a row, how big") { set("rowArtW", JsonPrimitive(it)) }
        Label("On a row — how strong")
        Range(Backup.num(lib["rowArtOp"], 95.0, 10.0, 100.0).roundToInt(), 10, 100, "%", "On a row, how strong") { set("rowArtOp", JsonPrimitive(it)) }
    }
    PageGroup(lib)
}

/**
 * "A game's own page" (index.html 6403, scope games, so it shows in the Queue's Customise too; 3-critic.md M1): its two
 * controls that change the New page. The rest (wallpaper, the figure, giant title, style) is Showcase's or the hub
 * figure's, not ported (the owner, Nov 10).
 */
@Composable
internal fun PageGroup(lib: Library) {
    val set = Games::customise
    Card {
        BasicText("A game's own page", style = Type.head)
        BasicText("Tapping a game opens its own page.", style = Type.note)
        Label("Motion on this page")
        Chips(ON_OFF, if (lib.off("hubAnim")) "off" else "on") { set("hubAnim", JsonPrimitive(it == "on")) }
        BasicText("Off freezes everything on a game's page: the slide between games, every fade. Nothing moves, nothing can land in the wrong place. The rest of the app keeps its motion.", style = Type.note)
        Label("Platforms")
        Chips(PLAT_TEXT, if (Backup.truthy(lib["platText"])) "text" else "logo") { set("platText", JsonPrimitive(it == "text")) }
    }
}

/**
 * Home's four groups (index.html 6190-6232, `openSettings("home")`): which tiles show (`S.homeHide` holds the ones
 * turned off), the game's colour on the Standings rows and the tab counts, Playing now's look (`S.dzX.play`: Classic)
 * and how many places Standings lists. Every one is live now; the tiles they change come with Home's next updates.
 */
@Composable
private fun HomeGroups(lib: Library) {
    val set = Games::customise
    Card {
        BasicText("Tiles on Home", style = Type.head)
        BasicText("Turn off anything you don't look at. A tile also hides itself when it has no data yet.", style = Type.note)
        val off = remember(lib) { (lib["homeHide"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content } }
        val offSet = remember(off) { off.toSet() }
        Toggles(HOME_TILES, offSet) { k ->
            // the order the web keeps: what was off, then the newly turned off one at the end
            set("homeHide", JsonArray((if (k in off) off - k else off + k).map(::JsonPrimitive)))
        }
        BasicText("The tiles arrive over Home's next few updates; what you pick here already counts.", style = Type.note)
    }
    Card {
        BasicText("How tiles look", style = Type.head)
        Label("Tint tiles with the game's own colour")
        Chips(YES_NO, if (lib.off("homeTint")) "off" else "on") { set("homeTint", JsonPrimitive(it == "on")) }
        Label("The counts under the title")
        Chips(SHOW_HIDE, if (lib.off("homeCount")) "off" else "on") { set("homeCount", JsonPrimitive(it == "on")) }
    }
    Card {
        BasicText("Playing now cards", style = Type.head)
        Label("Look")
        // the web's note also promises the game's character in the card; that figure isn't ported (Nov 10, X5)
        BasicText("Each game in its own colour and a big title.", style = Type.note)
        val dz = lib["dzX"] as? JsonObject
        Chips(DZ_LOOK, if (Backup.truthy(dz?.get("play"))) "off" else "on") { v ->
            set("dzX", JsonObject(dz.orEmpty() + ("play" to JsonPrimitive(v == "off"))))
        }
    }
    Card {
        BasicText("The standings tile", style = Type.head)
        Label("Places in the standings")
        Range(Backup.num(lib["standN"], 4.0, 2.0, 10.0).roundToInt(), 2, 10, "", "Places in the standings") { set("standN", JsonPrimitive(it)) }
    }
}

/**
 * `.chips` where each is on or off by itself (`aria-pressed`): lit in your accent when on; [off] holds the keys that
 * are off.
 */
@Composable
private fun Toggles(items: List<Pair<String, String>>, off: Set<String>, toggle: (String) -> Unit) {
    val volt = LocalLook.current.volt
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((v, label) in items) {
            val on = v !in off
            Box(
                Modifier
                    .sizeIn(minHeight = 40.dp)
                    .background(if (on) volt else Pn.card, Pn.pill)
                    .then(if (on) Modifier else Modifier.border(1.dp, Pn.line2, Pn.pill))
                    .toggleable(on, role = Role.Switch) { toggle(v) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = Type.label.copy(color = if (on) Pn.void else Pn.ice, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    softWrap = false, maxLines = 1,
                )
            }
        }
    }
}

/** `HOME_TILES` (index.html 6168): each tile's key and name, in the web's order. */
private val HOME_TILES = listOf(
    "soon" to "Next out", "read" to "Your read", "cat" to "What decides it", "duels" to "Duels", "stand" to "Standings",
    "coverage" to "Settle a duel", "unrated" to "Needs a verdict", "tonight" to "Tonight", "trap" to "Hype trap",
    "swing" to "Furthest from hype", "genre" to "Genre lean", "hours" to "Queue hours donut", "year" to "Year",
    "queue" to "Queue count", "playing" to "Playing now", "sessions" to "Play sessions", "wrapped" to "Year wrapped",
    "monthly" to "Monthly recap",
)
private val YES_NO = listOf("on" to "Yes", "off" to "No")
private val SHOW_HIDE = listOf("on" to "Show", "off" to "Hide")
private val DZ_LOOK = listOf("on" to "New", "off" to "Classic")

// the chips' choices, made once (a list built while drawing would be a new object on every redraw)
private val ON_OFF = listOf("on" to "On", "off" to "Off")
private val PLAT_TEXT = listOf("logo" to "Logos", "text" to "Names")
private val DECK_ROW = listOf("on" to "Show it", "off" to "Hide it")
private val Q_LOOK = listOf("strap" to "Rows", "select" to "Character select")
private val CARD_BG = listOf("hero" to "Art only", "plain" to "Plain")
private val ROW_ART = listOf("yes" to "In its own spot on the row", "no" to "Off")

@Composable
private fun Label(text: String) = BasicText(text, style = Type.label, modifier = Modifier.padding(top = 4.dp))

/** `.chips`: pills with a hairline; the picked one in your accent with `--void` words (`html.ui2 .chip`). */
@Composable
internal fun Chips(items: List<Pair<String, String>>, picked: String, pick: (String) -> Unit) {
    val volt = LocalLook.current.volt
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((v, label) in items) {
            val on = v == picked
            Box(
                Modifier
                    .sizeIn(minHeight = 40.dp)
                    .background(if (on) volt else Pn.card, Pn.pill)
                    .then(if (on) Modifier else Modifier.border(1.dp, Pn.line2, Pn.pill))
                    .clickable(role = Role.RadioButton) { if (!on) pick(v) }
                    .semantics { selected = on }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = Type.label.copy(color = if (on) Pn.void else Pn.ice, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    softWrap = false, maxLines = 1,
                )
            }
        }
    }
}

/**
 * `input[type=range]` in your accent beside its value (`.rowline`, the value in `--mute` 12, at least 42 wide, right
 * aligned). The thumb follows the finger (translation only); the value is sent when the finger lets go or taps, so
 * your games are rebuilt once, not on every step.
 */
@Composable
private fun Range(value: Int, min: Int, max: Int, unit: String, name: String, set: (Int) -> Unit) {
    val volt = LocalLook.current.volt
    val px = LocalDensity.current.density
    var width by remember { mutableFloatStateOf(1f) }
    // where the finger has it (-1: at the saved value)
    var held by remember { mutableFloatStateOf(-1f) }
    // the value let go of, shown until the rebuilt games bring it back as [value] (M72: it snapped back to the old one
    // for a moment); null once they have
    var sent by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(value) { sent = null }
    val now by rememberUpdatedState(value)
    val send by rememberUpdatedState(set)
    val shown = if (held >= 0f) (min + held * (max - min)).roundToInt() else sent ?: value
    val f = (shown - min).toFloat() / (max - min)
    val at = { x: Float -> (x / width).coerceIn(0f, 1f) }
    val done = { v: Int ->
        held = -1f
        if (v != now) {
            sent = v
            send(v)
        } else {
            sent = null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(Pn.touch)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(min, max) {
                    detectTapGestures { o -> done((min + at(o.x) * (max - min)).roundToInt()) }
                }
                .pointerInput(min, max) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> held = at(o.x) },
                        onDragEnd = { done((min + held.coerceAtLeast(0f) * (max - min)).roundToInt()) },
                        onDragCancel = { held = -1f },
                    ) { ch, _ -> held = at(ch.position.x) }
                }
                .semantics {
                    contentDescription = name
                    stateDescription = "$shown$unit"
                    progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), min.toFloat()..max.toFloat())
                    setProgress { v -> done(v.roundToInt().coerceIn(min, max)); true }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Pn.line2, Pn.pill))
            Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(4.dp).background(volt, Pn.pill))
            Box(
                Modifier
                    .offset { IntOffset((f * width - 9 * px).roundToInt(), 0) }
                    .size(18.dp)
                    .background(volt, Pn.pill),
            )
        }
        BasicText("$shown$unit", style = TextStyle(fontFamily = Fonts.poppins, fontSize = 12.sp, lineHeight = 16.sp, color = Pn.mute, textAlign = TextAlign.End), modifier = Modifier.widthIn(min = 42.dp))
    }
}
