package com.tsutsen.platformplayer.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only remote control for the player, used to drive the on-device
 * flow via adb without UI coordinates:
 *
 *     adb shell am broadcast -a com.tsutsen.platformplayer.d.DEBUG_PLAYER_CLOSE
 *
 * Triggers the same path as the player's close button (releases the
 * controller, stops PlayerService, releases the ExoPlayer) so the
 * close-then-replay notification flow can be tested deterministically.
 * Ignored in release builds.
 */
class DebugPlayerControlReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (!com.tsutsen.platformplayer.BuildConfig.DEBUG) return
        when (intent.action) {
            ACTION_CLOSE -> {
                Log.i(TAG, "DebugPlayerControlReceiver: closing player")
                scope.launch {
                    (context.applicationContext as com.tsutsen.platformplayer.PlatformPlayerApp)
                        .playerRepository()
                        .close()
                }
            }
        }
    }

    companion object {
        const val ACTION_CLOSE = "com.tsutsen.platformplayer.d.DEBUG_PLAYER_CLOSE"
        private const val TAG = "DebugPlayerControl"
    }
}
