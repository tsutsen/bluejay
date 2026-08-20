package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.feature.player.impl.GestureBadgeState
import com.tsutsen.platformplayer.feature.player.impl.GestureIndicatorOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerCompactOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerFloatingOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerNormalBottomOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerNormalTopOverlay

private const val TAG = "PlayerControls"
private const val CONTROLS_SLIDE_DISTANCE_DP = 24

/**
 * Handles all controls for the current mode.
 * - NORMAL/COMPACT/FULLSCREEN: PlayerUIScaffold with top/bottom bars
 * - FLOATING: mini controls with eased fade-in
 *
 * During morph transition, both control sets are composited with alpha blending
 * for a smooth animated transition.
 */
@Composable
fun PlayerControls(
    modifier: Modifier,
    videoLayout: VideoLayout,
    miniProgress: Float,
    fullscreenProgress: Float,
    normalBarAlpha: Float,
    compactBarAlpha: Float,
    fullscreenBarAlpha: Float,
    isCollapsedControls: Boolean,
    controlsVisible: Boolean,
    showTopOverlay: Boolean,
    showBottomOverlay: Boolean,
    resolvedShowTopBar: Boolean,
    resolvedShowBottomBar: Boolean,
    isLoading: Boolean,
    activeProgressIndicator: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator.Progress?,
    badgeState: GestureBadgeState,
    onBadgeSessionEnded: () -> Unit = {},
    state: PlayerUiState.Loaded,
    player: ExoPlayer?,
    subtitlesOn: Boolean,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    expandedDescription: Boolean,
    selectedTab: Int,
    onToggleDescription: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onSubtitleToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onMoreOptions: () -> Unit,
    onWatchLater: () -> Unit,
    loopMode: Int,
    onLoopMode: () -> Unit,
    onQueue: () -> Unit,
    onOptions: () -> Unit,
    onSeek: (Long) -> Unit,
    isMorphDragging: Boolean,
    onMorphDragStart: () -> Unit,
    onMorphDrag: (dragY: Float) -> Unit,
    onMorphDragEnd: (dragY: Float) -> Unit,
) {
    val density = LocalDensity.current
    // Measured heights of the top/bottom control bars — used to shift the gesture
    // badge so it never overlaps the visible controls.
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // ==================== Controls hide/show animation ====================
    // `isMorphDragging` is driven by PlayerGestureSystem and passed in from PlayerView;
    // when true, controls fade out instantly so the morph drag visual is unobstructed.
    val controlsVisibleAlpha by animateFloatAsState(
        targetValue = if (controlsVisible && !isMorphDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "controlsVisibility",
    )

    Box(modifier = modifier) {
        // ==================== Normal controls (fade out during morph) ====================
        if (miniProgress <= MINI_DRAG_THRESHOLD) {
            // Fade out normal controls from MORPH_TRANSITION_START to MORPH_TRANSITION_END
            val normalAlpha =
                if (miniProgress <= MORPH_TRANSITION_START) {
                    1f
                } else if (miniProgress >= MORPH_TRANSITION_END) {
                    0f
                } else {
                    (MORPH_TRANSITION_END - miniProgress) / (MORPH_TRANSITION_END - MORPH_TRANSITION_START)
                }.coerceAtLeast(0f)
            // Combine with the restored controls hide/show animation - this drives the VISUAL
            // fade of the bars only. Composition below is still gated on normalAlpha (morph
            // position) alone: if it were gated on effectiveNormalAlpha instead, the gesture
            // detector inside (tap, double-tap-seek, morph-drag-start) would stop being
            // composed the moment controlsVisible goes false - e.g. while the video is loading
            // and controls default to hidden - which removes the only handler that could ever
            // detect the tap needed to bring controls back. Modifier.alpha(0f) still receives
            // touches in Compose, so keeping this subtree composed and just fading it visually
            // preserves tap-to-reveal even while fully transparent.
            val effectiveNormalAlpha = normalAlpha * controlsVisibleAlpha
            // Badge-shift flags: true only when the bar is actually visible on screen.
            val topBarVisible =
                resolvedShowTopBar && effectiveNormalAlpha > 0.01f &&
                    maxOf(normalBarAlpha, fullscreenBarAlpha) * (1f - miniProgress).coerceIn(0f, 1f) > 0.01f
            val bottomBarVisible =
                resolvedShowBottomBar && effectiveNormalAlpha > 0.01f &&
                    (1f - miniProgress).coerceIn(0f, 1f) > 0.01f
            if (normalAlpha > 0.01f) {
                Box(modifier = Modifier.alpha(effectiveNormalAlpha)) {
                    // Fade out gradient backgrounds along with controls
                    val gradientAlpha = effectiveNormalAlpha
                    PlayerUIScaffold(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset(
                                        x = videoLayout.offsetX.toInt(),
                                        y = videoLayout.offsetY.toInt(),
                                    )
                                }.size(
                                    width = with(density) { videoLayout.widthPx.toDp() },
                                    height = with(density) { videoLayout.heightPx.toDp() },
                                ),
                        isLoading = isLoading,
                        showTopBar = resolvedShowTopBar,
                        showBottomBar = resolvedShowBottomBar,
                        gradientAlpha = gradientAlpha,
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
                            val topAlpha = maxOf(normalBarAlpha, fullscreenBarAlpha) * (1f - miniProgress).coerceIn(0f, 1f)
                            if (topAlpha > 0.01f) {
                                Box(
                                    modifier =
                                        Modifier.graphicsLayer {
                                            alpha = topAlpha
                                            // Slide up and out on hide, slide down and in on show -
                                            // driven by the same controlsVisibleAlpha as the fade,
                                            // so both directions are symmetric by construction.
                                            translationY = (1f - controlsVisibleAlpha) * -CONTROLS_SLIDE_DISTANCE_DP.dp.toPx()
                                        }
                                            .onSizeChanged { topBarHeightPx = it.height },
                                ) {
                                    PlayerNormalTopOverlay(
                                        title = state.currentVideo?.title ?: "Unknown",
                                        channelName = state.currentVideo?.author?.name ?: "Unknown",
                                        onMinimize = onMinimize,
                                        loopMode = loopMode,
                                        onLoopMode = onLoopMode,
                                        onWatchLater = onWatchLater,
                                        onQueue = onQueue,
                                        onOptions = onOptions,
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            val bottomAlpha = (1f - miniProgress).coerceIn(0f, 1f)
                            if (bottomAlpha > 0.01f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                // Slide down and out on hide, slide up and in on
                                                // show - mirrors the top bar's upward slide.
                                                translationY = (1f - controlsVisibleAlpha) * CONTROLS_SLIDE_DISTANCE_DP.dp.toPx()
                                            }
                                            .onSizeChanged { bottomBarHeightPx = it.height },
                                ) {
                                    // FULLSCREEN bottom overlay
                                    if (fullscreenBarAlpha > 0.99f) {
                                        Box(modifier = Modifier.alpha(fullscreenBarAlpha)) {
                                            PlayerNormalBottomOverlay(
                                                currentPositionMs = state.currentPositionMs,
                                                durationMs = state.durationMs,
                                                isPlaying = state.isPlaying,
                                                onPlayPause = onPlayPause,
                                                onPrevious = onPrevious,
                                                onNext = onNext,
                                                onChapters = onChapters,
                                                onFullscreen = onFullscreenToggle,
                                                onSeek = onSeek,
                                                isScrubbing = isScrubbing,
                                                scrubPositionMs = scrubPositionMs,
                                                subtitlesOn = subtitlesOn,
                                                onSubtitleToggle = onSubtitleToggle,
                                                chapters = state.chapters,
                                            )
                                        }
                                    }

                                    // NORMAL ↔ COMPACT: animated swap
                                    if (fullscreenBarAlpha < 0.99f && miniProgress < MINI_DRAG_THRESHOLD) {
                                        androidx.compose.animation.AnimatedContent(
                                            targetState = isCollapsedControls,
                                            transitionSpec = {
                                                androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
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
                                                    onOptions = onOptions,
                                                    onFullscreen = onFullscreenToggle,
                                                )
                                            } else {
                                                PlayerNormalBottomOverlay(
                                                    currentPositionMs = state.currentPositionMs,
                                                    durationMs = state.durationMs,
                                                    isPlaying = state.isPlaying,
                                                    onPlayPause = onPlayPause,
                                                    onPrevious = onPrevious,
                                                    onNext = onNext,
                                                    onChapters = onChapters,
                                                    onFullscreen = onFullscreenToggle,
                                                    onSeek = onSeek,
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
            if (normalAlpha > 0.01f) {
                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    x = videoLayout.offsetX.toInt(),
                                    y = videoLayout.offsetY.toInt(),
                                )
                            }.size(
                                width = with(density) { videoLayout.widthPx.toDp() },
                                height = with(density) { videoLayout.heightPx.toDp() },
                            ),
                ) {
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
        // its clip/shadow/PlayerFloatingOverlay child - to be measured and laid out for the
        // first time on whatever frame miniProgress crossed MORPH_TRANSITION_START, which
        // read as an abrupt pop-in that clipped the still-fading normal controls. Keeping the
        // container composed across the full [0,1] range and driving visibility purely through
        // floatingAlpha keeps layout continuous; only the expensive inner content is gated.
        run {
            // Fade in floating controls from MORPH_TRANSITION_START to MORPH_TRANSITION_END
            val floatingAlpha =
                if (miniProgress <= MORPH_TRANSITION_START) {
                    0f
                } else if (miniProgress >= MORPH_TRANSITION_END) {
                    1f
                } else {
                    (miniProgress - MORPH_TRANSITION_START) / (MORPH_TRANSITION_END - MORPH_TRANSITION_START)
                }.coerceIn(0f, 1f)

            Box(
                modifier =
                    Modifier
                        .offset {
                            IntOffset(
                                x = videoLayout.offsetX.toInt(),
                                y = videoLayout.offsetY.toInt(),
                            )
                        }.size(
                            width = with(density) { videoLayout.widthPx.toDp() },
                            height = with(density) { videoLayout.heightPx.toDp() },
                        ).graphicsLayer { alpha = floatingAlpha },
            ) {
                // Skip composing the (relatively heavy) content entirely while invisible.
                // This still avoids the pop-in bug because the outer Box above - which owns
                // the offset/size that must stay continuous with videoLayout - is unconditional.
                if (floatingAlpha > 0.01f) {
                    // Shadow (hidden during morph transition)
                    if (floatingAlpha > 0.5f) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .offset(y = 4.dp * floatingAlpha)
                                    .graphicsLayer { alpha = 0.15f * floatingAlpha }
                                    .background(
                                        androidx.compose.ui.graphics.Color.Black,
                                        androidx.compose.foundation.shape
                                            .RoundedCornerShape(videoLayout.cornerRadius),
                                    ),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = floatingAlpha
                                    val scale = 0.92f + 0.08f * floatingAlpha
                                    scaleX = scale
                                    scaleY = scale
                                    translationY = (1f - floatingAlpha) * 12.dp.toPx()
                                }.clip(
                                    androidx.compose.foundation.shape
                                        .RoundedCornerShape(videoLayout.cornerRadius),
                                ),
                    ) {
                        PlayerFloatingOverlay(
                            state = state,
                            onPlayPause = onPlayPause,
                            onClose = onClose,
                            onFullscreen = onFullscreenToggle,
                        )
                    }
                }
            }
        }
    }
}
