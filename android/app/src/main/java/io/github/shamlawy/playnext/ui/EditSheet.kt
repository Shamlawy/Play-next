package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Edit
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Pic
import io.github.shamlawy.playnext.data.Plat
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** What a game's Edit can ask of the screen round it: open it for a game, a line at the foot, the card back's Competitive. */
@androidx.compose.runtime.Stable
class EditActs(val open: (String) -> Unit, val say: (String) -> Unit, val comp: (Game) -> Unit)

val LocalEditActs = staticCompositionLocalOf<EditActs?> { null }

/** How Edit ended, for the screen round it: saved, the game removed, or moved back to the queue. */
sealed interface EditEnd {
    data object Saved : EditEnd
    data class Removed(val g: Game) : EditEnd
    data class Unplayed(val g: Game) : EditEnd
}

/**
 * A game's Edit (`openEdit`, android/parity/2-map/2-edit.md): a sheet with the game's name and three tab chips (Game |
 * Look | Scores), each tab's groups as closed rows (a drawn icon, the name, a grey line), one open at a time with
 * "‹ Back to all" over it, and Save / Review changes / Cancel / Remove always at its foot. Every control writes to a
 * draft ([Edit.Draft]); only Save puts what changed into the practice copy. No group has more than 7 settings at
 * Android's size (3-critic.md M6 counted), so none folds behind "More options".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSheet(modifier: Modifier, games: Games.State, id: String, close: () -> Unit, end: (EditEnd) -> Unit) {
    val ready = games as? Games.State.Ready
    val lib = ready?.lib
    val g = lib?.games?.firstOrNull { it.id == id }
    if (lib == null || g == null) {
        LaunchedEffect(Unit) { close() }
        return
    }
    val now = LocalNow.current
    val zone = ZoneId.systemDefault()
    val q = ready.cards?.queue?.takeIf { it.lib === lib } ?: remember(lib) { Queue(lib, Rank(lib, now(), zone), now(), zone) }
    val rec = remember(lib, id) { lib.played.firstOrNull { it.id == id } }
    val sums = remember(lib, g) { Edit.sums(q, g, rec) }
    val held: MutableState<Edit.Draft> = rememberSaveable(saver = draftSaver { Edit.open(lib, g) }) { mutableStateOf(Edit.open(lib, g)) }
    var d by held
    var tab by rememberSaveable { mutableStateOf("game") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var diff by remember { mutableStateOf<List<Edit.Change>?>(null) }
    val scroll = rememberScrollState()
    var listAt by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // a change makes Review's list old: it goes until Review is tapped again
    val set = { nd: Edit.Draft -> d = nd; diff = null }
    BackHandler { if (open != null) open = null else close() }
    // only when the open group changes: coming back (Settings over Edit, unfolded) keeps the restored spot
    var shownOpen by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        if (open == shownOpen) return@LaunchedEffect
        if (open == null) scroll.scrollTo(listAt) else scroll.scrollTo(0)
        shownOpen = open
    }

    EditFrame(modifier, close) { pull ->
        // the head: the name, and the tabs while no group is open
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(g.title, style = Type.sheetTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (open == null) {
                Chips(Edit.TABS, tab) {
                    Sounds.play(Sfx.Tap)
                    tab = it
                    listAt = 0
                    scope.launch { scroll.scrollTo(0) }
                }
            }
        }
        // Look's preview: the top of the game's page as the draft has it, over the scroll
        if (tab == "look") LookPreview(g, d)
        Column(
            Modifier.weight(1f, fill = false).nestedScroll(pull.connection).verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val o = open
            if (o == null) {
                for (name in Edit.groups(tab, rec != null)) {
                    GroupRow(name, sums[name].orEmpty()) {
                        Sounds.play(Sfx.Tap)
                        listAt = scroll.value
                        open = name
                    }
                }
            } else {
                Row(
                    Modifier.sizeIn(minHeight = Pn.touch).clickable(role = Role.Button) { open = null }.semantics { contentDescription = "Back to all" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PnIcon("ui:back", Pn.mute, 16.dp, null)
                    BasicText("Back to all", style = Type.label)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PnIcon("gi:$o", LocalLook.current.volt, 22.dp, null)
                    BasicText(o, style = Type.head.copy(fontSize = 17.sp, lineHeight = 22.sp))
                }
                val ctx = Ctx(lib, g, q, d, set, { msg = it })
                when (o) {
                    "Name" -> NameGroup(ctx)
                    "Release" -> ReleaseGroup(ctx, now)
                    "Where it stands" -> Chips(Web.STATES, d.str("state").ifEmpty { "upcoming" }) { set(d.set("state", JsonPrimitive(it))) }
                    "Platforms" -> PlatGroup(ctx)
                    "Genres and tags" -> GenreGroup(ctx)
                    "Notes" -> NotesGroup(ctx)
                    "Hype" -> {
                        Label("How much you want it")
                        val h = Backup.num(d["hype"], 0.0, 0.0, 10.0).toInt()
                        Chips(HYPES, if (h > 0) h.toString() else "") { set(d.set("hype", JsonPrimitive(it.toInt()))) }
                    }
                    "Critics" -> {
                        Field(ctx, "critic", "Critic score out of 100", if (Backup.truthy(g["critic"])) Backup.str(g["critic"]) else "", number = true)
                    }
                    "My rating" -> RatingGroup(ctx)
                    "Played" -> if (rec != null) PlayedGroup(ctx, rec, now) { end(EditEnd.Unplayed(g)) }
                    "Cover and colour" -> CoverGroup(ctx)
                    "Backdrop" -> BackdropGroup(ctx)
                    "Logo / character art" -> LogoGroup(ctx)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // the foot, always there (`.efoot`)
        Column(
            Modifier.fillMaxWidth().background(Pn.pan).border(1.dp, Pn.line, RoundedCornerShape(0.dp)).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // two even rows (four in a row don't fit at 412 and wrapped unevenly)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EBtn("Save", Kind.Go, Modifier.weight(1f)) {
                    Crumbs.tap("edit save", "edit")
                    Games.change(Edit.save(lib, g, d, now()) { Games.practiced(it) })
                    end(EditEnd.Saved)
                }
                EBtn("Review changes", Kind.Plain, Modifier.weight(1f)) {
                    Sounds.play(Sfx.Tap)
                    diff = Edit.review(lib, g, d)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EBtn("Cancel", Kind.Plain, Modifier.weight(1f)) { close() }
                EBtn("Remove", Kind.Del, Modifier.weight(1f)) {
                    Crumbs.tap("edit remove", "edit")
                    end(EditEnd.Removed(g))
                }
            }
            diff?.let { l ->
                BasicText(Edit.reviewHead(l.size), style = Type.note)
                // its own short scroll: a long list mustn't squeeze the groups above to nothing
                Column(Modifier.heightIn(max = 132.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in l) {
                    BasicText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Pn.ice)) { append(c.field) }
                            append("  " + c.was + "  →  ")
                            withStyle(SpanStyle(color = Pn.ice)) { append(c.now) }
                        },
                        style = Type.note,
                    )
                }
                }
            }
            msg?.let { BasicText(it, style = Type.note) }
        }
    }
}

// only your changes are kept (a whole game can pass the 1 MB of saved state), laid over the game as it is on return
private fun draftSaver(base: () -> Edit.Draft) = Saver<MutableState<Edit.Draft>, String>(save = { it.value.text() }, restore = { t -> Edit.Draft.of(t, base())?.let { mutableStateOf(it) } })

private val HYPES = (1..10).map { it.toString() to it.toString() }
private val SIMPLE_ADV = listOf("simple" to "Simple", "adv" to "Advanced")
private val AGAIN = listOf("yes" to "Yes — Replay list", "no" to "No")
private val STATUS = listOf("confirmed" to "confirmed", "rumoured" to "rumoured", "tba" to "tba")

/**
 * Everything a group's controls need: the game as saved, the draft and how to change it, the message line. A new one
 * for each change of the draft (never changed in place), so it's compared like a value.
 */
