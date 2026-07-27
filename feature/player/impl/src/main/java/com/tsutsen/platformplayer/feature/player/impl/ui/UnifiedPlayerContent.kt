package com.tsutsen.platformplayer.feature.player.impl.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.tsutsen.platformplayer.feature.player.impl.BottomOverlay
import com.tsutsen.platformplayer.feature.player.impl.ChannelRow
import com.tsutsen.platformplayer.feature.player.impl.CommentCard
import com.tsutsen.platformplayer.feature.player.impl.CompactControlsRow
import com.tsutsen.platformplayer.feature.player.impl.DescriptionSection
import com.tsutsen.platformplayer.feature.player.impl.PlayerControlsScaffold
import com.tsutsen.platformplayer.feature.player.impl.PlayerGestureCallbacks
import com.tsutsen.platformplayer.feature.player.impl.PlayerUiState
import com.tsutsen.platformplayer.feature.player.impl.PlayerVideoSurface
import com.tsutsen.platformplayer.feature.player.impl.RecommendedSection
import com.tsutsen.platformplayer.feature.player.impl.TabsSection
import com.tsutsen.platformplayer.feature.player.impl.TopOverlay
import com.tsutsen.platformplayer.feature.player.impl.VideoStatsRow
import com.tsutsen.platformplayer.feature.player.impl.formatRelativeTime

private const val TAG = "UnifiedPlayerContent"

/**
 * Threshold above which the mini gesture layer (drag-to-reposition) becomes active.
 * Below this, the mini player responds to tap-to-expand only.
 */
private const val MINI_DRAG_THRESHOLD = 0.98f

/**
 * Threshold below which the mini player is considered "settled" at NORMAL.
 * Above this, the mini player is considered "settled" at FLOATING.
 */
private const val MINI_SETTLED_THRESHOLD = 0.01f

/**
 * Threshold below which the fullscreen player is considered "settled" at NORMAL.
 * Above this, fullscreen is considered "settled" at FULLSCREEN.
 */
private const val FULLSCREEN_SETTLED_THRESHOLD = 0.01f

/**
 * Unified player content composable that replaces the four mode-specific composables
 * (WindowedPlayerContent, FloatingPlayerContent, FullscreenPlayerContent, and the old
 * PlayerControlsScaffold-based layout) with a single persistent layout tree. The [PlayerVideoSurface] never leaves composition across mode
 * changes — it is positioned and sized via the [videoLayout] geometry computed by the
 * caller.
 *
 * This composable handles:
 * - NORMAL / COMPACT: video box + scrollable details + scaffold controls
 * - FLOATING: mini player with drag/tap/expand gestures + bespoke controls
 * - FULLSCREEN: video fills container + scaffold controls (full-size)
 * - All transitions between the above via continuous geometry interpolation
 *
 * Two separate gesture subtrees coexist:
 * 1. [PlayerControlsScaffold] for NORMAL/COMPACT/FULLSCREEN (brightness/volume swipe, tap)
 * 2. Bespoke mini gesture Box for FLOATING (drag-to-reposition, tap-to-expand)
 *
 * Pointer input on each is conditionally attached based on the animation progresses,
 * not just alpha-gated (zero-alpha content still intercepts touches in Compose).
 */
