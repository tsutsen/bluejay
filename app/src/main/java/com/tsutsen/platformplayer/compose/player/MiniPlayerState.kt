/*
 * MiniPlayerState
 *
 * State for the floating mini player overlay.
 * Tracks position and visibility.
 */

package com.tsutsen.platformplayer.compose.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * State for the floating mini player.
 */
object MiniPlayerState {
    
    var isVisible by mutableStateOf(false)
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
    
    fun show() {
        isVisible = true
    }
    
    fun hide() {
        isVisible = false
    }
}
