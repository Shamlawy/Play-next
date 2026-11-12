package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Lists
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs

/**
 * Your lists (`openLists(null)`, index.html 12872-12906), opened by the Queue bar's lists button: make one (a name of
 * up to 32 characters and a mark: a drawn icon, never an emoji), rename one, delete one (the games stay), or tap one to show it in the Queue.
 * A change shows at once and lasts until the app closes (`Games.customise`, a read-only practice copy until switch
 * day). A list's + (and a list you just made) opens [AddGames]: every game, tap to put it in the list or take it out
 * (the web's `openLists(gid)` turned round: one list, many games; the game page's own Lists button comes in Phase 2).
 */
@Composable
fun ListsSheet(modifier: Modifier, games: Games.State, show: (String) -> Unit, close: () -> Unit, top: Boolean = true) {
    val lib = (games as? Games.State.Ready)?.lib
    val lists = remember(lib) { lib?.let { Lists.of(it) }.orEmpty() }
    // the mark for a new list moves on to the next one once a list is made (the web draws the sheet again); it is the
    // web's stored emoji, which the sheet only ever draws as its icon
    var emo by rememberSaveable(lists.size) { mutableStateOf(Lists.nextEmo(lists)) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameless by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var dropping by rememberSaveable { mutableStateOf<String?>(null) }
    // the list whose games are being picked, and its name while a list just made is on its way into the copy
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var addingName by rememberSaveable { mutableStateOf("") }
    val put = { l: List<Queue.QList> -> Games.customise("lists", Lists.json(l)) }
    val over = { Games.practiced(Lists.IN) as? kotlinx.serialization.json.JsonObject }
    // back steps out of what's open in the sheet first (Phase 1 check A6): Add games, a rename, the delete question
    BackHandler(enabled = top && (adding != null || editing != null || dropping != null)) {
        // (as its own Done and Keep: Done sounds, a rename or the question just closes)
        if (adding != null) { Sounds.play(Sfx.Back); adding = null } else { editing = null; dropping = null }
    }
    // a list made in the practice copy is gone after Android closes the app in the background (the copy starts over,
    // the sheet's own state comes back): its Add games doesn't come back with it (Phase 1 check C2)
    val pickFor = adding?.takeIf { id -> lists.any { it.id == id } || Lists.holds(Games.practiced("lists"), id) }
    if (adding != null && pickFor == null && lib != null) LaunchedEffect(Unit) { adding = null }
    if (pickFor != null && lib != null) {
        AddGames(modifier, lib, pickFor, lists.firstOrNull { it.id == pickFor }?.let { it.n to it.e } ?: (addingName to emo), flip = { g ->
            Games.customise(Lists.IN, Lists.flipped(over(), g, pickFor))
        }, done = {
            Sounds.play(Sfx.Back)
            adding = null
        }, close = close)
        return
    }
    val make = {
        if (name.isBlank()) {
            nameless = true
        } else {
            Sounds.play(Sfx.Add)
            Crumbs.tap("make list", "lists")
            val id = Lists.newId(System.currentTimeMillis())
            val made = Lists.made(lists, name, emo, id)
            put(made)
            // straight on to its games: a new list is made to be filled
            addingName = made.last().n
            adding = id
            name = ""
            nameless = false
        }
    }
    DyFrame(modifier, "Your lists", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:list", LocalLook.current.volt, 21.dp, null)
            BasicText("Your lists", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        BasicText(
            "Your own shelves. Each one is a chip in the Queue's bar: tap it to see just those games.",
            style = DyType.q, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        if (lists.isEmpty()) {
            BasicText("No lists yet. Make your first one below.", style = DyType.q, modifier = Modifier.padding(top = 2.dp))
        } else {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in lists) {
                    when (l.id) {
                        editing -> RenameRow(l) { v ->
                            put(Lists.renamed(lists, l.id, v))
                            editing = null
                        }
                        dropping -> DropRow(
                            l,
                            drop = {
                                Sounds.play(Sfx.Back)
                                Crumbs.tap("delete list", "lists")
                                put(Lists.dropped(lists, l.id))
                                lib?.let { Games.customise(Lists.IN, Lists.without(over(), it.games, l.id)) }
                                dropping = null
                            },
                            keep = { dropping = null },
                        )
                        else -> ListRow(
                            l, lib?.let { Lists.count(it, l.id) } ?: 0,
                            show = {
                                Crumbs.tap("show list", "lists")
                                show("L:" + l.id)
                            },
                            rename = {
                                Sounds.play(Sfx.Tap)
                                editing = l.id
                                dropping = null
                            },
                            drop = {
                                Sounds.play(Sfx.Tap)
                                dropping = l.id
                                editing = null
                            },
                            add = {
                                Sounds.play(Sfx.Open)
                                Crumbs.tap("add games", "lists")
                                editing = null
                                dropping = null
                                adding = l.id
                            },
                        )
                    }
                }
            }
        }
        // a new list (`.ls-new`): the line above it, its label, the marks, the name and Make
        Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp).background(Pn.steel))
        BasicText("New list", style = Type.kicker.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        MarkGrid(emo) {
            if (it != emo) Sounds.play(Sfx.Tap)
            emo = it
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LsField(name, { name = it; nameless = false }, "e.g. Co-op with a friend", "New list's name", Modifier.weight(1f), done = make)
            LsBtn("Make", LsKind.Go, onClick = make)
        }
        if (nameless) BasicText("Give the list a name", style = Type.note.copy(color = Pn.hot), modifier = Modifier.padding(top = 6.dp))
    }
}

