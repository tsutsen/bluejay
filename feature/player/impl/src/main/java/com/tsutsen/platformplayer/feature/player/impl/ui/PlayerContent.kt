package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

private const val TAG = "PlayerContent"

const val MINI_DRAG_THRESHOLD = 0.98f
const val MINI_SETTLED_THRESHOLD = 0.01f
const val MINI_CONTROLS_FADE_START = 0.6f
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
    gestureCallbacks: PlayerGestureCallbacks,
    isDraggingMiniPlayer: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    currentOffsetX: Float,
    currentOffsetY: Float,
    onOptions: () -> Unit,
    onChapters: () -> Unit,
    expandedDescription: Boolean,
    onToggleDescription: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLoadMoreComments: () -> Unit,
    isLoading: Boolean,
    brightnessValue: Float,
    volumeValue: Float,
    showBrightnessIndicator: Boolean,
    showVolumeIndicator: Boolean,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    isLooping: Boolean,
    onLoopToggle: () -> Unit,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onExpand: () -> Unit,
    onMorphDragStart: () -> Unit,
    onMorphDrag: (dragY: Float) -> Unit,
    onMorphDragEnd: (dragY: Float) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoreOptions: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSpeedHoldStart: () -> Unit = {},
    onSpeedHoldEnd: () -> Unit = {}
) {
    val density = LocalDensity.current

    // ==================== Derived alpha weights for control cross-fade ====================
    val normalBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) *
        (if (isCollapsedControls) 0f else 1f)
    val compactBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) *
        (if (isCollapsedControls) 1f else 0f)
    val miniControlsAlpha = miniProgress
    val fullscreenBarAlpha = fullscreenProgress * (1f - miniProgress)

    // ==================== Details panel fade/translate ====================
    val detailsAlpha = (1f - miniProgress) * (1f - fullscreenProgress)
    val detailsTranslateY = lerp(
        0f,
        containerHeight * 0.3f,
        maxOf(miniProgress, fullscreenProgress)
    )

    // ==================== Scaffold bar visibility ====================
    val miniMorphAlpha = (1f - miniProgress).coerceIn(0f, 1f)

    val resolvedShowTopBar = when {
        miniProgress > MINI_SETTLED_THRESHOLD -> miniMorphAlpha > 0.01f &&
            (fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD ||
                fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD))
        fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> showTopOverlay
        else -> showTopOverlay && !isCollapsedControls
    }

    val resolvedShowBottomBar = when {
        miniProgress > MINI_SETTLED_THRESHOLD -> miniMorphAlpha > 0.01f &&
            (fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD ||
                fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD))
        fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> showBottomOverlay
        else -> if (isCollapsedControls) true else showBottomOverlay
    }

    // ==================== Nested scroll connection ====================
    val nestedScrollModifier = remember(nestedScrollConnection, miniProgress, fullscreenProgress) {
        if (miniProgress < MINI_SETTLED_THRESHOLD && fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD) {
            Modifier.nestedScroll(nestedScrollConnection)
        } else {
            Modifier
        }
    }

    // ==================== Shared video modifier ====================
    val videoModifier = remember(videoLayout) {
        Modifier
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
            .graphicsLayer {
                shape = androidx.compose.foundation.shape.RoundedCornerShape(videoLayout.cornerRadius)
                clip = true
            }
    }

    Log.d(TAG, "PlayerContent compose: miniProgress=$miniProgress, fullscreenProgress=$fullscreenProgress, " +
        "layout=(${videoLayout.widthPx.toInt()}x${videoLayout.heightPx.toInt()} @ ${videoLayout.offsetX.toInt()},${videoLayout.offsetY.toInt()})")

    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 1. Persistent video surface ====================
        PlayerVideoSurface(player = player, modifier = Modifier.then(videoModifier))

        // ==================== 2. GestureLayer ====================
        PlayerGestures(
            modifier = Modifier.fillMaxSize(),
            videoLayout = videoLayout,
            miniProgress = miniProgress,
            fullscreenProgress = fullscreenProgress,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            miniWidthPx = miniWidthPx,
            miniHeightPx = miniHeightPx,
            floatingRestX = floatingRestX,
            floatingRestY = floatingRestY,
            currentOffsetX = currentOffsetX,
            currentOffsetY = currentOffsetY,
            isDraggingMiniPlayer = isDraggingMiniPlayer,
            onDragStateChanged = onDragStateChanged,
            onOffsetChanged = onOffsetChanged,
            gestureCallbacks = gestureCallbacks,
            onExpand = onExpand,
            onSeek = onSeek,
            isCollapsedControls = isCollapsedControls,
            onSpeedHoldStart = onSpeedHoldStart,
            onSpeedHoldEnd = onSpeedHoldEnd
        )

        // ==================== 3. Details panel (LazyColumn) ====================
        // Rendered on top of the gesture layer so the LazyColumn can receive scroll
        // events in the area below the video. Fades out smoothly during morph.
        if (fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD) {
            val detailsOffsetY = with(density) { videoLayout.heightPx.toDp() }
            // Fade out details during morph: alpha goes from 1 to 0 as miniProgress goes 0 to 1
            val detailsFadeAlpha = (1f - miniProgress).coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = detailsOffsetY)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = detailsAlpha * detailsFadeAlpha
                        translationY = detailsTranslateY
                    }
                    .then(nestedScrollModifier)
            ) {
                PlayerDetails(
                    state = state,
                    scrollState = scrollState,
                    expandedDescription = expandedDescription,
                    onToggleDescription = onToggleDescription,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    onLoadMoreComments = onLoadMoreComments
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
            brightnessValue = brightnessValue,
            volumeValue = volumeValue,
            showBrightnessIndicator = showBrightnessIndicator,
            showVolumeIndicator = showVolumeIndicator,
            state = state,
            player = player,
            isLooping = isLooping,
            isScrubbing = isScrubbing,
            scrubPositionMs = scrubPositionMs,
            expandedDescription = expandedDescription,
            selectedTab = selectedTab,
            onToggleDescription = onToggleDescription,
            onTabSelected = onTabSelected,
            onLoopToggle = onLoopToggle,
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
            onReplayToggle = onReplayToggle,
            onOptions = onOptions,
            onSeek = onSeek,
            gestureCallbacks = gestureCallbacks,
            onMorphDragStart = onMorphDragStart,
            onMorphDrag = onMorphDrag,
            onMorphDragEnd = onMorphDragEnd
        )
    }
}
