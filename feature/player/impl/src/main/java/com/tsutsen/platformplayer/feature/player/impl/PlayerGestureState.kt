package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tsutsen.platformplayer.core.model.PlayerMode

/**
 * State holder for morph gesture drag operations.
 * Encapsulates the drag start/drag/end lifecycle and provides
 * imperative progress updates during drag to avoid Animatable overhead.
 *
 * Usage:
 * ```
 * val gestureState = rememberGestureState()
 *
 * // In gesture callbacks:
 * gestureState.onDragStart {
 *     // Compute locked mode based on current progress
 *     computePlayerMode(...)
 * }
 * gestureState.onDrag(deltaY) {
 *     // Imperative progress update
 *     dragStartProgress + deltaY / dragTravelPx
 * }
 * gestureState.onDragEnd { currentProgress ->
 *     // Sync to morph, decide if minimize
 * }
 * ```
 */
@Composable
fun rememberGestureState(): GestureState {
    return remember { GestureState() }
}

class GestureState {
    var isDraggingMorph by mutableStateOf(false)
        internal set

    var lockedGestureMode by mutableStateOf(PlayerMode.NORMAL)
        internal set

    var morphDragStartProgress by mutableStateOf(0f)
        internal set

    var dragMorphProgress by mutableStateOf<Float?>(null)
        internal set

    // Running total of vertical movement since the drag started. The gesture recognizer
    // feeds onDrag() a per-frame delta (this move event's dy), not a cumulative total, so
    // this has to be accumulated here. Progress must be derived from the accumulated
    // distance, never from a single frame's delta directly — otherwise progress just
    // reflects momentary per-frame noise instead of how far the finger has actually
    // travelled, which reads as the player stuttering/snapping back and forth during drag.
    private var accumulatedDragY = 0f

    /**
     * Called when a morph drag starts.
     * @param onModeComputed Callback to update lockedGestureMode with the computed mode
     * @param onStartProgress Current progress to use as drag start point
     */
    fun onDragStart(onModeComputed: (PlayerMode) -> Unit, onStartProgress: () -> Float) {
        isDraggingMorph = true
        val startProgress = onStartProgress()
        morphDragStartProgress = startProgress
        accumulatedDragY = 0f
        dragMorphProgress = null // Clear stale override
        onModeComputed(computePlayerMode(
            miniProgress = startProgress,
            fullscreenProgress = 0f, // Will be updated from actual state
            playerHeightRatio = 1f, // Will be updated from actual state
            config = PlayerMorphConfig.Default,
        ))
    }

    /**
     * Called during morph drag. Updates progress imperatively.
     * @param deltaY This frame's incremental vertical movement in pixels (NOT a running total —
     *   the caller passes the raw per-move-event delta from the gesture recognizer).
     * @param dragTravelPx Total drag distance for full morph
     * @return Updated progress (also sets internal state)
     */
    fun onDrag(deltaY: Float, dragTravelPx: Float): Float {
        if (!isDraggingMorph) {
            isDraggingMorph = true
            accumulatedDragY = 0f
        }
        accumulatedDragY += deltaY
        val progress = (morphDragStartProgress + accumulatedDragY / dragTravelPx).coerceIn(0f, 1f)
        dragMorphProgress = progress
        return progress
    }

    /**
     * Called when morph drag ends.
     * @param currentProgress Current progress value
     * @param onSnapTo Callback to sync Animatable to progress
     * @param onMinimize Callback to minimize if progress exceeds threshold
     * @return True if drag was active (prevents spurious animations)
     */
    fun onDragEnd(
        currentProgress: Float,
        onSnapTo: (Float) -> Unit,
        onMinimize: () -> Unit,
    ): Boolean {
        if (!isDraggingMorph) return false

        isDraggingMorph = false
        dragMorphProgress = null
        accumulatedDragY = 0f
        lockedGestureMode = PlayerMode.NORMAL

        // Sync Animatable to drag value
        onSnapTo(currentProgress)

        // Check if should minimize
        if (currentProgress >= PlayerMorphConfig.Default.morphSettleThreshold) {
            onMinimize()
        }

        return true
    }
}