/** One list (`.ls-r`): its mark, its name over "N games · show in queue" (a tap shows it), Add games, Rename, Delete. */
@Composable
private fun ListRow(l: Queue.QList, n: Int, show: () -> Unit, rename: () -> Unit, drop: () -> Unit, add: () -> Unit) {
    LsRow {
        Mark(l.e)
        Column(
            Modifier.weight(1f).sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = show)
                .semantics { contentDescription = "${l.n}, $n game${if (n == 1) "" else "s"}, show in queue" },
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(l.n, style = DyType.name, maxLines = 1)
            BasicText(Lists.countLine(n), style = DyType.small, maxLines = 2)
        }
        LsBtn(null, LsKind.Plain, icon = "ui:plus", label = "Add games to " + l.n, onClick = add)
        LsBtn(null, LsKind.Plain, icon = "ui:edit", label = "Rename", onClick = rename)
        LsBtn(null, LsKind.Del, icon = "ui:trash", label = "Delete", onClick = drop)
    }
}

/**
 * Adding games to one list: every game in your order (a search when there are more than eight), each with its cover
 * and a tick when it's in; a tap puts it in or takes it out at once (`data-lt`'s toggle). Done goes back to your lists.
 * A lazy list: a big library draws only the rows on screen.
 */
@Composable
private fun AddGames(modifier: Modifier, lib: io.github.shamlawy.playnext.data.Library, listId: String, named: Pair<String, String>, flip: (io.github.shamlawy.playnext.data.Game) -> Unit, done: () -> Unit, close: () -> Unit) {
    val (n, e) = named
    var find by rememberSaveable(listId) { mutableStateOf("") }
    // a tap shows at once; the copy rebuilds off Main a moment later and then agrees
    val taps = remember(listId) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val inIt = { g: io.github.shamlawy.playnext.data.Game -> taps[g.id] ?: (listId in Lists.inLists(null, g)) }
    val all = lib.games
    val shown = remember(all, find) { find.trim().lowercase().let { f -> if (f.isEmpty()) all else all.filter { f in it.title.lowercase() } } }
    val count = all.count(inIt)
    DyFrame(modifier, "Add to $n", close, lazy = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mark(e)
            BasicText("Add to $n", style = DyType.head, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        BasicText(
            "Tap a game to put it in this list, or tap again to take it out. $count game${if (count == 1) "" else "s"} in it.",
            style = DyType.q, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        if (all.size > 8) {
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LsField(find, { find = it }, "Search your games", "Search your games", Modifier.weight(1f), icon = "ui:search") {}
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown.isEmpty()) item("none") { BasicText(if (all.isEmpty()) "No games yet." else "No game called that.", style = DyType.q) }
            items(shown.size, key = { shown[it].id }) { i ->
                val g = shown[i]
                val on = inIt(g)
                PickRow(g, on) {
                    Sounds.play(if (on) Sfx.Back else Sfx.Add)
                    Crumbs.tap(if (on) "out of list" else "into list", "lists")
                    taps[g.id] = !on
                    flip(g)
                }
            }
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LsBtn("Done", LsKind.Go, onClick = done)
        }
    }
}

/** One game in the picker: its cover, its name, and a tick (in the list) or a plus (not yet); the whole row toggles. */
@Composable
private fun PickRow(g: io.github.shamlawy.playnext.data.Game, on: Boolean, toggle: () -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = Pn.touch)
            .background(if (on) volt.copy(alpha = .1f) else Pn.void, shape).border(1.dp, if (on) volt.copy(alpha = .6f) else Pn.steel, shape)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { toggle() })
            .semantics { contentDescription = g.title }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicBox(io.github.shamlawy.playnext.data.Pic.cover(g), g, Modifier.size(width = 33.dp, height = 44.dp).clip(RoundedCornerShape(7.dp)), ground = androidx.compose.ui.graphics.Color(io.github.shamlawy.playnext.gen.Web.COVER_BG))
        BasicText(g.title, style = DyType.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        PnIcon(if (on) "ui:tick" else "ui:plus", if (on) volt else Pn.mute, 24.dp, null)
    }
}

