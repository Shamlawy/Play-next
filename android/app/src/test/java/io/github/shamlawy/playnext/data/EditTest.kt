package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Edit's rules (android/parity/2-map/2-edit.md), each as the web's openEdit / collectEdit / Save do them, on made-up
 * games: Save writes only what changed into the practice keys, the date and its certainty move together, typed hours
 * become yours, the Played record follows Don't remember and the score, Move back to the queue gives a state that
 * isn't Played, Competitive keeps the hours aside, and the review lists the changes in the web's words.
 */
class EditTest {
    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Dubai")

    private val lib = Library(Json.parseToJsonElement(LIB).jsonObject)
    private fun g(id: String) = lib.games.first { it.id == id }
    private val none: (String) -> JsonElement? = { null }

    private fun fields(out: Map<String, JsonElement>, id: String) = (out[Practice.GAMES] as JsonObject)[id] as JsonObject

    @Test fun saveWithNothingChangedWritesNothing() {
        for (id in listOf("a", "b", "c")) assertEquals("game $id", emptyMap<String, JsonElement>(), Edit.save(lib, g(id), Edit.open(lib, g(id)), now, none))
    }

    @Test fun saveWritesOnlyWhatChanged() {
        val d = Edit.open(lib, g("a")).set("in:title", JsonPrimitive("  Starfall Lantern Remastered ")).set("state", JsonPrimitive("playing"))
        val f = fields(Edit.save(lib, g("a"), d, now, none), "a")
        assertEquals(JsonPrimitive("Starfall Lantern Remastered"), f["title"])
        assertEquals(JsonPrimitive("playing"), f["state"])
        assertEquals("starting play stamps when (v218)", JsonPrimitive(now), f["plAt"])
        assertEquals(setOf("title", "state", "plAt"), f.keys)
    }

    @Test fun aBlankTitleKeepsTheOldOne() {
        val d = Edit.open(lib, g("a")).set("in:title", JsonPrimitive("   "))
        assertEquals(emptyMap<String, JsonElement>(), Edit.save(lib, g("a"), d, now, none))
    }

    @Test fun theDateAndItsCertaintyMoveTogether() {
        var d = Edit.open(lib, g("a"))
        d = Edit.pickDate(d, "")
        assertEquals("tba", d.str("status"))
        d = Edit.pickDate(d, "2027-02-14")
        assertEquals("a date from tba is confirmed", "confirmed", d.str("status"))
        val (d2, say) = Edit.pickStatus(d, "tba")
        assertEquals("", d2.str("date"))
        assertEquals(Edit.DATE_GONE, say)
        val f = fields(Edit.save(lib, g("a"), d2, now, none), "a")
        assertEquals(JsonPrimitive(""), f["date"])
        assertEquals(JsonPrimitive("tba"), f["status"])
        // rumoured with a date stays rumoured
        val (d3, _) = Edit.pickStatus(Edit.pickDate(d2, "2027-03-01"), "rumoured")
        assertEquals(JsonPrimitive("rumoured"), fields(Edit.save(lib, g("a"), d3, now, none), "a")["status"])
    }

    @Test fun theDateLine() {
        val q = Queue(lib, Rank(lib, now, zone), now, zone)
        assertEquals("No date — shows as TBA", Edit.dateSay(q, ""))
        assertEquals("14 Feb 27 2027", Edit.dateSay(q, "2027-02-14"))
        assertEquals("30 Sept 2026", Edit.dateSay(q, "2026-09-30"))
    }

    @Test fun typedHoursAreYoursAndHowLongToBeatsWhenTheyMatch() {
        val mine = fields(Edit.save(lib, g("a"), Edit.open(lib, g("a")).set("in:hours", JsonPrimitive("25")), now, none), "a")
        assertEquals(JsonPrimitive(25), mine["hours"])
        assertEquals(JsonPrimitive("me"), mine["hrsSrc"])
        val d = Edit.useHl(Edit.open(lib, g("a")), Edit.hl(g("a"))!!)
        assertEquals("20", d.typed("hours", ""))
        val hl = fields(Edit.save(lib, g("a"), d, now, none), "a")
        assertEquals(JsonPrimitive("hltb"), hl["hrsSrc"])
        assertEquals(JsonPrimitive(30), hl["hoursX"])
        assertEquals("the boxes' hints", "≈ 33", Edit.hoursHint(g("a"), "hoursX"))
        assertEquals("9½", Edit.hlFmt(9.4))
        assertEquals("—", Edit.hlFmt(0.0))
    }

    @Test fun platformsToggleAtTheEndAndKeepNamesThePickerDoesntKnow() {
        assertEquals("PS5, Wii, Switch", Edit.toggle("PS5, Wii", "Switch"))
        assertEquals("Wii", Edit.toggle("PS5, Wii", "ps5"))
        assertTrue(Edit.has("PS5, Wii", "wii"))
    }

