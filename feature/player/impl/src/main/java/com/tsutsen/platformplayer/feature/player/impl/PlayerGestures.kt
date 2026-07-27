package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Player modes for gesture handling.
 *
 * The gesture layer uses this to decide whether to enable brightness/volume vertical drag.
 * NORMAL/COMPACT = morph drag target (vertical drag disabled on gesture layer).
 * FULLSCREEN = brightness/volume target (vertical drag enabled).
 * FLOATING = handled by FloatingPlayerContent's own drag logic.
 */
enum class PlayerMode {
    NORMAL,
    COMPACT,
    FLOATING,
    FULLSCREEN
}

/**
 * Gesture callbacks shared across NORMAL, COMPACT, and FULLSCREEN modes.
 *
 * Each mode wires its own behavior into these lambdas — see PlayerScreen.kt for the
 * mode-specific dispatch. The key design point is that the gesture layer itself is
 * mode-agnostic: it just fires callbacks and lets the caller decide what happens.
 */
data class PlayerGestureCallbacks(
    val onTap: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onVerticalDrag: (touchX: Float, dragAmountPx: Float, areaWidthPx: Float) -> Unit
)

/**
 * Gesture layer: tap, double-tap, and vertical drag for brightness/volume.
 *
 * Mode-aware: vertical drag is only active in FULLSCREEN mode (brightness/volume).
 * In NORMAL/COMPACT, the video area is a morph drag target instead.
 *
 * Uses a stable `Unit` key so the pointerInput block is never torn down during
 * mode transitions. Lambdas are read through `rememberUpdatedState` because
 * `pointerInput(Unit)` only starts once, so direct closure capture would go stale.
 */
@Composable
internal fun PlayerGestureLayer(
    modifier: Modifier,
    callbacks: PlayerGestureCallbacks,
    mode: PlayerMode = PlayerMode.NORMAL
) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val enableVerticalDrag = mode == PlayerMode.FULLSCREEN

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enableVerticalDrag) Modifier.pointerInput(Unit) {
                    var lastTouchX = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            lastTouchX = it.x
                        },
                        onVerticalDrag = { change, dragAmount ->
                            lastTouchX = change.position.x
                            currentCallbacks.onVerticalDrag(lastTouchX, dragAmount, size.width.toFloat())
                        }
                    )
                } else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentCallbacks.onTap() },
                    onDoubleTap = { currentCallbacks.onDoubleTap() }
                )
            }
    )
}