/** Renaming (`data-ren`): the name in a field and Save; a blank name keeps the old one. */
@Composable
private fun RenameRow(l: Queue.QList, save: (String) -> Unit) {
    var v by rememberSaveable(l.id) { mutableStateOf(l.n) }
    LsRow {
        Mark(l.e)
        LsField(v, { v = it }, "", "Name of " + l.n, Modifier.weight(1f)) { save(v) }
        LsBtn("Save", LsKind.Plain) { save(v) }
    }
}

/** The web asks with `confirm()`; here the row asks in place: Delete or Keep. */
@Composable
private fun DropRow(l: Queue.QList, drop: () -> Unit, keep: () -> Unit) {
    LsRow {
        Mark(l.e)
        BasicText("Delete “${l.n}”? The games stay, only the list goes.", style = DyType.small.copy(color = Pn.ice), modifier = Modifier.weight(1f))
        LsBtn("Delete", LsKind.Del, onClick = drop)
        LsBtn("Keep", LsKind.Plain, onClick = keep)
    }
}

@Composable
private fun LsRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().background(Pn.void, shape).border(1.dp, Pn.steel, shape).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A list's mark in its tile (`.ls-r .e`): 36 square, round 11, your accent at .12, its drawn icon in your accent. */
@Composable
private fun Mark(e: String) {
    val volt = LocalLook.current.volt
    Box(Modifier.size(36.dp).background(volt.copy(alpha = .12f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        PnIcon(Lists.icon(e), volt, 20.dp, null)
    }
}

/** `.ls-emo`: the twelve marks in rows of six, drawn icons in white; the picked one is in your accent with its line and a tint. */
@Composable
private fun MarkGrid(picked: String, pick: (String) -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in Lists.MARKS.chunked(6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in row) {
                    val on = m.emo == picked
                    Box(
                        Modifier.weight(1f).height(Pn.touch).clickable(role = Role.RadioButton) { pick(m.emo) }
                            .semantics { selected = on; contentDescription = m.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(40.dp)
                                .background(if (on) volt.copy(alpha = .16f) else Pn.void, shape)
                                .border(1.dp, if (on) volt else Pn.steel, shape),
                            contentAlignment = Alignment.Center,
                        ) { PnIcon(m.icon, if (on) volt else Pn.ice, 21.dp, null) }
                    }
                }
            }
        }
    }
}