    @Test fun advancedPartsMoveTheirArea() {
        var d = Edit.open(lib, g("a"))
        d = Edit.setSub(d, "story", "writing", 8)
        d = Edit.setSub(d, "story", "plot", 5)
        assertEquals(6.5, Edit.subAvg(d["subs"] as JsonObject, "story"), 0.0)
        assertEquals(JsonPrimitive(6.5), (d["mine"] as JsonObject)["story"])
        // all parts at 0 leaves the area as it was
        val z = Edit.setSub(Edit.open(lib, g("a")), "story", "writing", 0)
        assertEquals(JsonPrimitive(7), (z["mine"] as JsonObject)["story"])
    }

    @Test fun summaries() {
        val q = Queue(lib, Rank(lib, now, zone), now, zone)
        val s = Edit.sums(q, g("a"), null)
        assertEquals("A lantern that remembers", s["Name"])
        assertEquals("30 Sept · 22h · confirmed", s["Release"])
        assertEquals("Out, not played", s["Where it stands"])
        assertEquals("PS5, Wii", s["Platforms"])
        assertEquals("—/10", s["Hype"])
        assertEquals("None yet", s["Critics"])
        assertEquals("the web's 'of 4' with 8 areas", "1 of 4 rated · 70/100", s["My rating"])
        assertEquals("Placeholder", s["Cover and colour"])
        assertEquals("Auto", s["Backdrop"])
        assertEquals("None", s["Logo / character art"])
        assertEquals("8.5/10", Edit.sums(q, g("b"), lib.played.first())["Played"])
    }

    @Test fun theGroups() {
        assertEquals(6, Edit.groups("game", false).size)
        assertEquals(listOf("Hype", "Critics", "My rating"), Edit.groups("scores", false))
        assertEquals("Played", Edit.groups("scores", true).last())
        assertEquals("no hub figure", listOf("Cover and colour", "Backdrop", "Logo / character art"), Edit.groups("look", true))
    }

    @Test fun dontRememberAndTheScoreGoIntoTheRecord() {
        val rec = { out: Map<String, JsonElement> -> (out[Practice.RECS] as JsonObject)["b"] as JsonObject }
        val dk = rec(Edit.save(lib, g("b"), Edit.open(lib, g("b")).rec("dk", JsonPrimitive(true)), now, none))
        assertEquals(JsonPrimitive(0), dk["real"])
        assertEquals(JsonPrimitive(1), dk["dk"])
        val zero = rec(Edit.save(lib, g("b"), Edit.open(lib, g("b")).rec("real", JsonPrimitive(0)), now, none))
        assertEquals("a real 0 is scored (z)", JsonPrimitive(1), zero["z"])
        val more = rec(Edit.save(lib, g("b"), Edit.open(lib, g("b")).rec("real", JsonPrimitive(9.5)).rec("runs", JsonPrimitive(3)).rec("again", JsonPrimitive(true)).rec("fin", JsonPrimitive("2025-07-15")), now, none))
        assertEquals(JsonPrimitive(9.5), more["real"])
        assertEquals(JsonPrimitive(3), more["runs"])
        assertEquals(JsonPrimitive(true), more["again"])
        assertEquals(JsonPrimitive("2025-07-15"), more["t"])
        assertFalse(more.containsKey("dk"))
    }

    @Test fun choosingPlayedMakesARecordAndLeavingItTakesItAway() {
        val made = Edit.save(lib, g("c"), Edit.open(lib, g("c")).set("state", JsonPrimitive("played")), now, none)
        val r = (made[Practice.RECS] as JsonObject)["c"] as JsonObject
        assertEquals("syncPlayed dates by UTC (3-critic.md C8)", JsonPrimitive("2026-10-27"), r["t"])
        assertFalse("Save keeps when play started (the web copies the state on first, so syncPlayed keeps plAt)", fields(made, "c").containsKey("plAt"))
        val gone = Edit.save(lib, g("b"), Edit.open(lib, g("b")).set("state", JsonPrimitive("dropped")), now, none)
        assertEquals(JsonNull, (gone[Practice.RECS] as JsonObject)["b"])
        assertEquals(JsonPrimitive(now), fields(gone, "b")["dropAt"])
    }

    @Test fun moveBackToTheQueueGivesAStateThatIsntPlayed() {
        val out = Edit.unplay(g("b"), now, none)
        assertEquals(JsonNull, (out[Practice.RECS] as JsonObject)["b"])
        assertEquals("out, not played (X5)", JsonPrimitive("backlog"), fields(out, "b")["state"])
        val soon = Game(JsonObject(g("b").o + ("date" to JsonPrimitive("2027-01-01"))))
        assertEquals(JsonPrimitive("upcoming"), fields(Edit.unplay(soon, now, none), "b")["state"])
    }

