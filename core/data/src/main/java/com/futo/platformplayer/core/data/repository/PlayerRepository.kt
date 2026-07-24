package com.futo.platformplayer.core.data.repository

import com.futo.platformplayer.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {

    val playerState: StateFlow<PlayerState>

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

    val mediaSessionToken: android.media.session.MediaSession.Token?
        get() = null
}
