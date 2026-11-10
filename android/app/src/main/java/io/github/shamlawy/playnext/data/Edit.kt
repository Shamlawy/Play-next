package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A game's Edit (`openEdit`, index.html 5369-6150; android/parity/2-map/2-edit.md §1-6, 9): a draft of the game that
 * every control writes to and nothing else reads, until Save puts what changed into the one practice copy
 * ([Games.change]). Cancel throws the draft away. Pure: the sheet keeps a [Draft] and asks this for summaries, the
 * review list and the practice keys Save writes.
 *
 * Android differs from the web on purpose in four places (3-critic.md): the release date has its own picker (W7);
 * Look leaves out the sliders that only moved Edit's own preview of the old card (Q2, W9) and the hub figure (the
 * owner, Nov 10); Remove is the Queue's practice delete with its Undo (X3); Move back to the queue also gives the game
 * a state that isn't Played (X5).
 */
object Edit {
    /**
     * The draft: [g] is the game being edited (the web's `editing`, a copy of the game plus what the controls set,
     * typed boxes under "in:" keys), [p] what the Played group set on its record ("real", "dk", "runs", "again",
     * "fin"), null for a game without one.
     */
    @androidx.compose.runtime.Immutable
    class Draft(val g: JsonObject, val p: JsonObject?) {
        fun set(k: String, v: JsonElement?): Draft = Draft(JsonObject(if (v == null) g - k else g + (k to v)), p)
        fun setAll(m: Map<String, JsonElement?>): Draft = Draft(JsonObject(m.entries.fold(g.toMap()) { a, (k, v) -> if (v == null) a - k else a + (k to v) }), p)
        fun rec(k: String, v: JsonElement?): Draft = Draft(g, p?.let { JsonObject(if (v == null) it - k else it + (k to v)) })
        operator fun get(k: String): JsonElement? = g[k]
        fun str(k: String): String = Backup.str(g[k])

        /** A typed box's text: what was typed, else what the game had. */
        fun typed(k: String, had: String): String = (g["in:$k"] as? JsonPrimitive)?.content ?: had

        fun text(): String = JsonObject(mapOf("g" to g, "p" to (p ?: JsonNull))).toString()

        companion object {
            fun of(text: String): Draft? = runCatching {
                val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as JsonObject
                Draft(o["g"] as JsonObject, o["p"] as? JsonObject)
            }.getOrNull()
        }
    }

    /** The draft a game's Edit opens with (`openEdit`: a game with a played record reads as Played). */
    fun open(lib: Library, g: Game): Draft {
        val rec = lib.played.firstOrNull { it.id == g.id }
        val d = if (rec != null && g.rawState != "played") JsonObject(g.o + ("state" to JsonPrimitive("played"))) else g.o
        return Draft(d, rec?.let { JsonObject(emptyMap()) })
    }

    // ---- the three tabs and their groups, in the web's order (chips Game | Look | Scores) ----

    val TABS = listOf("game" to "Game", "look" to "Look", "scores" to "Scores")

    /** Each tab's groups (14 without the hub figure; Played only with a record; 3-critic.md (a)). */
    fun groups(tab: String, played: Boolean): List<String> = when (tab) {
        "game" -> listOf("Name", "Release", "Where it stands", "Platforms", "Genres and tags", "Notes")
        "scores" -> listOf("Hype", "Critics", "My rating") + if (played) listOf("Played") else emptyList()
        else -> listOf("Cover and colour", "Backdrop", "Logo / character art")
    }

