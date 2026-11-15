package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Prices (index.html 19418-19700, android/parity/2-map/2-edit.md §7b; 3-critic.md M4): what the game costs on the PS
 * Store, Steam and the eShop, from the helper's `POST /prices` (read only: never `id`, `watch`, `off` or `all`, which
 * write the helper's watch list; [io.github.shamlawy.playnext.net.Routes] blocks them too). The backup keeps the web's
 * last check (`g.px`, the store ids, `S.fx`); a new answer lives in memory while the app runs, over the backup's copy
 * ([field]), as Reviews does.
 *
 * When to ask: as the web's page open does (`onNav` 19758), when a store the game is watched on has no answer yet, at
 * most once an hour per game, 700 ms after the page opens; and "Check this game now" in the sheet. The web's 12-hour
 * sweep of every game, its price-drop toasts, Nexi's line and the alerts wait for Phase 9 (this copy can't save, so a
 * sweep would only repeat). A game you own (`g.own`, M4) is never priced: the card says so and nothing is asked.
 */
object Prices {
    /** The chips' order: PS Store first, then Steam, the eShop. */
    val ORDER = listOf("ps", "st", "ns")
    private val STORES = listOf("st", "ps", "ns")
    private const val TRY_GAP = 3_600_000L
    private const val FX_OLD = 12 * 3_600_000L
    private val CC = Regex("^[a-z]{2}$")
    private val NS_US = setOf("us", "ca", "mx", "br", "ar", "cl", "co", "pe")

    private val mem = ConcurrentHashMap<String, JsonObject>()
    private val busy = ConcurrentHashMap.newKeySet<String>()
    private val tried = ConcurrentHashMap<String, Long>()
    @Volatile private var fxMem: JsonObject? = null

    /** The last check's trouble ("helper-old" for a helper from before prices), "" when it went through. */
    @Volatile var err: String = ""
        private set

    /** What changed (a counter): the card and the sheet read it so an answer shows. */
    val seen = MutableStateFlow(0)

