package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlayerScreen"

@Composable
fun PlayerView(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    Log.d(TAG, "PlayerScreen COMPOSE created (overlay mode)")
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // ==================== True window size (nav-bar independent) ====================
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

    // ==================== State ====================
    var showOptionsModal by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var activeIndicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showMiniPlayerOptions by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }

    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var isLooping by remember { mutableStateOf(false) }

    var miniPlayerOffsetX by remember { mutableStateOf(0f) }
    var miniPlayerOffsetY by remember { mutableStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }

    val morphProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var isDraggingMorph by remember { mutableStateOf(false) }

    val fullscreenProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

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
    val transitionDpSpec = spring<androidx.compose.ui.unit.Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    val isMinimizedAnim = remember { mutableStateOf(false) }
    val isFullscreenAnim = remember { mutableStateOf(false) }
    val playerFadeInProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    val player = remember(uiState) {
        (viewModel as? PlayerViewModel)?.getPlayer()?.exoPlayer
    }

    // Initialize from state on first load only (for indicator defaults).
    // Gesture handler owns indicator state during interaction.
    var initialized by remember { mutableStateOf(false) }
    val loadedState = uiState as? PlayerUiState.Loaded
    if (loadedState != null && !initialized) {
        // Seed initial values so indicators start from current system state
        initialized = true
    }

    // ==================== Animation sync ====================
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
        if (!minimized) controlsVisible = true
        Log.d(TAG, "Animation state synced: isMinimized=$minimized")
    }

    LaunchedEffect(isFullscreenState) {
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        val target = if (fullscreen) 1f else 0f
        if (fullscreen && morphProgress.value < 0.5f) {
            kotlinx.coroutines.delay(50)
        }
        if (kotlin.math.abs(fullscreenProgress.value - target) > 0.01f) {
            fullscreenProgress.animateTo(target, transitionSpringSpec)
        }
        isFullscreenAnim.value = fullscreen
        Log.d(TAG, "Fullscreen synced: isFullscreen=$fullscreen")
    }

    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            playerFadeInProgress.animateTo(1f, tween(durationMillis = 600))
        } else {
            playerFadeInProgress.animateTo(0f)
        }
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
            val dragTravelPx = containerSize.height * 0.45f

            val isMinimized = state.isMinimized
            val isFullscreen = state.isFullscreen

            val p = morphProgress.value
            val cornerRadius = (12f * p).coerceAtLeast(0f).dp
            val fullscreenP = fullscreenProgress.value

            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val isSmallWindow = with(context.resources.displayMetrics) {
                kotlin.math.min(widthPixels, heightPixels) < 600
            }

            // ==================== Orientation & system UI ====================
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
                    val insetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    if (isFullscreen) {
                        kotlinx.coroutines.delay(300)
                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        Log.d(TAG, "System UI hidden for fullscreen (animated)")
                    } else {
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                        Log.d(TAG, "System UI restored (animated)")
                    }
                }
            }

            // ==================== Auto-hide controls ====================
            LaunchedEffect(controlsVisible, state.isPlaying, isMinimizedAnim.value, isFullscreenAnim.value, morphProgress.value) {
                if (morphProgress.value > 0.01f && morphProgress.value < 0.99f) {
                    hideControlsJob?.cancel()
                    return@LaunchedEffect
                }
                val settled = !isMinimizedAnim.value
                val canAutoHide = settled && state.isPlaying && controlsVisible
                if (canAutoHide) {
                    hideControlsJob?.cancel()
                    hideControlsJob = launch {
                        delay(3000)
                        controlsVisible = false
                    }
                } else {
                    hideControlsJob?.cancel()
                }
            }

            LaunchedEffect(state.isPlaying) {
                if (!state.isPlaying) controlsVisible = true
            }

            LaunchedEffect(morphProgress.value) {
                if (morphProgress.value > 0.8f) {
                    controlsVisible = false
                }
            }

            // ==================== Collapsing player height ====================
            val scrollState = rememberLazyListState()
            val maxPlayerHeightPx = containerSize.height * 0.7f
            val minPlayerHeightPx = containerSize.height * 0.2f
            var playerHeightPx by remember { mutableStateOf(0f) }

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

            // ==================== Geometry ====================
            val density = LocalDensity.current
            val miniWidthPx = with(density) { miniWidth.toPx() }
            val miniHeightPx = with(density) { miniHeight.toPx() }
            val paddingPx = 16f * density.density
            val floatingRestX = containerSize.width - miniWidthPx - paddingPx
            val floatingRestY = containerSize.height - miniHeightPx - paddingPx

            LaunchedEffect(containerSize, isDraggingMiniPlayer) {
                if (containerSize == Size.Zero || isDraggingMiniPlayer) return@LaunchedEffect
                val minOffsetX = -floatingRestX
                val maxOffsetX = (containerSize.width - miniWidthPx) - floatingRestX
                val minOffsetY = -floatingRestY
                val maxOffsetY = (containerSize.height - miniHeightPx) - floatingRestY
                if (minOffsetX > maxOffsetX || minOffsetY > maxOffsetY) return@LaunchedEffect
                val clampedX = miniPlayerOffsetX.coerceIn(minOffsetX, maxOffsetX)
                val clampedY = miniPlayerOffsetY.coerceIn(minOffsetY, maxOffsetY)
                if (clampedX != miniPlayerOffsetX) miniPlayerOffsetX = clampedX
                if (clampedY != miniPlayerOffsetY) miniPlayerOffsetY = clampedY
            }

            val layoutDragX = if (isDraggingMiniPlayer) miniPlayerOffsetX else animatedMiniOffsetX
            val layoutDragY = if (isDraggingMiniPlayer) miniPlayerOffsetY else animatedMiniOffsetY

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

            // ==================== Overlay mode ====================
            val overlayMode = when {
                isMinimized && !isFullscreen -> PlayerOverlayMode.FLOATING
                isFullscreen -> PlayerOverlayMode.FULLSCREEN
                isCollapsedControls -> PlayerOverlayMode.COMPACT
                else -> PlayerOverlayMode.NORMAL
            }

            // ==================== Gesture configs (defaults, user overrides later) ====================
            val gestureConfigs = remember {
                com.tsutsen.platformplayer.feature.player.impl.gesture.buildDefaultGestureConfigs()
            }

            // ==================== Gesture action handler ====================
            val gestureHandler = remember {
                com.tsutsen.platformplayer.feature.player.impl.gesture.PlayerGestureActionHandler(
                    viewModel = viewModel,
                    screenHeight = { containerSize.height },
                    context = context,
                    activity = context as? android.app.Activity,
                    onIndicator = { indicator ->
                        activeIndicator = indicator
                    },
                    onIndicatorEnd = {
                        activeIndicator = null // overlay owns the fade-out delay
                    },
                )
            }

            // ==================== Tap handler (toggle controls) ====================
            val onTap: () -> Unit = {
                if (morphProgress.value !in 0.01f..0.99f) {
                    controlsVisible = !controlsVisible
                    hideControlsJob?.cancel()
                    if (controlsVisible && state.isPlaying) {
                        hideControlsJob = coroutineScope.launch {
                            delay(3000)
                            controlsVisible = false
                        }
                    }
                }
            }

            // ==================== Compose ====================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = playerFadeInProgress.value }
                ) {
                    val scrimAlpha = (1f - morphProgress.value) * (1f - fullscreenP) + fullscreenP
                    if (scrimAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = scrimAlpha))
                        )
                    }

                    PlayerContent(
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
                        overlayMode = overlayMode,
                        gestureConfigs = gestureConfigs,
                        gestureHandler = gestureHandler,
                        isScrubbing = isScrubbing,
                        isMorphDragging = isDraggingMorph,
                        onTap = onTap,
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
                        activeIndicator = activeIndicator,
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
                        },
                        onMorphDragStart = { isDraggingMorph = true },
                        onMorphDrag = { dragY ->
                            val progress = (dragY / dragTravelPx).coerceIn(0f, 1f)
                            coroutineScope.launch { morphProgress.snapTo(progress) }
                        },
                        onMorphDragEnd = { dragY ->
                            isDraggingMorph = false
                            val progress = (dragY / dragTravelPx).coerceIn(0f, 1f)
                            if (progress > 0.4f) {
                                viewModel.minimize()
                            } else {
                                coroutineScope.launch { morphProgress.animateTo(0f, transitionSpringSpec) }
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
                }

                // ==================== Modals ====================
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
