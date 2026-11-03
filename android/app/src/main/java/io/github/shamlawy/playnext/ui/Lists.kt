package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
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
 * up to 32 characters and an emoji), rename one, delete one (the games stay), or tap one to show it in the Queue.
 * A change shows at once and lasts until the app closes (`Games.customise`, a read-only practice copy until switch
 * day). Adding a game to a list (`openLists(gid)`, from a game page) comes with the game page in Phase 2.
 */
@Composable
fun ListsSheet(modifier: Modifier, games: Games.State, show: (String) -> Unit, close: () -> Unit) {
    val lib = (games as? Games.State.Ready)?.lib
    val lists = remember(lib) { lib?.let { Lists.of(it) }.orEmpty() }
    // the emoji for a new list moves on to the next one once a list is made (the web draws the sheet again)
    var emo by rememberSaveable(lists.size) { mutableStateOf(Lists.nextEmo(lists)) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameless by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var dropping by rememberSaveable { mutableStateOf<String?>(null) }
    val put = { l: List<Queue.QList> -> Games.customise("lists", Lists.json(l)) }
    val make = {
        if (name.isBlank()) {
            nameless = true
        } else {
            Sounds.play(Sfx.Add)
            Crumbs.tap("make list", "lists")
            put(Lists.made(lists, name, emo, Lists.newId(System.currentTimeMillis())))
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
            "Your own shelves. Pick one in the Queue's filter menu to see just those games.",
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
                        )
                    }
                }
            }
        }
        // a new list (`.ls-new`): the line above it, its label, the emoji, the name and Make
        Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp).background(Pn.steel))
        BasicText("New list", style = Type.kicker.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        EmojiGrid(emo) {
            if (it != emo) Sounds.play(Sfx.Tap)
            emo = it
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LsField(name, { name = it; nameless = false }, "e.g. Co-op with a friend", "New list's name", Modifier.weight(1f), make)
            LsBtn("Make", LsKind.Go, onClick = make)
        }
        if (nameless) BasicText("Give the list a name", style = Type.note.copy(color = Pn.hot), modifier = Modifier.padding(top = 6.dp))
    }
}

/** One list (`.ls-r`): its emoji, its name over "N games · show in queue" (a tap shows it), Rename, Delete. */
@Composable
private fun ListRow(l: Queue.QList, n: Int, show: () -> Unit, rename: () -> Unit, drop: () -> Unit) {
    LsRow {
        Emo(l.e)
        Column(
            Modifier.weight(1f).sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = show)
                .semantics { contentDescription = "${l.n}, $n game${if (n == 1) "" else "s"}, show in queue" },
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(l.n, style = DyType.name, maxLines = 1)
            BasicText(Lists.countLine(n), style = DyType.small, maxLines = 1)
        }
        LsBtn(null, LsKind.Plain, icon = "ui:edit", label = "Rename", onClick = rename)
        LsBtn(null, LsKind.Del, icon = "ui:trash", label = "Delete", onClick = drop)
    }
}

/** Renaming (`data-ren`): the name in a field and Save; a blank name keeps the old one. */
@Composable
private fun RenameRow(l: Queue.QList, save: (String) -> Unit) {
    var v by rememberSaveable(l.id) { mutableStateOf(l.n) }
    LsRow {
        Emo(l.e)
        LsField(v, { v = it }, "", "Name of " + l.n, Modifier.weight(1f)) { save(v) }
        LsBtn("Save", LsKind.Plain) { save(v) }
    }
}

/** The web asks with `confirm()`; here the row asks in place: Delete or Keep. */
@Composable
private fun DropRow(l: Queue.QList, drop: () -> Unit, keep: () -> Unit) {
    LsRow {
        Emo(l.e)
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

/** A list's emoji in its tile (`.ls-r .e`): 36 square, round 11, your accent at .12. */
@Composable
private fun Emo(e: String) {
    Box(Modifier.size(36.dp).background(LocalLook.current.volt.copy(alpha = .12f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        BasicText(e, style = TextStyle(fontSize = 19.sp).fixed(), maxLines = 1)
    }
}

/** `.ls-emo`: the twelve emoji in rows of six; the picked one has your accent's line and a tint. */
@Composable
private fun EmojiGrid(picked: String, pick: (String) -> Unit) {
    val volt = LocalLook.current.volt
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in Lists.EMO.chunked(6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (e in row) {
                    val on = e == picked
                    Box(
                        Modifier.weight(1f).height(Pn.touch).clickable(role = Role.RadioButton) { pick(e) }.semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(40.dp)
                                .background(if (on) volt.copy(alpha = .16f) else Pn.void, shape)
                                .border(1.dp, if (on) volt else Pn.steel, shape),
                            contentAlignment = Alignment.Center,
                        ) { BasicText(e, style = TextStyle(fontSize = 19.sp).fixed(), maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** `.ls-in`: a one-line field, at most 32 characters, Done on the keyboard does [done]. */
@Composable
private fun LsField(value: String, change: (String) -> Unit, hint: String, label: String, modifier: Modifier, done: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.background(Pn.void, shape).border(1.dp, Pn.steel, shape).padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
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
private object DyType {
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
internal fun DyFrame(modifier: Modifier, title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
            content = content,
        )
    }
}
