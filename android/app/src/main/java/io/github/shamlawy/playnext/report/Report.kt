package io.github.shamlawy.playnext.report

import io.github.shamlawy.playnext.data.Library
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** The web app's report shape (index.html bugReport), plus `app`, which the helper keeps for "android". */
@Serializable
data class Report(
    val sig: String,
    val kind: String,
    val msg: String,
    val stack: String = "",
    val at: String = "",
    val screen: String = "",
    val note: String = "",
    val w: Int = 0,
    val h: Int = 0,
    val dpr: Double = 0.0,
    val dev: String = "",
    val v: String = "",
    val style: String = "app",
    val crumbs: List<String> = emptyList(),
    val lib: String = "",
    val app: String = "android",
)

/** Pure rules shared with the web app, kept apart from Android so plain JVM tests can check them. */
object ReportRules {
    /** FNV-1a 32 bit over UTF-16 units, the same as index.html's bugHash. */
    fun hash(t: String): String {
        var h = 0x811c9dc5.toInt()
        for (c in t) {
            h = h xor c.code
            h *= 0x01000193
        }
        return (h.toLong() and 0xffffffffL).toString(16).padStart(8, '0')
    }

    /**
     * One problem = one signature: numbers ignored, the line:col cut off `at`. The "android|" start keeps every app sig
     * apart from the web app's, which share the helper's store. [apart] splits words the digits alone would merge (the
     * ran report: one per version, so two releases on one day are two proofs).
     */
    fun sig(kind: String, msg: String, at: String, screen: String, now: Long, apart: String = ""): String = hash(
        "android|" + kind + "|" + msg.replace(Regex("\\d+"), "#").take(240) + "|" + at.replace(Regex(":\\d+(:\\d+)?$"), "") +
            (if (kind == "layout" || kind == "slow") "|$screen" else "") +
            (if (kind == "user") "|$now" else "") +
            (if (apart.isNotEmpty()) "|$apart" else ""),
    )

    /**
     * The report's library line (`bugLib`, index.html 17527): sizes only, never names. "Out" counts games that are out
     * and not finished or dropped (the web's line reads `g.status`, which holds the date's certainty, so it always says
     * 0 out, 0 coming).
     */
    fun libLine(lib: Library, now: Long): String {
        var up = 0
        var kb = 0.0
        var long = 0
        for (g in lib.games) {
            long = maxOf(long, g.title.length)
            for (k in listOf("img", "key", "art")) {
                val v = (g[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                if (v.startsWith("data:")) { up++; kb += v.length * .75 / 1024 }
            }
        }
        val coming = lib.games.count { it.isUpcoming(now) }
        val out = lib.games.count { it.state != "played" && it.state != "dropped" && !it.isUpcoming(now) }
        val lists = (lib["lists"] as? JsonArray)?.size ?: 0
        return "${lib.games.size} games ($out out, $coming coming), $lists lists, $up uploaded pictures (${Math.round(kb)}KB), longest title $long chars"
    }
}

/** Titles, list names, keys and the restore code never leave the phone. */
object Scrub {
    private val query = Regex("([?&](?:key|token|tok|sig|id|code)=)[^&\\s\"']+", RegexOption.IGNORE_CASE)
    private val restoreCode = Regex("\\bPN(?:[-\\s]?[A-Z0-9]{2,8}){3,}\\b", RegexOption.IGNORE_CASE)
    private val bareCode = Regex("\\b[A-Z0-9]{20}\\b")
    private val hex = Regex("\\b(?:[0-9a-fA-F]{64}|[0-9a-fA-F]{24})\\b")
    private val dataUrl = Regex("data:[^\\s)\"']{40,}")

    fun text(s: String?, names: Collection<String> = emptyList(), secrets: Collection<String> = emptyList()): String {
        var t = (s ?: "").take(2000)
        for (n in names.filter { it.length > 2 }.sortedByDescending { it.length }) t = t.replace(n, "‹game›", ignoreCase = true)
        for (k in secrets.filter { it.length > 6 }) t = t.replace(k, "…")
        t = query.replace(t, "$1…")
        t = restoreCode.replace(t, "PN-…")
        t = hex.replace(t, "…")
        t = bareCode.replace(t, "…")
        t = dataUrl.replace(t, "data:…")
        return t
    }
}
