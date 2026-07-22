/*
 * VideoPlayerScene
 *
 * Compose-based video player scene that wraps the existing ExoPlayer infrastructure.
 * Uses AndroidView to host Media3's PlayerView for video playback.
 */

package com.futo.platformplayer.compose.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.futo.platformplayer.compose.navigation.GrayjayNavigator
import com.futo.platformplayer.compose.navigation.VideoDetail
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video player scene that displays a video using Media3's PlayerView.
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
                VideoPlayerView(
                    videoUrl = d.url,
                    startPosition = d.position,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * AndroidView that hosts Media3's PlayerView for video playback.
 *
 * @param videoUrl URL of the video to play
 * @param startPosition Optional position to resume from (in milliseconds)
 * @param modifier Modifier for the view
 */
@Composable
fun VideoPlayerView(
    videoUrl: String,
    startPosition: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { playerView ->
            val playerManager = StatePlayer.instance.getPlayerOrCreate(context)
            playerView.player = playerManager.player

            // Load the video
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUrl)
            playerManager.player.setMediaItem(mediaItem)
            playerManager.player.prepare()

            // Set resume position if provided
            startPosition?.let { position ->
                playerManager.player.seekTo(position)
            }

            // Auto-play
            playerManager.player.playWhenReady = true
        },
        modifier = modifier
    )
}
