package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Prices
import io.github.shamlawy.playnext.data.Queue
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.WhatsNew
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Show me (android-v44, the owner, Nov 15: "the tests you want me to make please make it easier for me … build nexi
 * guide where it used to take you for the updates"): the web's nxTour opened the right screen for each update; here a
 * release's card in What's new has Show me, which opens the place its Try it is about (whatsnew.txt's `go`, with a game
 * from your own library that has what the release changed), and a Try it bar stays at the foot while you test it, with
 * It works / Something's off. Something's off goes to Claude as a "Reported" problem (Reporter.user, with the screen
 * and the last taps); It works goes to the log (kind `tried`, bugsync's LOG_ONLY). Each answer is kept on this phone, so
 * What's new shows what you've tried and Settings counts what's left.
 */
internal object Tried {
    private const val KEY = "tried"

    /** Read before every look, so a mark redraws what shows it. */
    private var rev by mutableIntStateOf(0)

    /** Release -> "ok" (It works) or "off" (Something's off). */
    fun all(): Map<Int, String> {
        rev
        if (!Store.ready()) return emptyMap()
        return Store.prefs.getString(KEY, "").orEmpty().split(',').mapNotNull { p ->
            val (v, how) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toIntOrNull()?.let { it to how }
        }.toMap()
    }

    fun mark(v: Int, how: String) {
        if (!Store.ready()) return
        val next = all() + (v to how)
        // the newest 60 are plenty: older releases are long tested
        Store.prefs.edit().putString(KEY, next.entries.sortedByDescending { it.key }.take(60).joinToString(",") { "${it.key}:${it.value}" }).apply()
        rev++
    }

    /**
     * The newest releases with a place to show and no answer yet (Settings' "N to try", the bar's Next): only the last
     * [RECENT], never one whose place is What's new itself (the guide's own release: you're using it).
     */
    fun left(list: List<WhatsNew.Entry>): List<WhatsNew.Entry> {
        val done = all()
        return list.sortedByDescending { it.v }.take(RECENT).filter { Guide.shows(it) && it.go != "news" && it.v !in done }
    }

    const val RECENT = 5
}

/** Where Show me goes: whatsnew.txt's `go` words (its header lists them). */
internal object Guide {
    /**
     * The words Show me knows; `home` (every block before android-v33) shows no button, it was never a place to test;
     * `top` is Home's own top (Phase 3, android-v46), a place to test now; `reel` plays the opening reel where you are
     * (android-v53, [Reel.replay]).
     */
    private val PLACES = setOf("queue", "card:out", "flip", "settings", "probe", "news", "top", "reel")
    private val GAMES = setOf("playing", "about", "unplayed", "priced", "next")

    /** A `go` whatsnew.txt may use (ReleaseFeedTest holds every new block to it). */
    fun known(go: String) = go == "home" || go in PLACES || go.startsWith("game:") && go.removePrefix("game:") in GAMES

    /** Whether a card gets Show me: not `home`, and not `news` (What's new's own release: you're already there). */
    fun shows(e: WhatsNew.Entry) = known(e.go) && e.go != "home" && e.go != "news"

    /** Nexi's words as he takes you there: the Try it's first step, short. */
    fun line(e: WhatsNew.Entry): String {
        val t = News.words(e.tryIt).text
        val first = t.substringBefore(": ").substringBefore(". ").trim().trimEnd('.')
        return "Let's try v${e.v}: " + (if (first.length > 90) first.take(88).trimEnd() + "…" else first)
    }

    /**
     * The game for `game:<what>`: one you're playing, the longest About, one you haven't played that's out, one with
     * prices, or On deck's first (`next`). Falls back to On deck's first, then any game.
     */
    fun game(lib: Library, what: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Game? {
        val rk = Rank(lib, now, zone)
        val all = lib.games.filter { !it.comp }.ifEmpty { lib.games }
        fun waiting(g: Game) = !rk.isPlayed(g) && !rk.isDropped(g) && g.rawState != "played" && g.rawState != "playing"
        val pick = when (what) {
            "playing" -> all.firstOrNull { it.rawState == "playing" }
            "about" -> all.maxByOrNull { (it["about"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().length }
            "unplayed" -> all.firstOrNull { waiting(it) && !it.isUpcoming(now) } ?: all.firstOrNull(::waiting)
            "priced" -> all.firstOrNull { waiting(it) && Prices.of(it).isNotEmpty() && !it["own"].truthy() } ?: all.firstOrNull { waiting(it) && !it.isUpcoming(now) }
            else -> null
        }
        return pick ?: rk.onDeck(1).firstOrNull() ?: all.firstOrNull()
    }

    private fun kotlinx.serialization.json.JsonElement?.truthy() = io.github.shamlawy.playnext.data.Backup.truthy(this)

    /**
     * Where [e]'s Show me lands, from [nav] (What's new and what's under it close). The Queue's card targets open the
     * card on [pick] (its tab back to All when it doesn't hold the game, as Shuffle does) and light it, which also
     * brings it to the middle of the list.
     */
    fun nav(e: WhatsNew.Entry, nav: Nav, lib: Library?, pick: QueuePick?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Nav {
        val here = nav.go(nav.tab)
        val queue = nav.go("games").view("games", "queue")
        return when {
            e.go == "queue" -> queue
            e.go == "top" -> nav.go("home")
            e.go == "settings" -> here.open("settings")
            e.go == "probe" -> here.open("probe")
            e.go == "news" -> here.open("news:0")
            e.go.startsWith("game:") && lib != null -> game(lib, e.go.removePrefix("game:"), now, zone)?.let { queue.open("game:" + it.id) } ?: queue
            (e.go == "card:out" || e.go == "flip") && lib != null && pick != null -> {
                // the Queue as it shows (rememberQueueView): without the practice deletes, in the practice order
                val gone = pick.deleted.let { d -> if (pick.deletedFor === lib.from) d else emptySet() }
                val moved = pick.moved.let { m -> if (pick.movedFor === lib.from) m else emptyMap() }
                val base = lib.without(gone)
                val q = Queue(base, Rank(base, now, zone), now, zone)
                var list = q.sort(q.list(q.tabNow(pick.tab)), pick.sort, moved)
                val g = if (e.go == "card:out") q.sort(q.list("out"), pick.sort, moved).firstOrNull() ?: list.firstOrNull() else list.firstOrNull()
                if (g != null) {
                    if (list.none { it.id == g.id }) { pick.show("all"); list = q.sort(q.list("all"), pick.sort, moved) }
                    // already open (the first card is, at start): it stays as it is, so its entrance never replays later
                    if (pick.openIn(list) == g.id) { pick.focus = g.id; pick.collapsed = false } else pick.open(g.id)
                    if (e.go == "flip") pick.flip = g.id
                    // Character select: the card opens over the covers, whose grid stays where it is
                    if (lib.charSelect()) pick.sel = g.id else pick.lit = QueueLit(g.id, g.title, ++pick.taps)
                }
                queue
            }
            else -> nav.go("home")
        }
    }
}

/**
 * The Try it bar: at the foot while you test a release ([e]), over every screen but What's new. Its words open and
 * close with a tap on them (T60); Hide folds it into a small pill that brings it back. It works and Something's off
 * answer it; [next] is the next release left to try, if any.
 */
@Composable
internal fun TryBar(e: WhatsNew.Entry, next: WhatsNew.Entry?, modifier: Modifier, pillAt: Modifier, goNext: (WhatsNew.Entry) -> Unit, fold: Boolean = false, close: () -> Unit) {
    val look = LocalLook.current
    val scope = rememberCoroutineScope()
    var small by rememberSaveable(e.v) { mutableStateOf(false) }
    var open by rememberSaveable(e.v) { mutableStateOf(false) }
    var asking by rememberSaveable(e.v) { mutableStateOf(false) }
    var text by rememberSaveable(e.v) { mutableStateOf("") }
    var said by rememberSaveable(e.v) { mutableStateOf<String?>(null) }
    var sending by remember(e.v) { mutableStateOf(false) }
    // a tap on the pill while [fold] holds it there: open on purpose, until the bar hides again
    var unfolded by remember(e.v) { mutableStateOf(false) }
    // [fold]: a sheet is open or the Queue's Undo / line shows at the foot: the bar steps aside as its pill (a tap opens
    // it over them on purpose); while you type an answer it stays open
    if (small || fold && !asking && !unfolded) {
        BasicText(
            "Try it · v${e.v}",
            style = Type.label.copy(color = Pn.void),
            softWrap = false, maxLines = 1,
            modifier = pillAt
                .sizeIn(minHeight = Pn.touch)
                .background(look.volt, Pn.rs)
                .clickable(role = Role.Button, onClickLabel = "Show the Try it bar") { small = false; unfolded = true }
                .padding(horizontal = 16.dp, vertical = 15.dp),
        )
        return
    }
    Column(
        modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 16.dp)
            .background(Pn.card2, Pn.r).border(1.dp, Pn.line2, Pn.r).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Try it · v${e.v}", style = Type.label.copy(color = look.volt), softWrap = false, maxLines = 1, modifier = Modifier.weight(1f))
            // while your words are on their way the bar stays: closing it would cancel the send (second-look on #752)
            if (!sending) BasicText(
                "Hide", style = Type.label.copy(color = Pn.ice), softWrap = false, maxLines = 1,
                modifier = Modifier.sizeIn(minHeight = Pn.touch).clickable(role = Role.Button) { small = true; unfolded = false }.padding(horizontal = 12.dp, vertical = 15.dp),
            )
            if (!sending) BasicText(
                "Close", style = Type.label.copy(color = Pn.ice), softWrap = false, maxLines = 1,
                modifier = Modifier.sizeIn(minHeight = Pn.touch).clickable(role = Role.Button, onClick = close).padding(horizontal = 12.dp, vertical = 15.dp),
            )
        }
        val s = said
        if (s != null) {
            BasicText(s, style = Type.head, modifier = Modifier.padding(end = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (next != null) Btn("Next: v${next.v}", primary = true) { goNext(next) }
                Btn("Done", primary = next == null, onClick = close)
            }
            return@Column
        }
        // the words open and close with a tap on them (T60), never a Read more
        BasicText(
            News.words(e.tryIt), style = Type.note.copy(color = Pn.ice),
            maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp).clickable(onClickLabel = if (open) "Fold the steps" else "Show every step") { open = !open },
        )
        if (asking) {
            Box(
                Modifier.padding(end = 8.dp).fillMaxWidth().sizeIn(minHeight = 72.dp).background(Pn.pan, Pn.rs).border(1.dp, Pn.line2, Pn.rs).padding(12.dp),
            ) {
                if (text.isEmpty()) BasicText("What's off?", style = Type.body.copy(color = Pn.mute))
                BasicTextField(
                    value = text, onValueChange = { text = it.take(600) }, textStyle = Type.body, cursorBrush = SolidColor(look.volt),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What's off?" },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (sending) "Sending…" else "Send", primary = text.isNotBlank()) {
                    if (text.isBlank() || sending) return@Btn
                    sending = true
                    // marked first: closing the bar mid-send still keeps the answer (the report is queued either way)
                    Tried.mark(e.v, "off")
                    scope.launch {
                        val sent = Reporter.user("Try it v${e.v}, something's off: " + text.trim())
                        sending = false
                        said = if (sent) "Sent to Claude with this screen. Thanks!" else "Saved. It goes to Claude as soon as it can."
                    }
                }
                if (!sending) Btn("Cancel") { asking = false }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("It works", primary = true) {
                    Tried.mark(e.v, "ok")
                    Reporter.report("tried", "Try it v${e.v} works")
                    said = "Marked as working. Thanks!"
                }
                Btn("Something's off") { asking = true }
            }
        }
    }
}
