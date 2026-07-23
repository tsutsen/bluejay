/*
 * VideoPlayerGlobalState
 *
 * Global state for the video player that persists across navigation.
 * The ExoPlayer instance lives here and survives when navigating away from
 * the video player scene. This enables the floating mini player to keep
 * playing while the user browses other tabs.
 */

package com.futo.platformplayer.compose.player

import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails

/**
 * Global state for the video player.
 * The ExoPlayer instance persists across navigation, allowing the mini player
 * to keep playing while the user browses other tabs.
 */
object VideoPlayerGlobalState {
    var exoPlayer: ExoPlayer? = null
    var currentVideo: IPlatformVideoDetails? = null
    var isMiniPlayerActive = false
    var isExpanded = true
    
    /**
     * Set the current video and create a new ExoPlayer if needed
     */
    fun setVideo(video: IPlatformVideoDetails, player: ExoPlayer) {
        exoPlayer = player
        currentVideo = video
        isMiniPlayerActive = false
        isExpanded = true
    }
    
    /**
     * Collapse to mini player
     */
    fun collapse() {
        isMiniPlayerActive = true
        isExpanded = false
    }
    
    /**
     * Expand back to full player
     */
    fun expand() {
        isMiniPlayerActive = false
        isExpanded = true
    }
    
    /**
     * Hide the mini player and release the ExoPlayer
     */
    fun hide() {
        exoPlayer?.release()
        exoPlayer = null
        currentVideo = null
        isMiniPlayerActive = false
        isExpanded = true
    }
    
    /**
     * Get the current playback position
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
    
    /**
     * Save position for mini player
     */
    fun savePosition() {
        // Position is saved via MiniPlayerState.savePosition()
    }
}