/** `.ls-in`: a one-line field, at most 32 characters, Done on the keyboard does [done]. */
@Composable
private fun LsField(value: String, change: (String) -> Unit, hint: String, label: String, modifier: Modifier, icon: String? = null, done: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.background(Pn.void, shape).border(1.dp, Pn.steel, shape)
            .padding(start = if (icon != null) 38.dp else 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (icon != null) PnIcon(icon, Pn.mute, 18.dp, null, Modifier.offset(x = (-27).dp))
        if (value.isEmpty() && hint.isNotEmpty()) BasicText(hint, style = DyType.input.copy(color = Pn.mute), maxLines = 1, softWrap = false)
        BasicTextField(
            value = value,
            onValueChange = { change(it.take(Lists.MAX)) },
            singleLine = true,
            textStyle = DyType.input,
            cursorBrush = SolidColor(LocalLook.current.volt),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

private enum class LsKind { Plain, Go, Del }

/** `.ls-b`: 36 tall, round 12, bold; Make in your accent, Delete on --hot at .14. Its touch area is 48. */
@Composable
private fun LsBtn(text: String?, kind: LsKind, icon: String? = null, label: String? = null, onClick: () -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(12.dp)
    val bg = when (kind) {
        LsKind.Go -> volt
        LsKind.Del -> Pn.hot.copy(alpha = .14f)
        LsKind.Plain -> Pn.white.copy(alpha = .07f)
    }
    val ink = if (kind == LsKind.Go) Pn.void else Pn.ice
    Box(
        Modifier.sizeIn(minWidth = Pn.touch, minHeight = Pn.touch).clickable(role = Role.Button, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(36.dp).sizeIn(minWidth = 36.dp).background(bg, shape).padding(horizontal = if (text != null) 12.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) PnIcon(icon, ink, 18.dp, null)
            if (text != null) BasicText(text, style = DyType.btn.copy(color = ink), maxLines = 1, softWrap = false)
        }
    }
}

/** The dy sheets' words (index.html 2525-2538, 16117-16136). */
internal object DyType {
    private val base = TextStyle(fontFamily = Fonts.poppins, color = Pn.ice)
    val head = base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val x = base.copy(fontSize = 15.sp, lineHeight = 20.sp, color = Pn.mute)
    val q = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Pn.mute)
    val name = base.copy(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val small = base.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Pn.mute)
    val input = base.copy(fontSize = 16.sp, lineHeight = 22.sp)
    val btn = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
}

/**
 * The web's bottom sheet (`#diary .dy-in`): at most 520 wide and 85% tall, `--ink`, its top corners round 24, over
 * black at .6; a tap round it closes, so does a pull down past a quarter of the screen (`dyBox`, plan Oct 26). It
 * rises over the keyboard.
 */
@Composable
internal fun DyFrame(modifier: Modifier, title: String, close: () -> Unit, lazy: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(Pn.slab.copy(alpha = .6f)).scrimTap(close).imePadding()) {
        val pull = rememberPull(close, constraints.maxHeight.toFloat())
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        val tall: Dp = maxHeight * .85f
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = tall)
                .graphicsLayer { translationY = pull.offset }
                .background(Pn.ink, shape)
                // taps on the sheet are the sheet's: only the scrim round it closes
                .ownTaps()
                .semantics { paneTitle = title }
                .nestedScroll(pull.connection)
                // a sheet holding its own lazy list scrolls that list instead of itself
                .then(if (lazy) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .windowInsetsPadding(WindowInsets.navigationBars)
                // and the taskbar's room when Android doesn't report it (Taskbar)
                .padding(bottom = overNavBar(LocalBottomRoom.current))
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
            content = content,
        )
    }
}
