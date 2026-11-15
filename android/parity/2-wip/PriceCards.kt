package io.github.shamlawy.playnext.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import java.time.ZoneId

/** The region the prices are for, from the backup's choices, this phone's time zone and its language's country. */
@Composable
private fun pxRegion(lib: Library): Prices.Region {
    val country = androidx.compose.ui.platform.LocalLocale.current.platformLocale.country
    return remember(lib, country) { Prices.region(lib, java.util.TimeZone.getDefault().id, country) }
}

/** The page's ask for prices (`onNav`, 19758): a store with no answer yet, once an hour, 700 ms after the page opens. */
@Composable
private fun AskPrices(g: Game, lib: Library, rk: Rank, region: Prices.Region) {
    val now = LocalNow.current
    LaunchedEffect(g.id) {
        delay(700)
        if (askOnOpen && Prices.dueOnOpen(lib, rk, g, now())) Prices.fetch(lib, g, region, now)
    }
}

/**
 * The helper's cards side by side (`.gd-pair`, index.html 21879): Reviews and Prices in two columns from 760 wide when
 * both show (the same height, as the web's grid stretches them), else one under the other.
 */
@Composable
internal fun HelperPair(g: Game, page: Page, lib: Library, big: Boolean, modifier: Modifier) {
    val rvTick by Reviews.seen.collectAsState()
    val pxTick by Prices.seen.collectAsState()
    val now = LocalNow.current
    val region = pxRegion(lib)
    val zone = ZoneId.systemDefault()
    AskPrices(g, lib, page.q.rk, region)
    val rv = rvTick.let { reviewsShow(g, page, lib, now(), zone) }
    val px = pxTick.let { Prices.card(lib, page.q.rk, g, region, zone) }?.takeIf { it.shows }
    if (big && rv && px != null) {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReviewsSec(g, page, lib, Modifier.weight(1f).fillMaxHeight())
            PricesSec(g, px, region, narrow = true, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            if (rv) ReviewsSec(g, page, lib, Modifier)
            if (px != null) PricesSec(g, px, region, narrow = !big, Modifier)
        }
    }
}

/**
 * Prices on the game page (`gdPrices` + `pxCard`, 21977, 19605): a chip per store the game is watched on (PS Store,
 * Steam, the eShop) with its logo and price, the best deal ringed (in your PS Plus, else the cheapest, else the biggest
 * sale), a sale's "−N%", and the line under them (why the ringed one wins, the sale's end, the lowest seen); a chip
 * opens that store, the line the sheet. A game you own only says so (M4). The web's "Price alerts ›" reads "Details ›"
 * here: alerts wait for Phase 9, the sheet is the stores.
 */
