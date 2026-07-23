package com.futo.platformplayer.feature.player.impl

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.futo.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.futo.platformplayer.core.model.ContentItem
import com.futo.platformplayer.core.navigation.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Full-screen video player with gesture controls and overlays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    navigator: Navigator,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showTopOverlay by remember { mutableStateOf(true) }
    var showBottomOverlay by remember { mutableStateOf(true) }
    var showOptionsModal by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableStateOf(1.0f) }
    var volumeValue by remember { mutableStateOf(1.0f) }
    var replayEnabled by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("Auto") }

    // Auto-hide overlay
    LaunchedEffect(showTopOverlay, showBottomOverlay) {
        if (showTopOverlay && showBottomOverlay) {
            delay(3000)
            if (uiState is PlayerUiState.Loaded && (uiState as PlayerUiState.Loaded).isPlaying) {
                showTopOverlay = false
                showBottomOverlay = false
            }
        }
    }

    // Update brightness/volume indicators
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            val state = uiState as PlayerUiState.Loaded
            brightnessValue = state.brightness
            volumeValue = state.volume
        }
    }

    when (val state = uiState) {
        is PlayerUiState.Initial -> {
            VideoCardSkeleton(count = 1)
        }
        is PlayerUiState.Loaded -> {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            LaunchedEffect(state.currentVideo) {
                if (state.currentVideo != null) {
                    player.setMediaItem(
                        MediaItem.fromUri(state.currentVideo!!.url)
                    )
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // ExoPlayer view - use a regular Box to ensure proper sizing
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Overlay
                AnimatedVisibility(
                    visible = showTopOverlay,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    TopOverlay(
                        title = state.currentVideo?.title ?: "Unknown",
                        channelName = state.currentVideo?.author?.name ?: "Unknown",
                        onMinimize = { viewModel.minimize() },
                        onReplayToggle = { replayEnabled = !replayEnabled },
                        onWatchLater = { /* TODO */ },
                        onOptions = { showOptionsModal = true }
                    )
                }

                // Bottom Overlay
                AnimatedVisibility(
                    visible = showBottomOverlay,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomOverlay(
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        isPlaying = state.isPlaying,
                        onPlayPause = {
                            if (state.isPlaying) viewModel.pause() else viewModel.resume()
                        },
                        onPrevious = { viewModel.skipPrevious() },
                        onNext = { viewModel.skipNext() },
                        onChapters = { showChapters = !showChapters },
                        onFullscreen = { viewModel.toggleFullscreen() }
                    )
                }

                // Volume Indicator (left side)
                AnimatedVisibility(
                    visible = showVolumeIndicator,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(120.dp)
                    ) {
                        VolumeIndicator(
                            volume = volumeValue,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Brightness Indicator (right side)
                AnimatedVisibility(
                    visible = showBrightnessIndicator,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(120.dp)
                    ) {
                        BrightnessIndicator(
                            brightness = brightnessValue,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Double-tap seek indicators
                SeekIndicators(
                    showSeekBack = false, // TODO: Track double-tap state
                    showSeekForward = false
                )

                // Gesture handling
                GestureHandler(
                    onToggleOverlay = {
                        showTopOverlay = !showTopOverlay
                        showBottomOverlay = !showBottomOverlay
                    },
                    onBrightnessChange = { delta ->
                        brightnessValue = (brightnessValue + delta).coerceIn(0f, 1f)
                        viewModel.setBrightness(brightnessValue)
                        showBrightnessIndicator = true
                        coroutineScope.launch {
                            delay(1500)
                            showBrightnessIndicator = false
                        }
                    },
                    onVolumeChange = { delta ->
                        volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                        viewModel.setVolume(volumeValue)
                        showVolumeIndicator = true
                        coroutineScope.launch {
                            delay(1500)
                            showVolumeIndicator = false
                        }
                    },
                    onMinimize = { viewModel.minimize() },
                    onExitFullscreen = { viewModel.exitFullscreen() },
                    onDoubleTapLeft = { /* TODO: seek back 10s */ },
                    onDoubleTapRight = { /* TODO: seek forward 10s */ }
                )

                // Options Modal
                if (showOptionsModal) {
                    OptionsModal(
                        playbackSpeed = selectedSpeed,
                        quality = selectedQuality,
                        onSpeedChange = { speed ->
                            selectedSpeed = speed
                            viewModel.setPlaybackSpeed(speed)
                        },
                        onQualityChange = { quality ->
                            selectedQuality = quality
                            viewModel.setVideoQuality(quality)
                        },
                        onDismiss = { showOptionsModal = false }
                    )
                }

                // Chapters Panel
                if (showChapters) {
                    ChaptersPanel(
                        chapters = emptyList(), // TODO: Load chapters from video
                        currentPositionMs = state.currentPositionMs,
                        onChapterClick = { positionMs ->
                            viewModel.seekTo(positionMs)
                            showChapters = false
                        },
                        onDismiss = { showChapters = false }
                    )
                }
            }
        }
        is PlayerUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun TopOverlay(
    title: String,
    channelName: String,
    onMinimize: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onOptions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onReplayToggle) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = "Replay",
                    tint = Color.White
                )
            }
            IconButton(onClick = onWatchLater) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Watch Later",
                    tint = Color.White
                )
            }
            IconButton(onClick = onOptions) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Options",
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channelName,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomOverlay(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Timeline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formatTime(currentPositionMs),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
                    tint = Color.White
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun BrightnessIndicator(
    brightness: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BrightnessHigh,
            contentDescription = "Brightness",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(100.dp)
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(brightness)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(brightness * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun VolumeIndicator(
    volume: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Volume",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(100.dp)
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(volume)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SeekIndicators(
    showSeekBack: Boolean,
    showSeekForward: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (showSeekBack) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Seek back 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        if (showSeekForward) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Seek forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureHandler(
    onToggleOverlay: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMinimize: () -> Unit,
    onExitFullscreen: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit
) {
    var isLeftSide by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        val delta = -dragAmount / 500f
                        // Left half: volume
                        if (isLeftSide) {
                            onVolumeChange(delta)
                        }
                        // Right half: brightness
                        else {
                            onBrightnessChange(delta)
                        }
                    },
                    onDragStart = { isLeftSide = it.x < size.width / 2 }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggleOverlay()
                    },
                    onDoubleTap = {
                        if (it.x < size.width / 2) {
                            onDoubleTapLeft()
                        } else {
                            onDoubleTapRight()
                        }
                    },
                    onLongPress = { /* TODO: PiP */ }
                )
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsModal(
    playbackSpeed: Float,
    quality: String,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed}x") }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Quality",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            listOf("Auto", "1080p", "720p", "480p", "360p").forEach { q ->
                FilterChip(
                    selected = quality == q,
                    onClick = { onQualityChange(q) },
                    label = { Text(q) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersPanel(
    chapters: List<Chapter>,
    currentPositionMs: Long,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            itemsIndexed(chapters) { index, chapter ->
                val isSelected = currentPositionMs in chapter.startTimeMs..chapter.endTimeMs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            MaterialTheme.shapes.medium
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(chapter.startTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