@androidx.compose.runtime.Immutable
private class Ctx(val lib: Library, val g: Game, val q: Queue, val d: Edit.Draft, val set: (Edit.Draft) -> Unit, val say: (String?) -> Unit)

/**
 * The sheet itself (`#sheet`): at most 620 wide, `--u-pan` with a hairline, round 28 at the top (all round once
 * there's room), over `--void` at .86; a tap round it closes, a pull down past a quarter of the screen does too. Its
 * head and foot stay put while the middle scrolls, and it rises over the keyboard.
 */
@Composable
private fun EditFrame(modifier: Modifier, close: () -> Unit, content: @Composable ColumnScope.(Pull) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(Pn.void.copy(alpha = .86f)).scrimTap(close).imePadding()) {
        val round = maxWidth >= 660.dp
        val at = LocalRailAt.current
        val shift = pageShift(maxWidth, at)
        val most = if (maxWidth >= RAIL_FROM) minOf(620.dp, pageRoom(maxWidth, at)) else 620.dp
        val shape = if (round) RoundedCornerShape(28.dp) else RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)
        val pull = rememberPull(close, constraints.maxHeight.toFloat())
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout)).padding(top = maxHeight * .04f, bottom = if (round) maxHeight * .04f else 0.dp),
            contentAlignment = if (round) Alignment.Center else Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = most)
                    .fillMaxWidth()
                    .offset(x = -shift)
                    .graphicsLayer { translationY = pull.offset }
                    .clip(shape)
                    .background(Pn.pan, shape)
                    .border(1.dp, Pn.line2, shape)
                    .ownTaps()
                    .semantics { paneTitle = "Edit" }
                    // at the foot of the screen it clears the navigation bar and the taskbar; centred it needs neither
                    .then(if (round) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = overNavBar(LocalBottomRoom.current))),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(40.dp, 4.dp).background(Pn.line2, RoundedCornerShape(4.dp)))
                content(pull)
            }
        }
    }
}

