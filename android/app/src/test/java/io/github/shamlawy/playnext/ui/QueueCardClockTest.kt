package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.kit.Sfx
import io.github.shamlawy.playnext.kit.Sounds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/**
 * The tear's sound follows the card's own clock (android-v36, the owner: "The sound doesnt catch the cut animation"):
 * with the phone's animations at half speed (Compose's MotionDurationScale 2) the stub rips 2.8 s in, so its sound must
 * too. A fixed 1.4 s timer would pass QueueCardTest's same-speed check but fail here. Made-up games only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class QueueCardClockTest {
    @get:Rule
    val rule = createComposeRule(effectContext = object : MotionDurationScale { override val scaleFactor = 2f })

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private val lib = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,"deckOff":true,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8","genres":["Action","RPG"],"hours":22,"added":"2026-10-20"},
               {"id":"b","title":"Hollow Tide","state":"playing","date":"2026-08-02","ord":2,"colour":"#C2413A","genres":"Adventure, RPG","hours":40,"logged":12,"added":"2026-09-02"}
             ]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Test fun atHalfSpeedTheTearSoundsWhenTheStubRips() {
        val heard = mutableListOf<Pair<Sfx, Long>>()
        Sounds.heard = { heard += it to rule.mainClock.currentTime }
        try {
            RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
            rule.setContent {
                Frame(Look.of(lib), Prefs.of(lib), now = { now }) {
                    Shell(Nav("games", views = mapOf("games" to "queue")), Games.State.Ready(lib, now - 3_600_000L), go = {})
                }
            }
            rule.mainClock.advanceTimeBy(6_000)
            // scroll to the row BEFORE pausing the clock (L263: a scroll with the clock paused hung Gradle)
            val m = hasText("Hollow Tide", substring = true) and hasText("28 h left", substring = true) and hasClickAction()
            rule.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(m)
            val r = rule.onAllNodes(m).onLast().also { it.performScrollTo() }
            heard.clear()
            rule.mainClock.autoAdvance = false
            r.performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .12f, height * .3f)) }
            Snapshot.sendApplyNotifications()
            repeat(260) { rule.mainClock.advanceTimeByFrame() }
            rule.mainClock.autoAdvance = true
            val opens = heard.filter { it.first == Sfx.CardOpen }
            assertEquals("one open sound: $heard", 1, opens.size)
            val tear = heard.filter { it.first == Sfx.CardTear }
            assertEquals("one tear sound: $heard", 1, tear.size)
            val at = tear[0].second - opens[0].second
            assertTrue("at half speed the tear sounds 2.8 s into the opening, not 1.4 s: $at ms", at in 2_780L..2_850L)
        } finally { Sounds.heard = null }
    }
}
