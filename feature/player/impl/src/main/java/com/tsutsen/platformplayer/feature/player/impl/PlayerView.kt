package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.ui.R
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
import com.tsutsen.platformplayer.core.designsystem.component.QueueList
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.TrackSelectionValues
import com.tsutsen.platformplayer.feature.player.impl.GestureBadgeState
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureAnimationConstants
import com.tsutsen.platformplayer.feature.player.impl.gesture.MorphCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val TAG = "PlayerScreen"

@Composable
fun PlayerView(
    viewModel: PlayerViewModel = hiltViewModel(),
    onChannelClick: (String) -> Unit = {},
    onPluginSettingsClick: (String) -> Unit = {},
    isPip: Boolean = false,
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
    // Controller (gamepad / TV remote) key handling: route key-downs to the
    // view model while this screen is composed (Settings > Controller).
    val controllerKeyHandler =
        remember {
            object : (GamepadKeyBus.GamepadEvent) -> Boolean {
                override fun invoke(event: GamepadKeyBus.GamepadEvent) =
                    viewModel.handleControllerKey(event)
            }
        }
    DisposableEffect(Unit) {
        GamepadKeyBus.setPlayerHandler(controllerKeyHandler)
        onDispose { GamepadKeyBus.setPlayerHandler(null) }
    }
    val coroutineScope = rememberCoroutineScope()

    // Details overdrag: part of a downward drag may re-expand a collapsed
    // video (nested scroll, 1:1) before the true overdrag zone begins.
    // Holds that pending-expand distance, measured at drag start; morph px
    // = cumulative drag px - this.
    val detailsOverdragPendingExpandPx = remember { mutableStateOf(0f) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // ==================== Player surface (deep geometry module) ====================
    // All animated/measured surface state (window/container size, morph and
    // fullscreen progress, fade, mini-player drag offsets, collapse height)
    // lives in [surface]. The UI layers read it ONLY through frame-safe
    // accessors (frame-level modifier lambdas, derivedStateOf gates, or
    // call-time reads inside effects/handlers) — never bare in a body.
    val surface = remember { PlayerSurface(coroutineScope) }

    // Pure decision core of the morph/shrink/fullscreen drag choreography:
    // the drag callbacks below feed it drag values and apply its settle
    // decisions to the Animatables and view-model flips.
    val morph = remember { MorphCoordinator() }

    // ==================== True window size (nav-bar independent) ====================
    val view = LocalView.current
    DisposableEffect(view) {
        fun syncWindowSize() {
            surface.windowSize.value = Size(view.width.toFloat(), view.height.toFloat())
            // Decor view is always the full physical window; the delta is
            // the vertical system-bar inset applied to the content view.
            surface.windowOverhangPx.value =
                (view.rootView.height - view.height).coerceAtLeast(0).toFloat()
        }
        syncWindowSize()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncWindowSize() }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }

    // ==================== State ====================
    var showOptionsModal by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val badgeStateHolder = remember { mutableStateOf(GestureBadgeState()) }
    var badgeState by badgeStateHolder
    val badgeKeepAliveCounterHolder = remember { mutableStateOf(0) }
    var badgeKeepAliveCounter by badgeKeepAliveCounterHolder
    // Video options sheet (three-dot menu + long-press on queue cards): the
    // video it is bound to, or null when closed.
    var sheetVideo by remember { mutableStateOf<ContentItem?>(null) }
    var showQueueSheet by remember { mutableStateOf(false) }
    // Stable callbacks — captured once per text by LinkifiedText's remember.
    val onTimestampClick: (Long) -> Unit = remember { { ms -> viewModel.seekToClamped(ms) } }
    val onLinkClick: (String) -> Unit =
        remember(context) {
            { url: String ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }

    val controlsVisibleHolder = remember { mutableStateOf(true) }
    var controlsVisible by controlsVisibleHolder
    // Bumped on every pointer down anywhere in the player: the auto-hide
    // effect is keyed on it, so any interaction restarts the 5s clock.
    var controlsActivityTick by remember { mutableStateOf(0L) }

    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }
    // Hold-seek scrub target while the player is paused mid-scrub (null = not
    // previewing). Drives the badge and the timeline playhead; cleared when
    // the scrub commits (SeekCommitted) or a new video loads.
    val seekPreviewMsHolder = remember { mutableStateOf<Long?>(null) }
    var seekPreviewMs by seekPreviewMsHolder
    // The last committed hold-seek target, held on the playhead until the
    // live position flow catches up (see the SeekCommitted handler).
    val pendingSeekMsHolder = remember { mutableStateOf<Long?>(null) }
    var pendingSeekMs by pendingSeekMsHolder


    val playbackEngine = remember(viewModel) { viewModel.playbackEngine() }

    // System picture-in-picture: the PiP window is the video itself — no
    // chrome, controls, or gestures. (SurfaceView composites correctly in
    // the PiP window; this branch swap means the PiP window owns the only
    // video surface, so there is nothing to fight over on entry/exit.)
    if (isPip) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PlayerVideoSurface(playbackEngine)
        }
        return
    }

    // ==================== Animation sync ====================
    val isMinimizedState = (uiState as? PlayerUiState.Loaded)?.isMinimized
    val isFullscreenState = (uiState as? PlayerUiState.Loaded)?.isFullscreen

    // Fullscreen-axis settles are launched in the composition scope — never
    // the sync effect's body, whose key-change restarts would cancel an
    // in-flight animation (the state flip that usually follows a drag commit
    // lands a few ms later and WOULD restart the effect mid-settle).
    val settleScope = rememberCoroutineScope()

    // Settle / snap / system-bar / overdrag-morph choreography (see
    // PlayerMorphSync); the gesture pipeline is created from it too.
    val insetsController =
        remember(context) {
            (context as? Activity)?.let {
                WindowInsetsControllerCompat(it.window, it.window.decorView)
            }
        }
    val morphSync =
        remember {
            PlayerMorphSync(
                coroutineScope = coroutineScope,
                settleScope = settleScope,
                surface = surface,
                morph = morph,
                viewModel = viewModel,
                context = context,
                configuration = configuration,
                insetsController = insetsController,
                controlsVisible = controlsVisibleHolder,
                detailsOverdragPendingExpandPx = detailsOverdragPendingExpandPx,
            )
        }
    // Keep the orientation state fresh across configuration changes (the
    // original local callbacks recaptured LocalConfiguration.current on
    // every recomposition).
    morphSync.configuration = configuration
    morphSync.SyncEffects(isMinimizedState, isFullscreenState, uiState)

    when (val state = uiState) {
        is PlayerUiState.Initial -> {
            // No player active — don't show anything
        }

        is PlayerUiState.Loaded -> {
            LaunchedEffect(state.currentVideo?.url) {
                isScrubbing = false
                scrubPositionMs = 0L
                seekPreviewMs = null
                pendingSeekMs = null
            }

            // Hold the committed hold-seek target on the playhead until the
            // live position flow catches up: nulling the preview immediately
            // flashes the old position for a frame while ExoPlayer applies
            // the seek.
            LaunchedEffect(pendingSeekMs) {
                val target = pendingSeekMs ?: return@LaunchedEffect
                withTimeoutOrNull(1_000) {
                    viewModel.positionMs.first { kotlin.math.abs(it - target) <= 750 }
                }
                pendingSeekMs = null
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

            LaunchedEffect(isFullscreen, isSmallWindow, isLandscape) {
                val activity = context as? Activity
                if (activity != null) {
                    if (isFullscreen) {
                        if (isSmallWindow) {
                            // Small windows rotate to landscape when entering
                            // fullscreen — asynchronously. Wait for the
                            // rotation so the bars are configured for the
                            // final orientation.
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            snapshotFlow { surface.containerSize.value }
                                .first { it.width > it.height }
                        }
                        // Edge-to-edge window: bar state is layout-neutral,
                        // so no timing relative to the settle is needed.
                        morphSync.setFullscreenBarsNow(true)
                    } else {
                        morphSync.setFullscreenBarsNow(false)
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
            // Auto-hide: 5 seconds after the last interaction. The pointer
            // observer on the root Box bumps controlsActivityTick on every
            // pointer down, restarting this effect with a fresh clock.
            // Fullscreen is NOT excluded: the clock runs there too, so the
            // bars hide 5s after the last touch (a tap brings them back).
            // isFullscreenAnim is kept as a key only, so entering/leaving
            // fullscreen starts a fresh clock.
            LaunchedEffect(
                controlsActivityTick,
                controlsVisible,
                state.isPlaying,
                state.isLoading,
                isMinimizedAnim,
                isFullscreenAnim,
            ) {
                // Never autohide while loading — the play/pause button hosts
                // the spinner and the bars must stay up to show it.
                if (isMinimizedAnim || !controlsVisible || state.isLoading) return@LaunchedEffect
                // Wait (without restarting) until a morph in progress settles.
                snapshotFlow { surface.morphProgress.value }
                    .first { p -> p <= 0.01f || p >= 0.99f }
                delay(5000)
                controlsVisible = false
            }

            LaunchedEffect(state.isPlaying) {
                if (!state.isPlaying) controlsVisible = true
            }

            // Same for loading: keep the controls visible while the
            // play/pause button shows the loading spinner.
            LaunchedEffect(state.isLoading) {
                if (state.isLoading) controlsVisible = true
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

            // Reset to the normal (max) height when leaving the floating
            // mini. Keyed on isMinimized only: after a fullscreen exit the
            // video returns to its pre-fullscreen height (which may be
            // collapsed) — snapping it to max the moment the fullscreen
            // flag cleared was a visible size pop right after the shrink
            // settled.
            LaunchedEffect(isMinimized) {
                if (!isMinimized) {
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

            // Rebuilt when the user edits Settings > Gestures.
            val gesturePrefs by viewModel.gesturePrefs.collectAsState()

            // ==================== Action badges (PlayerEventBus) ====================
            // Badges are driven by the player event bus, not the gesture
            // handler, so the same badges show for actions from the
            // controller, the companion screen, etc. — not just gestures.
            PlayerEventBadgeEffects(
                viewModel = viewModel,
                state = state,
                badgeState = badgeStateHolder,
                badgeKeepAliveCounter = badgeKeepAliveCounterHolder,
                seekPreviewMs = seekPreviewMsHolder,
                pendingSeekMs = pendingSeekMsHolder,
            )

            // ==================== Gesture pipeline ====================
            // Configs are derived from the user's gesture settings; the
            // pipeline owns recognition and action dispatch. Its settle
            // and snap wiring lives in PlayerMorphSync.
            val gesturePipeline =
                remember(viewModel) {
                    morphSync.createGesturePipeline()
                }
            val gestureConfigs =
                remember(gesturePrefs, gesturePipeline) {
                    gesturePipeline.configs(gesturePrefs)
                }

            // ==================== Tap handler (toggle controls) ====================
            // Stable identity (recreated only when uiState changes): lets the
            // big child subtrees below skip recomposition.
            val onTap: () -> Unit =
                remember(state) {
                    {
                        if (surface.morphProgress.value !in 0.01f..0.99f) {
                            controlsVisible = !controlsVisible
                            // The tap's pointer down already bumped
                            // controlsActivityTick, so the auto-hide effect
                            // restarts its 5s clock on its own.
                        }
                    }
                }

            // Keep the display on while actively playing (multi-display
            // devices share the same player state, so both windows follow
            // it); cleared on pause/end.
            LaunchedEffect(state.isPlaying) {
                view.keepScreenOn = state.isPlaying
            }

            // ==================== Compose ====================
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (isMinimized) {
                                Modifier
                            } else {
                                Modifier.pointerInput(Unit) {
                                    // Pure observer: any pointer down in
                                    // the player (video, bars, sliders,
                                    // chat) restarts the auto-hide clock.
                                    // Never consumes — it coexists with
                                    // every other handler.
                                    //
                                    // Must NOT be attached in floating mode:
                                    // registering pointer input on this
                                    // full-screen box makes the whole player
                                    // window a hit target, and Compose
                                    // hit-testing stops at the front-most
                                    // sibling that produced a hit — so the
                                    // feed behind the mini player would
                                    // never receive touches (it could not
                                    // scroll).
                                    while (true) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            controlsActivityTick++
                                        }
                                    }
                                }
                            },
                        ).onGloballyPositioned { coordinates ->
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
                                }.layout { measurable, _ ->
                                    val layout = surface.videoLayout(isLandscape, density)
                                    // Bottom edge follows the morph: full
                                    // container height at rest, video height
                                    // while morphing — converges on the mini
                                    // rect exactly at p=1 (and follows the
                                    // finger during a mini drag, since the
                                    // video rect does too).
                                    val p = surface.morphProgress.value
                                    val heightPx =
                                        (
                                            surface.containerSize.value.height * (1f - p) +
                                                layout.heightPx * p
                                        ).roundToInt() +
                                            // Overhang by the system-bar inset while
                                            // not mini: keeps the plate covering the
                                            // physical window when a bar show/hide
                                            // resizes the content view mid/after a
                                            // morph (exposed window background for
                                            // a frame otherwise).
                                            if (p < 0.5f) {
                                                surface.windowOverhangPx.value.toInt()
                                            } else {
                                                0
                                            }
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
                        playbackEngine = playbackEngine,
                        state = state,
                        positionMs = viewModel.positionMs,
                        seekThumbnails = viewModel.seekThumbnails,
                        surface = surface,
                        isLandscape = isLandscape,
                        controlsVisible = controlsVisible,
                        scrollState = scrollState,
                        nestedScrollConnection = nestedScrollConnection,
                        gestureConfigs = gestureConfigs,
                        gesturePipeline = gesturePipeline,
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
                        onRecommendedClick =
                            remember(viewModel) {
                                { video: com.tsutsen.platformplayer.core.model.VideoCard ->
                                    viewModel.play(video)
                                }
                            },
                        gridColumns = gridColumns,
                        onLoadMoreComments =
                            remember(state) { { viewModel.loadMoreComments(state.currentVideo?.url ?: "") } },
                        onLoadMoreRecommendations =
                            remember(state) { { viewModel.loadMoreRecommendations(state.currentVideo?.url ?: "") } },
                        isLoading = state.isLoading,
                        badgeState = badgeState,
                        onBadgeSessionEnded = remember { { badgeState = GestureBadgeState() } },
                        scrubPositionMs = scrubPositionMs,
                        seekPreviewMs = pendingSeekMs ?: seekPreviewMs,
                        subtitlesOn =
                            state.selectedSubtitle != TrackSelectionValues.OFF && state.selectedSubtitle != TrackSelectionValues.AUTO,
                        onSubtitleToggle = remember { { viewModel.toggleSubtitles() } },
                        onMinimize = remember { { viewModel.minimize() } },
                        onExpand = remember { { viewModel.exitMiniPlayer() } },
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
                        onDetailsOverdragStart =
                            remember {
                                { morphSync.startOverscrollMorph() }
                            },
                        onDetailsOverdrag =
                            remember {
                                { px -> morphSync.updateOverscrollMorph(px) }
                            },
                        onDetailsOverdragEnd =
                            remember {
                                { px, velocity -> morphSync.finishOverscrollMorph(px, velocity) }
                            },
                    )
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
                        onDownloadWithQuality = { quality ->
                            viewModel.startDownload(quality)
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

                // Queue button (top row) → the queue sheet.
                if (showQueueSheet) {
                    BluejayModalBottomSheet(
                        onDismiss = { showQueueSheet = false },
                        title = stringResource(R.string.player_queue_title),
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
            // Scoped to the video surface (same geometry as the player), not the
            // whole view — the rest of the UI stays visible/interactive.
            val density = LocalDensity.current
            val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            Box(
                modifier = Modifier
                    .offset {
                        val layout = surface.videoLayout(isLandscape, density)
                        IntOffset(layout.offsetX.toInt(), layout.offsetY.toInt())
                    }
                    .layout { measurable, constraints ->
                        val layout = surface.videoLayout(isLandscape, density)
                        val placeable = measurable.measure(
                            Constraints(
                                layout.widthPx.roundToInt(), layout.widthPx.roundToInt(),
                                layout.heightPx.roundToInt(), layout.heightPx.roundToInt(),
                            ),
                        )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (state.pluginId != null) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onPluginSettingsClick(state.pluginId) }) {
                            Text(stringResource(R.string.player_goto_plugin_settings))
                        }
                    }
                }
            }
        }
    }
}
