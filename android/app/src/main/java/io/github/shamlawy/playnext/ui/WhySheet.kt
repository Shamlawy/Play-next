package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Why
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs

/**
 * Why you'll love it (`#aiwhy`, index.html 14899-14985, the page's ⋯): the game's colour along the top, "Why you'll
 * love it" over its name, then "Reading your ratings…", the helper's verdict in a pill and up to five reasons, "Based on
 * N games you've rated."; Ask again and Close. Under 3 rated games it says so and asks nothing. A kept answer (your
 * scores unchanged) shows at once.
 */
@Composable
internal fun WhySheet(lib: Library, g: Game, close: () -> Unit) {
    val accent = Color(g.colour)
    // each ask (the first, Ask again) is a new run of the effect below
    var asks by remember(g.id) { mutableIntStateOf(0) }
    var shown by remember(g.id) { mutableStateOf<Why.Shown?>(Why.kept(lib, g)?.let(Why::shown)) }
    var wait by remember(g.id) { mutableStateOf(false) }
    var err by remember(g.id) { mutableStateOf<Pair<String, String>?>(null) }
    val few = remember(lib, g.id) { Why.rated(lib, g.id).size < 3 }
    LaunchedEffect(asks) {
        if (few || (asks == 0 && shown != null)) return@LaunchedEffect
        wait = true
        err = null
        shown = null
        try {
            shown = Why.shown(Why.ask(lib, g))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Why.Old) {
            err = "Your helper doesn't know this trick yet — it needs the new version." to "old"
        } catch (e: Exception) {
            err = "Couldn't reach the AI right now. Try again in a moment." to (e.message ?: e.javaClass.simpleName.ifEmpty { "error" })
        } finally {
            wait = false
        }
    }
    val q = DyType.q.copy(lineHeight = 21.sp)
    DyFrame(Modifier, "Why you'll love it", close) {
        // the game's colour along the top (`border-top: 3px solid var(--accent)`)
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent, RoundedCornerShape(2.dp)))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("Why you'll love it", style = Type.kicker.copy(fontWeight = FontWeight.Bold))
                BasicText(g.title, style = DyType.head, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        Column(Modifier.padding(top = 12.dp)) {
            val s = shown
            val e = err
            when {
                few -> BasicText("Rate at least 3 games you've finished and I can tell you how this one fits your taste.", style = q)
                wait -> BasicText("Reading your ratings…", style = q)
                e != null -> {
                    BasicText(e.first, style = q)
                    BasicText(e.second, style = DyType.small, modifier = Modifier.padding(top = 6.dp))
                }
                s != null -> {
                    if (s.verdict.isNotEmpty()) {
                        BasicText(
                            s.verdict, style = DyType.btn.copy(color = accent, fontWeight = FontWeight.Bold), maxLines = 1,
                            modifier = Modifier.padding(bottom = 12.dp).background(Pn.white.copy(alpha = .06f), Pn.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    for (p in s.points) {
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(5.dp).background(accent, Pn.pill))
                            BasicText(p, style = q.copy(color = Pn.ice), modifier = Modifier.weight(1f))
                        }
                    }
                    BasicText("Based on ${s.n} game${if (s.n == 1) "" else "s"} you've rated.", style = DyType.small, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!few) {
                HBtn("Ask again", enabled = !wait) {
                    Crumbs.tap("why: ask again", "why sheet")
                    Sounds.play(Sfx.Tap)
                    asks++
                }
            }
            HBtn("Close", onClick = close)
            Box(Modifier.width(1.dp))
        }
    }
}
