/*
 * VideoPlayerScene
 *
 * Video player with clean state machine:
 * - FULL: Player in fullscreen mode
 * - DEFAULT: Player embedded in video detail page (normal mode, smaller height)
 * - MINI: Player floating as mini player
 *
 * Transitions between states are smooth animations.
 */

package com.tsutsen.platformplayer.compose.player

import android.net.Uri
import com.tsutsen.platformplayer.logging.Logger
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSAudioUrlRangeSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlRangeSource
import com.tsutsen.platformplayer.compose.navigation.GrayjayNavigator
import com.tsutsen.platformplayer.compose.navigation.VideoDetail
import com.tsutsen.platformplayer.helpers.VideoHelper
import com.tsutsen.platformplayer.states.StatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video player scene with clean state machine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScene(d: VideoDetail, n: GrayjayNavigator) {
    val context = LocalContext.current
    var videoDetails by remember { mutableStateOf<IPlatformVideoDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Load video details
    LaunchedEffect(d.url) {
        isLoading = true
        errorMessage = null
        try {
            withContext(Dispatchers.IO) {
                val result = StatePlatform.instance.getContentDetails(d.url).await()
                if (result !is IPlatformVideoDetails) {
                    throw IllegalStateException("Expected video content, found ${result.contentType}")
                }
                videoDetails = result
            }
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to load video: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            // No top bar - removed as requested
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (videoDetails != null) {
            val video = videoDetails!!
            
            // Set video in state machine
            LaunchedEffect(video.url) {
                VideoPlayerState.setVideo(video)
            }
            
            // Use global ExoPlayer if available, otherwise create new one
            val exoPlayer = remember(video.url) {
                VideoPlayerState.exoPlayer ?: run {
                    val newPlayer = ExoPlayer.Builder(context).build()
                    VideoPlayerState.exoPlayer = newPlayer
                    newPlayer
                }
            }
            
            // Load video with FutoVideoPlayer's approach: merge video and audio
            LaunchedEffect(video.url, exoPlayer) {
                try {
                    // Step 1: Resolve video details
                    val resolvedVideo = withContext(Dispatchers.IO) {
                        StatePlatform.instance.getContentDetails(video.url).await()
                    }
                    
                    // Step 2: Get video and audio sources using FutoVideoPlayer's approach
                    val (videoSource, audioSource) = withContext(Dispatchers.IO) {
                        if (resolvedVideo is IPlatformVideoDetails) {
                            val video = VideoHelper.selectBestVideoSource(
                                resolvedVideo.video,
                                -1,
                                arrayOf("mp4", "webm", "mkv")
                            )
                            val audio = VideoHelper.selectBestAudioSource(
                                resolvedVideo.video,
                                arrayOf("mp4", "webm", "mkv"),
                                null
                            )
                            Pair(video, audio)
                        } else {
                            Pair(null, null)
                        }
                    }
                    
                    // Step 3: Create MediaSources for video and audio separately
                    val (videoMediaSource, audioMediaSource) = withContext(Dispatchers.IO) {
                        val dataSourceFactory = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
                        
                        val videoMs = when (videoSource) {
                            is JSVideoUrlRangeSource -> {
                                // Use FutoVideoPlayer's itag-to-DASH conversion
                                Logger.i("VideoPlayer", "Converting itag video to DASH")
                                VideoHelper.convertItagSourceToChunkedDashSource(videoSource).first
                            }
                            is IDashManifestSource -> {
                                Logger.i("VideoPlayer", "Using DASH manifest")
                                DashMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(videoSource.url))
                                )
                            }
                            is IVideoUrlSource -> {
                                Logger.i("VideoPlayer", "Using progressive video")
                                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(videoSource.getVideoUrl()))
                                )
                            }
                            is IHLSManifestSource -> {
                                Logger.i("VideoPlayer", "Using HLS manifest")
                                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(videoSource.url))
                                )
                            }
                            else -> null
                        }
                        
                        val audioMs = when (audioSource) {
                            is JSAudioUrlRangeSource -> {
                                Logger.i("VideoPlayer", "Converting itag audio to DASH")
                                VideoHelper.convertItagSourceToChunkedDashSource(audioSource)
                            }
                            is IAudioUrlSource -> {
                                Logger.i("VideoPlayer", "Using progressive audio")
                                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(audioSource.getAudioUrl()))
                                )
                            }
                            is IHLSManifestSource -> {
                                Logger.i("VideoPlayer", "Using HLS audio")
                                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(audioSource.url))
                                )
                            }
                            else -> null
                        }
                        
                        Pair(videoMs, audioMs)
                    }
                    
                    // Step 4: Merge video and audio sources
                    val mergedMediaSource = withContext(Dispatchers.Main) {
                        if (videoMediaSource != null) {
                            if (audioMediaSource != null) {
                                Logger.i("VideoPlayer", "Merging video and audio sources")
                                MergingMediaSource(videoMediaSource, audioMediaSource)
                            } else {
                                Logger.i("VideoPlayer", "Using video-only source")
                                videoMediaSource
                            }
                        } else {
                            null
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (mergedMediaSource != null) {
                            exoPlayer.setMediaSource(mergedMediaSource)
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                            Logger.i("VideoPlayer", "Video prepared and playing")
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("VideoPlayer", "Error loading video", e)
                }
            }
            
            // Always show the content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Video player view - handles all three states
                VideoPlayerView(
                    exoPlayer = exoPlayer,
                    video = video,
                    n = n
                )

                // Title - only show when not in fullscreen
                if (VideoPlayerState.state != VideoPlayerState.PlayerState.FULL) {
                    Text(
                        text = video.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Channel row
                    ChannelRow(video = video)

                    // Tab bar
                    VideoPlayerTabs(
                        videoUrl = video.url,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it }
                    )
                }
            }
        }
    }
}

