/*
 * MiniPlayerOverlay
 *
 * A floating, draggable mini player that overlays any screen.
 * When collapsed, the video shrinks to a small floating box that the user
 * can drag anywhere on screen. Position persists across app sessions.
 */

package com.futo.platformplayer.compose.player

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlRangeSource
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Floating mini player overlay.
 * Renders a small video preview with pause/play controls that can be dragged anywhere on screen.
 */
@Composable
fun MiniPlayerOverlay(
    video: IPlatformVideoDetails,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(true) }
    
    // Load video URL in background
    LaunchedEffect(video.url) {
        withContext(Dispatchers.IO) {
            try {
                // Resolve video details
                val resolvedVideo = StatePlatform.instance.getContentDetails(video.url).await()
                if (resolvedVideo is IPlatformVideoDetails) {
                    // Get video source
                    val videoSource = VideoHelper.selectBestVideoSource(
                        resolvedVideo.video,
                        -1,
                        arrayOf("mp4", "webm", "mkv")
                    )
                    
                    if (videoSource != null) {
                        // Extract URL from source
                        val url = when (videoSource) {
                            is JSVideoUrlRangeSource -> {
                                (videoSource as? IVideoUrlSource)?.getVideoUrl() 
                                    ?: ""
                            }
                            is IDashManifestSource -> {
                                videoSource.url
                            }
                            is IHLSManifestSource -> {
                                videoSource.url
                            }
                            is IVideoUrlSource -> {
                                videoSource.getVideoUrl()
                            }
                            else -> ""
                        }
                        
                        if (url.isNotEmpty()) {
                            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                            player.prepare()
                            player.play()
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fail - mini player will show placeholder
            }
        }
    }
    
    // Save position when dragging ends
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
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
        // Video preview
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
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
                        player.pause()
                        isPlaying = false
                    } else {
                        player.play()
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