    /** The grey line under each group's name (`editSums`, index.html 5185), from the game as saved. */
    fun sums(q: Queue, g: Game, rec: Played?): Map<String, String> {
        val o = g.o
        val mine = o["mine"] as? JsonObject
        val rated = Web.RATINGS.count { (k) -> Backup.num(mine?.get(k), 0.0, -1e9, 1e9) > 0 }
        val rel = listOf(
            if (g.date.isNotEmpty()) q.shortDate(g.date).ifEmpty { "Invalid Date" } else "No date",
            if (Backup.truthy(o["hours"])) Backup.str(o["hours"]) + "h" else "",
            Backup.str(o["status"]),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        val img = Backup.str(o["img"])
        val art = Backup.str(o["art"])
        val desc = Backup.str(o["desc"])
        return mapOf(
            "Name" to if (desc.isNotEmpty()) desc.take(40) else "No note",
            "Release" to rel,
            "Where it stands" to (Web.STATES.firstOrNull { it.first == g.state }?.second ?: "Upcoming"),
            "Platforms" to sumList(Backup.str(o["platforms"])),
            "Genres and tags" to sumList(Backup.str(o["genres"])),
            "Notes" to listOf(if (Backup.truthy(o["learn"])) "learn" else "", if (Backup.truthy(o["tips"])) "tips" else "").filter { it.isNotEmpty() }.joinToString(" + ").ifEmpty { "Empty" },
            "Hype" to (Backup.str(o["hype"]).takeIf { Backup.truthy(o["hype"]) } ?: Queue.DASH) + "/10",
            "Critics" to if (Backup.truthy(o["critic"])) Backup.str(o["critic"]) + "/100" else "None yet",
            // the web's "of 4" with 8 areas, as printed (3-critic.md C17)
            "My rating" to if (rated > 0) "$rated of 4 rated · ${Rank.jsRound(q.avgScore(g) * 10).toInt()}/100" else "Not rated",
            "Played" to (rec?.let { Backup.str(it.o["real"]).takeIf { _ -> Backup.truthy(it.o["real"]) } } ?: Queue.DASH) + "/10",
            "Cover and colour" to if (img.isEmpty()) "Placeholder" else if (img.startsWith("data:")) "Uploaded" else "Linked",
            "Backdrop" to if (Backup.truthy(o["bgOff"])) "Off" else if (Backup.truthy(o["hero"]) || Backup.truthy(o["bg"]) || Backup.truthy(o["steamId"])) "Set" else "Auto",
            "Logo / character art" to if (art.isEmpty()) "None" else if (Backup.str(o["artSide"]) == "left") "Set, left" else "Set, right",
        )
    }

    /** `sumList(s, "None picked")`: the comma list's names, or "None picked". */
    private fun sumList(s: String): String = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "None picked" }

    // ---- Release: the date and its certainty (index.html 5646-5680) ----

    /** A date picked (or cleared, ""): from tba a date makes it confirmed; no date makes it tba. */
    fun pickDate(d: Draft, date: String): Draft {
        val st = d.str("status")
        return d.setAll(mapOf("date" to JsonPrimitive(date), "status" to if (date.isEmpty()) JsonPrimitive("tba") else if (st == "tba") JsonPrimitive("confirmed") else d["status"]))
    }

    /** A certainty chip: tba with a date clears the date (the line says why, [DATE_GONE]). */
    fun pickStatus(d: Draft, v: String): Pair<Draft, String?> =
        if (v == "tba" && d.str("date").isNotEmpty()) d.setAll(mapOf("status" to JsonPrimitive(v), "date" to JsonPrimitive(""))) to DATE_GONE
        else d.set("status", JsonPrimitive(v)) to null

    const val DATE_GONE = "Date cleared — TBA means there isn't one."

    /** `#e-datesay`: `shortDate(v) + " " + year`, or the TBA line. */
    fun dateSay(q: Queue, date: String): String {
        if (date.isEmpty()) return "No date — shows as TBA"
        val at = JsDate.utcMidnight(date) ?: return "Invalid Date NaN"
        return q.shortDate(date) + " " + Instant.ofEpochMilli(at).atZone(q.zone).year
    }

    /** The hours boxes' placeholders: "≈ N" from the main story's hours (none without them). */
    fun hoursHint(g: Game, k: String): String {
        val h = Backup.num(g["hours"], 0.0, -1e9, 1e9)
        if (!Backup.truthy(g["hours"])) return ""
        val x = Backup.num(g["hoursX"], 0.0, -1e9, 1e9)
        return "≈ " + Rank.jsRound(if (k == "hoursX") h * 1.5 else if (Backup.truthy(g["hoursX"])) x * 1.6 else h * 2.4).toLong()
    }

