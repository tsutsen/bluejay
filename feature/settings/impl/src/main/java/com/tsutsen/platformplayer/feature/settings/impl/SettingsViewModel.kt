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
        val playback: PlaybackPreferences,
        val language: String,
        val enableNotifications: Boolean,
        val enableBackgroundPlayback: Boolean,
        val enablePictureInPicture: Boolean,
        val confirmExit: Boolean,
        val enableDeveloperOptions: Boolean,
        val gridColumns: Int,
        val showRecommendedVideos: Boolean,
        val showComments: Boolean,
        val defaultResolution: String,
        val rememberSubtitleState: Boolean,
        val preferredSubtitleLanguage: String,
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
                                playback = prefs.playback,
                                language = prefs.language,
                                enableNotifications = prefs.enableNotifications,
                                enableBackgroundPlayback = prefs.enableBackgroundPlayback,
                                enablePictureInPicture = prefs.enablePictureInPicture,
                                confirmExit = prefs.confirmExit,
                                enableDeveloperOptions = prefs.enableDeveloperOptions,
                                gridColumns = prefs.gridColumns,
                                showRecommendedVideos = prefs.showRecommendedVideos,
                                showComments = prefs.showComments,
                                defaultResolution = prefs.defaultResolution,
                                rememberSubtitleState = prefs.rememberSubtitleState,
                                preferredSubtitleLanguage = prefs.preferredSubtitleLanguage,
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

        fun resetToDefaults() {
            viewModelScope.launch {
                settingsRepository.resetToDefaults()
            }
        }
    }
