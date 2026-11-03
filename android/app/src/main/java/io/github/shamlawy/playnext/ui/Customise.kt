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
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * Opens a screen's Customise (`openSettings(scope, sub)`): null where nothing can (the tests' bare screens). Phase 1
 * has the Queue's ("queue"); each later screen brings its own groups with its phase.
 */
val LocalCustomise = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The screens whose Customise is built: their header card carries the chip, and nowhere else (no dead taps, M10). */
val CUSTOMISE = setOf("queue")

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
        Btn("Close", onClick = close)
    }
}

private val CUSTOMISE_NAME = mapOf("queue" to "Queue")

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
}

// the chips' choices, made once (a list built while drawing would be a new object on every redraw)
private val DECK_ROW = listOf("on" to "Show it", "off" to "Hide it")
private val CARD_BG = listOf("hero" to "Art only", "plain" to "Plain")
private val ROW_ART = listOf("yes" to "In its own spot on the row", "no" to "Off")

@Composable
private fun Label(text: String) = BasicText(text, style = Type.label, modifier = Modifier.padding(top = 4.dp))

/** `.chips`: pills with a hairline; the picked one in your accent with `--void` words (`html.ui2 .chip`). */
@Composable
private fun Chips(items: List<Pair<String, String>>, picked: String, pick: (String) -> Unit) {
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
