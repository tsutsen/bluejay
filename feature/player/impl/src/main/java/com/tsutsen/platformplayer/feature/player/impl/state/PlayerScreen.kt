package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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

    // Mini (FLOATING) player drag state
    var miniPlayerOffsetX by remember { mutableStateOf(0f) }
    var miniPlayerOffsetY by remember { mutableStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }

    // Morph (drag-to-mini) state — continuous 0..1 progress from NORMAL to FLOATING
    val morphProgress = remember { Animatable(0f) }
    var isDraggingMorph by remember { mutableStateOf(false) }

    // Timeline scrub state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

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
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioMediumBouncy
    )
    val transitionDpSpec = spring<Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    // ==================== Animation state (persists across recompositions) ====================
    val isMinimizedAnim = remember { mutableStateOf(false) }
    val isFullscreenAnim = remember { mutableStateOf(false) }

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
    val isMinimizedState = (uiState as? PlayerUiState.Loaded)?.isMinimized
    val isFullscreenState = (uiState as? PlayerUiState.Loaded)?.isFullscreen

    LaunchedEffect(isMinimizedState, isFullscreenState) {
        val minimized = isMinimizedState ?: return@LaunchedEffect
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        // Expanding from mini: snap morph to 0 so Windowed mounts at full size
        // instead of crawling slowly from full-mini geometry.
        if (!minimized && isMinimizedAnim.value) {
            morphProgress.snapTo(0f)
        }
        isMinimizedAnim.value = minimized
        isFullscreenAnim.value = fullscreen
        if (!minimized) {
            showTopOverlay = true
            showBottomOverlay = true
        }
        Log.d(TAG, "Animation state synced: isMinimized=$minimized, isFullscreen=$fullscreen")
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
            val dragTravelPx = containerSize.height * 0.9f  // 90% drag = full mini

            val isMinimized = state.isMinimized
            val isFullscreen = state.isFullscreen

            // ==================== Animated values (lerp-derived from morphProgress) ====================
            // Sync morphProgress to the discrete state only when the user isn't dragging.
            // Gated to skip if morphProgress is already within epsilon of target — otherwise it
            // redundantly re-animates to the same value every time isMinimizedAnim flips.
            // NOTE: morphProgress.value is intentionally NOT a key here — including it would
            // restart the effect on every animation frame, cancelling/restarting the spring
            // and producing sluggish or stuttering motion.
            val morphTarget = if (isMinimizedAnim.value) 1f else 0f
            LaunchedEffect(isMinimizedAnim.value) {
                if (!isDraggingMorph && kotlin.math.abs(morphProgress.value - morphTarget) > 0.01f) {
                    morphProgress.animateTo(
                        targetValue = morphTarget,
                        animationSpec = transitionSpringSpec
                    )
                }
            }

            val p = morphProgress.value
            val cornerRadius = (12f * p).coerceAtLeast(0f).dp

            // detailAlpha fades over the last 40% of the gesture
            val detailAlpha = (1f - (morphProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
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

            // ==================== Collapsing player height (shared by video content AND controls overlay) ====================
            val scrollState = rememberLazyListState()
            val maxPlayerHeightPx = containerSize.height * 0.7f
            val minPlayerHeightPx = containerSize.height * 0.2f

            var playerHeightPx by remember { mutableStateOf(0f) }

            LaunchedEffect(maxPlayerHeightPx, minPlayerHeightPx) {
                playerHeightPx = if (playerHeightPx == 0f) {
                    maxPlayerHeightPx
                } else {
                    playerHeightPx.coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                }
            }

            val nestedScrollConnection = remember(minPlayerHeightPx, maxPlayerHeightPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        val previousHeight = playerHeightPx
                        val consumed = when {
                            delta < 0f -> {
                                val newHeight = (previousHeight + delta).coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                                newHeight - previousHeight
                            }
                            delta > 0f &&
                                scrollState.firstVisibleItemIndex == 0 &&
                                scrollState.firstVisibleItemScrollOffset == 0 -> {
                                val newHeight = (previousHeight + delta).coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                                newHeight - previousHeight
                            }
                            else -> 0f
                        }
                        if (consumed != 0f) {
                            playerHeightPx += consumed
                        }
                        return Offset(0f, consumed)
                    }
                }
            }

            val isCollapsedControls = !isFullscreenAnim.value &&
                containerSize.height > 0f &&
                (playerHeightPx / containerSize.height) <= 0.45f

            // Morph width/height from container size to fixed mini dimensions
            val density = LocalDensity.current
            val miniWidthPx = with(density) { miniWidth.toPx() }
            val miniHeightPx = with(density) { miniHeight.toPx() }
            val morphWidth = containerSize.width - (containerSize.width - miniWidthPx) * p
            val morphHeight = playerHeightPx - (playerHeightPx - miniHeightPx) * p

            // Compute resting translation targets matching FloatingPlayerContent's layout:
            //   align(BottomEnd) + padding(16.dp) → top-left at (containerWidth - miniWidth - 16dp, containerHeight - miniHeight - 16dp)
            val paddingPx = 16f * density.density  // 16dp in pixels
            val miniRestingTranslationX = containerSize.width - miniWidthPx - paddingPx
            val miniRestingTranslationY = containerSize.height - miniHeightPx - paddingPx
            val morphTranslationX = miniRestingTranslationX * p
            val morphTranslationY = miniRestingTranslationY * p

            // playerMode is the single source of truth other files should key off - see
            // PlayerMode.kt. Kept here for logging/analytics hooks even though the `when`
            // below still reads the underlying isMinimizedAnim/isFullscreenAnim directly
            // (those drive the cross-fade animation state, not just the discrete mode).
            val playerMode = computePlayerMode(
                isMinimized = isMinimizedAnim.value,
                isFullscreen = isFullscreenAnim.value,
                isCollapsedControls = isCollapsedControls
            )
            Log.d(TAG, "playerMode=$playerMode")

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

                when (playerMode) {
                    PlayerMode.FLOATING -> FloatingPlayerContent(
                        player = player,
                        state = state,
                        miniWidth = miniWidth,
                        miniHeight = miniHeight,
                        cornerRadius = cornerRadius,
                        offsetX = animatedMiniOffsetX,
                        offsetY = animatedMiniOffsetY,
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

                    PlayerMode.FULLSCREEN -> FullscreenPlayerContent(
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

                    PlayerMode.NORMAL, PlayerMode.COMPACT -> WindowedPlayerContent(
                        modifier = Modifier.graphicsLayer {
                            translationX = morphTranslationX
                            translationY = morphTranslationY
                            shape = RoundedCornerShape(cornerRadius)
                            clip = true
                        },
                        player = player,
                        state = state,
                        playerHeightPx = playerHeightPx,
                        scrollState = scrollState,
                        nestedScrollConnection = nestedScrollConnection,
                        isCollapsedControls = isCollapsedControls,
                        expandedDescription = expandedDescription,
                        onToggleDescription = { expandedDescription = !expandedDescription },
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onLoadMoreComments = { viewModel.loadMoreComments(state.currentVideo?.url ?: "") },
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
                        },
                        morphProgress = morphProgress.value,
                        morphWidth = morphWidth,
                        morphHeight = morphHeight,
                        miniHeight = miniHeight,
                        onMorphDragStart = {
                            isDraggingMorph = true
                        },
                        onMorphDrag = { deltaY ->
                            if (dragTravelPx > 0f) {
                                coroutineScope.launch {
                                    morphProgress.snapTo(
                                        (morphProgress.value + deltaY / dragTravelPx).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        },
                        onMorphDragEnd = {
                            isDraggingMorph = false
                            coroutineScope.launch {
                                if (morphProgress.value > 0.5f) {
                                    morphProgress.animateTo(1f, transitionSpringSpec)
                                    viewModel.minimize()
                                } else {
                                    morphProgress.animateTo(0f, transitionSpringSpec)
                                }
                            }
                        },
                        onClose = {
                            Log.d(TAG, "Close mini player: close")
                            viewModel.close()
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