/** A closed group (`details.grp`): its drawn icon, the bold name and the grey line, a chevron on the right. */
@Composable
private fun GroupRow(name: String, sum: String, open: () -> Unit) {
    val volt = LocalLook.current.volt
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = 60.dp).background(Pn.card, Pn.r).border(1.dp, Pn.line, Pn.r)
            .clickable(role = Role.Button, onClick = open).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PnIcon("gi:$name", volt, 22.dp, null)
        Column(Modifier.weight(1f)) {
            BasicText(name, style = Type.name)
            if (sum.isNotEmpty()) BasicText(sum, style = Type.note, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PnIcon("ui:back", Pn.mute, 14.dp, null, Modifier.graphicsLayer { rotationZ = 180f })
    }
}

@Composable
private fun Label(text: String) = BasicText(text, style = Type.label, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Note(text: String) = BasicText(text, style = Type.note)

private enum class Kind { Plain, Go, Del }

/** `.btn` / `.btn.ghost` / `.btn.danger`: 48 tall, round, the accent for the main one. */
@Composable
private fun EBtn(text: String, kind: Kind, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val volt = LocalLook.current.volt
    val bg = when (kind) { Kind.Go -> volt; Kind.Del -> Pn.hot.copy(alpha = .14f); Kind.Plain -> Pn.card }
    val ink = when { !enabled -> Pn.mute; kind == Kind.Go -> Pn.void; kind == Kind.Del -> Pn.hot; else -> Pn.ice }
    Box(
        modifier.sizeIn(minHeight = Pn.touch).background(bg, Pn.pill).then(if (kind == Kind.Plain) Modifier.border(1.dp, Pn.line2, Pn.pill) else Modifier)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.semantics { disabled() })
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Type.button.copy(color = ink), maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = .5.sp)) }
}

/** A text box (`input` / `textarea` in the sheet): `--void`, a hairline in steel, round 12, its label above. */
@Composable
private fun Field(c: Ctx, key: String, label: String, had: String, hint: String = "", lines: Int = 1, number: Boolean = false) {
    Label(label)
    val v = c.d.typed(key, had)
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().background(Pn.void, shape).border(1.dp, Pn.steel, shape).padding(horizontal = 12.dp, vertical = 11.dp)) {
        if (v.isEmpty() && hint.isNotEmpty()) BasicText(hint, style = Type.body.copy(color = Pn.mute))
        BasicTextField(
            value = v,
            onValueChange = { c.set(c.d.set("in:$key", JsonPrimitive(if (number) it.filter { ch -> ch.isDigit() || ch == '.' } else it))) },
            singleLine = lines == 1,
            minLines = lines,
            textStyle = Type.body.copy(color = Pn.ice),
            cursorBrush = SolidColor(LocalLook.current.volt),
            keyboardOptions = KeyboardOptions(capitalization = if (number) KeyboardCapitalization.None else KeyboardCapitalization.Sentences, keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/** Toggle chips (any number on), the picked ones in your accent: Platforms, Genres, What got you. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toggles(items: List<Pair<String, String>>, on: (String) -> Boolean, icon: (String) -> String? = { null }, flip: (String) -> Unit) {
    val volt = LocalLook.current.volt
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((v, label) in items) {
            val lit = on(v)
            Row(
                Modifier.sizeIn(minHeight = 40.dp).background(if (lit) volt else Pn.card, Pn.pill).then(if (lit) Modifier else Modifier.border(1.dp, Pn.line2, Pn.pill))
                    .clickable(role = Role.Checkbox) { flip(v) }.semantics { selected = lit }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                icon(v)?.let { PnIcon(it, if (lit) Pn.void else Pn.ice, 16.dp, null) }
                BasicText(label, style = Type.label.copy(color = if (lit) Pn.void else Pn.ice, fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * A slider (`input[type=range]`) in your accent beside its value: it follows the finger and writes the draft at every
 * step (a draft is cheap: nothing else redraws until Save).
 */
@Composable
private fun Slide(value: Double, min: Double, max: Double, step: Double, shown: String, name: String, set: (Double) -> Unit) {
    val volt = LocalLook.current.volt
    val px = LocalDensity.current.density
    var width by remember { mutableFloatStateOf(1f) }
    val send by rememberUpdatedState(set)
    val cur by rememberUpdatedState(value)
    val at = { x: Float -> val f = (x / width).coerceIn(0f, 1f); (Math.round((min + f * (max - min)) / step) * step).coerceIn(min, max) }
    val put = { v: Double -> if (v != cur) send(v) }
    val f = ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).height(Pn.touch).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(min, max, step) { detectTapGestures { o -> put(at(o.x)) } }
                .pointerInput(min, max, step) { detectHorizontalDragGestures { ch, _ -> put(at(ch.position.x)) } }
                .semantics {
                    contentDescription = name
                    stateDescription = shown
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat())
                    setProgress { v -> put((Math.round(v / step) * step).coerceIn(min, max)); true }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Pn.line2, Pn.pill))
            Box(Modifier.fillMaxWidth(f).height(4.dp).background(volt, Pn.pill))
            Box(Modifier.offset { IntOffset((f * width - 9 * px).roundToInt(), 0) }.size(18.dp).background(volt, Pn.pill))
        }
        BasicText(shown, style = Type.note.copy(fontSize = 12.sp, textAlign = TextAlign.End), modifier = Modifier.widthIn(min = 42.dp), maxLines = 1, softWrap = false)
    }
}

