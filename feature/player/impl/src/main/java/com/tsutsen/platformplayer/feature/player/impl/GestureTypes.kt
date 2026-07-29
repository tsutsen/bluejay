package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset

// ==================== Gesture Zones ====================

enum class GestureRow { TOP, MIDDLE, BOTTOM }
enum class GestureColumn { LEFT, CENTER, RIGHT }

data class GestureZone(val row: GestureRow, val column: GestureColumn) {
    companion object {
        val ALL: List<GestureZone> =
            GestureRow.entries.flatMap { r -> GestureColumn.entries.map { c -> GestureZone(r, c) } }
    }
}

fun resolveGestureZone(position: Offset, areaWidth: Float, areaHeight: Float): GestureZone {
    val row = when {
        position.y < areaHeight / 3f -> GestureRow.TOP
        position.y > areaHeight * 2f / 3f -> GestureRow.BOTTOM
        else -> GestureRow.MIDDLE
    }
    val column = when {
        position.x < areaWidth / 3f -> GestureColumn.LEFT
        position.x > areaWidth * 2f / 3f -> GestureColumn.RIGHT
        else -> GestureColumn.CENTER
    }
    return GestureZone(row, column)
}

// ==================== Gesture Types & Bindings ====================

/**
 * Discrete gestures fire once (tap, double-tap, long-press start/end).
 */
enum class DiscreteGesture { TAP, DOUBLE_TAP, LONG_PRESS_START, LONG_PRESS_END }

/**
 * Continuous gestures stream events (vertical drag for brightness/volume/morph).
 */
enum class ContinuousGesture { VERTICAL_DRAG }

/**
 * Discrete action invoked once when the gesture fires.
 */
fun interface DiscreteAction {
    fun invoke(zone: GestureZone, position: Offset)
}

/**
 * Continuous action that streams delta events during a drag.
 */
interface ContinuousAction {
    fun onStart(zone: GestureZone, position: Offset)
    fun onDelta(deltaPx: Float)
    fun onEnd()
}

/**
 * Bindings for a specific zone — each zone has its own independent set of gestures.
 * No precedence, no fallback. If a zone has no binding for a gesture, that gesture does nothing.
 */
data class ZoneBindings(
    val discrete: Map<DiscreteGesture, DiscreteAction> = emptyMap(),
    val continuous: Map<ContinuousGesture, ContinuousAction> = emptyMap()
)

/**
 * Gesture binding table — zone-based, no precedence.
 *
 * Each zone has its own complete set of gestures. The binding table is mode-specific:
 * - Fullscreen: TOP zones handle morph, MIDDLE/BOTTOM zones handle brightness/volume
 * - Normal: All zones handle morph (swipe-down to exit)
 *
 * Controls (seek bar, buttons) take precedence over gestures via requireUnconsumed=true
 * in the gesture recognizer — if a child consumes the touch, no gesture fires.
 */
data class GestureBindings(
    val byZone: Map<GestureZone, ZoneBindings>
) {
    fun resolveDiscrete(zone: GestureZone, gesture: DiscreteGesture): DiscreteAction? =
        byZone[zone]?.discrete?.get(gesture)

    fun resolveContinuous(zone: GestureZone, gesture: ContinuousGesture): ContinuousAction? =
        byZone[zone]?.continuous?.get(gesture)
}
