package io.github.shamlawy.playnext.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.shamlawy.playnext.gen.Web
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The price on a Queue card (android-v52, the owner, Nov 23: "show the prices of the game i dont own on the games cards
 * when they are closed and open … focus on ps5 only … and switch if the game is not on ps5"): one store per game, the PS
 * Store's PS5 version first, else the eShop. Android only: the web app has no price on its cards or in its orders.
 *
 * A wrong product would read as a bargain in the price orders, so a PS Store answer counts only once the helper has
 * checked it for PS5 (`p5`, [Prices.p5Checked]); the backup's older answers wait for that. Answers are kept on the phone
 * ([save], its own file, never the backup), so the cards have their prices at once and the stores are asked at most
 * every 12 hours a game. Reading prices writes nothing to the helper's watch list (`Routes.check`).
 */
object PriceTags {
    /** What a card shows. [usd] puts prices in different currencies in one order (null: no rate for it). */
    class Tag(val s: String, val now: String, val old: String, val pct: Int, val plus: Boolean, val free: Boolean, val pre: Boolean, val end: Long, val usd: Double?)

    /** Goes up as a batch of prices lands: a card's price and a price order read it, and nothing else redraws. */
    var v by mutableIntStateOf(0)
        private set

    private val PS5 = Regex("ps\\s*5|playstation\\s*5")
    // Switch and Switch 2 only: a 3DS, DS, Wii or N64 game has no eShop price of its own today
    private val SWITCH = Regex("switch|\\bns2?\\b")
    private const val BATCH = 5
    private const val MOST = 60
    private const val TOO_OLD = 7 * 86_400_000L
    private const val RETRY = 3_600_000L
    private const val FILE = "prices.json"

    private val running = AtomicBoolean(false)
    private val loaded = AtomicBoolean(false)
    private val asked = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var failedAt = 0L

    /** The shop region, as the game page's prices use it. */
    fun cc(lib: Library): String = Art.cc(lib, java.util.TimeZone.getDefault().id, java.util.Locale.getDefault().country)

    /** A game listed for PS5, or with no platforms yet (the store then says whether it's there). */
    fun onPs5(g: Game): Boolean = Rank.platList(g["platforms"]).map { it.lowercase() }.let { p -> p.isEmpty() || p.any { PS5.containsMatchIn(it) } }

    private fun onSwitch(g: Game) = Rank.platList(g["platforms"]).map { it.lowercase() }.let { p -> p.isEmpty() || p.any { SWITCH.containsMatchIn(it) } }

    /** The stores a card can show, best first. */
    fun stores(lib: Library, g: Game): List<String> = buildList {
        if (onPs5(g) && Prices.want(lib, g, "ps")) add("ps")
        if (onSwitch(g) && Prices.want(lib, g, "ns")) add("ns")
    }

    /** The card's price: null for a game you own, have played or dropped, one not on PS5 or Switch, or no answer yet. */
    fun of(lib: Library, rk: Rank, g: Game, cc: String, now: Long): Tag? {
        if (!Prices.watched(lib, rk, g)) return null
        for (s in stores(lib, g)) {
            if (s == "ps" && !Prices.p5Checked(g)) return null // not the Switch price while PS5 is still unanswered
            val p = Prices.px(g, s) ?: return null
            if (p.err.isNotEmpty() || now - p.at > TOO_OLD && !p.none) return null
            tag(lib, cc, s, p, now)?.let { return it }
        }
        return null
    }

    private fun tag(lib: Library, cc: String, s: String, p: Prices.Px, now: Long): Tag? {
        if (Prices.incl(lib, p)) return Tag(s, "", "", 0, plus = true, free = false, pre = p.pre, end = 0, usd = 0.0)
        if (p.free) return Tag(s, "", "", 0, plus = false, free = true, pre = p.pre, end = 0, usd = 0.0)
        if (p.bad) return null
        // a sale that ended since the last check is the full price again
        val ended = p.pct != 0.0 && p.end in 1 until now
        val e = (if (ended) p.base else Prices.eff(lib, p)) ?: return null
        val shown = if (ended) p.baseF else if (p.now != null && e < p.now && p.plusNowF.isNotEmpty()) p.plusNowF else p.nowF
        val base = p.base
        val pct = if (!ended && base != null && base > 0 && e < base) Rank.jsRound((1 - e / base) * 100).toInt() else 0
        // one way of writing a price on every card ("AED 26.17", "AED 262"), whichever store it came from
        val text = { v: Double?, f: String -> Prices.to(lib, cc, s, p, v)?.let { Prices.fmtN(it.a, it.c) } ?: Prices.short(f) }
        val old = if (pct > 0 && p.baseF.isNotEmpty()) text(base, p.baseF) else ""
        val rate = Prices.fxRate(lib, p.cur)
        val usd = Prices.maj(s, p.cur, e)?.let { a -> if (rate > 0) a / rate else null }
        return Tag(s, text(e, shown), old, pct, plus = false, free = false, pre = p.pre, end = if (pct > 0) p.end else 0, usd = usd)
    }

    /** What a screen reader says for it. */
    fun say(t: Tag): String = (Web.PX_NAME[t.s] ?: t.s) + ", " + when {
        t.plus -> "in your PS Plus"
        t.free -> "free"
        t.pct > 0 -> "${t.now}, ${t.pct}% off, was ${t.old}"
        else -> t.now
    }

    // ---- the two price orders ----

    /** "Biggest discount": the deepest sale first, then full-price games, then games with no price. */
    fun saleRank(t: Tag?): Double = t?.pct?.toDouble() ?: -1.0

    /** "Cheapest": free and PS Plus first, the dearest last, a price with no rate after those, no price at the end. */
    fun cheapRank(t: Tag?): Double = if (t == null) -2e12 else -(t.usd ?: 1e12)

    // ---- asking ----

    private fun stale(g: Game, s: String, now: Long): Boolean {
        val p = Prices.px(g, s) ?: return true
        if (p.err.isNotEmpty()) return now - p.at > RETRY
        if (s == "ps" && !Prices.p5Checked(g)) return true
        return now - p.at > if (p.none) Web.PX_RELOOK else Web.PX_GAP
    }

    /** Stale and not asked in the last hour: a store that keeps failing can't be asked on every Queue open. */
    private fun due(g: Game, s: String, now: Long) = stale(g, s, now) && now - (asked[g.id + ":" + s] ?: 0L) >= RETRY

    /** PS5 has a price (or is only failing for now): the eShop isn't needed. */
    private fun psHolds(lib: Library, p: Prices.Px?) = p != null && (p.err.isNotEmpty() || p.free || Prices.incl(lib, p) || !p.bad)

    /** One game for the helper, or null when its card is up to date: Steam never, the eShop only once PS5 has said no. */
    internal fun ask(lib: Library, rk: Rank, g: Game, now: Long): JsonObject? {
        if (!Prices.watched(lib, rk, g)) return null
        val st = stores(lib, g)
        if (st.isEmpty()) return null
        val psAsk = "ps" in st && due(g, "ps", now)
        val psSaidNo = "ps" !in st || Prices.p5Checked(g) && !psHolds(lib, Prices.px(g, "ps"))
        val nsAsk = "ns" in st && !psAsk && psSaidNo && due(g, "ns", now)
        if (!psAsk && !nsAsk) return null
        val base = Prices.item(lib, g, now)
        val ps = if (psAsk) Backup.str(base["ps"]).takeIf { it != "-" } ?: "" else "-"
        val ns = if (nsAsk) Backup.str(base["ns"]).takeIf { it != "-" } ?: "" else "-"
        val out = LinkedHashMap<String, JsonElement>(base)
        out["st"] = Backup.jsNumber(-1.0)
        out["ps"] = JsonPrimitive(ps)
        out["ns"] = JsonPrimitive(ns)
        if (ps == "-") { out["pc"] = JsonPrimitive(""); out.remove("p5") } else if ("p5" !in out) out["p5"] = JsonPrimitive(1)
        return JsonObject(out)
    }

    /**
     * Brings the cards' prices up to date: at most [MOST] games a pass, five a call, with a second pass for the games PS5
     * said no to (their Switch price). Never throws; a failure waits 10 minutes and keeps the prices it has.
     */
    suspend fun refresh(lib: Library, rk: Rank, cc: String, dir: File?, now: () -> Long): Unit = withContext(Dispatchers.Default) {
        if (!Prices.on(lib) || !running.compareAndSet(false, true)) return@withContext
        var got = false
        try {
            if (load(dir)) v++
            if (now() - failedAt < 600_000L) return@withContext
            repeat(2) {
                val due = lib.games.asSequence().mapNotNull { g -> ask(lib, rk, g, now())?.let { g to it } }.take(MOST).toList()
                for (c in due.chunked(BATCH)) {
                    for ((g, item) in c) for (s in listOf("ps", "ns")) if (Backup.str(item[s]) != "-") asked[g.id + ":" + s] = now()
                    val res = Prices.askMany(lib, cc, c.map { it.second }, now)
                    for ((g, _) in c) Prices.take(lib, g, res, now())
                    got = true
                    v++
                    delay(300)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedAt = now()
        } finally {
            if (got) save(dir)
            running.set(false)
        }
    }

    private fun load(dir: File?): Boolean {
        if (dir == null || !loaded.compareAndSet(false, true)) return false
        return runCatching { File(dir, FILE).takeIf { it.isFile }?.readText()?.let(Prices::restore) != null }.getOrDefault(false)
    }

    private fun save(dir: File?) {
        if (dir == null) return
        runCatching {
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(Prices.dump())
            if (!tmp.renameTo(File(dir, FILE))) tmp.delete()
        }
    }

    internal fun forget() { loaded.set(false); failedAt = 0L; asked.clear() }
}
