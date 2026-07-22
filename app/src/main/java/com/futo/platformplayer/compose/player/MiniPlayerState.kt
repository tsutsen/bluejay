/*
 * MiniPlayerState
 *
 * Global state for the mini player that persists across navigation.
 * When the video is minimized, it shrinks to a small bar at the bottom of the screen
 * and the user can navigate to other tabs or even switch apps.
 */

package com.futo.platformplayer.compose.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails

/**
 * Global state for the mini player.
 * This persists across navigation and allows the video to keep playing
 * while the user navigates to other tabs or switches apps.
 */
object MiniPlayerState {
    var isMiniPlayerActive by mutableStateOf(false)
    var currentVideo by mutableStateOf<IPlatformVideoDetails?>(null)
    var playbackPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    
    /**
     * Show the mini player with the given video
     */
    fun show(video: IPlatformVideoDetails, position: Long = 0L) {
        currentVideo = video
        playbackPosition = position
        isMiniPlayerActive = true
    }
    
    /**
     * Hide the mini player
     */
    fun hide() {
        isMiniPlayerActive = false
        currentVideo = null
        playbackPosition = 0L
        duration = 0L
    }
    
    /**
     * Toggle the mini player state
     */
    fun toggle() {
        isMiniPlayerActive = !isMiniPlayerActive
    }
}
