package com.tsutsen.platformplayer

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import java.io.File

private const val TAG = "GrayjayTheme"

/**
 * Hilt-enabled Application class for Bluejay.
 * Initializes DynamicColors for Material You support on Android 13+.
 * Applies theme mode (light/dark/auto) from user preferences.
 */
@HiltAndroidApp
class PlatformPlayerApp : Application() {

    companion object {
        private const val THEME_MODE_AUTO = "AUTO"
        private const val THEME_MODE_LIGHT = "LIGHT"
        private const val THEME_MODE_DARK = "DARK"
        private const val PREFS_NAME = "com.tsutsen.platformplayer.Settings"
        private const val KEY_THEME_MODE = "themeMode"

        fun applyThemeMode(context: android.content.Context) {
            Log.d(TAG, "applyThemeMode: starting")

            val themeMode = try {
                val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val themeModeInt = prefs.getInt(KEY_THEME_MODE, 0)

                when (themeModeInt) {
                    0 -> THEME_MODE_AUTO
                    1 -> THEME_MODE_LIGHT
                    2 -> THEME_MODE_DARK
                    else -> THEME_MODE_AUTO
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading settings: ${e.message}")
                THEME_MODE_AUTO
            }

            Log.d(TAG, "resolved theme mode: $themeMode")

            val modeName = when (themeMode) {
                THEME_MODE_AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }

            AppCompatDelegate.setDefaultNightMode(modeName)
            Log.d(TAG, "setDefaultNightMode called with: $modeName")
        }
    }

    override fun onCreate() {
        Log.d(TAG, "PlatformPlayerApp.onCreate() starting")
        super.onCreate()

        applyThemeMode(this)

        Log.d(TAG, "DynamicColors.applyToActivitiesIfAvailable(this)")
        DynamicColors.applyToActivitiesIfAvailable(this)

        Log.d(TAG, "PlatformPlayerApp.onCreate() complete")
    }
}
