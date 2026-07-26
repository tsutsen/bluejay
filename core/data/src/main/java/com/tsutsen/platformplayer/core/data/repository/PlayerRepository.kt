package com.tsutsen.platformplayer.core.data.repository

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
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

/**
 * Result of resolving a content URL — contains both the MediaSource for playback
 * and the VideoDetails for populating the video details page (title, author, etc.).
 */
data class ResolutionResult(
    val mediaSource: MediaSource?,
    val videoDetails: VideoDetails?
)

/**
 * Interface for resolving content URLs to MediaSources.
 * Implementation depends on the engine plugin being available.
 * Returns both a MediaSource for playback and IPlatformVideoDetails for the UI.
 */
interface VideoUrlResolver {
    suspend fun resolve(contentUrl: String): ResolutionResult
}
