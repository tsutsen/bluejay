/*
 * MiniPlayerState
 *
 * Global state for the floating mini player overlay.
 * Persists across navigation — mini player floats on top of whatever screen is visible.
 */

package com.futo.platformplayer.compose.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails

/**
 * Global state for the floating mini player overlay.
 * The mini player is a draggable, floating box that overlays any screen.
 */
object MiniPlayerState {
    var isMiniPlayerActive by mutableStateOf(false)
    var isExpanded by mutableStateOf(true)
    var currentVideo by mutableStateOf<IPlatformVideoDetails?>(null)
    var playbackPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    
    // Floating position (in pixels)
    var positionX by mutableStateOf(50f)
    var positionY by mutableStateOf(50f)
    
    private var prefs: SharedPreferences? = null
    
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("mini_player_prefs", Context.MODE_PRIVATE)
            positionX = prefs!!.getFloat("pos_x", 50f)
            positionY = prefs!!.getFloat("pos_y", 50f)
        }
    }
    
    fun savePosition() {
        prefs?.edit()?.putFloat("pos_x", positionX)?.putFloat("pos_y", positionY)?.apply()
    }
    
    fun show(video: IPlatformVideoDetails, position: Long = 0L) {
        currentVideo = video
        playbackPosition = position
        isMiniPlayerActive = true
        isExpanded = true
    }
    
    fun hide() {
        isMiniPlayerActive = false
        isExpanded = false
        currentVideo = null
        playbackPosition = 0L
        duration = 0L
    }
    
    fun collapse() {
        isExpanded = false
    }
    
    fun expand() {
        isExpanded = true
    }
    
    fun toggleExpanded() {
        isExpanded = !isExpanded
    }
}
