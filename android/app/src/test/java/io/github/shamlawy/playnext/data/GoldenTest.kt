package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.Repo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * The app's numbers against the web app's own (tools/golden.mjs runs index.html with UAE time at noon, 11:30 PM and
 * 12:30 AM and writes android/app/build/golden/golden.json). android-check makes the file first and sets
 * PN_GOLDEN=required, so there a missing file fails; elsewhere the test waits for `node tools/golden.mjs`.
 */
class GoldenTest {
    private val gold: JsonObject? by lazy {
        val f = File(Repo.dir, "android/app/build/golden/golden.json")
        if (!f.isFile) {
            if (System.getenv("PN_GOLDEN") == "required") error("No golden file: run node tools/golden.mjs first")
            null
        } else Json.parseToJsonElement(f.readText()).jsonObject
    }

    private fun golden(): JsonObject {
        assumeTrue("no golden file (node tools/golden.mjs)", gold != null)
        return gold!!
    }

    private val JsonElement.s get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

    @Test
    fun restoreCodesMatch() {
        for (c in golden()["codes"]!!.jsonArray.map { it.jsonObject }) {
            val code = c["code"]!!.s
            assertEquals(code, c["norm"]!!.s, Code.normalize(code))
            assertEquals(code, c["fmt"]!!.s, Code.format(code))
            if (c["norm"]!!.s.isEmpty()) continue
            val k = Code.keys(code)
            assertEquals(code, c["id"]!!.s, k.id)
            assertEquals(code, c["tok"]!!.s, k.tok)
            assertEquals(code, c["key"]!!.s, Code.hex(k.key))
            val sealed = c["sealed"]!!.s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertEquals("the web app's own sealed copy opens", c["text"]!!.s, Seal.open(sealed, k.key))
        }
    }

    @Test
    fun readBackupSaysTheSame() {
        for (r in golden()["reads"]!!.jsonArray.map { it.jsonObject }) {
            val raw = r["raw"]!!.s
            val got = Backup.read(raw)
            assertEquals(raw, r["ok"]!!.jsonPrimitive.boolean, got.ok)
            // the web app's JSON.parse names a position; the app says "Damaged" without one
            val msg = r["msg"]!!.s.replace(Regex("^Damaged around character \\d+ of \\d+"), "Damaged")
            assertEquals(raw, msg, got.msg)
        }
    }

    @Test
    fun restoreAndStartMatchAtEveryClock() {
        val zone = ZoneId.of(golden()["zone"]!!.s)
        for (c in golden()["cases"]!!.jsonArray.map { it.jsonObject }) {
            val name = c["name"]!!.s
            val now = c["now"]!!.jsonPrimitive.long
            val rb = Backup.read(c["raw"]!!.s)
            val read = c["read"]!!.jsonObject
            assertEquals(name, read["msg"]!!.s, rb.msg)

            val applied = Backup.apply(rb.data!!)
            assertEquals("$name: the games kept", c["games"]!!.jsonArray.map { it.s }, Library(applied).games.map { it.id })
            same("$name: played after the restore", c["played"]!!, applied["played"]!!)
            for (k in listOf("syncTok", "syncOn", "rawgKey", "v")) {
                assertFalse("$name: $k is dropped", k in applied.keys)
                assertFalse("$name: the web app dropped $k too", c["keys"]!!.jsonArray.any { it.s == k })
            }
            assertEquals("$name: unknown keys survive", rb.data["somethingNew"], applied["somethingNew"])

            val loaded = Backup.load(applied, now, zone)
            val lib = Library(loaded)
            val want = c["loaded"]!!.jsonObject
            val states = want["states"]!!.jsonObject.mapValues { it.value.s }
            assertEquals("$name: states after start", states, lib.games.associate { it.id to it.state })
            same("$name: played after start", want["played"]!!, loaded["played"]!!)
            assertEquals("$name: today (local)", want["todayLocal"]!!.s, JsDate.todayLocal(now, zone))
            assertEquals("$name: today (UTC)", want["todayUtc"]!!.s, JsDate.todayUtc(now))
            val per = want["per"]!!.jsonObject
            for (g in lib.games) {
                val p = per[g.id]!!.jsonObject
                assertEquals("$name ${g.id}: days()", p["days"]!!.s, JsDate.days(g.date, now))
                assertEquals("$name ${g.id}: isUpcoming", p["isUpcoming"]!!.jsonPrimitive.boolean, g.isUpcoming(now))
                assertEquals("$name ${g.id}: isFresh", p["isFresh"]!!.jsonPrimitive.boolean, g.isFresh(now, zone))
            }
            assertEquals("$name: a game's unknown fields survive", rb.data["games"]!!.jsonArray[0].jsonObject["xUnknown"], lib.games[0]["xUnknown"])
        }
    }

