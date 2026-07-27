package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlayerScreen"

/**
 * Video player overlay composable.
 * Rendered at the activity level on top of whatever screen is behind it.
 * No navigation needed — player just appears/disappears based on PlayerState.
 *
 * This file hoists state and shared effects (system UI, orientation, auto-hide, scroll state)
 * and delegates to:
 *   - PlayerMorphBox: shared morph box for NORMAL ↔ FLOATING transitions (video box + drag + chrome crossfade)
 *   - FullscreenPlayerContent: fullscreen mode (separate tree, no morph)
 *   - OptionsModal/ChaptersPanel: full-screen dialogs
 *
 * The morph box owns the video box geometry, drag gesture, and chrome crossfade.
 * Windowed content (details LazyColumn) and floating chrome (mini player overlay)
 * are passed as lambdas and crossfade based on morphProgress.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    Log.d(TAG, "PlayerScreen COMPOSE created (overlay mode)")
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var showTopOverlay by remember { mutableStateOf(true) }
    var showBottomOverlay by remember { mutableStateOf(true) }
    var showOptionsModal by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableStateOf(1.0f) }
    var volumeValue by remember { mutableStateOf(1.0f) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showMiniPlayerOptions by remember { mutableStateOf(false) }

    // Detail page state
    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Comments, 1 = Recommended
    var isLooping by remember { mutableStateOf(false) }

    // ==================== Morph state (WINDOWED <-> FLOATING) ====================
    val morphState = rememberPlayerMorphState(
        initialMinimized = (uiState as? PlayerUiState.Loaded)?.isMinimized ?: false
    )

    val transitionSpringSpec = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )
    val transitionDpSpec = spring<Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    // ==================== Animation state ====================
    // isFullscreen is derived directly from uiState — no separate anim state needed
    val isFullscreen = (uiState as? PlayerUiState.Loaded)?.isFullscreen ?: false
    val fullscreenCrossfadeAlpha by animateFloatAsState(
        targetValue = if (isFullscreen) 1f else 0f,
        animationSpec = transitionSpringSpec,
        label = "fullscreenCrossfadeAlpha"
    )

    // Timeline scrub state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    // ==================== ExoPlayer ====================
    val player = remember(uiState) {
        (viewModel as? PlayerViewModel)?.getPlayer()?.exoPlayer
    }

    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            val state = uiState as PlayerUiState.Loaded
            brightnessValue = state.brightness
            volumeValue = state.volume
        }
    }

    // Auto-hide overlays when playing
    LaunchedEffect(showTopOverlay, showBottomOverlay) {
        if (showTopOverlay && showBottomOverlay) {
            // Wait for the player to start playing
            while (true) {
                val currentState = uiState as? PlayerUiState.Loaded
                if (currentState != null && currentState.isPlaying) break
                delay(100)
            }
            // Wait 3 seconds after playing starts
            delay(3000)
            // Hide overlays if still playing
            val finalState = uiState as? PlayerUiState.Loaded
            if (finalState != null && finalState.isPlaying) {
                showTopOverlay = false
                showBottomOverlay = false
            }
        }
    }

    // Sync overlay visibility when exiting fullscreen
    LaunchedEffect(isFullscreen) {
        if (!morphState.progress.value.equals(1f) && !isFullscreen) {
            showTopOverlay = true
            showBottomOverlay = true
        }
        Log.d(TAG, "Overlay visibility synced: morphProgress=${morphState.progress.value}, isFullscreen=$isFullscreen")
    }

    // Sync morph progress when the ViewModel settles the state
    LaunchedEffect(uiState) {
        val loaded = uiState as? PlayerUiState.Loaded ?: return@LaunchedEffect
        val target = if (loaded.isMinimized) 1f else 0f
        if (morphState.progress.value != target) {
            morphState.animateToMode(loaded.isMinimized)
        }
    }

    when (val state = uiState) {
        is PlayerUiState.Initial -> {
            // No player active — don't show anything
        }
        is PlayerUiState.Loaded -> {
            val isTablet = configuration.smallestScreenWidthDp >= 600

            Log.d(TAG, "PlayerScreen entered Loaded state")
            Log.d(TAG, "Current video: ${state.currentVideo?.url}")
            Log.d(TAG, "Is playing: ${state.isPlaying}")
            Log.d(TAG, "Is loading: ${state.isLoading}")
            Log.d(TAG, "Is minimized: ${state.isMinimized}")
            Log.d(TAG, "Is fullscreen: ${state.isFullscreen}")

            LaunchedEffect(state.currentVideo?.url) {
                isScrubbing = false
                scrubPositionMs = 0L
            }

            val miniWidth = 280.dp
            val miniHeight = miniWidth * 9f / 16f
            val isMinimized = state.isMinimized
            val isFullscreen = state.isFullscreen

            // ==================== Morph-driven animated values ====================
            val morphProgress by morphState.progress.asState()

            val morphedCornerRadius by animateDpAsState(
                targetValue = (12 * morphProgress).dp,
                animationSpec = transitionDpSpec,
                label = "morphCornerRadius"
            )

            val fullscreenScrimAlpha by animateFloatAsState(
                targetValue = if (isFullscreen) 0.3f else 0f,
                animationSpec = transitionSpringSpec,
                label = "fullscreenScrimAlpha"
            )

            // ==================== Orientation & system UI ====================
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val isSmallWindow = with(context.resources.displayMetrics) {
                kotlin.math.min(widthPixels, heightPixels) < 600
            }

            LaunchedEffect(isLandscape, isSmallWindow, isFullscreen) {
                if (isLandscape && isSmallWindow && !isFullscreen && !isMinimized) {
                    Log.d(TAG, "Auto-entering fullscreen: landscape + phone")
                    viewModel.toggleFullscreen()
                } else if (!isLandscape && isFullscreen) {
                    Log.d(TAG, "Exiting fullscreen: portrait")
                    viewModel.exitFullscreen()
                }
            }

            LaunchedEffect(Unit) {
                val activity = context as? Activity
                if (activity != null) {
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    Log.d(TAG, "Edge-to-edge enabled for PlayerScreen")
                }
            }

            LaunchedEffect(isFullscreen, isSmallWindow) {
                val activity = context as? Activity
                if (activity != null) {
                    if (isFullscreen) {
                        activity.window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            )
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        Log.d(TAG, "System UI hidden for fullscreen")
                    } else {
                        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                        Log.d(TAG, "System UI restored")
                    }
                }
            }

            // ==================== Auto-hide controls in fullscreen ====================
            var controlsVisible by remember { mutableStateOf(true) }
            var hideControlsJob by remember { mutableStateOf<Job?>(null) }

            LaunchedEffect(isFullscreen, controlsVisible) {
                if (isFullscreen && controlsVisible) {
                    hideControlsJob = launch {
                        delay(3000)
                        controlsVisible = false
                    }
                } else {
                    hideControlsJob?.cancel()
                    if (!isFullscreen) controlsVisible = true
                }
            }

            // ==================== Scroll state ====================
            val scrollState = rememberPlayerScrollState(containerSize.height)
            val isCollapsedControls = !isFullscreen && scrollState.isCollapsedControls

            val gestureCallbacks = PlayerGestureCallbacks(
                onTap = {
                    controlsVisible = true
                    hideControlsJob?.cancel()
                    showTopOverlay = !showTopOverlay
                    showBottomOverlay = !showBottomOverlay
                },
                onDoubleTap = {
                    if (isFullscreen) {
                        Log.d(TAG, "Double-tap: exit fullscreen")
                        viewModel.exitFullscreen()
                    } else {
                        Log.d(TAG, "Double-tap: enter fullscreen")
                        viewModel.toggleFullscreen()
                    }
                },
                onVerticalDrag = { touchX, dragAmountPx, areaWidthPx ->
                    val delta = -dragAmountPx / 500f
                    if (touchX < areaWidthPx / 2) {
                        brightnessValue = (brightnessValue + delta).coerceIn(0f, 1f)
                        viewModel.setBrightness(brightnessValue)
                        showBrightnessIndicator = true
                        coroutineScope.launch {
                            delay(1500)
                            showBrightnessIndicator = false
                        }
                    } else {
                        volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                        viewModel.setVolume(volumeValue)
                        showVolumeIndicator = true
                        coroutineScope.launch {
                            delay(1500)
                            showVolumeIndicator = false
                        }
                    }
                }
            )

            // Morph drag state
            var dragDeltaY by remember { mutableStateOf(0f) }
            var dragStartTime by remember { mutableStateOf(0L) }
            val dragTravelPx = containerSize.height * 0.35f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    }
            ) {
                // ==================== FULLSCREEN mode (crossfaded) ====================
                if (fullscreenCrossfadeAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(fullscreenCrossfadeAlpha)
                            .background(Color.Black.copy(alpha = fullscreenScrimAlpha))
                    ) {
                        FullscreenPlayerContent(
                            player = player,
                            state = state,
                            isLoading = state.isLoading,
                            brightnessValue = brightnessValue,
                            volumeValue = volumeValue,
                            showBrightnessIndicator = showBrightnessIndicator,
                            showVolumeIndicator = showVolumeIndicator,
                            showTopOverlay = showTopOverlay,
                            showBottomOverlay = showBottomOverlay,
                            gestureCallbacks = gestureCallbacks,
                            onMinimize = {
                                Log.d(TAG, "Minimize button clicked")
                                viewModel.minimize()
                            },
                            onReplayToggle = { viewModel.toggleReplay() },
                            onWatchLater = { /* TODO */ },
                            onOptions = { showOptionsModal = true },
                            onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.resume() },
                            onPrevious = { viewModel.skipPrevious() },
                            onNext = { viewModel.skipNext() },
                            onChapters = { showChapters = !showChapters },
                            onFullscreenToggle = { viewModel.toggleFullscreen() },
                            onSeek = { positionMs ->
                                scrubPositionMs = positionMs
                                isScrubbing = true
                                viewModel.seekTo(positionMs)
                            },
                            isScrubbing = isScrubbing,
                            scrubPositionMs = scrubPositionMs
                        )
                    }
                }

                // ==================== NORMAL/FLOATING mode (shared morph box, crossfaded) ====================
                if (fullscreenCrossfadeAlpha < 1f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(1f - fullscreenCrossfadeAlpha)
                    ) {
                        // Video box area (with gesture layer and controls overlay)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(androidx.compose.ui.platform.LocalDensity.current) { scrollState.playerHeightPx.toDp() })
                                .pointerInput(morphState.isDragging) {
                                    if (morphState.isDragging) {
                                        detectDragGestures(
                                            onDragStart = {
                                                morphState.onDragStart()
                                                dragStartTime = System.currentTimeMillis()
                                            },
                                            onDrag = { change, dragAmount ->
                                                dragDeltaY += dragAmount.y
                                                coroutineScope.launch {
                                                    morphState.onDrag(dragAmount.y, dragTravelPx)
                                                }
                                            },
                                            onDragEnd = {
                                                val dragDuration = System.currentTimeMillis() - dragStartTime
                                                val velocityPxPerSec = if (dragDuration > 0) {
                                                    (dragDeltaY / dragDuration) * 1000f
                                                } else {
                                                    0f
                                                }
                                                coroutineScope.launch {
                                                    morphState.onDragEnd(
                                                        velocityPxPerSec = velocityPxPerSec,
                                                        dragTravelPx = dragTravelPx,
                                                        onSettledMinimized = {
                                                            Log.d(TAG, "Morph drag: settled to minimized")
                                                            viewModel.minimize()
                                                        },
                                                        onSettledExpanded = {
                                                            Log.d(TAG, "Morph drag: settled to expanded")
                                                            viewModel.exitMiniPlayer()
                                                        }
                                                    )
                                                }
                                                dragDeltaY = 0f
                                                dragStartTime = 0L
                                            },
                                            onDragCancel = {
                                                dragDeltaY = 0f
                                                dragStartTime = 0L
                                            }
                                        )
                                    }
                                }
                        ) {
                            // Video box (with morph positioning for floating mode)
                            val morphedHeight = scrollState.playerHeightPx - (scrollState.playerHeightPx - with(androidx.compose.ui.platform.LocalDensity.current) { miniHeight.toPx() }) * morphProgress
                            val morphedWidth = containerSize.width - (containerSize.width - with(androidx.compose.ui.platform.LocalDensity.current) { miniWidth.toPx() }) * morphProgress
                            val targetX = (containerSize.width - morphedWidth) * morphProgress
                            val targetY = (containerSize.height - morphedHeight) * morphProgress
                            val morphedCornerRadius = 12f * morphProgress

                            Box(
                                modifier = Modifier
                                    .width(with(androidx.compose.ui.platform.LocalDensity.current) { morphedWidth.toDp() })
                                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { morphedHeight.toDp() })
                                    .offset { IntOffset(targetX.toInt(), targetY.toInt()) }
                                    .clip(RoundedCornerShape(with(androidx.compose.ui.platform.LocalDensity.current) { morphedCornerRadius.toDp() }))
                            ) {
                                PlayerVideoSurface(player = player)
                            }

                            // Gesture layer (tap, double-tap, vertical drag for brightness/volume)
                            val gestureMode = if (isFullscreen) PlayerMode.FULLSCREEN else (if (isCollapsedControls) PlayerMode.COMPACT else PlayerMode.NORMAL)
                            PlayerGestureLayer(
                                modifier = Modifier.fillMaxSize(),
                                callbacks = gestureCallbacks,
                                mode = gestureMode
                            )

                            // Brightness/Volume indicators
                            if (showBrightnessIndicator) {
                                BrightnessIndicator(brightness = brightnessValue, modifier = Modifier.align(Alignment.CenterStart))
                            }
                            if (showVolumeIndicator) {
                                VolumeIndicator(volume = volumeValue, modifier = Modifier.align(Alignment.CenterEnd))
                            }

                            // Top bar
                            if (showTopOverlay && !isCollapsedControls) {
                                TopOverlay(
                                    title = state.currentVideo?.title ?: "Unknown",
                                    channelName = state.currentVideo?.author?.name ?: "Unknown",
                                    onMinimize = { viewModel.minimize() },
                                    onReplayToggle = { viewModel.toggleReplay() },
                                    onWatchLater = { /* TODO */ },
                                    onOptions = { showOptionsModal = true }
                                )
                            }

                            // Bottom bar
                            if (isCollapsedControls || showBottomOverlay) {
                                if (isCollapsedControls) {
                                    CompactControlsRow(
                                        isPlaying = state.isPlaying,
                                        isLooping = isLooping,
                                        onMinimize = { viewModel.minimize() },
                                        onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.resume() },
                                        onChapters = { showChapters = !showChapters },
                                        onLoopToggle = {
                                            isLooping = !isLooping
                                            player?.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                        },
                                        onWatchLater = { /* TODO */ },
                                        onOptions = { showOptionsModal = true },
                                        onFullscreen = { viewModel.toggleFullscreen() }
                                    )
                                } else {
                                    BottomOverlay(
                                        player = player,
                                        currentPositionMs = state.currentPositionMs,
                                        durationMs = state.durationMs,
                                        isPlaying = state.isPlaying,
                                        onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.resume() },
                                        onPrevious = { viewModel.skipPrevious() },
                                        onNext = { viewModel.skipNext() },
                                        onChapters = { showChapters = !showChapters },
                                        onFullscreen = { viewModel.toggleFullscreen() },
                                        onSeek = { positionMs ->
                                            scrubPositionMs = positionMs
                                            isScrubbing = true
                                            viewModel.seekTo(positionMs)
                                        },
                                        isScrubbing = isScrubbing,
                                        scrubPositionMs = scrubPositionMs
                                    )
                                }
                            }
                        }

                        // Details LazyColumn (fills remaining space below video box)
                        WindowedPlayerContent(
                            player = player,
                            state = state,
                            scrollState = scrollState.scrollState,
                            expandedDescription = expandedDescription,
                            onToggleDescription = { expandedDescription = !expandedDescription },
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            onLoadMoreComments = { viewModel.loadMoreComments(state.currentVideo?.url ?: "") },
                            nestedScrollConnection = scrollState.nestedScrollConnection
                        )
                    }
                }

                // ==================== Options modal ====================
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

                // ==================== Chapters panel ====================
                if (showChapters) {
                    ChaptersPanel(
                        chapters = emptyList(),
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
                modifier = Modifier.fillMaxSize().background(Color.Black),
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
