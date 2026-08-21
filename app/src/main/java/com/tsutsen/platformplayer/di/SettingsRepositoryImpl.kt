package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences
import com.tsutsen.platformplayer.core.datastore.model.SubtitlePreferences
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
                        dynamicColor = s.appearance.dynamicColor,
                    ),
                playback = PlaybackPreferences(autoPlay = s.playback.autoplay),
                subtitle =
                    SubtitlePreferences(
                        font = s.playback.subtitleFont,
                        size = s.playback.subtitleSize,
                        bottomPadding = s.playback.subtitleBottomPadding,
                    ),
                defaultPlaybackSpeed = s.playback.defaultPlaybackSpeed,
                enableDeveloperOptions = s.advancedSettings,
                dualScreen = s.dualScreen,
                gridColumns = s.feed.gridColumns,
                searchHistory = s.search.history,
                showRecommendedVideos = s.content.showRecommendedVideos,
                showComments = s.content.showComments,
            )
        }

        private fun emit() {
            _preferences.value = fromSettings()
        }

        override suspend fun updateAppearance(prefs: AppearancePreferences) {
            val s = Settings.instance
            s.appearance.themeMode = prefs.themeMode.name
            s.appearance.dynamicColor = prefs.dynamicColor
            s.save()
            emit()
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
                "enableDeveloperOptions" -> s.advancedSettings = value as Boolean
                "dualScreen" -> s.dualScreen = value as Boolean
                "dynamicColor" -> s.appearance.dynamicColor = value as Boolean
                "gridColumns" -> s.feed.gridColumns = value as Int
                "searchHistory" -> s.search.history = value as List<String>
                "showRecommendedVideos" -> s.content.showRecommendedVideos = value as Boolean
                "showComments" -> s.content.showComments = value as Boolean
                "subtitleFont" -> s.playback.subtitleFont = value as String
                "subtitleSize" -> s.playback.subtitleSize = value as String
                "subtitleBottomPadding" -> s.playback.subtitleBottomPadding = value as String
                "defaultPlaybackSpeed" -> s.playback.defaultPlaybackSpeed = value as Float
                else -> return
            }
            s.save()
            emit()
        }

        override suspend fun resetToDefaults() {
            val s = Settings.instance
            s.appearance.themeMode = "AUTO"
            s.appearance.dynamicColor = true
            s.advancedSettings = false
            s.dualScreen = false
            s.feed.gridColumns = 3
            s.content.showRecommendedVideos = true
            s.content.showComments = true
            s.playback.defaultPlaybackSpeed = 1f
            s.playback.subtitleFont = "default"
            s.playback.subtitleSize = "standard"
            s.playback.subtitleBottomPadding = "standard"
            s.save()
            emit()
        }
    }
