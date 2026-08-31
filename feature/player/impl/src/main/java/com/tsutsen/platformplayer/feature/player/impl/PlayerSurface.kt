package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// ==================== Morph / fade thresholds (shared by surface + UI layers) ====================
const val MINI_DRAG_THRESHOLD = 0.98f
const val MINI_SETTLED_THRESHOLD = 0.01f
const val MORPH_TRANSITION_START = 0.3f // When morph transition begins
const val MORPH_TRANSITION_END = 0.7f // When morph transition completes
const val DETAILS_FADE_START = 0.1f // Details start fading out earlier
const val DETAILS_FADE_END = 0.4f // Details fully faded before controls complete
const val FULLSCREEN_SETTLED_THRESHOLD = 0.01f

// ==================== Mini-player geometry (single source of truth) ====================
private val MINI_PLAYER_WIDTH = 280.dp
private val MINI_PLAYER_HEIGHT = MINI_PLAYER_WIDTH * 9f / 16f
private val MINI_PLAYER_PADDING = 16.dp

/** Spring that glides the mini player from the release position to a snapped edge. */
private val MINI_PLAYER_SETTLE_SPRING =
    spring<Float>(
        stiffness = Spring.StiffnessHigh,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )

/**
 * Deep geometry module for the player surface.
 *
 * Owns ALL animated and measured surface state — morph/fullscreen progress,
 * player fade, mini-player drag offsets, container/window measurements,
 * scroll-collapse height — and exposes *frame-safe accessors* for the derived
 * geometry the UI layers consume.
 *
 * **Recomposition contract (why this module exists):**
 *
 * The UI layers read these values in exactly two ways:
 *
 * 1. **Frame-level modifier lambdas** (`offset { }`, `width { }`,
 *    `graphicsLayer { }`, `Modifier.alpha { }`) — for values that change on
 *    every animation frame (video rect, cross-fade alphas). The lambda is
 *    re-evaluated by the framework when the read state changes, WITHOUT
 *    recomposing anything. This is how the video surface and the bars move
 *    during morph/fullscreen/collapse at full refresh rate.
 * 2. **`derivedStateOf` in a composable body** — for the boolean *gates*
 *    (bar visibility, panel visibility, collapsed mode). The computation runs
 *    whenever its inputs change, but recomposition only fires when the
 *    derived boolean actually flips.
 *
 * Reading a per-frame value directly in a composable body (a bare
 * `surface.fooNow()` call or an `Animatable.value` read) is a BUG: it makes
 * that composable — and its whole subtree, comments list and live chat
 * included — recompose on every animation frame.
 *
 * Pure geometry (anchors, lerp, overlay mode) lives in [PlayerGeometry.kt]
 * and [PlayerOverlayMode.kt]; this module is the state machine around it.
 *
 * Layering:
 * - [PlayerView] owns *policy*: LaunchedEffects feed uiState targets into
 *   this module (driving the Animatables, resetting collapse height,
 *   clamping drag offsets) and gesture callbacks dispatch viewModel intents
 *   (minimize/fullscreen).
 */
class PlayerSurface(private val scope: CoroutineScope) {
    // ==================== Measurements (fed by the screen) ====================
    /** True window size (nav-bar independent), from the decor view layout. */
    val windowSize = mutableStateOf(Size.Zero)
    /** Player Box size, from onGloballyPositioned. */
    val containerSize = mutableStateOf(Size.Zero)

    // ==================== Animated geometry (owned) ====================
    /** 0 = NORMAL, 1 = FLOATING (mini player). */
    val morphProgress = Animatable(0f)
    /** 0 = NORMAL, 1 = FULLSCREEN. */
    val fullscreenProgress = Animatable(0f)
    /**
     * Shrink axis for the morph-to-normal gesture: 0 = still fullscreen,
     * 1 = fully normal. Only meaningful while in fullscreen; the effective
     * fullscreen progress is [fullscreenProgress] × (1 − this).
     */
    val shrinkProgress = Animatable(0f)
    /** 0 = hidden, 1 = visible (per-player fade-in). */
    val playerFadeInProgress = Animatable(0f)
    /** Set by the sync LaunchedEffects once the morph settles. */
    val isMinimizedAnim = mutableStateOf(false)
    val isFullscreenAnim = mutableStateOf(false)

