package com.futo.platformplayer.feature.dualscreen

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import android.view.WindowManager
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

    init {
        // For now, assume no secondary displays (will be detected properly in Phase 7)
        _isCompanionAvailable.value = false
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
        // Will be implemented in Phase 7
        return null
    }
}
