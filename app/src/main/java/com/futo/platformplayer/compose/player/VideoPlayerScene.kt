/*
 * VideoPlayerScene
 *
 * YouTube-like collapsible video player using Media3 Compose UI.
 * Features:
 *   - Collapsible video player with minimize/expand button
 *   - Mini player bar when collapsed (thumbnail, title, expand button)
 *   - Player continues playing when collapsed
 *   - Smooth animations between states
 *   - Title, channel info, and tab bar below
 *   - No top app bar
 */

package com.futo.platformplayer.compose.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import coil.compose.AsyncImage
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.compose.navigation.GrayjayNavigator
import com.futo.platformplayer.compose.navigation.VideoDetail
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.video.FutoVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video player scene with collapsible video player.
 * 
 * Features:
 *   - Collapsible video player with minimize/expand button
 *   - Mini player bar when collapsed (thumbnail, title, expand button)
 *   - Player continues playing when collapsed
 *   - Title, channel info, and tab bar below
 *   - No top app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScene(d: VideoDetail, n: GrayjayNavigator) {
    val context = LocalContext.current
    var videoDetails by remember { mutableStateOf<IPlatformVideoDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMinimized by remember { mutableStateOf(false) }
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
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Collapsible video player
                CollapsibleVideoPlayer(
                    video = video,
                    isMinimized = isMinimized,
                    onMinimizeToggle = { isMinimized = !isMinimized },
                    startPosition = d.position
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
 * Collapsible video player that maintains the ExoPlayer instance across minimize/expand.
 * The player continues playing when collapsed.
 */
@Composable
private fun CollapsibleVideoPlayer(
    video: IPlatformVideoDetails,
    isMinimized: Boolean,
    onMinimizeToggle: () -> Unit,
    startPosition: Long?
) {
    val context = LocalContext.current
    
    // Keep the player instance alive across minimize/expand
    var futoPlayer by remember(video.url) { mutableStateOf<FutoVideoPlayer?>(null) }
    
    LaunchedEffect(video.url) {
        if (futoPlayer == null) {
            futoPlayer = FutoVideoPlayer(context)
        }
    }

    // Load video source
    LaunchedEffect(video.url, futoPlayer) {
        if (futoPlayer != null) {
            try {
                withContext(Dispatchers.IO) {
                    val result = StatePlatform.instance.getContentDetails(video.url).await()
                    if (result is IPlatformVideoDetails) {
                        val videoSource = futoPlayer!!.getPreferredVideoSource(result)
                        val audioSource = futoPlayer!!.getPreferredAudioSource(result, null)
                        
                        futoPlayer!!.setSource(videoSource, audioSource, play = true, resume = startPosition != null)
                        startPosition?.let {
                            futoPlayer!!.seekTo(it)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Lifecycle-aware playback
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> futoPlayer?.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> futoPlayer?.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Full player view when expanded
    AnimatedVisibility(
        visible = !isMinimized,
        enter = expandVertically(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(300))
    ) {
        FullVideoPlayerView(
            futoPlayer = futoPlayer,
            onMinimize = onMinimizeToggle
        )
    }

    // Mini player bar when collapsed
    AnimatedVisibility(
        visible = isMinimized,
        enter = expandVertically(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(300))
    ) {
        MiniPlayerBar(
            video = video,
            onExpand = onMinimizeToggle
        )
    }
}

/**
 * Full video player view with collapse button.
 */
@Composable
private fun FullVideoPlayerView(
    futoPlayer: FutoVideoPlayer?,
    onMinimize: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        // Video player - FutoVideoPlayer is a RelativeLayout that contains everything
        AndroidView(
            factory = { ctx ->
                FutoVideoPlayer(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { player ->
                // FutoVideoPlayer manages its own ExoPlayer internally
            },
            modifier = Modifier.fillMaxSize()
        )

        // Collapse button overlay
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
 * Mini player bar with thumbnail, title, and expand button.
 */
@Composable
private fun MiniPlayerBar(
    video: IPlatformVideoDetails,
    onExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = video.thumbnails.getHQThumbnail(),
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp, 68.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Title and channel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.author.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Expand icon
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Expand",
                modifier = Modifier.size(24.dp)
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
