package com.tsutsen.platformplayer.feature.player.impl.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalViewConfiguration
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
 * Separated into:
 * 1. [GestureLayer] - handles all gestures for the current mode
 * 2. [ControlsLayer] - handles all controls for the current mode
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
    controlsVisible: Boolean,
    showTopOverlay: Boolean,
    showBottomOverlay: Boolean,
    scrollState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    gestureCallbacks: PlayerGestureCallbacks,
    // Mini drag state
    isDraggingMiniPlayer: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    currentOffsetX: Float,
    currentOffsetY: Float,
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
    // Morph drag callbacks (for interactive morph)
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

        // ==================== 3. GestureLayer ====================
        // Handles all gestures for the current mode.
        GestureLayer(
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
            isCollapsedControls = isCollapsedControls
        )

        // ==================== 4. ControlsLayer ====================
        // Handles all controls for the current mode.
        ControlsLayer(
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

// ==================== GestureLayer ====================

/**
 * Handles all gestures for the current mode.
 * - NORMAL: swipe down on player → mini, swipe up on details (when at top) → fullscreen, double-tap left/right thirds → rewind ±5s
 * - COMPACT: swipe down on player → mini
 * - FULLSCREEN: swipe left/right for brightness/volume, double-tap left/right thirds → rewind ±5s
 * - FLOATING: drag-to-reposition with edge snapping
 */
@Composable
fun GestureLayer(
    modifier: Modifier,
    videoLayout: VideoLayout,
    miniProgress: Float,
    fullscreenProgress: Float,
    containerWidth: Float,
    containerHeight: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    floatingRestX: Float,
    floatingRestY: Float,
    currentOffsetX: Float,
    currentOffsetY: Float,
    isDraggingMiniPlayer: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    gestureCallbacks: PlayerGestureCallbacks,
    onExpand: () -> Unit,
    onSeek: (Long) -> Unit,
    isCollapsedControls: Boolean
) {
    val density = LocalDensity.current

    Box(modifier = modifier) {
        // ==================== FULLSCREEN mode gestures (full screen) ====================
        if (fullscreenProgress > FULLSCREEN_SETTLED_THRESHOLD) {
            // Brightness/volume swipe
            var touchX = 0f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                touchX = it.x
                                gestureCallbacks.onVerticalDragStart(it.x)
                            },
                            onVerticalDrag = { _, dragAmount ->
                                gestureCallbacks.onVerticalDrag(touchX, dragAmount, containerWidth)
                            }
                        )
                    }
            )

            // Double-tap left/right thirds → rewind ±5 seconds
            val thirdWidthDp = with(density) { (containerWidth / 3).toDp() }
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left third
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(thirdWidthDp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onSeek(-5000)
                                }
                            )
                        }
                )
                // Right third
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(thirdWidthDp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onSeek(5000)
                                }
                            )
                        }
                )
            }
        }

        // ==================== FLOATING mode gestures ====================
        if (miniProgress > MINI_DRAG_THRESHOLD) {
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
                    .pointerInput(Unit) {
                        var localOffsetX = currentOffsetX
                        var localOffsetY = currentOffsetY

                        detectTapGestures(
                            onTap = {
                                Log.d(TAG, "Mini tap → expand")
                                onExpand()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var localOffsetX = currentOffsetX
                        var localOffsetY = currentOffsetY

                        detectDragGestures(
                            onDragStart = {
                                Log.d(TAG, "Mini drag start")
                                onDragStateChanged(true)
                                localOffsetX = currentOffsetX
                                localOffsetY = currentOffsetY
                            },
                            onDrag = { change, dragAmount: Offset ->
                                change.consume()
                                localOffsetX += dragAmount.x
                                localOffsetY += dragAmount.y
                                onOffsetChanged(localOffsetX, localOffsetY)
                            },
                            onDragEnd = {
                                Log.d(TAG, "Mini drag end")
                                onDragStateChanged(false)
                                // Snap to nearest edge
                                val edgeThreshold = 100f
                                val paddingPx = 16.dp.toPx()
                                val initialX = containerWidth - miniWidthPx - paddingPx
                                val initialY = containerHeight - miniHeightPx - paddingPx
                                val actualX = initialX + localOffsetX
                                val actualY = initialY + localOffsetY

                                var snappedX = localOffsetX
                                if (actualX < edgeThreshold) {
                                    snappedX = -initialX
                                } else if (actualX > containerWidth - miniWidthPx - edgeThreshold) {
                                    snappedX = 0f
                                }

                                var snappedY = localOffsetY
                                if (actualY < edgeThreshold) {
                                    snappedY = -initialY
                                } else if (actualY > containerHeight - miniHeightPx - edgeThreshold) {
                                    snappedY = 0f
                                }

                                onOffsetChanged(snappedX, snappedY)
                            },
                            onDragCancel = {
                                Log.d(TAG, "Mini drag cancel")
                                onDragStateChanged(false)
                            }
                        )
                    }
            )
        }
    }
}

