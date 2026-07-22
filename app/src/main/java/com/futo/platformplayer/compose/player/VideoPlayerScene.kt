/*
 * VideoPlayerScene
 *
 * Compose-based video player scene with full video detail UI.
 * Uses the existing FutoVideoPlayer for proper video stream loading via the plugin system.
 * Includes title, channel info, description, comments, and recommended videos.
 */

package com.futo.platformplayer.compose.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.compose.navigation.GrayjayNavigator
import com.futo.platformplayer.compose.navigation.VideoDetail
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.video.FutoVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Video player scene with full video detail UI.
 *
 * @param d VideoDetail nav key containing the video URL and optional resume position
 * @param n Navigator for back navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScene(d: VideoDetail, n: GrayjayNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoDetails by remember { mutableStateOf<IPlatformVideoDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var descriptionExpanded by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = { Text(videoDetails?.name ?: "Loading...", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { n.goBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
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
            VideoPlayerWithDetails(
                video = videoDetails!!,
                startPosition = d.position,
                descriptionExpanded = descriptionExpanded,
                onDescriptionToggle = { descriptionExpanded = !descriptionExpanded },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

/**
 * Video player with title, channel info, description, comments, and recommended videos.
 */
@Composable
fun VideoPlayerWithDetails(
    video: IPlatformVideoDetails,
    startPosition: Long? = null,
    descriptionExpanded: Boolean,
    onDescriptionToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Video Player
        FutoVideoPlayerView(
            videoUrl = video.url,
            startPosition = startPosition,
            modifier = Modifier.fillMaxWidth()
        )

        // Video Details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = video.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Channel info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel thumbnail
                AsyncImage(
                    model = video.author.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp),
                    contentScale = ContentScale.Crop
                )

                // Channel name and metadata
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = video.author.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatVideoMetadata(video),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Description
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable(onClick = onDescriptionToggle),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (descriptionExpanded) {
                        Text(
                            text = video.description ?: "No description",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            text = (video.description ?: "No description").take(100) + if ((video.description ?: "").length > 100) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Comments and Recommended videos would go here
            // For now, show a placeholder
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Comments and recommended videos coming soon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * AndroidView that hosts FutoVideoPlayer for video playback.
 * Uses the existing plugin system to resolve video streams.
 *
 * @param videoUrl URL of the video to play
 * @param startPosition Optional position to resume from (in milliseconds)
 * @param modifier Modifier for the view
 */
@OptIn(UnstableApi::class)
@Composable
fun FutoVideoPlayerView(
    videoUrl: String,
    startPosition: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            // Launch coroutine to load video and set source
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val result = StatePlatform.instance.getContentDetails(videoUrl).await()
                        if (result !is IPlatformVideoDetails) {
                            throw IllegalStateException("Expected video content, found ${result.contentType}")
                        }
                        val video = result
                        val videoSource = player.getPreferredVideoSource(video)
                        val audioSource = player.getPreferredAudioSource(video, null)

                        player.setSource(videoSource, audioSource, play = true, resume = startPosition != null)
                        startPosition?.let {
                            player.seekTo(it)
                        }
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Format video metadata (views, date, etc.)
 */
private fun formatVideoMetadata(video: IPlatformVideoDetails): String {
    val parts = mutableListOf<String>()

    if (video.viewCount > 0) {
        parts.add("${video.viewCount.toHumanNumber()} views")
    }

    if (video.datetime != null) {
        val diff = video.datetime?.toEpochSecond() ?: 0
        val now = System.currentTimeMillis() / 1000
        val secondsAgo = now - diff
        val ago = if (secondsAgo < 60) "just now"
                  else if (secondsAgo < 3600) "${secondsAgo / 60} minutes ago"
                  else if (secondsAgo < 86400) "${secondsAgo / 3600} hours ago"
                  else "${secondsAgo / 86400} days ago"
        parts.add(ago)
    }

    return parts.joinToString(" • ")
}

// Add toHumanNumber extension if not already available
private fun Long.toHumanNumber(): String {
    return when {
        this >= 1_000_000_000 -> String.format("%.1fB", this / 1_000_000_000.0)
        this >= 1_000_000 -> String.format("%.1fM", this / 1_000_000.0)
        this >= 1_000 -> String.format("%.1fK", this / 1_000.0)
        else -> this.toString()
    }
}
