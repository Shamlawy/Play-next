package io.github.shamlawy.playnext.data

import io.github.shamlawy.playnext.gen.Web
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Edit › Look's pictures (Phase 2 piece 2b, android/parity/2-map/2-edit.md §4) as index.html picks them: Auto-pick's
 * sharpest of the top five, the stores filling gaps and winning a backdrop at 1.5x the pixels, Browse's rows and
 * line, a pick on the draft beating the old link on Save, an upload kept out of saved state, the colour read from art
 * (`colourFrom` / `palNear`) and the shop regions. Made-up games and addresses.
 */
class ArtTest {
    private fun arr(s: String): JsonArray = Json.parseToJsonElement(s).jsonArray
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val logos = arr("""[{"url":"https://x/l1.png","width":400,"height":100},{"url":"https://x/l2.png","width":1600,"height":400}]""")
    private val heroes = arr("""[{"url":"https://x/h1.jpg","width":1920,"height":620},{"url":"https://x/h2.jpg","width":1000,"height":300},
        {"url":"https://x/h3.jpg","width":3840,"height":1240},{"url":"https://x/h4.jpg","width":1920,"height":620},{"url":"https://x/h5.jpg","width":1920,"height":620},
        {"url":"https://x/h6.jpg","width":7680,"height":2480}]""")

    @Test fun autoPickTakesTheSharpestOfTheTopFive() {
        val t = Art.sgTop("Starfall Lantern", Art.sg(logos, "art", "Logo"), Art.sg(heroes, "hero", "Hero"), emptyList())
        assertEquals("https://x/l2.png", t.art)
        assertEquals("the sixth isn't looked at", "https://x/h3.jpg", t.hero)
        assertEquals("", t.cover)
        assertEquals(3840L * 1240, t.heroA)
    }

    private val stores = obj("""{"ok":true,"found":{"st":{"id":1,"name":"Starfall Lantern: Deluxe"}},"pics":[
        {"s":"st","r":"hero","u":"https://s/hero.jpg","w":3840,"h":1240,"k":"Library hero"},
        {"s":"xb","r":"hero","u":"https://s/shot.jpg","w":7680,"h":4320,"k":"Screenshot"},
        {"s":"st","r":"cover","u":"https://s/square.jpg","w":1000,"h":1000,"k":"Box"},
        {"s":"ps","r":"cover","u":"https://s/box.jpg","w":600,"h":900,"k":"Portrait"},
        {"s":"ps","r":"art","u":"https://s/logo.png","w":800,"h":300,"k":"Logo"}]}""")

    @Test fun theStoresFillGapsAndWinABackdropOnlyWhenMuchSharper() {
        val sg = Art.Top("Starfall Lantern", "https://x/l.png", "https://x/h.jpg", "", heroA = 3840L * 1240)
        val t = Art.withStores(sg, "Starfall Lantern", stores)!!
        assertEquals("SteamGridDB's logo stays", "https://x/l.png", t.art)
        assertEquals("a box-shaped cover fills the gap, never the square one", "https://s/box.jpg", t.cover)
        assertEquals("same pixels and a screenshot kind: SteamGridDB's backdrop stays", "https://x/h.jpg", t.hero)
        val soft = Art.withStores(sg.copy(heroA = 1920L * 620), "Starfall Lantern", stores)!!
        assertEquals("a clean store backdrop with 1.5x the pixels wins", "https://s/hero.jpg", soft.hero)
        val none = Art.withStores(null, "Starfall Lantern", stores)!!
        assertEquals("nothing on SteamGridDB: named after the store's match", "Starfall Lantern: Deluxe", none.name)
        assertEquals("https://s/logo.png", none.art)
        assertNull("no stores, no SteamGridDB: nothing", Art.withStores(null, "x", obj("""{"err":"x","pics":[]}""")))
    }

