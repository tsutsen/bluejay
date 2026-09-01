package com.tsutsen.platformplayer.core.datastore.model

/**
 * Player controller (gamepad / TV remote) settings (Settings > Controller).
 *
 * [mappings] maps a stable action id
 * ([com.tsutsen.platformplayer.core.model.PlayerControllerActions]) to the
 * Android [android.view.KeyEvent] keyCode the user bound to it. An absent
 * action falls back to the action's [com.tsutsen.platformplayer.core.model.PlayerControllerActions.defaultKeyCode].
 */
data class ControllerPreferences(
    val enabled: Boolean = false,
    val mappings: Map<String, Int> = emptyMap(),
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
)