// ---- Game ----

@Composable
private fun NameGroup(c: Ctx) {
    Field(c, "title", "Title", c.g.title)
    Field(c, "desc", "Why you want it", Backup.str(c.g["desc"]))
    Field(c, "about", "What it's about", Backup.str(c.g["about"]), hint = "A line or two on the story. The RAWG pull fills this in.", lines = 4)
}

@Composable
private fun ReleaseGroup(c: Ctx, now: () -> Long) {
    Label("Release date")
    DatePick(c.d.str("date"), Edit.today(now(), c.q.zone).toEpochDay()) { v -> c.set(Edit.pickDate(c.d, v)) }
    // the box already says the date in words; the line under it only when there's none ("shows as TBA")
    if (c.d.str("date").isEmpty()) Note(Edit.dateSay(c.q, ""))
    Field(c, "hours", "Main story (hours)", if (Backup.truthy(c.g["hours"])) Backup.str(c.g["hours"]) else "", number = true)
    if (Edit.nexiHours(c.g, c.d)) Note("Typical time, filled in by Nexi")
    Field(c, "hoursX", "Story + extras", if (Backup.truthy(c.g["hoursX"])) Backup.str(c.g["hoursX"]) else "", hint = Edit.hoursHint(c.g, "hoursX"), number = true)
    Field(c, "hours100", "Everything (100%)", if (Backup.truthy(c.g["hours100"])) Backup.str(c.g["hours100"]) else "", hint = Edit.hoursHint(c.g, "hours100"), number = true)
    val hl = Edit.hl(c.g)
    if (hl != null && !c.g.comp) {
        val same = Edit.hlSame(c.g.o, hl)
        val used = c.d["in:hours"] != null && Edit.hlSame(JsonObject(mapOf("hours" to JsonPrimitive(c.d.typed("hours", "")), "hoursX" to JsonPrimitive(c.d.typed("hoursX", "")), "hours100" to JsonPrimitive(c.d.typed("hours100", "")))), hl)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                buildAnnotatedString {
                    append("HowLongToBeat: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) {
                        append("${Edit.hlFmt(Backup.num(hl["m"], 0.0, 0.0, 1e5))} · ${Edit.hlFmt(Backup.num(hl["x"], 0.0, 0.0, 1e5))} · ${Edit.hlFmt(Backup.num(hl["c"], 0.0, 0.0, 1e5))} h")
                    }
                    if (same) append(" · these are them")
                },
                style = Type.note, modifier = Modifier.weight(1f, fill = false),
            )
            if (!same && !used) EBtn("Use these", Kind.Plain) { Sounds.play(Sfx.Tap); c.set(Edit.useHl(c.d, hl)) }
        }
    }
    BasicText(
        buildAnnotatedString {
            append("You play ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append(Edit.styleLabel(c.lib)) }
            append(", so that number is the one used for evenings and plans. Leave the others blank and a typical estimate is used.")
        },
        style = Type.note,
    )
    Label("Date certainty")
    Chips(STATUS, c.d.str("status")) { v ->
        val (nd, say) = Edit.pickStatus(c.d, v)
        c.set(nd)
        if (say != null) c.say(say)
    }
}

/**
 * The release date's own picker (never a native or Material date dialog: the house style, 3-critic.md (b)): the date
 * as words with ✕ to clear it, and under it the month picker's look (‹ year ›, twelve months) plus the month's days.
 */