    @Test fun autoPickSaysWhatItFilled() {
        assertEquals(Art.NOTHING, Art.autoSay(null).first)
        val (say, got) = Art.autoSay(Art.Top("Copper Meadow", "a", "", "c"))
        assertEquals("Matched “Copper Meadow” and filled 2 of 3. Tap Save to keep them.", say)
        assertEquals(listOf("art", "cover"), got)
        assertEquals("Matched “Copper Meadow” but it has no artwork yet.", Art.autoSay(Art.Top("Copper Meadow", "", "", "")).first)
    }

    @Test fun browseRowsAreSharpestFirstWithBoxShapedCoversFirst() {
        val pics = Art.stores(stores) + Art.sg(heroes, "hero", "Hero")
        val rows = Art.rows(pics)
        assertEquals(listOf("cover", "hero", "art"), rows.map { it.first.first })
        assertEquals("the box-shaped cover leads though it's smaller", listOf("https://s/box.jpg", "https://s/square.jpg"), rows[0].second.map { it.u })
        assertEquals("https://s/shot.jpg", rows[1].second.first().u)
        assertEquals(
            "11 pictures from SteamGridDB, Steam, Xbox and PlayStation. Sharpest first, each with its size and where it's from. Tap one to use it.",
            Art.browseSay(pics, null, false),
        )
        assertEquals(Art.NOTHING + Art.OLD, Art.browseSay(emptyList(), null, true))
        assertEquals("SteamGridDB said 500", Art.browseSay(emptyList(), "SteamGridDB said 500", false))
        assertEquals(listOf("4K", "HD", "", "Low", ""), listOf(Art.tier(3840, 2160), Art.tier(1920, 620), Art.tier(1000, 300), Art.tier(600, 300), Art.tier(0, 0)))
    }

    @Test fun rawgScreenshotsGetASmallCopyForTheirTile() {
        val l = Art.rawgShots(obj("""{"results":[{"image":"https://media.rawg.io/media/screenshots/a.jpg","width":1920,"height":1080},{"image":"https://media.rawg.io/media/screenshots/b.jpg","is_deleted":true}]}"""))
        assertEquals(1, l.size)
        assertEquals("https://media.rawg.io/media/resize/420/-/screenshots/a.jpg", l[0].th)
        assertEquals("a screenshot sets the backdrop", "hero", l[0].role)
    }

    private val lib = Library(Json.parseToJsonElement(
        """{"theme":"midnight","games":[{"id":"a","title":"Starfall Lantern","state":"backlog","img":"https://old/cover.jpg","art":"https://old/logo.png","hero":"https://old/hero.jpg","colour":"#3A6FD8","ord":1}],"duels":[],"played":[]}""",
    ).jsonObject)
    private val g = lib.games.first()

    @Test fun aPickBeatsTheOldLinkOnSave() {
        val d = Edit.open(lib, g).setAll(Art.put("cover", "https://new/cover.jpg") + Art.put("hero", "https://new/hero.jpg") + Art.put("art", "https://new/logo.png"))
        val f = (Edit.save(lib, g, d, Instant.parse("2026-11-12T08:00:00Z").toEpochMilli()) { null }[Practice.GAMES] as JsonObject)["a"] as JsonObject
        assertEquals(JsonPrimitive("https://new/cover.jpg"), f["img"])
        assertEquals(JsonPrimitive("https://new/hero.jpg"), f["hero"])
        assertEquals(JsonPrimitive("https://new/logo.png"), f["art"])
    }

    @Test fun anUploadBeatsTheOldLinkAndStaysOutOfSavedState() {
        val data = "data:image/webp;base64," + "A".repeat(60_000)
        val d = Edit.open(lib, g).setAll(mapOf("img" to JsonPrimitive(data), "imgCleared" to null, "in:img" to JsonPrimitive("")))
        assertEquals(JsonPrimitive(data), Edit.collect(g, d)["img"])
        val t = d.text()
        assertTrue("saved state carries a ticket, not the picture (${t.length})", t.length < 1000)
        assertEquals("the ticket comes back while the app runs", JsonPrimitive(data), Edit.Draft.of(t, Edit.open(lib, g))!!["img"])
        val stale = t.replace(Regex("pn-big:[0-9a-f-]+"), "pn-big:gone-1")
        val back = Edit.Draft.of(stale, Edit.open(lib, g))!!
        assertEquals("an upload from a closed app is dropped: the cover stays", JsonPrimitive("https://old/cover.jpg"), back["img"])
        assertEquals("its box is still emptied, so the old link keeps", "https://old/cover.jpg", Backup.str(Edit.collect(g, back)["img"]))
    }

