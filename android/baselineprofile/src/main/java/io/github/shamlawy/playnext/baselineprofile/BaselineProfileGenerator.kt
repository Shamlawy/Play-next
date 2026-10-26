package io.github.shamlawy.playnext.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The journeys the profile covers: a cold start into the 120 Hz test (the app's start, Home's code, the probe's list,
 * pictures and flings), which runs one round on an emulator and ends on "Run again".
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startAndProbe() = rule.collect(
        packageName = PKG,
        includeInStartupProfile = true,
        maxIterations = 5,
        stableIterations = 2,
    ) {
        pressHome()
        startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("playnext://open?nudge=probe")).setPackage(PKG))
        device.wait(Until.hasObject(By.text("Run again")), 60_000)
        device.findObject(By.text("Close"))?.click()
        device.wait(Until.hasObject(By.text("Run the test")), 5_000)
    }

    private companion object {
        const val PKG = "io.github.shamlawy.playnext"
    }
}
