package com.tsutsen.platformplayer.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.*
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
    data class Loaded(
        val appearance: AppearancePreferences,
        val subtitle: SubtitlePreferences,
        val defaultPlaybackSpeed: Float,
        val defaultSpeedup: Float,
        val speedupSensitivity: Float,
        val defaultVideoResolution: String,
        val defaultDownloadResolution: String,
        val enableDeveloperOptions: Boolean,
        val librarySectionOrder: List<String>,
        val dualScreen: Boolean,
        val dualScreenPages: List<String>,
        val dualScreenVideoTabs: List<String>,
        val dualScreenVideoTabOrder: List<String>,
        val dualScreenPageOrder: List<String>,
        val dualScreenFeedSources: List<String>,
        val dualScreenLibrarySlots: List<String>,
        val gridColumns: Int,
        val showRecommendedVideos: Boolean,
        val showComments: Boolean,
        val autoUpdatePlugins: Boolean,
        val playerGestures: PlayerGesturePreferences,
        val jumpStepSeconds: Int = 5,
        val controller: ControllerPreferences = ControllerPreferences(),
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        /** User playlists, for the dual-screen library-slot picker. */
        val playlists: StateFlow<List<PlaylistOption>> = libraryRepository.playlists

        /** Enabled sources, for the dual-screen feed-sources picker. */
        val enabledSources: StateFlow<List<SourceInfo>> = homeRepository.enabledSources

        init {
            // Observe preferences and map to UiState
            viewModelScope.launch {
                settingsRepository.preferences
                    .collect { prefs ->
                        _uiState.value =
                            SettingsUiState.Loaded(
                                appearance = prefs.appearance,
                                subtitle = prefs.subtitle,
                                defaultPlaybackSpeed = prefs.defaultPlaybackSpeed,
                                defaultSpeedup = prefs.defaultSpeedup,
                                speedupSensitivity = prefs.speedupSensitivity,
                                defaultVideoResolution = prefs.defaultVideoResolution,
                                defaultDownloadResolution = prefs.defaultDownloadResolution,
                                enableDeveloperOptions = prefs.enableDeveloperOptions,
                                librarySectionOrder = prefs.librarySectionOrder,
                                dualScreen = prefs.dualScreen,
                                dualScreenPages = prefs.dualScreenPages,
                                dualScreenVideoTabs = prefs.dualScreenVideoTabs,
                                dualScreenVideoTabOrder = prefs.dualScreenVideoTabOrder,
                                dualScreenPageOrder = prefs.dualScreenPageOrder,
                                dualScreenFeedSources = prefs.dualScreenFeedSources,
                                dualScreenLibrarySlots = prefs.dualScreenLibrarySlots,
                                gridColumns = prefs.gridColumns,
                                showRecommendedVideos = prefs.showRecommendedVideos,
                                showComments = prefs.showComments,
                                autoUpdatePlugins = prefs.autoUpdatePlugins,
                                playerGestures = prefs.playerGestures,
                                jumpStepSeconds = prefs.jumpStepSeconds,
                                controller = prefs.controller,
                            )
                    }
            }
        }

        fun updateAppearance(prefs: AppearancePreferences) {
            viewModelScope.launch {
                settingsRepository.updateAppearance(prefs)
            }
        }

        fun updatePlayback(prefs: PlaybackPreferences) {
            viewModelScope.launch {
                settingsRepository.updatePlayback(prefs)
            }
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
                        if (mode == "fullscreen") empty else p.playerGestures.fullscreen,
                    normal = if (mode == "normal") empty else p.playerGestures.normal,
                )
            }
        }

        /** Save one cell of the gesture editor (Settings > Gestures). */
        fun setPlayerGesturesCell(
            mode: String,
            slot: String,
            type: String,
            action: String,
        ) {
            viewModelScope.launch {
                val p = _uiState.value as? SettingsUiState.Loaded ?: return@launch
                val set =
                    if (mode == "normal") p.playerGestures.normal else p.playerGestures.fullscreen
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
                        if (mode == "fullscreen") set.updated(slot, updated) else p.playerGestures.fullscreen,
                    normal =
                        if (mode == "normal") set.updated(slot, updated) else p.playerGestures.normal,
                )
            }
        }

        private fun PlayerGestureSlotSet.updated(
            slot: String,
            map: Map<String, String>,
        ): PlayerGestureSlotSet =
            copy(
                top = if (slot == "top") map else top,
                bottomLeft = if (slot == "bottomLeft") map else bottomLeft,
                bottomCenter = if (slot == "bottomCenter") map else bottomCenter,
                bottomRight = if (slot == "bottomRight") map else bottomRight,
            )

        fun setLibrarySectionOrder(order: List<String>) {
            viewModelScope.launch { settingsRepository.updateLibrarySectionOrder(order) }
        }

        fun resetToDefaults() {
            viewModelScope.launch {
                settingsRepository.resetToDefaults()
            }
        }
    }