    /** The header cards (Piece 3): each screen's covers, numbers, line, genres, mix bar, chart and backdrop. */
    @Test
    fun headerCardsMatchAtEveryClock() {
        val zone = ZoneId.of(golden()["zone"]!!.s)
        val cases = golden()["shx"]?.jsonArray
        assertTrue("the golden file has header-card cases", !cases.isNullOrEmpty())
        for (c in cases!!.map { it.jsonObject }) {
            val name = c["name"]!!.s
            val now = c["now"]!!.jsonPrimitive.long
            val lib = Library(Backup.load(Backup.apply(Backup.read(c["raw"]!!.s).data!!), now, zone))
            val kinds = c["kinds"]!!.jsonObject
            assertEquals("$name: every screen", Shx.KINDS.toSet(), kinds.keys)
            for (k in Shx.KINDS) {
                val w = kinds[k]!!.jsonObject
                val got = Shx.of(k, lib, now, zone)
                val at = "$name $k"
                assertEquals("$at: covers", w["c"]!!.jsonArray.map { it.s }, got.covers.map { it.id })
                assertEquals("$at: numbers", w["st"]!!.jsonArray.map { it.jsonArray.let { s -> s[0].s to s[1].s } },
                    got.stats.map { (it.value + (it.unit?.let { u -> "<em>$u</em>" } ?: "")) to it.label })
                val line = w["line"]?.takeIf { it !is JsonNull }?.jsonObject
                assertEquals("$at: line", line?.let { listOf(it["id"]!!.s, it["k"]!!.s, it["x"]!!.s) }, got.line?.let { listOf(it.g.id, it.k, it.x) })
                assertEquals("$at: genres", w["tags"]!!.jsonArray.map { it.jsonArray.let { t -> t[0].s to t[1].jsonPrimitive.long.toInt() } }, got.tags)
                val bar = w["bar"]?.takeIf { it !is JsonNull }?.jsonArray
                assertEquals("$at: mix bar", bar?.map { it.jsonArray.let { b -> Triple(b[0].jsonPrimitive.long.toInt(), b[1].s, b[2].jsonPrimitive.long.toInt()) } },
                    got.bar?.map { Triple(it.n, it.label, it.c) })
                val chart = w["chart"]?.takeIf { it !is JsonNull }?.jsonObject
                assertEquals("$at: chart", chart?.let { it["v"]!!.jsonArray.map { v -> v.jsonPrimitive.long.toInt() } to it["cap"]!!.s }, got.chart?.let { it.v to it.cap })
                val art = when (val a = got.art) { null -> ""; is Pic.Drawn -> "drawn"; is Pic.Url -> a.url }
                assertEquals("$at: backdrop", w["art"]!!.s, art)
            }
        }
    }

    /** JSON values equal as the web app reads them: numbers by value (9 == 9.0), everything else exactly. */
    private fun same(what: String, a: JsonElement, b: JsonElement) {
        fun norm(e: JsonElement): Any? = when (e) {
            is JsonNull -> null
            is JsonPrimitive -> if (e.isString) "s:" + e.content else e.doubleOrNull ?: e.content
            is JsonArray -> e.map(::norm)
            is JsonObject -> e.mapValues { norm(it.value) }.toList()
        }
        assertEquals(what, norm(a), norm(b))
    }
}
