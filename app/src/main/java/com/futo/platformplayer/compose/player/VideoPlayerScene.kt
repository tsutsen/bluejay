/*
 * VideoPlayerScene
 *
 * Compose-based video player scene that uses the existing FutoVideoPlayer
 * for proper video stream loading via the plugin system.
 */

package com.futo.platformplayer.compose.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.futo.platformplayer.compose.navigation.GrayjayNavigator
import com.futo.platformplayer.compose.navigation.VideoDetail
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.video.FutoVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Video player scene that displays a video using the existing FutoVideoPlayer.
 * This properly uses the plugin system to resolve video streams.
 *
 * @param d VideoDetail nav key containing the video URL and optional resume position
 * @param n Navigator for back navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScene(d: VideoDetail, n: GrayjayNavigator) {
    val context = LocalContext.current
    var videoTitle by remember(d.url) { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(d.url) {
        isLoading = true
        errorMessage = null
        try {
            withContext(Dispatchers.IO) {
                val result = StatePlatform.instance.getContentDetails(d.url).await()
                videoTitle = result.name ?: d.url
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
                title = { Text(videoTitle, maxLines = 1) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Text(
                    text = "Loading...",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                FutoVideoPlayerView(
                    videoUrl = d.url,
                    startPosition = d.position,
                    modifier = Modifier.fillMaxSize()
                )
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
                        if (result !is com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails) {
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