// ==================== ControlsLayer ====================

/**
 * Handles all controls for the current mode.
 * - NORMAL: top/bottom overlay
 * - COMPACT: compactcontrolsrow
 * - FULLSCREEN: top/bottom overlay
 * - FLOATING: mini controls
 */
@Composable
fun ControlsLayer(
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

    Box(modifier = modifier) {
        // ==================== NORMAL/COMPACT/FULLSCREEN controls ====================
        // Only render when not in mini mode
        if (miniProgress <= MINI_DRAG_THRESHOLD) {
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
                    )
                    .pointerInput(Unit) {
                        val touchSlop = 12f // default touch slop in px
                        var lastTapTime = 0L
                        var lastTapX = 0f
                        var lastTapY = 0f
                        val doubleTapTimeoutMs = 300L

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDragY = 0f
                            var pastSlop = false
                            var pointerId = down.id
                            var isDownward = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: break

                                // Check if this pointer just went up (pressed changed from true to false)
                                if (change.previousPressed && !change.pressed) {
                                    if (!pastSlop) {
                                        // Short press — treat as tap / double-tap
                                        val now = System.currentTimeMillis()
                                        val dx = change.previousPosition.x - lastTapX
                                        val dy = change.previousPosition.y - lastTapY
                                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                                        if (now - lastTapTime < doubleTapTimeoutMs && dist < touchSlop) {
                                            // Double-tap: seek ±5s based on x position
                                            val videoWidth = videoLayout.widthPx
                                            val third = videoWidth / 3f
                                            if (change.position.x < third) {
                                                onSeek(-5000)
                                            } else if (change.position.x > videoWidth - third) {
                                                onSeek(5000)
                                            }
                                        } else {
                                            // Single tap
                                            gestureCallbacks.onTap()
                                        }
                                        lastTapTime = now
                                        lastTapX = change.position.x
                                        lastTapY = change.position.y
                                    } else {
                                        // Drag ended — decide commit
                                        onMorphDragEnd(totalDragY)
                                    }
                                    break
                                }

                                val dy = change.position.y - change.previousPosition.y
                                if (!pastSlop) {
                                    totalDragY += dy
                                    if (kotlin.math.abs(totalDragY) > touchSlop) {
                                        pastSlop = true
                                        if (totalDragY > 0f) {
                                            // Downward intent — commit to morph
                                            isDownward = true
                                            onMorphDragStart()
                                            change.consume()
                                            onMorphDrag(totalDragY)
                                        } else {
                                            // Upward — don't steal; let nested scroll handle
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
                showTopBar = resolvedShowTopBar,
                showBottomBar = resolvedShowBottomBar,
                callbacks = PlayerGestureCallbacks(
                    onTap = { /* handled by GestureLayer */ },
                    onDoubleTap = { /* handled by GestureLayer */ },
                    onVerticalDragStart = { /* handled by GestureLayer */ },
                    onVerticalDrag = { _, _, _ -> /* handled by GestureLayer */ }
                ),
                disableVerticalDragGestures = true,
                disableTapGestures = true,
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
                    // §4.3: layer NORMAL bottom, COMPACT row, and FULLSCREEN bottom
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

        // ==================== FLOATING mode controls ====================
        if (miniProgress > MINI_DRAG_THRESHOLD) {
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
                // Mini player background scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                )

                // Mini player controls
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top row: Play/Pause + Close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom row: Title + author + More options
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tap title area to expand (gesture layer also handles tap)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                Log.d(TAG, "Mini title tap → expand")
                                                onExpand()
                                            }
                                        )
                                    }
                            ) {
                                Text(
                                    text = state.currentVideo?.title ?: "Unknown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val authorName = state.currentVideo?.author?.name
                                if (!authorName.isNullOrEmpty()) {
                                    Text(
                                        text = authorName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onMoreOptions,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = if (state.durationMs > 0) {
                                    state.currentPositionMs.toFloat() / state.durationMs
                                } else 0f,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }
    }
}
