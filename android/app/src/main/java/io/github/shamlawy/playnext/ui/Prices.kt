package io.github.shamlawy.playnext.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shamlawy.playnext.data.Art
import io.github.shamlawy.playnext.data.Backup
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Page
import io.github.shamlawy.playnext.data.Prices
import io.github.shamlawy.playnext.data.Rank
import io.github.shamlawy.playnext.data.Reviews
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.report.Crumbs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The phone's shop region (`pxCC`): yours from the backup, else the time zone's, else the phone's country. */
@Composable
private fun shopCC(lib: Library): String {
    val country = LocalLocale.current.platformLocale.country
    return Art.cc(lib, java.util.TimeZone.getDefault().id, country)
}

/** Open a store's page in the browser. */
private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Prices on the game page (`gdPrices` + `pxCard`, index.html 19604, 21977): one row of store chips (PS Store, Steam,
 * eShop; each its logo and price, the old one and "−N%" on a sale), the best deal ringed in your accent (PS Plus's in
 * green), the line under them saying why it wins, when the sale ends, the lowest seen and the currency it's shown in,
 * opening the Prices sheet. Shown only with a price, ✓ Plus or "checking"; a game you own says so instead (M4). The
 * page asks for its prices 700 ms after it opens when they're missing or old ([Prices.due]).
 */
@Composable
internal fun PricesSec(g: Game, page: Page, lib: Library, modifier: Modifier) {
    val tick by Prices.seen.collectAsState()
    val now = LocalNow.current
    val cc = shopCC(lib)
    val rk = page.q.rk
    LaunchedEffect(g.id) {
        delay(700)
        if (askOnOpen && Prices.due(lib, rk, g, now())) Prices.fetch(lib, rk, g, cc, now)
    }
    // keyed on the counter, so an answer or a busy state builds a new card (and new chips) and nothing else does
    val card = remember(tick, lib, rk, g, cc) { Prices.card(lib, rk, g, cc) }
    if (!Prices.show(card)) return
    card!!
    val edit = LocalEditActs.current
    val ctx = LocalContext.current
    val volt = LocalLook.current.volt
    val green = Color(Web.PX_GREEN)
    Section("ui:tag", "Prices", "", modifier) {
        if (card.own) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PnIcon("ui:tick", green, 18.dp, null)
                BasicText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append("You own it") }
                        append(" · no price checks.")
                    },
                    style = Type.note.copy(fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
            return@Section
        }
        val one = card.chips.size == 1
        // three stores, or a long price among them: the tight look (`.px-l.tight`, `@container (max-width: 400px) .n3`)
        val tight = card.chips.size > 2
        Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 8.dp)) {
            for (c in card.chips) Chip(c, one, tight, volt, green, Modifier.weight(1f)) {
                Crumbs.tap("price chip " + c.s, "game page")
                Sounds.play(Sfx.Tap)
                openUrl(ctx, Prices.url(lib, g, c.s, cc))
            }
        }
        // `.px-more`: why the ringed one wins, then "Price alerts ›" (the sheet)
        Row(
            Modifier.padding(top = 6.dp).fillMaxWidth().sizeIn(minHeight = Pn.touch)
                .clickable(role = Role.Button) { Crumbs.tap("prices sheet", "game page"); Sounds.play(Sfx.Open); edit?.prices(g.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(card.cap, style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            BasicText("Price alerts ›", style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = volt), maxLines = 1, softWrap = false)
        }
    }
}

/** `<u>`: a currency code beside the digits ("AED 249") drawn small, so the digits read. */
private fun priceText(t: String): AnnotatedString = buildAnnotatedString {
    val m = Regex("^([A-Z]{2,4})[\\s\\u00A0]?(?=\\d)|(?<=\\d)[\\s\\u00A0]?([A-Z]{2,4})$").findAll(t).toList()
    var at = 0
    for (x in m) {
        append(t.substring(at, x.range.first))
        withStyle(SpanStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)) { append(" " + (x.groupValues[1].ifEmpty { x.groupValues[2] }) + " ") }
        at = x.range.last + 1
    }
    append(t.substring(at))
}

