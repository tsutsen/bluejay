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
 *
 * Everything that belongs to a single drag gesture — start progress, accumulated
 * movement, the travel distance used to convert pixels to progress, and the mode
 * locked in for the gesture's duration — lives together as fields on
 * [MorphPhase.Dragging] rather than as separate sibling vars. That makes a drag
 * snapshot atomic: there's no way to read a "torn" combination of drag-related
 * fields, because there's only ever one field (`phase`) to read.
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

    // Must be observable — this is the value onDrag() mutates on every pointer-move frame,
    // and it's the sole trigger for recomposing anything reading `progress` mid-drag. As a
    // plain var, mutating it would be invisible to Compose's snapshot system: nothing would
    // re-read `progress` until some unrelated state happened to change.
    private var phase: MorphPhase by mutableStateOf(MorphPhase.Idle)

    sealed interface MorphPhase {
        data object Idle : MorphPhase
        data class Dragging(
            val startProgress: Float,
            val accumulatedDragY: Float,
            val dragTravelPx: Float,
            val lockedMode: PlayerMode,
        ) : MorphPhase
        data class Animating(val target: Float) : MorphPhase
    }

    // ---- Public state ----

    /**
     * Current morph progress in [0, 1].
     * Returns drag-driven progress during drag, Animatable value otherwise.
     */
    val progress: Float
        get() = when (val p = phase) {
            is MorphPhase.Dragging -> (p.startProgress + p.accumulatedDragY / p.dragTravelPx).coerceIn(0f, 1f)
            else -> morphProgress.value
        }

    /** Whether the user is currently dragging the morph. */
    val isDragging: Boolean get() = phase is MorphPhase.Dragging

    /** The gesture mode locked for the duration of the current drag, if any. */
    val lockedGestureMode: PlayerMode
        get() = (phase as? MorphPhase.Dragging)?.lockedMode ?: PlayerMode.NORMAL

    // ---- Drag lifecycle ----

    /**
     * Called when a morph drag starts.
     * @param onModeComputed Callback to notify the caller of the locked mode for this drag
     * @param onStartProgress Current progress to use as drag start point
     */
    fun onDragStart(
        onModeComputed: (PlayerMode) -> Unit,
        onStartProgress: () -> Float,
    ) {
        // A drag starting always wins over whatever animation was in flight — cancel it
        // outright rather than leaving it to race the new drag's writes to morphProgress.
        animJob?.cancel()

        val startProgress = onStartProgress()
        val lockedMode = computePlayerMode(
            miniProgress = startProgress,
            fullscreenProgress = 0f,
            playerHeightRatio = 1f,
            config = config,
        )
        phase = MorphPhase.Dragging(
            startProgress = startProgress,
            accumulatedDragY = 0f,
            dragTravelPx = 1f, // placeholder — overwritten by the first onDrag() call
            lockedMode = lockedMode,
        )
        onModeComputed(lockedMode)
    }

    /**
     * Called during morph drag. Accumulates per-frame delta and updates progress.
     * @param deltaY This frame's incremental vertical movement in pixels
     * @param dragTravelPx Total drag distance for full morph
     * @return Updated progress
     */
    fun onDrag(deltaY: Float, dragTravelPx: Float): Float {
        val p = phase
        // onDrag() should only ever be called between onDragStart() and onDragEnd() — the
        // gesture recognizer guarantees onStart() precedes onDelta(). If this fires, the
        // wiring is broken somewhere upstream; fail loudly instead of silently defaulting
        // to startProgress = 0f, which would just read as an unexplained visual snap.
        check(p is MorphPhase.Dragging) {
            "MorphState.onDrag() called without a preceding onDragStart()"
        }

        val updated = p.copy(
            accumulatedDragY = p.accumulatedDragY + deltaY,
            dragTravelPx = dragTravelPx,
        )
        phase = updated
        return (updated.startProgress + updated.accumulatedDragY / updated.dragTravelPx).coerceIn(0f, 1f)
    }

    /**
     * Called when morph drag ends.
     * @param onMinimize Callback to minimize if progress exceeds threshold
     * @return True if drag was active (prevents spurious animations)
     */
    fun onDragEnd(
        onMinimize: () -> Unit,
    ): Boolean {
        val p = phase as? MorphPhase.Dragging ?: return false

        val progressAtRelease = (p.startProgress + p.accumulatedDragY / p.dragTravelPx).coerceIn(0f, 1f)
        // Set morphProgress to current progress so animation continues from here
        // instead of starting from 0
        scope.launch {
            morphProgress.snapTo(progressAtRelease)
        }

        if (progressAtRelease >= config.morphSettleThreshold) {
            // No animation follows on this path — make sure nothing stale is left running
            // that could still write to morphProgress after we've settled into Idle.
            animJob?.cancel()
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
        animJob?.cancel()
        animJob = scope.launch { morphProgress.snapTo(target) }
    }

    /**
     * Cancel any in-flight settle/restore animation and return to Idle.
     *
     * Deliberately a no-op on the phase itself while a drag is active: this is called from
     * PlayerView's mode-sync effect specifically to cancel a *competing animation* the moment
     * a new drag begins, not to abort the drag. Resetting `phase` unconditionally here would
     * wipe out the just-started Dragging phase (and its startProgress) on every drag start.
     */
    fun cancelAnimation() {
        animJob?.cancel()
        if (phase !is MorphPhase.Dragging) {
            phase = MorphPhase.Idle
        }
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
