package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import com.tsutsen.platformplayer.core.model.PlayerMode
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableStateOf(1.0f) }
    var volumeValue by remember { mutableStateOf(1.0f) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showMiniPlayerOptions by remember { mutableStateOf(false) }
    
    // Single Job references for brightness/volume indicator hide delays
    // to prevent race conditions during continuous drag
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }

    val autoHide = rememberAutoHideState(autoHideMs = PlayerMorphConfig.Default.autoHideMs)

    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var isLooping by remember { mutableStateOf(false) }

    var miniPlayerOffsetX by remember { mutableStateOf(0f) }
    var miniPlayerOffsetY by remember { mutableStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }

    val morph = rememberMorphState(onMinimize = { viewModel.minimize() })
    val fullscreen = rememberFullscreenState()

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }
    
    // Extracted gesture state management
    val gestureState = rememberGestureState()

    // Effective morph progress: dragMorphProgress during drag, morph.progress otherwise
    fun effectiveMorphProgress() = gestureState.dragMorphProgress ?: morph.progress

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

    // Parse gesture spec YAML once — drives which gestures are active per zone/mode
    val gestureSpecs = remember {
        buildGestureSpecs(GestureSpecParser.parse(GestureSpecResource.YAML))
    }

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

    // ==================== Animation sync ====================
    val uiMode = (uiState as? PlayerUiState.Loaded)?.mode

    // Single LaunchedEffect handles both drag cancellation and mode sync.
    // When isDraggingMorph changes, the effect is cancelled/re-fired.
    // When uiMode changes, the effect re-fires with the new mode.
    // This prevents the animation from fighting the user's drag.
    LaunchedEffect(uiMode, gestureState.isDraggingMorph) {
        if (gestureState.isDraggingMorph) {
            morph.cancelAnimation()
            fullscreen.cancelAnimation()
            return@LaunchedEffect
        }

        val mode = uiMode ?: return@LaunchedEffect
        val morphTarget = if (mode == PlayerMode.FLOATING) 1f else 0f
        val currentProgress = effectiveMorphProgress()
        if (kotlin.math.abs(currentProgress - morphTarget) > 0.01f) {
            morph.animateTo(morphTarget)
        }
        isMinimizedAnim.value = (mode == PlayerMode.FLOATING)

        // Sync fullscreen (NORMAL↔FULLSCREEN)
        val fsTarget = if (mode == PlayerMode.FULLSCREEN) 1f else 0f
        if (mode == PlayerMode.FULLSCREEN && currentProgress < 0.5f) {
            kotlinx.coroutines.delay(50)
        }
        if (kotlin.math.abs(fullscreen.progress - fsTarget) > 0.01f) {
            if (mode == PlayerMode.FULLSCREEN) fullscreen.enterFullscreen()
            else fullscreen.exitFullscreen()
        }
        isFullscreenAnim.value = (mode == PlayerMode.FULLSCREEN)

        // Show controls on mode change
        if (mode != PlayerMode.FLOATING) autoHide.show()
        Log.d(TAG, "Mode synced: $mode")
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
            Log.d(TAG, "Mode: ${state.mode}")

            val config = PlayerMorphConfig.Default

            LaunchedEffect(state.currentVideo?.url) {
                isScrubbing = false
                scrubPositionMs = 0L
            }

            val miniWidth = config.miniPlayerWidthDp
            val miniHeight = miniWidth * config.miniPlayerAspectRatio
            val dragTravelPx = (containerSize.height * config.morphDragTravelFraction).coerceAtLeast(1f)

            val mode = state.mode

            val p = effectiveMorphProgress()
            val cornerRadius = (12f * p).coerceAtLeast(0f).dp
            val fullscreenP = fullscreen.progress

            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val isSmallWindow = with(context.resources.displayMetrics) {
                kotlin.math.min(widthPixels, heightPixels) < 600
            }

            // ==================== Orientation & system UI ====================
            LaunchedEffect(isLandscape, isSmallWindow, mode) {
                if (isLandscape && isSmallWindow && mode == PlayerMode.NORMAL) {
                    Log.d(TAG, "Auto-entering fullscreen: landscape + phone")
                    viewModel.toggleFullscreen()
                } else if (!isLandscape && mode == PlayerMode.FULLSCREEN) {
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

            LaunchedEffect(mode, isSmallWindow) {
                val activity = context as? Activity
                if (activity != null) {
                    val insetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    if (mode == PlayerMode.FULLSCREEN) {
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
            // Only schedule the auto-hide timer when settled and playing.
            // Do NOT force-hide here — other LaunchedEffects handle
            // show/hide for pause, morph transitions, and fullscreen.
            // Note: effectiveMorphProgress() is NOT a key — it changes every drag event
            // and would cause unnecessary re-runs that fight the drag.
            LaunchedEffect(state.isPlaying, isMinimizedAnim.value, isFullscreenAnim.value) {
                // During morph transition: hide controls (they'll be replaced by mini controls)
                if (effectiveMorphProgress() > config.miniSettledThreshold && effectiveMorphProgress() < (1f - config.miniSettledThreshold)) {
                    autoHide.hide()
                    return@LaunchedEffect
                }
                // When morph is mostly done (mini-player settled): hide normal controls
                if (effectiveMorphProgress() > config.controlsHideAtProgress) {
                    autoHide.hide()
                    return@LaunchedEffect
                }
                // Only schedule auto-hide timer when settled, playing, and controls are visible
                val settled = !isMinimizedAnim.value && !isFullscreenAnim.value
                if (settled && state.isPlaying) {
                    autoHide.notifyInteraction()
                }
            }

            LaunchedEffect(state.isPlaying) {
                if (!state.isPlaying) autoHide.show()
            }

            // ==================== Collapsing player height ====================
            val scrollState = rememberLazyListState()
            val maxPlayerHeightPx = containerSize.height * 0.7f
            val minPlayerHeightPx = containerSize.height * 0.2f
            var playerHeightPx by remember { mutableStateOf(0f) }

            LaunchedEffect(isMinimizedAnim.value, isFullscreenAnim.value) {
                if (!isMinimizedAnim.value) {
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

            // ==================== Geometry ====================
            val density = LocalDensity.current
            val miniWidthPx = with(density) { miniWidth.toPx() }
            val miniHeightPx = with(density) { miniHeight.toPx() }
            val paddingPx = with(density) { config.miniPlayerPaddingDp.toPx() }
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
                miniProgress = effectiveMorphProgress(),
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

            val playerHeightRatio = if (containerSize.height > 0f) playerHeightPx / containerSize.height else 1f
            val visibility = computeControlsVisibility(
                miniProgress = effectiveMorphProgress(),
                fullscreenProgress = fullscreenP,
                playerHeightRatio = playerHeightRatio,
                controlsVisible = autoHide.isVisible,
                config = config,
            )

            // ==================== Seek callback ====================
            val onSeek = { positionMs: Long ->
                scrubPositionMs = positionMs
                isScrubbing = true
                viewModel.seekTo(positionMs)
            }

            // ==================== Gesture bindings ====================
            // Lock gesture mode on drag start so bindings don't change mid-gesture.
            // Without this, effectiveMorphProgress() crosses thresholds → mode flips to FLOATING →
            // gestures become "none" → drag dies mid-swipe.
            val gestureMode = if (gestureState.isDraggingMorph) {
                gestureState.lockedGestureMode
            } else {
                computePlayerMode(
                    miniProgress = effectiveMorphProgress(),
                    fullscreenProgress = fullscreenP,
                    playerHeightRatio = playerHeightRatio,
                    config = config,
                )
            }

            // Cache GestureBindings to avoid recreating the binding map on every composition.
            // Action wrappers are recreated when mode/specs change, but they're cheap.
            val gestureBindings = remember(gestureMode, gestureSpecs) {
                buildGestureBindings(
                    mode = gestureMode,
                    specs = gestureSpecs,
                    actions = createGestureActions(
                        GestureCallbacks(
                            onMorphDragStart = {
                                gestureState.onDragStart(
                                    onModeComputed = { mode -> gestureState.lockedGestureMode = mode },
                                    onStartProgress = { effectiveMorphProgress() }
                                )
                            },
                            onMorphDrag = { totalDragY ->
                                gestureState.onDrag(totalDragY, dragTravelPx)
                            },
                            onMorphDragEnd = {
                                val progress = effectiveMorphProgress()
                                gestureState.onDragEnd(
                                    currentProgress = progress,
                                    onSnapTo = { morph.snapTo(it) },
                                    onMinimize = { viewModel.minimize() }
                                )
                            },
                            onBrightnessDrag = { delta ->
                                val newBrightness = (brightnessValue - delta / 500f).coerceIn(0f, 1f)
                                brightnessValue = newBrightness
                                viewModel.setBrightness(newBrightness)
                                showBrightnessIndicator = true
                                brightnessHideJob?.cancel()
                                brightnessHideJob = coroutineScope.launch { delay(1500); showBrightnessIndicator = false }
                            },
                            onVolumeDrag = { delta ->
                                val newVolume = (volumeValue - delta / 500f).coerceIn(0f, 1f)
                                volumeValue = newVolume
                                viewModel.setVolume(newVolume)
                                showVolumeIndicator = true
                                volumeHideJob?.cancel()
                                volumeHideJob = coroutineScope.launch { delay(1500); showVolumeIndicator = false }
                            },
                            onDoubleTapSeekLeft = { onSeek(-5000) },
                            onDoubleTapSeekRight = { onSeek(5000) },
                            onTap = {
                                if (effectiveMorphProgress() > 0.01f && effectiveMorphProgress() < 0.99f) return@GestureCallbacks
                                autoHide.notifyInteraction()
                            },
                            onLongPressStart = {
                                Log.d(TAG, "Speed hold start: 2x")
                                viewModel.setPlaybackSpeed(2f)
                            },
                            onLongPressEnd = {
                                Log.d(TAG, "Speed hold end: normal")
                                viewModel.setPlaybackSpeed(1f)
                            },
                        )
                    ),
                )
            }

            val gestureBindingsState = rememberUpdatedState(gestureBindings)

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
                    // Skip scrim during loading — it would intercept all touches and block gestures.
                    // The fade animation (playerFadeInProgress) already handles the visual transition.
                    val scrimAlpha = (1f - effectiveMorphProgress()) * (1f - fullscreenP) + fullscreenP
                    if (scrimAlpha > 0.01f && !state.isLoading) {
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
                        miniProgress = effectiveMorphProgress(),
                        fullscreenProgress = fullscreenP,
                        containerWidth = containerSize.width,
                        containerHeight = containerSize.height,
                        playerHeightPx = playerHeightPx,
                        miniWidthPx = miniWidthPx,
                        miniHeightPx = miniHeightPx,
                        floatingRestX = floatingRestX,
                        floatingRestY = floatingRestY,
                        visibility = visibility,
                        playerHeightRatio = playerHeightRatio,
                        controlsVisible = autoHide.isVisible,
                        scrollState = scrollState,
                        nestedScrollConnection = nestedScrollConnection,
                        gestureBindingsState = gestureBindingsState,
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
                        onSeek = onSeek,
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
