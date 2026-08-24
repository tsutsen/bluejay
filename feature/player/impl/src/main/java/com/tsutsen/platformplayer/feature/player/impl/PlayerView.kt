package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
import com.tsutsen.platformplayer.core.designsystem.component.QueueList
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.feature.player.impl.GestureBadgeState
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator
import com.tsutsen.platformplayer.feature.player.impl.ui.CastingSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlayerScreen"

@Composable
fun PlayerView(
    viewModel: PlayerViewModel = hiltViewModel(),
    onChannelClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val liveChat by viewModel.liveChat.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val savedTypes by viewModel.savedTypes.collectAsState(initial = emptySet())
    val loopMode by viewModel.loopMode.collectAsState(initial = 0)
    val queue by viewModel.queue.collectAsState(initial = emptyList())
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    // While the player is fullscreen, back exits fullscreen instead of
    // falling through to the app-level handler (home / exit).
    // This BackHandler is registered after the app-level one (PlayerView is
    // composed later), so it wins while enabled.
    BackHandler(enabled = (uiState as? PlayerUiState.Loaded)?.isFullscreen == true) {
        viewModel.exitFullscreen()
    }
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
    var activeProgressIndicator by remember { mutableStateOf<GestureIndicator.Progress?>(null) }
    var badgeState by remember { mutableStateOf(GestureBadgeState()) }
    var badgeKeepAliveCounter by remember { mutableStateOf(0) }
    var showMiniPlayerOptions by remember { mutableStateOf(false) }
    // Video options sheet (three-dot menu + long-press on queue cards): the
    // video it is bound to, or null when closed.
    var sheetVideo by remember { mutableStateOf<ContentItem?>(null) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showCastingSheet by remember { mutableStateOf(false) }
    // Stable callbacks — captured once per text by LinkifiedText's remember.
    val onTimestampClick: (Long) -> Unit = remember { { ms -> viewModel.seekToClamped(ms) } }
    val onLinkClick: (String) -> Unit =
        remember(context) {
            { url: String ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }

    var controlsVisible by remember { mutableStateOf(true) }
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }

    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    var miniPlayerOffsetX by remember { mutableStateOf(0f) }
    var miniPlayerOffsetY by remember { mutableStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }

    val morphProgress =
        remember {
            androidx.compose.animation.core
                .Animatable(0f)
        }
    var isDraggingMorph by remember { mutableStateOf(false) }
    var isDraggingFullscreen by remember { mutableStateOf(false) }

    val fullscreenProgress =
        remember {
            androidx.compose.animation.core
                .Animatable(0f)
        }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

    val animatedMiniOffsetX by animateFloatAsState(
        targetValue = miniPlayerOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "animatedMiniOffsetX",
    )
    val animatedMiniOffsetY by animateFloatAsState(
        targetValue = miniPlayerOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "animatedMiniOffsetY",
    )

    val transitionSpringSpec =
        tween<Float>(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        )
    val transitionDpSpec =
        spring<androidx.compose.ui.unit.Dp>(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        )

    val isMinimizedAnim = remember { mutableStateOf(false) }
    val isFullscreenAnim = remember { mutableStateOf(false) }
    val playerFadeInProgress =
        remember {
            androidx.compose.animation.core
                .Animatable(0f)
        }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    val player =
        remember(uiState) {
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
    }

    LaunchedEffect(isFullscreenState, isDraggingFullscreen) {
        // Don't fight the finger mid-drag: the drag callbacks own
        // fullscreenProgress until END; the commit (or cancel) that follows
        // re-enters this effect and finishes the move.
        if (isDraggingFullscreen) return@LaunchedEffect
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        val target = if (fullscreen) 1f else 0f
        if (fullscreen && morphProgress.value < 0.5f) {
            kotlinx.coroutines.delay(50)
        }
        if (kotlin.math.abs(fullscreenProgress.value - target) > 0.01f) {
            fullscreenProgress.animateTo(target, transitionSpringSpec)
        }
        isFullscreenAnim.value = fullscreen
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
            val isSmallWindow =
                with(context.resources.displayMetrics) {
                    kotlin.math.min(widthPixels, heightPixels) < 600
                }

            // ==================== Orientation & system UI ====================
            // Small-window devices auto-enter fullscreen on landscape. Portrait
            // fullscreen is fully supported (it fills the portrait window with
            // the video letterboxed), so there is NO forced exit when not
            // landscape — the user controls it via the button or back.
            LaunchedEffect(isLandscape, isSmallWindow, isFullscreen) {
                if (isLandscape && isSmallWindow && !isFullscreen && !isMinimized) {
                    viewModel.toggleFullscreen()
                }
            }

            LaunchedEffect(Unit) {
                val activity = context as? Activity
                if (activity != null) {
                    androidx.core.view.WindowCompat
                        .setDecorFitsSystemWindows(activity.window, false)
                }
            }

            LaunchedEffect(isFullscreen, isSmallWindow) {
                val activity = context as? Activity
                if (activity != null) {
                    val insetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    if (isFullscreen) {
                        kotlinx.coroutines.delay(300)
                        insetsController.hide(
                            androidx.core.view.WindowInsetsCompat.Type
                                .systemBars(),
                        )
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    } else {
                        insetsController.show(
                            androidx.core.view.WindowInsetsCompat.Type
                                .systemBars(),
                        )
                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
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
                    hideControlsJob =
                        launch {
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
            // The NORMAL-mode video height.
            //  - Landscape: 70% of the window height, leaving room for the
            //    details page below the video.
            //  - Portrait: a width-constrained 16:9 box. 0.7 of the tall
            //    portrait window height would make the video far too large.
            val maxPlayerHeightPx =
                if (isLandscape) {
                    containerSize.height * 0.7f
                } else {
                    containerSize.width * 9f / 16f
                }
            // Scroll-collapsible lower bound (drag the video shorter).
            val minPlayerHeightPx =
                if (isLandscape) {
                    containerSize.height * 0.2f
                } else {
                    maxPlayerHeightPx * 0.4f
                }
            var playerHeightPx by remember { mutableStateOf(0f) }

            // Reset the collapsed height the moment the state leaves the
            // floating mini (isMinimized flips false immediately), not only
            // after the 300ms morph settles (isMinimizedAnim) — otherwise the
            // player lands in COMPACT for a beat before expanding to NORMAL.
            LaunchedEffect(isMinimized, isFullscreenAnim.value) {
                if (!isMinimized && !isFullscreenAnim.value) {
                    playerHeightPx = maxPlayerHeightPx
                }
            }

            LaunchedEffect(maxPlayerHeightPx, minPlayerHeightPx) {
                playerHeightPx =
                    if (playerHeightPx == 0f) {
                        maxPlayerHeightPx
                    } else {
                        playerHeightPx.coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                    }
            }

            val nestedScrollConnection =
                remember(minPlayerHeightPx, maxPlayerHeightPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            val delta = available.y
                            val previousHeight = playerHeightPx
                            val consumed =
                                when {
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

                                    else -> {
                                        0f
                                    }
                                }
                            if (consumed != 0f) {
                                playerHeightPx += consumed
                            }
                            return Offset(0f, consumed)
                        }
                    }
                }

            // "Collapsed" = the video has been dragged down to (or below) half
            // of its own maximum height. Measuring against maxPlayerHeightPx
            // (not the window height) is orientation-independent: in portrait
            // the 16:9 video is only ~25% of the tall window height, so a fixed
            // 0.45 window-fraction made it read as permanently collapsed and
            // never entered NORMAL mode.
            val isCollapsedControls =
                !isFullscreenAnim.value &&
                    maxPlayerHeightPx > 0f &&
                    (playerHeightPx / maxPlayerHeightPx) <= 0.5f

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

            val videoLayout =
                computeVideoLayout(
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
                    fullscreenHeightPx = if (windowHeightPx > 0f) windowHeightPx else containerSize.height,
                )

            // ==================== Overlay mode ====================
            val overlayMode =
                when {
                    isMinimized && !isFullscreen -> PlayerOverlayMode.FLOATING
                    isFullscreen -> PlayerOverlayMode.FULLSCREEN
                    isCollapsedControls -> PlayerOverlayMode.COMPACT
                    else -> PlayerOverlayMode.NORMAL
                }

            // ==================== Gesture configs (defaults, user overrides later) ====================
            val gestureConfigs =
                remember {
                    com.tsutsen.platformplayer.feature.player.impl.gesture
                        .buildDefaultGestureConfigs()
                }

            // ==================== Gesture action handler ====================
            val gestureHandler =
                remember {
                    com.tsutsen.platformplayer.feature.player.impl.gesture.PlayerGestureActionHandler(
                        viewModel = viewModel,
                        screenHeight = { containerSize.height },
                        context = context,
                        activity = context as? android.app.Activity,
                        onIndicator = { indicator ->
                            when (indicator) {
                                is GestureIndicator.Progress -> {
                                    activeProgressIndicator = indicator
                                }

                                is GestureIndicator.TextBadge -> {
                                    badgeKeepAliveCounter++
                                    badgeState =
                                        GestureBadgeState(
                                            key = indicator.key,
                                            label = indicator.label,
                                            icon = indicator.icon,
                                            visible = true,
                                            keepAlive = badgeKeepAliveCounter,
                                        )
                                }

                                is GestureIndicator.Badge -> {
                                    badgeKeepAliveCounter++
                                    badgeState =
                                        GestureBadgeState(
                                            key = indicator.key,
                                            label = indicator.format(indicator.value),
                                            icon = indicator.icon,
                                            visible = true,
                                            keepAlive = badgeKeepAliveCounter,
                                        )
                                }

                                else -> {
                                    Unit
                                }
                            }
                        },
                        onIndicatorEnd = {
                            activeProgressIndicator = null
                            // Badges auto-hide via their own fade animation — don't touch badgeState here
                        },
                        onFullscreenDragStart = { isDraggingFullscreen = true },
                        onFullscreenDrag = { dragY ->
                            // Read containerSize at call time — this block is
                            // remembered once, so no locals may be captured.
                            val travel = containerSize.height * 0.45f
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            coroutineScope.launch { fullscreenProgress.snapTo(progress) }
                        },
                        onFullscreenDragEnd = { dragY ->
                            isDraggingFullscreen = false
                            val travel = containerSize.height * 0.45f
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            if (progress > 0.4f) {
                                viewModel.toggleFullscreen()
                            } else {
                                coroutineScope.launch {
                                    fullscreenProgress.animateTo(0f, transitionSpringSpec)
                                }
                            }
                        },
                    )
                }

            // ==================== Tap handler (toggle controls) ====================
            val onTap: () -> Unit = {
                if (morphProgress.value !in 0.01f..0.99f) {
                    controlsVisible = !controlsVisible
                    hideControlsJob?.cancel()
                    if (controlsVisible && state.isPlaying) {
                        hideControlsJob =
                            coroutineScope.launch {
                                delay(3000)
                                controlsVisible = false
                            }
                    }
                }
            }

            // ==================== Compose ====================
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            containerSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = playerFadeInProgress.value },
                ) {
                    val scrimAlpha = (1f - morphProgress.value) * (1f - fullscreenP) + fullscreenP
                    if (scrimAlpha > 0.01f) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = scrimAlpha)),
                        )
                    }

                    PlayerContent(
                        player = player,
                        state = state,
                        positionMs = viewModel.positionMs,
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
                        onOffsetChanged = { x, y ->
                            miniPlayerOffsetX = x
                            miniPlayerOffsetY = y
                        },
                        currentOffsetX = miniPlayerOffsetX,
                        currentOffsetY = miniPlayerOffsetY,
                        onOptions = { showOptionsModal = true },
                        onChapters = { showChapters = !showChapters },
                        expandedDescription = expandedDescription,
                        onToggleDescription = { expandedDescription = !expandedDescription },
                        selectedTab = selectedTab,
                        onChannelClick = onChannelClick,
                        onLike = { viewModel.toggleLike(state.isLiked) },
                        onDislike = { viewModel.toggleDislike(state.isDisliked) },
                        onMore = { sheetVideo = state.currentVideo },
                        isSubscribedChannel = state.isSubscribedChannel,
                        onSubscribe = { viewModel.subscribeChannel() },
                        isLive = state.isLive,
                        liveChat = liveChat,
                        onTimestampClick = onTimestampClick,
                        onLinkClick = onLinkClick,
                        onTabSelected = { selectedTab = it },
                        onRecommendedClick = { video -> viewModel.play(video) },
                        gridColumns = gridColumns,
                        onLoadMoreComments = { viewModel.loadMoreComments(state.currentVideo?.url ?: "") },
                        isLoading = state.isLoading,
                        activeProgressIndicator = activeProgressIndicator,
                        badgeState = badgeState,
                        onBadgeSessionEnded = {
                            badgeState = GestureBadgeState()
                        },
                        scrubPositionMs = scrubPositionMs,
                        subtitlesOn =
                            state.selectedSubtitle != "Off" && state.selectedSubtitle != "Auto",
                        onSubtitleToggle = {
                            viewModel.toggleSubtitles()
                        },
                        onMinimize = {
                            viewModel.minimize()
                        },
                        onFullscreen = {
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
                            viewModel.close()
                        },
                        loopMode = loopMode,
                        onLoopMode = { viewModel.cycleLoopMode() },
                        onWatchLater = {
                            viewModel.toggleWatchLater(savedTypes.contains(SavedVideoType.WATCH_LATER))
                        },
                        isWatchLater = savedTypes.contains(SavedVideoType.WATCH_LATER),
                        onQueue = { showQueueSheet = true },
                        // Casting deferred: the fcast protocol only reaches fcast
                        // receiver apps (not Chromecast TVs), which is niche. Users
                        // can use system screen cast instead. Re-enable by
                        // restoring this line + the sheet block below.
                        // onCast = { showCastingSheet = true },
                        onPrevious = { viewModel.skipPrevious() },
                        onNext = { viewModel.skipNext() },
                        onSeek = { positionMs ->
                            scrubPositionMs = positionMs
                            isScrubbing = true
                            viewModel.seekTo(positionMs)
                        },
                        onScrubFinished = {
                            isScrubbing = false
                        },
                        onMoreOptions = { showMiniPlayerOptions = true },
                        onFullscreenToggle = { viewModel.toggleFullscreen() },
                        onDetailsOverdrag = { viewModel.toggleFullscreen() },
                    )
                }

                // Casting indicator — while mirrored to a receiver, a chip
                // above the controls names the active device.
                if (state.isCasting) {
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 64.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp),
                                ).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Casting to ${state.castDeviceName ?: "receiver"}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // ==================== Modals ====================
                if (showOptionsModal) {
                    val downloadInfo = downloads.find { it.url == state.currentVideo?.url }
                    val downloadState =
                        when {
                            downloadInfo == null -> DownloadButtonState.Idle
                            downloadInfo.done -> DownloadButtonState.Downloaded
                            else -> DownloadButtonState.Downloading(downloadInfo.progress)
                        }
                    OptionsModal(
                        playbackSpeed = state.playbackSpeed,
                        quality = state.selectedQuality,
                        qualities = state.videoQualities,
                        subtitle = state.selectedSubtitle,
                        subtitles = state.subtitleLanguages,
                        loopMode = loopMode,
                        isWatchLater = savedTypes.contains(SavedVideoType.WATCH_LATER),
                        downloadState = downloadState,
                        onSpeedChange = { speed ->
                            viewModel.setPlaybackSpeed(speed)
                        },
                        onQualityChange = { quality ->
                            viewModel.setVideoQuality(quality)
                        },
                        onSubtitleChange = { subtitle ->
                            viewModel.setSubtitle(subtitle)
                        },
                        onLoopClick = { viewModel.cycleLoopMode() },
                        onWatchLaterClick = {
                            viewModel.toggleWatchLater(savedTypes.contains(SavedVideoType.WATCH_LATER))
                        },
                        onDownload = {
                            when (downloadState) {
                                is DownloadButtonState.Idle -> viewModel.startDownload()
                                is DownloadButtonState.Downloading -> viewModel.cancelDownload()
                                is DownloadButtonState.Downloaded -> viewModel.deleteDownload()
                                is DownloadButtonState.Starting -> Unit
                            }
                        },
                        onDismiss = { showOptionsModal = false },
                    )
                }

                if (showChapters) {
                    ChaptersPanel(
                        chapters = state.chapters,
                        positionMs = viewModel.positionMs,
                        onChapterClick = { positionMs ->
                            viewModel.seekTo(positionMs)
                            showChapters = false
                        },
                        onDismiss = { showChapters = false },
                    )
                }

                // Video options sheet (three-dot menu + long-press on queue
                // cards), bound to the long-pressed / current video.
                sheetVideo?.let { video ->
                    CurrentVideoOptionsSheet(
                        video = video,
                        viewModel = viewModel,
                        isCurrentlyPlaying = video.url == state.currentVideo?.url,
                        onDismiss = { sheetVideo = null },
                        onGoToChannel = onChannelClick,
                    )
                }

                // Cast button (top row) → the casting sheet.
                // Deferred — see the onCast note above. Kept (commented) so the
                // cast stack (StateCasting, repository, resolver path) stays
                // wired and re-enabling is a two-line change.
                // if (showCastingSheet) {
                //     BluejayModalBottomSheet(
                //         onDismiss = { showCastingSheet = false },
                //         title = "Cast to…",
                //     ) {
                //         CastingSheet(
                //             castState = viewModel.castState,
                //             onConnect = { viewModel.castConnect(it) },
                //             onConnectByUrl = { viewModel.castConnectByUrl(it) },
                //             onDisconnect = {
                //                 viewModel.castDisconnect()
                //                 showCastingSheet = false
                //             },
                //             onDismiss = { showCastingSheet = false },
                //         )
                //     }
                // }

                // Queue button (top row) → the queue sheet.
                if (showQueueSheet) {
                    BluejayModalBottomSheet(
                        onDismiss = { showQueueSheet = false },
                        title = "Queue",
                        scroll = false,
                    ) {
                        QueueList(
                            items = queue,
                            currentIndex = state.selectedIndex,
                            onPlay = {
                                viewModel.playQueueItem(it)
                                showQueueSheet = false
                            },
                            onRemove = { url -> viewModel.removeQueueItemUrl(url) },
                            onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                            onLongClick = { video -> sheetVideo = video },
                            modifier = Modifier.fillMaxHeight(0.7f),
                        )
                    }
                }
            }
        }

        is PlayerUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * Long-press-style options sheet for the currently playing video. Same
 * [VideoOptionsSheet] the library uses for card long-presses, with live
 * saved/download state from [PlayerViewModel].
 */
