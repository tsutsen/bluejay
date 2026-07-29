package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset

/**
 * Discrete gestures fire once (tap, double-tap, long-press start/end).
 */
enum class DiscreteGesture { TAP, DOUBLE_TAP, LONG_PRESS_START, LONG_PRESS_END }

/**
 * Continuous gestures stream events (vertical drag for brightness/volume/fullscreen/minimize).
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
 * Bindings for a specific zone (or row/column/global scope).
 */
data class ZoneBindings(
    val discrete: Map<DiscreteGesture, DiscreteAction> = emptyMap(),
    val continuous: Map<ContinuousGesture, ContinuousAction> = emptyMap()
)

/**
 * Gesture binding table with precedence resolution.
 *
 * Precedence: zone-specific > row > column > global.
 * Row beats column deliberately — see the TOP/BOTTOM row vs LEFT/RIGHT column overlap
 * in the default table, where row wins for drag and column wins for double-tap.
 */
data class GestureBindings(
    val byZone: Map<GestureZone, ZoneBindings> = emptyMap(),
    val byRow: Map<GestureRow, ZoneBindings> = emptyMap(),
    val byColumn: Map<GestureColumn, ZoneBindings> = emptyMap(),
    val global: ZoneBindings = ZoneBindings()
) {
    fun resolveDiscrete(zone: GestureZone, gesture: DiscreteGesture): DiscreteAction? =
        byZone[zone]?.discrete?.get(gesture)
            ?: byRow[zone.row]?.discrete?.get(gesture)
            ?: byColumn[zone.column]?.discrete?.get(gesture)
            ?: global.discrete[gesture]

    fun resolveContinuous(zone: GestureZone, gesture: ContinuousGesture): ContinuousAction? =
        byZone[zone]?.continuous?.get(gesture)
            ?: byRow[zone.row]?.continuous?.get(gesture)
            ?: byColumn[zone.column]?.continuous?.get(gesture)
            ?: global.continuous[gesture]
}
