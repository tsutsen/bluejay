package com.tsutsen.platformplayer.benchmark

import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluejayBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {}

    @Test
    fun feedScrollJank() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            startActivityAndWait(Intent())
            device.waitForIdle()
        },
    ) {
        val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        repeat(3) {
            device.swipe(630, 2000, 630, 600, 30)
            device.waitForIdle()
            device.swipe(630, 600, 630, 2000, 30)
            device.waitForIdle()
        }
    }

    @Test
    fun morphGestureJank() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            startActivityAndWait(Intent())
            device.waitForIdle()
            // open the first video on the home feed
            device.findObject(By.clickable(true))?.click()
            device.waitForIdle()
        },
    ) {
        val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        // swipe-to-fullscreen morph from the middle of the screen, then back
        device.swipe(630, 1400, 630, 2500, 40)
        device.waitForIdle()
        device.swipe(630, 2500, 630, 1400, 40)
        device.waitForIdle()
    }

    companion object {
        const val PACKAGE = "com.tsutsen.platformplayer.d"
    }
}
