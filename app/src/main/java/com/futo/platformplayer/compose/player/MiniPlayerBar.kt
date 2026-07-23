/*
 * MiniPlayerOverlay
 *
 * Floating mini player that overlays any screen.
 * Uses the global ExoPlayer from VideoPlayerState.
 */

package com.futo.platformplayer.compose.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails

/**
 * Floating mini player overlay.
 * Renders the global ExoPlayer in a small PlayerView that can be dragged anywhere on screen.
 */
@Composable
fun MiniPlayerOverlay(
    video: IPlatformVideoDetails,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    // Get the global ExoPlayer
    val exoPlayer = remember { VideoPlayerState.exoPlayer }
    var isPlaying by remember { mutableStateOf(exoPlayer?.isPlaying == true) }
    
    // Update playing state when player changes
    LaunchedEffect(exoPlayer) {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = exoPlayer?.isPlaying == true
            }
        })
    }
    
    Box(
        modifier = modifier
            .size(width = 280.dp, height = 160.dp)
            .shadow(8.dp, shape = RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { /* Start dragging */ },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Update position in MiniPlayerState
                        MiniPlayerState.positionX += dragAmount.x
                        MiniPlayerState.positionY += dragAmount.y
                    },
                    onDragEnd = {
                        // Save position
                        MiniPlayerState.savePosition()
                    },
                    onDragCancel = {
                        // Save position on cancel too
                        MiniPlayerState.savePosition()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Expand player when tapped
                        onExpand()
                    }
                )
            }
    ) {
        // Video preview using global ExoPlayer
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        this.useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient overlay at bottom for title
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
            
            // Title overlay
            Text(
                text = video.name ?: "Unknown",
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 30.dp)
            )
            
            // Close button (top-right)
            IconButton(
                onClick = { onClose() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            // Play/Pause button (center)
            IconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer?.pause()
                        isPlaying = false
                    } else {
                        exoPlayer?.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