@Composable
private fun DatePick(value: String, todayDay: Long, pick: (String) -> Unit) {
    val today = LocalDate.ofEpochDay(todayDay)
    val volt = LocalLook.current.volt
    val cur = runCatching { LocalDate.parse(value) }.getOrNull()
    var year by rememberSaveable(value) { mutableIntStateOf(cur?.year ?: today.year) }
    var month by rememberSaveable(value) { mutableIntStateOf(cur?.monthValue ?: today.monthValue) }
    var shown by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).sizeIn(minHeight = Pn.touch).background(Pn.void, shape).border(1.dp, Pn.steel, shape)
                .clickable(role = Role.Button) { shown = !shown }.semantics { contentDescription = "Release date" }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { BasicText(if (cur != null) "${cur.dayOfMonth} ${MONTHS[cur.monthValue - 1]} ${cur.year}" else "Pick a date", style = Type.body.copy(color = if (cur != null) Pn.ice else Pn.mute)) }
        Box(
            Modifier.size(Pn.touch).clickable(role = Role.Button) { pick("") }.semantics { contentDescription = "Clear the date" },
            contentAlignment = Alignment.Center,
        ) { PnIcon("ui:close", Pn.mute, 18.dp, null) }
    }
    if (!shown) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow("Year before", true) { year-- }
        BasicText(year.toString(), style = Type.name.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
        Arrow("Year after", false) { year++ }
    }
    MonthGrid(month, { false }) { month = it }
    val days = LocalDate.of(year, month, 1).lengthOfMonth()
    val lead = LocalDate.of(year, month, 1).dayOfWeek.value - 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in 0 until (lead + days + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val n = w * 7 + i - lead + 1
                    Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val on = cur != null && cur.year == year && cur.monthValue == month && cur.dayOfMonth == n
                            Box(
                                Modifier.fillMaxSize().background(if (on) volt else Pn.card, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) { pick(String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, n)); shown = false }
                                    .semantics { selected = on },
                                contentAlignment = Alignment.Center,
                            ) { BasicText(n.toString(), style = Type.label.copy(color = if (on) Pn.void else Pn.ice)) }
                        }
                    }
                }
            }
        }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
private fun Arrow(label: String, left: Boolean, enabled: Boolean = true, go: () -> Unit) {
    Box(
        Modifier.size(Pn.touch).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = go) else Modifier.semantics { disabled() }).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { PnIcon("ui:back", if (enabled) Pn.ice else Pn.line2, 18.dp, null, if (left) Modifier else Modifier.graphicsLayer { rotationZ = 180f }) }
}

