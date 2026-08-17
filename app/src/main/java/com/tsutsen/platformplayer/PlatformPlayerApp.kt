package com.tsutsen.platformplayer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import java.io.File

private const val TAG = "GrayjayTheme"

/**
 * Hilt-enabled Application class for Bluejay.
 * Initializes DynamicColors for Material You support on Android 13+.
 * Applies theme mode (light/dark/auto) from user preferences.
 */
@HiltAndroidApp
class PlatformPlayerApp : Application() {

    @Inject
    lateinit var playerRepository: PlayerRepository

    /** Accessor for components that cannot use Hilt (e.g. plain broadcast receivers). */
    fun playerRepository(): PlayerRepository = playerRepository

    companion object {
        /** Process-wide context, set in [onCreate]; used by [Settings] persistence. */
        var context: Context? = null
            private set

        /** Apply theme mode from the single config ([Settings] singleton). */
        fun applyThemeMode() {
            val nightMode =
                when (Settings.instance.appearance.themeMode) {
                    "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
                    "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    override fun onCreate() {
        Log.d(TAG, "PlatformPlayerApp.onCreate() starting")
        super.onCreate()
        context = applicationContext

        applyThemeMode()

        if (Settings.instance.appearance.dynamicColor) {
            Log.d(TAG, "DynamicColors.applyToActivitiesIfAvailable(this)")
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        Log.d(TAG, "PlatformPlayerApp.onCreate() complete")
    }
}
