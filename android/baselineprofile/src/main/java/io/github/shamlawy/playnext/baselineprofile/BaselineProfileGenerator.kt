package io.github.shamlawy.playnext.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * What the phone should have compiled before first use, made on CI's emulator with the app's made-up backup (Demo: the
 * global setting `playnext_demo`, which only the shell can set and only an emulator reads): a cold start to Home (the
 * startup profile), then the walk every screen shares (every tab, scrolling, a one-finger view swipe, a game page from a
 * header card cover, Settings) and the 120 Hz test's picture list flinging. The release smoke test walks the same way
 * (tools/android-smoke.sh) and fails when a screen doesn't show; here a step that finds nothing is skipped, so a
 * profile still comes.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun demo() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("settings put global playnext_demo 1")
    }

    @Test
    fun start() = rule.collect(packageName = PKG, maxIterations = 4, stableIterations = 2, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Games")), 10_000)
    }

    @Test
    fun walk() = rule.collect(packageName = PKG, maxIterations = 4, stableIterations = 2) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Games")), 10_000)
        scroll()
        // unfolded, the rail names Add "Add a game"
        for (t in listOf("Games", "Plan", "Taste", "Add( a game)?", "Home")) tap(By.desc(Pattern.compile(t)))
        tap(By.desc("Games"))
        scroll()
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w * 85 / 100, h * 70 / 100, w * 15 / 100, h * 70 / 100, 12)
        settle()
        tap(By.desc("Games"))
        if (tap(By.desc(COVER))) {
            device.wait(Until.hasObject(By.desc("Back")), 5_000)
            settle()
            scroll(back = false)
            device.pressBack()
            settle()
        }
        if (tap(By.desc("Settings"))) {
            scroll(back = false)
            device.pressBack()
            settle()
        }
        startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("playnext://open?nudge=probe")).setPackage(PKG))
        // the warm-up (1.5 s) and most of one fling: enough to touch the list, picture and loop code
        Thread.sleep(8_000)
        device.pressBack()
        device.waitForIdle()
    }

    /** Taps what [sel] finds once it shows; false when nothing does within 5 s. */
    private fun MacrobenchmarkScope.tap(sel: BySelector): Boolean {
        val o = device.wait(Until.findObject(sel), 5_000) ?: return false
        o.click()
        settle()
        return true
    }

    /** A finger up the middle of the screen, and [back] down again (not on a sheet or page: a pull past its top closes it). */
    private fun MacrobenchmarkScope.scroll(back: Boolean = true) {
        val x = device.displayWidth / 2
        val h = device.displayHeight
        device.swipe(x, h * 75 / 100, x, h * 30 / 100, 15)
        settle()
        if (!back) return
        device.swipe(x, h * 35 / 100, x, h * 80 / 100, 15)
        settle()
    }

    /** The slash, a page's growth, a sheet's slide: done well inside this. */
    private fun settle() = Thread.sleep(900)

    private companion object {
        const val PKG = "io.github.shamlawy.playnext"

        /** A header card cover of the made-up backup (the app's Demo.kt names them). */
        val COVER: Pattern = Pattern.compile("(Starfall|Hollow|Copper|Ninefold|Quiet|Paper|Lumen|Ember|Glass|Velvet|Iron|Moss) [A-Za-z0-9 ]+")
    }
}
