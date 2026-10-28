package io.github.shamlawy.playnext.ui

import androidx.compose.runtime.saveable.SaverScope
import io.github.shamlawy.playnext.data.Library
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared kit's settings as the web app saves them (absent, false, true, odd values) and the views per tab. */
class KitTest {
    private fun lib(s: String) = Library(Json.parseToJsonElement("""{"games":[],"played":[],"duels":[]$s}""").jsonObject)

    @Test fun defaultsWhenNothingWasSet() {
        val p = Prefs.of(lib(""))
        assertEquals(TabFade.Slash, p.tabFade)
        assertTrue(p.pill)
        assertFalse(p.motionOff)
        assertFalse(p.shxOff)
        assertEquals(Prefs.SHX_FULL, p.shx)
        assertEquals("queue", p.viewOf("games", emptyMap()))
        assertEquals("month", p.viewOf("plan", emptyMap()))
        assertEquals("insights", p.viewOf("taste", emptyMap()))
        assertEquals(null, p.viewOf("home", emptyMap()))
        assertEquals(listOf("insights", "duels", "friend", "book"), p.views("taste").map { it.first })
    }

    /** Sounds is off unless switched on; Nexi blips unless muted (now or by the older switch); buzz and page motion on. */
    @Test fun soundBuzzAndPageMotionAsSaved() {
        val none = Prefs.of(lib(""))
        assertFalse(none.sound)
        assertTrue(none.nexiVoice)
        assertTrue(none.buzz)
        assertTrue(none.hubAnim)
        assertTrue(Prefs.of(lib(""","sound":true""")).sound)
        assertFalse(Prefs.of(lib(""","pnVoice":"mute"""")).nexiVoice)
        assertFalse("the older switch, while pnVoice was never set", Prefs.of(lib(""","pnMute":true""")).nexiVoice)
        assertTrue("pnVoice wins over the older switch", Prefs.of(lib(""","pnVoice":"blip","pnMute":true""")).nexiVoice)
        val off = Prefs.of(lib(""","buzz":false,"hubAnim":false"""))
        assertFalse(off.buzz)
        assertFalse(off.hubAnim)
    }

    @Test fun tabFadeReadsLikeApplyLook() {
        assertEquals(TabFade.Fade, Prefs.of(lib(""","tabFade":true""")).tabFade)
        assertEquals(TabFade.Slash, Prefs.of(lib(""","tabFade":"slash"""")).tabFade)
        assertEquals(TabFade.Instant, Prefs.of(lib(""","tabFade":false""")).tabFade)
        // only true fades: a string "true" is neither the fade nor the slash
        assertEquals(TabFade.Instant, Prefs.of(lib(""","tabFade":"true"""")).tabFade)
    }

    @Test fun switchesAreOffOnlyWhenSavedFalse() {
        val p = Prefs.of(lib(""","motion":false,"pill":false,"batOff":true,"shxOff":1,"timerOn":true"""))
        assertTrue(p.motionOff)
        assertFalse(p.pill)
        assertTrue(p.batOff)
        assertTrue(p.shxOff)
        assertFalse(p.shxOn("fan"))
        // a null or 0 isn't S.x === false
        assertFalse(Prefs.of(lib(""","motion":null,"pill":0""")).motionOff)
        assertTrue(Prefs.of(lib(""","pill":0""")).pill)
    }

    @Test fun headerCardExtras() {
        val p = Prefs.of(lib(""","shxX":["fan","bar"]"""))
        assertTrue(p.shxOn("fan"))
        assertTrue(p.shxOn("bar"))
        assertFalse(p.shxOn("line"))
        assertEquals(emptyList<String>(), Prefs.of(lib(""","shxX":[]""")).shx)
    }

    @Test fun timerOnlyWhenSwitchedOn() {
        assertEquals(listOf("month", "flow", "plan"), Prefs.of(lib("")).views("plan").map { it.first })
        assertEquals(listOf("month", "flow", "plan", "timer"), Prefs.of(lib(""","timerOn":true""")).views("plan").map { it.first })
        // the web app was on Timer, but it's off now: the tab's default
        assertEquals("month", Prefs.of(lib(""","calView":"timer"""")).viewOf("plan", emptyMap()))
        assertEquals("timer", Prefs.of(lib(""","calView":"timer","timerOn":true""")).viewOf("plan", emptyMap()))
    }

    @Test fun savedViewsThenYoursWin() {
        val p = Prefs.of(lib(""","gamesView":"played","tasteView":"psych""""))
        assertEquals("played", p.viewOf("games", emptyMap()))
        // Psychology was folded into Insights (v205): an old saved view falls back to the default
        assertEquals("insights", p.viewOf("taste", emptyMap()))
        assertEquals("lib", p.viewOf("games", mapOf("games" to "lib")))
        assertEquals("played", p.viewOf("games", mapOf("games" to "nonsense")))
    }

    @Test fun navKeepsViewsAcrossTabsAndBack() {
        val n = Nav().go("games").view("games", "replay").go("plan").open("settings").back().go("games")
        assertEquals("games", n.tab)
        assertEquals("replay", n.views["games"])
    }

    @Test fun navSurvivesBeingSaved() {
        val n = Nav().go("taste").view("taste", "duels").view("games", "lib").open("settings").open("game:a").open("game:b")
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val back = Nav.Saver.restore(with(Nav.Saver) { scope.save(n) }!!)
        assertEquals(n, back)
        val deep = Nav("games", listOf("game:a", "game:b"), dropped = 7)
        assertEquals(deep, Nav.Saver.restore(with(Nav.Saver) { scope.save(deep) }!!))
        // what android-v8 and older saved: [tab, overlay, views, …tabs you came through]
        assertEquals(Nav("taste", listOf("settings"), mapOf("taste" to "duels")), Nav.Saver.restore(listOf("taste", "settings", "taste=duels", "home", "games")))
        assertEquals(Nav("plan"), Nav.Saver.restore(listOf("plan", "")))
    }
}