    /** The helper's call; tests swap it. */
    @Volatile internal var post: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/prices"), body)) { r ->
            if (r.code == 404) throw Recap.HelperError("helper-old")
            if (!r.isSuccessful) throw Recap.HelperError("HTTP ${r.code}")
            r.body.string()
        }
    }

    // ---- JavaScript's strict checks on the backup's values ----

    private fun isTrue(e: JsonElement?) = e is JsonPrimitive && !e.isString && e.booleanOrNull == true
    private fun isFalse(e: JsonElement?) = e is JsonPrimitive && !e.isString && e.booleanOrNull == false
    private fun n(e: JsonElement?) = Backup.num(e, 0.0, -1e15, 1e15)
    private fun has(e: JsonElement?) = e != null && e !is JsonNull
    /** `p.x != null` as a number (null when missing or not a number). */
    private fun d(o: JsonObject?, k: String): Double? = o?.get(k)?.takeIf { has(it) }?.let { Backup.num(it, Double.NaN, -1e15, 1e15) }?.takeIf { !it.isNaN() }

    /** A game's field: this run's answer, else the backup's. */
    fun field(g: Game, k: String): JsonElement? = mem[g.id]?.get(k) ?: g[k]

    /** `g.px[s]`: one store's last answer. */
    fun store(g: Game, s: String): JsonObject? = (field(g, "px") as? JsonObject)?.get(s) as? JsonObject

    fun busy(g: Game) = g.id in busy

    // ---- what's watched (`pxOn`, `pxStores`, `pxWant*`) ----

    fun on(lib: Library) = !isTrue(lib["pxOff"])

    fun storeOn(lib: Library, s: String) = !isFalse(lib[if (s == "st") "pxSteam" else if (s == "ps") "pxPS" else "pxNS"])

    fun own(g: Game) = Backup.truthy(g["own"])

    private fun pl(g: Game) = Rank.platList(g["platforms"]).map { it.lowercase() }

    /** The game is watched on store [s]: the store is on, you haven't said it isn't there, and its platforms fit. */
    fun want(lib: Library, g: Game, s: String): Boolean {
        if (!storeOn(lib, s)) return false
        val pl = pl(g)
        return when (s) {
            "st" -> n(field(g, "pxSt")) != -1.0 && (n(field(g, "pxSt")) > 0 || Backup.truthy(g["steamId"]) || pl.isEmpty() || pl.any { Regex("pc|steam|windows|deck").containsMatchIn(it) })
            "ps" -> Backup.str(field(g, "pxPs")) != "-" && (Backup.truthy(field(g, "pxPs")) || pl.isEmpty() || pl.any { Regex("^ps|playstation").containsMatchIn(it) })
            else -> Backup.str(field(g, "pxNs")) != "-" && (Backup.truthy(field(g, "pxNs")) || pl.isEmpty() || pl.any { Regex("switch|nintendo|\\bns\\b").containsMatchIn(it) })
        }
    }

    /** Not off, not played, dropped or competitive: prices matter for this game at all. */
    private fun live(lib: Library, rk: Rank, g: Game) = on(lib) && !rk.isPlayed(g) && !rk.isDropped(g) && !g.comp

    // ---- regions and money (`pxCC` … `pxShort`) ----

    /** The four regions a request names: shop, eShop, PlayStation Store, and the currency you see prices in. */
    class Region(val cc: String, val nscc: String, val pscc: String, val mc: String)

    fun region(lib: Library, tz: String, country: String): Region {
        val cc = Art.cc(lib, tz, country)
        val nscc = Backup.str(lib["pxNsCC"]).takeIf { CC.matches(it) } ?: if (cc in Web.NS_OK) cc else "us"
        val cur = Backup.str(lib["pxCur"])
        val mc = if (cur == "store") "" else if (Regex("^[A-Z]{3}$").matches(cur)) cur else Web.PX_CUR[cc] ?: ""
        return Region(cc, nscc, Art.psCC(lib, cc), mc)
    }

    fun tier(lib: Library) = Backup.num(lib["psPlus"], 0.0, 0.0, 3.0).toInt()

    private fun fx(lib: Library): JsonObject? = fxMem ?: (lib["fx"] as? JsonObject)

    private fun rate(lib: Library, c: String): Double =
        (((fx(lib)?.get("r") as? JsonObject)?.get(c))?.let { n(it) }?.takeIf { it > 0 } ?: Web.FX_PEG[c]) ?: 0.0

    private fun fxOld(lib: Library, now: Long): Boolean {
        val f = fx(lib) ?: return true
        return f["r"] !is JsonObject || now - n(f["at"]).toLong() > FX_OLD
    }

    /** `pxMaj`: Steam and the PS Store send cents (whole amounts for a few currencies on the PS Store), the eShop the amount. */
    private fun maj(s: String, cur: String, v: Double?): Double? = v?.let { if (s == "ns") it else if (s == "ps" && cur in Web.PX_ZD) it else it / 100 }

    /** An amount: [a] in currency [c]; [x] when it was converted into yours. */
    class Amt(val a: Double, val c: String, val x: Boolean)

    /** `pxTo`: the amount in your currency when it can be converted, else in the store's. */
    fun to(lib: Library, r: Region, s: String, p: JsonObject?, v: Double?): Amt? {
        val from = Backup.str(p?.get("cur"))
        val a = maj(s, from, v) ?: return null
        if (from.isEmpty()) return null
        val rf = rate(lib, from)
        val rt = rate(lib, r.mc)
        return if (r.mc.isNotEmpty() && r.mc != from && rf > 0 && rt > 0) Amt(a / rf * rt, r.mc, true) else Amt(a, from, false)
    }

    /** `pxFmtN`: "$59.99", "€19.99", "AED 249" (no cents from 100 up), as the web's English currency format writes it. */
    fun money(a: Double, c: String): String {
        val sym = runCatching { java.util.Currency.getInstance(c).getSymbol(Locale.US) }.getOrNull() ?: return c + " " + Math.round(a)
        val whole = a >= 100
        val f = DecimalFormat(if (whole) "#,##0" else "#,##0.00", DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }
        return if (sym.last().isLetter()) sym + "\u00A0" + f.format(a) else sym + f.format(a)
    }

    /** `pxM`: a store price in your currency when it's converted, else the store's own text [f]. */
    fun m(lib: Library, r: Region, s: String, p: JsonObject?, v: Double?, f: String): String {
        val t = to(lib, r, s, p, v)
        return if (t != null && t.x) money(t.a, t.c) else f
    }

    private fun mx(lib: Library, r: Region, s: String, p: JsonObject?, v: Double?) = to(lib, r, s, p, v)?.x == true

    /** `pxShort`: "$59.99 USD" → "$59.99", "AED 249.00" → "AED 249": the chips are narrow. */
    fun short(f: String): String = f.replace(Regex("^(\\$[\\d.,]+)[\\s\\u00A0]*USD$"), "$1").replaceFirst(Regex("([.,]00)(?=\\D*$)"), "").trim()

    /** `pxEff`: what you'd pay: the PS Plus price from Essential up when it's lower. */
    fun eff(lib: Library, p: JsonObject?): Double? {
        val now = d(p, "now") ?: return null
        val pn = d(p, "plusNow")
        return if (tier(lib) >= 1 && pn != null) minOf(now, pn) else now
    }

    /** `pxIncl`: in your PS Plus catalogue (Extra or Premium, the game's tier at or under yours). */
    fun incl(lib: Library, p: JsonObject?): Boolean {
        val plus = n(p?.get("plus"))
        return p != null && plus > 0 && tier(lib) >= 2 && plus <= tier(lib)
    }

    /** `pxEnd`: "3 Mar" (day-month, as the rest of the app writes dates). */
    fun end(t: Double, zone: ZoneId): String {
        if (t <= 0) return ""
        val day = Instant.ofEpochMilli(t.toLong()).atZone(zone).toLocalDate()
        return "${day.dayOfMonth} ${day.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)}"
    }

    /** `pxUrl`: the store's page for the game, or its search. */
    fun url(g: Game, s: String, r: Region): String {
        val q = java.net.URLEncoder.encode(g.title, "UTF-8").replace("+", "%20")
        return when (s) {
            "ns" -> Backup.str(field(g, "pxNsU")).ifEmpty {
                if (r.nscc in NS_US) "https://www.nintendo.com/us/search/#q=$q" else "https://www.nintendo.com/en-gb/Search/Search-299117.html?q=$q"
            }
            "st" -> n(field(g, "pxSt")).let { if (it > 0) "https://store.steampowered.com/app/${it.toLong()}/" else "https://store.steampowered.com/search/?term=$q" }
            else -> Backup.str(field(g, "pxPs")).let { if (it.isNotEmpty() && it != "-") "https://store.playstation.com/en-${r.pscc}/product/$it" else "https://store.playstation.com/en-${r.pscc}/search/$q" }
        }
    }

    /** "Prices for UAE (PlayStation: the AE store), shown in AED." */
    fun forLine(r: Region): String {
        val name = Web.PX_REGIONS.firstOrNull { it.first == r.cc }?.second ?: r.cc.uppercase()
        return "Prices for $name" + (if (r.pscc != r.cc) " (PlayStation: the ${r.pscc.uppercase()} store)" else "") + (if (r.mc.isNotEmpty()) ", shown in ${r.mc}" else "") + "."
    }

    // ---- the card (`pxCard`, 19605) ----

    enum class Kind { PRICE, INCL, SMALL, CHECKING }

    /** One store's chip: its [text] ([old] struck when it's on sale, [pct] off), the best deal's ring, and what TalkBack says. */
    class Chip(val s: String, val text: String, val kind: Kind, val old: String, val pct: Int, val best: Boolean, val incl: Boolean, val tip: String)

    /** The card: the chips and the line under them, or just "You own it". [shows]: worth a page section (`gdPrices`). */
    class Card(val chips: List<Chip>, val cap: String, val own: Boolean, val tight: Boolean) {
        val shows: Boolean get() = own || chips.any { it.kind != Kind.SMALL }
    }

    private fun priced(p: JsonObject?) = p != null && !Backup.truthy(p["none"]) && !Backup.truthy(p["err"]) && !Backup.truthy(p["nop"]) &&
        !Backup.truthy(p["nosale"]) && (Backup.truthy(p["free"]) || has(p["now"]))

    /** `pxCard` for game [g]: null when prices don't apply (off, played, dropped, competitive, or no store fits). */
    fun card(lib: Library, rk: Rank, g: Game, r: Region, zone: ZoneId): Card? {
        if (!live(lib, rk, g)) return null
        if (own(g)) return Card(emptyList(), "", own = true, tight = false)
        val list = ORDER.filter { want(lib, g, it) }
        if (list.isEmpty()) return null
        val checking = busy(g)
        val P = list.associateWith { store(g, it) }
        val pr = list.filter { priced(P[it]) }
        fun free(s: String) = Backup.truthy(P[s]?.get("free"))
        fun value(s: String): Double { if (free(s)) return 0.0; val e = eff(lib, P[s]); return to(lib, r, s, P[s], e)?.a ?: e ?: 0.0 }
        fun cur(s: String): String { if (free(s)) return "*"; return to(lib, r, s, P[s], eff(lib, P[s]))?.c ?: Backup.str(P[s]?.get("cur")) }
        fun effF(s: String): String {
            val p = P[s]
            val e = eff(lib, p)
            val now = d(p, "now")
            val plusF = Backup.str(p?.get("plusNowF"))
            return m(lib, r, s, p, e, if (e != null && now != null && e < now && plusF.isNotEmpty()) plusF else Backup.str(p?.get("nowF")))
        }
        fun pct(s: String): Int {
            val p = P[s]
            val e = eff(lib, p)
            val base = n(p?.get("base"))
            return if (base > 0 && e != null && e < base) Rank.jsRound((1 - e / base) * 100).toInt() else n(p?.get("pct")).toInt()
        }
        // the best deal: in your PS Plus, else the cheapest (one currency only: the eShop can be another region's), else the biggest sale
        var best = list.firstOrNull { incl(lib, P[it]) } ?: ""
        var why = if (best.isNotEmpty()) "is in your PS Plus" else ""
        if (best.isEmpty() && pr.size > 1) {
            val curs = pr.map(::cur).filter { it != "*" }.toSet()
            if (curs.size <= 1) {
                val lo = pr.minOf(::value)
                val w = pr.filter { value(it) == lo }
                if (w.size < pr.size) { best = w[0]; why = "is cheapest" }
            } else {
                val sale = pr.filter { pct(it) > 0 }.sortedByDescending(::pct)
                if (sale.isNotEmpty()) { best = sale[0]; why = "has the best sale" }
            }
        }
        val name = { s: String -> Web.PX_NAME[s] ?: s }
        val lab = list.map { s ->
            val p = P[s]
            when {
                p == null -> if (checking) "checking" else "—"
                Backup.truthy(p["none"]) || Backup.truthy(p["nosale"]) -> "n/a"
                Backup.truthy(p["err"]) -> "retry"
                incl(lib, p) -> "✓ Plus"
                free(s) -> "Free"
                Backup.truthy(p["nop"]) -> "soon"
                else -> short(effF(s))
            }
        }
        val tight = list.size > 2 && lab.any { it.replaceFirst(Regex("[A-Z]{2,4}"), "~~").replace(Regex("[\\s\\u00A0]"), "").length > 6 }
        val tier = Web.PS_PLUS.getOrElse(tier(lib)) { "" }
        val chips = list.mapIndexed { i, s ->
            val p = P[s]
            val inc = incl(lib, p)
            val dd = if (p != null && !inc && s in pr) pct(s) else 0
            val e = eff(lib, p)
            val now = d(p, "now")
            val conv = mx(lib, r, s, p, e)
            val sayPrice = { (if (e != null && now != null && e < now) "PS Plus price " else "") + (if (conv) "about " else "") + lab[i] +
                (if (dd > 0) ", ${dd}% off, was ${m(lib, r, s, p, d(p, "base"), Backup.str(p?.get("baseF")))}" else "") }
            val tip = listOf(
                name(s),
                when {
                    p == null -> if (checking) "checking" else if (err == "helper-old") "waiting for Nexi's helper update" else "not checked yet"
                    Backup.truthy(p["none"]) -> "not found"
                    Backup.truthy(p["nosale"]) -> "not sold in your region"
                    Backup.truthy(p["err"]) -> "couldn't check just now"
                    inc -> "in your PS Plus $tier" + (Backup.str(p["nowF"]).takeIf { it.isNotEmpty() }?.let { ", or buy " + m(lib, r, s, p, now, it) } ?: "")
                    Backup.truthy(p["nop"]) -> "no price yet"
                    else -> sayPrice()
                },
                if (p != null && n(p["pct"]) > 0 && n(p["end"]) > 0) "sale ends " + end(n(p["end"]), zone) else "",
                if (p != null && Backup.truthy(p["pre"])) "pre-order" else "",
                if (p != null && !inc && n(p["plus"]) > 0) "in PS Plus ${Web.PS_PLUS.getOrElse(n(p["plus"]).toInt()) { "" }}" else "",
            ).filter { it.isNotEmpty() }.joinToString(", ")
            val kind = when {
                p == null -> if (checking) Kind.CHECKING else Kind.SMALL
                inc -> Kind.INCL
                s in pr -> Kind.PRICE
                else -> Kind.SMALL
            }
            val old = if (kind == Kind.PRICE && dd > 0 && Backup.str(p?.get("baseF")).isNotEmpty()) short(m(lib, r, s, p, d(p, "base"), Backup.str(p?.get("baseF")))) else ""
            Chip(s, lab[i], kind, old, dd, s == best, inc, tip)
        }
        // the line under the chips: why the ringed one wins, its sale's end, the lowest seen, and "≈ in AED" when converted
        val bp = if (best.isNotEmpty()) P[best] else null
        val low = list.filter { s -> val x = P[s]; val lw = d(x, "low"); val e = eff(lib, x); x != null && lw != null && has(x["now"]) && !incl(lib, x) && e != null && lw < e }
        val conv = pr.any { mx(lib, r, it, P[it], eff(lib, P[it])) }
        val se = if (bp != null && n(bp["pct"]) > 0 && n(bp["end"]) > 0) bp else if (best.isEmpty()) pr.mapNotNull { P[it] }.firstOrNull { n(it["pct"]) > 0 && n(it["end"]) > 0 } else null
        val cap = listOf(
            if (best.isNotEmpty()) "${name(best)} $why" else "",
            se?.let { (if (best.isNotEmpty()) "ends " else "sale ends ") + end(n(it["end"]), zone) } ?: "",
            low.firstOrNull()?.let { s -> "lowest seen " + m(lib, r, s, P[s], d(P[s], "low"), Backup.str(P[s]?.get("lowF"))) } ?: "",
            if (conv) "≈ in ${r.mc}" else "",
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        return Card(chips, cap, own = false, tight = tight)
    }

    // ---- asking (`pxItem`, `pxRun(true, g)`, `pxTake`) ----

    /** `pxItem`: what the helper should look up for [g] (an owned game asks nothing, M4). */
    fun item(lib: Library, g: Game, now: Long): JsonObject {
        val relook = { t: Double -> t <= 0 || now - t > Web.PX_RELOOK }
        val own = own(g)
        val pxSt = n(field(g, "pxSt"))
        val st = if (own || !want(lib, g, "st")) -1.0 else if (pxSt > 0) pxSt else n(g["steamId"]).takeIf { it > 0 } ?: if (relook(n(field(g, "pxStNo")))) 0.0 else -1.0
        val pxPs = Backup.str(field(g, "pxPs"))
        val pxPsC = Backup.str(field(g, "pxPsC"))
        val ps = if (own || !want(lib, g, "ps")) "-" else if (pxPs.isNotEmpty() && pxPs != "-") pxPs else if (pxPsC.isNotEmpty() || relook(n(field(g, "pxPsNo")))) "" else "-"
        val pxNs = Backup.str(field(g, "pxNs"))
        val ns = if (own || !want(lib, g, "ns")) "-" else if (pxNs.isNotEmpty() && pxNs != "-") pxNs else if (relook(n(field(g, "pxNsNo")))) "" else "-"
        // the name another store knows it by, for a store that hasn't found it by your title
        val n2 = STORES.map { Backup.str(store(g, it)?.get("name")) }.firstOrNull { it.isNotEmpty() && it.lowercase() != g.title.lowercase() } ?: ""
        val m = linkedMapOf<String, JsonElement>(
            "k" to JsonPrimitive(g.id), "t" to JsonPrimitive(g.title), "st" to Backup.jsNumber(st), "ps" to JsonPrimitive(ps),
            "pc" to JsonPrimitive(if (ps != "-") pxPsC else ""), "ns" to JsonPrimitive(ns), "sw2" to JsonPrimitive(pl(g).any { Regex("switch\\s*2").containsMatchIn(it) }),
        )
        if (n2.isNotEmpty()) m["n2"] = JsonPrimitive(n2)
        return JsonObject(m)
    }

    private fun asks(it: JsonObject) = n(it["st"]) != -1.0 || Backup.str(it["ps"]) != "-" || Backup.str(it["ns"]) != "-"

    /** The request's body: the regions, one game, the drop size and your PS Plus, and the rates when they're old. */
    fun body(lib: Library, r: Region, item: JsonObject, fx: Boolean): String {
        val m = linkedMapOf<String, JsonElement>(
            "cc" to JsonPrimitive(r.cc), "nscc" to JsonPrimitive(r.nscc), "pscc" to JsonPrimitive(r.pscc), "mc" to JsonPrimitive(r.mc),
            "items" to JsonArray(listOf(item)), "min" to Backup.jsNumber(Backup.num(lib["pxMin"], 0.0, 0.0, 90.0)), "tier" to JsonPrimitive(tier(lib)),
        )
        if (fx) m["fx"] = JsonPrimitive(1)
        return JsonObject(m).toString()
    }

    /** A store this game is watched on with no answer yet (`pxGap`). */
    fun gap(lib: Library, g: Game, now: Long): Boolean {
        val it = item(lib, g, now)
        return (n(it["st"]) != -1.0 && store(g, "st") == null) || (Backup.str(it["ps"]) != "-" && store(g, "ps") == null) || (Backup.str(it["ns"]) != "-" && store(g, "ns") == null)
    }

    /** The page's own ask (`onNav` 19758): a store with no answer yet, once an hour per game. */
    fun dueOnOpen(lib: Library, rk: Rank, g: Game, now: Long): Boolean {
        if (!live(lib, rk, g) || own(g) || busy(g)) return false
        if (STORES.none { want(lib, g, it) && store(g, it) == null }) return false
        return now - (tried[g.id] ?: 0L) > TRY_GAP
    }

    /** Ask the helper about [g] (then once more for a store still unanswered). Never throws; false when it didn't go through. */
    suspend fun fetch(lib: Library, g: Game, r: Region, now: () -> Long): Boolean {
        if (!on(lib) || own(g)) return false
        if (!busy.add(g.id)) return false
        tried[g.id] = now()
        seen.value++
        try {
            for (round in 0..1) {
                val it = item(lib, g, now())
                if (!asks(it)) return true
                if (round == 1 && !gap(lib, g, now())) break
                val j = Json.parseToJsonElement(post(body(lib, r, it, round == 0 && fxOld(lib, now())))) as? JsonObject ?: throw Recap.HelperError("bad answer")
                (j["fx"] as? JsonObject)?.let { f -> if (Backup.truthy(f["ok"]) && n((f["r"] as? JsonObject)?.get("USD")) == 1.0) fxMem = JsonObject(mapOf("r" to f["r"]!!, "t" to (f["t"] ?: JsonPrimitive(now())), "at" to JsonPrimitive(now()))) }
                take(lib, g, ((j["res"] as? JsonObject)?.get(g.id)) as? JsonObject, now())
                seen.value++
            }
            err = ""
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            err = if (e.message == "helper-old") "helper-old" else (e.message ?: e.javaClass.simpleName).take(80)
            return false
        } finally {
            busy.remove(g.id)
            seen.value++
        }
    }

    /** `pxTake` for one game: each store's answer kept with the ids it found (the lowest price seen, since when). */
    internal fun take(lib: Library, g: Game, res: JsonObject?, now: Long) {
        if (res == null) return
        val f = LinkedHashMap<String, JsonElement>(mem[g.id] ?: emptyMap())
        val get = { k: String -> f[k] ?: g[k] }
        val px = LinkedHashMap<String, JsonElement>((get("px") as? JsonObject) ?: emptyMap())
        val at = JsonPrimitive(now)
        for (s in STORES) {
            val p = res[s] as? JsonObject ?: continue
            val was = px[s] as? JsonObject
            if (Backup.truthy(p["none"])) {
                f[if (s == "st") "pxStNo" else if (s == "ps") "pxPsNo" else "pxNsNo"] = at
                px[s] = JsonObject(mapOf("none" to JsonPrimitive(1), "at" to at))
                continue
            }
            if (Backup.truthy(p["err"])) {
                if (!has(was?.get("now"))) px[s] = JsonObject(mapOf("err" to JsonPrimitive(Backup.str(p["err"]).take(60)), "at" to at))
                continue
            }
            if (Backup.truthy(p["id"])) {
                if (s == "st" && !(n(get("pxSt")) > 0)) f["pxSt"] = Backup.jsNumber(n(p["id"]))
                if (s == "ps" && !Backup.truthy(get("pxPs"))) f["pxPs"] = p["id"]!!
                if (s == "ns" && !Backup.truthy(get("pxNs"))) f["pxNs"] = JsonPrimitive(Backup.str(p["id"]))
            }
            if (s == "ns" && Backup.truthy(p["url"])) f["pxNsU"] = p["url"]!!
            if (s == "ps" && Backup.truthy(p["cid"])) f["pxPsC"] = p["cid"]!!
            val extra = LinkedHashMap<String, JsonElement>()
            val z = JsonPrimitive(0)
            if (s == "ps") {
                extra["plus"] = p["plus"]?.takeIf { Backup.truthy(it) } ?: z
                extra["trial"] = p["trial"]?.takeIf { Backup.truthy(it) } ?: z
                p["plusNow"]?.takeIf { has(it) }?.let { extra["plusNow"] = it }
                p["plusNowF"]?.takeIf { has(it) }?.let { extra["plusNowF"] = it }
            }
            if (s != "st") {
                extra["end"] = p["end"]?.takeIf { Backup.truthy(it) } ?: z
                extra["pre"] = p["pre"]?.takeIf { Backup.truthy(it) } ?: z
            }
            val base = linkedMapOf<String, JsonElement>()
            p["id"]?.let { base["id"] = it }
            p["name"]?.let { base["name"] = it }
            if (Backup.truthy(p["free"]) || Backup.truthy(p["nop"]) || Backup.truthy(p["nosale"])) {
                base["nop"] = JsonPrimitive(if (Backup.truthy(p["nosale"])) 0 else 1)
                base["nosale"] = JsonPrimitive(if (Backup.truthy(p["nosale"])) 1 else 0)
                base["free"] = JsonPrimitive(if (Backup.truthy(p["free"])) 1 else 0)
                base["at"] = at
                px[s] = JsonObject(base + extra)
                continue
            }
            if (!has(p["now"])) {
                if (!has(was?.get("now"))) { base["nop"] = JsonPrimitive(1); base["at"] = at; px[s] = JsonObject(base + extra) }
                continue
            }
            val e = eff(lib, p) ?: continue
            val sameId = was != null && was["id"] == p["id"]
            val wl = d(was, "low")
            val low = if (wl != null && sameId) minOf(wl, e) else e
            val effF = if (e != d(p, "now") && Backup.truthy(p["plusNowF"])) p["plusNowF"]!! else p["nowF"] ?: JsonNull
            val rec = linkedMapOf<String, JsonElement>(
                "id" to (p["id"] ?: JsonNull), "name" to JsonPrimitive(Backup.str(p["name"]).ifEmpty { Backup.str(was?.get("name")) }),
                "cur" to (p["cur"] ?: JsonNull), "base" to (p["base"] ?: JsonNull), "now" to p["now"]!!, "pct" to (p["pct"]?.takeIf { Backup.truthy(it) } ?: JsonPrimitive(0)),
                "baseF" to (p["baseF"] ?: JsonNull), "nowF" to (p["nowF"] ?: JsonNull), "low" to Backup.jsNumber(low),
                "lowF" to if (low == e) effF else (was?.get("lowF")?.takeIf { Backup.truthy(it) } ?: effF), "at" to at,
                "t0" to if (sameId && Backup.truthy(was?.get("t0"))) was!!["t0"]!! else at,
            )
            px[s] = JsonObject(rec + extra)
        }
        f["px"] = JsonObject(px)
        mem[g.id] = JsonObject(f)
    }

    internal fun forget() { mem.clear(); busy.clear(); tried.clear(); fxMem = null; err = "" }
}