/**
 * Video player view that handles all three states:
 * - FULL: Fullscreen mode
 * - DEFAULT: Embedded in video page (smaller height)
 * - MINI: Not shown here (handled by MiniPlayerOverlay)
 */
@Composable
private fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    video: IPlatformVideoDetails,
    n: GrayjayNavigator
) {
    val context = LocalContext.current
    var swipeTriggered by remember { mutableStateOf(0f) }
    
    // Determine player height based on state
    val playerHeight = when (VideoPlayerState.state) {
        VideoPlayerState.PlayerState.FULL -> 0.5f // 50% of screen height
        VideoPlayerState.PlayerState.DEFAULT -> 0.25f // 25% of screen height
        VideoPlayerState.PlayerState.MINI -> 0f // Not shown here
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((playerHeight * 1080).dp) // Approximate height based on 1080p height
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        swipeTriggered += dragAmount
                        when (VideoPlayerState.state) {
                            VideoPlayerState.PlayerState.DEFAULT -> {
                                // Swipe down to minimize
                                if (swipeTriggered > 200) {
                                    VideoPlayerState.minimize()
                                    MiniPlayerState.show()
                                    n.goBack()
                                    Logger.i("VideoPlayer", "Minimized to mini player via swipe")
                                    swipeTriggered = 0f
                                }
                            }
                            VideoPlayerState.PlayerState.FULL -> {
                                // Swipe down to exit fullscreen
                                if (swipeTriggered > 200) {
                                    VideoPlayerState.exitFullscreen()
                                    swipeTriggered = 0f
                                }
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        swipeTriggered = 0f
                    }
                )
            }
    ) {
        // Video player using PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay controls
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Collapse/Expand button - top left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = {
                        when (VideoPlayerState.state) {
                            VideoPlayerState.PlayerState.DEFAULT -> {
                                // Minimize to mini player
                                VideoPlayerState.minimize()
                                MiniPlayerState.show()
                                n.goBack()
                                Logger.i("VideoPlayer", "Minimized to mini player")
                            }
                            VideoPlayerState.PlayerState.FULL -> {
                                // Exit fullscreen
                                VideoPlayerState.exitFullscreen()
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (VideoPlayerState.state == VideoPlayerState.PlayerState.DEFAULT) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (VideoPlayerState.state == VideoPlayerState.PlayerState.DEFAULT) "Minimize" else "Exit Fullscreen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Fullscreen button - top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = {
                        when (VideoPlayerState.state) {
                            VideoPlayerState.PlayerState.DEFAULT -> {
                                // Enter fullscreen
                                VideoPlayerState.enterFullscreen()
                            }
                            VideoPlayerState.PlayerState.FULL -> {
                                // Exit fullscreen
                                VideoPlayerState.exitFullscreen()
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (VideoPlayerState.state == VideoPlayerState.PlayerState.FULL) {
                            Icons.Default.FullscreenExit
                        } else {
                            Icons.Default.Fullscreen
                        },
                        contentDescription = if (VideoPlayerState.state == VideoPlayerState.PlayerState.FULL) "Exit Fullscreen" else "Fullscreen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ==================== Video Player Components ====================

@Composable
private fun ChannelRow(video: IPlatformVideoDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50))
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Channel info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.author?.name ?: "Unknown Channel",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (video.viewCount != null) "${video.viewCount} views" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Like button
        IconButton(onClick = { /* TODO: Like */ }) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Like",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // More button
        IconButton(onClick = { /* TODO: More options */ }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoPlayerTabs(
    videoUrl: String,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Comments", "Poly Comments", "Recommended")
    
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { tabPositions ->
            if (selectedTabIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    height = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}
