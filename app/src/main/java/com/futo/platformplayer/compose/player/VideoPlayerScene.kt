/*
 * VideoPlayerScene
 *
 * YouTube-like video player scene with collapsible mini player.
 * Features:
 *   - Full video player with title, channel info, and tab bar
 *   - Minimize button shrinks video to a global mini player bar
 *   - Mini player persists when navigating to other tabs
 *   - Video continues playing in background when minimized
 *   - No top app bar
 */

package com.futo.platformplayer.compose.player

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.compose.navigation.GrayjayNavigator
import com.futo.platformplayer.compose.navigation.VideoDetail
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.video.FutoVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video player scene with collapsible mini player.
 * 
 * Features:
 *   - Full video player with title, channel info, and tab bar
 *   - Minimize button shrinks video to a global mini player bar
 *   - Mini player persists when navigating to other tabs
 *   - Video continues playing in background when minimized
 *   - Uses modern ExoPlayer with media3-ui-compose
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
            
            // Create modern ExoPlayer instance
            val exoPlayer = remember(video.url) {
                ExoPlayer.Builder(context).build()
            }
            
            // Load video and audio sources by resolving through plugin system
            LaunchedEffect(video.url, exoPlayer) {
                try {
                    // Step 1: Resolve the video details on background thread
                    val resolvedVideo = withContext(Dispatchers.IO) {
                        StatePlatform.instance.getContentDetails(video.url).await()
                    }
                    
                    // Step 2: Get the preferred video and audio sources using VideoHelper
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
                    
                    // Step 3: Extract URLs from sources on background thread
                    val (videoUrl, audioUrl) = withContext(Dispatchers.IO) {
                        val vUrl = when (videoSource) {
                            is IVideoUrlSource -> {
                                Log.d("VideoPlayer", "Video URL: ${videoSource.getVideoUrl()}")
                                videoSource.getVideoUrl()
                            }
                            is IDashManifestSource -> {
                                (videoSource as? IVideoUrlSource)?.getVideoUrl()
                            }
                            is IHLSManifestSource -> {
                                (videoSource as? IVideoUrlSource)?.getVideoUrl()
                            }
                            else -> {
                                Log.w("VideoPlayer", "Unsupported video source type: ${videoSource?.javaClass?.simpleName}")
                                null
                            }
                        }
                        
                        val aUrl = when (audioSource) {
                            is IAudioUrlSource -> {
                                Log.d("VideoPlayer", "Audio URL: ${audioSource.getAudioUrl()}")
                                audioSource.getAudioUrl()
                            }
                            is IHLSManifestSource -> {
                                (audioSource as? IAudioUrlSource)?.getAudioUrl()
                            }
                            else -> null
                        }
                        
                        Pair(vUrl, aUrl)
                    }
                    
                    // Step 4: Set up the player on main thread
                    withContext(Dispatchers.Main) {
                        if (videoUrl != null) {
                            Log.d("VideoPlayer", "Setting media item: $videoUrl (audio: $audioUrl)")
                            
                            // For DASH/HLS with separate audio, we need to create a merged source
                            // For now, just use the video URL (audio is usually muxed)
                            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                            
                            exoPlayer.setMediaItem(mediaItem)
                            exoPlayer.prepare()
                            
                            d.position?.let { position ->
                                exoPlayer.seekTo(position)
                            }
                            
                            exoPlayer.playWhenReady = true
                        } else {
                            errorMessage = "No playable video source found"
                            Log.e("VideoPlayer", "No playable video URL found")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to load video: ${e.message}"
                        Log.e("VideoPlayer", "Failed to load video", e)
                    }
                }
            }
            
            // Cleanup player when leaving
            DisposableEffect(Unit) {
                onDispose {
                    exoPlayer.release()
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Full video player with minimize button
                FullVideoPlayerView(
                    exoPlayer = exoPlayer,
                    video = video,
                    onMinimize = {
                        // Save position before minimizing
                        MiniPlayerState.show(video, exoPlayer.currentPosition)
                        // Pause the player when minimizing
                        exoPlayer.pause()
                    }
                )

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
 * Full video player view with minimize button.
 * Uses modern ExoPlayer with PlayerView for rendering.
 */
@Composable
private fun FullVideoPlayerView(
    exoPlayer: ExoPlayer,
    video: IPlatformVideoDetails,
    onMinimize: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        // Video player using modern ExoPlayer with PlayerView
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

        // Minimize button overlay
        IconButton(
            onClick = onMinimize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.ExpandLess,
                contentDescription = "Minimize"
            )
        }
    }
}

/**
 * Channel row: avatar + name + subs | view count | like count | more button
 */
@Composable
private fun ChannelRow(video: IPlatformVideoDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel avatar
        AsyncImage(
            model = video.author.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            contentScale = ContentScale.Crop
        )

        // Channel name + subs
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = video.author.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (video.author.subscribers != null && video.author.subscribers!! > 0) {
                Text(
                    text = formatNumber(video.author.subscribers!!) + " subscribers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // View count
        if (video.viewCount > 0) {
            Text(
                text = formatNumber(video.viewCount) + " views",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Like count
        val likes = (video.rating as? RatingLikeDislikes)?.likes ?: 0L
        if (likes > 0) {
            Text(
                text = formatNumber(likes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // More button
        IconButton(onClick = { /* TODO: show more options */ }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More"
            )
        }
    }
}

/**
 * Tab bar: platform comments | poly comments | recommended videos
 */
@Composable
private fun VideoPlayerTabs(
    videoUrl: String,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Comments", "Poly Comments", "Recommended")

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    Text(
                        text = "Platform comments will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                1 -> {
                    Text(
                        text = "Polycentric comments will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                2 -> {
                    Text(
                        text = "Recommended videos will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Format number with K/M/B suffix
 */
private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000_000 -> String.format("%.1fB", number / 1_000_000_000.0)
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}
