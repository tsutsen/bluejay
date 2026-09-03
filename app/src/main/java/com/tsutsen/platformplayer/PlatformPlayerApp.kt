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
import com.tsutsen.platformplayer.core.database.dao.NotificationDao
import com.tsutsen.platformplayer.states.StateApp
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

private const val TAG = "BluejayTheme"

/**
 * Hilt-enabled Application class for Bluejay.
 * Initializes DynamicColors for Material You support on Android 13+.
 * Applies theme mode (light/dark/auto) from user preferences.
 */
@HiltAndroidApp
class PlatformPlayerApp : Application() {
    init {
        // The Hilt-generated subclass injects the @Inject fields (the
        // PlayerRepository graph, which includes SettingsRepository) BEFORE
        // the onCreate() body runs. Settings persistence needs the context at
        // that moment, so establish it as early as possible. (An Application
        // IS its own application context; `applicationContext` is still null
        // during construction, so store `this` directly.)
        context = this
    }

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var notificationDao: NotificationDao

    /** Accessor for components that cannot use Hilt (e.g. plain broadcast receivers). */
    fun playerRepository(): PlayerRepository = playerRepository

    /** Accessor for non-Hilt components (e.g. WorkManager workers). */
    fun notificationDao(): NotificationDao = notificationDao

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

        // Hilt injects the singleton graph inside super.onCreate() — before
        // any Activity onCreate runs. Some singletons (the casting
        // repository) can reach FragmentedStorage during construction, so
        // file storage must exist BEFORE the injection, not after.
        // MainActivity's mainAppStarting() force-initializes it later
        // exactly as before.
        StateApp.instance.setGlobalContext(applicationContext)
        StateApp.instance.initializeFiles()

        super.onCreate()

        // Start recording local watch history (the stats/history data source).
        com.tsutsen.platformplayer.stats.WatchHistoryRecorder.start(playerRepository)

        applyThemeMode()

        if (Settings.instance.appearance.dynamicColor) {
            Log.d(TAG, "DynamicColors.applyToActivitiesIfAvailable(this)")
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        Log.d(TAG, "PlatformPlayerApp.onCreate() complete")
    }
}