    // ==================== Drag state ====================
    val isDraggingMorph = mutableStateOf(false)
    val isDraggingFullscreen = mutableStateOf(false)
    val isDraggingShrink = mutableStateOf(false)
    val isDraggingMiniPlayer = mutableStateOf(false)

    /**
     * Set by the morph drag-END callback (minimize path only). The sync
     * effect consumes it to wait a short grace window before settling:
     * the committed flip lands a few frames after the callback (async VM
     * call), and settling to the opposite target in the meantime would dip
     * and reverse.
     */
    var morphDragJustEnded = false

    /**
     * True while a fullscreen-axis settle animation is in flight. Settle
     * animations are launched in the composition scope (never the sync
     * effect's body, whose restarts would cancel them); this flag guards
     * the sync effect meanwhile and is a key of it, so the effect
     * re-enters to finish the bookkeeping once the settle completes.
     *
     * The details panel also reads it: its first layout is heavy (comments,
     * recommendations, live chat) and must not compose mid-motion.
     */
    val isSettlingFullscreen = mutableStateOf(false)
    val miniPlayerOffsetX = mutableStateOf(0f)
    val miniPlayerOffsetY = mutableStateOf(0f)

    /**
     * The mini offset while NOT dragging. Only ever written by
     * [startMiniDrag]/[endMiniDrag]/[cancelMiniDrag]: at drag end it is
     * snap-set to the exact release (finger) position before the drag flag
     * clears, then spring-glide-animated to the snapped target. Because the
     * snap happens in the same step as the flag flip, the render switch in
     * [miniOffsetNow] is never discontinuous — no chase lag, no stale copy.
     */
    val miniEasedOffsetX = Animatable(0f)
    val miniEasedOffsetY = Animatable(0f)
    /** Guards against stale release coroutines from superseded drags. */
    private var miniDragGeneration = 0


    // ==================== Scroll-collapse height ====================
    /** Current collapsible player height (px), driven by the nested scroll. */
    val playerHeightPx = mutableStateOf(0f)

    /**
     * Max height of the collapsible player.
     * - Landscape: 70% of the container height, leaving room for the
     *   details page below the video.
     * - Portrait: a width-constrained 16:9 box (0.7 of the tall portrait
     *   window height would make the video far too large).
     */
    fun maxPlayerHeightPx(isLandscape: Boolean): Float {
        val c = containerSize.value
        return if (isLandscape) c.height * 0.7f else c.width * 9f / 16f
    }

    /** Scroll-collapsible lower bound (drag the video shorter). */
    fun minPlayerHeightPx(isLandscape: Boolean, maxPlayerHeightPx: Float): Float {
        val c = containerSize.value
        return if (isLandscape) c.height * 0.2f else maxPlayerHeightPx * 0.4f
    }

    /** Drag travel distance that maps to the full 0..1 morph progress. */
    fun dragTravelPx(): Float = containerSize.value.height * 0.45f

    // ==================== Mini-player geometry ====================

    fun miniWidthPx(density: Density): Float = with(density) { MINI_PLAYER_WIDTH.toPx() }

    fun miniHeightPx(density: Density): Float = with(density) { MINI_PLAYER_HEIGHT.toPx() }

    fun miniSizePx(density: Density): Size = Size(miniWidthPx(density), miniHeightPx(density))

    /** Resting (bottom-end) top-left corner of the floating mini player. */
    fun floatingRestPx(density: Density): Offset {
        val c = containerSize.value
        val pad = with(density) { MINI_PLAYER_PADDING.toPx() }
        return Offset(c.width - miniWidthPx(density) - pad, c.height - miniHeightPx(density) - pad)
    }

    /**
     * Current mini offset: the raw (finger) offset while dragging — 1:1
     * tracking, zero lag — and the eased copy otherwise. The eased copy is
     * snap-seeded to the exact finger position in the same step that the
     * flag clears, so the switch can never show a stale/lagged position.
     *
     * Note: after [endMiniDrag] the flag stays set until the release
     * coroutine runs (at most a frame), so a fast drag + release never
     * renders the pre-release eased position.
     */
    fun miniOffsetNow(): Offset =
        if (isDraggingMiniPlayer.value) {
            Offset(miniPlayerOffsetX.value, miniPlayerOffsetY.value)
        } else {
            Offset(miniEasedOffsetX.value, miniEasedOffsetY.value)
        }

