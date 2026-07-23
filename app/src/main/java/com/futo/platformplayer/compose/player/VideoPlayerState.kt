/*
 * VideoPlayerState
 *
 * Clean state machine for the video player with two states:
 * - FULL: Player embedded in video detail page
 * - MINI: Player floating as mini player
 *
 * Transitions between states are smooth animations.
 */

package com.futo.platformplayer.compose.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails

/**
 * Video player state machine.
 */
object VideoPlayerState {
    
    /**
     * Video player states.
     */
    enum class PlayerState {
        /** Player embedded in video detail page */
        FULL,
        /** Player floating as mini player */
        MINI
    }
    
    var state by mutableStateOf(PlayerState.FULL)
    var currentVideo: IPlatformVideoDetails? = null
    var playbackPosition by mutableStateOf(0L)
    var exoPlayer: ExoPlayer? = null
    
    /**
     * Set the current video
     */
    fun setVideo(video: IPlatformVideoDetails) {
        currentVideo = video
        state = PlayerState.FULL
    }
    
    /**
     * Minimize the player
     */
    fun minimize() {
        state = PlayerState.MINI
    }
    
    /**
     * Expand the player back to full size
     */
    fun expand() {
        state = PlayerState.FULL
    }
    
    /**
     * Hide the player completely (release resources)
     */
    fun hide() {
        exoPlayer?.release()
        exoPlayer = null
        currentVideo = null
        playbackPosition = 0L
        state = PlayerState.FULL
    }
}
