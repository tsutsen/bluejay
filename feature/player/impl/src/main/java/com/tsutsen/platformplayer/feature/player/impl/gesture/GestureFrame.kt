package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.ui.geometry.Offset

/**
 * Lifecycle phase of a continuous gesture (swipe / hold).
 */
enum class GesturePhase { START, ACTIVE, END }

/**
 * One frame emitted during a continuous gesture (swipe or hold).
 *
 * A complete gesture produces: START → zero or more ACTIVE → END.
 *
 * @property sector       the 3×3 grid sector the gesture originated in
 * @property gestureType  SWIPE_VERTICAL, SWIPE_HORIZONTAL, or HOLD
 * @property action       the resolved [GestureAction] for this sector + type
 * @property phase        START / ACTIVE / END
 * @property instantDelta movement since the last frame (pixels)
 * @property totalDelta   cumulative movement since gesture START (pixels)
 *                         — for HOLD this is the modulation offset from
 *                           the initial hold position
 * @property elapsedMs    milliseconds since gesture START
 * @property fingerPosition current absolute finger position (pixels)
 */
data class GestureFrame(
    val sector: GestureSector,
    val gestureType: GestureType,
    val action: GestureAction,
    val phase: GesturePhase,
    val instantDelta: Offset = Offset.Zero,
    val totalDelta: Offset = Offset.Zero,
    val elapsedMs: Long = 0,
    val fingerPosition: Offset
)

/**
 * Single-shot event for instant gestures (currently DOUBLE_TAP only).
 */
data class InstantActionEvent(
    val sector: GestureSector,
    val action: GestureAction,
    val position: Offset
)
