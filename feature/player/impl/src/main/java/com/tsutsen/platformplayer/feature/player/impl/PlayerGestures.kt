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
 * Gesture callbacks shared across NORMAL, COMPACT, and FULLSCREEN modes.
 *
 * Each mode wires its own behavior into these lambdas — see PlayerScreen.kt for the
 * mode-specific dispatch. The key design point is that the gesture layer itself is
 * mode-agnostic: it just fires callbacks and lets the caller decide what happens.
 */
data class PlayerGestureCallbacks(
    val onTap: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onVerticalDragStart: (touchX: Float) -> Unit,
    // areaWidthPx is the gesture layer's own width - callers use it to decide left-half
    // (brightness) vs right-half (volume), same split the original inline code did with
    // `size.width` from inside the pointerInput block.
    val onVerticalDrag: (touchX: Float, dragAmountPx: Float, areaWidthPx: Float) -> Unit
)

/**
 * Gesture layer: tap, double-tap, and vertical drag for brightness/volume.
 *
 * Uses a stable `Unit` key so the pointerInput block is never torn down during
 * mode transitions. The `disableVerticalDragGestures` flag is the only way to
 * disable the vertical drag — the tap gestures are always active.
 *
 * Lambdas are read through `rememberUpdatedState` because `pointerInput(Unit)`
 * only starts once, so direct closure capture would go stale.
 */
@Composable
internal fun PlayerGestureLayer(
    modifier: Modifier,
    callbacks: PlayerGestureCallbacks,
    disableVerticalDragGestures: Boolean = false
) {
    val currentCallbacks by rememberUpdatedState(callbacks)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (disableVerticalDragGestures) Modifier else Modifier.pointerInput(Unit) {
                    var lastTouchX = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            lastTouchX = it.x
                            currentCallbacks.onVerticalDragStart(lastTouchX)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            lastTouchX = change.position.x
                            currentCallbacks.onVerticalDrag(lastTouchX, dragAmount, size.width.toFloat())
                        }
                    )
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentCallbacks.onTap() },
                    onDoubleTap = { currentCallbacks.onDoubleTap() }
                )
            }
    )
}