    /** "You play main story, so …" (the play style is Settings' `S.playStyle`; Edit only says it). */
    fun styleLabel(lib: Library): String {
        val s = Backup.str(lib["playStyle"])
        return (Web.PLAY_STYLES.firstOrNull { it.first == s } ?: Web.PLAY_STYLES[0]).second.lowercase()
    }

    /** HowLongToBeat's numbers in the backup (`g.hl`), when it has a main story time. */
    fun hl(g: Game): JsonObject? = (g["hl"] as? JsonObject)?.takeIf { Backup.num(it["m"], 0.0, -1e9, 1e9) > 0 }

    /** `hlFmt`: under 10 to the nearest half ("9½"), else whole; "—" for none. */
    fun hlFmt(h: Double): String = if (!(h > 0)) Queue.DASH else if (h < 10) jsNum(Math.round(h * 2) / 2.0).replace(".5", "½") else Rank.jsRound(h).toLong().toString()

    private val HL_K = listOf("hours" to "m", "hoursX" to "x", "hours100" to "c")

    /** `hlSame`: the game's hours are HowLongToBeat's (a missing extras/100% on its side matches anything). */
    fun hlSame(o: JsonObject, hl: JsonObject): Boolean = HL_K.all { (k, kk) ->
        val v = hl[kk]?.let { Backup.num(it, 0.0, -1e9, 1e9) } ?: 0.0
        jsPlus(o[k]) == v || (kk != "m" && !(v > 0))
    }

    /** "Use these": HowLongToBeat's three numbers into the boxes (blank for none). */
    fun useHl(d: Draft, hl: JsonObject): Draft = d.setAll(HL_K.associate { (k, kk) ->
        val v = Backup.num(hl[kk], 0.0, -1e9, 1e9)
        "in:$k" to JsonPrimitive(if (v > 0) jsNum(v) else "")
    })

    /** The hint under the hours: Nexi's typical time while the box still holds it (index.html 20582). */
    fun nexiHours(g: Game, d: Draft): Boolean {
        val hb = g["hbV"] ?: return false
        val typed = d.typed("hours", Backup.str(g["hours"]))
        return Backup.truthy(hb) && typed.trim().toDoubleOrNull() == Backup.num(hb, -1.0, -1e9, 1e9)
    }

    // ---- Where it stands, platforms, genres ----

    /** A name in a comma list, toggled at its end (`bindPlatPicker`); names the picker doesn't know stay as they are. */
    fun toggle(list: String, name: String): String {
        val parts = list.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val has = parts.any { it.equals(name, ignoreCase = true) }
        return (if (has) parts.filterNot { it.equals(name, ignoreCase = true) } else parts + name).joinToString(", ")
    }

    fun has(list: String, name: String): Boolean = list.split(",").any { it.trim().equals(name, ignoreCase = true) }

    // ---- Scores ----

    /** `subAvg(subs, cat)`: the mean of the parts you scored (one decimal), 0 when none. */
    fun subAvg(subs: JsonObject?, cat: String): Double {
        val v = Web.SUBRATE[cat].orEmpty().map { (k) -> Backup.num(subs?.get("$cat:$k"), 0.0, 0.0, 10.0) }.filter { it > 0 }
        if (v.isEmpty()) return 0.0
        return Rank.jsRound(v.sum() / v.size * 10) / 10.0
    }

    /** Moving one part: the part, and the area's score follows the parts' mean (none scored: left as it was). */
    fun setSub(d: Draft, cat: String, k: String, v: Int): Draft {
        val subs = JsonObject(((d["subs"] as? JsonObject).orEmpty()) + ("$cat:$k" to JsonPrimitive(v)))
        val avg = subAvg(subs, cat)
        val mine = (d["mine"] as? JsonObject).orEmpty()
        return d.setAll(mapOf("subs" to subs, "mine" to if (avg > 0) JsonObject(mine + (cat to Backup.jsNumber(avg))) else JsonObject(mine)))
    }

