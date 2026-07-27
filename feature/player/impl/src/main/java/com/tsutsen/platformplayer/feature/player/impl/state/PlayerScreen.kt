package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.tsutsen.platformplayer.feature.player.impl.ui.UnifiedPlayerContent
import com.tsutsen.platformplayer.feature.player.impl.ui.computeVideoLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlayerScreen"

/**
 * Video player overlay composable.
 * Rendered at the activity level on top of whatever screen is behind it.
 * No navigation needed — player just appears/disappears based on PlayerState.
 *
 * This file hoists state and shared effects (system UI, orientation, auto-hide, the
 * collapsing-height nested-scroll math) and dispatches to [UnifiedPlayerContent], which
 * handles all four modes (NORMAL, COMPACT, FLOATING, FULLSCREEN) in a single persistent
 * composable tree with smooth geometry transitions via [computeVideoLayout].
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

    // ==================== True window size (nav-bar independent) ====================
    // Read directly from the platform View rather than from a locally-measured Compose
    // container. containerSize (measured further down via onGloballyPositioned) reflects
    // whatever constraints this composable's ancestors give it, which may shrink to make
    // room for the nav bar and only expand once it's hidden. The underlying View here is
    // the real window content area — already edge-to-edge — so its size doesn't change
    // when the nav bar is shown/hidden, only on genuine resizes (rotation, multi-window,
    // fold/unfold). Fullscreen geometry is driven off this instead, so entering fullscreen
    // no longer waits on the nav bar being hidden first.
    val view = LocalView.current
    var windowWidthPx by remember { mutableStateOf(0f) }
    var windowHeightPx by remember { mutableStateOf(0f) }
    DisposableEffect(view) {
        fun syncWindowSize() {
            windowWidthPx = view.width.toFloat()
            windowHeightPx = view.height.toFloat()
        }
        syncWindowSize()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncWindowSize() }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }

    var showOptionsModal by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableStateOf(1.0f) }
    var volumeValue by remember { mutableStateOf(1.0f) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showMiniPlayerOptions by remember { mutableStateOf(false) }

    // Control visibility (unified system)
    var controlsVisible by remember { mutableStateOf(true) }
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }

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

    // Fullscreen progress — continuous 0..1 from NORMAL to FULLSCREEN
    val fullscreenProgress = remember { Animatable(0f) }

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

    val transitionSpringSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
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

    // Sync animation state with actual state
    val isMinimizedState = (uiState as? PlayerUiState.Loaded)?.isMinimized
    val isFullscreenState = (uiState as? PlayerUiState.Loaded)?.isFullscreen

    LaunchedEffect(isMinimizedState, isDraggingMorph) {
        if (isDraggingMorph) return@LaunchedEffect
        val minimized = isMinimizedState ?: return@LaunchedEffect
        val target = if (minimized) 1f else 0f
        if (kotlin.math.abs(morphProgress.value - target) > 0.01f) {
            morphProgress.animateTo(target, transitionSpringSpec)
        }
        isMinimizedAnim.value = minimized
        if (!minimized) {
            controlsVisible = true
        }
        Log.d(TAG, "Animation state synced: isMinimized=$minimized")
    }

    LaunchedEffect(isFullscreenState) {
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        val target = if (fullscreen) 1f else 0f
        // When entering fullscreen, wait for the system UI to take effect
        // before starting the animation. This avoids the stutter where the
        // container is nav-bar-constrained and then jumps when the nav bar hides.
        if (fullscreen && morphProgress.value < 0.5f) {
            kotlinx.coroutines.delay(50)
        }
        if (kotlin.math.abs(fullscreenProgress.value - target) > 0.01f) {
            fullscreenProgress.animateTo(target, transitionSpringSpec)
        }
        isFullscreenAnim.value = fullscreen
        Log.d(TAG, "Fullscreen synced: isFullscreen=$fullscreen")
    }

    when (val state = uiState) {
        is PlayerUiState.Initial -> {
            // No player active — don't show anything
        }
        is PlayerUiState.Loaded -> {
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
            val dragTravelPx = containerSize.height * 0.45f  // 45% of screen height = full morph

            val isMinimized = state.isMinimized
            val isFullscreen = state.isFullscreen

            // ==================== Animated values (lerp-derived from morphProgress) ====================
            // Sync morphProgress to the discrete state via the LaunchedEffect above.
            // NOTE: morphProgress.value is intentionally NOT a key in the LaunchedEffect —
            // including it would restart the effect on every animation frame, cancelling/restarting the spring
            // and producing sluggish or stuttering motion.

            val p = morphProgress.value
            val cornerRadius = (12f * p).coerceAtLeast(0f).dp

            // Fullscreen progress for unified composable
            val fullscreenP = fullscreenProgress.value

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

            // ==================== Auto-hide controls ====================
            // Unified system: controlsVisible drives both top and bottom bars.
            // Auto-hide after 3s when playing and settled in NORMAL or FULLSCREEN.
            // Force visible during transitions and while scrubbing.

            // Auto-hide controls in NORMAL and FULLSCREEN when playing and settled
            // Skip auto-hide during morph drag
            LaunchedEffect(controlsVisible, state.isPlaying, isMinimizedAnim.value, isFullscreenAnim.value, morphProgress.value) {
                // Don't auto-hide during morph
                if (morphProgress.value > 0.01f && morphProgress.value < 0.99f) {
                    hideControlsJob?.cancel()
                    return@LaunchedEffect
                }
                val settled = !isMinimizedAnim.value // mini has its own chrome
                val canAutoHide = settled && state.isPlaying && controlsVisible
                if (canAutoHide) {
                    hideControlsJob?.cancel()
                    hideControlsJob = launch {
                        delay(3000)
                        controlsVisible = false
                    }
                } else {
                    hideControlsJob?.cancel()
                    // Don't force true here — only force when entering modes / scrubbing
                }
            }

            // Show controls when pausing
            LaunchedEffect(state.isPlaying) {
                if (!state.isPlaying) controlsVisible = true
            }

            // Hide controls during morph drag (progress > 0.8)
            LaunchedEffect(morphProgress.value) {
                if (morphProgress.value > 0.8f) {
                    controlsVisible = false
                }
            }

            // ==================== Collapsing player height (shared by video content AND controls overlay) ====================
            val scrollState = rememberLazyListState()
            val maxPlayerHeightPx = containerSize.height * 0.7f
            val minPlayerHeightPx = containerSize.height * 0.2f

            var playerHeightPx by remember { mutableStateOf(0f) }

            // Reset player height when leaving mini/fullscreen to avoid stale layout
            LaunchedEffect(isMinimizedAnim.value, isFullscreenAnim.value) {
                if (!isMinimizedAnim.value && !isFullscreenAnim.value) {
                    playerHeightPx = maxPlayerHeightPx
                }
            }

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

            // Compute unified video layout geometry
            val density = LocalDensity.current
            val miniWidthPx = with(density) { miniWidth.toPx() }
            val miniHeightPx = with(density) { miniHeight.toPx() }
            val paddingPx = 16f * density.density  // 16dp in pixels
            // Floating resting position: BottomEnd + 16dp padding
            val floatingRestX = containerSize.width - miniWidthPx - paddingPx
            val floatingRestY = containerSize.height - miniHeightPx - paddingPx

            // Re-clamp the remembered offset whenever the container size changes (rotation,
            // fold/unfold, multi-window resize). floatingRestX/Y move with the container but
            // miniPlayerOffsetX/Y is a raw pixel delta from that anchor, so without this a
            // position snapped away from the default corner could end up partially or fully
            // off-screen after a resize. Skipped while actively dragging so it doesn't fight
            // the gesture.
            LaunchedEffect(containerSize, isDraggingMiniPlayer) {
                if (containerSize == Size.Zero || isDraggingMiniPlayer) return@LaunchedEffect
                val minOffsetX = -floatingRestX
                val maxOffsetX = (containerSize.width - miniWidthPx) - floatingRestX
                val minOffsetY = -floatingRestY
                val maxOffsetY = (containerSize.height - miniHeightPx) - floatingRestY
                // Guard against a degenerate container smaller than the mini player itself
                // (e.g. a very small multi-window split), where min > max would crash coerceIn.
                if (minOffsetX > maxOffsetX || minOffsetY > maxOffsetY) return@LaunchedEffect
                val clampedX = miniPlayerOffsetX.coerceIn(minOffsetX, maxOffsetX)
                val clampedY = miniPlayerOffsetY.coerceIn(minOffsetY, maxOffsetY)
                if (clampedX != miniPlayerOffsetX) miniPlayerOffsetX = clampedX
                if (clampedY != miniPlayerOffsetY) miniPlayerOffsetY = clampedY
            }

            // Use raw offset during drag to avoid fighting the spring
            val layoutDragX = if (isDraggingMiniPlayer) miniPlayerOffsetX else animatedMiniOffsetX
            val layoutDragY = if (isDraggingMiniPlayer) miniPlayerOffsetY else animatedMiniOffsetY

            // Always use the measured Compose container for NORMAL geometry.
            // Fullscreen target uses the stable window size so the animation
            // does not wait on (or jump with) the nav bar.
            val videoLayout = computeVideoLayout(
                miniProgress = morphProgress.value,
                fullscreenProgress = fullscreenP,
                containerWidth = containerSize.width,
                containerHeight = containerSize.height,
                playerHeightPx = playerHeightPx,
                miniWidthPx = miniWidthPx,
                miniHeightPx = miniHeightPx,
                floatingRestX = floatingRestX,
                floatingRestY = floatingRestY,
                dragOffsetX = layoutDragX,
                dragOffsetY = layoutDragY,
                fullscreenWidthPx = if (windowWidthPx > 0f) windowWidthPx else containerSize.width,
                fullscreenHeightPx = if (windowHeightPx > 0f) windowHeightPx else containerSize.height
            )

            val gestureCallbacks = PlayerGestureCallbacks(
                onTap = {
                    // Don't toggle controls during morph
                    if (morphProgress.value > 0.01f && morphProgress.value < 0.99f) return@PlayerGestureCallbacks
                    controlsVisible = !controlsVisible
                    hideControlsJob?.cancel()
                    if (controlsVisible && state.isPlaying) {
                        // Restart auto-hide timer via the LaunchedEffect above
                        hideControlsJob = coroutineScope.launch {
                            delay(3000)
                            controlsVisible = false
                        }
                    }
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
                // Scrim: dim content behind the player overlay in windowed modes.
                // In fullscreen mode, the scrim is always 100% opaque black.
                val scrimAlpha = (1f - morphProgress.value) * (1f - fullscreenP) + fullscreenP
                if (scrimAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                    )
                }

                // Unified player content — handles NORMAL, COMPACT, FLOATING, and FULLSCREEN
                // in a single persistent composable tree with smooth geometry transitions.
                UnifiedPlayerContent(
                    player = player,
                    state = state,
                    videoLayout = videoLayout,
                    miniProgress = morphProgress.value,
                    fullscreenProgress = fullscreenP,
                    containerWidth = containerSize.width,
                    containerHeight = containerSize.height,
                    playerHeightPx = playerHeightPx,
                    miniWidthPx = miniWidthPx,
                    miniHeightPx = miniHeightPx,
                    floatingRestX = floatingRestX,
                    floatingRestY = floatingRestY,
                    isCollapsedControls = isCollapsedControls,
                    controlsVisible = controlsVisible,
                    showTopOverlay = controlsVisible,
                    showBottomOverlay = controlsVisible,
                    scrollState = scrollState,
                    nestedScrollConnection = nestedScrollConnection,
                    gestureCallbacks = gestureCallbacks,
                    isDraggingMiniPlayer = isDraggingMiniPlayer,
                    onDragStateChanged = { isDraggingMiniPlayer = it },
                    onOffsetChanged = { x, y -> miniPlayerOffsetX = x; miniPlayerOffsetY = y },
                    currentOffsetX = miniPlayerOffsetX,
                    currentOffsetY = miniPlayerOffsetY,
                    onOptions = { showOptionsModal = true },
                    onChapters = { showChapters = !showChapters },
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
                    isScrubbing = isScrubbing,
                    scrubPositionMs = scrubPositionMs,
                    isLooping = isLooping,
                    onLoopToggle = {
                        isLooping = !isLooping
                        player?.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    },
                    onMinimize = {
                        Log.d(TAG, "Minimize button clicked")
                        viewModel.minimize()
                    },
                    onFullscreen = {
                        Log.d(TAG, "Fullscreen button clicked")
                        viewModel.toggleFullscreen()
                    },
                    onExpand = {
                        viewModel.exitMiniPlayer()
                        // Deliberately NOT resetting miniPlayerOffsetX/Y here — keeping it
                        // lets the mini player reopen at the same corner it was last
                        // dragged/snapped to instead of always resetting to bottom-right.
                    },
                    onMorphDragStart = {
                        isDraggingMorph = true
                    },
                    onMorphDrag = { dragY ->
                        // Convert dragY to progress 0..1
                        val progress = (dragY / dragTravelPx).coerceIn(0f, 1f)
                        coroutineScope.launch {
                            morphProgress.snapTo(progress)
                        }
                    },
                    onMorphDragEnd = { dragY ->
                        isDraggingMorph = false
                        val progress = (dragY / dragTravelPx).coerceIn(0f, 1f)
                        if (progress > 0.4f) {
                            // Commit: minimize. miniPlayerOffsetX/Y is intentionally left as-is
                            // so the mini player reopens at whatever corner it was last
                            // snapped to (see onExpand above for the matching change).
                            viewModel.minimize()
                        } else {
                            // Reject: spring back to NORMAL
                            coroutineScope.launch {
                                morphProgress.animateTo(0f, transitionSpringSpec)
                            }
                        }
                    },
                    onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.resume() },
                    onClose = {
                        Log.d(TAG, "Close mini player: close")
                        viewModel.close()
                    },
                    onReplayToggle = { viewModel.toggleReplay() },
                    onWatchLater = { /* TODO */ },
                    onPrevious = { viewModel.skipPrevious() },
                    onNext = { viewModel.skipNext() },
                    onSeek = { positionMs ->
                        scrubPositionMs = positionMs
                        isScrubbing = true
                        viewModel.seekTo(positionMs)
                    },
                    onMoreOptions = { showMiniPlayerOptions = true },
                    onFullscreenToggle = { viewModel.toggleFullscreen() }
                )

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
