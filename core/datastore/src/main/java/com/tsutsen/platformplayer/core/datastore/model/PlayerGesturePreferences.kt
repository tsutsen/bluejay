package com.tsutsen.platformplayer.core.datastore.model

/**
 * Per-slot player gesture assignments for one player mode: each surface slot
 * (top / bottom-left / bottom-center / bottom-right) maps the four
 * recognised gesture types to a flat action id (see
 * [com.tsutsen.platformplayer.core.model.PlayerGestures] for the id
 * catalog). Empty string = unassigned (no-op). An absent key = "not
 * customized, use the canonical default".
 */
data class PlayerGestureSlotSet(
    val top: Map<String, String> = emptyMap(),
    val bottomLeft: Map<String, String> = emptyMap(),
    val bottomCenter: Map<String, String> = emptyMap(),
    val bottomRight: Map<String, String> = emptyMap(),
) {
    /** True when the user customized any cell (vs. canonical defaults). */
    val isCustomized: Boolean
        get() =
            top.isNotEmpty() ||
            bottomLeft.isNotEmpty() ||
            bottomCenter.isNotEmpty() ||
            bottomRight.isNotEmpty()
}

/** Per-mode gesture preferences (fullscreen + normal player). */
data class PlayerGesturePreferences(
    val fullscreen: PlayerGestureSlotSet = PlayerGestureSlotSet(),
    val normal: PlayerGestureSlotSet = PlayerGestureSlotSet(),
) {
    val isCustomized: Boolean
        get() = fullscreen.isCustomized || normal.isCustomized
}
