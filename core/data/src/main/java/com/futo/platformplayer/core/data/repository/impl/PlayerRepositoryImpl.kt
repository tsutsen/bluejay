package com.futo.platformplayer.core.data.repository.impl

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.core.data.repository.PlayerRepository
import com.futo.platformplayer.core.model.ContentItem
import com.futo.platformplayer.core.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlayerRepository implementation.
 * TODO: Phase 4 - Replace with direct ExoPlayer usage.
 */
@Singleton
class PlayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerRepository {

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    override suspend fun play(videoId: String) {
        _playerState.update { it.copy(isPlaying = true, currentVideo = ContentItem(
            id = videoId, url = videoId, title = "Loading...", author = null,
            thumbnailUrl = null, contentType = com.futo.platformplayer.core.model.ContentType.VIDEO
        )) }
    }

    override suspend fun pause() {
        _playerState.update { it.copy(isPlaying = false) }
    }

    override suspend fun resume() {
        _playerState.update { it.copy(isPlaying = true) }
    }

    override suspend fun seekTo(positionMs: Long) {
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    override suspend fun setVolume(volume: Float) {
        _playerState.update { it.copy(volume = volume) }
    }

    override suspend fun setBrightness(brightness: Float) {
        _playerState.update { it.copy(brightness = brightness) }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    override suspend fun setVideoQuality(quality: String) {
        // TODO: Implement
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
}
