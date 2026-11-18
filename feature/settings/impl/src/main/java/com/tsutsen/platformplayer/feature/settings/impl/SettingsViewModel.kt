package com.tsutsen.platformplayer.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.UpdateCheckState
import com.tsutsen.platformplayer.core.data.repository.UpdateRepository
import com.tsutsen.platformplayer.core.datastore.model.*
import com.tsutsen.platformplayer.core.model.GestureAction
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SourceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MVI state for Settings screen.
 */
sealed interface SettingsUiState {
    /**
     * The typed preferences AS-IS: the screen reads [AppPreferences] directly
     * instead of a 41-field hand-maintained shadow (one mapping to forget on
     * every new key).
     */
    data class Loaded(
        val prefs: AppPreferences,
    ) : SettingsUiState

data object Loading : SettingsUiState

    data class Error(
        val message: String,
    ) : SettingsUiState
}

/**
 * ViewModel for Settings screen.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val libraryRepository: LibraryRepository,
        private val homeRepository: HomeRepository,
        private val backupProvider: BackupProvider,
        private val updateRepository: UpdateRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        /** "Backup & restore" section tiles; null until loaded. */
        val backupSummary = MutableStateFlow<BackupSummary?>(null)

        fun loadBackupSummary() {
            viewModelScope.launch { backupSummary.value = backupProvider.summary() }
        }

        /** Re-show the first-launch getting started flow (About section). */
        fun restartGettingStarted() {
            viewModelScope.launch {
                settingsRepository.updateGeneral("gettingStartedCompleted", false)
            }
        }

        /** Manual "Check for updates" (About section): GitHub releases. */
        val updateCheckState: StateFlow<UpdateCheckState> = updateRepository.checkState

        /**
         * Card click: an offered update ([UpdateCheckState.Available]) starts
         * the download, a downloaded one ([UpdateCheckState.Ready]) retries the
         * install; otherwise run a fresh check. Re-checks while a check or
         * download is running are ignored.
         */
        fun onUpdateCardClick() {
            when (val s = updateRepository.checkState.value) {
                is UpdateCheckState.Available -> updateRepository.downloadUpdate()
                is UpdateCheckState.Ready -> updateRepository.installUpdate()
                is UpdateCheckState.Checking,
                is UpdateCheckState.Downloading -> Unit
                else -> updateRepository.checkForUpdates()
            }
        }

        val currentVersionName: String get() = updateRepository.currentVersionName

        fun exportBackup() = backupProvider.exportBackup()

        fun importBackup() = backupProvider.importBackup()

        /** User playlists, for the dual-screen library-slot picker. */
        val playlists: StateFlow<List<PlaylistOption>> = libraryRepository.playlists

        /** Enabled sources, for the dual-screen feed-sources picker. */
        val enabledSources: StateFlow<List<SourceInfo>> = homeRepository.enabledSources

        init {
            // Observe preferences and map to UiState
            viewModelScope.launch {
                settingsRepository.preferences
                    .collect { prefs ->
                        _uiState.value = SettingsUiState.Loaded(prefs = prefs)
                    }
            }
        }

        fun updateAppearance(prefs: AppearancePreferences) {
            viewModelScope.launch {
                settingsRepository.updateAppearance(prefs)
            }
        }

        /** Appearance > Font. */
        fun setFontFamily(family: FontFamily) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                settingsRepository.updateAppearance(p.prefs.appearance.copy(fontFamily = family))
            }
        }

        /** Appearance > Font size scale (1.0 = shipped sizes). */
        fun setFontSizeScale(scale: Float) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                settingsRepository.updateAppearance(p.prefs.appearance.copy(fontSizeScale = scale))
            }
        }

        /** Appearance > Thumbnail tint (ARGB; null = none). */
        fun setThumbnailTint(argb: Long?) {
            viewModelScope.launch { settingsRepository.setThumbnailTint(argb) }
        }

        /** Appearance > Thumbnails > Tint strength (0..1). */
        fun setThumbnailTintStrength(strength: Float) {
            viewModelScope.launch { settingsRepository.setThumbnailTintStrength(strength) }
        }

        /** Appearance > Thumbnails > Tint blend mode ([TintBlendMode] name). */
        fun setThumbnailTintBlendMode(mode: String) {
            viewModelScope.launch { settingsRepository.setThumbnailTintBlendMode(mode) }
        }

        /** Appearance > Thumbnail desaturation (1 = full color, 0 = grayscale). */
        fun setThumbnailSaturation(saturation: Float) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                settingsRepository.updateAppearance(p.prefs.appearance.copy(thumbnailSaturation = saturation))
            }
        }

        /** Appearance > Thumbnail contrast (1 = neutral, 0 = flat gray, 2 = high contrast). */
        fun setThumbnailContrast(contrast: Float) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                settingsRepository.updateAppearance(p.prefs.appearance.copy(thumbnailContrast = contrast))
            }
        }

        /** Save or update a custom theme (Settings > Appearance > Custom themes). */
        fun saveTheme(theme: CustomTheme) {
            viewModelScope.launch { settingsRepository.saveCustomTheme(theme) }
        }

        /** Delete a custom theme (clears it if it is the active theme). */
        fun deleteTheme(id: String) {
            viewModelScope.launch { settingsRepository.deleteCustomTheme(id) }
        }

        /** Set the active custom theme, or null for the default theme. */
        fun setActiveTheme(id: String?) {
            viewModelScope.launch { settingsRepository.setActiveThemeId(id) }
        }

        fun updatePlayback(prefs: PlaybackPreferences) {
            viewModelScope.launch {
                settingsRepository.updatePlayback(prefs)
            }
        }

        /** Toggle queue autoplay (Settings > Content > Video page). */
        fun setAutoplay(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAutoplay(enabled) }
        }

        fun updateGeneral(
            key: String,
            value: Any,
        ) {
            viewModelScope.launch {
                settingsRepository.updateGeneral(key, value)
            }
        }

        fun setDualScreenPages(pages: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenPages(pages) }
        }

        fun setDualScreenVideoTabs(tabs: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenVideoTabs(tabs) }
        }

        fun setDualScreenLibrarySlots(slots: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenLibrarySlots(slots) }
        }

        fun setDualScreenVideoTabOrder(order: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenVideoTabOrder(order) }
        }

        fun setDualScreenPageOrder(order: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenPageOrder(order) }
        }

        fun setDualScreenTopCreatorsScope(scope: String) {
            viewModelScope.launch {
                settingsRepository.updateDualScreenTopCreatorsScope(scope)
            }
        }

        fun setDualScreenDashPageOrder(order: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenDashPageOrder(order) }
        }

        fun setDualScreenFeedSources(ids: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenFeedSources(ids) }
        }

        /** Save the controller (gamepad) settings (Settings > Controller). */
        fun setController(prefs: ControllerPreferences) {
            viewModelScope.launch { settingsRepository.updateControllerSettings(prefs) }
        }

        /** Clear gesture customizations for one player mode (back to defaults). */
        fun resetPlayerGestures(mode: String) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                val empty = PlayerGestureSlotSet()
                settingsRepository.updatePlayerGestures(
                    fullscreen =
                        if (mode == "fullscreen") empty else p.prefs.playerGestures.fullscreen,
                    normal = if (mode == "normal") empty else p.prefs.playerGestures.normal,
                )
            }
        }

        /** Save one cell of the gesture editor (Settings > Gestures). */
        fun setPlayerGesturesCell(
            mode: String,
            slot: String,
            type: String,
            action: GestureAction,
        ) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                val set =
                    if (mode == "normal") p.prefs.playerGestures.normal else p.prefs.playerGestures.fullscreen
                val updated =
                    (when (slot) {
                        "top" -> set.top
                        "bottomLeft" -> set.bottomLeft
                        "bottomCenter" -> set.bottomCenter
                        "bottomRight" -> set.bottomRight
                        else -> emptyMap()
                    }).toMutableMap()
                updated[type] = action
                settingsRepository.updatePlayerGestures(
                    fullscreen =
                        if (mode == "fullscreen") set.updated(slot, updated) else p.prefs.playerGestures.fullscreen,
                    normal =
                        if (mode == "normal") set.updated(slot, updated) else p.prefs.playerGestures.normal,
                )
            }
        }

        private fun PlayerGestureSlotSet.updated(
            slot: String,
            map: Map<String, GestureAction>,
        ): PlayerGestureSlotSet =
            copy(
                top = if (slot == "top") map else top,
                bottomLeft = if (slot == "bottomLeft") map else bottomLeft,
                bottomCenter = if (slot == "bottomCenter") map else bottomCenter,
                bottomRight = if (slot == "bottomRight") map else bottomRight,
            )

        fun setShowWatchedVideos(show: Boolean) {
            viewModelScope.launch { settingsRepository.setShowWatchedVideos(show) }
        }

        fun setShowStartedVideos(show: Boolean) {
            viewModelScope.launch { settingsRepository.setShowStartedVideos(show) }
        }

        fun setHomebrewRecommendations(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setHomebrewRecommendations(enabled) }
        }

        fun setSourceYoutubeFeed(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSourceYoutubeFeed(enabled) }
        }

        fun setSourceSubscriptions(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSourceSubscriptions(enabled) }
        }

        fun setSourceTopCreators(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSourceTopCreators(enabled) }
        }

        fun setProbCuts(cut1: Int, cut2: Int) {
            viewModelScope.launch { settingsRepository.setProbCuts(cut1, cut2) }
        }

        fun setRecency(recency: String) {
            viewModelScope.launch { settingsRepository.setRecency(recency) }
        }

        /** Live per-video hidden/reduced entries (Hidden content subpage). */
        val hiddenVideos: StateFlow<List<HiddenContentEntry>>
            get() = settingsRepository.hiddenVideos

        /** Live per-creator hidden/reduced entries (Hidden content subpage). */
        val hiddenCreators: StateFlow<List<HiddenContentEntry>>
            get() = settingsRepository.hiddenCreators

        /** Hidden content subpage: change a video's spawn probability. */
        fun setHiddenVideoProbability(url: String, probability: Int) {
            viewModelScope.launch {
                val entry = settingsRepository.hiddenVideos.value.firstOrNull { it.url == url }
                settingsRepository.setVideoProbability(url, entry?.name ?: "", probability, entry?.imageUrl ?: "")
            }
        }

        /** Hidden content subpage: change a creator's spawn probability. */
        fun setHiddenCreatorProbability(url: String, probability: Int) {
            viewModelScope.launch {
                val entry = settingsRepository.hiddenCreators.value.firstOrNull { it.url == url }
                settingsRepository.setCreatorProbability(url, entry?.name ?: "", probability, entry?.imageUrl ?: "")
            }
        }

        fun setDockTabOrder(order: List<String>) {
            viewModelScope.launch { settingsRepository.setDockTabOrder(order) }
        }

        fun setDefaultDockTab(key: String) {
            viewModelScope.launch { settingsRepository.setDefaultDockTab(key) }
        }

        fun setSubscriptionsOrder(order: String) {
            viewModelScope.launch { settingsRepository.setSubscriptionsOrder(order) }
        }

        fun setLibrarySectionOrder(order: List<String>) {
            viewModelScope.launch { settingsRepository.updateLibrarySectionOrder(order) }
        }

        fun setLibrarySectionsEnabled(ids: List<String>) {
            viewModelScope.launch { settingsRepository.updateLibrarySectionsEnabled(ids) }
        }

        fun resetToDefaults() {
            viewModelScope.launch {
                settingsRepository.resetToDefaults()
            }
        }
    }
