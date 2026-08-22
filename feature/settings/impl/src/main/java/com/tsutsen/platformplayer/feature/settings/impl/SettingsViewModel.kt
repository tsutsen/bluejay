package com.tsutsen.platformplayer.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.*
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
        val enableDeveloperOptions: Boolean,
        val dualScreen: Boolean,
        val dualScreenPages: List<String>,
        val dualScreenVideoTabs: List<String>,
        val dualScreenLibrarySections: List<String>,
        val gridColumns: Int,
        val showRecommendedVideos: Boolean,
        val showComments: Boolean,
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
                                enableDeveloperOptions = prefs.enableDeveloperOptions,
                                dualScreen = prefs.dualScreen,
                                dualScreenPages = prefs.dualScreenPages,
                                dualScreenVideoTabs = prefs.dualScreenVideoTabs,
                                dualScreenLibrarySections = prefs.dualScreenLibrarySections,
                                gridColumns = prefs.gridColumns,
                                showRecommendedVideos = prefs.showRecommendedVideos,
                                showComments = prefs.showComments,
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

        fun setDualScreenLibrarySections(sectionIds: List<String>) {
            viewModelScope.launch { settingsRepository.updateDualScreenLibrarySections(sectionIds) }
        }

        fun resetToDefaults() {
            viewModelScope.launch {
                settingsRepository.resetToDefaults()
            }
        }
    }
