package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureActionHandler
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureConfigs
import com.tsutsen.platformplayer.feature.player.impl.gesture.PlayerGestureSystem
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Unified player content composable. Delegates to GestureLayer, ControlsLayer, and
 * DetailsPanel.
 *
 * The [PlayerVideoSurface] never leaves composition across mode changes — it is positioned
 * and sized via frame-level `offset/width/height` lambdas that read
 * [PlayerSurface.videoLayout] (see the recomposition contract on [PlayerSurface]).
 *
 * **This composable body must only read surface state that flips rarely** (via
 * `derivedStateOf` booleans or layout-level measurements like [PlayerSurface.containerSize]).
 * Per-frame values (video rect, alphas) flow through modifier lambdas, so the heavy
 * subtrees (details list, comments, live chat) never recompose on animation frames.
 */
@Composable
fun PlayerContent(
    player: ExoPlayer?,
    state: PlayerUiState.Loaded,
    positionMs: StateFlow<Long>,
    surface: PlayerSurface,
    isLandscape: Boolean,
    controlsVisible: Boolean,
    scrollState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    gestureConfigs: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureConfigs,
    gestureHandler: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureActionHandler,
    isScrubbing: Boolean,
    onMiniOffsetChanged: (x: Float, y: Float) -> Unit,
    onTap: () -> Unit,
    onOptions: () -> Unit,
    onCast: (() -> Unit)? = null,
    onChapters: () -> Unit,
    expandedDescription: Boolean,
    onToggleDescription: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onRecommendedClick: (VideoCard) -> Unit,
    gridColumns: Int,
    onLoadMoreComments: () -> Unit,
    onChannelClick: (String) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMore: () -> Unit,
    isSubscribedChannel: Boolean = false,
    onSubscribe: () -> Unit = {},
    onTimestampClick: (Long) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    isLoading: Boolean,
    activeProgressIndicator: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator.Progress?,
    badgeState: com.tsutsen.platformplayer.feature.player.impl.GestureBadgeState,
    onBadgeSessionEnded: () -> Unit = {},
    scrubPositionMs: Long,
    subtitlesOn: Boolean,
    onSubtitleToggle: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    isLive: Boolean = false,
    liveChat: com.tsutsen.platformplayer.core.model.LiveChatUiState? = null,
    loopMode: Int,
    onLoopMode: () -> Unit,
    onWatchLater: () -> Unit,
    isWatchLater: Boolean = false,
    onQueue: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubFinished: () -> Unit = {},
    onFullscreenToggle: () -> Unit,
    onDetailsOverdragStart: () -> Unit = {},
    onDetailsOverdrag: (overdragPx: Float) -> Unit = {},
    onDetailsOverdragEnd: (overdragPx: Float) -> Unit = {},
) {
    val density = LocalDensity.current

    // ==================== Shared video modifier ====================
    // Per-frame values are read INSIDE the lambdas: the framework re-runs them
    // when the underlying surface state changes, without recomposing anything.
    // (computeVideoLayout is a small pure function; calling it once per lambda
    // per frame is negligible.)
    val videoModifier =
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
                }.graphicsLayer {
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(surface.videoLayout(isLandscape, density).cornerRadius)
                    clip = true
                }
        }

    // Rarely-flipping composition gates: derivedStateOf re-computes when its
    // inputs change but only recomposes when the derived boolean flips — never
    // per animation frame.
    val showSubtitles by remember(surface) {
        derivedStateOf { surface.morphProgress.value < MINI_SETTLED_THRESHOLD }
    }
    // Computed directly in the body, NOT in a derivedStateOf: `state` is a
    // plain data class (not snapshot-observable), so a derivedStateOf would
    // capture the first `state` instance forever and never see
    // minimize/fullscreen flips — the gesture layer would stay in NORMAL
    // mode forever (no mini-drag, no fullscreen volume/brightness).
    // isCollapsedNow only flips on scroll-collapse/layout changes, so this
    // read costs no per-frame recompositions.
    val overlayMode =
        computePlayerOverlayMode(
            state.isMinimized,
            state.isFullscreen,
            surface.isCollapsedNow(isLandscape),
        )
    // The details panel's first layout is heavy (comments, recommendations,
    // live chat): it must not compose while the fullscreen axis is animating,
    // or that cost drops frames during the video's move. While IN fullscreen
    // it stays composed only through the entry fade-out (so it never pops
    // out mid-animation) and is dropped once settled — including during the
    // morph-to-normal exit motion, which runs while still fullscreen.
    // Seeded synchronously on the flip frame (remember key): an effect-seeded
    // flag would be read stale by the derived for one frame, dropping the
    // panel on the way into fullscreen (blink) before re-adding it.
    val isFullscreenNow = state.isFullscreen
    val entryFadeRemaining =
        remember(isFullscreenNow) {
            isFullscreenNow && surface.detailsAlphaNow(isLandscape) > 0.01f
        }
    val detailsVisible by remember(surface, isLandscape, isFullscreenNow) {
        derivedStateOf {
            if (isFullscreenNow) {
                entryFadeRemaining && surface.detailsAlphaNow(isLandscape) > 0.01f
            } else {
                surface.detailsAlphaNow(isLandscape) > 0.01f &&
                    !surface.isSettlingFullscreen.value
            }
        }
    }
    // Time-based fade-IN: the p-based alpha window (0.1-0.4) is traversed in
    // only ~90ms of the 300ms click-to-expand tween, so the details would
    // pop in. Multiply by a settle that runs 0->1 whenever the details
    // (re)appear. The 100ms wait lets the details subtree's first heavy
    // compose/measure frame land while the alpha is still 0, so the fade
    // itself runs on lighter frames. Fade-OUT stays p-based, so drags
    // remain tied to the finger. settle stays 0 while the panel is hidden so
    // a re-compose can never flash a full-alpha frame before the fade starts
    // (that flash was the fullscreen->normal blink).
    val detailsSettle = remember { Animatable(0f) }
    LaunchedEffect(detailsVisible) {
        if (detailsVisible) {
            detailsSettle.snapTo(0f)
            kotlinx.coroutines.delay(100)
            detailsSettle.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        } else {
            detailsSettle.snapTo(0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 1. Persistent video surface ====================
        // The black plate behind the floating mini player is the page scrim
        // in PlayerView — it follows the video box through the morph and
        // lands exactly on the mini rect, so no separate scrim is needed
        // here.
        PlayerVideoSurface(player = player, modifier = Modifier.then(videoModifier))

        // ==================== 1b. Subtitle overlay ====================
        // Rendered inside the video's offset/size space (clipped with it) so
        // captions follow the surface across morph/fullscreen transitions.
        // Constant font size regardless of surface size - see SubtitleStyle.
        // Hidden in floating (mini) mode: the surface is too small for them.
        if (state.subtitleText.isNotBlank() && showSubtitles) {
            val subtitleTextStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = SubtitleStyle.fontFamily,
                    fontWeight = SubtitleStyle.fontWeight,
                    fontStyle = SubtitleStyle.fontStyle,
                    fontSize = SubtitleStyle.fontSize.sp,
                    lineHeight = SubtitleStyle.lineHeight.sp,
                    textAlign = TextAlign.Center,
                )
            // Crisp glyph outline: a 3x3 ring of offset copies (no spread
            // radius in compose 1.10's Shadow, so fake it the classic way).
            val halfOutlinePx = (SubtitleStyle.outlineWidth / 2f * density.density).roundToInt()
            val outlineOffsets =
                if (halfOutlinePx > 0) {
                    buildList {
                        for (dx in intArrayOf(-halfOutlinePx, 0, halfOutlinePx)) {
                            for (dy in intArrayOf(-halfOutlinePx, 0, halfOutlinePx)) {
                                if (dx != 0 || dy != 0) add(IntOffset(dx, dy))
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            Box(
                modifier = videoModifier,
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = SubtitleStyle.bottomPadding)
                            .then(
                                if (SubtitleStyle.backdropEnabled) {
                                    Modifier
                                        .background(
                                            SubtitleStyle.backdropColor,
                                            MaterialTheme.shapes.medium,
                                        ).padding(horizontal = 8.dp)
                                        .padding(vertical = 2.dp)
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    outlineOffsets.forEach { offset ->
                        Text(
                            text = state.subtitleText,
                            style = subtitleTextStyle,
                            color = SubtitleStyle.outlineColor,
                            maxLines = 4,
                            modifier = Modifier.offset { offset },
                        )
                    }
                    Text(
                        text = state.subtitleText,
                        style = subtitleTextStyle,
                        color = SubtitleStyle.textColor,
                        maxLines = 4,
                    )
                }
            }
        }

        // ==================== 2. GestureLayer ====================
        // containerSize only changes on layout changes (rare); isDraggingMiniPlayer
        // only flips on drag start/end — both safe as direct body reads.
        PlayerGestureSystem(
            modifier = Modifier.fillMaxSize(),
            surface = surface,
            overlayMode = overlayMode,
            isLandscape = isLandscape,
            gestureConfigs = gestureConfigs,
            handler = gestureHandler,
            isScrubbing = isScrubbing,
            onTap = onTap,
            // Floating mode
            onOffsetChanged = onMiniOffsetChanged,
            onExpand = onExpand,
        )

        // ==================== 3. Details panel (LazyColumn) ====================
        // Rendered on top of the gesture layer so the LazyColumn can receive scroll
        // events in the area below the video. The panel fades in/out smoothly via alpha
        // during fullscreen exit and morph transitions. Once fully faded (alpha < 0.01),
        // it is removed from composition so the LazyColumn no longer intercepts pointer
        // events, allowing the feed behind the floating mini player to be scrolled.
        //
        // The video height (offset), cascade alpha, and slide are per-frame values —
        // they are read inside modifier lambdas so the LazyColumn subtree below
        // never recomposes while they animate. The nested scroll connection is always
        // attached; it self-gates to NORMAL mode at scroll time (see PlayerView).
        if (detailsVisible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset {
                            // roundToInt to match the video surface's exact
                            // pixel height — floor/round mix left a 1px seam
                            // depending on the fractional height.
                            IntOffset(0, surface.videoLayout(isLandscape, density).heightPx.roundToInt())
                        }
                        .fillMaxHeight()
                        .graphicsLayer {
                            val a =
                                surface.detailsAlphaNow(isLandscape) * detailsSettle.value
                            val ty = surface.detailsTranslateYNow()
                            alpha = a
                            translationY = ty
                            // The details panel is the largest element on
                            // screen (≈3/4 of the screen in portrait, 30% in
                            // landscape). A graphicsLayer is NOT rasterized by
                            // default: while its alpha/translation animate, the
                            // whole details draw list is re-issued every frame
                            // — 3.4× more draw work in portrait, which is what
                            // made portrait morphs drop frames while landscape
                            // survived the identical animation. Rasterize only
                            // mid-animation: the per-frame cost becomes a cheap
                            // bitmap composite; settled (alpha 0/1, no slide)
                            // runs the normal on-screen path with no cache.
                            if (a in 0.01f..0.99f || ty != 0f) {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                        }.nestedScroll(nestedScrollConnection),
            ) {
                PlayerDetails(
                    state = state,
                    scrollState = scrollState,
                    expandedDescription = expandedDescription,
                    onToggleDescription = onToggleDescription,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    onRecommendedClick = onRecommendedClick,
                    gridColumns = gridColumns,
                    onLoadMoreComments = onLoadMoreComments,
                    onChannelClick = onChannelClick,
                    onLike = onLike,
                    onDislike = onDislike,
                    onMore = onMore,
                    isSubscribedChannel = isSubscribedChannel,
                    onSubscribe = onSubscribe,
                    isLive = isLive,
                    liveChat = liveChat,
                    onTimestampClick = onTimestampClick,
                    onLinkClick = onLinkClick,
                    onOverdragStart = onDetailsOverdragStart,
                    onOverdrag = onDetailsOverdrag,
                    onOverdragEnd = onDetailsOverdragEnd,
                )
            }
        }

        // ==================== 4. ControlsLayer ====================
        PlayerControls(
            modifier = Modifier.fillMaxSize(),
            surface = surface,
            isLandscape = isLandscape,
            controlsVisible = controlsVisible,
            isLoading = isLoading,
            activeProgressIndicator = activeProgressIndicator,
            badgeState = badgeState,
            onBadgeSessionEnded = onBadgeSessionEnded,
            state = state,
            positionMs = positionMs,
            subtitlesOn = subtitlesOn,
            isScrubbing = isScrubbing,
            scrubPositionMs = scrubPositionMs,
            onSubtitleToggle = onSubtitleToggle,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onChapters = onChapters,
            onFullscreenToggle = onFullscreenToggle,
            onMinimize = onMinimize,
            onClose = onClose,
            onWatchLater = onWatchLater,
            isWatchLater = isWatchLater,
            loopMode = loopMode,
            onLoopMode = onLoopMode,
            onQueue = onQueue,
            onOptions = onOptions,
            onCast = onCast,
            onSeek = onSeek,
            onScrubFinished = onScrubFinished,
        )
    }
}