    @Test fun competitiveKeepsTheHoursAside() {
        val on = fields(Edit.comp(g("a"), true, none), "a")
        assertEquals(JsonPrimitive(true), on["comp"])
        assertEquals(JsonPrimitive(0), on["hours"])
        assertEquals(JsonPrimitive(22), on["hoursWas"])
        val back = Game(JsonObject(g("a").o + on))
        val off = fields(Edit.comp(back, false, none), "a")
        assertEquals(JsonPrimitive(22), off["hours"])
        assertEquals(JsonNull, off["hoursWas"])
    }

    @Test fun theReviewListsChangesInTheWebsWords() {
        assertEquals(emptyList<Edit.Change>(), Edit.review(lib, g("a"), Edit.open(lib, g("a"))))
        assertEquals("Nothing has changed yet.", Edit.reviewHead(0))
        val d = Edit.open(lib, g("a")).set("in:desc", JsonPrimitive("Something much longer than twenty-eight letters")).set("img", JsonPrimitive("https://example.invalid/a.jpg"))
        val l = Edit.review(lib, g("a"), d)
        assertEquals(listOf("Why you want it", "Cover"), l.map { it.field })
        assertEquals("A lantern that remembers", l[0].was)
        assertEquals("Something much longer than t…", l[0].now)
        assertEquals("a link", l[1].now)
        assertEquals("2 changes waiting:", Edit.reviewHead(2))
    }

    @Test fun links() {
        val st = Edit.link("https://store.steampowered.com/app/1234567/Some_Game/", "cover") as Edit.Link.Use
        assertTrue(st.url.endsWith("steam/apps/1234567/library_600x900_2x.jpg"))
        assertEquals("1234567", st.steamId)
        assertTrue((Edit.link("1234567", "back") as Edit.Link.Use).url.endsWith("library_hero.jpg"))
        assertEquals("https://raw.githubusercontent.com/someone/art/main/a.png", (Edit.link("https://github.com/someone/art/blob/main/a.png", "art") as Edit.Link.Use).url)
        assertEquals("That isn't a web link. It needs to start with http.", (Edit.link("cover.png", "cover") as Edit.Link.Bad).why)
        assertEquals("Nothing to use — paste a link first.", (Edit.link("  ", "cover") as Edit.Link.Bad).why)
        assertEquals(" It's 3840x2160 (4K).", Edit.sizeSay(3840, 2160))
        // a typed link wins on Save; a cleared cover saves none
        val d = Edit.open(lib, g("a")).set("in:img", JsonPrimitive("1234567"))
        assertTrue(Backup.str(fields(Edit.save(lib, g("a"), d, now, none), "a")["img"]).endsWith("library_600x900_2x.jpg"))
    }

    private fun libWith(edit: (String) -> String) = Library(Json.parseToJsonElement(edit(LIB)).jsonObject)

    @Test fun aBackupThatLandsWhileEditIsOpenKeepsTheFieldsYouDidntTouch() {
        val d = Edit.open(lib, g("b")).set("in:title", JsonPrimitive("Copper Meadow Deluxe"))
        val newer = libWith { it.replace("\"hours\":12,\"hype\":8", "\"hours\":12,\"hype\":9") }
        val gb = newer.games.first { it.id == "b" }
        val f = fields(Edit.save(newer, gb, d, now, none), "b")
        assertEquals("the newer hype stays (not the 8 Edit opened with)", setOf("title"), f.keys)
        assertEquals("the review lists only your change", listOf("Title"), Edit.review(newer, gb, d).map { it.field })
    }

    @Test fun theSavedDraftHoldsOnlyYourChanges() {
        val big = libWith { it.replace("\"desc\":\"A lantern that remembers\"", "\"desc\":\"A lantern that remembers\",\"img\":\"data:image/jpeg;base64,${"A".repeat(400_000)}\"") }
        val ga = big.games.first { it.id == "a" }
        val d = Edit.open(big, ga).set("hype", JsonPrimitive(7)).set("status", null)
        val t = d.text()
        assertTrue("a few bytes, not the picture (${t.length})", t.length < 200)
        val back = Edit.Draft.of(t, Edit.open(big, ga))!!
        assertEquals(d.g, back.g)
        assertEquals(Edit.save(big, ga, d, now, none), Edit.save(big, ga, back, now, none))
    }

