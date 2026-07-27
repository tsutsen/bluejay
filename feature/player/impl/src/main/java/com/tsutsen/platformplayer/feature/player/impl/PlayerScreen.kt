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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
 * This file only hoists state and shared effects (system UI, orientation, auto-hide, the
 * collapsing-height nested-scroll math) and then dispatches to one of the four mode-specific
 * composables based on [PlayerMode]:
 *   FLOATING   -> FloatingPlayerContent.kt
 *   NORMAL/COMPACT -> WindowedPlayerContent.kt (COMPACT is a control-row swap inside NORMAL)
 *   FULLSCREEN -> FullscreenPlayerContent.kt
 * Shared video-surface and control-scaffold code lives in PlayerVideoSurface.kt and
 * PlayerControlsScaffold.kt respectively.
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
    // Continuous 0f→1f progress replaces the old boolean isMinimizedAnim. Drives the
    // video-box height, corner radius, and (in floating mode) the mini-player offset
    // so the morph tracks the finger 1:1 during a drag instead of only animating after.
    val morphState = rememberPlayerMorphState(
        initialMinimized = (uiState as? PlayerUiState.Loaded)?.isMinimized ?: false
    )

    // Animated offset for the floating mini-player snap animation (separate from morph drag).
    // When morphState.progress == 1f the mini-player is rendered; this offset lets it glide
    // to its snapped corner position after a drag settles.
    var miniPlayerOffsetX by remember { mutableStateOf(0f) }
    var miniPlayerOffsetY by remember { mutableStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }

    // Animated offset for snap animation - faster, snappier
    val animatedMiniOffsetX by animateFloatAsState(
        targetValue = miniPlayerOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "animatedMiniOffsetX"
    )
    val animatedMiniOffsetY by animateFloatAsState(
        targetValue = miniPlayerOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "animatedMiniOffsetY"
    )

    val transitionSpringSpec = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )
    val transitionDpSpec = spring<Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    // ==================== Animation state (persists across recompositions) ====================
    val isFullscreenAnim = remember { mutableStateOf(false) }

    // Timeline scrub state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    // ==================== ExoPlayer (from repository) ====================
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
            var currentState: PlayerUiState.Loaded? = uiState as? PlayerUiState.Loaded
            while (currentState == null || !currentState.isPlaying) {
                delay(100)
                currentState = uiState as? PlayerUiState.Loaded
            }
            delay(3000)
            currentState = uiState as? PlayerUiState.Loaded
            if (currentState != null && currentState.isPlaying) {
                showTopOverlay = false
                showBottomOverlay = false
            }
        }
    }

    // Sync animation state with actual state
    val isFullscreenState = (uiState as? PlayerUiState.Loaded)?.isFullscreen

    LaunchedEffect(isFullscreenState) {
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        isFullscreenAnim.value = fullscreen
        if (!morphState.progress.value.equals(1f) && !fullscreen) {
            showTopOverlay = true
            showBottomOverlay = true
        }
        Log.d(TAG, "Animation state synced: morphProgress=${morphState.progress.value}, isFullscreen=$fullscreen")
    }

    // Sync morph progress when the ViewModel settles the state (button tap, etc.)
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
            // These are derived directly from morphState.progress (0f=windowed, 1f=floating)
            // so the UI tracks the finger 1:1 during a drag.
            val morphProgress by morphState.progress.asState()

            // Video box dimensions: lerp between windowed (full-width, scroll-driven height)
            // and floating (miniWidth, miniHeight).
            val morphedCornerRadius by animateDpAsState(
                targetValue = (12 * morphProgress).dp,
                animationSpec = transitionDpSpec,
                label = "morphCornerRadius"
            )

            // Floating-mode offset: only applied when morph is complete (progress ≈ 1f).
            // During the morph the windowed player stays in its scroll-driven position.
            val floatingOffsetX by animateFloatAsState(
                targetValue = if (morphProgress > 0.99f) miniPlayerOffsetX else 0f,
                animationSpec = transitionSpringSpec,
                label = "floatingOffsetX"
            )
            val floatingOffsetY by animateFloatAsState(
                targetValue = if (morphProgress > 0.99f) miniPlayerOffsetY else 0f,
                animationSpec = transitionSpringSpec,
                label = "floatingOffsetY"
            )

            val fullscreenScrimAlpha by animateFloatAsState(
                targetValue = if (isFullscreenAnim.value) 0.3f else 0f,
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

            // ==================== Scroll state (extracted to PlayerScrollState) ====================
            val scrollState = rememberPlayerScrollState(containerSize.height)
            val isCollapsedControls = !isFullscreenAnim.value && scrollState.isCollapsedControls

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
                onVerticalDragStart = { },
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

            // Drag-to-morph state for the windowed video box
            var dragDeltaY by remember { mutableStateOf(0f) }
            var isMorphDragging by remember { mutableStateOf(false) }
            val dragTravelPx = containerSize.height * 0.35f // total drag for full 0→1 morph

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    }
            ) {
                if (isFullscreenAnim.value) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = fullscreenScrimAlpha)))
                }

                // ==================== Mode dispatch ====================
                // FLOATING: morphProgress > 0.5f (morphed to mini)
                // FULLSCREEN: isFullscreenAnim.value
                // NORMAL/COMPACT: otherwise (windowed, with COMPACT determined by scroll)
                if (isFullscreenAnim.value) {
                    // FULLSCREEN mode
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
                } else if (morphProgress > 0.5f) {
                    // FLOATING mode (morphed to mini)
                    FloatingPlayerContent(
                        player = player,
                        state = state,
                        miniWidth = miniWidth,
                        miniHeight = miniHeight,
                        cornerRadius = morphedCornerRadius,
                        offsetX = floatingOffsetX,
                        offsetY = floatingOffsetY,
                        containerWidth = containerSize.width,
                        containerHeight = containerSize.height,
                        isDragging = isDraggingMiniPlayer,
                        onDragStateChanged = { isDraggingMiniPlayer = it },
                        onOffsetChanged = { x, y -> miniPlayerOffsetX = x; miniPlayerOffsetY = y },
                        onExpand = { viewModel.exitMiniPlayer() },
                        onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.resume() },
                        onClose = {
                            Log.d(TAG, "Close mini player: close")
                            viewModel.close()
                        },
                        onMoreOptions = { showMiniPlayerOptions = true },
                        onFullscreen = {
                            Log.d(TAG, "Fullscreen from mini player: exit mini then enter fullscreen")
                            viewModel.exitMiniPlayer()
                            viewModel.toggleFullscreen()
                        }
                    )
                } else {
                    // NORMAL/COMPACT mode (windowed)
                    val morphedPlayerHeightPx = scrollState.playerHeightPx - (scrollState.playerHeightPx - scrollState.minPlayerHeightPx) * morphProgress

                    WindowedPlayerContent(
                        modifier = Modifier.pointerInput(isMorphDragging) {
                            if (isMorphDragging) {
                                detectDragGestures(
                                    onDragStart = {
                                        morphState.onDragStart()
                                        isMorphDragging = true
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDeltaY += dragAmount.y
                                        // Positive drag (down) → increases progress toward floating
                                        coroutineScope.launch {
                                            morphState.onDrag(dragAmount.y, dragTravelPx)
                                        }
                                    },
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            morphState.onDragEnd(
                                                velocityPxPerSec = dragDeltaY * 60f, // rough px/s
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
                                        isMorphDragging = false
                                        dragDeltaY = 0f
                                    },
                                    onDragCancel = {
                                        isMorphDragging = false
                                        dragDeltaY = 0f
                                    }
                                )
                            }
                        },
                        player = player,
                        state = state,
                        playerHeightPx = morphedPlayerHeightPx,
                        scrollState = scrollState.scrollState,
                        nestedScrollConnection = scrollState.nestedScrollConnection,
                        isCollapsedControls = isCollapsedControls,
                        expandedDescription = expandedDescription,
                        onToggleDescription = { expandedDescription = !expandedDescription },
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onLoadMoreComments = { viewModel.loadMoreComments(state.currentVideo?.url ?: "") },
                        cornerRadius = morphedCornerRadius,
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
                        scrubPositionMs = scrubPositionMs,
                        isLooping = isLooping,
                        onLoopToggle = {
                            isLooping = !isLooping
                            player?.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        }
                    )
                }

                // ==================== Options modal (full-screen dialog) ====================
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

                // ==================== Chapters panel (full-screen dialog) ====================
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
