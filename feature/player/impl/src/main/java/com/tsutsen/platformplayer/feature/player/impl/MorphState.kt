package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tsutsen.platformplayer.core.model.PlayerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Consolidated state holder for morph progress and drag operations.
 *
 * Replaces the previous split between `GestureState` (drag lifecycle) and `MorphState`
 * (Animatable + animation methods) with a single explicit state machine:
 *
 *   Idle       — Not dragging, Animatable settles to target
 *   Dragging   — User is dragging, progress driven by drag
 *   Animating  — User released, Animatable animating to target
 *
 * This eliminates the `Animatable + nullable-override + isDragging flag` pattern
 * where "which source of truth wins" bugs could occur.
 */
@Composable
fun rememberMorphState(
    onMinimize: () -> Unit,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): MorphState {
    val scope = rememberCoroutineScope()
    return remember { MorphState(scope, onMinimize, config) }
}

class MorphState(
    private val scope: CoroutineScope,
    private val onMinimize: () -> Unit,
    private val config: PlayerMorphConfig = PlayerMorphConfig.Default,
) {
    // ---- Internal state ----

    private val morphProgress = Animatable(0f)
    private var animJob: Job? = null

    var phase: MorphPhase by mutableStateOf(MorphPhase.Idle)
        private set
    
    private var dragTravelPx: Float = 1f // Default, updated via onDrag()

    sealed interface MorphPhase {
        data object Idle : MorphPhase
        data class Dragging(val startProgress: Float, val accumulatedDragY: Float) : MorphPhase
        data class Animating(val target: Float) : MorphPhase
    }

    // ---- Public state ----

    /**
     * Current morph progress in [0, 1].
     * Returns drag-driven progress during drag, Animatable value otherwise.
     */
    val progress: Float
        get() = when (val p = phase) {
            is MorphPhase.Dragging -> {
                val raw = (p.startProgress + p.accumulatedDragY / dragTravelPx).coerceIn(0f, 1f)
                raw
            }
            else -> morphProgress.value
        }

    /** Whether the user is currently dragging the morph. */
    val isDragging: Boolean get() = phase is MorphPhase.Dragging

    /** The gesture mode locked during drag to prevent mode changes mid-gesture. */
    var lockedGestureMode: PlayerMode = PlayerMode.NORMAL
        internal set

    // ---- Drag lifecycle ----

    /**
     * Called when a morph drag starts.
     * @param onModeComputed Callback to update lockedGestureMode with the computed mode
     * @param onStartProgress Current progress to use as drag start point
     */
    fun onDragStart(
        onModeComputed: (PlayerMode) -> Unit,
        onStartProgress: () -> Float,
    ) {
        val startProgress = onStartProgress()
        phase = MorphPhase.Dragging(startProgress, 0f)
        onModeComputed(
            computePlayerMode(
                miniProgress = startProgress,
                fullscreenProgress = 0f,
                playerHeightRatio = 1f,
                config = config,
            )
        )
    }

    /**
     * Called during morph drag. Accumulates per-frame delta and updates progress.
     * @param deltaY This frame's incremental vertical movement in pixels
     * @param dragTravelPx Total drag distance for full morph
     * @return Updated progress
     */
    fun onDrag(deltaY: Float, dragTravelPx: Float): Float {
        this.dragTravelPx = dragTravelPx
        val p = phase
        if (p !is MorphPhase.Dragging) {
            phase = MorphPhase.Dragging(0f, deltaY)
            return (0f + deltaY / dragTravelPx).coerceIn(0f, 1f)
        }
        val newAccumulated = p.accumulatedDragY + deltaY
        phase = MorphPhase.Dragging(p.startProgress, newAccumulated)
        val progress = (p.startProgress + newAccumulated / dragTravelPx).coerceIn(0f, 1f)
        return progress
    }

    /**
     * Called when morph drag ends.
     * @param onSnapTo Callback to sync Animatable to progress
     * @param onMinimize Callback to minimize if progress exceeds threshold
     * @return True if drag was active (prevents spurious animations)
     */
    fun onDragEnd(
        onSnapTo: (Float) -> Unit,
        onMinimize: () -> Unit,
    ): Boolean {
        val p = phase
        if (p !is MorphPhase.Dragging) return false

        val progress = (p.startProgress + p.accumulatedDragY / dragTravelPx).coerceIn(0f, 1f)
        onSnapTo(progress)

        if (progress >= config.morphSettleThreshold) {
            onMinimize()
            phase = MorphPhase.Idle
        } else {
            phase = MorphPhase.Animating(0f)
            launchAnimation(0f)
        }

        return true
    }

    // ---- Animation methods ----

    /** Animate morph to target progress. */
    fun animateTo(target: Float) {
        phase = MorphPhase.Animating(target)
        launchAnimation(target)
    }

    /** Instantly set morph to target progress. */
    fun snapTo(target: Float) {
        scope.launch { morphProgress.snapTo(target) }
    }

    /** Cancel any in-flight animation and return to Idle. */
    fun cancelAnimation() {
        animJob?.cancel()
        phase = MorphPhase.Idle
    }

    // ---- Internal ----

    private fun launchAnimation(target: Float) {
        animJob?.cancel()
        animJob = scope.launch {
            morphProgress.animateTo(target, transitionSpringSpec)
        }
    }

    companion object {
        private val transitionSpringSpec = tween<Float>(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        )
    }
}
