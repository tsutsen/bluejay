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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Gesture + overlay behavior shared by NORMAL, COMPACT, and FULLSCREEN. All three modes wrap
 * their video box in this exact same set of layers — gesture detection, brightness/volume
 * indicators, loading spinner, seek indicators, and gradient scrims.
 *
 * FLOATING does not use this — it has its own drag gesture and its own compact control row.
 */
data class PlayerGestureCallbacks(
    val onTap: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onVerticalDragStart: (touchX: Float) -> Unit,
    val onVerticalDrag: (touchX: Float, dragAmountPx: Float, areaWidthPx: Float) -> Unit,
    val onSpeedHoldStart: () -> Unit = {},
    val onSpeedHoldEnd: () -> Unit = {}
)

@Composable
fun PlayerUIScaffold(
    modifier: Modifier,
    isLoading: Boolean,
    showTopBar: Boolean,
    showBottomBar: Boolean,
    callbacks: PlayerGestureCallbacks,
    disableVerticalDragGestures: Boolean = false,
    disableTapGestures: Boolean = false,
    gradientAlpha: Float = 1f,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val currentCallbacks by rememberUpdatedState(callbacks)

    Box(modifier = modifier.clipToBounds()) {
        // ==================== Gesture layer ====================
        var touchX = 0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (disableVerticalDragGestures) Modifier
                    else Modifier.pointerInput(Unit) {
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
                .then(
                    if (disableTapGestures) Modifier
                    else Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { currentCallbacks.onTap() },
                            onDoubleTap = { currentCallbacks.onDoubleTap() }
                        )
                    }
                )
        )

        // ==================== Loading Spinner ====================
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = Color.White)
        }

        // ==================== Top gradient + bar ====================
        if (showTopBar && gradientAlpha > 0.01f) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f * gradientAlpha), Color.Transparent)
                            )
                        )
                )
                topBar()
            }
        }

        // ==================== Bottom gradient + bar ====================
        if (showBottomBar && gradientAlpha > 0.01f) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f * gradientAlpha))
                            )
                        )
                )
                bottomBar()
            }
        }
    }
}