    fun setMine(d: Draft, cat: String, v: Int): Draft = d.set("mine", JsonObject(((d["mine"] as? JsonObject).orEmpty()) + (cat to JsonPrimitive(v))))

    /** The Played score's words: "Unscored" (Don't remember), its one decimal, "—" before you score it. */
    fun realSay(rec: Played, d: Draft): String {
        val dk = d.p?.get("dk")?.let { Backup.truthy(it) } ?: Backup.truthy(rec.o["dk"])
        if (dk) return "Unscored"
        val r = d.p?.get("real")
        if (r != null) return String.format(java.util.Locale.US, "%.1f", Backup.num(r, 0.0, 0.0, 10.0))
        return if (scored(rec.o)) String.format(java.util.Locale.US, "%.1f", Backup.num(rec.o["real"], 0.0, 0.0, 10.0)) else Queue.DASH
    }

    /** `scored(p)`: not "don't remember", and a number above 0 (or a real 0). */
    fun scored(p: JsonObject): Boolean = !Backup.truthy(p["dk"]) && (Backup.num(p["real"], 0.0, -1e9, 1e9) > 0 || (Backup.truthy(p["z"]) && (p["real"] as? JsonPrimitive)?.doubleOrNull == 0.0))

    /**
     * "N of M games tagged so far. At M, Taste starts telling you…" (`whyPull().n` against `analysisNeed()`): null
     * once there are enough.
     */
    fun taggedSay(lib: Library): String? {
        val need = Backup.num(lib["analysisAt"], 3.0, 1.0, 20.0).toInt()
        val have = lib.played.count { p -> scored(p.o) && lib.games.firstOrNull { it.id == p.id }?.let { (it["why"] as? JsonArray)?.isNotEmpty() } == true }
        return if (have >= need) null else "$have of $need games tagged so far. At $need, Taste starts telling you which of these actually tracks your best games."
    }

    // ---- Look: the colour row (`palRow`, index.html 4886: round the colour wheel in OKLab, greys last) ----

    val PALETTE_ROW: List<Long> by lazy {
        fun k(c: Long): Double {
            val (l, a, b) = okLab(c)
            val ch = Math.hypot(a, b)
            return if (ch < .05) 10 + l else (Math.atan2(b, a) + Math.PI * 1.75) % (Math.PI * 2)
        }
        Web.PALETTE.sortedBy { k(it) }
    }

