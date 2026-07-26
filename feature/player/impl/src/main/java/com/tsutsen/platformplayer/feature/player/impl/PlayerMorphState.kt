package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Continuous progress driving the WINDOWED <-> FLOATING morph.
 *   0f = fully windowed (NORMAL/COMPACT video box bounds)
 *   1f = fully floating (mini-player bounds)
 *
 * Deliberately not a boolean. Mid-drag, [progress] sits anywhere in between and callers
 * (PlayerScreen.kt) lerp the video box width/height/offset/corner-radius directly off it, so
 * the morph tracks the finger 1:1 instead of only animating after the fact. It only becomes
 * a discrete "is minimized" fact once a drag settles or a button-tap animation completes -
 * see [onDragEnd] / [animateToMode], which are the only two places that call back into the
 * ViewModel.
 *
 * This intentionally has nothing to do with FULLSCREEN - fullscreen is a separate axis with
 * its own enter/exit gesture (double-tap, orientation, dedicated button). Don't offer the
 * drag-to-minimize gesture while isFullscreenAnim is true; there's nothing to morph from.
 */
class PlayerMorphState(initialMinimized: Boolean) {
    val progress = Animatable(if (initialMinimized) 1f else 0f)

    /** True while a finger is actively dragging the morph (as opposed to settling/animating). */
    var isDragging: Boolean = false
        private set

    fun onDragStart() {
        isDragging = true
    }

    /**
     * @param deltaPx raw pointer delta for this drag step (positive = moving down/toward mini)
     * @param dragTravelPx total drag distance that corresponds to a full 0->1 traversal -
     *   e.g. containerHeightPx * 0.35f felt about right in similar apps, tune to taste
     */
    suspend fun onDrag(deltaPx: Float, dragTravelPx: Float) {
        if (dragTravelPx <= 0f) return
        val next = (progress.value + deltaPx / dragTravelPx).coerceIn(0f, 1f)
        progress.snapTo(next)
    }

    /**
     * Call on drag release. Projects the release velocity a short distance forward so a fast
     * flick commits even if the finger hadn't crossed the halfway mark yet (matches
     * BottomSheet-style "swipe to dismiss" feel), then springs to whichever endpoint that
     * lands closer to. [onSettledMinimized]/[onSettledExpanded] fire only if the settled side
     * is different from where this drag started - a drag that springs back to its origin is a
     * cancel and should not re-trigger a ViewModel call.
     */
    suspend fun onDragEnd(
        velocityPxPerSec: Float,
        dragTravelPx: Float,
        onSettledMinimized: suspend () -> Unit,
        onSettledExpanded: suspend () -> Unit
    ) {
        isDragging = false
        if (dragTravelPx <= 0f) return

        val wasMinimized = progress.value > 0.5f
        val flingReach = (velocityPxPerSec / dragTravelPx) * 0.15f // ~150ms of projected travel
        val projected = (progress.value + flingReach).coerceIn(0f, 1f)
        val target = if (projected > 0.5f) 1f else 0f

        progress.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
        )

        val isNowMinimized = target == 1f
        if (isNowMinimized != wasMinimized) {
            if (isNowMinimized) onSettledMinimized() else onSettledExpanded()
        }
    }

    /** Non-drag path: minimize/expand/close buttons animate straight to the endpoint. */
    suspend fun animateToMode(minimized: Boolean) {
        if (isDragging) return // an active drag owns progress right now, don't fight it
        progress.animateTo(
            targetValue = if (minimized) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
}

@Composable
fun rememberPlayerMorphState(initialMinimized: Boolean): PlayerMorphState =
    remember { PlayerMorphState(initialMinimized) }
