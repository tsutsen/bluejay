package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

/**
 * NORMAL and COMPACT modes. These are the same video box + scrollable detail page - COMPACT
 * is just what NORMAL looks like once the user has scrolled the video box down to <=30% of
 * the container height (see `isCollapsedControls` in PlayerScreen.kt). The only thing that
 * changes is which control row [PlayerControlsScaffold] draws: the full top/bottom overlay
 * (title, timeline, playback buttons) in NORMAL, vs. just [CompactControlsRow] in COMPACT.
 *
 * If you're looking for where COMPACT "starts", it's the `isCollapsedControls` condition
 * passed in from the caller - there's no separate composable for it because there's nothing
 * structurally separate to render; only the control row and top-bar visibility differ.
 */
@Composable
fun WindowedPlayerContent(
    modifier: Modifier,
    player: ExoPlayer?,
    state: PlayerUiState.Loaded,
    playerHeightPx: Float,
    scrollState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    isCollapsedControls: Boolean,
    expandedDescription: Boolean,
    onToggleDescription: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLoadMoreComments: () -> Unit,
    // Shared scaffold state
    isLoading: Boolean,
    brightnessValue: Float,
    volumeValue: Float,
    showBrightnessIndicator: Boolean,
    showVolumeIndicator: Boolean,
    showTopOverlay: Boolean,
    showBottomOverlay: Boolean,
    gestureCallbacks: PlayerGestureCallbacks,
    // NORMAL top/bottom bar callbacks
    onMinimize: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onOptions: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    isScrubbing: Boolean,
    scrubPositionMs: Long,
    // COMPACT-only
    isLooping: Boolean,
    onLoopToggle: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // ==================== Video box (height driven by scroll, shared by NORMAL/COMPACT) ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { playerHeightPx.toDp() })
                .background(Color.Black)
                .clipToBounds()
        ) {
            PlayerVideoSurface(player = player)
        }

        // ==================== Scrollable video details ====================
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .nestedScroll(nestedScrollConnection)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            item {
                Text(
                    text = state.currentVideo?.title ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
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
        } // end Column

        // ==================== Controls scaffold: NORMAL bars, or COMPACT row ====================
        PlayerControlsScaffold(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(with(LocalDensity.current) { playerHeightPx.toDp() })
            .clipToBounds(),
        isLoading = isLoading,
        brightnessValue = brightnessValue,
        volumeValue = volumeValue,
        showBrightnessIndicator = showBrightnessIndicator,
        showVolumeIndicator = showVolumeIndicator,
        showTopBar = showTopOverlay && !isCollapsedControls,
        showBottomBar = if (isCollapsedControls) true else showBottomOverlay,
        callbacks = gestureCallbacks,
        disableVerticalDragGestures = true,
        topBar = {
            TopOverlay(
                title = state.currentVideo?.title ?: "Unknown",
                channelName = state.currentVideo?.author?.name ?: "Unknown",
                onMinimize = onMinimize,
                onReplayToggle = onReplayToggle,
                onWatchLater = onWatchLater,
                onOptions = onOptions
            )
        },
        bottomBar = {
            AnimatedContent(
                targetState = isCollapsedControls,
                transitionSpec = {
                    (slideInVertically(initialOffsetY = { it }) + fadeIn()).togetherWith(
                        slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    )
                }
            ) { collapsed ->
                if (collapsed) {
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
                } else {
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
        )
    } // end Box
}
