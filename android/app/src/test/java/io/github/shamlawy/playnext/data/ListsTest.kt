package io.github.shamlawy.playnext.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Your lists' maths (index.html 12860-12870, `listNew` / rename / `listDrop`), on made-up lists. */
class ListsTest {
    private val two = listOf(Queue.QList("l1", "Couch nights", "🛋️"), Queue.QList("l2", "Short ones", "⚡"))

    @Test fun aNewListIsTrimmedCutAndNamedWhenEmpty() {
        val l = Lists.made(two, "  Co-op with a friend  ", "🤝", "lx").last()
        assertEquals(Queue.QList("lx", "Co-op with a friend", "🤝"), l)
        assertEquals("at most 32 characters", 32, Lists.made(two, "x".repeat(40), "⭐", "ly").last().n.length)
        assertEquals("My list", Lists.made(two, "   ", "", "lz").last().n)
        assertEquals("⭐", Lists.made(two, "A", "", "lz").last().e)
        assertEquals("it goes at the end", listOf("l1", "l2", "lx"), Lists.made(two, "Co-op", "🤝", "lx").map { it.id })
    }

    @Test fun aBlankRenameChangesNothing() {
        assertSame(two, Lists.renamed(two, "l1", "   "))
        assertEquals("Late nights", Lists.renamed(two, "l1", " Late nights ").first().n)
        assertEquals("the other list stays", two[1], Lists.renamed(two, "l1", "Late nights")[1])
    }

    @Test fun deletingTakesOnlyThatList() {
        assertEquals(listOf(two[1]), Lists.dropped(two, "l1"))
        assertEquals(two, Lists.dropped(two, "nope"))
    }

    @Test fun theEmojiGoesRoundInTurn() {
        assertEquals("⭐", Lists.nextEmo(emptyList()))
        assertEquals("🤝", Lists.nextEmo(two))
        assertEquals("⭐", Lists.nextEmo(List(12) { Queue.QList("l$it", "n", "⭐") }))
    }

    @Test fun anIdLooksLikeTheWebs() {
        val id = Lists.newId(1_793_000_000_000L, Random(1))
        assertTrue(id, Regex("l[0-9a-z]{8}[0-9a-z]{3}").matches(id))
        assertEquals("l" + 1_793_000_000_000L.toString(36), id.dropLast(3))
    }

    @Test fun theJsonReadsBackTheSame() {
        val lib = Library(JsonObject(mapOf("games" to Json.parseToJsonElement("[]"), "lists" to Lists.json(two))))
        assertEquals(two, Lists.of(lib))
    }

    @Test fun countsTheGamesInAList() {
        val lib = Library(
            Json.parseToJsonElement(
                """{"games":[{"id":"a","title":"A","lists":["l1","l2"]},{"id":"b","title":"B","lists":["l1"]},{"id":"c","title":"C"}]}""",
            ).jsonObject,
        )
        assertEquals(2, Lists.count(lib, "l1"))
        assertEquals(1, Lists.count(lib, "l2"))
        assertEquals("1 game · show in queue", Lists.countLine(1))
        assertEquals("2 games · show in queue", Lists.countLine(2))
    }
}