@Composable
private fun PricesSec(g: Game, card: Prices.Card, region: Prices.Region, narrow: Boolean, modifier: Modifier) {
    val edit = LocalEditActs.current
    val ctx = LocalContext.current
    val volt = LocalLook.current.volt
    val still = LocalCalm.current
    Section("ui:price", "Prices", "", modifier) {
        if (card.own) {
            BasicText(
                buildAnnotatedString {
                    append("✓ ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pn.ice)) { append("You own it") }
                    append(" · no price checks.")
                },
                style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 14.dp),
            )
            return@Section
        }
        // room above for a sale's badge, which sits over the chip's top edge
        Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (card.tight) 6.dp else 8.dp)) {
            for (c in card.chips) {
                PxChip(c, card.chips.size, card.tight || (narrow && card.chips.size == 3), still, volt, Modifier.weight(1f)) {
                    Crumbs.tap("open a store", "game page")
                    Sounds.play(Sfx.Tap)
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prices.url(g, c.s, region))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        Row(
            Modifier.padding(top = 4.dp).fillMaxWidth().sizeIn(minHeight = Pn.touch)
                .then(if (edit != null) Modifier.clickable(role = Role.Button, onClickLabel = "Every store's price") {
                    Crumbs.tap("prices sheet", "game page")
                    Sounds.play(Sfx.Tap)
                    edit.prices(g.id)
                } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(card.cap, style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (edit != null) BasicText("Details ›", style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = volt), maxLines = 1, softWrap = false)
        }
    }
}

private val GREEN = Color(Web.PX_GREEN)

/** `.px-c`: one store's chip, 48 tall and round 15; the best deal in your accent, a PS Plus game in green. */
@Composable
private fun PxChip(c: Prices.Chip, n: Int, tight: Boolean, still: Boolean, volt: Color, modifier: Modifier, open: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    val far = Color(Web.PX_BEST_FAR)
    val (edge, ground) = when {
        c.incl -> Color(Web.PX_INCL_EDGE) to Brush.verticalGradient(0f to Color(Web.PX_INCL_NEAR), .75f to far)
        c.best -> volt.copy(alpha = .65f) to Brush.verticalGradient(0f to volt.copy(alpha = .2f), .75f to far)
        else -> Pn.line to Brush.verticalGradient(listOf(Color(Web.PX_CHIP), Color(Web.PX_CHIP)))
    }
    val logo = when { c.incl -> GREEN; c.best -> volt; else -> Pn.ice.copy(alpha = .92f) }
    val one = n == 1
    // `.tight`, and three stores in a card under 400 wide (the web's container query)
    val small = tight
    Box(modifier.height(48.dp)) {
        Row(
            Modifier.fillMaxSize().background(ground, shape).border(1.dp, edge, shape)
                .clickable(role = Role.Button, onClick = open).semantics { contentDescription = c.tip }
                .padding(horizontal = if (one) 14.dp else if (small) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (one) Arrangement.Start else Arrangement.spacedBy(if (small) 5.dp else 7.dp, Alignment.CenterHorizontally),
        ) {
            PnIcon("px:" + c.s, logo, if (small) 15.dp else 17.dp, null)
            if (one) {
                BasicText(Web.PX_NAME[c.s] ?: "", style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(start = 7.dp).clearAndSetSemantics { })
                Spacer(Modifier.weight(1f))
                if (c.old.isNotEmpty()) BasicText(c.old, style = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp, textDecoration = TextDecoration.LineThrough), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(end = 7.dp).clearAndSetSemantics { })
            }
            val fs = if (small) 13.5f else 15f
            val bold = Type.stat.copy(fontSize = fs.sp, lineHeight = (fs * 1.1f).sp, letterSpacing = 0.sp)
            val quiet = Type.note.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
            val auto = { max: Float -> TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = max.sp, stepSize = .5.sp) }
            val hush = Modifier.clearAndSetSemantics { }
            when (c.kind) {
                Prices.Kind.PRICE -> BasicText(priceText(c.text), style = bold.copy(color = Pn.ice), maxLines = 1, softWrap = false, autoSize = auto(fs), modifier = hush)
                Prices.Kind.INCL -> BasicText(c.text, style = bold.copy(color = GREEN), maxLines = 1, softWrap = false, autoSize = auto(fs), modifier = hush)
                Prices.Kind.CHECKING -> Checking(quiet, still, hush)
                Prices.Kind.SMALL -> BasicText(c.text, style = quiet, maxLines = 1, softWrap = false, autoSize = auto(12.5f), modifier = hush)
            }
        }
        // `.px-c em`: "−20%" over the top edge, right
        if (c.pct > 0) BasicText(
            "−${c.pct}%",
            style = Type.stat.copy(fontSize = 10.5.sp, lineHeight = 12.6.sp, letterSpacing = 0.sp, color = Color(Web.PX_GREEN_INK)),
            maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = (-8).dp).background(GREEN, Pn.pill).padding(horizontal = 6.dp, vertical = 2.dp).clearAndSetSemantics { },
        )
    }
}

/** "AED 249" with the code drawn small (`.px-c b u`: .7em, lighter), so the digits carry the chip. */
private fun priceText(t: String): AnnotatedString = buildAnnotatedString {
    val code = SpanStyle(fontSize = .7.em, fontWeight = FontWeight.SemiBold, color = Pn.ice.copy(alpha = .8f))
    val head = Regex("^([A-Z]{2,4})[\\s\\u00A0]?(?=\\d)").find(t)
    val tail = Regex("(\\d)[\\s\\u00A0]?([A-Z]{2,4})$").find(t)
    when {
        head != null -> { withStyle(code) { append(head.groupValues[1] + "\u2009") }; append(t.substring(head.range.last + 1)) }
        tail != null -> { append(t.substring(0, tail.range.first + 1)); withStyle(code) { append("\u2009" + tail.groupValues[2]) } }
        else -> append(t)
    }
}

