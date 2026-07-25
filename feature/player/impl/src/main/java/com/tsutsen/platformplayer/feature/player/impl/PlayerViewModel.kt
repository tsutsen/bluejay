package com.tsutsen.platformplayer.feature.player.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MVI state for the Video Player screen.
 */
sealed interface PlayerUiState {
    data class Loaded(
        val isPlaying: Boolean,
        val currentPositionMs: Long,
        val durationMs: Long,
        val volume: Float,
        val brightness: Float,
        val playbackSpeed: Float,
        val isFullscreen: Boolean,
        val isMinimized: Boolean,
        val currentVideo: ContentItem?,
        val queue: List<ContentItem>,
        val selectedIndex: Int,
        val error: String?
    ) : PlayerUiState

    data object Initial : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

/**
 * ViewModel for the Video Player.
 * Bridges between PlayerRepository (data layer) and PlayerScreen (UI).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Initial)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        // Observe repository player state and map to UiState
        viewModelScope.launch {
            playerRepository.playerState
                .collect { playerState ->
                    _uiState.value = PlayerUiState.Loaded(
                        isPlaying = playerState.isPlaying,
                        currentPositionMs = playerState.currentPositionMs,
                        durationMs = playerState.durationMs,
                        volume = playerState.volume,
                        brightness = playerState.brightness,
                        playbackSpeed = playerState.playbackSpeed,
                        isFullscreen = playerState.isFullscreen,
                        isMinimized = playerState.isMinimized,
                        currentVideo = playerState.currentVideo,
                        queue = playerState.queue,
                        selectedIndex = playerState.selectedIndex,
                        error = playerState.error
                    )
                }
        }
    }

    fun play(videoId: String) {
        viewModelScope.launch {
            playerRepository.play(videoId)
        }
    }

    fun pause() {
        viewModelScope.launch {
            playerRepository.pause()
        }
    }

    fun resume() {
        viewModelScope.launch {
            playerRepository.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playerRepository.seekTo(positionMs)
        }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch {
            playerRepository.setVolume(volume)
        }
    }

    fun setBrightness(brightness: Float) {
        viewModelScope.launch {
            playerRepository.setBrightness(brightness)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            playerRepository.setPlaybackSpeed(speed)
        }
    }

    fun setVideoQuality(quality: String) {
        viewModelScope.launch {
            playerRepository.setVideoQuality(quality)
        }
    }

    fun toggleFullscreen() {
        viewModelScope.launch {
            playerRepository.toggleFullscreen()
        }
    }

    fun minimize() {
        viewModelScope.launch {
            playerRepository.minimize()
        }
    }

    fun exitFullscreen() {
        viewModelScope.launch {
            playerRepository.exitFullscreen()
        }
    }

    fun exitMiniPlayer() {
        viewModelScope.launch {
            playerRepository.exitMiniPlayer()
        }
    }

    fun close() {
        viewModelScope.launch {
            playerRepository.close()
        }
    }

    fun skipNext() {
        viewModelScope.launch {
            // TODO: Implement queue navigation
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            // TODO: Implement queue navigation
        }
    }

    fun toggleReplay() {
        viewModelScope.launch {
            // TODO: Implement replay toggle
        }
    }
}
