/*
 * VideoPlayerScene
 *
 * YouTube-like video player scene with collapsible mini player.
 * Features:
 *   - Full video player with title, channel info, and tab bar
 *   - Collapse button top-left inside player
 *   - Swipe down to enter PiP mode (Picture-in-Picture)
 *   - Big video hides when in PiP mode
 *   - Merges video and audio sources like FutoVideoPlayer does
 *   - Uses global ExoPlayer that persists across navigation
 */

package com.futo.platformplayer.compose.player

import android.net.Uri
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
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
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSAudioUrlRangeSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlRangeSource
import com.futo.platformplayer.compose.navigation.GrayjayNavigator
import com.futo.platformplayer.compose.navigation.VideoDetail
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video player scene with collapsible mini player.
 * Uses global ExoPlayer that persists across navigation.
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
            
            // Use global ExoPlayer if available, otherwise create new one
            val exoPlayer = remember(video.url) {
                if (VideoPlayerGlobalState.exoPlayer != null && VideoPlayerGlobalState.currentVideo?.url == video.url) {
                    // Reuse existing player
                    Log.d("VideoPlayer", "Reusing existing ExoPlayer")
                    VideoPlayerGlobalState.exoPlayer!!
                } else {
                    // Create new player
                    Log.d("VideoPlayer", "Creating new ExoPlayer")
                    val newPlayer = ExoPlayer.Builder(context).build()
                    VideoPlayerGlobalState.setVideo(video, newPlayer)
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
                                Log.d("VideoPlayer", "Converting itag video to DASH")
                                VideoHelper.convertItagSourceToChunkedDashSource(videoSource).first
                            }
                            is IDashManifestSource -> {
                                Log.d("VideoPlayer", "Using DASH manifest")
                                DashMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(videoSource.url))
                                )
                            }
                            is IVideoUrlSource -> {
                                Log.d("VideoPlayer", "Using progressive video")
                                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(videoSource.getVideoUrl()))
                                )
                            }
                            is IHLSManifestSource -> {
                                Log.d("VideoPlayer", "Using HLS manifest")
                                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(videoSource.url))
                                )
                            }
                            else -> null
                        }
                        
                        val audioMs = when (audioSource) {
                            is JSAudioUrlRangeSource -> {
                                Log.d("VideoPlayer", "Converting itag audio to DASH")
                                VideoHelper.convertItagSourceToChunkedDashSource(audioSource)
                            }
                            is IAudioUrlSource -> {
                                Log.d("VideoPlayer", "Using progressive audio")
                                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
                                    MediaItem.fromUri(Uri.parse(audioSource.getAudioUrl()))
                                )
                            }
                            is IHLSManifestSource -> {
                                Log.d("VideoPlayer", "Using HLS audio")
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
                                Log.d("VideoPlayer", "Merging video and audio sources")
                                MergingMediaSource(videoMediaSource, audioMediaSource)
                            } else {
                                Log.d("VideoPlayer", "Using video-only source")
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
                            Log.d("VideoPlayer", "Video prepared and playing")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoPlayer", "Error loading video", e)
                }
            }
            
            // Cleanup player when leaving
            DisposableEffect(Unit) {
                onDispose {
                    // Don't release the player here - it's managed globally
                    // Just pause playback when leaving the scene
                    exoPlayer?.pause()
                }
            }
            
            // Always show the content, but hide video player when mini player is active
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Full video player with collapse button (top-left) and swipe-down
                // Only show when mini player is NOT active
                if (!MiniPlayerState.isMiniPlayerActive) {
                    FullVideoPlayerView(
                        exoPlayer = exoPlayer,
                        video = video,
                        n = n
                    )
                }

                // Title
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

/**
 * Full video player view with collapse button (top-left) and swipe-down gesture.
 * Uses PlayerView.
 */
@Composable
private fun FullVideoPlayerView(
    exoPlayer: ExoPlayer,
    video: IPlatformVideoDetails,
    n: GrayjayNavigator
) {
    var swipeTriggered by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        swipeTriggered += dragAmount
                        if (swipeTriggered > 200) {
                            // Collapse to mini player and navigate back
                            val position = exoPlayer.currentPosition
                            MiniPlayerState.show(video, position)
                            MiniPlayerState.collapse()
                            VideoPlayerGlobalState.collapse()
                            n.goBack()
                            Log.d("VideoPlayer", "Collapsed to mini player via swipe")
                            swipeTriggered = 0f
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

        // Collapse button overlay - top left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = {
                    Log.d("VideoPlayer", "Minimize button clicked")
                    // Collapse to mini player and navigate back
                    val position = exoPlayer.currentPosition
                    MiniPlayerState.show(video, position)
                    MiniPlayerState.collapse()
                    VideoPlayerGlobalState.collapse()
                    n.goBack()
                    Log.d("VideoPlayer", "Collapsed to mini player")
                },
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Minimize",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
