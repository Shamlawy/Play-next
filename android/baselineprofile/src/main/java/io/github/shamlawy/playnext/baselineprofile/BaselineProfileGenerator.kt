package io.github.shamlawy.playnext.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the phone should have compiled before first use: a cold start to Home, then the 120 Hz test's picture list
 * flinging (the path every list screen will share). New screens add their own walk here as they arrive.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startAndFling() = rule.collect(
        packageName = PKG,
        maxIterations = 4,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("playnext://open?nudge=probe")).setPackage(PKG))
        // the warm-up (1.5 s) and most of one fling: enough to touch the list, picture and loop code
        Thread.sleep(8_000)
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PKG = "io.github.shamlawy.playnext"
    }
}
