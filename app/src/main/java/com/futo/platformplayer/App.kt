package com.futo.platformplayer

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Custom Application class for Grayjay.
 * Initializes DynamicColors for Material You support on Android 13+.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Material You dynamic color on Android 13+ (API 33+)
        // This extracts colors from the user's wallpaper for theming
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
