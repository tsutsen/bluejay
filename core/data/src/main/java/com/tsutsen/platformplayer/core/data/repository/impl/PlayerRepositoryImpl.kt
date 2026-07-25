package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlayerRepository implementation with actual ExoPlayer usage.
 */
@Singleton
class PlayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerRepository {

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playerState.update { it.copy(currentPositionMs = newPosition.positionMs) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playerState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }
    }

    override suspend fun play(videoId: String) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
            exoPlayer?.addListener(playerListener)
        }

        val mediaItem = MediaItem.fromUri(videoId)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true

        _playerState.update {
            it.copy(
                isPlaying = true,
                currentVideo = ContentItem(
                    id = videoId,
                    url = videoId,
                    title = "Loading...",
                    author = null,
                    thumbnailUrl = null,
                    contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO
                )
            )
        }
    }

    override suspend fun pause() {
        exoPlayer?.playWhenReady = false
        _playerState.update { it.copy(isPlaying = false) }
    }

    override suspend fun resume() {
        exoPlayer?.playWhenReady = true
        _playerState.update { it.copy(isPlaying = true) }
    }

    override suspend fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    override suspend fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        _playerState.update { it.copy(volume = volume) }
    }

    override suspend fun setBrightness(brightness: Float) {
        // Brightness is controlled by the activity/window, not ExoPlayer directly
        _playerState.update { it.copy(brightness = brightness) }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = exoPlayer?.playbackParameters?.withSpeed(speed)
            ?: androidx.media3.common.PlaybackParameters(speed)
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    override suspend fun setVideoQuality(quality: String) {
        // TODO: Implement quality selection based on available tracks
    }

    override suspend fun toggleFullscreen() {
        _playerState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    override suspend fun minimize() {
        _playerState.update { it.copy(isMinimized = true, isFullscreen = false) }
    }

    override suspend fun exitFullscreen() {
        _playerState.update { it.copy(isFullscreen = false) }
    }

    override suspend fun exitMiniPlayer() {
        _playerState.update { it.copy(isMinimized = false, isFullscreen = false) }
    }

    override suspend fun close() {
        exoPlayer?.release()
        exoPlayer = null
        _playerState.update {
            PlayerState(
                isPlaying = false,
                isMinimized = false,
                isFullscreen = false,
                currentVideo = null,
                currentPositionMs = 0L,
                durationMs = 0L,
                volume = 1f,
                brightness = 1f,
                playbackSpeed = 1f
            )
        }
    }
}
