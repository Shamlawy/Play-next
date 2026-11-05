package io.github.shamlawy.playnext.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gate 3's second half: main's build against the PR's on one CI emulator (android-bench.yml runs this class on both, in
 * turns A/B/A/B, and fails only on a clear slowdown). Made-up backup only (the Demo switch, as the profile walk).
 * Cold start to Home, and the Queue scrolled with flings (frame times). `-e pn.iter N` sets the iterations (default 5).
 */
@RunWith(AndroidJUnit4::class)
class SpeedBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val iterations = InstrumentationRegistry.getArguments().getString("pn.iter")?.toIntOrNull() ?: 5

    @Before
    fun demo() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("settings put global playnext_demo 1")
    }

    @Test
    fun startup() = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(StartupTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Games")), 10_000)
    }

    @Test
    fun queueScroll() = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(FrameTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.desc("Games")), 10_000)
            device.findObject(By.desc("Games"))?.click()
            device.waitForIdle()
            Thread.sleep(1_200)
        },
    ) {
        repeat(2) { fling(up = true) }
        repeat(2) { fling(up = false) }
    }

    /** One quick finger up (or down) the middle of the screen, then the list's own fling to its end. */
    private fun MacrobenchmarkScope.fling(up: Boolean) {
        val x = device.displayWidth / 2
        val h = device.displayHeight
        if (up) device.swipe(x, h * 78 / 100, x, h * 25 / 100, 8) else device.swipe(x, h * 25 / 100, x, h * 78 / 100, 8)
        Thread.sleep(1_100)
    }

    private companion object {
        const val PKG = "io.github.shamlawy.playnext"
    }
}
