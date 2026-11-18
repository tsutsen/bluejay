package com.tsutsen.platformplayer.feature.player.impl

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowInsetsControllerCompat
import com.tsutsen.platformplayer.core.designsystem.layout.DockChromeProgress
import com.tsutsen.platformplayer.feature.player.impl.gesture.DefaultGesturePipeline
import com.tsutsen.platformplayer.feature.player.impl.gesture.MorphCoordinator
import com.tsutsen.platformplayer.feature.player.impl.gesture.PlayerGestureEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settle / snap / system-bar / overdrag-morph choreography for the player
 * surface, extracted verbatim from PlayerView's composition scope. Owns the
 * per-axis pending snap jobs, the morph/fullscreen settle functions, the
 * fullscreen system-bar policy, the details-overdrag-to-fullscreen logic and
 * the gesture-pipeline wiring; the state-sync effects run in [SyncEffects].
 */
internal class PlayerMorphSync(
    private val coroutineScope: CoroutineScope,
    private val settleScope: CoroutineScope,
    val surface: PlayerSurface,
    private val morph: MorphCoordinator,
    private val viewModel: PlayerViewModel,
    private val context: Context,
    // Refreshed by PlayerView on every recomposition — the original local
    // callbacks recaptured LocalConfiguration.current each recomposition.
    internal var configuration: Configuration,
    private val insetsController: WindowInsetsControllerCompat?,
    private val controlsVisible: MutableState<Boolean>,
    private val detailsOverdragPendingExpandPx: MutableState<Float>,
) {
    /**
     * One pending snap per axis. Each new drag frame cancels the previous
     * frame's pending snap, and the settle/fold paths cancel the axis' pending
     * snap before animating — a stale snapTo would otherwise cancel the
     * in-flight tween and strand the settle flag.
     */
    private val snapJobs = HashMap<Animatable<Float, AnimationVector1D>, Job>()

    fun snapAxis(
        axis: Animatable<Float, AnimationVector1D>,
        value: Float,
    ) {
        snapJobs[axis]?.cancel()
        snapJobs[axis] = coroutineScope.launch { axis.snapTo(value) }
    }

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
                        initialVelocity = morph.velocityToward(surface.morphProgress.value, target, initialVelocityPps),
                    )
                }
                // Land exactly on the target.
                surface.morphProgress.snapTo(target)
            } finally {
                surface.isSettlingMorph.value = false
                surface.isMinimizedAnim.value = target == 1f
                if (target == 0f) controlsVisible.value = true
            }
        }
    }

    // Fullscreen-axis settles are launched in the composition scope — never
    // the sync effect's body, whose key-change restarts would cancel an
    // in-flight animation (the state flip that usually follows a drag commit
    // lands a few ms later and WOULD restart the effect mid-settle).
    // isSettlingFullscreen guards the sync effect while a settle runs; the
    // state flip is deliberately scheduled AFTER the video settles (see the
    // drag-END callbacks), so the flip's wave of work — details panel
    // composition, status bars re-inset, controls swap — lands on a still
    // frame instead of during the video's move.
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
                        initialVelocity = morph.velocityToward(surface.fullscreenProgress.value, target, initialVelocityPps),
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
        snapAxis(surface.fullscreenProgress, morph.dragProgress(over, fullscreenOverdragTravelPx()))
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
        val vPps = morph.releaseVelocity(velocityPxPerMs, travel)
        if (morph.dragProgress(over, travel) > MorphCoordinator.COMMIT_FRACTION) {
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

    // ==================== Gesture pipeline ====================
    // Configs are derived from the user's gesture settings; the
    // pipeline owns recognition and action dispatch.
    fun createGesturePipeline(): DefaultGesturePipeline =
        DefaultGesturePipeline(
            PlayerGestureEffects(
                viewModel = viewModel,
                screenHeight = { surface.containerSize.value.height },
                context = context,
                activity = context as? android.app.Activity,
                onMorphDragStart = { surface.isDraggingMorph.value = true },
                onMorphDrag = { dragY ->
                    // Read the surface at call time — this block is
                    // remembered once, so no locals may be captured.
                    snapAxis(surface.morphProgress, morph.dragProgress(dragY, surface.dragTravelPx()))
                },
                onMorphDragEnd = { dragY, velocityPxPerMs ->
                    surface.isDraggingMorph.value = false
                    val travel = surface.dragTravelPx()
                    val settle =
                        morph.release(morph.dragProgress(dragY, travel), velocityPxPerMs, travel)
                    // Commit: the flip lands async — the settle guard
                    // absorbs it (the settle is already running).
                    if (settle.commit) viewModel.minimize()
                    // Settle NOW, seeded with the release velocity:
                    // the spring continues the finger's motion
                    // instead of freezing (grace window) and
                    // restarting from rest (tween).
                    settleMorphTo(settle.target, settle.velocityProgressPerMs)
                },
                onShrinkDragStart = { surface.isDraggingShrink.value = true },
                onShrinkDrag = { dragY ->
                    snapAxis(surface.shrinkProgress, morph.dragProgress(dragY, surface.dragTravelPx()))
                },
                onShrinkDragEnd = { dragY, velocityPxPerMs ->
                    surface.isDraggingShrink.value = false
                    val travel = surface.dragTravelPx()
                    val settle =
                        morph.shrinkRelease(morph.dragProgress(dragY, travel), velocityPxPerMs, travel)
                    if (settle.commit) {
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
                            // Fold read at execution time (see above).
                            surface.fullscreenProgress.snapTo(
                                morph.fold(surface.fullscreenProgress.value, surface.shrinkProgress.value),
                            )
                            surface.shrinkProgress.snapTo(0f)
                        }
                        settleFullscreenTo(0f, settle.velocityProgressPerMs) {
                            viewModel.exitFullscreen()
                        }
                    } else {
                        // Cancel: spring the shrink axis back —
                        // reversed velocity keeps it continuous with
                        // the release instead of a snap-pop.
                        snapJobs[surface.shrinkProgress]?.cancel()
                        coroutineScope.launch {
                            surface.shrinkProgress.animateTo(
                                0f,
                                surface.MORPH_SETTLE_SPRING,
                                initialVelocity = settle.velocityProgressPerMs,
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
                    snapAxis(surface.fullscreenProgress, morph.dragProgress(dragY, surface.dragTravelPx()))
                },
                onFullscreenDragEnd = { dragY, velocityPxPerMs ->
                    surface.isDraggingFullscreen.value = false
                    val travel = surface.dragTravelPx()
                    val settle =
                        morph.release(morph.dragProgress(dragY, travel), velocityPxPerMs, travel)
                    if (settle.commit) {
                        // Committed expand: flip state NOW (the details
                        // fade-out and system bars key off it), then
                        // settle to fullscreen on a spring seeded with
                        // the release velocity; the settle guard keeps
                        // the in-flight flip from restarting the move.
                        viewModel.toggleFullscreen()
                        settleFullscreenTo(settle.target, settle.velocityProgressPerMs)
                    } else {
                        // Cancelled: bars were hidden at drag start.
                        setFullscreenBarsNow(false)
                        settleFullscreenTo(settle.target, settle.velocityProgressPerMs)
                    }
                },
            ),
        )

    /**
     * State-sync effects, called from PlayerView's composition in the same
     * order the effects originally appeared there.
     */
    @Composable
    fun SyncEffects(
        isMinimizedState: Boolean?,
        isFullscreenState: Boolean?,
        uiState: PlayerUiState,
    ) {
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

        // Portrait dock choreography: mirror the fullscreen axis (shrink
        // included) into the app shell's DockChromeProgress every animation
        // frame, so the dock slides out with the growing video continuously
        // instead of popping on the isFullscreen flag flip (which lands at
        // drag-end commit — too late, the video already covers the dock).
        // Reset on dispose so a closed player never strands the dock hidden.
        LaunchedEffect(surface) {
            snapshotFlow { surface.effectiveFullscreenNow() }.collect {
                DockChromeProgress.fullscreen = it.coerceIn(0f, 1f)
            }
        }
        DisposableEffect(surface) {
            onDispose { DockChromeProgress.fullscreen = 0f }
        }

        LaunchedEffect(uiState) {
            if (uiState is PlayerUiState.Loaded) {
                surface.playerFadeInProgress.animateTo(1f, tween(durationMillis = 600))
            } else {
                surface.playerFadeInProgress.animateTo(0f)
            }
        }
    }
}
