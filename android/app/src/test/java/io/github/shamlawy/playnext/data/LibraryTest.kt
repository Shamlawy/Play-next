package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The Queue placeholder's groups (worked out once per backup) and a game's colour. Made-up games only. */
class LibraryTest {
    private val lib = Library(
        Json.parseToJsonElement(
            """{"games":[
              {"id":"b","title":"Hollow Tide","state":"backlog","ord":2},
              {"id":"a","title":"Starfall Lantern","state":"backlog","ord":1},
              {"id":"c","title":"Copper Meadow","state":"playing","ord":3},
              {"id":"d","title":"Ninefold Gate","ord":4}
            ]}""",
        ).jsonObject,
    )

    @Test fun groupsByStateInYourOrderOnce() {
        val groups = lib.byState
        assertEquals(listOf("playing", "backlog", "upcoming").sorted(), groups.map { it.state }.sorted())
        assertEquals(listOf("a", "b"), groups.first { it.state == "backlog" }.games.map { it.id })
        assertEquals("a missing state counts as upcoming", listOf("d"), groups.first { it.state == "upcoming" }.games.map { it.id })
        assertSame("worked out once, not per visit", groups, lib.byState)
    }

    @Test fun colours() {
        assertEquals(0xFF3A6FD8L, Game.hex("#3A6FD8"))
        assertNull(Game.hex("3A6FD8"))
        assertNull(Game.hex("#3A6FD"))
    }
}
