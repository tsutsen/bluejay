package com.futo.platformplayer.feature.dualscreen

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.Display
import android.view.WindowManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the companion window on secondary displays.
 * Handles display detection, window creation, and Picture-in-Picture mode.
 */
@Singleton
class CompanionWindowManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _isCompanionAvailable = MutableStateFlow(false)
    val isCompanionAvailable: StateFlow<Boolean> = _isCompanionAvailable.asStateFlow()

    private val _secondaryDisplayCount = MutableStateFlow(0)
    val secondaryDisplayCount: StateFlow<Int> = _secondaryDisplayCount.asStateFlow()

    init {
        checkForSecondaryDisplays()
    }

    /**
     * Check if secondary displays are available (e.g., AYN Thor's second screen).
     */
    private fun checkForSecondaryDisplays() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displays = windowManager.displays
        val secondaryCount = displays.count { it.displayId != Display.DEFAULT_DISPLAY }
        _secondaryDisplayCount.value = secondaryCount
        _isCompanionAvailable.value = secondaryCount > 0
    }

    /**
     * Launch the companion window on a secondary display.
     */
    fun launchCompanionWindow(activity: Activity) {
        if (!_isCompanionAvailable.value) {
            android.util.Log.w("CompanionWindow", "No secondary display available")
            return
        }

        // For now, use PiP as a fallback since full secondary display support
        // requires CompanionActivity implementation (Phase 7)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    /**
     * Check if the device is in a folding state (useful for dual-screen devices).
     */
    fun isFoldingDevice(): Boolean {
        // Will be fully implemented with WindowLayoutInfo in Phase 7
        return false
    }

    /**
     * Get the size of the secondary display, if available.
     */
    fun getSecondaryDisplaySize(): Pair<Int, Int>? {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displays = windowManager.displays
        val secondary = displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        return secondary?.let {
            val metrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(it)
            Pair(metrics.bounds.width(), metrics.bounds.height())
        }
    }
}
