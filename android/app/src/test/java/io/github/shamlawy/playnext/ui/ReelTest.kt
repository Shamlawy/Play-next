package io.github.shamlawy.playnext.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import io.github.shamlawy.playnext.R
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Library
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import kotlin.math.abs

/**
 * The opening reel (Phase 3 piece 7, android-v53): a cold launch waits on the glowing pixel with "Tap to play" and Skip,
 * a tap plays the 15 s reel, a tap while it plays, Skip or back ends it, and it leaves by itself at the end; Off (this
 * phone's choice or the backup's `reelOff`) and calm show nothing, and it shows once a run. Every frame draws, the
 * first and last are the pixel, and the covers come in the web's order. Made-up games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun lib(extra: String = "") = Library(
        Json.parseToJsonElement(
            """
            {"theme":"midnight","lists":[],"sort":"manual"$extra,"duels":[],"played":[],"games":[
               {"id":"a","title":"Starfall Lantern","state":"backlog","ord":2,"img":"https://example.com/a.jpg","colour":"#7A3CFF"},
               {"id":"b","title":"Copper Meadow","state":"playing","hours":12,"img":"https://example.com/b.jpg","colour":"#E0662B"},
               {"id":"c","title":"Moth Bell","state":"backlog","ord":1,"img":"https://example.com/c.jpg"},
               {"id":"d","title":"Drawn One","state":"backlog","ord":0,"img":"data:image/svg+xml,x"},
               {"id":"e","title":"No Picture","state":"backlog","ord":3},
               {"id":"f","title":"Glass Tide","state":"played","img":"https://example.com/f.jpg"}
            ]}
            """.trimIndent(),
        ).jsonObject,
    )

    @Before
    fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.prefs.edit().clear().commit()
        Reel.reset()
    }

    @After
    fun tearDown() = Reel.reset()

    private fun show(l: Library = lib(), calm: Boolean = false) {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-xxhdpi")
        Reel.arm()
        val st = Games.State.Ready(l, 0L)
        rule.setContent { Frame(Look.of(l), Prefs.of(l)) { OpeningReel(st, Prefs.of(l), calm) } }
        rule.waitForIdle()
    }

    @Test fun itWaitsOnThePixelThenATapPlaysItToTheEnd() {
        show()
        rule.onNodeWithContentDescription("Play the opening reel").assertExists()
        rule.onNodeWithText("Tap to play", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Skip", useUnmergedTree = true).assertExists()
        rule.mainClock.advanceTimeBy(5_000)
        assertTrue("it waits for the tap", Reel.up)
        // the reel's clock runs every frame: held, so a wait for idle doesn't run it to its end
        rule.mainClock.autoAdvance = false
        rule.onRoot().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("The opening reel").assertExists()
        rule.onNodeWithText("Tap to play", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(Reel.up)
        rule.mainClock.advanceTimeBy(13_000)
        assertTrue("still playing at 14 s", Reel.up)
        rule.mainClock.advanceTimeBy(1_500)
        assertFalse("it leaves by itself after 15 s", Reel.up)
    }

    @Test fun aTapWhileItPlaysEndsIt() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        assertTrue("playing", Reel.up)
        rule.onNodeWithContentDescription("The opening reel").assertExists()
        rule.onRoot().performClick()
        rule.mainClock.advanceTimeBy(400)
        assertFalse(Reel.up)
    }

    @Test fun skipEndsItBeforeItPlays() {
        show()
        rule.onNodeWithText("Skip", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        assertFalse(Reel.up)
    }

    @Test fun backEndsIt() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performClick()
        rule.mainClock.advanceTimeBy(500)
        assertTrue("playing", Reel.up)
        Espresso.pressBack()
        rule.mainClock.advanceTimeBy(400)
        assertFalse(Reel.up)
    }

    @Test fun offOnThisPhoneShowsNothing() {
        Reel.pick(true)
        show()
        rule.onNodeWithContentDescription("Play the opening reel").assertDoesNotExist()
        assertFalse(Reel.up)
        assertEquals("kept on the phone", true, Store.prefs.getBoolean("reelOff", false))
    }

    @Test fun offInTheBackupShowsNothingUntilThisPhonePicks() {
        show(lib(""","reelOff":true"""))
        rule.onNodeWithContentDescription("Play the opening reel").assertDoesNotExist()
        assertFalse(Reel.up)
        assertTrue(Reel.off(Prefs.of(lib(""","reelOff":true"""))))
        Reel.pick(false)
        assertFalse("Every launch here wins over the backup", Reel.off(Prefs.of(lib(""","reelOff":true"""))))
    }

    @Test fun calmShowsNothing() {
        show(calm = true)
        rule.onNodeWithContentDescription("Play the opening reel").assertDoesNotExist()
        assertFalse(Reel.up)
    }

    @Test fun itShowsOnceARun() {
        Reel.arm()
        Reel.gone()
        Reel.arm()
        assertFalse("a second launch in the same run (a fold, a return) plays nothing", Reel.up)
    }

    @Test fun theCoversArePlayingNowThenTheQueueThenTheRest() {
        val l = lib()
        val picked = Reel.pick9(l, 0L, ZoneId.of("UTC")).map { it.id }
        // playing first, then the waiting games that are out in your order, then any other with a real picture
        assertEquals(listOf("b", "c", "a", "f"), picked)
    }

    @Test fun everyFrameDrawsAndItStartsAndEndsOnThePixel() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val face = runCatching { ResourcesCompat.getFont(ctx, R.font.poppins_700) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val w = 412
        val h = 915
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val shots = System.getenv("REEL_SHOTS")?.let { File(it).apply { mkdirs() } }
        val art = ReelDraw.Art(List(9) { i -> if (i % 2 == 0) Bitmap.createBitmap(120, 156, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(200, 60 + i * 20, 90)) } else null }, "Starfall Lantern", 12.0)
        var t = 0f
        while (t < ReelDraw.LEN) {
            ReelDraw.frame(Canvas(bm), t, w.toFloat(), h.toFloat(), face, if (t > 5f) art else null)
            var lit = 0
            for (y in 0 until h step 8) for (x in 0 until w step 8) if (bm.getPixel(x, y) != android.graphics.Color.BLACK) lit++
            assertTrue("something is drawn at $t s", lit > 0)
            shots?.let { d -> File(d, "a-%05.2f.png".format(t)).outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            t += .25f
        }
        for (end in listOf(0f, 14.99f)) {
            ReelDraw.frame(Canvas(bm), end, w.toFloat(), h.toFloat(), face)
            val c = bm.getPixel(w / 2, h / 2)
            assertTrue("the pixel is blue at $end s", abs(android.graphics.Color.red(c) - 77) < 12 && abs(android.graphics.Color.blue(c) - 255) < 12)
            assertEquals("and only the pixel", android.graphics.Color.BLACK, bm.getPixel(w / 2 + 40, h / 2))
        }
    }
}
