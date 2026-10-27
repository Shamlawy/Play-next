package io.github.shamlawy.playnext.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `shName`'s answers for what phones share (made-up games; the repo is public). */
class SharedNameTest {
    @Test fun storeLinksNameTheGameByTheirAddress() {
        assertEquals(Shared("Lantern Hollow", 123450), SharedName.of(null, "Check this out https://store.steampowered.com/app/123450/Lantern_Hollow/"))
        assertEquals(Shared("paper kingdom"), SharedName.of("", "https://rawg.io/games/paper-kingdom"))
        assertEquals(Shared("copper meadow"), SharedName.of(null, "https://www.nintendo.com/us/store/products/copper-meadow-switch/"))
    }

    @Test fun wordsLoseLinksTrailersAndTags() {
        assertEquals(Shared("Starfall Lantern"), SharedName.of("Starfall Lantern - Official Launch Trailer | PS5 Games", "https://youtu.be/x"))
        assertEquals(Shared("Quiet Orbit"), SharedName.of(null, "Quiet Orbit on Steam #indie @studio https://example.com/q"))
        // a Steam link without a name in its address keeps its id with the words
        assertEquals(Shared("Ninefold Gate", 777), SharedName.of("Ninefold Gate", "https://store.steampowered.com/app/777"))
    }

    @Test fun nothingToNameIsNothing() {
        assertNull(SharedName.of(null, "https://example.com/only-a-link"))
        assertNull(SharedName.of(null, null))
    }
}
