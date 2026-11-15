package io.github.shamlawy.playnext.ui

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.shamlawy.playnext.Store
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot

/**
 * What's new (android-v41, the owner: "add an update now to tell me what new thing you made and what to test"): after
 * an update the sheet lists every release since the one this phone last showed, each with what to try; never after a
 * first install, once per release.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhatsNewTest {
    @get:Rule
    val rule = createComposeRule()

    @Before fun setUp() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.prefs.edit().clear().commit()
    }

    @After fun tearDown() { Store.prefs.edit().clear().commit() }

    @Test fun onlyAfterAnUpdateAndOncePerRelease() {
        assertNull("a first install has nothing new to tell", News.since(41))
        Store.prefs.edit().putInt("updatedTo", 41).putInt("updatedSeen", 39).commit()
        assertEquals("from the last Updated to card this phone showed", 39, News.since(41))
        News.seen(41)
        assertNull("once per release", News.since(41))
        assertEquals("the next update starts after this one", 41, News.since(43))
        Store.prefs.edit().clear().putInt("updatedTo", 41).commit()
        assertEquals("no record: just this release", 40, News.since(41))
    }

    @Test fun theSheetShowsWhatChangedAndWhatToTry() {
        var closed = false
        kotlinx.coroutines.runBlocking { News.load(ApplicationProvider.getApplicationContext()) }
        rule.setContent { WhatsNewSheet(Modifier, since = 39) { closed = true } }
        rule.onNodeWithText("What's new").assertExists()
        rule.onNodeWithText("v40").assertExists()
        rule.onNodeWithText("v39").assertDoesNotExist()
        rule.onRoot().captureRoboImage("src/test/screenshots/whatsnew-412.png")
        rule.onNodeWithText("Every release").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("v39").performScrollTo()
        rule.onNodeWithText("Got it").performScrollTo().performClick()
        assertTrue(closed)
    }

    @Test fun boldMarksAreDrawnBoldAndOtherMarksDropped() {
        val w = News.words("Tap <b>Check now</b> or <i>this</i>")
        assertEquals("Tap Check now or this", w.text)
        assertEquals(1, w.spanStyles.size)
    }
}
