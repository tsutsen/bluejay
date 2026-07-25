package com.tsutsen.platformplayer.core.data.repository

import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {

    val playerState: StateFlow<PlayerState>

    /**
     * Get the ExoPlayer instance managed by this repository.
     * Used by PlayerScreen to bind a PlayerView to the player.
     */
    val exoPlayer: ExoPlayer?

    suspend fun play(videoId: String)
    suspend fun pause()
    suspend fun resume()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun setBrightness(brightness: Float)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun setVideoQuality(quality: String)
    suspend fun toggleFullscreen()
    suspend fun minimize()
    suspend fun exitFullscreen()
    suspend fun exitMiniPlayer()
    suspend fun close()

    val mediaSessionToken: android.media.session.MediaSession.Token?
        get() = null
}
