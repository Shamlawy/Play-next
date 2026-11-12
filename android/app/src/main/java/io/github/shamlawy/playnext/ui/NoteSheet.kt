package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Notes
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Where was I's card on the game page (`wiCard`, index.html 19290): the newest note under "Where you left off · <ago>"
 * (a welcome back after two days away), or for a game you're playing with none yet "Where are you up to?" with a plus.
 * Inside a page section it is a flat lift (`.gd-sec .wi`). A tap opens the note sheet.
 */
@Composable
fun WhereCard(g: Game, now: Long, open: () -> Unit, modifier: Modifier = Modifier) {
    val n = Notes.last(g)
    if (n == null && g.rawState != "playing") return
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.padding(top = 12.dp, bottom = 2.dp).fillMaxWidth().clip(shape)
            // in a page section both kinds are the same flat lift, no edge (`.gd-sec .wi, .gd-sec .wi.back`)
            .background(Color(Web.GD_WI))
            .clickable(role = Role.Button) {
                Crumbs.tap("where was I", "game page")
                Sounds.play(Sfx.Tap)
                open()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val volt = LocalLook.current.volt
        PnIcon("ui:pin", volt, 20.dp, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (n != null) {
                BasicText(Notes.head(n, now), style = Type.label.copy(fontSize = 12.sp, lineHeight = 16.sp, color = volt).fixed())
                BasicText(n.x, style = Type.body.copy(fontSize = 14.5.sp, lineHeight = 20.3.sp).fixed())
            } else {
                BasicText("Where are you up to?", style = Type.label.copy(fontSize = 12.sp, lineHeight = 16.sp, color = volt).fixed())
                BasicText("Leave yourself a note for next time", style = Type.note.copy(fontSize = 12.sp, lineHeight = 16.sp).fixed())
            }
        }
        Box(Modifier.size(30.dp).background(Color.White.copy(alpha = .06f), CircleShape), contentAlignment = Alignment.Center) {
            PnIcon(if (n != null) "ui:edit" else "ui:plus", Pn.mute, 16.dp, null)
        }
    }
}

/**
 * The note sheet (`wiSheet`, index.html 19297): "Where are you in <game>?", a tip, a box of up to 240 characters (focused
 * as the sheet opens), five quick chips that add to it, Save note, then your earlier notes newest first, each with its
 * date and a delete. [saved] runs after a save (the toast); an empty box only takes the focus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteSheet(g: Game, now: () -> Long, close: () -> Unit, saved: () -> Unit) {
    var box by rememberSaveable(g.id, stateSaver = TextFieldValue.Saver) { mutableTextFieldValue() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus.requestFocus() }
    }
    val volt = LocalLook.current.volt
    DyFrame(Modifier, "Where are you in ${g.title}?", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:pin", volt, 21.dp, null)
            BasicText("Where are you in ${g.title}?", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        BasicText(
            "A line for future you: the chapter, what you were about to do, anything to remember.",
            style = DyType.q, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        val shape = RoundedCornerShape(14.dp)
        Box(Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth().heightIn(min = 84.dp).background(Pn.void, shape).border(1.dp, Pn.steel, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (box.text.isEmpty()) BasicText("Chapter 6, about to fight the water boss. Buy fire potions first.", style = DyType.input.copy(fontSize = 15.sp, color = Pn.mute))
            BasicTextField(
                value = box,
                onValueChange = { box = it.copy(text = it.text.take(Notes.LONGEST)) },
                minLines = 3,
                textStyle = DyType.input.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(volt),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Your note" },
            )
        }
        FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (q in Web.WI_QUICK) {
                Box(
                    Modifier.sizeIn(minHeight = 32.dp).clip(Pn.pill).background(Pn.card).border(1.dp, Pn.line2, Pn.pill)
                        .clickable(role = Role.Button) {
                            Sounds.play(Sfx.Tap)
                            val t = Notes.chip(box.text, q).take(Notes.LONGEST)
                            box = TextFieldValue(t, TextRange(t.length))
                            runCatching { focus.requestFocus() }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(q, style = DyType.small.copy(fontSize = 12.5.sp, color = Pn.ice), maxLines = 1, softWrap = false) }
            }
        }
        Box(
            Modifier.fillMaxWidth().sizeIn(minHeight = Pn.touch).clip(Pn.pill).background(volt)
                .clickable(role = Role.Button) {
                    if (box.text.isBlank()) {
                        runCatching { focus.requestFocus() }
                    } else {
                        Crumbs.tap("save note", "note sheet")
                        Games.change(Notes.add(g, box.text, now()) { Games.practiced(it) })
                        Sounds.play(Sfx.Add)
                        saved()
                    }
                },
            contentAlignment = Alignment.Center,
        ) { BasicText("Save note", style = DyType.btn.copy(color = Pn.void)) }
        val old = Notes.of(g).reversed()
        if (old.isNotEmpty()) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("Earlier notes", style = DyType.small.copy(fontWeight = FontWeight.SemiBold))
                val zone = ZoneId.systemDefault()
                val year = Instant.ofEpochMilli(now()).atZone(zone).year
                for (n in old) {
                    Row(
                        Modifier.fillMaxWidth().background(Pn.card, shape).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(noteDate(n.t, zone, year), style = DyType.small.copy(fontSize = 11.sp, lineHeight = 15.sp))
                            BasicText(n.x, style = DyType.q.copy(fontSize = 13.5.sp, lineHeight = 19.sp, color = Pn.ice))
                        }
                        Box(
                            Modifier.size(Pn.touch).clickable(role = Role.Button) {
                                Sounds.play(Sfx.Back)
                                Games.change(Notes.drop(g, n.at) { Games.practiced(it) })
                            }.semantics { contentDescription = "Delete this note" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(32.dp).background(Color.White.copy(alpha = .05f), CircleShape), contentAlignment = Alignment.Center) {
                                PnIcon("ui:trash", Pn.mute, 16.dp, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mutableTextFieldValue() = TextFieldValue("")

/** An earlier note's date: day and short month in the phone's language, with the year when it isn't this one. */
private fun noteDate(t: Long, zone: ZoneId, year: Int): String {
    val d = Instant.ofEpochMilli(t).atZone(zone)
    val loc = Locale.getDefault()
    val skel = if (d.year == year) "dMMM" else "dMMMy"
    val pat = runCatching { android.text.format.DateFormat.getBestDateTimePattern(loc, skel) }.getOrDefault(if (d.year == year) "d MMM" else "d MMM y")
    return d.format(DateTimeFormatter.ofPattern(pat, loc))
}
