package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
 * Gesture + overlay behavior shared by NORMAL, COMPACT, and FULLSCREEN. All three modes wrap
 * their video box in this exact same set of layers - the old file duplicated the gesture
 * layer, brightness/volume indicators, loading spinner, seek indicators, and gradient scrims
 * once for all three (it was a single `if (fullscreen) ... else ...` block that all three
 * modes funneled through), which made it easy to lose track of which mode you were editing.
 * Pulling it out means each mode's own file only has to say what its top/bottom bar is.
 *
 * FLOATING does not use this - it has its own drag gesture and its own compact control row,
 * see FloatingPlayerContent.kt.
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

@Composable
fun PlayerControlsScaffold(
    modifier: Modifier,
    isLoading: Boolean,
    brightnessValue: Float,
    volumeValue: Float,
    showBrightnessIndicator: Boolean,
    showVolumeIndicator: Boolean,
    showTopBar: Boolean,
    showBottomBar: Boolean,
    callbacks: PlayerGestureCallbacks,
    disableVerticalDragGestures: Boolean = false,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    // pointerInput below is keyed on Unit (matches the original - the gesture detector
    // coroutines only start once), so lambdas must be read through rememberUpdatedState
    // rather than closed over directly, or they'd go stale after the first composition.
    val currentCallbacks by rememberUpdatedState(callbacks)

    Box(modifier = modifier) {
        // ==================== Gesture layer (brightness/volume swipe, tap-to-toggle) ====================
        var touchX = 0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (disableVerticalDragGestures) Modifier else Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                touchX = it.x
                                currentCallbacks.onVerticalDragStart(it.x)
                            },
                            onVerticalDrag = { _, dragAmount ->
                                currentCallbacks.onVerticalDrag(touchX, dragAmount, size.width.toFloat())
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

        // ==================== Brightness Indicator ====================
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(modifier = Modifier.fillMaxHeight().width(120.dp)) {
                BrightnessIndicator(
                    brightness = brightnessValue,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // ==================== Volume Indicator ====================
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(modifier = Modifier.fillMaxHeight().width(120.dp)) {
                VolumeIndicator(
                    volume = volumeValue,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // ==================== Loading Spinner ====================
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = Color.White)
        }

        // ==================== Double-tap seek indicators ====================
        SeekIndicators(showSeekBack = false, showSeekForward = false)

        // ==================== Top gradient + bar ====================
        AnimatedVisibility(
            visible = showTopBar,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )
                topBar()
            }
        }

        // ==================== Bottom gradient + bar ====================
        // Gradient sits BEHIND the control row (overlaid, mirroring the top bar above),
        // anchored to the bottom edge. The previous version used a Column, which stacks the
        // bar and gradient sequentially instead of overlapping them - that pushes the actual
        // control row away from the screen edge and leaves the gradient occupying the edge
        // on its own, so the bar visually reads as displaced/swapped relative to where the
        // top bar sits.
        AnimatedVisibility(
            visible = showBottomBar,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                bottomBar()
            }
        }
    }
}
