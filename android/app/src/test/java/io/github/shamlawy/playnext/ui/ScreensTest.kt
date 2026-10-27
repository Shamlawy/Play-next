package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import io.github.shamlawy.playnext.update.Updater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
 * The screens as pictures (Robolectric draws them, no emulator): restore, Home, Games, Settings and Home without a
 * code, folded (412) and unfolded (859). The approved pictures live in src/test/screenshots; android-check fails when
 * one changes (verifyRoborazziDebug), and `./gradlew :app:recordRoborazziDebug` writes new ones after a meant change.
 * Made-up games only: the repo is public. The clock is fixed, so "as of" and "in N days" never move.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// a plain Application: PlayNextApp starts the backup check and WorkManager, which a picture doesn't need
@Config(application = Application::class, sdk = [36])
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        // "as of" is in the phone's zone: the same picture on every machine
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private val lib = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","bgGrad":30,
             "games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","date":"2026-09-30","ord":1,"colour":"#3A6FD8"},
               {"id":"b","title":"Hollow Tide: The Long Way Down to the Sunken Archive","state":"backlog","date":"2026-08-02","ord":2,"colour":"#C2413A"},
               {"id":"c","title":"Copper Meadow","state":"playing","date":"2026-05-11","ord":3,"colour":"#2E9E6A"},
               {"id":"d","title":"Ninefold Gate","state":"upcoming","date":"2026-11-14","ord":4,"colour":"#B06AD8"},
               {"id":"e","title":"Quiet Orbit","state":"upcoming","date":"","ord":5,"colour":"#D89A3A"},
               {"id":"f","title":"Paper Kingdom","state":"played","date":"2025-12-01","ord":6,"colour":"#5AB3C9"}
             ],"duels":[],"played":[]}
            """.trimIndent(),
        ).jsonObject,
    )
    private val ready = Games.State.Ready(lib, copyAt = now - 3 * 3_600_000L)

    /** [yours]: your theme and background from the backup, else the app's own (no code yet). */
    private fun shot(name: String, width: Int, yours: Boolean = true, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${if (width < 600) 915 else 1000}dp-xxhdpi")
        rule.setContent { Frame(Look.of(lib.takeIf { yours }), now = { now }) { content() } }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name-$width.png")
    }

    @Test fun restore412() = shot("restore", 412, yours = false) { RestoreScreen(onSkip = {}, onDone = {}) }
    @Test fun restore859() = shot("restore", 859, yours = false) { RestoreScreen(onSkip = {}, onDone = {}) }
    @Test fun home412() = shot("home", 412) { Shell(Nav("home"), ready, go = {}) }
    @Test fun home859() = shot("home", 859) { Shell(Nav("home"), ready, go = {}) }
    @Test fun games412() = shot("games", 412) { Shell(Nav("games"), ready, go = {}) }
    @Test fun games859() = shot("games", 859) { Shell(Nav("games"), ready, go = {}) }
    @Test fun settings412() = shot("settings", 412) { Shell(Nav("home", overlay = "settings"), ready, go = {}) }
    @Test fun settings859() = shot("settings", 859) { Shell(Nav("home", overlay = "settings"), ready, go = {}) }
    /** After Check now found nothing newer: the card says when it looked (the owner on v5: the tap seemed to do nothing). */
    @Test fun checked412() {
        Store.prefs.edit().putLong("updChecked", now - 20 * 60_000L).commit()
        Updater.set(Updater.State.UpToDate)
        try {
            shot("settings-checked", 412) { Shell(Nav("home", overlay = "settings"), ready, go = {}) }
        } finally {
            Updater.set(Updater.State.Idle)
        }
    }

    @Test fun noCode412() = shot("no-code", 412, yours = false) { Shell(Nav("home"), Games.State.NoCode, go = {}) }
    @Test fun noCode859() = shot("no-code", 859, yours = false) { Shell(Nav("home"), Games.State.NoCode, go = {}) }
}
