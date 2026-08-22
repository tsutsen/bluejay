package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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

private const val TAG = "PlayerContent"

const val MINI_DRAG_THRESHOLD = 0.98f
const val MINI_SETTLED_THRESHOLD = 0.01f
const val MORPH_TRANSITION_START = 0.3f // When morph transition begins
const val MORPH_TRANSITION_END = 0.7f // When morph transition completes
const val DETAILS_FADE_START = 0.1f // Details start fading out earlier
const val DETAILS_FADE_END = 0.4f // Details fully faded before controls complete
const val FULLSCREEN_SETTLED_THRESHOLD = 0.01f

/**
 * Unified player content composable. Computes derived alpha weights, resolved visibility,
 * and video geometry, then delegates to GestureLayer, ControlsLayer, and DetailsPanel.
 *
 * The [PlayerVideoSurface] never leaves composition across mode changes — it is positioned
 * and sized via the [videoLayout] geometry computed by the caller.
 */
@Composable
fun PlayerContent(
    player: ExoPlayer?,
    state: PlayerUiState.Loaded,
    positionMs: StateFlow<Long>,
    videoLayout: VideoLayout,
    miniProgress: Float,
    fullscreenProgress: Float,
    containerWidth: Float,
    containerHeight: Float,
    playerHeightPx: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    floatingRestX: Float,
    floatingRestY: Float,
    isCollapsedControls: Boolean,
    controlsVisible: Boolean,
    showTopOverlay: Boolean,
    showBottomOverlay: Boolean,
    scrollState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    overlayMode: com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode,
    gestureConfigs: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureConfigs,
    gestureHandler: com.tsutsen.platformplayer.feature.player.impl.gesture.GestureActionHandler,
    isScrubbing: Boolean,
    isMorphDragging: Boolean,
    isDraggingMiniPlayer: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    currentOffsetX: Float,
    currentOffsetY: Float,
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
    onFullscreen: () -> Unit,
    onExpand: () -> Unit,
    onMorphDragStart: () -> Unit,
    onMorphDrag: (dragY: Float) -> Unit,
    onMorphDragEnd: (dragY: Float) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    loopMode: Int,
    onLoopMode: () -> Unit,
    onWatchLater: () -> Unit,
    isWatchLater: Boolean = false,
    onQueue: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubFinished: () -> Unit = {},
    onMoreOptions: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onDetailsOverdrag: () -> Unit = {},
) {
    val density = LocalDensity.current

    // ==================== Derived alpha weights for control cross-fade ====================
    val normalBarAlpha =
        (1f - miniProgress) * (1f - fullscreenProgress) *
            (if (isCollapsedControls) 0f else 1f)
    val compactBarAlpha =
        (1f - miniProgress) * (1f - fullscreenProgress) *
            (if (isCollapsedControls) 1f else 0f)
    val miniControlsAlpha = miniProgress
    val fullscreenBarAlpha = fullscreenProgress * (1f - miniProgress)

    // ==================== Details panel fade/translate ====================
    val detailsAlpha = (1f - miniProgress) * (1f - fullscreenProgress)
    val detailsTranslateY =
        lerp(
            0f,
            containerHeight * 0.3f,
            maxOf(miniProgress, fullscreenProgress),
        )

    // ==================== Scaffold bar visibility ====================
    val miniMorphAlpha = (1f - miniProgress).coerceIn(0f, 1f)

    val resolvedShowTopBar =
        when {
            // NOTE: must mirror the `else` (idle) branch's `!isCollapsedControls` check.
            // Without it, starting a drag while controls were collapsed flips this from
            // false -> true on the very first pixel of drag movement, while gradientAlpha
            // (normalAlpha) is still ~1.0 (it doesn't start fading until MORPH_TRANSITION_START),
            // so the top scrim pops in at full opacity instead of fading in with everything else.
            //
            // Deliberately NOT gated on showTopOverlay (raw controlsVisible) here: that's an
            // un-animated boolean, so gating composition on it directly cuts the top bar (and its
            // fade+slide graphicsLayer) out of composition the instant controls should hide, before
            // gradientAlpha's 200ms tween gets a single frame to render. Hiding due to
            // controlsVisible is handled entirely by the animated gradientAlpha downstream in
            // PlayerControls.kt / PlayerUIScaffold.kt - this only decides structural applicability.
            miniProgress > MINI_SETTLED_THRESHOLD -> {
                miniMorphAlpha > 0.01f && !isCollapsedControls &&
                    (
                        fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD ||
                            fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD)
                    )
            }

            fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> {
                true
            }

            else -> {
                !isCollapsedControls
            }
        }

    val resolvedShowBottomBar =
        when {
            miniProgress > MINI_SETTLED_THRESHOLD -> {
                miniMorphAlpha > 0.01f &&
                    (
                        fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD ||
                            fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD)
                    )
            }

            fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> {
                true
            }

            else -> {
                true
            }
        }

    // ==================== Nested scroll connection ====================
    val nestedScrollModifier =
        remember(nestedScrollConnection, miniProgress, fullscreenProgress) {
            if (miniProgress < MINI_SETTLED_THRESHOLD && fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD) {
                Modifier.nestedScroll(nestedScrollConnection)
            } else {
                Modifier
            }
        }

    // ==================== Shared video modifier ====================
    val videoModifier =
        remember(videoLayout) {
            Modifier
                .offset {
                    IntOffset(
                        x = videoLayout.offsetX.toInt(),
                        y = videoLayout.offsetY.toInt(),
                    )
                }.size(
                    width = with(density) { videoLayout.widthPx.toDp() },
                    height = with(density) { videoLayout.heightPx.toDp() },
                ).graphicsLayer {
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(videoLayout.cornerRadius)
                    clip = true
                }
        }

    Log.d(
        TAG,
        "PlayerContent compose: miniProgress=$miniProgress, fullscreenProgress=$fullscreenProgress, " +
            "layout=(${videoLayout.widthPx.toInt()}x${videoLayout.heightPx.toInt()} @ ${videoLayout.offsetX.toInt()},${videoLayout.offsetY.toInt()})",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 1. Persistent video surface ====================
        PlayerVideoSurface(player = player, modifier = Modifier.then(videoModifier))

        // ==================== 1b. Subtitle overlay ====================
        // Rendered inside the video's offset/size space (clipped with it) so
        // captions follow the surface across morph/fullscreen transitions.
        // Constant font size regardless of surface size - see SubtitleStyle.
        // Hidden in floating (mini) mode: the surface is too small for them.
        if (state.subtitleText.isNotBlank() && miniProgress < MINI_SETTLED_THRESHOLD) {
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
            val halfOutlinePx =
                with(density) { (SubtitleStyle.outlineWidth / 2f).dp.roundToPx() }
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
        PlayerGestureSystem(
            modifier = Modifier.fillMaxSize(),
            overlayMode = overlayMode,
            gestureConfigs = gestureConfigs,
            handler = gestureHandler,
            isScrubbing = isScrubbing,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            onTap = onTap,
            onMorphDragStart = onMorphDragStart,
            onMorphDrag = onMorphDrag,
            onMorphDragEnd = onMorphDragEnd,
            // Floating mode
            floatingVideoLayout = videoLayout,
            isDraggingMiniPlayer = isDraggingMiniPlayer,
            onDragStateChanged = onDragStateChanged,
            onOffsetChanged = onOffsetChanged,
            onExpand = onExpand,
            floatingRestX = floatingRestX,
            floatingRestY = floatingRestY,
            currentOffsetX = currentOffsetX,
            currentOffsetY = currentOffsetY,
            miniWidthPx = miniWidthPx,
            miniHeightPx = miniHeightPx,
        )

        // ==================== 3. Details panel (LazyColumn) ====================
        // Rendered on top of the gesture layer so the LazyColumn can receive scroll
        // events in the area below the video. The panel fades in/out smoothly via alpha
        // during fullscreen exit and morph transitions. Once fully faded (alpha < 0.01),
        // it is removed from composition so the LazyColumn no longer intercepts pointer
        // events, allowing the feed behind the floating mini player to be scrolled.
        val detailsOffsetY = with(density) { videoLayout.heightPx.toDp() }
        // Fade out details earlier than controls for a cascading effect
        val detailsFadeAlpha =
            if (miniProgress <= DETAILS_FADE_START) {
                1f
            } else if (miniProgress >= DETAILS_FADE_END) {
                0f
            } else {
                (DETAILS_FADE_END - miniProgress) / (DETAILS_FADE_END - DETAILS_FADE_START)
            }.coerceAtLeast(0f)
        val detailsAlphaFinal = detailsAlpha * detailsFadeAlpha

        // Keep panel in composition while visible (alpha > 0.01), remove when fully faded
        if (detailsAlphaFinal > 0.01f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset(y = detailsOffsetY)
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = detailsAlphaFinal
                            translationY = detailsTranslateY
                        }.then(nestedScrollModifier),
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
                    onTimestampClick = onTimestampClick,
                    onLinkClick = onLinkClick,
                    onOverdragTop = onDetailsOverdrag,
                )
            }
        }

        // ==================== 4. ControlsLayer ====================
        PlayerControls(
            modifier = Modifier.fillMaxSize(),
            videoLayout = videoLayout,
            miniProgress = miniProgress,
            fullscreenProgress = fullscreenProgress,
            normalBarAlpha = normalBarAlpha,
            compactBarAlpha = compactBarAlpha,
            fullscreenBarAlpha = fullscreenBarAlpha,
            isCollapsedControls = isCollapsedControls,
            controlsVisible = controlsVisible,
            showTopOverlay = showTopOverlay,
            showBottomOverlay = showBottomOverlay,
            resolvedShowTopBar = resolvedShowTopBar,
            resolvedShowBottomBar = resolvedShowBottomBar,
            isLoading = isLoading,
            activeProgressIndicator = activeProgressIndicator,
            badgeState = badgeState,
            onBadgeSessionEnded = onBadgeSessionEnded,
            state = state,
            positionMs = positionMs,
            player = player,
            subtitlesOn = subtitlesOn,
            isScrubbing = isScrubbing,
            scrubPositionMs = scrubPositionMs,
            expandedDescription = expandedDescription,
            selectedTab = selectedTab,
            onToggleDescription = onToggleDescription,
            onTabSelected = onTabSelected,
            onSubtitleToggle = onSubtitleToggle,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onChapters = onChapters,
            onFullscreenToggle = onFullscreenToggle,
            onMinimize = onMinimize,
            onExpand = onExpand,
            onClose = onClose,
            onMoreOptions = onMoreOptions,
            onWatchLater = onWatchLater,
            isWatchLater = isWatchLater,
            loopMode = loopMode,
            onLoopMode = onLoopMode,
            onQueue = onQueue,
            onOptions = onOptions,
            onCast = onCast,
            onSeek = onSeek,
            onScrubFinished = onScrubFinished,
            isMorphDragging = isMorphDragging,
            onMorphDragStart = onMorphDragStart,
            onMorphDrag = onMorphDrag,
            onMorphDragEnd = onMorphDragEnd,
        )
    }
}