    @Test fun theColourComesFromTheArtsMainHue() {
        // a mostly red picture with a few vivid blue specks: red wins (v294, not the most vivid pixel)
        val w = 48
        val h = 64
        val px = IntArray(w * h) { i -> if (i % 37 == 0) 0xFF0040FF.toInt() else 0xFFC8201E.toInt() }
        val c = Art.colourFrom(px, w, h)!!
        val lab = Edit.okLab(Game.hex(c)!!)
        assertTrue("$c is a swatch of the palette", c in Web.PALETTE.map { Edit.hex(it) })
        assertTrue("$c leans red ($lab)", lab.second > .08 && lab.second > Math.abs(lab.third))
        assertEquals("grey art gets steel", Art.STEEL, Art.colourFrom(IntArray(w * h) { 0xFF808080.toInt() }, w, h))
        assertNull("see-through art: nothing to read", Art.colourFrom(IntArray(w * h) { 0x00FF0000 }, w, h))
    }

    @Test fun theSparkSwatchFollowsTheArt() {
        val o = g.o
        assertTrue("the first swatch counts as from the art", Art.colourAuto(o + ("colour" to JsonPrimitive(Edit.hex(Web.PALETTE[0])))))
        assertFalse("a hand pick never is", Art.colourAuto(o + ("colourHand" to JsonPrimitive(true)) + ("colourOf" to JsonPrimitive("x"))))
        assertTrue(Art.colourAuto(o + ("colourOf" to JsonPrimitive("12:abc"))))
        assertFalse(Art.colourAuto(o + ("colour" to JsonPrimitive("#3A6FD9"))))
        val d = Edit.open(lib, g).set("in:img", JsonPrimitive(" https://typed/cover.jpg "))
        assertEquals("the cover the form has now", "https://typed/cover.jpg", Art.autoSrc(g, d))
        assertEquals("", Art.autoSrc(g, d.set("imgCleared", JsonPrimitive(true))))
        val src = "https://typed/cover.jpg"
        assertEquals("${src.length}:$src$src", Art.colKey(src))
        val m: Map<String, JsonElement?> = Art.autoColour(src, null)
        assertEquals(JsonPrimitive(Edit.hex(Web.PALETTE[0])), m["colour"])
        assertEquals(JsonPrimitive(""), m["colourOf"])
    }

    @Test fun theShopRegionsAreTheWebsOnes() {
        val none = Library(Json.parseToJsonElement("""{"games":[]}""").jsonObject)
        assertEquals("ae", Art.cc(none, "Asia/Dubai", "US"))
        assertEquals("de", Art.cc(none, "Europe/Berlin", "DE"))
        assertEquals("us", Art.cc(none, "Europe/Berlin", ""))
        val picked = Library(Json.parseToJsonElement("""{"games":[],"pxCC":"gb","pxPsCC":"eg"}""").jsonObject)
        assertEquals("gb", Art.cc(picked, "Asia/Dubai", "AE"))
        assertEquals("a region with no PlayStation Store isn't used", "gb", Art.psCC(picked, "gb"))
        assertEquals("ae", Art.psCC(none, "eg"))
        assertEquals("us", Art.psCC(none, "pk"))
    }

    @Test fun theSteamIdIsTheDraftsWhileItsTitleIsTheSame() {
        val d = Edit.open(lib, g).set("steamId", JsonPrimitive("620"))
        assertEquals(620L, Art.steamId(d, "Starfall  Lantern!"))
        assertEquals(0L, Art.steamId(d, "Copper Meadow"))
    }
}
