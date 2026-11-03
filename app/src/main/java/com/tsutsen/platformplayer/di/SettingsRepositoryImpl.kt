package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.designsystem.layout.bluejayNavItems
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.AutoUpdatePreferences
import com.tsutsen.platformplayer.core.datastore.model.BackupPreferences
import com.tsutsen.platformplayer.core.datastore.model.BrowsingPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.DownloadsPreferences
import com.tsutsen.platformplayer.core.datastore.model.LoggingPreferences
import com.tsutsen.platformplayer.core.datastore.model.NotificationPreferences
import com.tsutsen.platformplayer.core.datastore.model.OtherPreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences
import com.tsutsen.platformplayer.core.datastore.model.StoragePreferences
import com.tsutsen.platformplayer.core.datastore.model.SubscriptionsPreferences
import com.tsutsen.platformplayer.core.datastore.model.SubtitlePreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.model.GestureAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SettingsRepository backed by the legacy [Settings] singleton — the only
 * reader left (all other files read the typed [AppPreferences] seam). Each
 * setter is field access + [Settings.save], then a re-emit so the UI updates
 * live.
 */
@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor() : SettingsRepository {
        private val _preferences = MutableStateFlow(fromSettings())
        override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

        // Kept out of [AppPreferences] (the sheet only needs this one list);
        // re-synced after the hidden-set changes below.
        private val _hiddenVideoUrls = MutableStateFlow(Settings.instance.content.hiddenVideoUrls)
        override val hiddenVideoUrls: StateFlow<List<String>> = _hiddenVideoUrls.asStateFlow()

        // Same pattern as _hiddenVideoUrls: kept out of [AppPreferences] (the
        // sheet only needs these lists); re-synced after each change below.
        private val _hiddenCreators = MutableStateFlow(Settings.instance.content.hiddenCreators)
        override val hiddenCreators: StateFlow<List<String>> = _hiddenCreators.asStateFlow()

        private val _downrankedCreators =
            MutableStateFlow(Settings.instance.content.downrankedCreators)
        override val downrankedCreators: StateFlow<List<String>> = _downrankedCreators.asStateFlow()

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
                playback =
                    PlaybackPreferences(
                        autoPlay = s.playback.autoplay,
                        preferOriginalAudio = s.playback.preferOriginalAudio,
                        restartPlaybackAfterLoss = s.playback.restartPlaybackAfterLoss,
                    ),
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
                                top = s.playerGestures.fullscreen.top.mapValues { GestureAction.fromPersisted(it.value) },
                                bottomLeft = s.playerGestures.fullscreen.bottomLeft.mapValues { GestureAction.fromPersisted(it.value) },
                                bottomCenter = s.playerGestures.fullscreen.bottomCenter.mapValues { GestureAction.fromPersisted(it.value) },
                                bottomRight = s.playerGestures.fullscreen.bottomRight.mapValues { GestureAction.fromPersisted(it.value) },
                            ),
                        normal =
                            PlayerGestureSlotSet(
                                top = s.playerGestures.normal.top.mapValues { GestureAction.fromPersisted(it.value) },
                                bottomLeft = s.playerGestures.normal.bottomLeft.mapValues { GestureAction.fromPersisted(it.value) },
                                bottomCenter = s.playerGestures.normal.bottomCenter.mapValues { GestureAction.fromPersisted(it.value) },
                                bottomRight = s.playerGestures.normal.bottomRight.mapValues { GestureAction.fromPersisted(it.value) },
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
                probCut1 = s.recommendations.probCut1,
                probCut2 = s.recommendations.probCut2,
                autoUpdatePlugins = s.plugins.autoUpdatePlugins,
                dockTabOrder = s.other.dockTabOrder,
                defaultDockTab = s.other.defaultDockTab,
                subscriptionsOrder = s.subscriptions.subscriptionsOrder,
                didFirstStart = s.didFirstStart,
                downloads =
                    DownloadsPreferences(
                        byteRangeDownload = s.downloads.byteRangeDownload,
                        // shouldDownload / byteRangeThreadCount: legacy stub
                        // getters returned the constants true / 4.
                    ),
                storage =
                    StoragePreferences(
                        storageGeneral = s.storage.storage_general,
                        storageDownload = s.storage.storage_download,
                        isStorageMainValid = s.storage.isStorageMainValid,
                        // storageGeneralUri: the legacy stub getter returned a
                        // constant null, unrelated to storageGeneral.
                    ),
                subscriptions =
                    SubscriptionsPreferences(
                        fetchOnAppBoot = s.subscriptions.fetchOnAppBoot,
                        useSubscriptionExchange = s.subscriptions.useSubscriptionExchange,
                        peekChannelContents = s.subscriptions.peekChannelContents,
                        // backgroundIntervalMinutes / concurrency: legacy stub
                        // getters returned the constants 60 / 4.
                    ),
                backup =
                    BackupPreferences(
                        // shouldAutomaticBackup: legacy stub getter returned
                        // the constant false.
                        didAskAutoBackup = s.backup.didAskAutoBackup,
                        lastAutoBackupTime = s.backup.lastAutoBackupTime,
                    ),
                notifications =
                    NotificationPreferences(
                        plannedContentNotification = s.notifications.plannedContentNotification,
                    ),
                logging = LoggingPreferences(logLevel = s.logging.logLevel),
                browsing =
                    BrowsingPreferences(
                        useDownloadedCABundle = s.browsing.useDownloadedCABundle,
                        videoCache = s.browsing.videoCache,
                    ),
                autoUpdate =
                    AutoUpdatePreferences(
                        // isAutoUpdateEnabled: legacy stub getter returned the
                        // constant true.
                        shouldBackgroundDownload = s.autoUpdate.shouldBackgroundDownload,
                    ),
                other =
                    OtherPreferences(
                        watchLaterAddStart = s.other.watchLaterAddStart,
                        playlistAllowDups = s.other.playlistAllowDups,
                    ),
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

        override suspend fun setAutoplay(enabled: Boolean) =
            persist { it.playback.autoplay = enabled }

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

        override suspend fun setProbCuts(cut1: Int, cut2: Int) =
            persist {
                val lo = cut1.coerceIn(0, 100)
                val hi = cut2.coerceIn(lo, 100)
                it.recommendations.probCut1 = lo
                it.recommendations.probCut2 = hi
            }

        override suspend fun setDockTabOrder(order: List<String>) =
            persist { it.other.dockTabOrder = order }

        override suspend fun setDefaultDockTab(key: String) =
            persist { it.other.defaultDockTab = key }

        override suspend fun setSubscriptionsOrder(order: String) =
            persist { it.subscriptions.subscriptionsOrder = order }

        override suspend fun addHiddenVideoUrl(url: String) {
            persist { it.content.hiddenVideoUrls = (it.content.hiddenVideoUrls + url).distinct() }
            _hiddenVideoUrls.value = Settings.instance.content.hiddenVideoUrls
        }

        override suspend fun removeHiddenVideoUrl(url: String) {
            persist { it.content.hiddenVideoUrls = it.content.hiddenVideoUrls - url }
            _hiddenVideoUrls.value = Settings.instance.content.hiddenVideoUrls
        }

        override suspend fun addHiddenCreator(authorUrl: String) {
            persist { it.content.hiddenCreators = (it.content.hiddenCreators + authorUrl).distinct() }
            _hiddenCreators.value = Settings.instance.content.hiddenCreators
        }

        override suspend fun removeHiddenCreator(authorUrl: String) {
            persist { it.content.hiddenCreators = it.content.hiddenCreators - authorUrl }
            _hiddenCreators.value = Settings.instance.content.hiddenCreators
        }

        override suspend fun addDownrankedCreator(authorUrl: String) {
            persist {
                it.content.downrankedCreators = (it.content.downrankedCreators + authorUrl).distinct()
            }
            _downrankedCreators.value = Settings.instance.content.downrankedCreators
        }

        override suspend fun removeDownrankedCreator(authorUrl: String) {
            persist { it.content.downrankedCreators = it.content.downrankedCreators - authorUrl }
            _downrankedCreators.value = Settings.instance.content.downrankedCreators
        }

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
                            top = fullscreen.top.mapValues { it.value.name },
                            bottomLeft = fullscreen.bottomLeft.mapValues { it.value.name },
                            bottomCenter = fullscreen.bottomCenter.mapValues { it.value.name },
                            bottomRight = fullscreen.bottomRight.mapValues { it.value.name },
                        ),
                    normal =
                        Settings.PlayerGestureSlotSet(
                            top = normal.top.mapValues { it.value.name },
                            bottomLeft = normal.bottomLeft.mapValues { it.value.name },
                            bottomCenter = normal.bottomCenter.mapValues { it.value.name },
                            bottomRight = normal.bottomRight.mapValues { it.value.name },
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

        override suspend fun setStorageGeneral(uri: String?) =
            persist { it.storage.storage_general = uri }

        override suspend fun setStorageDownload(uri: String?) =
            persist { it.storage.storage_download = uri }

        override suspend fun setDidAskAutoBackup(asked: Boolean) =
            persist { it.backup.didAskAutoBackup = asked }

        override suspend fun setDidFirstStart(firstStart: Boolean) =
            persist { it.didFirstStart = firstStart }

        override suspend fun setLastAutoBackupTime(time: OffsetDateTime) =
            persist { it.backup.lastAutoBackupTime = time }

        /** Full legacy-settings JSON (backup export + custom-settings diffing). */
        fun encodeLegacySettings(): String = Settings.instance.encode()

        /** Re-emit prefs after the legacy Settings object was swapped (backup import). */
        fun reloadFromLegacy() = emit()

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
    
    companion object {
        /** Single accessor for the typed settings seam; set once in PlatformPlayerApp.onCreate. */
        lateinit var instance: SettingsRepositoryImpl
            private set

        fun bind(instance: SettingsRepositoryImpl) {
            this.instance = instance
        }
    }
}
