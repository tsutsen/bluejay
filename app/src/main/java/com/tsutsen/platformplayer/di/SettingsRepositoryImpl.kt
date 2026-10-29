package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.designsystem.layout.bluejayNavItems
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
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
                        uiRounding = s.appearance.uiRounding,
                        customThemes = s.appearance.customThemes,
                        activeThemeId = s.appearance.activeThemeId,
                    ),
                playback = PlaybackPreferences(autoPlay = s.playback.autoplay),
                subtitle =
                    SubtitlePreferences(
                        font = s.playback.subtitleFont,
                        size = s.playback.subtitleFontSize,
                        bottomPadding = s.playback.subtitleBottomPadding,
                        outline = s.playback.subtitleOutline,
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
                dualScreenTopCreatorsScope = s.dualScreenTopCreatorsScope,
                dualScreenDashPageOrder = s.dualScreenDashPageOrder,
                dualScreenFeedSources = s.dualScreenFeedSources,
                dualScreenLibrarySlots = s.dualScreenLibrarySlots,
                librarySectionOrder = s.librarySectionOrder,
                librarySectionsEnabled = s.librarySectionsEnabled,
                gridColumns = s.feed.gridColumns,
                homeHiddenSources = s.feed.hiddenSources,
                searchHistory = s.search.history,
                gettingStartedCompleted = s.gettingStartedCompleted,
                homeLoginPromptsDismissed = s.feed.loginPromptsDismissed,
                showRecommendedVideos = s.content.showRecommendedVideos,
                showComments = s.content.showComments,
                showWatchedVideos = s.content.showWatchedVideos,
                showStartedVideos = s.content.showStartedVideos,
                homebrewRecommendations = s.recommendations.homebrewRecommendations,
                sourceYoutubeFeed = s.recommendations.sourceYoutubeFeed,
                sourceSubscriptions = s.recommendations.sourceSubscriptions,
                sourceTopCreators = s.recommendations.sourceTopCreators,
                autoUpdatePlugins = s.plugins.autoUpdatePlugins,
                dockTabOrder = s.other.dockTabOrder,
                defaultDockTab = s.other.defaultDockTab,
                subscriptionsOrder = s.subscriptions.subscriptionsOrder,
            )
        }

        private fun emit() {
            _preferences.value = fromSettings()
        }

        override suspend fun updateAppearance(prefs: AppearancePreferences) {
            val s = Settings.instance
            s.appearance.themeMode = prefs.themeMode.name
            s.appearance.dynamicColor = prefs.dynamicColor
            s.appearance.uiRounding = prefs.uiRounding
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
            when (key) {
                "enableDeveloperOptions" -> setEnableDeveloperOptions(value as Boolean)
                "dualScreen" -> setDualScreen(value as Boolean)
                "dynamicColor" -> setDynamicColor(value as Boolean)
                "uiRounding" -> setUiRounding((value as Number).toInt())
                "gridColumns" -> setGridColumns(value as Int)
                "homeHiddenSources" -> setHomeHiddenSources(value as List<String>)
                "homeLoginPromptsDismissed" -> setHomeLoginPromptsDismissed(value as List<String>)
                "gettingStartedCompleted" -> setGettingStartedCompleted(value as Boolean)
                "searchHistory" -> setSearchHistory(value as List<String>)
                "showRecommendedVideos" -> setShowRecommendedVideos(value as Boolean)
                "showComments" -> setShowComments(value as Boolean)
                "autoUpdatePlugins" -> setAutoUpdatePlugins(value as Boolean)
                "subtitleFont" -> setSubtitleFont(value as String)
                "subtitleFontSize" -> setSubtitleFontSize((value as Number).toInt())
                "subtitleBottomPadding" -> setSubtitleBottomPadding((value as Number).toInt())
                "subtitleOutline" -> setSubtitleOutline((value as Number).toInt())
                "defaultPlaybackSpeed" -> setDefaultPlaybackSpeed(value as Float)
                "defaultSpeedup" -> setDefaultSpeedup(value as Float)
                "speedupSensitivity" -> setSpeedupSensitivity(value as Float)
                "jumpStepSeconds" -> setJumpStepSeconds((value as Number).toInt())
                "defaultVideoResolution" -> setDefaultVideoResolution(value as String)
                "defaultDownloadResolution" -> setDefaultDownloadResolution(value as String)
                else -> return
            }
        }

        // Each typed setter persists the field and re-emits (same contract as
        // updateGeneral, which now delegates here).
        override suspend fun setEnableDeveloperOptions(enabled: Boolean) =
            persist { it.advancedSettings = enabled }

        override suspend fun setDualScreen(enabled: Boolean) =
            persist { it.dualScreen = enabled }

        override suspend fun setDynamicColor(enabled: Boolean) =
            persist { it.appearance.dynamicColor = enabled }

        override suspend fun setUiRounding(rounding: Int) =
            persist { it.appearance.uiRounding = rounding }

        override suspend fun setGridColumns(columns: Int) =
            persist { it.feed.gridColumns = columns }

        override suspend fun setHomeHiddenSources(ids: List<String>) =
            persist { it.feed.hiddenSources = ids }

        override suspend fun setHomeLoginPromptsDismissed(ids: List<String>) =
            persist { it.feed.loginPromptsDismissed = ids }

        override suspend fun setGettingStartedCompleted(completed: Boolean) =
            persist { it.gettingStartedCompleted = completed }

        override suspend fun setSearchHistory(history: List<String>) =
            persist { it.search.history = history }

        override suspend fun setShowRecommendedVideos(show: Boolean) =
            persist { it.content.showRecommendedVideos = show }

        override suspend fun setShowComments(show: Boolean) =
            persist { it.content.showComments = show }

        override suspend fun setShowWatchedVideos(show: Boolean) =
            persist { it.content.showWatchedVideos = show }

        override suspend fun setShowStartedVideos(show: Boolean) =
            persist { it.content.showStartedVideos = show }

        override suspend fun setHomebrewRecommendations(enabled: Boolean) =
            persist { it.recommendations.homebrewRecommendations = enabled }

        override suspend fun setSourceYoutubeFeed(enabled: Boolean) =
            persist { it.recommendations.sourceYoutubeFeed = enabled }

        override suspend fun setSourceSubscriptions(enabled: Boolean) =
            persist { it.recommendations.sourceSubscriptions = enabled }

        override suspend fun setSourceTopCreators(enabled: Boolean) =
            persist { it.recommendations.sourceTopCreators = enabled }

        override suspend fun setDockTabOrder(order: List<String>) =
            persist { it.other.dockTabOrder = order }

        override suspend fun setDefaultDockTab(key: String) =
            persist { it.other.defaultDockTab = key }

        override suspend fun setSubscriptionsOrder(order: String) =
            persist { it.subscriptions.subscriptionsOrder = order }

        override suspend fun addHiddenVideoUrl(url: String) =
            persist { it.content.hiddenVideoUrls = (it.content.hiddenVideoUrls + url).distinct() }

        override suspend fun setAutoUpdatePlugins(enabled: Boolean) =
            persist { it.plugins.autoUpdatePlugins = enabled }

        override suspend fun setSubtitleFont(font: String) =
            persist { it.playback.subtitleFont = font }

        override suspend fun setSubtitleFontSize(sizeSp: Int) =
            persist { it.playback.subtitleFontSize = sizeSp }

        override suspend fun setSubtitleBottomPadding(paddingDp: Int) =
            persist { it.playback.subtitleBottomPadding = paddingDp }

        override suspend fun setSubtitleOutline(width: Int) =
            persist { it.playback.subtitleOutline = width }

        override suspend fun setDefaultPlaybackSpeed(speed: Float) =
            persist { it.playback.defaultPlaybackSpeed = speed }

        override suspend fun setDefaultSpeedup(multiplier: Float) =
            persist { it.playback.defaultSpeedup = multiplier }

        override suspend fun setSpeedupSensitivity(sensitivity: Float) =
            persist { it.playback.speedupSensitivity = sensitivity }

        override suspend fun setJumpStepSeconds(seconds: Int) =
            persist { it.playback.jumpStepSeconds = seconds }

        override suspend fun setDefaultVideoResolution(resolution: String) =
            persist { it.content.defaultVideoResolution = resolution }

        override suspend fun setDefaultDownloadResolution(resolution: String) =
            persist { it.content.defaultDownloadResolution = resolution }

        private inline fun persist(block: (Settings) -> Unit) {
            val s = Settings.instance
            block(s)
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

        override suspend fun updateDualScreenTopCreatorsScope(scope: String) {
            val s = Settings.instance
            s.dualScreenTopCreatorsScope = scope
            s.save()
            emit()
        }

        override suspend fun updateDualScreenDashPageOrder(order: List<String>) {
            val s = Settings.instance
            s.dualScreenDashPageOrder = order
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

        override suspend fun saveCustomTheme(theme: CustomTheme) {
        val s = Settings.instance
        s.appearance.customThemes = s.appearance.customThemes.filter { it.id != theme.id } + theme
        s.save()
        emit()
    }

    override suspend fun deleteCustomTheme(id: String) {
        val s = Settings.instance
        s.appearance.customThemes = s.appearance.customThemes.filterNot { it.id == id }
        if (s.appearance.activeThemeId == id) s.appearance.activeThemeId = null
        s.save()
        emit()
    }

    override suspend fun setActiveThemeId(id: String?) {
        val s = Settings.instance
        s.appearance.activeThemeId = id
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

        override suspend fun updateLibrarySectionsEnabled(ids: List<String>) {
            val s = Settings.instance
            s.librarySectionsEnabled = ids
            s.save()
            emit()
        }

        override suspend fun resetToDefaults() {
            val s = Settings.instance
            s.appearance.themeMode = "AUTO"
            s.appearance.dynamicColor = true
            s.appearance.uiRounding = 100
            s.advancedSettings = false
            s.dualScreen = false
            s.dualScreenPages = listOf("video", "library", "home")
            s.dualScreenVideoTabs =
                listOf("info", "controls", "comments", "chapters", "recommended", "queue")
            s.dualScreenVideoTabOrder =
                listOf("info", "controls", "comments", "chapters", "recommended", "queue")
            s.dualScreenPageOrder = listOf("controls", "video", "tabs")
            s.dualScreenTopCreatorsScope = "week"
            s.dualScreenDashPageOrder = listOf("stats", "top_creators", "continue")
            s.dualScreenFeedSources = emptyList()
            s.dualScreenLibrarySlots =
                listOf("watch_later", "liked", "favourite", "history")
            s.librarySectionOrder =
                listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists")
            s.librarySectionsEnabled =
                listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists")
            s.feed.gridColumns = 3
            s.content.showRecommendedVideos = true
            s.content.showComments = true
            s.content.showWatchedVideos = true
            s.content.showStartedVideos = true
            s.other.dockTabOrder = bluejayNavItems.map { it.key }
            s.other.defaultDockTab = "home"
            s.subscriptions.subscriptionsOrder = "watchtime"
            s.playback.defaultPlaybackSpeed = 1f
            s.playback.defaultSpeedup = 2f
            s.playback.speedupSensitivity = 1f
            s.content.defaultVideoResolution = "Auto"
            s.content.defaultDownloadResolution = "480p"
            s.playback.subtitleFont = "default"
            s.playback.subtitleFontSize = 16
            s.playback.subtitleBottomPadding = 20
            s.playback.subtitleOutline = 3
            s.save()
            emit()
        }
    }
