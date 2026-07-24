package com.futo.platformplayer.feature.player.impl

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import coil.compose.AsyncImage
import com.futo.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.futo.platformplayer.core.model.ContentItem
import com.futo.platformplayer.core.navigation.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Full-screen video player with gesture controls and overlays.
 * Supports three states: fullscreen, mini-player (floating), and transitions between them.
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
    var showMiniPlayerOptions by remember { mutableStateOf(false) }
    var touchX by remember { mutableStateOf(0f) }

    // Animated transition state: 0f = fullscreen, 1f = mini-player
    val expandFraction = remember { Animatable(0f) }

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
        is PlayerUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navigator.goBack() }) {
                        Text("Go Back")
                    }
                }
            }
        }
        is PlayerUiState.Loaded -> {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val isTablet = configuration.smallestScreenWidthDp >= 600
            val miniPlayerScale = if (isTablet) 0.35f else 0.45f

            // Keep player reference across minimized/expanded states
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

            // Calculate mini-player size
            val screenWidth = configuration.screenWidthDp.dp
            val miniWidth = (screenWidth * miniPlayerScale).coerceAtMost(400.dp)
            val miniHeight = miniWidth * 9f / 16f

            // Determine if player is minimized
            val isMinimized = expandFraction.value > 0.5f

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Scrim/background when transitioning (matches Flow app pattern)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - expandFraction.value).coerceIn(0f, 0.6f)))
                )

                // Video player - scaled and positioned based on expandFraction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fraction = expandFraction.value
                            val scale = 1f - (fraction * (1f - miniPlayerScale))
                            this.scaleX = scale
                            this.scaleY = scale
                            // Position at bottom-right when minimized
                            this.translationX = if (fraction > 0.5f) {
                                size.width * (fraction - 0.5f) * 0.7f
                            } else 0f
                            this.translationY = if (fraction > 0.5f) {
                                size.height * (fraction - 0.5f) * 0.6f
                            } else 0f
                            // Add rounded corners and shadow when minimized
                            if (fraction > 0.1f) {
                                shape = RoundedCornerShape((12f * fraction).dp)
                                clip = true
                                shadowElevation = (8f * fraction).dp.toPx()
                            }
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    // ExoPlayer view
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

                        // Show thumbnail when minimized and not playing
                        if (isMinimized && !state.isPlaying) {
                            AsyncImage(
                                model = state.currentVideo?.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Mini-player controls overlay (only visible when minimized)
                if (isMinimized) {
                    Box(
                        modifier = Modifier
                            .size(miniWidth, miniHeight)
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .graphicsLayer {
                                shape = RoundedCornerShape(12.dp)
                                clip = true
                            }
                    ) {
                        // Dark overlay for better control visibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                        )

                        // Controls
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Top row: Play/Pause (left) and Close (right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (state.isPlaying) viewModel.pause() else viewModel.resume()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            expandFraction.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 300)
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Bottom row: Title and actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.currentVideo?.title ?: "Unknown",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.currentVideo?.author?.name?.isNotEmpty() == true) {
                                        Text(
                                            text = state.currentVideo!!.author!!.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // More options
                                IconButton(
                                    onClick = { showMiniPlayerOptions = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Fullscreen
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            expandFraction.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 300)
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Progress bar
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                LinearProgressIndicator(
                                    progress = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }

                    // Tap to expand
                    Box(
                        modifier = Modifier
                            .size(miniWidth, miniHeight)
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        coroutineScope.launch {
                                            expandFraction.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 300)
                                            )
                                        }
                                    }
                                )
                            }
                    )
                }

                // Fullscreen mode - show overlays and handle gestures
                if (!isMinimized) {
                    // Top overlay
                    AnimatedVisibility(
                        visible = showTopOverlay,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { navigator.goBack() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = state.currentVideo?.title ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.currentVideo?.author?.name?.isNotEmpty() == true) {
                                        Text(
                                            text = state.currentVideo!!.author!!.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { replayEnabled = !replayEnabled },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Replay",
                                            tint = if (replayEnabled) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = { showOptionsModal = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                expandFraction.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(durationMillis = 300)
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Minimize,
                                            contentDescription = "Minimize",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom overlay
                    AnimatedVisibility(
                        visible = showBottomOverlay,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(state.currentPositionMs) + " / " + formatTime(state.durationMs),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Gesture controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        if (offset.x < size.width / 3f) {
                                            // Double tap left - seek back 10s
                                            viewModel.seekTo(state.currentPositionMs - 10000)
                                        } else if (offset.x > size.width * 2f / 3f) {
                                            // Double tap right - seek forward 10s
                                            viewModel.seekTo(state.currentPositionMs + 10000)
                                        } else {
                                            // Double tap center - toggle play/pause
                                            if (state.isPlaying) viewModel.pause() else viewModel.resume()
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (touchX < size.width / 2f) {
                                            showBrightnessIndicator = false
                                        } else {
                                            showVolumeIndicator = false
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val delta = -dragAmount / 500f
                                    if (touchX < size.width / 2f) {
                                        // Left side - brightness
                                        brightnessValue = (brightnessValue + delta).coerceIn(0f, 1f)
                                        showBrightnessIndicator = true
                                    } else {
                                        // Right side - volume
                                        volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                                        showVolumeIndicator = true
                                    }
                                }
                            }
                    ) {
                        // Brightness indicator
                        AnimatedVisibility(
                            visible = showBrightnessIndicator,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 32.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BrightnessHigh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${(brightnessValue * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(60.dp)
                                            .background(Color.White.copy(alpha = 0.5f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight(brightnessValue)
                                                .fillMaxWidth()
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }

                        // Volume indicator
                        AnimatedVisibility(
                            visible = showVolumeIndicator,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 32.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${(volumeValue * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(60.dp)
                                            .background(Color.White.copy(alpha = 0.5f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight(volumeValue)
                                                .fillMaxWidth()
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Seek indicator (placeholder - TODO: implement seek animation)
                    /* TODO: Implement seek indicator when double-tap seek is added */
                }
            }

            // Options modal
            if (showOptionsModal) {
                OptionsModal(
                    currentSpeed = selectedSpeed,
                    currentQuality = selectedQuality,
                    onSpeedSelected = { speed ->
                        selectedSpeed = speed
                        viewModel.setPlaybackSpeed(speed)
                    },
                    onQualitySelected = { quality ->
                        selectedQuality = quality
                    },
                    onDismiss = { showOptionsModal = false }
                )
            }

            // Chapters panel (placeholder - TODO: implement when chapters data is available)
            /* TODO: Implement chapters panel when IPlatformVideo includes chapters data */

            // Dispose player
            DisposableEffect(Unit) {
                onDispose {
                    player.release()
                }
            }
        }
    }
}

@Composable
private fun OptionsModal(
    currentSpeed: Float,
    currentQuality: String,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val qualities = listOf("Auto", "360p", "480p", "720p", "1080p", "1440p", "2160p")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Options") },
        text = {
            Column {
                Text("Playback Speed", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    speeds.forEach { speed ->
                        FilterChip(
                            selected = currentSpeed == speed,
                            onClick = { onSpeedSelected(speed) },
                            label = { Text("${speed}x") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Quality", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    qualities.forEach { quality ->
                        FilterChip(
                            selected = currentQuality == quality,
                            onClick = { onQualitySelected(quality) },
                            label = { Text(quality) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/* Chapters panel implementation will be added when IPlatformVideo includes chapters data */

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
