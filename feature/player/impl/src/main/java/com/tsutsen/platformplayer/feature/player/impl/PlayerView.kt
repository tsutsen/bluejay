package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt
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
import kotlinx.coroutines.flow.first
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

    // ==================== Player surface (deep geometry module) ====================
    // All animated/measured surface state (window/container size, morph and
    // fullscreen progress, fade, mini-player drag offsets, collapse height)
    // lives in [surface]. The UI layers read it ONLY through frame-safe
    // accessors (frame-level modifier lambdas, derivedStateOf gates, or
    // call-time reads inside effects/handlers) — never bare in a body.
    val surface = remember { PlayerSurface(coroutineScope) }

    // ==================== True window size (nav-bar independent) ====================
    val view = LocalView.current
    DisposableEffect(view) {
        fun syncWindowSize() {
            surface.windowSize.value = Size(view.width.toFloat(), view.height.toFloat())
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

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

    val transitionSpringSpec =
        tween<Float>(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        )

    val player =
        remember(uiState) {
            (viewModel as? PlayerViewModel)?.getPlayer()?.exoPlayer
        }

    // ==================== Animation sync ====================
    val isMinimizedState = (uiState as? PlayerUiState.Loaded)?.isMinimized
    val isFullscreenState = (uiState as? PlayerUiState.Loaded)?.isFullscreen

    LaunchedEffect(isMinimizedState, surface.isDraggingMorph.value) {
        if (surface.isDraggingMorph.value) return@LaunchedEffect
        val minimized = isMinimizedState ?: return@LaunchedEffect
        val target = if (minimized) 1f else 0f
        if (kotlin.math.abs(surface.morphProgress.value - target) > 0.01f) {
            surface.morphProgress.animateTo(target, transitionSpringSpec)
        }
        surface.isMinimizedAnim.value = minimized
        if (!minimized) controlsVisible = true
    }

    LaunchedEffect(isFullscreenState, surface.isDraggingFullscreen.value) {
        // Don't fight the finger mid-drag: the drag callbacks own
        // fullscreenProgress until END; the commit (or cancel) that follows
        // re-enters this effect and finishes the move.
        if (surface.isDraggingFullscreen.value) return@LaunchedEffect
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        val target = if (fullscreen) 1f else 0f
        if (fullscreen && surface.morphProgress.value < 0.5f) {
            kotlinx.coroutines.delay(50)
        }
        if (kotlin.math.abs(surface.fullscreenProgress.value - target) > 0.01f) {
            surface.fullscreenProgress.animateTo(target, transitionSpringSpec)
        }
        surface.isFullscreenAnim.value = fullscreen
    }

    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            surface.playerFadeInProgress.animateTo(1f, tween(durationMillis = 600))
        } else {
            surface.playerFadeInProgress.animateTo(0f)
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

            val isMinimized = state.isMinimized
            val isFullscreen = state.isFullscreen
            val density = LocalDensity.current

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
            // Keyed on RARELY-flipping values only (never per-frame morph
            // progress): the effect waits internally for the morph to settle
            // via a snapshotFlow read, so it cannot restart on every frame.
            val isMinimizedAnim by remember(surface) {
                derivedStateOf { surface.isMinimizedAnim.value }
            }
            val isFullscreenAnim by remember(surface) {
                derivedStateOf { surface.isFullscreenAnim.value }
            }
            LaunchedEffect(controlsVisible, state.isPlaying, isMinimizedAnim, isFullscreenAnim) {
                if (isMinimizedAnim || isFullscreenAnim) return@LaunchedEffect
                // Wait (without restarting) until a morph in progress settles.
                snapshotFlow { surface.morphProgress.value }
                    .first { p -> p <= 0.01f || p >= 0.99f }
                if (!controlsVisible) return@LaunchedEffect
                hideControlsJob?.cancel()
                hideControlsJob =
                    launch {
                        delay(3000)
                        controlsVisible = false
                    }
            }

            LaunchedEffect(state.isPlaying) {
                if (!state.isPlaying) controlsVisible = true
            }

            LaunchedEffect(isMinimizedAnim) {
                if (isMinimizedAnim) controlsVisible = false
            }

            // ==================== Collapsing player height ====================
            val scrollState = rememberLazyListState()
            // NORMAL-mode video height and its scroll-collapsible lower bound
            // are owned by the surface (orientation-dependent).
            val maxPlayerHeightPx = surface.maxPlayerHeightPx(isLandscape)
            val minPlayerHeightPx = surface.minPlayerHeightPx(isLandscape, maxPlayerHeightPx)

            // Reset the collapsed height the moment the state leaves the
            // floating mini (isMinimized flips false immediately), not only
            // after the 300ms morph settles (isMinimizedAnim) — otherwise the
            // player lands in COMPACT for a beat before expanding to NORMAL.
            LaunchedEffect(isMinimized, surface.isFullscreenAnim.value) {
                if (!isMinimized && !surface.isFullscreenAnim.value) {
                    surface.playerHeightPx.value = surface.maxPlayerHeightPx(isLandscape)
                }
            }

            LaunchedEffect(maxPlayerHeightPx, minPlayerHeightPx) {
                surface.playerHeightPx.value =
                    if (surface.playerHeightPx.value == 0f) {
                        maxPlayerHeightPx
                    } else {
                        surface.playerHeightPx.value.coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                    }
            }

            val nestedScrollConnection =
                remember(minPlayerHeightPx, maxPlayerHeightPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            // Self-gate: playerHeightPx is NORMAL-mode state, so
                            // only consume detail-scroll while fully settled in
                            // normal mode. This lets the caller keep the
                            // connection attached at all times (no per-frame
                            // attach/detach of the modifier chain).
                            if (
                                surface.morphProgress.value >= MINI_SETTLED_THRESHOLD ||
                                surface.fullscreenProgress.value >= FULLSCREEN_SETTLED_THRESHOLD
                            ) {
                                return Offset.Zero
                            }
                            val delta = available.y
                            val previousHeight = surface.playerHeightPx.value
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
                                surface.playerHeightPx.value += consumed
                            }
                            return Offset(0f, consumed)
                        }
                    }
                }

            // ==================== Clamp mini-player drag offsets ====================
            LaunchedEffect(surface.containerSize.value, surface.isDraggingMiniPlayer.value) {
                val container = surface.containerSize.value
                if (container == Size.Zero || surface.isDraggingMiniPlayer.value) return@LaunchedEffect
                val rest = surface.floatingRestPx(density)
                val mini = surface.miniSizePx(density)
                val minOffsetX = -rest.x
                val maxOffsetX = (container.width - mini.width) - rest.x
                val minOffsetY = -rest.y
                val maxOffsetY = (container.height - mini.height) - rest.y
                if (minOffsetX > maxOffsetX || minOffsetY > maxOffsetY) return@LaunchedEffect
                val clampedX = surface.miniPlayerOffsetX.value.coerceIn(minOffsetX, maxOffsetX)
                val clampedY = surface.miniPlayerOffsetY.value.coerceIn(minOffsetY, maxOffsetY)
                if (clampedX != surface.miniPlayerOffsetX.value) surface.miniPlayerOffsetX.value = clampedX
                if (clampedY != surface.miniPlayerOffsetY.value) surface.miniPlayerOffsetY.value = clampedY
            }

            // ==================== Gesture configs (defaults + user overrides) ====================
            // Rebuilt when the user edits Settings > Gestures.
            val gesturePrefs by viewModel.gesturePrefs.collectAsState()
            val gestureConfigs =
                remember(gesturePrefs) {
                    com.tsutsen.platformplayer.feature.player.impl.gesture
                        .buildGestureConfigs(gesturePrefs)
                }

            // ==================== Gesture action handler ====================
            val gestureHandler =
                remember {
                    com.tsutsen.platformplayer.feature.player.impl.gesture.PlayerGestureActionHandler(
                        viewModel = viewModel,
                        screenHeight = { surface.containerSize.value.height },
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
                        onFullscreenDragStart = { surface.isDraggingFullscreen.value = true },
                        onFullscreenDrag = { dragY ->
                            // Read the surface at call time — this block is
                            // remembered once, so no locals may be captured.
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            coroutineScope.launch { surface.fullscreenProgress.snapTo(progress) }
                        },
                        onFullscreenDragEnd = { dragY ->
                            surface.isDraggingFullscreen.value = false
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            if (progress > 0.4f) {
                                viewModel.toggleFullscreen()
                            } else {
                                coroutineScope.launch {
                                    surface.fullscreenProgress.animateTo(0f, transitionSpringSpec)
                                }
                            }
                        },
                    )
                }

            // ==================== Tap handler (toggle controls) ====================
            // Stable identity (recreated only when uiState changes): lets the
            // big child subtrees below skip recomposition.
            val onTap: () -> Unit =
                remember(state) {
                    {
                        if (surface.morphProgress.value !in 0.01f..0.99f) {
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
                }

            // ==================== Compose ====================
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            surface.containerSize.value = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = surface.playerFadeInProgress.value },
                ) {
                    // Black scrim that follows the video box: full page in
                    // normal/fullscreen, shrinks with the video through the
                    // morph, and lands exactly on the floating mini player —
                    // its solid plate (rounded corners and all) while the
                    // underlying layers stay visible. Frame-safe: every
                    // per-frame value is read inside the modifier lambdas.
                    val pageScrimModifier =
                        remember(surface, isLandscape, density) {
                            Modifier
                                .offset {
                                    val layout = surface.videoLayout(isLandscape, density)
                                    IntOffset(
                                        x = layout.offsetX.toInt(),
                                        y = layout.offsetY.toInt(),
                                    )
                                }.layout { measurable, constraints ->
                                    val layout = surface.videoLayout(isLandscape, density)
                                    // Bottom edge follows the morph: full
                                    // container height at rest, video height
                                    // while morphing — converges on the mini
                                    // rect exactly at p=1 (and follows the
                                    // finger during a mini drag, since the
                                    // video rect does too).
                                    val p = surface.morphProgress.value
                                    val heightPx =
                                        (surface.containerSize.value.height * (1f - p) +
                                            layout.heightPx * p).roundToInt()
                                    val widthPx = layout.widthPx.roundToInt()
                                    val placeable =
                                        measurable.measure(
                                            Constraints(widthPx, widthPx, heightPx, heightPx),
                                        )
                                    layout(placeable.width, placeable.height) {
                                        placeable.place(0, 0)
                                    }
                                }.graphicsLayer {
                                    shape =
                                        RoundedCornerShape(
                                            surface.videoLayout(isLandscape, density).cornerRadius,
                                        )
                                    clip = true
                                }.background(Color.Black)
                        }
                    Box(modifier = pageScrimModifier)

                    PlayerContent(
                        player = player,
                        state = state,
                        positionMs = viewModel.positionMs,
                        surface = surface,
                        isLandscape = isLandscape,
                        controlsVisible = controlsVisible,
                        scrollState = scrollState,
                        nestedScrollConnection = nestedScrollConnection,
                        gestureConfigs = gestureConfigs,
                        gestureHandler = gestureHandler,
                        isScrubbing = isScrubbing,
                        onTap = onTap,
                        onOptions = remember { { showOptionsModal = true } },
                        onChapters = remember { { showChapters = !showChapters } },
                        expandedDescription = expandedDescription,
                        onToggleDescription = remember { { expandedDescription = !expandedDescription } },
                        selectedTab = selectedTab,
                        onChannelClick = onChannelClick,
                        onLike = remember(state) { { viewModel.toggleLike(state.isLiked) } },
                        onDislike = remember(state) { { viewModel.toggleDislike(state.isDisliked) } },
                        onMore = remember(state) { { sheetVideo = state.currentVideo } },
                        isSubscribedChannel = state.isSubscribedChannel,
                        onSubscribe = remember { { viewModel.subscribeChannel() } },
                        isLive = state.isLive,
                        liveChat = liveChat,
                        onTimestampClick = onTimestampClick,
                        onLinkClick = onLinkClick,
                        onTabSelected = remember { { selectedTab = it } },
                        onRecommendedClick = remember(viewModel) { { video: com.tsutsen.platformplayer.core.model.VideoCard -> viewModel.play(video) } },
                        gridColumns = gridColumns,
                        onLoadMoreComments =
                            remember(state) { { viewModel.loadMoreComments(state.currentVideo?.url ?: "") } },
                        isLoading = state.isLoading,
                        activeProgressIndicator = activeProgressIndicator,
                        badgeState = badgeState,
                        onBadgeSessionEnded = remember { { badgeState = GestureBadgeState() } },
                        scrubPositionMs = scrubPositionMs,
                        subtitlesOn =
                            state.selectedSubtitle != "Off" && state.selectedSubtitle != "Auto",
                        onSubtitleToggle = remember { { viewModel.toggleSubtitles() } },
                        onMinimize = remember { { viewModel.minimize() } },
                        onExpand = remember { { viewModel.exitMiniPlayer() } },
                        onMorphDragStart = remember { { surface.isDraggingMorph.value = true } },
                        onMorphDrag =
                            remember {
                                { dragY: Float ->
                                    val progress = (dragY / surface.dragTravelPx()).coerceIn(0f, 1f)
                                    coroutineScope.launch { surface.morphProgress.snapTo(progress) }
                                }
                            },
                        onMorphDragEnd =
                            remember {
                                { dragY: Float ->
                                    surface.isDraggingMorph.value = false
                                    val progress = (dragY / surface.dragTravelPx()).coerceIn(0f, 1f)
                                    if (progress > 0.4f) {
                                        viewModel.minimize()
                                    } else {
                                        coroutineScope.launch {
                                            surface.morphProgress.animateTo(0f, transitionSpringSpec)
                                        }
                                    }
                                }
                            },
                        onMiniOffsetChanged =
                            remember
                            {
                                { x: Float, y: Float ->
                                    surface.miniPlayerOffsetX.value = x
                                    surface.miniPlayerOffsetY.value = y
                                }
                            },
                        onPlayPause = remember(state) { { if (state.isPlaying) viewModel.pause() else viewModel.resume() } },
                        onClose = remember { { viewModel.close() } },
                        loopMode = loopMode,
                        onLoopMode = remember { { viewModel.cycleLoopMode() } },
                        onWatchLater =
                            remember {
                                {
                                    viewModel.toggleWatchLater(savedTypes.contains(SavedVideoType.WATCH_LATER))
                                }
                            },
                        isWatchLater = savedTypes.contains(SavedVideoType.WATCH_LATER),
                        onQueue = remember { { showQueueSheet = true } },
                        // Casting deferred: the fcast protocol only reaches fcast
                        // receiver apps (not Chromecast TVs), which is niche. Users
                        // can use system screen cast instead. Re-enable by
                        // restoring this line + the sheet block below.
                        // onCast = { showCastingSheet = true },
                        onPrevious = remember { { viewModel.skipPrevious() } },
                        onNext = remember { { viewModel.skipNext() } },
                        onSeek =
                            remember
                            {
                                { positionMs: Long ->
                                    scrubPositionMs = positionMs
                                    isScrubbing = true
                                    viewModel.seekTo(positionMs)
                                }
                            },
                        onScrubFinished = remember { { isScrubbing = false } },
                        onFullscreenToggle = remember { { viewModel.toggleFullscreen() } },
                        onDetailsOverdrag = remember { { viewModel.toggleFullscreen() } },
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
                        audioTracks = state.audioTracks,
                        selectedAudioTrack = state.selectedAudioTrack,
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
                        onAudioChange = { track ->
                            viewModel.setAudioTrack(track)
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
