package com.tsutsen.platformplayer.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * One controller binding: the bound key, and the name of the input device
 * it was bound with (display only — events from any device still match).
 */
@Serializable
data class ControllerBinding(
    val keyCode: Int,
    val deviceName: String? = null,
)

/**
 * Player controller (gamepad / TV remote) settings (Settings > Controller).
 *
 * [mappings] maps a stable action id
 * ([com.tsutsen.platformplayer.core.model.PlayerControllerActions]) to the
 * binding the user chose. An absent action falls back to the action's
 * [com.tsutsen.platformplayer.core.model.PlayerControllerActions.defaultKeyCode].
 */
data class ControllerPreferences(
    val enabled: Boolean = false,
    val mappings: Map<String, ControllerBinding> = emptyMap(),
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
)
