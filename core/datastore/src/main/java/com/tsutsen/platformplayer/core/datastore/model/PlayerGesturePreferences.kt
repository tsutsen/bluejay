package com.tsutsen.platformplayer.core.datastore.model

/**
 * Per-slot player gesture assignments: each surface slot (top / bottom-left /
 * bottom-center / bottom-right) maps the four recognised gesture types to a
 * flat action id (see [com.tsutsen.platformplayer.core.model.PlayerGestures]
 * for the id catalog). Empty string = unassigned (no-op).
 */
data class PlayerGesturePreferences(
    val top: Map<String, String> = emptyMap(),
    val bottomLeft: Map<String, String> = emptyMap(),
    val bottomCenter: Map<String, String> = emptyMap(),
    val bottomRight: Map<String, String> = emptyMap(),
)
