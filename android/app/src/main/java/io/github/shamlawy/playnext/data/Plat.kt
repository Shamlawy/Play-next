package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.JsonElement

/**
 * A game's systems as the web draws them (`platOf`, `platChips`, index.html 7798-7828): each family once, in
 * `PLAT_ORDER`, with its versions sorted ("4·Vita", "One·360"), then at most two systems it has no logo for, as words.
 */
object Plat {
    /** One family's logo ([k], drawn from `PLAT_LOGO` as the icon "plat:k"; "io" is the word iOS) and its versions. */
    data class Chip(val k: String?, val sub: String, val text: String?)

    private val ORDER = listOf("ps", "sw", "xb", "pc", "dk", "ap", "io", "an", "lx")

    /** `platOf(n)`: [family, version], or null for a system with no logo. */
    fun of(n: String): Pair<String, String>? {
        val t = n.lowercase().replace(Regex("\\s+"), " ").trim()
        Regex("^(?:ps|playstation)\\s?(\\d)$").find(t)?.let { return "ps" to it.groupValues[1] }
        if (Regex("^(playstation|ps)$").matches(t)) return "ps" to ""
        if (Regex("^(ps ?vita|playstation vita)$").matches(t)) return "ps" to "Vita"
        if (Regex("^(psp|playstation portable)$").matches(t)) return "ps" to "PSP"
        Regex("^(?:nintendo )?switch(?: ?(2))?$|^ns(2)?$").find(t)?.let { m ->
            return "sw" to m.groupValues[1].ifEmpty { m.groupValues[2] }
        }
        if (t.startsWith("xbox series")) return "xb" to "X|S"
        if (t == "xbox one") return "xb" to "One"
        if (t == "xbox 360") return "xb" to "360"
        if (t == "xbox") return "xb" to ""
        if (Regex("^(pc|windows|steam|pc \\(windows\\)|microsoft windows)$").matches(t)) return "pc" to ""
        if (Regex("^steam ?deck$").matches(t)) return "dk" to ""
        if (Regex("^(mac|macos|mac os|os x|apple macintosh)$").matches(t)) return "ap" to ""
        if (Regex("^(ios|iphone|ipad)$").matches(t)) return "io" to ""
        if (t == "android") return "an" to ""
        if (t == "linux") return "lx" to ""
        return null
    }

    /** `platChips(str)`: the logos in order, then up to two plain names. */
    fun chips(e: JsonElement?): List<Chip> {
        val subs = LinkedHashMap<String, MutableList<String>>()
        val rest = mutableListOf<String>()
        for (n in Rank.platList(e)) {
            val f = of(n)
            if (f == null) { rest += n; continue }
            val l = subs.getOrPut(f.first) { mutableListOf() }
            if (f.second.isNotEmpty() && f.second !in l) l += f.second
        }
        // `(parseFloat(x) || 99)`: a version that isn't a number sorts last (a stable sort keeps their order)
        fun key(s: String) = Regex("^\\s*[-+]?(\\d+\\.?\\d*|\\.\\d+)").find(s)?.value?.trim()?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 99.0
        return ORDER.filter { it in subs }.map { k -> Chip(k, subs.getValue(k).sortedBy(::key).joinToString(" · ").replace(" ", ""), null) } +
            rest.take(2).map { Chip(null, "", it) }
    }
}