    /**
     * Start a mini-player drag: re-seed the raw offset from the eased copy
     * (which may be mid-glide from a previous release — grabbing mid-glide
     * must not pop) and mark dragging.
     */
    fun startMiniDrag() {
        miniPlayerOffsetX.value = miniEasedOffsetX.value
        miniPlayerOffsetY.value = miniEasedOffsetY.value
        isDraggingMiniPlayer.value = true
    }

    /**
     * End a mini-player drag and glide to the snapped target.
     *
     * The raw offset keeps holding the release (finger) position — the
     * render stays on it (flag still set) until the coroutine below snap-seeds
     * the eased copy to that exact position and clears the flag, so the
     * switch is pixel-continuous even after a fast flick. The spring then
     * carries the eased copy from the finger position to the snap target.
     */
    fun endMiniDrag(snappedX: Float, snappedY: Float) {
        val generation = ++miniDragGeneration
        scope.launch {
            if (generation != miniDragGeneration) return@launch
            miniEasedOffsetX.snapTo(miniPlayerOffsetX.value)
            miniEasedOffsetY.snapTo(miniPlayerOffsetY.value)
            if (generation != miniDragGeneration) return@launch
            isDraggingMiniPlayer.value = false
            if (generation != miniDragGeneration) return@launch
            // Both axes glide simultaneously (corner snaps must be diagonal,
            // not a two-leg staircase).
            coroutineScope {
                launch { miniEasedOffsetX.animateTo(snappedX, MINI_PLAYER_SETTLE_SPRING) }
                launch { miniEasedOffsetY.animateTo(snappedY, MINI_PLAYER_SETTLE_SPRING) }
            }
        }
    }

    /** Drag cancelled: settle at the last finger position, no snap. */
    fun cancelMiniDrag() {
        endMiniDrag(miniPlayerOffsetX.value, miniPlayerOffsetY.value)
    }

    // ==================== Per-frame geometry (frame-safe accessors) ====================
    // All of these are meant to be called from frame-level modifier lambdas
    // (or derivedStateOf for the booleans). They are allocation-free.

    /**
     * Effective fullscreen progress after the shrink axis: dragging
     * morph-to-normal in fullscreen lowers this toward 0, driving both the
     * surface geometry and the bar cross-fade.
     */
    fun effectiveFullscreenNow(): Float =
        fullscreenProgress.value * (1f - shrinkProgress.value)

    /** Current video rect (position + size + corner) for the given orientation. */
    fun videoLayout(isLandscape: Boolean, density: Density): VideoLayout {
        val container = containerSize.value
        val window = windowSize.value
        val miniSize = miniSizePx(density)
        val rest = floatingRestPx(density)
        val drag = miniOffsetNow()
        return computeVideoLayout(
            miniProgress = morphProgress.value,
            fullscreenProgress = effectiveFullscreenNow(),
            containerWidth = container.width,
            containerHeight = container.height,
            playerHeightPx = playerHeightPx.value,
            miniWidthPx = miniSize.width,
            miniHeightPx = miniSize.height,
            floatingRestX = rest.x,
            floatingRestY = rest.y,
            dragOffsetX = drag.x,
            dragOffsetY = drag.y,
            fullscreenWidthPx = if (window.width > 0f) window.width else container.width,
            fullscreenHeightPx = if (window.height > 0f) window.height else container.height,
        )
    }

    // ==================== Control-bar cross-fade alphas ====================

    fun normalBarAlphaNow(isLandscape: Boolean): Float =
        (1f - morphProgress.value) *
            (1f - effectiveFullscreenNow()) *
            (if (isCollapsedNow(isLandscape)) 0f else 1f)

    fun compactBarAlphaNow(isLandscape: Boolean): Float =
        (1f - morphProgress.value) *
            (1f - effectiveFullscreenNow()) *
            (if (isCollapsedNow(isLandscape)) 1f else 0f)

    fun fullscreenBarAlphaNow(): Float = effectiveFullscreenNow() * (1f - morphProgress.value)

