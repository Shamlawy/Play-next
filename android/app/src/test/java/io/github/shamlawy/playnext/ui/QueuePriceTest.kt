package io.github.shamlawy.playnext.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.data.Prices
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

/** The price on the Queue's closed rows and open card, folded and unfolded (android-v52; made-up games and prices). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class QueuePriceTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-23T08:00:00Z").toEpochMilli()
    private var nav by mutableStateOf(Nav("games", views = mapOf("games" to "queue")))

    private val lib = Library(Json.parseToJsonElement("""
        {"theme":"midnight","bgGrad":30,"deckOff":true,"pxCC":"ae","psPlus":2,"games":[
          {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"platforms":"PS5, Switch 2","hours":22,"hype":8},
          {"id":"b","title":"Hollow Tide","state":"backlog","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":["Adventure"],"platforms":"PS5","hours":40},
          {"id":"c","title":"Glass Road","state":"backlog","date":"2026-05-02","ord":3,"colour":"#3FA46A","genres":["Puzzle"],"platforms":"Switch"},
          {"id":"d","title":"Paper Kingdom","state":"backlog","date":"2025-12-01","ord":4,"colour":"#5AB3C9","genres":["Strategy"],"platforms":"PS5"},
          {"id":"e","title":"A Very Long Named Game of the Ninefold Gate","state":"upcoming","date":"2026-12-14","ord":5,"colour":"#B06AD8","genres":["RPG"],"platforms":"PS5"}
        ],"played":[]}
    """.trimIndent()).jsonObject)

    private val answers = Json.parseToJsonElement("""{
        "a":{"ps":{"id":"EP0001-PPSA00001_00-MADEUPGAME000000","name":"x","cur":"AED","base":26176,"now":2617,"pct":90,"baseF":"261.76 AED","nowF":"26.17 AED","end":${Instant.parse("2026-11-28T00:00:00Z").toEpochMilli()},"p5":1}},
        "b":{"ps":{"id":"EP0001-PPSA00002_00-MADEUPGAME000000","name":"x","cur":"AED","base":26176,"now":26176,"pct":0,"baseF":"261.76 AED","nowF":"261.76 AED","p5":1}},
        "c":{"ns":{"id":"70010000000003","name":"x","url":"","cur":"USD","base":39.99,"now":19.99,"pct":50,"baseF":"$39.99","nowF":"$19.99"}},
        "d":{"ps":{"id":"EP0001-PPSA00004_00-MADEUPGAME000000","name":"x","cur":"AED","base":22436,"now":22436,"pct":0,"baseF":"224.36 AED","nowF":"224.36 AED","plus":2,"p5":1}},
        "e":{"ps":{"id":"EP0001-PPSA00005_00-MADEUPGAME000000","name":"x","cur":"AED","base":29916,"now":29916,"pct":0,"baseF":"299.16 AED","nowF":"299.16 AED","pre":1,"p5":1}}
    }""").jsonObject

    @Before fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        for (g in lib.games) Prices.take(lib, g, answers, now)
    }

    @After fun clean() { Prices.forget() }

    private fun show(width: Int) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${if (width < 600) 915 else 1000}dp-xxhdpi")
        rule.setContent { Frame(Look.of(lib), Prefs.of(lib), now = { now }) { Shell(nav, Games.State.Ready(lib, now - 3_600_000L), go = { nav = it }) } }
        rule.mainClock.advanceTimeBy(2_500)
    }

    private fun shot(width: Int) {
        show(width)
        rule.onAllNodesWithContentDescription("PS Store, AED 26.17, 90% off, was AED 262", useUnmergedTree = true).onFirst().assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/queue-price-$width.png")
    }

    @Test fun rowsAndCard412() = shot(412)
    @Test fun rowsAndCard859() = shot(859)
}
