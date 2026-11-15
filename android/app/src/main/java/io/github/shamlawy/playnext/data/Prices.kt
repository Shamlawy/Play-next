package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Prices (index.html 19415-19700, android/parity/2-map/2-edit.md §7b, M4): what a game you're waiting for costs now on
 * Steam, the PS Store and the eShop, through the helper's `POST /prices` (read only: never `id`, `watch`, `off` or `all`,
 * which would write the helper's watch list; `Routes.check` blocks them). The web keeps the answers in the backup (`g.px`,
 * the store ids `g.pxSt`/`pxPs`/`pxPsC`/`pxNs`/`pxNsU`, "not on this store" times `pxStNo`/`pxPsNo`/`pxNsNo`); here a new
 * answer lives in memory while the app runs, over the backup's copy ([of], [f]), so the page's card reads the newest.
 *
 * When to ask: the web prices every watched game twice a day (`pxRun`, 12 h) and a game's page asks for that one game
 * when a store it's watched on has no answer yet; this copy can't save, so the page asks for its game 700 ms after it
 * opens when a watched store has no answer, or the oldest answer is over 12 hours old, at most once an hour per game.
 * A game you own (`g.own`, M4) isn't priced: the card says so. Price-drop toasts, Nexi's line, the deals sheet and the
 * alerts (the helper's watch list) wait for Phase 9; the sheet's writes (not on this store, a pasted link) too.
 */
object Prices {
    val STORES = listOf("st", "ps", "ns")

    fun on(lib: Library) = lib["pxOff"] != JsonPrimitive(true)

    private val mem = ConcurrentHashMap<String, Map<String, JsonElement>>()
    private val ids = ConcurrentHashMap<String, Map<String, JsonElement>>()
    private val tried = ConcurrentHashMap<String, Long>()
    private val busy = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var fx: JsonObject? = null
    @Volatile var err: String = ""
        private set

    /** What changed (a counter): the page's card and the sheet read it so an answer or a busy state shows. */
    val seen = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The helper's call; tests swap it. */
    @Volatile internal var post: suspend (String) -> String = { body ->
        io.github.shamlawy.playnext.net.Net.call(io.github.shamlawy.playnext.net.Net.postJson(io.github.shamlawy.playnext.net.Net.helper("/prices"), body)) { r ->
            if (r.code == 404) throw Recap.HelperError("helper-old")
            if (!r.isSuccessful) throw Recap.HelperError("HTTP ${r.code}")
            r.body.string()
        }
    }

    fun busy(g: Game) = g.id in busy

    private fun n(e: JsonElement?) = Backup.num(e, 0.0, -1e15, 1e15)
    private fun numOrNull(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { !it.isString || it.content.toDoubleOrNull() != null }?.content?.toDoubleOrNull()

    /** A store field of the game: this run's answer, else the backup's (`g.pxSt`, `g.pxPs`…). */
    fun f(g: Game, k: String): JsonElement? = ids[g.id]?.let { if (k in it) it[k] else null } ?: g[k]

    /** `g.px`: this run's answers, else the backup's. */
    fun of(g: Game): Map<String, JsonElement> = mem[g.id] ?: (g["px"] as? JsonObject).orEmpty()

    // ---- your region, your currency, your PS Plus ----

    /** `pxNsCC`: the eShop you buy from (there's none in the Gulf). */
    fun nsCC(lib: Library, cc: String): String = Backup.str(lib["pxNsCC"]).takeIf { Regex("^[a-z]{2}$").matches(it) } ?: if (cc in Web.NS_OK) cc else "us"

    /** `pxMyCur`: "" = each store's own, a code, else your region's. */
    fun myCur(lib: Library, cc: String): String {
        val c = Backup.str(lib["pxCur"])
        return if (c == "store") "" else if (Regex("^[A-Z]{3}$").matches(c)) c else Web.PX_CUR[cc] ?: ""
    }

    fun tier(lib: Library): Int = Backup.num(lib["psPlus"], 0.0, 0.0, 3.0).toInt()

    /** `fxRate`: per 1 USD, the helper's rates (this run's, else the backup's `S.fx`), the pegged Gulf ones until then. */
    fun fxRate(lib: Library, c: String): Double {
        val r = ((fx ?: lib["fx"] as? JsonObject)?.get("r") as? JsonObject)?.get(c)
        val v = n(r)
        return if (v > 0) v else Web.FX_PEG[c] ?: 0.0
    }

    private fun stores(lib: Library) = mapOf("st" to (lib["pxSteam"] != JsonPrimitive(false)), "ps" to (lib["pxPS"] != JsonPrimitive(false)), "ns" to (lib["pxNS"] != JsonPrimitive(false)))
    private fun pl(g: Game) = Rank.platList(g["platforms"]).map { it.lowercase() }
    private fun str(g: Game, k: String) = f(g, k)?.let { if (it is JsonPrimitive && it !is JsonNull) it.content else "" } ?: ""

    /** `pxWantSt` / `pxWantPs` / `pxWantNs`: the store is on, not marked "not on it", and the game's platforms take it. */
    fun want(lib: Library, g: Game, s: String): Boolean {
        if (stores(lib)[s] != true) return false
        val p = pl(g)
        return when (s) {
            "st" -> n(f(g, "pxSt")) != -1.0 && (n(f(g, "pxSt")) > 0 || Backup.truthy(g["steamId"]) || p.isEmpty() || p.any { Regex("pc|steam|windows|deck").containsMatchIn(it) })
            "ps" -> str(g, "pxPs") != "-" && (str(g, "pxPs").isNotEmpty() || p.isEmpty() || p.any { Regex("^ps|playstation").containsMatchIn(it) })
            else -> str(g, "pxNs") != "-" && (str(g, "pxNs").isNotEmpty() || p.isEmpty() || p.any { Regex("switch|nintendo|\\bns\\b").containsMatchIn(it) })
        }
    }

    /** A game whose prices are watched at all: not played, dropped, competitive or owned, on a store it can be on. */
    fun watched(lib: Library, rk: Rank, g: Game) = on(lib) && !rk.isPlayed(g) && g.rawState != "dropped" && !g.comp && !Backup.truthy(g["own"]) && STORES.any { want(lib, g, it) }

    /** `pxItem`: one game for the helper (st 0 = look it up, -1 = not on Steam; ps/ns "" = look up, "-" = not wanted). */
    fun item(lib: Library, g: Game, now: Long): JsonObject {
        val relook = { k: String -> val t = n(f(g, k)); t == 0.0 || now - t > Web.PX_RELOOK }
        val own = Backup.truthy(g["own"])
        val st = if (own || !want(lib, g, "st")) -1.0 else if (n(f(g, "pxSt")) > 0) n(f(g, "pxSt")) else if (n(g["steamId"]) > 0) n(g["steamId"]) else if (relook("pxStNo")) 0.0 else -1.0
        val psC = str(g, "pxPsC")
        val ps = if (own || !want(lib, g, "ps")) "-" else str(g, "pxPs").takeIf { it.isNotEmpty() && it != "-" } ?: if (psC.isNotEmpty() || relook("pxPsNo")) "" else "-"
        val ns = if (own || !want(lib, g, "ns")) "-" else str(g, "pxNs").takeIf { it.isNotEmpty() && it != "-" } ?: if (relook("pxNsNo")) "" else "-"
        val px = of(g)
        val n2 = STORES.mapNotNull { Backup.str((px[it] as? JsonObject)?.get("name")).ifEmpty { null } }.firstOrNull { !it.equals(g.title, ignoreCase = true) }
        return JsonObject(buildMap {
            put("k", JsonPrimitive(g.id)); put("t", JsonPrimitive(g.title)); put("st", Backup.jsNumber(st)); put("ps", JsonPrimitive(ps))
            put("pc", JsonPrimitive(if (ps != "-") psC else "")); put("ns", JsonPrimitive(ns))
            put("sw2", JsonPrimitive(pl(g).any { Regex("switch\\s*2").containsMatchIn(it) }))
            if (n2 != null) put("n2", JsonPrimitive(n2))
        })
    }

    // ---- one store's answer ----

    /** One store's answer as the card reads it (`g.px[s]`). */
    class Px(o: JsonObject) {
        val none = Backup.truthy(o["none"]); val err = Backup.str(o["err"]); val nop = Backup.truthy(o["nop"]); val nosale = Backup.truthy(o["nosale"])
        val free = Backup.truthy(o["free"]); val now: Double? = o["now"].num(); val base: Double? = o["base"].num(); val pct = Backup.num(o["pct"], 0.0, -1e9, 1e9)
        val plus = Backup.num(o["plus"], 0.0, 0.0, 9.0).toInt(); val plusNow: Double? = o["plusNow"].num(); val cur = Backup.str(o["cur"]); val name = Backup.str(o["name"])
        val nowF = Backup.str(o["nowF"]); val baseF = Backup.str(o["baseF"]); val plusNowF = Backup.str(o["plusNowF"]); val low: Double? = o["low"].num(); val lowF = Backup.str(o["lowF"])
        val end = Backup.num(o["end"], 0.0, 0.0, 1e15).toLong(); val pre = Backup.truthy(o["pre"]); val at = Backup.num(o["at"], 0.0, 0.0, 1e15).toLong()
        val bad get() = none || err.isNotEmpty() || nop || nosale
    }

    private fun JsonElement?.num(): Double? = if (this == null || this is JsonNull) null else numOrNull(this)

    fun px(g: Game, s: String): Px? = (of(g)[s] as? JsonObject)?.let(::Px)

    /** `pxEff`: your price: the PS Plus member price from Essential up when it's lower. */
    fun eff(lib: Library, p: Px?): Double? = if (p?.now == null) null else if (tier(lib) >= 1 && p.plusNow != null) minOf(p.now, p.plusNow) else p.now

    /** `pxIncl`: in your PS Plus catalogue (Extra or Premium, the game's tier at or under yours). */
    fun incl(lib: Library, p: Px?) = p != null && p.plus > 0 && tier(lib) >= 2 && p.plus <= tier(lib)

    /** `pxMaj`: Steam and the PS Store send cents (whole for zero-decimal currencies on the PS Store), the eShop the amount. */
    fun maj(s: String, cur: String, v: Double?): Double? = if (v == null || v.isNaN()) null else if (s == "ns") v else if (s == "ps" && cur in Web.PX_ZD) v else v / 100

    class Amt(val a: Double, val c: String, val x: Boolean)

    /** `pxTo`: the amount in your currency (x = converted), else in the store's. */
    fun to(lib: Library, cc: String, s: String, p: Px, v: Double?): Amt? {
        val from = p.cur
        val a = maj(s, from, v) ?: return null
        if (from.isEmpty()) return null
        val t = myCur(lib, cc)
        val rf = fxRate(lib, from)
        val rt = fxRate(lib, t)
        return if (t.isNotEmpty() && t != from && rf > 0 && rt > 0) Amt(a / rf * rt, t, true) else Amt(a, from, false)
    }

    private val SYM = mapOf("USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "INR" to "₹", "AUD" to "A$", "CAD" to "CA$", "BRL" to "R$", "MXN" to "MX$", "KRW" to "₩")

    /** `pxFmtN` (`Intl.NumberFormat("en", currency)`): "$59.99", "€19.99", "AED 249" (no cents from 100 up). */
    fun fmtN(a: Double, c: String): String {
        val big = a >= 100
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.ENGLISH).apply { minimumFractionDigits = if (big) 0 else 2; maximumFractionDigits = if (big) 0 else 2; roundingMode = java.math.RoundingMode.HALF_EVEN }
        val d = nf.format(a)
        return SYM[c]?.let { it + d } ?: "$c\u00A0$d"
    }

    /** `pxM`: a price in your currency when it's converted, else the store's own text [f]. */
    fun m(lib: Library, cc: String, s: String, p: Px, v: Double?, f: String): String = to(lib, cc, s, p, v)?.takeIf { it.x }?.let { fmtN(it.a, it.c) } ?: f
    fun mx(lib: Library, cc: String, s: String, p: Px, v: Double?) = to(lib, cc, s, p, v)?.x == true

    /** `pxShort`: "$59.99 USD" → "$59.99", "AED 249.00" → "AED 249": the chips are narrow. */
    fun short(f: String): String = f.replace(Regex("^(\\$[\\d.,]+)\\s*USD$"), "$1").replace(Regex("([.,]00)(?=\\D*$)"), "").trim()

    /** `pxEnd`: "3 Mar". */
    fun endDay(t: Long): String = if (t <= 0) "" else java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH).format(java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()))

    // ---- the card ----

    /** A store chip: [kind] is how its value draws (`<small>` words, the price in `<b>`, or ✓ Plus). */
    class Chip(val s: String, val label: String, val kind: Kind, val old: String, val pct: Int, val best: Boolean, val incl: Boolean, val tip: String)
    enum class Kind { Dash, Checking, Words, Price, Plus }
    class Card(val own: Boolean, val chips: List<Chip>, val cap: String)

    /**
     * `pxCard` (+ M4's owned line): null when the card has nothing to say. `gdPrices` shows it only with a price, ✓ Plus
     * or "checking" ([show]); a row of n/a or — says nothing.
     */
    fun card(lib: Library, rk: Rank, g: Game, cc: String): Card? {
        if (!on(lib) || rk.isPlayed(g) || g.rawState == "dropped" || g.comp) return null
        if (Backup.truthy(g["own"])) return Card(true, emptyList(), "")
        val list = listOf("ps", "st", "ns").filter { want(lib, g, it) }
        if (list.isEmpty()) return null
        val P = list.associateWith { px(g, it) }
        val priced = list.filter { val p = P[it]; p != null && !p.bad && (p.free || p.now != null) }
        val checking = busy(g)
        fun amt(s: String) = P[s]!!.let { p -> to(lib, cc, s, p, eff(lib, p)) }
        fun v(s: String): Double = P[s]!!.let { p -> if (p.free) 0.0 else amt(s)?.a ?: eff(lib, p) ?: 0.0 }
        fun cur(s: String): String = P[s]!!.let { p -> if (p.free) "*" else amt(s)?.c ?: p.cur }
        fun effF(s: String): String = P[s]!!.let { p -> val e = eff(lib, p); m(lib, cc, s, p, e, if (e != null && p.now != null && e < p.now && p.plusNowF.isNotEmpty()) p.plusNowF else p.nowF) }
        fun pct(s: String): Int = P[s]!!.let { p -> val e = eff(lib, p); if (p.base != null && p.base > 0 && e != null && e < p.base) Rank.jsRound((1 - e / p.base) * 100).toInt() else p.pct.toInt() }
        var best = list.firstOrNull { incl(lib, P[it]) } ?: ""
        var why = if (best.isNotEmpty()) "is in your PS Plus" else ""
        if (best.isEmpty() && priced.size > 1) {
            val curs = priced.map(::cur).filter { it != "*" }.toSet()
            if (curs.size <= 1) {
                val lo = priced.minOf(::v)
                val w = priced.filter { v(it) == lo }
                if (w.size < priced.size) { best = w[0]; why = "is cheapest" }
            } else {
                priced.filter { pct(it) > 0 }.maxByOrNull(::pct)?.let { best = it; why = "has the best sale" }
            }
        }
        val chips = list.map { s ->
            val p = P[s]
            val inc = incl(lib, p)
            val d = if (p != null && !inc && s in priced) pct(s) else 0
            val (kind, label) = when {
                p == null -> if (checking) Kind.Checking to "checking" else Kind.Dash to "—"
                p.none || p.nosale -> Kind.Words to "n/a"
                p.err.isNotEmpty() -> Kind.Words to "retry"
                inc -> Kind.Plus to "Plus"
                p.free -> Kind.Price to "Free"
                p.nop -> Kind.Words to "soon"
                else -> Kind.Price to short(effF(s))
            }
            val old = if (d > 0 && p != null && p.baseF.isNotEmpty() && kind == Kind.Price) short(m(lib, cc, s, p, p.base, p.baseF)) else ""
            val name = Web.PX_NAME[s] ?: s
            val tip = listOf(
                name,
                when {
                    p == null -> if (checking) "checking" else if (err == "helper-old") "waiting for Nexi's helper update" else "not checked yet"
                    p.none -> "not found on this store"
                    p.nosale -> "not sold in your region"
                    p.err.isNotEmpty() -> "couldn't check just now, I'll try again"
                    inc -> "in your PS Plus ${Web.PS_PLUS[tier(lib)]}" + if (p.nowF.isNotEmpty()) ", or buy " + m(lib, cc, s, p, p.now, p.nowF) else ""
                    p.nop -> "no price yet"
                    else -> (if (mx(lib, cc, s, p, eff(lib, p))) "about " else "") + label + if (d > 0) ", $d% off, was ${m(lib, cc, s, p, p.base, p.baseF)}" else ""
                },
                if (p != null && p.pct != 0.0 && p.end > 0) "sale ends " + endDay(p.end) else "",
                if (p?.pre == true) "pre-order" else "",
                if (p != null && !inc && p.plus > 0) "in PS Plus ${Web.PS_PLUS.getOrElse(p.plus) { "" }}" else "",
            ).filter { it.isNotEmpty() }.joinToString(", ")
            Chip(s, label, kind, old, d, s == best, inc, tip)
        }
        // the line under the chips: why the ringed one wins, its sale's end, the lowest seen, the currency it's shown in
        val bp = if (best.isNotEmpty()) P[best] else null
        val low = list.filter { val x = P[it]; x?.low != null && x.now != null && !incl(lib, x) && x.low < (eff(lib, x) ?: x.now) }
        val conv = priced.any { mx(lib, cc, it, P[it]!!, eff(lib, P[it])) }
        val se = if (bp != null && bp.pct != 0.0 && bp.end > 0) bp else if (best.isEmpty()) priced.mapNotNull { P[it] }.firstOrNull { it.pct != 0.0 && it.end > 0 } else null
        val cap = listOf(
            if (best.isNotEmpty()) "${Web.PX_NAME[best]} $why" else "",
            se?.let { (if (best.isNotEmpty()) "ends " else "sale ends ") + endDay(it.end) } ?: "",
            low.firstOrNull()?.let { s -> P[s]!!.let { "lowest seen " + m(lib, cc, s, it, it.low, it.lowF) } } ?: "",
            if (conv) "≈ in " + myCur(lib, cc) else "",
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        return Card(false, chips, cap)
    }

    /** `gdPrices`: the section shows only when the card has a price, ✓ Plus or "checking" (or it's yours). */
    fun show(c: Card?) = c != null && (c.own || c.chips.any { it.kind == Kind.Price || it.kind == Kind.Plus || it.kind == Kind.Checking })

    /** `pxUrl`: the store's page for the game, else its search. */
    fun url(lib: Library, g: Game, s: String, cc: String): String {
        val t = java.net.URLEncoder.encode(g.title, "UTF-8").replace("+", "%20")
        return when (s) {
            "ns" -> str(g, "pxNsU").ifEmpty {
                if (nsCC(lib, cc) in setOf("us", "ca", "mx", "br", "ar", "cl", "co", "pe")) "https://www.nintendo.com/us/search/#q=$t"
                else "https://www.nintendo.com/en-gb/Search/Search-299117.html?q=$t"
            }
            "st" -> n(f(g, "pxSt")).takeIf { it > 0 }?.let { "https://store.steampowered.com/app/${it.toLong()}/" } ?: "https://store.steampowered.com/search/?term=$t"
            else -> {
                val psc = Art.psCC(lib, cc)
                str(g, "pxPs").takeIf { it.isNotEmpty() && it != "-" }?.let { "https://store.playstation.com/en-$psc/product/$it" } ?: "https://store.playstation.com/en-$psc/search/$t"
            }
        }
    }

    // ---- asking ----

    /** The page's own ask: a watched store with no answer, or the oldest answer over 12 h old; once an hour a game. */
    fun due(lib: Library, rk: Rank, g: Game, now: Long): Boolean {
        if (!watched(lib, rk, g) || busy(g) || now - (tried[g.id] ?: 0L) < 3_600_000L) return false
        val ws = STORES.filter { want(lib, g, it) }
        val oldest = ws.minOf { px(g, it)?.at ?: 0L }
        return oldest == 0L || now - oldest > Web.PX_GAP
    }

    /** `pxGap`: a store this game is watched on that has no answer yet. */
    private fun gap(it: JsonObject, g: Game): Boolean {
        val p = of(g)
        return (n(it["st"]) != -1.0 && p["st"] == null) || (Backup.str(it["ps"]) != "-" && p["ps"] == null) || (Backup.str(it["ns"]) != "-" && p["ns"] == null)
    }

    private fun body(lib: Library, cc: String, items: List<JsonObject>, withFx: Boolean) = JsonObject(buildMap {
        put("cc", JsonPrimitive(cc)); put("nscc", JsonPrimitive(nsCC(lib, cc))); put("pscc", JsonPrimitive(Art.psCC(lib, cc)))
        put("mc", JsonPrimitive(myCur(lib, cc))); put("items", JsonArray(items))
        put("min", Backup.jsNumber(Backup.num(lib["pxMin"], 0.0, 0.0, 90.0))); put("tier", JsonPrimitive(tier(lib)))
        if (withFx) put("fx", JsonPrimitive(1))
    }).toString()

    /** `fxOld`: rates over 12 h old (or none). */
    private fun fxOld(lib: Library, now: Long): Boolean {
        val f = fx ?: lib["fx"] as? JsonObject ?: return true
        return f["r"] !is JsonObject || now - n(f["at"]) > 12 * 3_600_000L
    }

    /**
     * `pxRun(true, g)`: one game, then one more go for any store that still didn't answer. Never throws; a failure keeps
     * the old prices and says why in [err] ("helper-old" for a helper from before prices).
     */
    suspend fun fetch(lib: Library, rk: Rank, g: Game, cc: String, now: () -> Long) {
        if (!watched(lib, rk, g)) return
        if (!busy.add(g.id)) return
        tried[g.id] = now()
        seen.value++
        try {
            val it = item(lib, g, now()).takeIf { n(it["st"]) != -1.0 || Backup.str(it["ps"]) != "-" || Backup.str(it["ns"]) != "-" } ?: return
            take(lib, g, ask(body(lib, cc, listOf(it), fxOld(lib, now())), now), now())
            val again = item(lib, g, now())
            if (gap(again, g)) take(lib, g, ask(body(lib, cc, listOf(again), false), now), now())
            err = ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            err = (if (e is Recap.HelperError) e.message ?: "" else e.toString()).take(80)
        } finally {
            busy.remove(g.id)
            seen.value++
        }
    }

    private suspend fun ask(body: String, now: () -> Long): JsonObject? {
        val j = Json.parseToJsonElement(post(body)) as? JsonObject
        (j?.get("fx") as? JsonObject)?.let { f -> if (Backup.truthy(f["ok"]) && f["r"] is JsonObject && n((f["r"] as JsonObject)["USD"]) == 1.0) fx = JsonObject(f + ("at" to JsonPrimitive(now()))) }
        return j?.get("res") as? JsonObject
    }

    /** `pxTake` for one game: each store's answer into this run's memory ("not on this store" and "couldn't check" too). */
    internal fun take(lib: Library, g: Game, res: JsonObject?, now: Long) {
        val r = res?.get(g.id) as? JsonObject ?: return
        val P = LinkedHashMap(of(g))
        val I = LinkedHashMap(ids[g.id].orEmpty())
        val t = JsonPrimitive(now)
        for (s in STORES) {
            val p = r[s] as? JsonObject ?: continue
            if (Backup.truthy(p["none"])) {
                I[mapOf("st" to "pxStNo", "ps" to "pxPsNo", "ns" to "pxNsNo")[s]!!] = t
                P[s] = JsonObject(mapOf("none" to JsonPrimitive(1), "at" to t))
                continue
            }
            val was = P[s] as? JsonObject
            if (p["err"] != null && p["err"] !is JsonNull) {
                if (was?.get("now").num() == null) P[s] = JsonObject(mapOf("err" to JsonPrimitive(Backup.str(p["err"]).take(60)), "at" to t))
                continue
            }
            val id = p["id"]
            if (id != null && id !is JsonNull && Backup.str(id).isNotEmpty()) {
                if (s == "st" && !(n(f(g, "pxSt")) > 0)) I["pxSt"] = Backup.jsNumber(n(id))
                if (s == "ps" && str(g, "pxPs").isEmpty()) I["pxPs"] = JsonPrimitive(Backup.str(id))
                if (s == "ns" && str(g, "pxNs").isEmpty()) I["pxNs"] = JsonPrimitive(Backup.str(id))
            }
            if (s == "ns" && Backup.str(p["url"]).isNotEmpty()) I["pxNsU"] = JsonPrimitive(Backup.str(p["url"]))
            if (s == "ps" && Backup.str(p["cid"]).isNotEmpty()) I["pxPsC"] = JsonPrimitive(Backup.str(p["cid"]))
            val extra: Map<String, JsonElement> = when (s) {
                "ps" -> mapOf("plus" to (p["plus"] ?: JsonPrimitive(0)), "trial" to (p["trial"] ?: JsonPrimitive(0)), "plusNow" to (p["plusNow"] ?: JsonNull), "plusNowF" to (p["plusNowF"] ?: JsonNull), "end" to (p["end"] ?: JsonPrimitive(0)), "pre" to (p["pre"] ?: JsonPrimitive(0)))
                "ns" -> mapOf("end" to (p["end"] ?: JsonPrimitive(0)), "pre" to (p["pre"] ?: JsonPrimitive(0)))
                else -> emptyMap()
            }
            val base = mapOf("id" to (p["id"] ?: JsonNull), "name" to (p["name"] ?: JsonNull))
            if (Backup.truthy(p["free"]) || Backup.truthy(p["nop"]) || Backup.truthy(p["nosale"])) {
                val ns = Backup.truthy(p["nosale"])
                P[s] = JsonObject(base + mapOf("nop" to JsonPrimitive(if (ns) 0 else 1), "nosale" to JsonPrimitive(if (ns) 1 else 0), "free" to JsonPrimitive(if (Backup.truthy(p["free"])) 1 else 0), "at" to t) + extra)
                continue
            }
            if (p["now"].num() == null) {
                if (was?.get("now").num() == null) P[s] = JsonObject(base + mapOf("nop" to JsonPrimitive(1), "at" to t) + extra)
                continue
            }
            val np = Px(p)
            val e = eff(lib, np)!!
            val w = was?.let(::Px)
            val sameId = was != null && Backup.str(was["id"]) == Backup.str(p["id"])
            val low = if (w?.low != null && sameId) minOf(w.low, e) else e
            val effF = if (e != np.now && np.plusNowF.isNotEmpty()) np.plusNowF else np.nowF
            P[s] = JsonObject(base + mapOf(
                "name" to JsonPrimitive(np.name.ifEmpty { w?.name ?: "" }), "cur" to (p["cur"] ?: JsonNull), "base" to (p["base"] ?: JsonNull), "now" to p["now"]!!,
                "pct" to (p["pct"] ?: JsonPrimitive(0)), "baseF" to (p["baseF"] ?: JsonNull), "nowF" to (p["nowF"] ?: JsonNull),
                "low" to Backup.jsNumber(low), "lowF" to JsonPrimitive(if (low == e) effF else w?.lowF?.ifEmpty { effF } ?: effF), "at" to t,
                "t0" to (if (sameId && was?.get("t0") != null) was["t0"]!! else t),
            ) + extra)
        }
        mem[g.id] = P
        ids[g.id] = I
        seen.value++
    }

    internal fun forget() { mem.clear(); ids.clear(); tried.clear(); busy.clear(); fx = null; err = "" }
}
