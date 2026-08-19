package com.tsutsen.platformplayer.feature.player.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import com.tsutsen.platformplayer.core.model.VideoChapter
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
        val selectedSubtitle: String = "Auto",
        val subtitleText: String = "",
        val chapters: List<VideoChapter> = emptyList(),
        val recommendations: List<Card> = emptyList(),
        val showComments: Boolean = true,
        val showRecommended: Boolean = true,
    ) : PlayerUiState

    data object Initial : PlayerUiState

    data class Error(
        val message: String,
    ) : PlayerUiState
}

/**
 * ViewModel for the Video Player.
 * Bridges between PlayerRepository (data layer) and PlayerScreen (UI).
 */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val playerRepository: PlayerRepository,
        private val commentRepository: CommentRepository,
        private val settingsRepository: SettingsRepository,
        private val historyTracker: HistoryTracker,
        private val libraryRepository: LibraryRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Initial)
        val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Lazily,
                    settingsRepository.preferences.value.gridColumns,
                )

        init {
            // Observe repository player state and map to UiState
            // Position updates are now handled by the repository's position ticker
            viewModelScope.launch {
                playerRepository.playerState
                    .collect { playerState ->
                        _uiState.value =
                            PlayerUiState.Loaded(
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
                                // Comments/recommendations/chapters live in the shared
                                // PlayerState — PlayerRepository.play() fetches them for
                                // every play path (main screen AND companion screen),
                                // so both displays always show the same data.
                                comments = playerState.comments,
                                videoQualities = playerState.videoQualities,
                                subtitleLanguages = playerState.subtitleLanguages,
                                selectedQuality = playerState.selectedQuality,
                                selectedSubtitle = playerState.selectedSubtitle,
                                subtitleText = playerState.subtitleText,
                                chapters = playerState.chapters,
                                recommendations = playerState.recommendations,
                                showComments = settingsRepository.preferences.value.showComments,
                                showRecommended =
                                    settingsRepository.preferences.value
                                        .showRecommendedVideos,
                            )

                        // Track playback history. The player's real duration
                        // backfills history + library rows that stored none,
                        // so library cards show a duration after one play.
                        val video = playerState.currentVideo
                        if (video != null) {
                            historyTracker.trackPlayback(
                                contentUrl = video.url,
                                title = video.title,
                                author = video.author?.name,
                                authorUrl = video.author?.url?.takeIf { it.isNotEmpty() },
                                thumbnailUrl = video.thumbnailUrl,
                                totalDurationMs = playerState.durationMs,
                                viewCount = video.viewCount,
                            )
                            if (playerState.durationMs > 0) {
                                libraryRepository.backfillDuration(video.url, playerState.durationMs)
                            }
                        }
                    }
            }
        }

        fun play(videoId: String) {
            viewModelScope.launch {
                // PlayerRepository.play() clears the previous video's extras and
                // fetches the new one's (comments/recs/chapters) — the single
                // orchestration point shared with the companion screen.
                playerRepository.play(videoId)
                // Track in history
                historyTracker.trackPlayback(
                    contentUrl = videoId,
                    title = videoId,
                    author = null,
                    thumbnailUrl = null,
                )
            }
        }

        fun loadMoreComments(contentUrl: String) {
            viewModelScope.launch {
                // Guard: a faster switch to a new video supersedes this request.
                if (playerRepository.playerState.value.currentVideo?.url != contentUrl) {
                    return@launch
                }
                try {
                    val moreComments =
                        withContext(Dispatchers.IO) {
                            commentRepository.loadMoreComments(contentUrl)
                        }

                    if (moreComments.isNotEmpty()) {
                        val current = playerRepository.playerState.value
                        playerRepository.setVideoExtras(
                            current.comments + moreComments,
                            current.recommendations,
                        )
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
