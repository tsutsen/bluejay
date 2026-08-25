package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.feature.player.impl.GestureBadgeState
import com.tsutsen.platformplayer.feature.player.impl.GestureIndicatorOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerCompactOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerFloatingOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerNormalBottomOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerNormalTopOverlay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private const val TAG = "PlayerControls"
private const val CONTROLS_SLIDE_DISTANCE_DP = 24

/**
 * Handles all controls for the current mode.
 * - NORMAL/COMPACT/FULLSCREEN: PlayerUIScaffold with top/bottom bars
 * - FLOATING: mini controls with eased fade-in
 *
 * During morph transition, both control sets are composited with alpha blending
 * for a smooth animated transition.
 *
 * **Recomposition contract** (see [PlayerSurface]): the body only reads surface
 * state through `derivedStateOf` boolean gates (which flip rarely) and the
 * controls-visibility Animatable (which animates for 200ms on show/hide). All
 * per-frame morph alphas flow through frame-level modifier lambdas, so this
 * subtree does NOT recompose on animation frames.
 */
@Composable
fun PlayerControls(
    modifier: Modifier,
    surface: PlayerSurface,
    isLandscape: Boolean,
    controlsVisible: Boolean,
    isLoading: Boolean,
    activeProgressIndicator: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator.Progress?,
    badgeState: GestureBadgeState,
    onBadgeSessionEnded: () -> Unit = {},
    state: PlayerUiState.Loaded,
    positionMs: StateFlow<Long>,
    subtitlesOn: Boolean,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    onSubtitleToggle: () -> Unit,

    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onWatchLater: () -> Unit,
    isWatchLater: Boolean = false,
    loopMode: Int,
    onLoopMode: () -> Unit,
    onQueue: () -> Unit,
    onOptions: () -> Unit,
    onCast: (() -> Unit)? = null,
    onSeek: (Long) -> Unit,
    onScrubFinished: () -> Unit = {},
) {
    val density = LocalDensity.current

    // ==================== Controls hide/show animation ====================
    // Own Animatable (instead of animateFloatAsState) so the per-frame value can
    // be read from frame-level lambdas without subscribing this composable.
    // `isDraggingMorph` only flips on drag start/end — a safe direct body read.
    val isMorphDragging = surface.isDraggingMorph.value
    val controlsVisibleAlpha = remember { Animatable(1f) }
    LaunchedEffect(controlsVisible, isMorphDragging) {
        controlsVisibleAlpha.animateTo(
            if (controlsVisible && !isMorphDragging) 1f else 0f,
            tween(durationMillis = 200),
        )
    }

    // ==================== Rarely-flipping composition gates ====================
    val normalControlsComposed by remember(surface, isLandscape) {
        derivedStateOf { surface.morphProgress.value <= MINI_DRAG_THRESHOLD }
    }
    val normalBarVisible by remember(surface, isLandscape) {
        derivedStateOf { surface.morphFadeNow() > 0.01f }
    }
    val topBarComposed by remember(surface, isLandscape) {
        derivedStateOf { surface.topAlphaNow(isLandscape) > 0.01f }
    }
    val bottomBarComposed by remember(surface, isLandscape) {
        derivedStateOf { surface.bottomAlphaNow() > 0.01f }
    }
    val fullscreenBottomBar by remember(surface) {
        derivedStateOf { surface.fullscreenBarAlphaNow() > 0.99f }
    }
    val normalCompactSwap by remember(surface) {
        derivedStateOf {
            surface.fullscreenBarAlphaNow() < 0.99f &&
                surface.morphProgress.value < MINI_DRAG_THRESHOLD
        }
    }
    val isCollapsedControls by remember(surface, isLandscape) {
        derivedStateOf { surface.isCollapsedNow(isLandscape) }
    }
    val resolvedShowTopBar by remember(surface, isLandscape) {
        derivedStateOf { surface.resolvedShowTopBarNow(isLandscape) }
    }
    val resolvedShowBottomBar by remember(surface, isLandscape) {
        derivedStateOf { surface.resolvedShowBottomBarNow(isLandscape) }
    }
    val gradientsVisible by remember(surface, isLandscape) {
        derivedStateOf {
            surface.morphFadeNow() * controlsVisibleAlpha.value > 0.01f
        }
    }
    // Badge-shift flags: true only when the bar is actually visible on screen.
    val topBarVisible by remember(surface, isLandscape) {
        derivedStateOf {
            surface.resolvedShowTopBarNow(isLandscape) &&
                surface.morphFadeNow() * controlsVisibleAlpha.value > 0.01f &&
                surface.topAlphaNow(isLandscape) > 0.01f
        }
    }
    val bottomBarVisible by remember(surface, isLandscape) {
        derivedStateOf {
            surface.resolvedShowBottomBarNow(isLandscape) &&
                surface.morphFadeNow() * controlsVisibleAlpha.value > 0.01f &&
                surface.bottomAlphaNow() > 0.01f
        }
    }
    val floatingContentVisible by remember(surface) {
        derivedStateOf { surface.floatingAlphaNow() > 0.01f }
    }
    val floatingShadowVisible by remember(surface) {
        derivedStateOf { surface.floatingAlphaNow() > 0.5f }
    }

    // Measured heights of the top/bottom control bars — used to shift the gesture
    // badge so it never overlaps the visible controls.
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // ==================== Shared video-box modifier ====================
    // Frame-level lambdas: the bars track the morphing video rect without
    // recomposing anything.
    val videoBoxModifier =
        remember(surface, isLandscape, density) {
            Modifier
                .offset {
                    val layout = surface.videoLayout(isLandscape, density)
                    IntOffset(
                        x = layout.offsetX.toInt(),
                        y = layout.offsetY.toInt(),
                    )
                }.layout { measurable, constraints ->
                    // Frame-safe sizing: snapshot reads here trigger re-LAYOUT,
                    // never recomposition.
                    val layout = surface.videoLayout(isLandscape, density)
                    val placeable =
                        measurable.measure(
                            Constraints(layout.widthPx.roundToInt(), layout.widthPx.roundToInt(), layout.heightPx.roundToInt(), layout.heightPx.roundToInt()),
                        )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
        }

    Box(modifier = modifier) {
        // ==================== Normal controls (fade out during morph) ====================
        if (normalControlsComposed) {
            // The outer Box stays composed across controls show/hide (it is only
            // gated on the morph position): if it were gated on the combined
            // effective alpha instead, the gesture detector inside would stop
            // being composed the moment controlsVisible goes false - e.g. while
            // the video is loading and controls default to hidden - which would
            // remove the only handler that could ever detect the tap needed to
            // bring controls back. Modifier.alpha(0f) still receives touches in
            // Compose, so keeping this subtree composed and just fading it
            // visually preserves tap-to-reveal even while fully transparent.
            if (normalBarVisible) {
                Box(
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = surface.morphFadeNow() * controlsVisibleAlpha.value
                        },
                ) {
                    PlayerUIScaffold(
                        modifier = videoBoxModifier,
                        isLoading = isLoading,
                        showTopBar = resolvedShowTopBar,
                        showBottomBar = resolvedShowBottomBar,
                        gradientsVisible = gradientsVisible,
                        gradientAlpha = { surface.morphFadeNow() * controlsVisibleAlpha.value },
                        callbacks =
                            PlayerGestureCallbacks(
                                onTap = { /* handled by gesture layer */ },
                                onDoubleTap = { /* handled by gesture layer */ },
                                onVerticalDragStart = { /* handled by gesture layer */ },
                                onVerticalDrag = { _, _, _ -> /* handled by gesture layer */ },
                            ),
                        disableVerticalDragGestures = true,
                        disableTapGestures = true,
                        topBar = {
                            if (topBarComposed) {
                                Box(
                                    modifier =
                                        Modifier
                                            .graphicsLayer {
                                                alpha = surface.topAlphaNow(isLandscape)
                                                // Slide up and out on hide, slide down and in on show -
                                                // driven by the same controlsVisibleAlpha as the fade,
                                                // so both directions are symmetric by construction.
                                                translationY =
                                                    (1f - controlsVisibleAlpha.value) *
                                                        -CONTROLS_SLIDE_DISTANCE_DP.dp.toPx()
                                            }.onSizeChanged { topBarHeightPx = it.height },
                                ) {
                                    PlayerNormalTopOverlay(
                                        title = state.currentVideo?.title ?: "Unknown",
                                        channelName = state.currentVideo?.author?.name ?: "Unknown",
                                        onMinimize = onMinimize,
                                        loopMode = loopMode,
                                        onLoopMode = onLoopMode,
                                        onWatchLater = onWatchLater,
                                        isWatchLater = isWatchLater,
                                        onQueue = onQueue,
                                        onOptions = onOptions,
                                        onCast = onCast,
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            if (bottomBarComposed) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                // Slide down and out on hide, slide up and in on
                                                // show - mirrors the top bar's upward slide.
                                                translationY =
                                                    (1f - controlsVisibleAlpha.value) *
                                                        CONTROLS_SLIDE_DISTANCE_DP.dp.toPx()
                                            }.onSizeChanged { bottomBarHeightPx = it.height },
                                ) {
                                    // FULLSCREEN bottom overlay
                                    if (fullscreenBottomBar) {
                                        Box(
                                            modifier =
                                                Modifier.graphicsLayer {
                                                    alpha = surface.fullscreenBarAlphaNow()
                                                },
                                        ) {
                                            PlayerNormalBottomOverlay(
                                                positionMs = positionMs,
                                                durationMs = state.durationMs,
                                                isPlaying = state.isPlaying,
                                                onPlayPause = onPlayPause,
                                                onPrevious = onPrevious,
                                                onNext = onNext,
                                                onChapters = onChapters,
                                                onFullscreen = onFullscreenToggle,
                                                onSeek = onSeek,
                                                onScrubFinished = onScrubFinished,
                                                isScrubbing = isScrubbing,
                                                scrubPositionMs = scrubPositionMs,
                                                subtitlesOn = subtitlesOn,
                                                onSubtitleToggle = onSubtitleToggle,
                                                chapters = state.chapters,
                                            )
                                        }
                                    }

                                    // NORMAL ↔ COMPACT: animated swap
                                    if (normalCompactSwap) {
                                        AnimatedContent(
                                            targetState = isCollapsedControls,
                                            transitionSpec = {
                                                fadeIn() togetherWith fadeOut()
                                            },
                                            label = "normalCompactControls",
                                        ) { collapsed ->
                                            if (collapsed) {
                                                PlayerCompactOverlay(
                                                    isPlaying = state.isPlaying,
                                                    subtitlesOn = subtitlesOn,
                                                    onMinimize = onMinimize,
                                                    onPlayPause = onPlayPause,
                                                    onChapters = onChapters,
                                                    onSubtitleToggle = onSubtitleToggle,
                                                    onWatchLater = onWatchLater,
                                                    isWatchLater = isWatchLater,
                                                    onOptions = onOptions,
                                                    onFullscreen = onFullscreenToggle,
                                                )
                                            } else {
                                                PlayerNormalBottomOverlay(
                                                    positionMs = positionMs,
                                                    durationMs = state.durationMs,
                                                    isPlaying = state.isPlaying,
                                                    onPlayPause = onPlayPause,
                                                    onPrevious = onPrevious,
                                                    onNext = onNext,
                                                    onChapters = onChapters,
                                                    onFullscreen = onFullscreenToggle,
                                                    onSeek = onSeek,
                                                    onScrubFinished = onScrubFinished,
                                                    isScrubbing = isScrubbing,
                                                    scrubPositionMs = scrubPositionMs,
                                                    subtitlesOn = subtitlesOn,
                                                    onSubtitleToggle = onSubtitleToggle,
                                                    chapters = state.chapters,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }

            // ==================== Gesture Indicator (always on top, independent of controls) ====================
            if (normalBarVisible) {
                Box(modifier = videoBoxModifier) {
                    GestureIndicatorOverlay(
                        activeProgressIndicator = activeProgressIndicator,
                        badgeState = badgeState,
                        topBarHeightPx = topBarHeightPx,
                        bottomBarHeightPx = bottomBarHeightPx,
                        topBarVisible = topBarVisible,
                        bottomBarVisible = bottomBarVisible,
                        onBadgeSessionEnded = onBadgeSessionEnded,
                    )
                }
            }
        }

        // ==================== Floating controls (fade in during morph) ====================
        // NOTE: this subtree is ALWAYS composed (no outer `if` gate on miniProgress).
        // Gating composition on a miniProgress threshold caused the whole Box - including
        // its clip/shadow/PlayerFloatingOverlay child - to be measured and laid out for
        // the first time on whatever frame miniProgress crossed MORPH_TRANSITION_START,
        // which read as an abrupt pop-in that clipped the still-fading normal controls.
        // Keeping the container composed across the full [0,1] range and driving
        // visibility purely through floatingAlpha keeps layout continuous; only the
        // expensive inner content is gated.
        Box(
            modifier =
                videoBoxModifier.graphicsLayer { alpha = surface.floatingAlphaNow() },
        ) {
            // Skip composing the (relatively heavy) content entirely while invisible.
            // This still avoids the pop-in bug because the outer Box above - which owns
            // the offset/size that must stay continuous with the video rect - is
            // unconditional.
            if (floatingContentVisible) {
                // Shadow (hidden during morph transition)
                if (floatingShadowVisible) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .offset {
                                    IntOffset(0, (4f * density.density * surface.floatingAlphaNow()).roundToInt())
                                }
                                .graphicsLayer { alpha = 0.15f * surface.floatingAlphaNow() }
                                .background(
                                    Color.Black,
                                    RoundedCornerShape(
                                        surface.videoLayout(isLandscape, density).cornerRadius,
                                    ),
                                ),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val a = surface.floatingAlphaNow()
                                alpha = a
                                val scale = 0.92f + 0.08f * a
                                scaleX = scale
                                scaleY = scale
                                translationY = (1f - a) * 12.dp.toPx()
                            }.clip(
                                RoundedCornerShape(
                                    surface.videoLayout(isLandscape, density).cornerRadius,
                                ),
                            ),
                ) {
                    PlayerFloatingOverlay(
                        state = state,
                        positionMs = positionMs,
                        onPlayPause = onPlayPause,
                        onClose = onClose,
                        onFullscreen = onFullscreenToggle,
                    )
                }
            }
        }
    }
}
