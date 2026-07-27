package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

/**
 * Shared morph box that lives for the entire morph lifecycle (NORMAL ↔ FLOATING).
 *
 * Geometry is lerped from `morphProgress`:
 * - height: lerp between `playerHeightPx` (windowed) and `miniHeightPx` (floating)
 * - corner radius: lerp between 0dp and 12dp
 * - position: windowed position → snapped corner position
 *
 * Chrome crossfades:
 * - Windowed chrome (details + controls) fades out as morphProgress goes 0→0.4
 * - Floating chrome fades in as morphProgress goes 0.6→1
 *
 * This eliminates the tree swap on mode transitions - no LazyColumn teardown,
 * no video surface recreation. The morph box is the single source of truth for
 * video box geometry, drag, and crossfade.
 */
@Composable
fun PlayerMorphBox(
    player: ExoPlayer?,
    morphProgress: Float,
    playerHeightPx: Float,
    miniHeightPx: Float,
    miniWidthPx: Float,
    containerWidth: Float,
    containerHeight: Float,
    isMorphDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
    onExpand: () -> Unit,
    windowedContent: @Composable () -> Unit,
    floatingContent: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val morphedHeight by animateFloatAsState(
        targetValue = playerHeightPx - (playerHeightPx - miniHeightPx) * morphProgress,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "morphBoxHeight"
    )
    val morphedCornerRadius by animateFloatAsState(
        targetValue = 12f * morphProgress,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "morphCornerRadius"
    )
    val floatingOffsetX by animateFloatAsState(
        targetValue = if (morphProgress > 0.99f) 0f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "floatingOffsetX"
    )
    val floatingOffsetY by animateFloatAsState(
        targetValue = if (morphProgress > 0.99f) 0f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "floatingOffsetY"
    )

    val windowedAlpha by animateFloatAsState(
        targetValue = if (morphProgress < 0.4f) 1f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "windowedAlpha"
    )
    val floatingAlpha by animateFloatAsState(
        targetValue = if (morphProgress > 0.6f) 1f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "floatingAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isMorphDragging) {
                if (isMorphDragging) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { _, dragAmount ->
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
            }
    ) {
        // Windowed chrome (details + controls) - fades out during morph
        if (windowedAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(windowedAlpha)
            ) {
                windowedContent()
            }
        }

        // Floating chrome - fades in during morph
        if (floatingAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(floatingAlpha)
            ) {
                floatingContent()
            }
        }

        // Video box - always visible, geometry lerps with morphProgress
        val morphedHeightDp = with(density) { morphedHeight.toDp() }
        val morphedCornerRadiusDp = with(density) { morphedCornerRadius.toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(morphedHeightDp)
                .offset {
                    if (morphProgress > 0.99f) {
                        IntOffset(
                            x = (containerWidth - miniWidthPx).toInt() + floatingOffsetX.toInt(),
                            y = (containerHeight - miniHeightPx).toInt() + floatingOffsetY.toInt()
                        )
                    } else {
                        IntOffset(0, 0)
                    }
                }
                .clip(RoundedCornerShape(morphedCornerRadiusDp))
                .clipToBounds()
                .background(Color.Black)
        ) {
            PlayerVideoSurface(player = player)
        }
    }
}
