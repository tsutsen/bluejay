package com.futo.platformplayer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import java.io.File

private const val TAG = "GrayjayTheme"

/**
 * Custom Application class for Grayjay.
 * Initializes DynamicColors for Material You support on Android 13+.
 * Applies theme mode (light/dark/auto) from user preferences.
 */
class App : Application() {

    companion object {
        // Theme mode values
        private const val THEME_MODE_AUTO = "AUTO"
        private const val THEME_MODE_LIGHT = "LIGHT"
        private const val THEME_MODE_DARK = "DARK"

        /**
         * Apply theme mode from settings.
         * Reads from the JSON settings file where AppearanceSettings.themeMode is stored.
         */
        fun applyThemeMode(context: Context) {
            Log.d(TAG, "applyThemeMode: starting")

            // Try to read theme mode from the JSON settings file
            // The file is stored as com.futo.platformplayer.Settings.json
            val settingsFile = File(context.filesDir, "com.futo.platformplayer.Settings.json")
            Log.d(TAG, "Looking for settings at: ${settingsFile.absolutePath}")
            Log.d(TAG, "File exists: ${settingsFile.exists()}")

            val themeMode = try {
                if (settingsFile.exists()) {
                    val json = settingsFile.readText()
                    Log.d(TAG, "settings.json exists, size: ${json.length}")

                    // Simple string search for themeMode in the JSON
                    // The JSON structure is: "appearance":{"themeMode":1,...}
                    val themeModeMatch = Regex("""\"themeMode\"\s*:\s*(\d+)""").find(json)
                    val themeModeInt = themeModeMatch?.groupValues?.get(1)?.toIntOrNull()
                    Log.d(TAG, "themeMode from settings.json: $themeModeInt")

                    when (themeModeInt) {
                        0 -> THEME_MODE_AUTO
                        1 -> THEME_MODE_LIGHT
                        2 -> THEME_MODE_DARK
                        else -> THEME_MODE_AUTO
                    }
                } else {
                    Log.d(TAG, "settings.json does not exist, using default")
                    THEME_MODE_AUTO
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading settings.json: ${e.message}")
                THEME_MODE_AUTO
            }

            Log.d(TAG, "resolved theme mode: $themeMode")

            val modeName = when (themeMode) {
                THEME_MODE_AUTO -> {
                    Log.d(TAG, "Setting MODE_NIGHT_FOLLOW_SYSTEM")
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                THEME_MODE_LIGHT -> {
                    Log.d(TAG, "Setting MODE_NIGHT_NO (light)")
                    AppCompatDelegate.MODE_NIGHT_NO
                }
                THEME_MODE_DARK -> {
                    Log.d(TAG, "Setting MODE_NIGHT_YES (dark)")
                    AppCompatDelegate.MODE_NIGHT_YES
                }
                else -> {
                    Log.w(TAG, "Invalid mode '$themeMode', defaulting to light")
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }

            AppCompatDelegate.setDefaultNightMode(modeName)
            Log.d(TAG, "setDefaultNightMode called with: $modeName")
            Log.d(TAG, "Current night mode: ${AppCompatDelegate.getDefaultNightMode()}")
        }
    }

    override fun onCreate() {
        Log.d(TAG, "App.onCreate() starting")
        super.onCreate()
        Log.d(TAG, "After super.onCreate()")

        // Apply theme mode from settings (defaults to auto/light if not set)
        applyThemeMode(this)

        Log.d(TAG, "DynamicColors.applyToActivitiesIfAvailable(this)")
        // Enable Material You dynamic color on Android 13+ (API 33+)
        DynamicColors.applyToActivitiesIfAvailable(this)

        Log.d(TAG, "App.onCreate() complete")
    }
}
