package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
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
                        size = s.playback.subtitleFontSize,
                        bottomPadding = s.playback.subtitleBottomPadding,
                    ),
                defaultPlaybackSpeed = s.playback.defaultPlaybackSpeed,
                defaultSpeedup = s.playback.defaultSpeedup,
                speedupSensitivity = s.playback.speedupSensitivity,
                jumpStepSeconds = s.playback.jumpStepSeconds,
                controller =
                    ControllerPreferences(
                        enabled = s.controller.enabled,
                        mappings = s.controller.mappings,
                        seekBackSeconds = s.controller.seekBackSeconds,
                        seekForwardSeconds = s.controller.seekForwardSeconds,
                    ),
                playerGestures=
                    PlayerGesturePreferences(
                        fullscreen =
                            PlayerGestureSlotSet(
                                top = s.playerGestures.fullscreen.top,
                                bottomLeft = s.playerGestures.fullscreen.bottomLeft,
                                bottomCenter = s.playerGestures.fullscreen.bottomCenter,
                                bottomRight = s.playerGestures.fullscreen.bottomRight,
                            ),
                        normal =
                            PlayerGestureSlotSet(
                                top = s.playerGestures.normal.top,
                                bottomLeft = s.playerGestures.normal.bottomLeft,
                                bottomCenter = s.playerGestures.normal.bottomCenter,
                                bottomRight = s.playerGestures.normal.bottomRight,
                            ),
                    ),
                defaultVideoResolution = s.content.defaultVideoResolution,
                defaultDownloadResolution = s.content.defaultDownloadResolution,
                enableDeveloperOptions = s.advancedSettings,
                dualScreen = s.dualScreen,
                dualScreenPages = s.dualScreenPages,
                dualScreenVideoTabs = s.dualScreenVideoTabs,
                dualScreenVideoTabOrder = s.dualScreenVideoTabOrder,
                dualScreenPageOrder = s.dualScreenPageOrder,
                dualScreenFeedSources = s.dualScreenFeedSources,
                dualScreenLibrarySlots = s.dualScreenLibrarySlots,
                librarySectionOrder = s.librarySectionOrder,
                gridColumns = s.feed.gridColumns,
                homeHiddenSources = s.feed.hiddenSources,
                searchHistory = s.search.history,
                showRecommendedVideos = s.content.showRecommendedVideos,
                showComments = s.content.showComments,
                autoUpdatePlugins = s.plugins.autoUpdatePlugins,
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
                "homeHiddenSources" -> s.feed.hiddenSources = value as List<String>
                "searchHistory" -> s.search.history = value as List<String>
                "showRecommendedVideos" -> s.content.showRecommendedVideos = value as Boolean
                "showComments" -> s.content.showComments = value as Boolean
                "autoUpdatePlugins" -> s.plugins.autoUpdatePlugins = value as Boolean
                "subtitleFont" -> s.playback.subtitleFont = value as String
                "subtitleFontSize" -> s.playback.subtitleFontSize = (value as Number).toInt()
                "subtitleBottomPadding" -> s.playback.subtitleBottomPadding = (value as Number).toInt()
                "defaultPlaybackSpeed" -> s.playback.defaultPlaybackSpeed = value as Float
                "defaultSpeedup" -> s.playback.defaultSpeedup = value as Float
                "speedupSensitivity" -> s.playback.speedupSensitivity = value as Float
                "jumpStepSeconds" -> s.playback.jumpStepSeconds = (value as Number).toInt()
                "defaultVideoResolution" -> s.content.defaultVideoResolution = value as String
                "defaultDownloadResolution" -> s.content.defaultDownloadResolution = value as String
                else -> return
            }
            s.save()
            emit()
        }

        override suspend fun updateDualScreenPages(pages: List<String>) {
            val s = Settings.instance
            s.dualScreenPages = pages
            s.save()
            emit()
        }

        override suspend fun updateDualScreenVideoTabs(tabs: List<String>) {
            val s = Settings.instance
            s.dualScreenVideoTabs = tabs
            s.save()
            emit()
        }

        override suspend fun updateDualScreenLibrarySlots(slots: List<String>) {
            val s = Settings.instance
            s.dualScreenLibrarySlots = slots
            s.save()
            emit()
        }

        override suspend fun updateDualScreenVideoTabOrder(order: List<String>) {
            val s = Settings.instance
            s.dualScreenVideoTabOrder = order
            s.save()
            emit()
        }

        override suspend fun updateDualScreenPageOrder(order: List<String>) {
            val s = Settings.instance
            s.dualScreenPageOrder = order
            s.save()
            emit()
        }

        override suspend fun updatePlayerGestures(
            fullscreen: PlayerGestureSlotSet,
            normal: PlayerGestureSlotSet,
        ) {
            val s = Settings.instance
            s.playerGestures =
                Settings.PlayerGesturePreferences(
                    fullscreen =
                        Settings.PlayerGestureSlotSet(
                            top = fullscreen.top,
                            bottomLeft = fullscreen.bottomLeft,
                            bottomCenter = fullscreen.bottomCenter,
                            bottomRight = fullscreen.bottomRight,
                        ),
                    normal =
                        Settings.PlayerGestureSlotSet(
                            top = normal.top,
                            bottomLeft = normal.bottomLeft,
                            bottomCenter = normal.bottomCenter,
                            bottomRight = normal.bottomRight,
                        ),
                )
            s.save()
            emit()
        }

        override suspend fun updateControllerSettings(prefs: ControllerPreferences) {
            val s = Settings.instance
            s.controller.enabled = prefs.enabled
            s.controller.mappings = prefs.mappings
            s.controller.seekBackSeconds = prefs.seekBackSeconds
            s.controller.seekForwardSeconds = prefs.seekForwardSeconds
            s.save()
            emit()
        }

        override suspend fun updateDualScreenFeedSources(ids: List<String>) {
            val s = Settings.instance
            s.dualScreenFeedSources = ids
            s.save()
            emit()
        }

        override suspend fun updateLibrarySectionOrder(order: List<String>) {
            val s = Settings.instance
            s.librarySectionOrder = order
            s.save()
            emit()
        }

        override suspend fun resetToDefaults() {
            val s = Settings.instance
            s.appearance.themeMode = "AUTO"
            s.appearance.dynamicColor = true
            s.advancedSettings = false
            s.dualScreen = false
            s.dualScreenPages = listOf("video", "library", "home")
            s.dualScreenVideoTabs =
                listOf("info", "controls", "comments", "chapters", "recommended", "queue")
            s.dualScreenVideoTabOrder =
                listOf("info", "controls", "comments", "chapters", "recommended", "queue")
            s.dualScreenPageOrder = listOf("controls", "video", "tabs")
            s.dualScreenFeedSources = emptyList()
            s.dualScreenLibrarySlots =
                listOf("watch_later", "liked", "favourite", "history")
            s.librarySectionOrder =
                listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists")
            s.feed.gridColumns = 3
            s.content.showRecommendedVideos = true
            s.content.showComments = true
            s.playback.defaultPlaybackSpeed = 1f
            s.playback.defaultSpeedup = 2f
            s.playback.speedupSensitivity = 1f
            s.content.defaultVideoResolution = "Auto"
            s.content.defaultDownloadResolution = "480p"
            s.playback.subtitleFont = "default"
            s.playback.subtitleFontSize = 16
            s.playback.subtitleBottomPadding = 20
            s.save()
            emit()
        }
    }
