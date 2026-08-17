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
        val comments: List<CommentItem> = emptyList(),
        val videoQualities: List<Int> = emptyList(),
        val subtitleLanguages: List<String> = emptyList(),
        val selectedQuality: String = "Auto",
        val selectedSubtitle: String = "Auto"
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
    private val commentRepository: CommentRepository,
    private val historyTracker: HistoryTracker
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Initial)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // Preserve comments across repository state emissions
    private var cachedComments: List<CommentItem> = emptyList()

    init {
        // Observe repository player state and map to UiState
        // Position updates are now handled by the repository's position ticker
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
                        comments = cachedComments,
                        videoQualities = playerState.videoQualities,
                        subtitleLanguages = playerState.subtitleLanguages,
                        selectedQuality = playerState.selectedQuality,
                        selectedSubtitle = playerState.selectedSubtitle
                    )
                    
                    // Track playback history
                    val video = playerState.currentVideo
                    if (video != null) {
                        historyTracker.trackPlayback(
                            contentUrl = video.url,
                            title = video.title,
                            author = video.author?.name,
                            thumbnailUrl = video.thumbnailUrl,
                        )
                    }
                }
        }
    }

    fun play(videoId: String) {
        viewModelScope.launch {
            // Reset comments for new video
            cachedComments = emptyList()
            playerRepository.play(videoId)
            // Track in history
            historyTracker.trackPlayback(
                contentUrl = videoId,
                title = videoId,
                author = null,
                thumbnailUrl = null
            )
            // Fetch comments after video starts playing
            fetchComments(videoId)
        }
    }

    private suspend fun fetchComments(contentUrl: String) {
        try {
            val comments = withContext(Dispatchers.IO) {
                commentRepository.getComments(contentUrl)
            }
            
            cachedComments = comments
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
                    cachedComments = cachedComments + moreComments
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

    /** Seek relative to current ExoPlayer position (avoids stale UI state). */
    fun seekBy(deltaMs: Long) {
        val current = playerRepository.exoPlayer?.currentPosition ?: return
        viewModelScope.launch {
            playerRepository.seekTo((current + deltaMs).coerceIn(0, playerRepository.exoPlayer?.duration ?: Long.MAX_VALUE))
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

    fun setSubtitle(selection: String) {
        viewModelScope.launch {
            playerRepository.setSubtitle(selection)
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
