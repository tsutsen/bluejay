package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.model.PlayerGestures

/**
 * Build the gesture configs from the user's per-slot assignments.
 *
 * The user config is mode-independent: 4 slots × 4 gesture types, where each
 * slot owns a column of the engine's 9-sector grid:
 *   top          → the whole top row
 *   bottomLeft   → left column, middle + bottom rows
 *   bottomCenter → center column, middle + bottom rows
 *   bottomRight  → right column, middle + bottom rows
 *
 * Each cell resolves to the user's assignment when set, else the canonical
 * [PlayerGestures.DEFAULT_SLOTS] default. The same config drives fullscreen,
 * normal and compact; the floating mini surface stays gesture-free.
 */
fun buildGestureConfigs(prefs: PlayerGesturePreferences): GestureConfigs {
    val slotMaps: Map<String, Map<String, String>> =
        mapOf(
            "top" to prefs.top,
            "bottomLeft" to prefs.bottomLeft,
            "bottomCenter" to prefs.bottomCenter,
            "bottomRight" to prefs.bottomRight,
        )

    val slotFor: Map<GestureSector, String> =
        mapOf(
            GestureSector.TOP_LEFT to "top",
            GestureSector.TOP_CENTER to "top",
            GestureSector.TOP_RIGHT to "top",
            GestureSector.MIDDLE_LEFT to "bottomLeft",
            GestureSector.MIDDLE_CENTER to "bottomCenter",
            GestureSector.MIDDLE_RIGHT to "bottomRight",
            GestureSector.BOTTOM_LEFT to "bottomLeft",
            GestureSector.BOTTOM_CENTER to "bottomCenter",
            GestureSector.BOTTOM_RIGHT to "bottomRight",
        )

    val unified =
        GestureConfig().withSectors(
            GestureSector.entries.associate { sector ->
                val slot = slotFor[sector] ?: "top"
                val map = slotMaps[slot].orEmpty()
                sector to
                    GestureSlotConfig(
                        swipeVertical =
                            PlayerGestures.resolve(slot, "swipe_v", map).toEngineAction(),
                        swipeHorizontal =
                            PlayerGestures.resolve(slot, "swipe_h", map).toEngineAction(),
                        doubleTap =
                            PlayerGestures.resolve(slot, "double_tap", map).toEngineAction(),
                        hold = PlayerGestures.resolve(slot, "hold", map).toEngineAction(),
                    )
            },
        )

    return GestureConfigs(
        fullscreen = unified,
        normal = unified,
        compact = unified,
        // No gestures on the mini player surface.
        floating = GestureConfig(),
    )
}

/** Flat action id → engine [GestureAction] (unknown/empty → NONE). */
internal fun String?.toEngineAction(): GestureAction =
    when (this) {
        PlayerGestures.NONE, null -> GestureAction.NONE
        PlayerGestures.VOLUME -> GestureAction.VOLUME
        PlayerGestures.BRIGHTNESS -> GestureAction.BRIGHTNESS
        PlayerGestures.SPEEDUP -> GestureAction.SPEEDUP
        PlayerGestures.SPEEDDOWN -> GestureAction.SPEEDDOWN
        PlayerGestures.REWIND_BACK -> GestureAction.REWIND_BACK
        PlayerGestures.REWIND_FORWARD -> GestureAction.REWIND_FORWARD
        PlayerGestures.CONTEXT_MENU -> GestureAction.CONTEXT_MENU
        PlayerGestures.MORPH_TO_FLOATING -> GestureAction.MORPH_TO_FLOATING
        PlayerGestures.MORPH_TO_FULLSCREEN -> GestureAction.MORPH_TO_FULLSCREEN
        PlayerGestures.MORPH_VERTICAL -> GestureAction.MORPH_VERTICAL
        else -> GestureAction.NONE
    }
