package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Speed
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
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.feature.player.impl.GestureBadgeState
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureAnimationConstants
import androidx.compose.ui.graphics.vector.ImageVector
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator
import com.tsutsen.platformplayer.feature.player.impl.ui.CastingSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "PlayerScreen"

/** Window in which same-direction seeks accumulate into one running badge total. */
private const val SEEK_ACCUMULATE_WINDOW_MS = 800L

@Composable
fun PlayerView(
    viewModel: PlayerViewModel = hiltViewModel(),
    onChannelClick: (String) -> Unit = {},
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
    var activeProgressIndicator by remember { mutableStateOf<GestureIndicator.Progress?>(null) }
    var badgeState by remember { mutableStateOf(GestureBadgeState()) }
    var badgeKeepAliveCounter by remember { mutableStateOf(0) }
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

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every pointer down anywhere in the player: the auto-hide
    // effect is keyed on it, so any interaction restarts the 5s clock.
    var controlsActivityTick by remember { mutableStateOf(0L) }

    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }


    val player =
        remember(uiState) {
            (viewModel as? PlayerViewModel)?.getPlayer()?.exoPlayer
        }

    // System picture-in-picture: the PiP window is the video itself — no
    // chrome, controls, or gestures. (SurfaceView composites correctly in
    // the PiP window; this branch swap means the PiP window owns the only
    // video surface, so there is nothing to fight over on entry/exit.)
    if (isPip) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PlayerVideoSurface(player)
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
    // isSettlingFullscreen guards the sync effect while a settle runs; the
    // state flip is deliberately scheduled AFTER the video settles (see the
    // drag-END callbacks), so the flip's wave of work — details panel
    // composition, status bars re-inset, controls swap — lands on a still
    // frame instead of during the video's move.
    val settleScope = rememberCoroutineScope()

    /**
     * One pending snap per axis. Each new drag frame cancels the previous
     * frame's pending snap, and the settle/fold paths cancel the axis' pending
     * snap before animating — a stale snapTo would otherwise cancel the
     * in-flight tween and strand the settle flag.
     */
    val snapJobs = remember { HashMap<Animatable<Float, AnimationVector1D>, Job>() }

    fun snapAxis(
        axis: Animatable<Float, AnimationVector1D>,
        value: Float,
    ) {
        snapJobs[axis]?.cancel()
        snapJobs[axis] = coroutineScope.launch { axis.snapTo(value) }
    }

    /**
     * A release flick AWAY from the target would make the spring move the
     * wrong way before reversing — clamp it to rest. Toward the target it
     * is kept as-is (overshoot included: that is the momentum).
     */
    fun velocityToward(current: Float, target: Float, velocityPps: Float): Float =
        if (target >= current) velocityPps.coerceAtLeast(0f) else velocityPps.coerceAtMost(0f)

    /**
     * Settle the morph axis. Launched in the composition scope — never the
     * sync effect's body, whose key-change restarts would cancel an
     * in-flight animation — and guarded by isSettlingMorph, so the flip
     * that follows a drag commit lands a few ms later and is absorbed by
     * the guard instead of restarting the move.
     * [initialVelocityPps] seeds the settle spring ([PlayerSurface.MORPH_SETTLE_SPRING])
     * with the release velocity (progress per ms).
     */
    fun settleMorphTo(target: Float, initialVelocityPps: Float = 0f) {
        if (surface.isSettlingMorph.value) return
        // Already at rest at the target: nothing to settle. The sync effect
        // below is keyed on this flag and re-enters on every flag clear;
        // without this bail it re-settles to the same target forever — a
        // tight no-op loop that spins the main thread every frame.
        if (
            !surface.isDraggingMorph.value &&
            kotlin.math.abs(surface.morphProgress.value - target) <= 0.01f
        ) {
            return
        }
        surface.isSettlingMorph.value = true
        settleScope.launch {
            snapJobs[surface.morphProgress]?.cancel()
            try {
                if (kotlin.math.abs(surface.morphProgress.value - target) > 0.01f) {
                    surface.morphProgress.animateTo(
                        target,
                        surface.MORPH_SETTLE_SPRING,
                        initialVelocity = velocityToward(surface.morphProgress.value, target, initialVelocityPps),
                    )
                }
                // Land exactly on the target.
                surface.morphProgress.snapTo(target)
            } finally {
                surface.isSettlingMorph.value = false
                surface.isMinimizedAnim.value = target == 1f
                if (target == 0f) controlsVisible = true
            }
        }
    }

    fun settleFullscreenTo(
        target: Float,
        initialVelocityPps: Float = 0f,
        springSpec: SpringSpec<Float>? = null,
        after: (() -> Unit)? = null,
    ) {
        val spring = springSpec ?: surface.MORPH_SETTLE_SPRING
        if (surface.isSettlingFullscreen.value) return
        // Already at rest at the target: nothing to settle. The sync effect
        // below is keyed on this flag and re-enters on every flag clear;
        // without this bail it re-settles to the same target forever —
        // ~14 Hz of flag flips in fullscreen that keep the details gate
        // (and its 300 ms settle animation) running continuously.
        if (
            !surface.isDraggingFullscreen.value &&
            !surface.isDraggingShrink.value &&
            kotlin.math.abs(surface.fullscreenProgress.value - target) <= 0.01f
        ) {
            after?.invoke()
            return
        }
        // Set synchronously, BEFORE the launch: the sync effect below is keyed
        // on this flag and re-enters on the drag-end / state flips that land a
        // few ms later. If the flag were only set inside the launched
        // coroutine, those re-entries would pass the guard and launch a second
        // settle — each tween cancelling the other, restarting the 300ms tween
        // from its current position on every flag flip (the visible stutter and
        // velocity jump near the end of the morph).
        surface.isSettlingFullscreen.value = true
        settleScope.launch {
            snapJobs[surface.fullscreenProgress]?.cancel()
            if (target == 1f && surface.morphProgress.value < 0.5f) {
                // A minimize morph may still be running: wait for it to
                // settle so the two axes don't fight over the video rect.
                kotlinx.coroutines.delay(50)
            }
            try {
                if (kotlin.math.abs(surface.fullscreenProgress.value - target) > 0.01f) {
                    surface.fullscreenProgress.animateTo(
                        target,
                        spring,
                        initialVelocity = velocityToward(surface.fullscreenProgress.value, target, initialVelocityPps),
                    )
                }
                // Land exactly on the target.
                surface.fullscreenProgress.snapTo(target)
            } finally {
                // A newer same-priority mutate (a drag snapTo) interrupts this
                // mutate — the CancellationException unwinds this coroutine
                // quietly, so the flag MUST clear in finally, or every later
                // settle and the sync effect would bail on the stale flag
                // forever (the stuck-mid-morph state).
                surface.isSettlingFullscreen.value = false
            }
            after?.invoke()
        }
    }

    LaunchedEffect(
        isMinimizedState,
        surface.isDraggingMorph.value,
        surface.isSettlingMorph.value,
    ) {
        if (surface.isDraggingMorph.value || surface.isSettlingMorph.value) {
            return@LaunchedEffect
        }
        val minimized = isMinimizedState ?: return@LaunchedEffect
        settleMorphTo(if (minimized) 1f else 0f)
    }

    // ---- Details overdrag morph -----------------------------------------
    // The list is pinned at the top while (a) a collapsed video re-expands
    // (nested scroll, 1:1) and (b) the true overdrag zone follows the
    // finger into the fullscreen axis. detailsOverdragPendingExpandPx is
    // the (a) distance, measured at drag start; morph px = cumulative - it.
    // ==================== System bars (fullscreen coupling) ====================
    // The fullscreen anchor is the MEASURED window size, and hiding/showing
    // the system bars RESIZES that window on devices where the content view
    // is inset by the bars. So bar state must change only while the anchor
    // is irrelevant (fullscreen progress 0 or 1), never mid-morph — otherwise
    // the video height jumps when the resize lands mid-animation.
    val insetsController =
        remember(context) {
            (context as? Activity)?.let {
                WindowInsetsControllerCompat(it.window, it.window.decorView)
            }
        }

    /**
     * System-bar policy for fullscreen. The main window is edge-to-edge
     * (setDecorFitsSystemWindows(false) + LAYOUT_IN_SCREEN), so bar state
     * never resizes the content view — hide/show is layout-neutral and safe
     * at any point of a morph (the window that DOES resize on bar changes is
     * the companion's presentation window, which doesn't morph).
     *
     * - Landscape: the video reaches every edge → hide both bars.
     * - Portrait: the video is a letterboxed 16:9 — hide the status bar
     *   (immersive) but keep the navigation bar (it sits below the video and
     *   is the way back).
     */
    fun setFullscreenBarsNow(fullscreen: Boolean) {
        val controller = insetsController ?: return
        // Window orientation, NOT the player container: in normal portrait
        // the video is a WIDE 16:9 letterbox, so container-size comparison
        // misreads it as landscape and hid the NAV bar at drag start — the
        // bottom-inset reflow behind the video was the morph stutter. The
        // button path only looked correct because its effect ran after the
        // container had already grown tall.
        val isPortrait =
            configuration.orientation !=
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val bars =
            if (isPortrait) {
                androidx.core.view.WindowInsetsCompat.Type
                    .statusBars()
            } else {
                androidx.core.view.WindowInsetsCompat.Type
                    .systemBars()
            }
        if (fullscreen) {
            controller.hide(bars)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(bars)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    val isLandscapeNow: () -> Boolean =
        { surface.containerSize.value.width > surface.containerSize.value.height }

    /**
     * Begin the overdrag-to-fullscreen morph: remember how much of the
     * drag still has to re-expand the video, claim the fullscreen axis,
     * and hide the system bars (edge-to-edge: layout-neutral, so hiding
     * at drag start is free).
     */
    fun beginOverscrollMorph(pendingExpandPx: Float) {
        detailsOverdragPendingExpandPx.value = pendingExpandPx
        surface.isDraggingFullscreen.value = true
        setFullscreenBarsNow(true)
    }

    /**
     * Overdrag-to-fullscreen is only allowed from NORMAL. A drag that
     * starts in COMPACT re-expands the video via the normal nested scroll
     * first — starting the morph (and hiding the system bars) there fired
     * both transitions in one gesture and flashed the bars on cancel.
     * Such a drag lazy-starts in [updateOverscrollMorph] once the video is
     * fully expanded and the drag continues.
     */
    fun startOverscrollMorph() {
        val isLand = isLandscapeNow()
        if (surface.isCollapsedNow(isLand)) return
        val pending =
            (surface.maxPlayerHeightPx(isLand) - surface.playerHeightPx.value)
                .coerceAtLeast(0f)
        beginOverscrollMorph(pending)
    }

    // Morph travel = the distance the video still has to GROW: full window
    // height minus current video height. The video bottom then follows the
    // finger 1:1, and sensitivity self-adjusts per orientation: portrait
    // (small video, long travel) commits late, landscape (big video, short
    // travel) commits early — matching the room actually available to drag.
    fun fullscreenOverdragTravelPx(): Float =
        (surface.windowSize.value.height - surface.playerHeightPx.value)
            .coerceAtLeast(1f)

    fun updateOverscrollMorph(cumulativePx: Float) {
        if (!surface.isDraggingFullscreen.value) {
            // Lazy start: a drag that began in COMPACT consumed its px via
            // the nested-scroll re-expansion. Engage the fullscreen morph
            // only once the video is fully expanded and the drag continues
            // past the top — counting all px accumulated so far as the
            // re-expansion distance so the morph starts from 0.
            if (cumulativePx > 0f && !surface.isCollapsedNow(isLandscapeNow())) {
                beginOverscrollMorph(cumulativePx)
            } else {
                return
            }
        }
        val over =
            (cumulativePx - detailsOverdragPendingExpandPx.value)
                .coerceAtLeast(0f)
        if (over <= 0f) return // still re-expanding the collapsed video
        val travel = fullscreenOverdragTravelPx()
        val progress = if (travel > 0f) (over / travel).coerceIn(0f, 1f) else 0f
        snapAxis(surface.fullscreenProgress, progress)
    }

    fun finishOverscrollMorph(
        cumulativePx: Float,
        velocityPxPerMs: Float = 0f,
    ) {
        if (!surface.isDraggingFullscreen.value) return // never started (COMPACT drag)
        surface.isDraggingFullscreen.value = false
        val travel = fullscreenOverdragTravelPx()
        val over =
            (cumulativePx - detailsOverdragPendingExpandPx.value)
                .coerceAtLeast(0f)
        detailsOverdragPendingExpandPx.value = 0f
        // Release velocity in progress/ms (same seeding as the fullscreen
        // drag axis); velocityToward clamps a flick away from the target to
        // a settle-from-rest.
        val vPps = if (travel > 0f) velocityPxPerMs / travel else 0f
        if (travel > 0f && over > 0.4f * travel) {
            // Committed: flip state first (details fade out),
            // then settle to full.
            viewModel.toggleFullscreen()
            settleFullscreenTo(1f, vPps, springSpec = surface.OVERDRAG_SETTLE_SPRING)
        } else {
            // Cancelled: the bars were hidden when the drag started — put
            // them back (the isFullscreen effect doesn't fire: state didn't
            // change).
            setFullscreenBarsNow(false)
            settleFullscreenTo(0f, vPps, springSpec = surface.OVERDRAG_SETTLE_SPRING)
        }
    }

    LaunchedEffect(
        isFullscreenState,
        surface.isDraggingFullscreen.value,
        surface.isDraggingShrink.value,
        surface.isSettlingFullscreen.value,
    ) {
        // Don't fight the finger mid-drag: the drag callbacks own
        // fullscreenProgress/shrinkProgress until END; the settle that
        // follows is launched in the composition scope and guarded by
        // isSettlingFullscreen, so restarts here can neither cancel it nor
        // double-start it.
        if (
            surface.isDraggingFullscreen.value ||
            surface.isDraggingShrink.value ||
            surface.isSettlingFullscreen.value
        ) {
            return@LaunchedEffect
        }
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        surface.isFullscreenAnim.value = fullscreen
        // Button / back flips move the axis to the new rest position.
        settleFullscreenTo(if (fullscreen) 1f else 0f)
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
                        setFullscreenBarsNow(true)
                    } else {
                        setFullscreenBarsNow(false)
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
                isMinimizedAnim,
                isFullscreenAnim,
            ) {
                if (isMinimizedAnim || !controlsVisible) return@LaunchedEffect
                // Wait (without restarting) until a morph in progress settles.
                snapshotFlow { surface.morphProgress.value }
                    .first { p -> p <= 0.01f || p >= 0.99f }
                delay(5000)
                controlsVisible = false
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

            // ==================== Gesture configs (defaults + user overrides) ====================
            // Rebuilt when the user edits Settings > Gestures.
            val gesturePrefs by viewModel.gesturePrefs.collectAsState()
            val gestureConfigs =
                remember(gesturePrefs) {
                    com.tsutsen.platformplayer.feature.player.impl.gesture
                        .buildGestureConfigs(gesturePrefs)
                }

            // ==================== Action badges (PlayerEventBus) ====================
            // Badges are driven by the player event bus, not the gesture
            // handler, so the same badges show for actions from the
            // controller, the companion screen, etc. — not just gestures.
            LaunchedEffect(Unit) {
                // Consecutive same-direction seeks within the window accumulate
                // into one running total (double-tap -5s -5s -> "-10s").
                var seekTotalMs = 0L
                var seekDir = 0
                var seekTime = 0L
                var capsuleJob: Job? = null

                fun showCapsule(key: String, level: Float, icon: ImageVector) {
                    activeProgressIndicator =
                        GestureIndicator.Progress(key = key, value = level, icon = icon)
                    capsuleJob?.cancel()
                    capsuleJob = launch {
                        delay(GestureAnimationConstants.INDICATOR_HIDE_DELAY_MS)
                        if (activeProgressIndicator?.key == key) activeProgressIndicator = null
                    }
                }

                PlayerEventBus.events.collect { event ->
                    when (event) {
                        is PlayerEvent.Seek -> {
                            val now = System.currentTimeMillis()
                            val dir = if (event.deltaMs < 0) -1 else 1
                            seekTotalMs =
                                if (dir == seekDir && now - seekTime < SEEK_ACCUMULATE_WINDOW_MS)
                                    seekTotalMs + event.deltaMs
                                else event.deltaMs
                            seekDir = dir
                            seekTime = now
                            val seconds = seekTotalMs / 1000
                            badgeKeepAliveCounter++
                            badgeState =
                                GestureBadgeState(
                                    key = if (dir < 0) "rewind_back" else "rewind_forward",
                                    label = if (seconds > 0) "+${seconds}s" else "${seconds}s",
                                    icon =
                                        if (dir < 0)
                                            Icons.Default.Replay10
                                        else Icons.Default.Forward10,
                                    visible = true,
                                    keepAlive = badgeKeepAliveCounter,
                                )
                        }

                        is PlayerEvent.PlaybackSpeedChanged -> {
                            badgeKeepAliveCounter++
                            badgeState =
                                GestureBadgeState(
                                    key = "speed",
                                    label = "%.2fx".format(event.speed),
                                    icon = Icons.Outlined.Speed,
                                    visible = true,
                                    keepAlive = badgeKeepAliveCounter,
                                )
                        }

                        is PlayerEvent.BrightnessChanged ->
                            showCapsule("brightness", event.level, Icons.Default.BrightnessHigh)

                        is PlayerEvent.VolumeChanged ->
                            showCapsule("volume", event.level, Icons.Default.VolumeUp)

                        // PlayPauseToggled / NextRequested / PreviousRequested /
                        // Closed: no badge yet — they still pass through the bus
                        // for future consumers.
                        else -> Unit
                    }
                }
            }

            // ==================== Gesture action handler ====================
            val gestureHandler =
                remember {
                    com.tsutsen.platformplayer.feature.player.impl.gesture.PlayerGestureActionHandler(
                        viewModel = viewModel,
                        screenHeight = { surface.containerSize.value.height },
                        context = context,
                        activity = context as? android.app.Activity,
                        onMorphDragStart = { surface.isDraggingMorph.value = true },
                        onMorphDrag = { dragY ->
                            // Read the surface at call time — this block is
                            // remembered once, so no locals may be captured.
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            snapAxis(surface.morphProgress, progress)
                        },
                        onMorphDragEnd = { dragY, velocityPxPerMs ->
                            surface.isDraggingMorph.value = false
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            // Commit: the flip lands async — the settle guard
                            // absorbs it (the settle is already running).
                            if (progress > 0.4f) viewModel.minimize()
                            // Settle NOW, seeded with the release velocity:
                            // the spring continues the finger's motion
                            // instead of freezing (grace window) and
                            // restarting from rest (tween).
                            settleMorphTo(
                                if (progress > 0.4f) 1f else 0f,
                                if (travel > 0f) velocityPxPerMs / travel else 0f,
                            )
                        },
                        onShrinkDragStart = { surface.isDraggingShrink.value = true },
                        onShrinkDrag = { dragY ->
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            snapAxis(surface.shrinkProgress, progress)
                        },
                        onShrinkDragEnd = { dragY, velocityPxPerMs ->
                            surface.isDraggingShrink.value = false
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            if (progress > 0.4f) {
                                // Committed morph-to-normal: fold the shrink axis
                                // into the fullscreen axis (single-axis move),
                                // then animate to normal WHILE STILL FULLSCREEN.
                                // The state flip happens only after the video
                                // settles, so its wave of work (details panel
                                // composition, status bars re-inset, controls
                                // swap) lands on a still frame, not mid-motion.
                                // The fold must land before the settle starts;
                                // both dispatch to the same scope in order.
                                // Cancel the last shrink frame's pending snap
                                // first: it could run after the fold and
                                // re-raise shrinkProgress past 0.
                                snapJobs[surface.shrinkProgress]?.cancel()
                                coroutineScope.launch {
                                    val effective =
                                        surface.fullscreenProgress.value *
                                            (1f - surface.shrinkProgress.value)
                                    surface.fullscreenProgress.snapTo(effective)
                                    surface.shrinkProgress.snapTo(0f)
                                }
                                // The fullscreen axis inherits the shrink
                                // velocity, reversed: the shrink axis grows
                                // downward as the fs axis shrinks downward.
                                settleFullscreenTo(
                                    0f,
                                    if (travel > 0f) -velocityPxPerMs / travel else 0f,
                                ) { viewModel.exitFullscreen() }
                            } else {
                                // Cancel: spring the shrink axis back —
                                // reversed velocity keeps it continuous with
                                // the release instead of a snap-pop.
                                snapJobs[surface.shrinkProgress]?.cancel()
                                coroutineScope.launch {
                                    surface.shrinkProgress.animateTo(
                                        0f,
                                        surface.MORPH_SETTLE_SPRING,
                                        initialVelocity = if (travel > 0f) -velocityPxPerMs / travel else 0f,
                                    )
                                }
                            }
                        },
                        onFullscreenDragStart = {
                            surface.isDraggingFullscreen.value = true
                            // Edge-to-edge: layout-neutral, so hiding at
                            // drag start is free.
                            setFullscreenBarsNow(true)
                        },
                        onFullscreenDrag = { dragY ->
                            // Read the surface at call time — this block is
                            // remembered once, so no locals may be captured.
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            snapAxis(surface.fullscreenProgress, progress)
                        },
                        onFullscreenDragEnd = { dragY, velocityPxPerMs ->
                            surface.isDraggingFullscreen.value = false
                            val travel = surface.dragTravelPx()
                            val progress = if (travel > 0f) (dragY / travel).coerceIn(0f, 1f) else 0f
                            if (progress > 0.4f) {
                                // Committed expand: flip state NOW (the details
                                // fade-out and system bars key off it), then
                                // settle to fullscreen on a spring seeded with
                                // the release velocity; the settle guard keeps
                                // the in-flight flip from restarting the move.
                                viewModel.toggleFullscreen()
                                settleFullscreenTo(1f, if (travel > 0f) velocityPxPerMs / travel else 0f)
                            } else {
                                // Cancelled: bars were hidden at drag start.
                                setFullscreenBarsNow(false)
                                settleFullscreenTo(0f, if (travel > 0f) velocityPxPerMs / travel else 0f)
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
                        onRecommendedClick =
                            remember(viewModel) {
                                { video: com.tsutsen.platformplayer.core.model.VideoCard ->
                                    viewModel.play(video)
                                }
                            },
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
                        onDetailsOverdragStart =
                            remember {
                                { startOverscrollMorph() }
                            },
                        onDetailsOverdrag =
                            remember {
                                { px -> updateOverscrollMorph(px) }
                            },
                        onDetailsOverdragEnd =
                            remember {
                                { px, velocity -> finishOverscrollMorph(px, velocity) }
                            },
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
                                    shape = RoundedCornerShape(BluejayTokens().radius.card),
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
