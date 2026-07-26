package com.tsutsen.platformplayer.feature.player.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val error: String?,
        val isLoading: Boolean = false,
        val isCompleted: Boolean = false,
        val comments: List<CommentItem> = emptyList()
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
    private val playerRepository: PlayerRepository,
    private val commentRepository: CommentRepository
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
                        error = playerState.error,
                        isLoading = playerState.isLoading,
                        isCompleted = playerState.isCompleted,
                        comments = playerState.comments
                    )
                }
        }
        
        // Continuously update current position for smooth timeline
        viewModelScope.launch {
            while (true) {
                val player = playerRepository.exoPlayer
                if (player != null && player.isPlaying) {
                    val position = player.currentPosition
                    val currentState = _uiState.value
                    if (currentState is PlayerUiState.Loaded && currentState.currentPositionMs != position) {
                        _uiState.value = currentState.copy(currentPositionMs = position)
                    }
                }
                delay(100)
            }
        }
    }

    fun play(videoId: String) {
        viewModelScope.launch {
            playerRepository.play(videoId)
            // Fetch comments after video starts playing
            fetchComments(videoId)
        }
    }

    private suspend fun fetchComments(contentUrl: String) {
        try {
            val comments = withContext(Dispatchers.IO) {
                commentRepository.getComments(contentUrl)
            }
            
            _uiState.value = when (val state = _uiState.value) {
                is PlayerUiState.Loaded -> state.copy(comments = comments)
                else -> state
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Failed to fetch comments", e)
        }
    }

    fun loadMoreComments(contentUrl: String) {
        viewModelScope.launch {
            try {
                val moreComments = withContext(Dispatchers.IO) {
                    commentRepository.loadMoreComments(contentUrl)
                }
                
                if (moreComments.isNotEmpty()) {
                    _uiState.value = when (val state = _uiState.value) {
                        is PlayerUiState.Loaded -> {
                            val updatedComments = state.comments + moreComments
                            state.copy(comments = updatedComments)
                        }
                        else -> state
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to load more comments", e)
            }
        }
    }

    fun getPlayer() = playerRepository

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