    @Test fun droppingStampsWhenAndHowFarIn() {
        val l = libWith { it.replace("\"plAt\":1760000000000,\"hours\":40", "\"plAt\":1760000000000,\"hours\":40,\"logged\":10") }
        val f = fields(Edit.save(l, l.games.first { it.id == "c" }, Edit.open(l, l.games.first { it.id == "c" }).set("state", JsonPrimitive("dropped")), now, none), "c")
        assertEquals(JsonPrimitive(now), f["dropAt"])
        assertEquals("10h of 60 (extras: 40 × 1.5)", 0.17, (f["dropP"] as JsonPrimitive).content.toDouble(), 1e-9)
    }

    @Test fun aBackdropLinkTurnsTheBackdropBackOn() {
        val off = libWith { it.replace("\"mine\":{\"story\":7}", "\"mine\":{\"story\":7},\"bgOff\":true") }
        val ga = off.games.first { it.id == "a" }
        val d = Edit.open(off, ga).setAll(Edit.useLink("hero", "back", Edit.Link.Use("https://example.com/wide.jpg", "")))
        val f = fields(Edit.save(off, ga, d, now, none), "a")
        assertEquals(JsonPrimitive("https://example.com/wide.jpg"), f["hero"])
        assertEquals(JsonPrimitive(false), f["bgOff"])
    }

    @Test fun theDropPointUsesTheHoursTypedInTheSameSave() {
        val l = libWith { it.replace("\"plAt\":1760000000000,\"hours\":40", "\"plAt\":1760000000000,\"logged\":10") }
        val gc = l.games.first { it.id == "c" }
        val d = Edit.open(l, gc).set("state", JsonPrimitive("dropped")).set("in:hours", JsonPrimitive("40"))
        assertEquals("10h of 60 (extras: 40 × 1.5)", 0.17, (fields(Edit.save(l, gc, d, now, none), "c")["dropP"] as JsonPrimitive).content.toDouble(), 1e-9)
    }

    @Test fun theFinishMonth() {
        val today = LocalDate.of(2026, 10, 27)
        assertEquals("same month keeps the day", "2025-06-20", Edit.finDate("2025-06-20", 2025, 6, today))
        assertEquals("another month the 15th", "2025-03-15", Edit.finDate("2025-06-20", 2025, 3, today))
        assertEquals("this month from another: the 15th too (finWire)", "2026-10-15", Edit.finDate("2025-06-20", 2026, 10, today))
        assertEquals("2026-10-03", Edit.finDate("2026-10-03", 2026, 10, today))
        val early = LocalDate.of(2026, 10, 10)
        assertEquals("this month at most today", "2026-10-10", Edit.finDate("2025-06-30", 2026, 10, early))
        assertEquals("the same month is capped too", "2026-10-10", Edit.finDate("2026-10-25", 2026, 10, early))
    }

    @Test fun theDraftSurvivesBeingSaved() {
        val d = Edit.open(lib, g("b")).set("in:title", JsonPrimitive("x")).rec("dk", JsonPrimitive(true))
        val back = Edit.Draft.of(d.text(), Edit.open(lib, g("b")))!!
        assertEquals(d.g, back.g)
        assertEquals(d.p, back.p)
        assertNull(Edit.Draft.of("not json", Edit.open(lib, g("b"))))
    }

    @Test fun thePaletteRunsRoundTheWheelWithGreysLast() {
        assertEquals(32, Edit.PALETTE_ROW.size)
        assertEquals(Edit.PALETTE_ROW.toSet(), io.github.shamlawy.playnext.gen.Web.PALETTE.toSet())
        val grey = Edit.PALETTE_ROW.takeLast(2).map { Edit.hex(it) }
        assertTrue("greys last: $grey", grey.all { h -> Edit.okLab(Game.hex(h)!!).let { Math.hypot(it.second, it.third) < .05 } })
    }

    @Test fun whyIsKeptAsAList() {
        val d = Edit.open(lib, g("b")).set("why", JsonArray(listOf(JsonPrimitive("chars"), JsonPrimitive("music"))))
        assertEquals(JsonArray(listOf(JsonPrimitive("chars"), JsonPrimitive("music"))), fields(Edit.save(lib, g("b"), d, now, none), "b")["why"])
    }
}

private val LIB = """
            {"rateMode":"simple","playStyle":"extras",
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","status":"confirmed","ord":1,"hours":22,"platforms":"PS5, Wii","genres":"JRPG",
                "hl":{"m":20,"x":30,"c":50,"cnt":900},"desc":"A lantern that remembers","mine":{"story":7}},
               {"id":"b","title":"Copper Meadow","state":"played","date":"2025-05-11","ord":2,"hours":12,"hype":8,"why":["chars"]},
               {"id":"c","title":"Quiet Harbour","state":"playing","date":"2026-01-02","ord":3,"plAt":1760000000000,"hours":40}
             ],
             "duels":[],"played":[{"id":"b","real":8.5,"t":"2025-06-20","runs":1}]}
            """.trimIndent()
