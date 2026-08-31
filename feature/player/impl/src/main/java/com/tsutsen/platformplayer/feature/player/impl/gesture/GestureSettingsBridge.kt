package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.model.PlayerGestures

/**
 * Build gesture configs from the user's per-slot assignments.
 *
 * User settings are mode-independent and slot-based: 4 slots (top /
 * bottom-left / bottom-center / bottom-right) × 4 gesture types. The engine
 * works on a 9-sector grid, so each slot owns a column of sectors:
 *   top          → the whole top row
 *   bottomLeft   → left column, middle + bottom rows
 *   bottomCenter → center column, middle + bottom rows
 *   bottomRight  → right column, middle + bottom rows
 *
 * Everything else stays on the shipped defaults: the sector slot's base
 * behaviour (double-tap seek, hold speed, horizontal scrub) is preserved and
 * each user assignment replaces the corresponding gesture-type action.
 *
 * @param prefs the saved assignments (all-empty maps = pure defaults).
 */
fun buildGestureConfigs(prefs: PlayerGesturePreferences): GestureConfigs {
    val defaults = buildDefaultGestureConfigs()

    // sector -> (slot assignments)
    val assignmentsFor: Map<GestureSector, Map<String, String>> =
        mapOf(
            GestureSector.TOP_LEFT to prefs.top,
            GestureSector.TOP_CENTER to prefs.top,
            GestureSector.TOP_RIGHT to prefs.top,
            GestureSector.MIDDLE_LEFT to prefs.bottomLeft,
            GestureSector.MIDDLE_CENTER to prefs.bottomCenter,
            GestureSector.MIDDLE_RIGHT to prefs.bottomRight,
            GestureSector.BOTTOM_LEFT to prefs.bottomLeft,
            GestureSector.BOTTOM_CENTER to prefs.bottomCenter,
            GestureSector.BOTTOM_RIGHT to prefs.bottomRight,
        )

    fun applyUser(
        slot: GestureSlotConfig,
        assignments: Map<String, String>,
    ): GestureSlotConfig {
        var result = slot
        assignments.forEach { (type, actionId) ->
            val action = actionId.toEngineAction()
            result = when (type) {
                "swipe_v" -> result.copy(swipeVertical = action)
                "swipe_h" -> result.copy(swipeHorizontal = action)
                "double_tap" -> result.copy(doubleTap = action)
                "hold" -> result.copy(hold = action)
                else -> result
            }
        }
        return result
    }

    fun remap(config: GestureConfig): GestureConfig =
        GestureConfig().withSectors(
            GestureSector.entries.associate { sector ->
                sector to applyUser(config.sectors[sector] ?: GestureSlotConfig(), assignmentsFor[sector].orEmpty())
            },
        )

    // FLOATING stays all-NONE (no gestures on the mini player surface).
    return GestureConfigs(
        fullscreen = remap(defaults.fullscreen),
        normal = remap(defaults.normal),
        compact = remap(defaults.compact),
        floating = defaults.floating,
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
