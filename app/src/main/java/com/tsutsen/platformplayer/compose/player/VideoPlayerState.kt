/*
 * VideoPlayerState
 *
 * Clean state machine for the video player with three states:
 * - FULL: Player in fullscreen mode
 * - DEFAULT: Player embedded in video detail page (normal mode)
 * - MINI: Player floating as mini player
 *
 * Transitions between states are smooth animations.
 */

package com.tsutsen.platformplayer.compose.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails

/**
 * Video player state machine.
 */
object VideoPlayerState {
    
    /**
     * Video player states.
     */
    enum class PlayerState {
        /** Player in fullscreen mode */
        FULL,
        /** Player embedded in video detail page (normal mode) */
        DEFAULT,
        /** Player floating as mini player */
        MINI
    }
    
    var state by mutableStateOf(PlayerState.DEFAULT)
    var currentVideo: IPlatformVideoDetails? = null
    var playbackPosition by mutableStateOf(0L)
    var exoPlayer: ExoPlayer? = null
    
    /**
     * Set the current video
     */
    fun setVideo(video: IPlatformVideoDetails) {
        currentVideo = video
        state = PlayerState.DEFAULT
    }
    
    /**
     * Enter fullscreen mode
     */
    fun enterFullscreen() {
        state = PlayerState.FULL
    }
    
    /**
     * Exit fullscreen mode (back to default)
     */
    fun exitFullscreen() {
        state = PlayerState.DEFAULT
    }
    
    /**
     * Minimize the player
     */
    fun minimize() {
        state = PlayerState.MINI
    }
    
    /**
     * Expand the player back to default size
     */
    fun expand() {
        state = PlayerState.DEFAULT
    }
    
    /**
     * Hide the player completely (release resources)
     */
    fun hide() {
        exoPlayer?.release()
        exoPlayer = null
        currentVideo = null
        playbackPosition = 0L
        state = PlayerState.DEFAULT
    }
}