/** Twelve months, six to a row (`finPick`'s buttons); [off] months can't be picked. */
@Composable
private fun MonthGrid(picked: Int, off: (Int) -> Boolean, pick: (Int) -> Unit) {
    val volt = LocalLook.current.volt
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 6) {
                    val m = r * 6 + i + 1
                    val on = m == picked
                    val no = off(m)
                    Box(
                        Modifier.weight(1f).height(40.dp).background(if (on) volt else Pn.card, RoundedCornerShape(10.dp))
                            .then(if (no) Modifier.semantics { disabled() } else Modifier.clickable(role = Role.Button) { pick(m) })
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) { BasicText(MONTHS[m - 1], style = Type.label.copy(color = if (on) Pn.void else if (no) Pn.line2 else Pn.ice), maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

@Composable
private fun PlatGroup(c: Ctx) {
    val list = c.d.str("platforms")
    Toggles(PLAT_ITEMS, { Edit.has(list, it) }, icon = { n -> Plat.of(n)?.first?.takeIf { it != "io" }?.let { "plat:$it" } }) { n -> c.set(c.d.set("platforms", JsonPrimitive(Edit.toggle(c.d.str("platforms"), n)))) }
}

private val PLAT_ITEMS = Web.PLATS.map { it to it }

@Composable
private fun GenreGroup(c: Ctx) {
    val list = c.d.str("genres")
    for ((head, names) in GENRE_ITEMS) {
        Label(head)
        Toggles(names, { Edit.has(list, it) }) { n -> c.set(c.d.set("genres", JsonPrimitive(Edit.toggle(c.d.str("genres"), n)))) }
    }
}

private val GENRE_ITEMS = Web.GENRE_GROUPS.map { (h, l) -> h to l.map { it to it } }

@Composable
private fun NotesGroup(c: Ctx) {
    Field(c, "learn", "What I can learn from it", Backup.str(c.g["learn"]), hint = "Psychology, themes, ideas worth taking away", lines = 3)
    Field(c, "tips", "How to play it well", Backup.str(c.g["tips"]), hint = "Guide open, don't miss social links, play blind…", lines = 3)
}

// ---- Scores ----

@Composable
private fun RatingGroup(c: Ctx) {
    val mode = if (Backup.str(c.lib["rateMode"]) == "adv") "adv" else "simple"
    // a setting for every game (`S.rateMode`), kept at once: not part of Save or Cancel
    Chips(SIMPLE_ADV, mode) { Games.customise("rateMode", JsonPrimitive(it)) }
    Note("Simple scores each category straight. Advanced scores the parts and works the category out from them — the number it lands on is the one everything else uses, so you can move between the two whenever you like.")
    val mine = c.d["mine"] as? JsonObject
    val subs = c.d["subs"] as? JsonObject
    for ((k, label) in Web.RATINGS) {
        val v = Backup.num(mine?.get(k), 0.0, 0.0, 10.0)
        if (mode == "simple") {
            Label(label)
            Slide(Math.round(v).toDouble(), 0.0, 10.0, 1.0, if (v > 0) Edit.jsNum(v) else Queue.DASH, label) { c.set(Edit.setMine(c.d, k, it.toInt())) }
        } else {
            val avg = Edit.subAvg(subs, k)
            BasicText(
                buildAnnotatedString {
                    append("$label ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append(if (avg > 0) Edit.jsNum(avg) else if (v > 0) Edit.jsNum(v) else Queue.DASH) }
                },
                style = Type.head, modifier = Modifier.padding(top = 6.dp),
            )
            for ((sk, sl) in Web.SUBRATE[k].orEmpty()) {
                val sv = Backup.num(subs?.get("$k:$sk"), 0.0, 0.0, 10.0)
                Label(sl)
                Slide(sv, 0.0, 10.0, 1.0, if (sv > 0) Edit.jsNum(sv) else Queue.DASH, sl) { c.set(Edit.setSub(c.d, k, sk, it.toInt())) }
            }
        }
    }
}

@Composable
private fun PlayedGroup(c: Ctx, rec: io.github.shamlawy.playnext.data.Played, now: () -> Long, unplay: () -> Unit) {
    val p = c.d.p ?: return
    val dk = p["dk"]?.let { Backup.truthy(it) } ?: Backup.truthy(rec.o["dk"])
    Label("What it actually deserved")
    val real = p["real"]?.let { Backup.num(it, 5.0, 0.0, 10.0) } ?: if (Edit.scored(rec.o)) Backup.num(rec.o["real"], 5.0, 0.0, 10.0) else 5.0
    Slide(real, 0.0, 10.0, .5, Edit.realSay(rec, c.d), "What it actually deserved") { v -> c.set(c.d.rec("real", Backup.jsNumber(v)).rec("dk", JsonPrimitive(false))) }
    Toggles(DK, { dk }, icon = { "ui:unsure" }) { c.set(c.d.rec("dk", JsonPrimitive(!dk)).rec("real", null)) }
    Note("You hyped it ${Backup.str(c.g["hype"]).takeIf { Backup.truthy(c.g["hype"]) } ?: Queue.DASH}/10 before playing.")
    Label("Times through")
    val runs = Backup.num(p["runs"], Backup.num(rec.o["runs"], 1.0, 1.0, 12.0), 1.0, 12.0)
    Slide(runs, 1.0, 12.0, 1.0, "${runs.toInt()}×", "Times through") { c.set(c.d.rec("runs", JsonPrimitive(it.toInt()))) }
    Label("When you finished it")
    val today = Edit.today(now(), c.q.zone)
    val fin = Backup.str(p["fin"]).ifEmpty { Backup.str(rec.o["t"]) }
    val fd = runCatching { LocalDate.parse(fin.take(10)) }.getOrNull()
    var year by rememberSaveable { mutableIntStateOf(fd?.year ?: today.year) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow("Year before", true) { year-- }
        BasicText(year.toString(), style = Type.name.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
        Arrow("Year after", false, enabled = year < today.year) { year++ }
    }
    MonthGrid(if (fd != null && fd.year == year) fd.monthValue else 0, { m -> year > today.year || (year == today.year && m > today.monthValue) }) { m ->
        c.set(c.d.rec("fin", JsonPrimitive(Edit.finDate(fin, year, m, today))))
    }
    Note("Pick the month. Your monthly and yearly recaps count the game there.")
    Label("What got you about it")
    Note("Pick as many as fit, or none. This is the only thing here that isn't a score — it's what turns \"you rate RPGs highly\" into \"you rate games with characters you care about highly\".")
    val why = (c.d["why"] as? JsonArray)?.map { Backup.str(it) } ?: emptyList()
    for ((head, list) in Web.WHY_GROUPS) {
        BasicText(head, style = Type.head, modifier = Modifier.padding(top = 4.dp))
        Toggles(list, { it in why }) { k ->
            val cur = (c.d["why"] as? JsonArray)?.map { Backup.str(it) } ?: emptyList()
            c.set(c.d.set("why", JsonArray((if (k in cur) cur - k else cur + k).map(::JsonPrimitive))))
        }
    }
    Edit.taggedSay(c.lib)?.let { Note(it) }
    Label("Want another run at it")
    val again = p["again"]?.let { Backup.truthy(it) } ?: Backup.truthy(rec.o["again"])
    Chips(AGAIN, if (again) "yes" else "no") { c.set(c.d.rec("again", JsonPrimitive(it == "yes"))) }
    EBtn("Move back to the queue", Kind.Plain) { unplay() }
}

private val DK = listOf("dk" to "Don't remember")

// ---- Look ----

/** The game as the draft would leave it, for Look's preview and pictures. */
private fun drafted(c: Ctx): Game = Game(JsonObject(c.g.o + Edit.collect(c.g, c.d)))

@Composable
private fun LookPreview(g: Game, d: Edit.Draft) {
    val pg = remember(g, d) { Game(JsonObject(g.o + Edit.collect(g, d))) }
    val accent = Game.hex(Backup.str(pg["colour"]))?.let { Color(it) } ?: Color(pg.colour)
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(150.dp).clip(Pn.r).border(1.dp, Pn.line, Pn.r)) {
        Hero(pg, 150.dp, false, accent)
        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp).width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).border(1.dp, Pn.ice.copy(alpha = .4f), RoundedCornerShape(8.dp))) {
            PicBox(Pic.cover(pg), pg, Modifier.fillMaxSize(), ground = Color(Web.COVER_BG), align = framing(pg, "imgX", "imgY", 50.0))
        }
    }
}

private fun framing(g: Game, x: String, y: String, dy: Double) = androidx.compose.ui.BiasAlignment(
    (Backup.num(g[x], 50.0, 0.0, 100.0) / 50.0 - 1).toFloat(), (Backup.num(g[y], dy, 0.0, 100.0) / 50.0 - 1).toFloat(),
)

@Composable
private fun CoverGroup(c: Ctx) {
    val pg = drafted(c)
    Label("Colour")
    Swatches(c)
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append("Three pictures, three jobs.\n") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append("Cover") }
            append(" — the box art. Shows in the queue, the library and the calendar. Every game needs one.\n")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append("Backdrop") }
            append(" — the wide scene filling the back of this game's page and behind the queue. No cut-out needed.\n")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append("Logo / character art") }
            append(" — the small cut-out or logo floating on the queue card. Transparent PNG.")
        },
        style = Type.note,
    )
    Label("Cover")
    val img = Backup.str(pg["img"])
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(84.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
            PicBox(Pic.cover(pg), pg, Modifier.fillMaxSize(), ground = Color(Web.COVER_BG), align = framing(pg, "imgX", "imgY", 50.0))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Note(
                when {
                    Backup.truthy(c.d["imgCleared"]) && img.isEmpty() -> "Removed — tap Save"
                    img.startsWith("data:") -> "Uploaded · about ${Math.round(img.length * .75 / 1024)} KB"
                    img.isNotEmpty() -> "From a link"
                    else -> "None — showing a placeholder"
                },
            )
            EBtn("Remove cover", Kind.Plain, enabled = img.isNotEmpty()) {
                c.set(c.d.setAll(mapOf("img" to JsonPrimitive(""), "imgCleared" to JsonPrimitive(true), "in:img" to JsonPrimitive(""))))
                c.say("Cover removed — tap Save.")
            }
        }
    }
    Label("Framing — which part of the picture to keep")
    val ix = Backup.num(pg["imgX"], 50.0, 0.0, 100.0)
    Slide(ix, 0.0, 100.0, 1.0, "${ix.toInt()}%", "Framing across") { c.set(c.d.set("imgX", JsonPrimitive(it.toInt()))) }
    Note("Left to right. The preview above moves as you drag.")
    val iy = Backup.num(pg["imgY"], 50.0, 0.0, 100.0)
    Slide(iy, 0.0, 100.0, 1.0, "${iy.toInt()}%", "Framing down") { c.set(c.d.set("imgY", JsonPrimitive(it.toInt()))) }
    Note("Top to bottom. Useful when you've pasted wide key art and the faces sit high in it.")
    EBtn("Back to centre", Kind.Plain) { c.set(c.d.setAll(mapOf("imgX" to JsonPrimitive(50), "imgY" to JsonPrimitive(50)))) }
    LinkRow(c, "img", "cover", "img", "https://… or a Steam link")
    Note("Paste anything here — a direct image link, a github.com page, or a Steam store link or app ID. A Steam link pulls the right file for whichever picture this box is for. Links cost no storage and keep the picture at full size, so they beat uploading for anything large.")
}

