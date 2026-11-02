package com.tsutsen.platformplayer.core.datastore.model

import com.tsutsen.platformplayer.core.model.GestureAction

/**
 * Per-slot player gesture assignments for one player mode: each surface slot
 * (top / bottom-left / bottom-center / bottom-right) maps the four
 * recognised gesture types to a [GestureAction] (the single action
 * vocabulary, persisted by name). [GestureAction.NONE] = unassigned (no-op).
 * An absent key = "not customized, use the canonical default".
 */
data class PlayerGestureSlotSet(
    val top: Map<String, GestureAction> = emptyMap(),
    val bottomLeft: Map<String, GestureAction> = emptyMap(),
    val bottomCenter: Map<String, GestureAction> = emptyMap(),
    val bottomRight: Map<String, GestureAction> = emptyMap(),
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
