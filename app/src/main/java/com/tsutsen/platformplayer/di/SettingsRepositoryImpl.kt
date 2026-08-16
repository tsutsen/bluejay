package com.tsutsen.platformplayer.di

import androidx.appcompat.app.AppCompatDelegate
import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.ContrastLevel
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SettingsRepository backed by the legacy [Settings] singleton (single source
 * of truth — 21 reader files read it directly, no migration). Each setter is
 * field access + [Settings.save], then a re-emit so the UI updates live.
 */
@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor() : SettingsRepository {
        private val _preferences = MutableStateFlow(fromSettings())
        override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

        private fun fromSettings(): AppPreferences {
            val s = Settings.instance
            return AppPreferences(
                appearance =
                    AppearancePreferences(
                        themeMode =
                            runCatching { ThemeMode.valueOf(s.appearance.themeMode) }
                                .getOrDefault(ThemeMode.AUTO),
                        contrastLevel =
                            runCatching { ContrastLevel.valueOf(s.appearance.contrastLevel) }
                                .getOrDefault(ContrastLevel.STANDARD),
                        dynamicColor = s.appearance.dynamicColor,
                    ),
                playback = PlaybackPreferences(autoPlay = s.playback.autoplay),
                language = s.language,
                enableNotifications = s.notifications.enabled,
                enableBackgroundPlayback = s.playback.enableBackgroundPlayback,
                enablePictureInPicture = s.playback.enablePictureInPicture,
                confirmExit = s.confirmExit,
                enableDeveloperOptions = s.advancedSettings,
                gridColumns = s.feed.gridColumns,
            )
        }

        private fun emit() {
            _preferences.value = fromSettings()
        }

        override suspend fun updateAppearance(prefs: AppearancePreferences) {
            val s = Settings.instance
            s.appearance.themeMode = prefs.themeMode.name
            s.appearance.contrastLevel = prefs.contrastLevel.name
            s.appearance.dynamicColor = prefs.dynamicColor
            s.save()
            emit()
            applyThemeMode(prefs.themeMode)
        }

        override suspend fun updatePlayback(prefs: PlaybackPreferences) {
            val s = Settings.instance
            s.playback.autoplay = prefs.autoPlay
            s.save()
            emit()
        }

        override suspend fun updateGeneral(
            key: String,
            value: Any,
        ) {
            val s = Settings.instance
            when (key) {
                "language" -> s.language = value as String
                "enableNotifications" -> s.notifications.enabled = value as Boolean
                "enableBackgroundPlayback" -> s.playback.enableBackgroundPlayback = value as Boolean
                "enablePictureInPicture" -> s.playback.enablePictureInPicture = value as Boolean
                "confirmExit" -> s.confirmExit = value as Boolean
                "enableDeveloperOptions" -> s.advancedSettings = value as Boolean
                "dynamicColor" -> s.appearance.dynamicColor = value as Boolean
                "gridColumns" -> s.feed.gridColumns = value as Int
                else -> return
            }
            s.save()
            emit()
        }

        override suspend fun resetToDefaults() {
            val s = Settings.instance
            s.appearance.themeMode = "AUTO"
            s.appearance.dynamicColor = true
            s.appearance.contrastLevel = "STANDARD"
            s.playback.enableBackgroundPlayback = true
            s.playback.enablePictureInPicture = true
            s.notifications.enabled = true
            s.confirmExit = false
            s.advancedSettings = false
            s.language = "en"
            s.feed.gridColumns = 3
            s.save()
            emit()
            applyThemeMode(ThemeMode.AUTO)
        }

        private fun applyThemeMode(mode: ThemeMode) {
            val nightMode =
                when (mode) {
                    ThemeMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