/** The colour row (`swatchRow`): the palette round the colour wheel, greys last; a pick is yours (`colourHand`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(c: Ctx) {
    val cur = Game.hex(c.d.str("colour"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (col in Edit.PALETTE_ROW) {
            val h = Edit.hex(col)
            val on = col == cur
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.RadioButton) {
                    c.set(c.d.setAll(mapOf("colour" to JsonPrimitive(h), "colourHand" to JsonPrimitive(true))))
                }.semantics { selected = on; contentDescription = "Colour $h" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).background(Color(col), Pn.pill).then(if (on) Modifier.border(3.dp, Pn.ice, Pn.pill) else Modifier.border(1.dp, Pn.line2, Pn.pill)))
            }
        }
    }
}

/**
 * A picture's link box (`linkRow` + `bindLink`): paste, tap Use this link; a Steam page or app id takes the right file,
 * a github page its raw file. The picture is tried before it's kept, and the line under says how it went.
 */
@Composable
private fun LinkRow(c: Ctx, box: String, role: String, field: String, hint: String) {
    val had = Backup.str(c.g[field]).takeIf { it.startsWith("http") } ?: ""
    Field(c, box, "Link — an image, a Steam page, or a github page", had, hint = hint)
    var line by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by rememberUpdatedState(c)
    EBtn("Use this link", Kind.Plain) {
        when (val l = Edit.link(c.d.typed(box, had), role)) {
            is Edit.Link.Bad -> line = l.why
            is Edit.Link.Use -> {
                line = "Trying it…"
                now.set(now.d.setAll(Edit.useLink(field, box, l)))
                scope.launch {
                    val r = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(picUrl(l.url)).build())
                    line = when (r) {
                        is SuccessResult -> l.note + Edit.sizeSay(r.image.width, r.image.height)
                        is ErrorResult -> if (l.steamId != null) "Steam has no ${Edit.steamFile(role)} for app ${l.steamId}. Try a different picture, or paste a direct image link."
                        else "Saved, but that link didn't load for me — it may be a web page rather than the image itself, or blocked."
                    }
                }
            }
        }
    }
    line?.let { Note(it) }
}

