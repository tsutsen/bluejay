package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerCompactOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerFloatingOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerNormalBottomOverlay
import com.tsutsen.platformplayer.feature.player.impl.ui.overlays.PlayerNormalTopOverlay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "PlayerControls"

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
    visibility: ControlsVisibility,
    controlsVisible: Boolean,
    resolvedShowTopBar: Boolean,
    resolvedShowBottomBar: Boolean,
    isLoading: Boolean,
    brightnessValue: Float,
    volumeValue: Float,
    showBrightnessIndicator: Boolean,
    showVolumeIndicator: Boolean,
    state: PlayerUiState.Loaded,
    player: ExoPlayer?,
    isLooping: Boolean,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    expandedDescription: Boolean,
    selectedTab: Int,
    onToggleDescription: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onLoopToggle: () -> Unit,
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
    onReplayToggle: () -> Unit,
    onOptions: () -> Unit,
    onSeek: (Long) -> Unit,
    gestureCallbacks: PlayerGestureCallbacks,
    onMorphDragStart: () -> Unit,
    onMorphDrag: (dragY: Float) -> Unit,
    onMorphDragEnd: (dragY: Float) -> Unit
) {
    val density = LocalDensity.current

    // ==================== Controls hide/show animation ====================
    // `controlsVisible` was previously threaded through as a parameter but never actually
    // applied to anything - restoring that here. Also tracks morph-drag-active locally so
    // controls hide the instant a morph swipe starts, regardless of what drives
    // `controlsVisible` upstream. `isMorphDragging` is flipped in the gesture handler below,
    // right where onMorphDragStart()/onMorphDragEnd() already fire.
    var isMorphDragging by remember { mutableStateOf(false) }
    val controlsVisibleAlpha by animateFloatAsState(
        targetValue = if (controlsVisible && !isMorphDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "controlsVisibility"
    )

    Box(modifier = modifier) {
        // ==================== Normal controls (fade out during morph) ====================
        if (miniProgress <= PlayerMorphConfig.Default.miniDragThreshold) {
            val config = PlayerMorphConfig.Default
            // Fade out normal controls from MORPH_TRANSITION_START to MORPH_TRANSITION_END
            val normalAlpha = visibility.normalBarAlpha
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
            AnimatedVisibility(
                visible = normalAlpha > 0.01f,
                enter = fadeIn(animationSpec = tween(config.effectiveDuration(200))),
                exit = fadeOut(animationSpec = tween(config.effectiveDuration(200))),
            ) {
                Box(modifier = Modifier.alpha(effectiveNormalAlpha)) {
                    // Fade out gradient backgrounds along with controls
                    val gradientAlpha = effectiveNormalAlpha
                    PlayerUIScaffold(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = videoLayout.offsetX.toInt(),
                                    y = videoLayout.offsetY.toInt()
                                )
                            }
                            .size(
                                width = with(density) { videoLayout.widthPx.toDp() },
                                height = with(density) { videoLayout.heightPx.toDp() }
                            )
                            .pointerInput(Unit) {
                                var lastTapTime = 0L
                                var lastTapX = 0f
                                var lastTapY = 0f

                                awaitEachGesture {
                                    // requireUnconsumed = true is essential here: without it, a
                                    // tap that started on a child control (Play/Pause, minimize,
                                    // chapters, etc.) still reaches this loop even after the
                                    // child's own clickable already consumed it, and gets
                                    // (mis)treated as a background tap - toggling
                                    // controlsVisible as an unwanted side effect of pressing any
                                    // button. That's what caused controls to blink on
                                    // play/pause: the button press toggled controlsVisible off,
                                    // then the isPlaying-changed effect in PlayerView.kt forced
                                    // it back on a frame later.
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    var totalDragY = 0f
                                    var pastSlop = false
                                    var pointerId = down.id
                                    var isDownward = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                            ?: break

                                        // A child (e.g. a button's long-press ripple) claimed
                                        // this touch after the initial down - bail out rather
                                        // than treating it as a background tap or morph drag.
                                        // Once pastSlop is true we've already claimed the
                                        // gesture ourselves via change.consume() below, so this
                                        // check only applies before that point.
                                        if (!pastSlop && change.isConsumed) break

                                        if (change.previousPressed && !change.pressed) {
                                            if (!pastSlop) {
                                                if (miniProgress > PlayerMorphConfig.Default.miniSettledThreshold) break

                                                val now = System.currentTimeMillis()
                                                val dx = change.previousPosition.x - lastTapX
                                                val dy = change.previousPosition.y - lastTapY
                                                val dist = sqrt(dx * dx + dy * dy)

                                                if (now - lastTapTime < PlayerMorphConfig.Default.doubleTapIntervalMs && dist < PlayerMorphConfig.Default.touchSlop) {
                                                    val videoWidth = videoLayout.widthPx
                                                    val third = videoWidth / 3f
                                                    if (change.position.x < third) {
                                                        onSeek(-5000)
                                                    } else if (change.position.x > videoWidth - third) {
                                                        onSeek(5000)
                                                    }
                                                } else {
                                                    gestureCallbacks.onTap()
                                                }
                                                lastTapTime = now
                                                lastTapX = change.position.x
                                                lastTapY = change.position.y
                                            } else {
                                                isMorphDragging = false
                                                onMorphDragEnd(totalDragY)
                                            }
                                            break
                                        }

                                        val dy = change.position.y - change.previousPosition.y
                                        if (!pastSlop) {
                                            totalDragY += dy
                                            if (abs(totalDragY) > PlayerMorphConfig.Default.touchSlop) {
                                                pastSlop = true
                                                if (totalDragY > 0f) {
                                                    isDownward = true
                                                    isMorphDragging = true
                                                    onMorphDragStart()
                                                    change.consume()
                                                    onMorphDrag(totalDragY)
                                                } else {
                                                    break
                                                }
                                            }
                                        } else {
                                            if (isDownward) {
                                                totalDragY += dy
                                                change.consume()
                                                onMorphDrag(totalDragY)
                                            }
                                        }
                                    }
                                }
                            },
                        isLoading = isLoading,
                        brightnessValue = brightnessValue,
                        volumeValue = volumeValue,
                        showBrightnessIndicator = showBrightnessIndicator,
                        showVolumeIndicator = showVolumeIndicator,
                        showTopBar = visibility.showNormalTopBar,
                        showBottomBar = visibility.showNormalBottomBar,
                        gradientAlpha = gradientAlpha,
                        callbacks = PlayerGestureCallbacks(
                            onTap = { /* handled by gesture layer */ },
                            onDoubleTap = { /* handled by gesture layer */ },
                            onVerticalDragStart = { /* handled by gesture layer */ },
                            onVerticalDrag = { _, _, _ -> /* handled by gesture layer */ }
                        ),
                        disableVerticalDragGestures = true,
                        disableTapGestures = true,
                        topBar = {
                            val topAlpha = visibility.normalBarAlpha
                            AnimatedVisibility(
                                visible = topAlpha > 0.01f,
                                enter = fadeIn(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(200))),
                                exit = fadeOut(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(200))),
                            ) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        // Slide up and out on hide, slide down and in on show -
                                        // driven by the same controlsVisibleAlpha as the fade,
                                        // so both directions are symmetric by construction.
                                        translationY = (1f - controlsVisibleAlpha) * -config.controlsSlideDistanceDp.dp.toPx()
                                    }
                                ) {
                                    PlayerNormalTopOverlay(
                                        title = state.currentVideo?.title ?: "Unknown",
                                        channelName = state.currentVideo?.author?.name ?: "Unknown",
                                        onMinimize = onMinimize,
                                        onReplayToggle = onReplayToggle,
                                        onWatchLater = onWatchLater,
                                        onOptions = onOptions
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            AnimatedVisibility(
                                visible = visibility.normalBarAlpha > 0.01f,
                                enter = fadeIn(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(200))),
                                exit = fadeOut(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(200))),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            // Slide down and out on hide, slide up and in on
                                            // show - mirrors the top bar's upward slide.
                                            translationY = (1f - controlsVisibleAlpha) * config.controlsSlideDistanceDp.dp.toPx()
                                        }
                                ) {
                                    // FULLSCREEN bottom overlay
                                    AnimatedVisibility(
                                        visible = visibility.showFullscreenBar,
                                        enter = fadeIn(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(200))),
                                        exit = fadeOut(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(200))),
                                    ) {
                                        PlayerNormalBottomOverlay(
                                            player = player,
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
                                            scrubPositionMs = scrubPositionMs
                                        )
                                    }

                                    // NORMAL ↔ COMPACT: animated swap
                                    if (!visibility.showFullscreenBar && miniProgress < PlayerMorphConfig.Default.miniDragThreshold) {
                                        androidx.compose.animation.AnimatedContent(
                                            targetState = visibility.showCompactBar,
                                            transitionSpec = {
                                                androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                                            },
                                            label = "normalCompactControls"
                                        ) { collapsed ->
                                            if (collapsed) {
                                                PlayerCompactOverlay(
                                                    isPlaying = state.isPlaying,
                                                    isLooping = isLooping,
                                                    onMinimize = onMinimize,
                                                    onPlayPause = onPlayPause,
                                                    onChapters = onChapters,
                                                    onLoopToggle = onLoopToggle,
                                                    onWatchLater = onWatchLater,
                                                    onOptions = onOptions,
                                                    onFullscreen = onFullscreenToggle
                                                )
                                            } else {
                                                PlayerNormalBottomOverlay(
                                                    player = player,
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
                                                    scrubPositionMs = scrubPositionMs
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
        val floatingAlpha = visibility.floatingAlpha

        AnimatedVisibility(
            visible = visibility.showFloatingOverlay,
            enter = fadeIn(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(250))),
            exit = fadeOut(animationSpec = tween(PlayerMorphConfig.Default.effectiveDuration(250))),
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = videoLayout.offsetX.toInt(),
                            y = videoLayout.offsetY.toInt()
                        )
                    }
                    .size(
                        width = with(density) { videoLayout.widthPx.toDp() },
                        height = with(density) { videoLayout.heightPx.toDp() }
                    )
            ) {
                if (floatingAlpha > 0.5f) {
                    // Shadow (hidden during morph transition)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 4.dp * floatingAlpha)
                            .graphicsLayer { alpha = 0.15f * floatingAlpha }
                            .background(androidx.compose.ui.graphics.Color.Black,
                                androidx.compose.foundation.shape.RoundedCornerShape(videoLayout.cornerRadius))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 0.92f + 0.08f * floatingAlpha
                            scaleX = scale
                            scaleY = scale
                            translationY = (1f - floatingAlpha) * 12.dp.toPx()
                        }
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(videoLayout.cornerRadius))
                ) {
                    PlayerFloatingOverlay(
                        state = state,
                        onPlayPause = onPlayPause,
                        onClose = onClose,
                        onFullscreen = onFullscreenToggle
                    )
                }
            }
        }
    }
}
