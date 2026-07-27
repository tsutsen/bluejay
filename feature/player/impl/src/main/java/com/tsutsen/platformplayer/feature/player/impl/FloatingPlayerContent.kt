package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

private const val TAG = "PlayerScreen"

/**
 * FLOATING mode: small draggable 16:9 window with just play/pause, close, title, more-options
 * and fullscreen-jump buttons. Snaps to the nearest corner on release.
 *
 * NOTE: the previous implementation of this block had no video surface at all (there was a
 * `// Mini player is handled separately` comment where the video should have been, and nothing
 * ever picked it up) - this version renders [PlayerVideoSurface] behind the controls, which is
 * almost certainly what was intended.
 */
@Composable
fun FloatingPlayerContent(
    player: ExoPlayer?,
    state: PlayerUiState.Loaded,
    miniWidth: Dp,
    miniHeight: Dp,
    cornerRadius: Dp,
    // Pass the *animated* (spring) offset here so the window glides on release; the
    // onOffsetChanged callback below should update the raw target state that feeds that
    // animateFloatAsState in PlayerScreen.kt, not this value directly.
    offsetX: Float,
    offsetY: Float,
    containerWidth: Float,
    containerHeight: Float,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onMoreOptions: () -> Unit,
    onFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        var isDragging by remember { mutableStateOf(false) }
        
        Box(
            modifier = Modifier
                .size(miniWidth, miniHeight)
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset { IntOffset(x = offsetX.toInt(), y = offsetY.toInt()) }
                .graphicsLayer {
                shape = RoundedCornerShape(cornerRadius)
                clip = true
            }
            .pointerInput(isDragging) {
                var localOffsetX = offsetX
                var localOffsetY = offsetY

                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount: Offset ->
                        change.consume()
                        localOffsetX += dragAmount.x
                        localOffsetY += dragAmount.y
                        onOffsetChanged(localOffsetX, localOffsetY)
                    },
                    onDragEnd = {
                        isDragging = false
                        // Snap to nearest edge or keep position
                        val miniWidthPx = miniWidth.toPx()
                        val miniHeightPx = miniHeight.toPx()
                        val paddingPx = 16.dp.toPx()
                        val edgeThreshold = 100f

                        val initialX = containerWidth - miniWidthPx - paddingPx
                        val initialY = containerHeight - miniHeightPx - paddingPx
                        val actualX = initialX + localOffsetX
                        val actualY = initialY + localOffsetY

                        var snappedX = localOffsetX
                        if (actualX < edgeThreshold) {
                            snappedX = -initialX
                        } else if (actualX > containerWidth - miniWidthPx - edgeThreshold) {
                            snappedX = 0f
                        }

                        var snappedY = localOffsetY
                        if (actualY < edgeThreshold) {
                            snappedY = -initialY
                        } else if (actualY > containerHeight - miniHeightPx - edgeThreshold) {
                            snappedY = 0f
                        }

                        onOffsetChanged(snappedX, snappedY)
                    },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        // Video surface behind the controls.
        PlayerVideoSurface(player = player, modifier = Modifier.fillMaxSize())

        // Background scrim + tap-to-expand
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(isDragging) {
                    if (!isDragging) {
                        detectTapGestures(
                            onTap = {
                                Log.d(TAG, "Tap background: expand mini player")
                                onExpand()
                            }
                        )
                    }
                }
        )

        // Buttons on top - not affected by the background tap
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentVideo?.title ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val authorName = state.currentVideo?.author?.name
                    if (!authorName.isNullOrEmpty()) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onMoreOptions, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                LinearProgressIndicator(
                    progress = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f,
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
    }
}