@Composable
private fun BackdropGroup(c: Ctx) {
    val pg = drafted(c)
    Note("The wide scene behind this game. Left alone it falls back to Steam's artwork, then the cover. A landscape shot works best — it gets blurred and faded, so detail doesn't matter.")
    val back = Pic.backdrop(pg)
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(Web.GD_BG))) {
        if (back != null) PicBox(back, pg, Modifier.fillMaxSize(), ground = Color(Web.GD_BG), align = framing(pg, "bgX", "bgY", 50.0))
    }
    Note("Framing — which part of the scene to keep.")
    val bx = Backup.num(pg["bgX"], 50.0, 0.0, 100.0)
    Slide(bx, 0.0, 100.0, 1.0, "${bx.toInt()}%", "Backdrop framing across") { c.set(c.d.set("bgX", JsonPrimitive(it.toInt()))) }
    val by = Backup.num(pg["bgY"], 50.0, 0.0, 100.0)
    Slide(by, 0.0, 100.0, 1.0, "${by.toInt()}%", "Backdrop framing down") { c.set(c.d.set("bgY", JsonPrimitive(it.toInt()))) }
    EBtn("Back to centre", Kind.Plain) { c.set(c.d.setAll(mapOf("bgX" to JsonPrimitive(50), "bgY" to JsonPrimitive(50)))) }
    LinkRow(c, "back", "back", "hero", "https://… a wide shot, or a Steam link")
    val off = Backup.truthy(c.d["bgOff"])
    var said by remember { mutableStateOf<String?>(null) }
    said?.let { Note(it) }
    EBtn(if (off) "Use a backdrop again" else "No backdrop on this game", Kind.Plain) {
        if (off) {
            c.set(c.d.set("bgOff", JsonPrimitive(false)))
            said = "Backdrop back on. Tap Save."
        } else {
            c.set(c.d.setAll(mapOf("bgOff" to JsonPrimitive(true), "hero" to JsonPrimitive(""), "in:back" to JsonPrimitive(""))))
            said = "Backdrop off. Tap Save."
        }
    }
}

@Composable
private fun LogoGroup(c: Ctx) {
    val art = Backup.str(c.g["art"])
    Note("A transparent PNG that floats over the card. RAWG has no cut-outs or logos, so this one is on you" + (if (art.isNotEmpty()) " — currently about ${Math.round(art.length * .75 / 1024)} KB" else "") + ".")
    LinkRow(c, "art", "art", "art", "https://… a transparent PNG, or a Steam link")
    val now = Backup.str(Edit.collect(c.g, c.d)["art"])
    // on a checker, so a light or a dark cut-out both show (`.cutprev`)
    Box(
        Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)).drawBehind {
            val s = 12.dp.toPx()
            drawRect(Pn.card)
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else s
                while (x < size.width) { drawRect(Pn.line2, Offset(x, y), Size(s, s)); x += 2 * s }
                y += s
                row++
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (now.isNotEmpty()) AsyncImage(model = picUrl(now), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp))
    }
    if (Backup.truthy(c.d["artCleared"])) Note("Art will be removed when you save.")
    EBtn("Remove art", Kind.Plain, enabled = now.isNotEmpty()) {
        c.set(c.d.setAll(mapOf("art" to JsonPrimitive(""), "artCleared" to JsonPrimitive(true), "in:art" to JsonPrimitive(""))))
    }
}