@Composable
private fun CurrentVideoOptionsSheet(
    video: ContentItem,
    viewModel: PlayerViewModel,
    isCurrentlyPlaying: Boolean,
    onDismiss: () -> Unit,
    onGoToChannel: (String) -> Unit,
) {
    val savedTypes by viewModel.savedTypes.collectAsState(initial = emptySet())
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val contained by viewModel.containedPlaylists.collectAsState(initial = emptySet())
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val queue by viewModel.queue.collectAsState(initial = emptyList())
    val downloadInfo = downloads.find { it.url == video.url }
    val downloadState =
        when {
            downloadInfo == null -> DownloadButtonState.Idle
            downloadInfo.done -> DownloadButtonState.Downloaded
            else -> DownloadButtonState.Downloading(downloadInfo.progress)
        }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    VideoOptionsSheet(
        url = video.url,
        onDismiss = onDismiss,
        onPlay = onDismiss,
        onGoToChannel = onGoToChannel,
        onToggleWatchLater = {
            viewModel.toggleWatchLater(savedTypes.contains(SavedVideoType.WATCH_LATER))
        },
        onToggleLiked = {
            viewModel.toggleLike(savedTypes.contains(SavedVideoType.LIKED))
        },
        onToggleFavourite = {
            viewModel.toggleFavourite(savedTypes.contains(SavedVideoType.FAVOURITE))
        },
        onDownload = {
            when (downloadState) {
                is DownloadButtonState.Idle -> viewModel.startDownload()
                is DownloadButtonState.Downloading -> viewModel.cancelDownload()
                is DownloadButtonState.Downloaded -> viewModel.deleteDownload()
                is DownloadButtonState.Starting -> Unit
            }
        },
        onDownloadWithQuality = { quality -> viewModel.startDownload(quality) },
        onAddToPlaylist = { playlistId ->
            if (playlistId == null) {
                showNewPlaylistDialog = true
            } else {
                viewModel.addToPlaylist(video, playlistId)
            }
        },
        onAddToQueue = { viewModel.addToQueue(video) },
        isInQueue = queue.any { it.url == video.url },
        isCurrentlyPlaying = isCurrentlyPlaying,
        onRemoveFromQueue = { viewModel.removeQueueItemUrl(video.url) },
        downloadState = downloadState,
        isWatchLaterSaved = savedTypes.contains(SavedVideoType.WATCH_LATER),
        isLikedSaved = savedTypes.contains(SavedVideoType.LIKED),
        isFavouriteSaved = savedTypes.contains(SavedVideoType.FAVOURITE),
        playlists = playlists,
        authorUrl = video.author?.url,
        title = video.title,
        durationMs = video.durationMs,
        viewCount = video.viewCount,
        publishedAt = video.publishedAt,
        containedPlaylistIds = contained,
        onTogglePlaylist = { id, checked ->
            viewModel.togglePlaylistMembership(id, checked)
        },
    )

    if (showNewPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylistAndAdd(name)
                        showNewPlaylistDialog = false
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Cancel") }
            },
        )
    }
}