    /** Morph-position fade of the normal controls: 1 at p<=START, 0 at p>=END. */
    fun morphFadeNow(): Float {
        val p = morphProgress.value
        return when {
            p <= MORPH_TRANSITION_START -> 1f
            p >= MORPH_TRANSITION_END -> 0f
            else -> (MORPH_TRANSITION_END - p) / (MORPH_TRANSITION_END - MORPH_TRANSITION_START)
        }.coerceAtLeast(0f)
    }

    /** Complement of [morphFadeNow]: the floating mini controls' fade-in. */
    fun floatingAlphaNow(): Float = 1f - morphFadeNow()

    /** Top bar cross-fade: normal bar vs fullscreen bar, scaled out during morph. */
    fun topAlphaNow(isLandscape: Boolean): Float =
        maxOf(normalBarAlphaNow(isLandscape), fullscreenBarAlphaNow()) *
            (1f - morphProgress.value).coerceIn(0f, 1f)

    /** Bottom bar fade out during morph. */
    fun bottomAlphaNow(): Float = (1f - morphProgress.value).coerceIn(0f, 1f)

    // ==================== Details panel cascade fade ====================
    // Fades out earlier than controls for a cascading effect.

    fun detailsAlphaNow(isLandscape: Boolean): Float {
        val p = morphProgress.value
        val detailsFade =
            when {
                p <= DETAILS_FADE_START -> 1f
                p >= DETAILS_FADE_END -> 0f
                else -> (DETAILS_FADE_END - p) / (DETAILS_FADE_END - DETAILS_FADE_START)
            }.coerceAtLeast(0f)
        return ((1f - p) * (1f - effectiveFullscreenNow()) * detailsFade).coerceAtLeast(0f)
    }

    fun detailsTranslateYNow(): Float =
        lerp(
            0f,
            containerSize.value.height * 0.3f,
            maxOf(morphProgress.value, effectiveFullscreenNow()),
        )

    // ==================== Collapsed (scroll) mode ====================

    /**
     * "Collapsed" = the video has been dragged down to (or below) half
     * of its own maximum height. Measuring against maxHeight (not the
     * window height) is orientation-independent: in portrait the 16:9
     * video is only ~25% of the tall window height, so a fixed
     * window-fraction made it read as permanently collapsed and never
     * entered NORMAL mode.
     */
    fun isCollapsedNow(isLandscape: Boolean): Boolean {
        val maxHeight = maxPlayerHeightPx(isLandscape)
        return !isFullscreenAnim.value &&
            maxHeight > 0f &&
            (playerHeightPx.value / maxHeight) <= 0.5f
    }

    // ==================== Resolved scaffold bar visibility ====================

    private fun fullscreenSettledNow(): Boolean {
        val fsP = effectiveFullscreenNow()
        return fsP < FULLSCREEN_SETTLED_THRESHOLD || fsP > (1f - FULLSCREEN_SETTLED_THRESHOLD)
    }

    /**
     * NOTE: the top-bar branch keeps the `!isCollapsed` check of the
     * `else` (idle) branch. Without it, starting a drag while controls
     * were collapsed flips visibility from false -> true on the very
     * first pixel of drag movement, while normalBarAlpha is still ~1.0
     * (it doesn't start fading until MORPH_TRANSITION_START), so the
     * top scrim pops in at full opacity instead of fading in with
     * everything else.
     */
    fun resolvedShowTopBarNow(isLandscape: Boolean): Boolean {
        val p = morphProgress.value
        val fsP = effectiveFullscreenNow()
        val isCollapsed = isCollapsedNow(isLandscape)
        val miniMorphAlpha = (1f - p).coerceIn(0f, 1f)
        return when {
            p > MINI_SETTLED_THRESHOLD -> {
                miniMorphAlpha > 0.01f && !isCollapsed && fullscreenSettledNow()
            }

            fsP > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> {
                true
            }

            else -> {
                !isCollapsed
            }
        }
    }

    fun resolvedShowBottomBarNow(isLandscape: Boolean): Boolean {
        val p = morphProgress.value
        val fsP = effectiveFullscreenNow()
        val miniMorphAlpha = (1f - p).coerceIn(0f, 1f)
        return when {
            p > MINI_SETTLED_THRESHOLD -> {
                miniMorphAlpha > 0.01f && fullscreenSettledNow()
            }

            fsP > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> {
                true
            }

            else -> {
                true
            }
        }
    }
}