/** "checking": its words breathing (`pxck`, 1.2 s, alpha only), still when calm. */
@Composable
private fun Checking(style: androidx.compose.ui.text.TextStyle, still: Boolean, modifier: Modifier) {
    if (still) { BasicText("checking", style = style, maxLines = 1, softWrap = false, modifier = modifier); return }
    val t = rememberInfiniteTransition("pxck")
    val a by t.animateFloat(1f, .35f, infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse), "pxck")
    BasicText("checking", style = style, maxLines = 1, softWrap = false, modifier = modifier.graphicsLayer { alpha = a })
}

/**
 * "Prices" (`pxSheet`, 19655), read only: per store when it was checked and what it costs (in your PS Plus, not sold
 * here, free, no price yet, on sale with the old price, the lowest seen, the store's own price when converted), or why
 * it isn't watched, and the store's page; then the region line and Check this game now. The web's "Not on <store>",
 * "Watch it again" and the pasted store link change your records: they come on switch day.
 */
@Composable
fun PriceSheet(lib: Library, g: Game, rk: Rank, now: () -> Long, close: () -> Unit) {
    val tick by Prices.seen.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val volt = LocalLook.current.volt
    val region = pxRegion(lib)
    val zone = ZoneId.systemDefault()
    var msg by remember(g.id) { mutableStateOf("") }
    val busy = tick.let { Prices.busy(g) }
    val line = DyType.q.copy(lineHeight = 19.sp)
    DyFrame(Modifier, "Prices", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon("ui:price", volt, 21.dp, null)
            BasicText("Prices", style = DyType.head, modifier = Modifier.weight(1f).padding(start = 7.dp))
            Box(
                Modifier.size(Pn.touch).clickable(role = Role.Button, onClick = close).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { BasicText("✕", style = DyType.x) }
        }
        BasicText(g.title, style = DyType.q, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        for (s in listOf("st", "ps", "ns")) {
            val p = tick.let { Prices.store(g, s) }
            val name = Web.PX_NAME[s] ?: s
            Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().background(Pn.card, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(name, style = DyType.name, modifier = Modifier.weight(1f))
                    val at = Backup.num(p?.get("at"), 0.0, 0.0, 1e15).toLong()
                    if (at > 0) BasicText("checked " + Reviews.ago(at, now()), style = DyType.small)
                }
                StoreState(lib, g, s, p, region, zone, line)
                Box(Modifier.padding(top = 10.dp)) {
                    HBtn("Open $name", icon = "ui:out") {
                        Crumbs.tap("open a store", "prices sheet")
                        Sounds.play(Sfx.Tap)
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prices.url(g, s, region))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
        }
        BasicText(Prices.forLine(region), style = line, modifier = Modifier.padding(top = 4.dp))
        if (msg.isNotEmpty()) BasicText(msg, style = line.copy(color = Pn.ice), modifier = Modifier.padding(top = 6.dp))
        if (Prices.on(lib) && !Prices.own(g)) Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
            HBtn(if (busy) "Checking…" else "Check this game now", main = true, enabled = !busy, modifier = Modifier.weight(1f)) {
                Crumbs.tap("check prices now", "prices sheet")
                Sounds.play(Sfx.Tap)
                msg = ""
                scope.launch {
                    val ok = Prices.fetch(lib, g, region, now)
                    msg = if (ok) "" else if (Prices.err == "helper-old") "Nexi's helper needs its update first." else "Couldn't check right now."
                }
            }
        }
    }
}

/** One store's line in the sheet (`pxSheet`'s row). */
@Composable
private fun StoreState(lib: Library, g: Game, s: String, p: kotlinx.serialization.json.JsonObject?, r: Prices.Region, zone: ZoneId, line: androidx.compose.ui.text.TextStyle) {
    val name = Web.PX_NAME[s] ?: s
    val str = { k: String -> Backup.str(p?.get(k)) }
    val num = { k: String -> p?.get(k)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.let { Backup.num(it, Double.NaN, -1e15, 1e15) }?.takeIf { !it.isNaN() } }
    val top = Modifier.padding(top = 6.dp)
    val bold = line.copy(fontWeight = FontWeight.Bold, color = Pn.ice)
    when {
        !Prices.want(lib, g, s) -> {
            val off = if (s == "st") Backup.num(Prices.field(g, "pxSt"), 0.0, -1e15, 1e15) == -1.0 else Backup.str(Prices.field(g, if (s == "ns") "pxNs" else "pxPs")) == "-"
            BasicText(if (off) "You told me it isn't on this store." else if (Prices.storeOn(lib, s)) "Not watched: its platforms don't include this store." else "This store is switched off in Settings.", style = line, modifier = top)
        }
        p == null -> {
            val tied = if (s == "st") Backup.num(Prices.field(g, "pxSt"), 0.0, -1e15, 1e15) > 0 else Backup.str(Prices.field(g, if (s == "ns") "pxNs" else "pxPs")).let { it.isNotEmpty() && it != "-" }
            BasicText(if (tied) "Linked; the price comes with the next check." else "Not found yet.", style = line, modifier = top)
        }
        Backup.truthy(p["none"]) -> BasicText("I couldn't find it on $name.", style = line, modifier = top)
        Backup.truthy(p["err"]) -> BasicText("The last check didn't work (${str("err")}).", style = line, modifier = top)
        Prices.incl(lib, p) -> Column(top) {
            BasicText("✓ In your PS Plus ${Web.PS_PLUS.getOrElse(Prices.tier(lib)) { "" }}", style = bold.copy(color = GREEN))
            val buy = str("nowF").takeIf { it.isNotEmpty() }?.let { " · or buy for " + Prices.m(lib, r, s, p, num("now"), it) } ?: ""
            if (str("name").isNotEmpty() || buy.isNotEmpty()) BasicText(str("name") + buy, style = DyType.small)
        }
        Backup.truthy(p["nosale"]) || Backup.truthy(p["nop"]) || Backup.truthy(p["free"]) -> Column(top) {
            BasicText(if (Backup.truthy(p["nosale"])) "Not sold in your region" else if (Backup.truthy(p["free"])) "Free" else "No price yet (not out yet)", style = if (Backup.truthy(p["free"])) bold else line)
            if (str("name").isNotEmpty()) BasicText(str("name"), style = DyType.small)
        }
        else -> Column(top) {
            val conv = Prices.to(lib, r, s, p, num("now"))?.x == true
            val pct = Backup.num(p["pct"], 0.0, 0.0, 100.0).toInt()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText((if (conv) "≈ " else "") + Prices.m(lib, r, s, p, num("now"), str("nowF")), style = bold)
                if (pct > 0) {
                    BasicText(Prices.m(lib, r, s, p, num("base"), str("baseF")), style = line.copy(textDecoration = TextDecoration.LineThrough))
                    BasicText("−$pct%", style = DyType.small.copy(fontWeight = FontWeight.Bold, color = Color(Web.PX_GREEN_INK)), modifier = Modifier.background(GREEN, Pn.pill).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            val low = num("low")
            val lowLine = if (low != null && num("now") != null && low < num("now")!!) " · lowest seen " + Prices.m(lib, r, s, p, low, str("lowF")) else ""
            if (str("name").isNotEmpty() || lowLine.isNotEmpty()) BasicText(str("name") + lowLine, style = DyType.small)
            if (conv) BasicText("Store price ${str("nowF")}" + (if (pct > 0) " (was ${str("baseF")})" else "") + " · converted at today's rate", style = DyType.small.copy(fontSize = 11.5.sp))
            val end = Backup.num(p["end"], 0.0, 0.0, 1e15)
            if (pct > 0 && end > 0) BasicText("Sale ends " + Prices.end(end, zone), style = DyType.small)
        }
    }
}