@Composable
fun UnifiedPlayerContent(
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
    showTopOverlay: Boolean,
    showBottomOverlay: Boolean,
    scrollState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    gestureCallbacks: PlayerGestureCallbacks,
    // Mini drag state
    isDraggingMiniPlayer: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    // Modal callbacks (modal rendering stays in PlayerScreen)
    onOptions: () -> Unit,
    onChapters: () -> Unit,
    // Detail state
    expandedDescription: Boolean,
    onToggleDescription: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLoadMoreComments: () -> Unit,
    // Playback state
    isLoading: Boolean,
    brightnessValue: Float,
    volumeValue: Float,
    showBrightnessIndicator: Boolean,
    showVolumeIndicator: Boolean,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    isLooping: Boolean,
    onLoopToggle: () -> Unit,
    // Callbacks
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onMoreOptions: () -> Unit,
    onFullscreenToggle: () -> Unit
) {
    val density = LocalDensity.current

    // ==================== Derived alpha weights for control cross-fade ====================
    // §4.3: weighted alpha cross-fade, not threshold-based AnimatedContent
    val normalBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) *
        (if (isCollapsedControls) 0f else 1f)
    val compactBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) *
        (if (isCollapsedControls) 1f else 0f)
    val miniControlsAlpha = miniProgress
    val fullscreenBarAlpha = fullscreenProgress * (1f - miniProgress)

    // ==================== Details panel fade/translate ====================
    // §4.2: stays in composition, fades + translates during mini/fullscreen
    val detailsAlpha = (1f - miniProgress) * (1f - fullscreenProgress)
    val detailsTranslateY = lerp(
        0f,
        containerHeight * 0.3f,
        maxOf(miniProgress, fullscreenProgress)
    )

    // ==================== Scaffold bar visibility (§4.7) ====================
    // Force bars visible during active transitions to avoid flicker from two
    // independent animation systems fighting near thresholds.
    val isTransitioning =
        (miniProgress > MINI_SETTLED_THRESHOLD && miniProgress < (1f - MINI_SETTLED_THRESHOLD)) ||
            (fullscreenProgress > FULLSCREEN_SETTLED_THRESHOLD &&
                fullscreenProgress < (1f - FULLSCREEN_SETTLED_THRESHOLD))

    val resolvedShowTopBar = if (isTransitioning) {
        true
    } else {
        when {
            miniProgress > (1f - MINI_SETTLED_THRESHOLD) -> true // mini keeps its bars
            fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> showTopOverlay
            else -> showTopOverlay && !isCollapsedControls // NORMAL/COMPACT
        }
    }

    val resolvedShowBottomBar = if (isTransitioning) {
        true
    } else {
        when {
            miniProgress > (1f - MINI_SETTLED_THRESHOLD) -> true
            fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> showBottomOverlay
            else -> if (isCollapsedControls) true else showBottomOverlay
        }
    }

    // ==================== Scaffold vertical drag gating (§4.6) ====================
    // When mini drag is active, the scaffold's vertical drag would conflict.
    // When fullscreen is active, vertical drag is needed for brightness/volume.
    val disableVerticalDrag = when {
        miniProgress > MINI_DRAG_THRESHOLD -> true
        fullscreenProgress > (1f - FULLSCREEN_SETTLED_THRESHOLD) -> false
        else -> true // NORMAL/COMPACT
    }

    // ==================== Nested scroll connection (§4.2) ====================
    // Only active when settled in NORMAL territory; otherwise it fights the morph.
    val nestedScrollModifier = remember(nestedScrollConnection, miniProgress, fullscreenProgress) {
        if (miniProgress < MINI_SETTLED_THRESHOLD && fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD) {
            Modifier.nestedScroll(nestedScrollConnection)
        } else {
            Modifier
        }
    }

    // ==================== Shared video modifier ====================
    // §4.8: pass offset/size/graphicsLayer directly to PlayerVideoSurface — no extra Box.
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
                shape = RoundedCornerShape(videoLayout.cornerRadius)
                clip = true
            }
    }

    Log.d(TAG, "UnifiedPlayerContent compose: miniProgress=$miniProgress, fullscreenProgress=$fullscreenProgress, " +
        "layout=(${videoLayout.widthPx.toInt()}x${videoLayout.heightPx.toInt()} @ ${videoLayout.offsetX.toInt()},${videoLayout.offsetY.toInt()})")

    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 1. Persistent video surface ====================
        // Never leaves composition across mode changes — positioned via geometry.
        PlayerVideoSurface(player = player, modifier = Modifier.then(videoModifier))

        // ==================== 2. Details panel (LazyColumn) ====================
        // Stays in composition, fades + translates. Starts below video, fills remaining height.
        val detailsOffsetY = with(density) { videoLayout.heightPx.toDp() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = detailsOffsetY)
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = detailsAlpha
                    translationY = detailsTranslateY
                }
                .then(nestedScrollModifier)
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .clipToBounds()
            ) {
                item {
                    Text(
                        text = state.currentVideo?.title ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    ChannelRow(
                        author = state.currentVideo?.author,
                        onSubscribe = { /* TODO */ },
                        onWatchLater = { /* TODO */ },
                        onShare = { /* TODO */ },
                        onMore = { /* TODO */ }
                    )
                }
                item {
                    VideoStatsRow(
                        viewCount = state.currentVideo?.viewCount ?: 0,
                        publishedAt = state.currentVideo?.publishedAt,
                        likeCount = state.currentVideo?.likeCount,
                        dislikeCount = state.currentVideo?.dislikeCount
                    )
                }
                item {
                    DescriptionSection(
                        description = state.currentVideo?.description ?: "",
                        isExpanded = expandedDescription,
                        onToggle = onToggleDescription
                    )
                }
                item {
                    TabsSection(selectedTab = selectedTab, onTabSelected = onTabSelected)
                }
                when (selectedTab) {
                    0 -> {
                        itemsIndexed(state.comments) { index, comment ->
                            CommentCard(
                                username = comment.author,
                                timeAgo = formatRelativeTime(comment.publishedAtMs),
                                text = comment.text,
                                likeCount = comment.likeCount.toInt()
                            )
                            if (index < state.comments.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = onLoadMoreComments) {
                                    Text("Load more comments")
                                }
                            }
                        }
                    }
                    1 -> {
                        item { RecommendedSection() }
                    }
                }
            }
        }

        // ==================== 3. Controls scaffold (NORMAL/COMPACT/FULLSCREEN) ====================
        // Scaffold is positioned at the video location, constrained to video height.
        // For mini mode, it's positioned at (videoLayout.offsetX, videoLayout.offsetY)
        // with size (videoLayout.widthPx, videoLayout.heightPx).
        PlayerControlsScaffold(
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
                ),
            isLoading = isLoading,
            brightnessValue = brightnessValue,
            volumeValue = volumeValue,
            showBrightnessIndicator = showBrightnessIndicator,
            showVolumeIndicator = showVolumeIndicator,
            showTopBar = resolvedShowTopBar,
            showBottomBar = resolvedShowBottomBar,
            callbacks = gestureCallbacks,
            disableVerticalDragGestures = disableVerticalDrag,
            topBar = {
                // §4.3: weighted alpha cross-fade for top bar content
                val topAlpha = maxOf(normalBarAlpha, fullscreenBarAlpha)
                if (topAlpha > 0.01f) {
                    Box(modifier = Modifier.alpha(topAlpha)) {
                        TopOverlay(
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
                // §4.3: layer NORMAL bottom, COMPACT row, and FULLSCREEN bottom with各自的 alpha
                Box(modifier = Modifier.fillMaxWidth()) {
                    // NORMAL bottom overlay
                    if (normalBarAlpha > 0.01f && !isCollapsedControls) {
                        Box(modifier = Modifier.alpha(normalBarAlpha)) {
                            BottomOverlay(
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
                    // COMPACT control row
                    if (compactBarAlpha > 0.01f) {
                        Box(modifier = Modifier.alpha(compactBarAlpha)) {
                            CompactControlsRow(
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
                        }
                    }
                    // FULLSCREEN bottom overlay
                    if (fullscreenBarAlpha > 0.01f) {
                        Box(modifier = Modifier.alpha(fullscreenBarAlpha)) {
                            BottomOverlay(
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
        )

    }
}