    /** `okLab([r, g, b])` (index.html 4867). */
    fun okLab(argb: Long): Triple<Double, Double, Double> {
        fun f(v: Long): Double = (v / 255.0).let { if (it <= .04045) it / 12.92 else Math.pow((it + .055) / 1.055, 2.4) }
        val r = f((argb shr 16) and 0xFF)
        val g = f((argb shr 8) and 0xFF)
        val b = f(argb and 0xFF)
        val l = Math.cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b)
        val m = Math.cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b)
        val s = Math.cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b)
        return Triple(.2104542553 * l + .793617785 * m - .0040720468 * s, 1.9779984951 * l - 2.428592205 * m + .4505937099 * s, .0259040371 * l + .7827717662 * m - .808675766 * s)
    }

    /** `#RRGGBB` for a colour of the palette (how the web stores `g.colour`). */
    fun hex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    // ---- When you finished it (`finPick`, index.html 12826) ----

    /** The month picked: same month keeps the day, another month the 15th, this month at most today. */
    fun finDate(old: String, year: Int, month: Int, today: LocalDate): String {
        val od = runCatching { LocalDate.parse(old) }.getOrNull()
        val day = when {
            od != null && od.year == year && od.monthValue == month -> od.dayOfMonth
            year == today.year && month == today.monthValue -> minOf(od?.dayOfMonth ?: 15, today.dayOfMonth)
            else -> 15
        }
        return "%04d-%02d-%02d".format(year, month, day)
    }

    // ---- Look: links (`resolveLink` / `smartUrl`, index.html 7620, 4207) ----

    /** What a picture's link box makes of what you typed, before it's tried. */
    sealed interface Link {
        data class Use(val url: String, val note: String, val steamId: String? = null) : Link
        data class Bad(val why: String) : Link
    }

    private val STEAM = Regex("store\\.steampowered\\.com/app/(\\d+)|steamcommunity\\.com/app/(\\d+)")
    private val GH = Regex("^https?://github\\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$")

    /** Steam's file for each picture's job. */
    fun steamFile(role: String): String = when (role) { "cover" -> "library_600x900_2x.jpg"; "back" -> "library_hero.jpg"; else -> "logo.png" }

    /** `resolveLink(raw, role)`: a Steam page or bare app id becomes that game's file; a github page its raw file. */
    fun link(raw: String, role: String): Link {
        val v = raw.trim()
        if (v.isEmpty()) return Link.Bad("Nothing to use — paste a link first.")
        val id = STEAM.find(v)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } } ?: v.takeIf { Regex("^\\d{2,8}$").matches(it) }
        if (id != null) return Link.Use(Web.STEAM_CDN + "steam/apps/$id/" + steamFile(role), "Took ${steamFile(role)} from Steam. Tap Save.", id)
        GH.find(v)?.let { m -> return Link.Use("https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}", "Turned that page address into a direct image link. Tap Save.") }
        if (!Regex("^(https?:|data:)", RegexOption.IGNORE_CASE).containsMatchIn(v)) return Link.Bad("That isn't a web link. It needs to start with http.")
        return Link.Use(v, "Looks good. Tap Save.")
    }

    /** `smartUrl`: what Save keeps from a typed box (a link it can use, else nothing). */
    fun smartUrl(raw: String, role: String): String = (link(raw, role) as? Link.Use)?.url ?: ""

    /** A picture's size words: "It's 3840x2160 (4K)." (≥3000 4K, ≥1500 HD, <700 Low). */
    fun sizeSay(w: Int, h: Int): String {
        val m = maxOf(w, h)
        val tag = if (m >= 3000) " (4K)" else if (m >= 1500) " (HD)" else if (m < 700) " (Low)" else ""
        return " It's ${w}x$h$tag."
    }

    // ---- Save: what changed, as the web's collectEdit + its Save button write it ----

    /** The fields Save writes (`collectEdit`, index.html 6057), worked out from the draft; Look's preview-only ones stay out. */
    fun collect(g: Game, d: Draft): Map<String, JsonElement> {
        val o = g.o
        fun s(k: String) = Backup.str(o[k])
        fun box(k: String) = d.typed(k, s(k)).trim()
        val out = LinkedHashMap<String, JsonElement>()
        val img = smartUrl(d.typed("img", if (s("img").startsWith("http")) s("img") else ""), "cover")
        val back = smartUrl(d.typed("back", if (s("hero").startsWith("http")) s("hero") else ""), "back")
        val artUrl = smartUrl(d.typed("art", if (s("art").startsWith("http")) s("art") else ""), "art")
        out["title"] = JsonPrimitive(box("title").ifEmpty { g.title })
        out["desc"] = JsonPrimitive(box("desc"))
        out["about"] = JsonPrimitive(box("about"))
        val date = d.str("date")
        out["date"] = JsonPrimitive(date)
        for (k in listOf("hours", "hoursX", "hours100")) out[k] = Backup.jsNumber(jsPlusText(d.typed(k, if (Backup.truthy(o[k])) s(k) else "")))
        val st = d.str("status")
        out["status"] = JsonPrimitive(if (date.isNotEmpty()) (if (st == "tba") "confirmed" else st.ifEmpty { s("status").ifEmpty { "confirmed" } }) else "tba")
        d["hype"]?.let { out["hype"] = it }
        d["colour"]?.let { out["colour"] = it }
        out["colourHand"] = JsonPrimitive(Backup.truthy(d["colourHand"]))
        out["colourOf"] = JsonPrimitive(d.str("colourOf"))
        out["state"] = JsonPrimitive(d.str("state").ifEmpty { "upcoming" })
        out["platforms"] = JsonPrimitive(d.str("platforms"))
        out["genres"] = JsonPrimitive(d.str("genres"))
        out["learn"] = JsonPrimitive(box("learn"))
        out["tips"] = JsonPrimitive(box("tips"))
        out["critic"] = Backup.jsNumber(box("critic").let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: 0.0 })
        out["img"] = JsonPrimitive(
            when {
                img.isNotEmpty() -> img
                Backup.truthy(d["imgCleared"]) -> ""
                else -> d.str("img").ifEmpty { s("img") }
            },
        )
        out["imgX"] = Backup.jsNumber(Backup.num(d["imgX"], Backup.num(o["imgX"], 50.0, 0.0, 100.0), 0.0, 100.0))
        out["imgY"] = Backup.jsNumber(Backup.num(d["imgY"], Backup.num(o["imgY"], 50.0, 0.0, 100.0), 0.0, 100.0))
        out["hero"] = JsonPrimitive(if (back.isNotEmpty()) back else if (Backup.truthy(d["bgOff"])) "" else d.str("hero"))
        out["bgOff"] = JsonPrimitive(Backup.truthy(d["bgOff"]))
        out["bgX"] = Backup.jsNumber(Backup.num(d["bgX"], Backup.num(o["bgX"], 50.0, 0.0, 100.0), 0.0, 100.0))
        out["bgY"] = Backup.jsNumber(Backup.num(d["bgY"], Backup.num(o["bgY"], 50.0, 0.0, 100.0), 0.0, 100.0))
        out["art"] = JsonPrimitive(
            when {
                artUrl.isNotEmpty() -> artUrl
                Backup.truthy(d["artCleared"]) -> ""
                s("art").startsWith("http") && d.str("art").startsWith("http") -> ""
                else -> d.str("art")
            },
        )
        out["steamId"] = JsonPrimitive(d.str("steamId").ifEmpty { s("steamId") })
        out["why"] = d["why"] as? JsonArray ?: JsonArray(emptyList())
        out["mine"] = d["mine"] as? JsonObject ?: JsonObject(emptyMap())
        val subs = d["subs"] as? JsonObject
        out["subs"] = if (subs != null && subs.isNotEmpty()) subs else (o["subs"] as? JsonObject ?: JsonObject(emptyMap()))
        return out
    }

    /** A value Save writes that changes nothing: the same, or empty both times ("", 0, false, {}, [] and missing). */
    private fun same(a: JsonElement?, b: JsonElement?): Boolean {
        if (a == b) return true
        fun empty(e: JsonElement?) = e == null || e is JsonNull || (e is JsonPrimitive && !Backup.truthy(e)) || (e is JsonObject && e.isEmpty()) || (e is JsonArray && e.isEmpty())
        if (empty(a) && empty(b)) return true
        val x = (a as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        val y = (b as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        return x != null && x == y
    }

    /**
     * Save (`#e-save`): the practice keys' new values. The game's changed fields (and `hrsSrc` / `hbV` when the hours
     * changed, 23427), `syncPlayed` for the state, then the record: Don't remember or the score (`scoreWrite`), times
     * through, another run, the month you finished it.
     */
    fun save(lib: Library, g: Game, d: Draft, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val next = collect(g, d)
        // against what the untouched draft would write (the web writes every field, its defaults included: framing 50,
        // backdrop off false; here only what you changed goes in)
        val base = collect(g, open(lib, g))
        val f = LinkedHashMap<String, JsonElement>()
        for ((k, v) in next) if (!same(base[k], v) && !same(g[k], v)) f[k] = v
        // `open` reads a recorded game as Played; that repair is the web's too
        if (g.rawState != d.str("state").ifEmpty { "upcoming" }) f["state"] = next["state"]!!
        // whose numbers: changed hours are yours, or HowLongToBeat's when they're its own
        val after = JsonObject(g.o + f)
        if (HL_K.any { (k) -> jsPlus(g[k]) != jsPlus(after[k]) }) {
            val hl = hl(g)
            val src = if (jsPlus(after["hours"]) > 0) (if (hl != null && hlSame(after, hl)) "hltb" else "me") else ""
            f["hrsSrc"] = if (src.isEmpty()) JsonNull else JsonPrimitive(src)
            if (g["hbV"] != null) f["hbV"] = JsonNull
        }
        val state = d.str("state").ifEmpty { "upcoming" }
        val out = LinkedHashMap<String, JsonElement>()
        val rec0 = lib.played.firstOrNull { it.id == g.id }
        // `syncPlayed`'s v218 hook: starting play stamps when, leaving playing for another state forgets it
        if (state == "playing" && !Backup.truthy(g["plAt"])) f["plAt"] = JsonPrimitive(now)
        if (state != "playing" && Backup.truthy(g["plAt"]) && state != g.rawState) f["plAt"] = JsonNull
        if (state == "dropped" && g.rawState != "dropped" && !Backup.truthy(g["dropP"])) f["dropAt"] = JsonPrimitive(now)
        var rec: JsonObject? = rec0?.o
        if (state == "played" && rec == null) rec = JsonObject(mapOf("id" to JsonPrimitive(g.id), "real" to JsonPrimitive(0), "t" to JsonPrimitive(JsDate.todayUtc(now))))
        if (state != "played") rec = null
        val p = d.p
        if (rec != null && rec0 != null && p != null) {
            val r = LinkedHashMap(rec)
            if (Backup.truthy(p["dk"])) {
                r["real"] = JsonPrimitive(0); r["dk"] = JsonPrimitive(1); r.remove("z")
            } else p["real"]?.let { v ->
                val n = Backup.num(v, Backup.num(r["real"], 0.0, 0.0, 10.0), 0.0, 10.0)
                r["real"] = Backup.jsNumber(n); r.remove("dk")
                if (n == 0.0) r["z"] = JsonPrimitive(1) else r.remove("z")
            }
            r["runs"] = Backup.jsNumber(Backup.num(p["runs"], Backup.num(r["runs"], 1.0, 1.0, 12.0), 1.0, 12.0))
            p["again"]?.let { r["again"] = it }
            val fin = Backup.str(p["fin"])
            if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(fin)) r["t"] = JsonPrimitive(fin)
            rec = JsonObject(r)
        }
        if (f.isNotEmpty()) out[Practice.GAMES] = Practice.fields(cur(Practice.GAMES), g.id, f)
        if (rec != rec0?.o) out[Practice.RECS] = Practice.record(cur(Practice.RECS), g.id, rec)
        return out
    }

    /**
     * "Move back to the queue" (`#e-unplay`): at once, no Save: the record goes, and the state with it (X5: the web
     * leaves Played on a game that reads as unplayed; here it's out, not played, or upcoming for a date to come).
     */
    fun unplay(g: Game, now: Long, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val st = if (g.date.isNotEmpty() && (JsDate.utcMidnight(g.date) ?: 0L) > now) "upcoming" else "backlog"
        return mapOf(
            Practice.RECS to Practice.record(cur(Practice.RECS), g.id, null),
            Practice.GAMES to Practice.fields(cur(Practice.GAMES), g.id, mapOf("state" to JsonPrimitive(st))),
        )
    }

    /** The card back's Competitive (`compSet`): on keeps the hours aside (`hoursWas`), off gives them back. */
    fun comp(g: Game, on: Boolean, cur: (String) -> JsonElement?): Map<String, JsonElement> {
        val f = LinkedHashMap<String, JsonElement>()
        f["comp"] = JsonPrimitive(on)
        if (on) {
            if (jsPlus(g["hours"]) != 0.0) f["hoursWas"] = Backup.jsNumber(jsPlus(g["hours"]))
            f["hours"] = JsonPrimitive(0)
        } else if (Backup.truthy(g["hoursWas"])) {
            f["hours"] = g["hoursWas"]!!
            f["hoursWas"] = JsonNull
        }
        return mapOf(Practice.GAMES to Practice.fields(cur(Practice.GAMES), g.id, f))
    }

    // ---- Review changes (`pendingEdits`, index.html 6029) ----

    private val FIELD_NAMES = listOf(
        "title" to "Title", "desc" to "Why you want it", "about" to "What it's about", "date" to "Release date",
        "hours" to "Main story hours", "hoursX" to "Story + extras hours", "hours100" to "100% hours", "status" to "Date certainty", "hype" to "Hype",
        "state" to "Where it stands", "platforms" to "Platforms", "genres" to "Genres", "learn" to "What I can learn", "tips" to "How to play it",
        "critic" to "Critic score", "colour" to "Colour", "img" to "Cover", "art" to "Character art", "hero" to "Backdrop", "bgOff" to "Backdrop off",
        "imgX" to "Cover framing across", "imgY" to "Cover framing down", "bgX" to "Backdrop framing across", "bgY" to "Backdrop framing down",
        "real" to "What it deserved", "runs" to "Times through", "why" to "What got you",
    )

    /** One row of the review: the field's name, what it was and what it will be. */
    data class Change(val field: String, val was: String, val now: String)

    /** Every field Save would change, in the web's order. */
    fun review(lib: Library, g: Game, d: Draft): List<Change> {
        val next = LinkedHashMap<String, JsonElement?>(collect(g, d))
        // the web compares with the saved game, so an untouched framing or backdrop switch shows as a change
        // ("empty → 50"); here a field you left alone never shows
        val base = collect(g, open(lib, g))
        for (k in next.keys.toList()) if (next[k] == base[k]) next[k] = g[k]
        val rec = lib.played.firstOrNull { it.id == g.id }
        if (rec != null) {
            val p = d.p
            next["real"] = p?.get("real")?.let { Backup.jsNumber(Backup.num(it, Backup.num(rec.o["real"], 0.0, 0.0, 10.0), 0.0, 10.0)) } ?: rec.o["real"]
            next["runs"] = Backup.jsNumber(Backup.num(p?.get("runs"), Backup.num(rec.o["runs"], 1.0, 1.0, 12.0), 1.0, 12.0))
        }
        return FIELD_NAMES.mapNotNull { (k, name) ->
            if (k !in next) return@mapNotNull null
            val was = when (k) {
                "real", "runs" -> rec?.o?.get(k)
                "state" -> JsonPrimitive(g.state)
                "status" -> JsonPrimitive(Backup.str(g["status"]))
                else -> g[k]
            }
            val now = next[k]
            if (norm(was) == norm(now)) null else Change(name, shortVal(was), shortVal(now))
        }
    }

    /** "Nothing has changed yet." / "N change(s) waiting:" */
    fun reviewHead(n: Int): String = if (n == 0) "Nothing has changed yet." else "$n change${if (n == 1) "" else "s"} waiting:"

    /** Empty, missing and zero all mean "not set" (`String(v)` otherwise, as JavaScript prints it). */
    private fun norm(e: JsonElement?): String = when (e) {
        null, JsonNull -> ""
        is JsonPrimitive -> if (e.isString) e.content else if (e.booleanOrNull != null) e.content else e.doubleOrNull?.let { if (it == 0.0) "" else jsNum(it) } ?: e.content
        is JsonArray -> e.joinToString(",") { Backup.str(it) }
        is JsonObject -> "[object Object]"
    }

    private fun shortVal(e: JsonElement?): String {
        val t = norm(e)
        if (e == null || e is JsonNull || (e is JsonPrimitive && e.isString && e.content.isEmpty())) return "empty"
        if (t.isEmpty() && e is JsonPrimitive && !e.isString) return "0"
        if (t.startsWith("data:")) return "an upload"
        if (Regex("^https?:").containsMatchIn(t)) return "a link"
        return if (t.length > 28) t.take(28) + "…" else t
    }

    // ---- JavaScript's numbers ----

    /** `+v || 0` for a stored value. */
    private fun jsPlus(e: JsonElement?): Double = Backup.num(e, 0.0, -1e15, 1e15).let { if (it.isNaN()) 0.0 else it }

    /** `+box.value || 0` for a typed box ("" and words are 0). */
    private fun jsPlusText(t: String): Double = t.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull()?.takeIf { d -> d.isFinite() } ?: 0.0 }

    /** A number as JavaScript prints it: "24", "9.5". */
    fun jsNum(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    /** Today on the phone. */
    fun today(now: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}
