package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val preferences: StateFlow<AppPreferences>

    suspend fun updateAppearance(prefs: com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences)
    suspend fun updatePlayback(prefs: com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences)
    /** Toggle queue autoplay (Settings > Content > Video page). */
    suspend fun setAutoplay(enabled: Boolean)
    suspend fun updateGeneral(key: String, value: Any)

    // Typed per-key setters (the keys the settings screens use).
    suspend fun setEnableDeveloperOptions(enabled: Boolean)
    suspend fun setDualScreen(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setUiRounding(rounding: Int)
    suspend fun setGridColumns(columns: Int)
    suspend fun setHomeHiddenSources(ids: List<String>)
    suspend fun setHomeLoginPromptsDismissed(ids: List<String>)
    suspend fun setGettingStartedCompleted(completed: Boolean)
    suspend fun setSearchHistory(history: List<String>)
    suspend fun setShowRecommendedVideos(show: Boolean)
    suspend fun setShowComments(show: Boolean)
    suspend fun setShowWatchedVideos(show: Boolean)
    suspend fun setShowStartedVideos(show: Boolean)
    suspend fun setHomebrewRecommendations(enabled: Boolean)
    suspend fun setSourceYoutubeFeed(enabled: Boolean)
    suspend fun setSourceSubscriptions(enabled: Boolean)
    suspend fun setSourceTopCreators(enabled: Boolean)
    /** Spawn-probability cut points (percent 0..100, coerced so cut1 <= cut2) splitting 0..100 into the three source segments. */
    suspend fun setProbCuts(cut1: Int, cut2: Int)
    suspend fun setDockTabOrder(order: List<String>)
    suspend fun setDefaultDockTab(key: String)
    suspend fun setSubscriptionsOrder(order: String)
    /** Persist a video URL as hidden (long-press sheet "Hide"); filtered from the home feed. */
    suspend fun addHiddenVideoUrl(url: String)

    /** Remove a video URL from the hidden set (long-press sheet "Unhide"). */
    suspend fun removeHiddenVideoUrl(url: String)

    /** Live hidden video URLs (drives the options sheet's Hide/Unhide tile). */
    val hiddenVideoUrls: StateFlow<List<String>>

    /** Persist a creator (authorUrl) as hidden; its cards are filtered out of the home feed. */
    suspend fun addHiddenCreator(authorUrl: String)

    /** Remove a creator from the hidden set (sheet arrow menu toggle). */
    suspend fun removeHiddenCreator(authorUrl: String)

    /** Persist a creator (authorUrl) as downranked; its cards sink to the end of the home feed. */
    suspend fun addDownrankedCreator(authorUrl: String)

    /** Remove a creator from the downranked set (sheet arrow menu toggle). */
    suspend fun removeDownrankedCreator(authorUrl: String)

    /** Live hidden creator urls (drives the options sheet's "Don't show this creator"). */
    val hiddenCreators: StateFlow<List<String>>

    /** Live downranked creator urls (drives the options sheet's "Show less from this creator"). */
    val downrankedCreators: StateFlow<List<String>>
    suspend fun setAutoUpdatePlugins(enabled: Boolean)
    suspend fun setSubtitleFont(font: String)
    suspend fun setSubtitleFontSize(sizeSp: Int)
    suspend fun setSubtitleBottomPadding(paddingDp: Int)
    suspend fun setSubtitleOutline(width: Int)
    suspend fun setDefaultPlaybackSpeed(speed: Float)
    suspend fun setDefaultSpeedup(multiplier: Float)
    suspend fun setSpeedupSensitivity(sensitivity: Float)
    suspend fun setJumpStepSeconds(seconds: Int)
    suspend fun setDefaultVideoResolution(resolution: String)
    suspend fun setDefaultDownloadResolution(resolution: String)
    suspend fun updateDualScreenPages(pages: List<String>)
    suspend fun updateDualScreenVideoTabs(tabs: List<String>)
    suspend fun updateDualScreenLibrarySlots(slots: List<String>)
    suspend fun updateDualScreenVideoTabOrder(order: List<String>)
    suspend fun updateDualScreenPageOrder(order: List<String>)
    suspend fun updateDualScreenTopCreatorsScope(scope: String)
    suspend fun updateDualScreenDashPageOrder(order: List<String>)
    suspend fun updateDualScreenFeedSources(ids: List<String>)

    /** Save the per-mode gesture assignments (Settings > Gestures). */
    suspend fun updatePlayerGestures(
        fullscreen: PlayerGestureSlotSet,
        normal: PlayerGestureSlotSet,
    )

    /** Save the controller (gamepad) settings (Settings > Controller). */
    suspend fun updateControllerSettings(prefs: ControllerPreferences)

    /** Upsert a user theme (same id = update, new id = add). */
    suspend fun saveCustomTheme(theme: CustomTheme)

    /** Delete a user theme; clears it if it was active. */
    suspend fun deleteCustomTheme(id: String)

    /** Set the active custom theme (null = default theming). */
    suspend fun setActiveThemeId(id: String?)

    suspend fun updateLibrarySectionOrder(order: List<String>)
    suspend fun updateLibrarySectionsEnabled(ids: List<String>)
    suspend fun resetToDefaults()

}