/** `.px-c`: a store's chip (46 tall, round 15): its logo, its name when it's the only one, the old price, the price. */
@Composable
private fun Chip(c: Prices.Chip, one: Boolean, tight: Boolean, volt: Color, green: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    val ground = Color(Web.PX_CHIP)
    val ring = when { c.incl -> green.copy(alpha = .55f); c.best -> volt.copy(alpha = .65f); else -> Pn.line }
    val fill = when {
        c.incl -> Brush.linearGradient(0f to green.copy(alpha = .18f), .75f to ground)
        c.best -> Brush.linearGradient(0f to volt.copy(alpha = .2f), .75f to ground)
        else -> Brush.linearGradient(listOf(ground, ground))
    }
    val logo = when { c.incl -> green; c.best -> volt; else -> Pn.ice.copy(alpha = .92f) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).background(fill, shape).border(1.dp, ring, shape)
                .clickable(role = Role.Button, onClick = onClick).clearAndSetSemantics { contentDescription = c.tip }
                .padding(horizontal = if (one) 14.dp else if (tight) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (one) Arrangement.spacedBy(7.dp) else Arrangement.spacedBy(if (tight) 5.dp else 7.dp, Alignment.CenterHorizontally),
        ) {
            PnIcon("px:" + c.s, logo, if (tight) 15.dp else 17.dp, null)
            if (one) BasicText(Web.PX_NAME[c.s] ?: "", style = Type.note.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (one && c.old.isNotEmpty()) BasicText(c.old, style = Type.note.copy(fontSize = 12.5.sp, textDecoration = TextDecoration.LineThrough), maxLines = 1, softWrap = false)
            val fit = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = if (tight) 13.5.sp else 15.sp, stepSize = .5.sp)
            when (c.kind) {
                Prices.Kind.Price -> BasicText(priceText(c.label), style = Type.stat.copy(fontSize = if (tight) 13.5.sp else 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = Pn.ice), maxLines = 1, softWrap = false, autoSize = fit)
                Prices.Kind.Plus -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    PnIcon("ui:tick", green, 14.dp, null)
                    BasicText(c.label, style = Type.stat.copy(fontSize = if (tight) 13.5.sp else 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = green), maxLines = 1, softWrap = false)
                }
                else -> BasicText(c.label, style = Type.note.copy(fontSize = 12.5.sp), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.5.sp, stepSize = .5.sp))
            }
        }
        // `.px-c em`: the sale badge, top right over the edge
        if (c.pct > 0) {
            BasicText(
                "−${c.pct}%",
                style = Type.note.copy(fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = Color(Web.PX_ON_GREEN)),
                maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = (-8).dp).background(green, Pn.pill).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The Prices sheet (`pxSheet`, index.html 19655): a row per store (checked when, its price or why there's none, the
 * store's page), the region and currency the prices are for, and Check this game now. Read only until switch day:
 * "Not on this store", "Watch it again" and a pasted store link write the game, so they wait (Phase 9 has the alerts).
 */
@Composable
internal fun PricesSheet(lib: Library, g: Game, now: () -> Long, close: () -> Unit) {
    val tick by Prices.seen.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cc = shopCC(lib)
    val rk = remember(lib) { Rank(lib, now(), java.time.ZoneId.systemDefault()) }
    val busy = tick.let { Prices.busy(g) }
    val err = tick.let { Prices.err }
    val volt = LocalLook.current.volt
    val green = Color(Web.PX_GREEN)
    val line = DyType.q.copy(lineHeight = 19.sp)
    DyFrame(Modifier, "Prices", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:tag", volt, 21.dp, null)
            BasicText("Prices", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        BasicText(g.title, style = DyType.q, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        for (s in Prices.STORES) {
            val p = tick.let { Prices.px(g, s) }
            val want = Prices.want(lib, g, s)
            val name = Web.PX_NAME[s] ?: s
            Column(
                Modifier.padding(bottom = 10.dp).fillMaxWidth().background(Pn.card, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PnIcon("px:$s", Pn.ice, 17.dp, null)
                    BasicText(name, style = line.copy(fontWeight = FontWeight.Bold, color = Pn.ice), modifier = Modifier.weight(1f))
                    if (p != null && p.at > 0) BasicText("checked " + Reviews.ago(p.at, now()), style = Type.note.copy(fontSize = 12.sp))
                }
                SheetPrice(lib, g, s, p, want, cc, line, green)
                Row {
                    HBtn("Open $name", icon = "ui:out") {
                        Crumbs.tap("open store $s", "prices sheet")
                        Sounds.play(Sfx.Tap)
                        openUrl(ctx, Prices.url(lib, g, s, cc))
                    }
                }
            }
        }
        val region = Web.PX_REGIONS.firstOrNull { it.first == cc }?.second ?: cc.uppercase()
        val psc = Art.psCC(lib, cc)
        val cur = Prices.myCur(lib, cc)
        BasicText(
            "Prices for $region" + (if (psc != cc) " (PlayStation: the ${psc.uppercase()} store)" else "") + (if (cur.isNotEmpty()) ", shown in $cur" else "") +
                " · change it in the web app's Settings → Price alerts.",
            style = line.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
        if (err.isNotEmpty() && !busy) {
            BasicText(if (err == "helper-old") "Nexi's helper needs its update first." else "Couldn't check right now.", style = line.copy(fontSize = 12.5.sp), modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
            HBtn(if (busy) "Checking…" else "Check this game now", main = true, enabled = !busy && Prices.watched(lib, rk, g), modifier = Modifier.weight(1f)) {
                Crumbs.tap("check prices now", "prices sheet")
                Sounds.play(Sfx.Tap)
                scope.launch { Prices.fetch(lib, rk, g, cc, now) }
            }
        }
    }
}

/** One store's row in the sheet: its price (and the store's own when it's converted), ✓ In your PS Plus, or why not. */
@Composable
private fun SheetPrice(lib: Library, g: Game, s: String, p: Prices.Px?, want: Boolean, cc: String, line: androidx.compose.ui.text.TextStyle, green: Color) {
    val name = Web.PX_NAME[s] ?: s
    val small = Type.note.copy(fontSize = 12.sp, lineHeight = 16.sp)
    when {
        !want -> {
            val off = if (s == "st") Backup.num(Prices.f(g, "pxSt"), 0.0, -1e15, 1e15) == -1.0 else Backup.str(Prices.f(g, if (s == "ns") "pxNs" else "pxPs")) == "-"
            val on = lib[mapOf("st" to "pxSteam", "ps" to "pxPS", "ns" to "pxNS")[s]!!] != kotlinx.serialization.json.JsonPrimitive(false)
            BasicText(if (off) "You told me it isn't on this store." else if (on) "Not watched: its platforms don't include this store." else "This store is switched off in Settings.", style = line)
        }
        p != null && p.none -> BasicText("I couldn't find it on $name.", style = line)
        p != null && p.err.isNotEmpty() -> BasicText("The last check didn't work (${p.err}). I'll try again on my own.", style = line)
        p != null && Prices.incl(lib, p) -> Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PnIcon("ui:tick", green, 16.dp, null)
                BasicText("In your PS Plus ${Web.PS_PLUS[Prices.tier(lib)]}", style = line.copy(fontWeight = FontWeight.Bold, color = green))
            }
            BasicText(p.name + if (p.nowF.isNotEmpty()) " · or buy for " + Prices.m(lib, cc, s, p, p.now, p.nowF) else "", style = small)
        }
        p != null && (p.nosale || p.nop || p.free) -> Column {
            BasicText(if (p.nosale) "Not sold in your region" else if (p.free) "Free" else "No price yet (not out yet)", style = line.copy(fontWeight = if (p.free) FontWeight.Bold else FontWeight.Normal))
            if (p.name.isNotEmpty()) BasicText(p.name, style = small)
        }
        p != null && p.now != null -> Column {
            val conv = Prices.mx(lib, cc, s, p, p.now)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText((if (conv) "≈ " else "") + Prices.m(lib, cc, s, p, p.now, p.nowF), style = line.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pn.ice))
                if (p.pct != 0.0) {
                    BasicText(Prices.m(lib, cc, s, p, p.base, p.baseF), style = small.copy(textDecoration = TextDecoration.LineThrough))
                    BasicText(
                        "−${p.pct.toInt()}%",
                        style = small.copy(fontWeight = FontWeight.Bold, color = Color(Web.PX_ON_GREEN)),
                        modifier = Modifier.background(green, Pn.pill).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            val lowSeen = p.low != null && p.low < p.now
            BasicText(p.name + if (lowSeen) " · lowest seen " + Prices.m(lib, cc, s, p, p.low, p.lowF) else "", style = small)
            if (conv) BasicText("Store price ${p.nowF}" + if (p.pct != 0.0 && p.baseF.isNotEmpty()) " (was ${p.baseF})" else "", style = small.copy(fontSize = 11.5.sp))
        }
        else -> {
            val tied = if (s == "st") Backup.num(Prices.f(g, "pxSt"), 0.0, -1e15, 1e15) > 0 else Backup.str(Prices.f(g, if (s == "ns") "pxNs" else "pxPs")).let { it.isNotEmpty() && it != "-" }
            BasicText(if (tied) "Linked; the price comes with the next check." else "Not found yet.", style = line)
        }
    }
}
