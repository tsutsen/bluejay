package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.LiveChatUiState
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.feature.player.impl.ui.components.LiveChatPanel
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Scrollable details panel below the video. Contains title, channel row,
 * stats, description, tabs, and comments/recommendations.
 */
@Composable
internal fun PlayerDetails(
    state: PlayerUiState.Loaded,
    scrollState: LazyListState,
    expandedDescription: Boolean,
    onToggleDescription: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    gridColumns: Int,
    onRecommendedClick: (VideoCard) -> Unit,
    onLoadMoreComments: () -> Unit,
    onChannelClick: (String) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMore: () -> Unit,
    isSubscribedChannel: Boolean = false,
    onSubscribe: () -> Unit = {},
    isLive: Boolean = false,
    liveChat: LiveChatUiState? = null,
    onTimestampClick: (Long) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    /**
     * Overdrag morph: while the list is pinned at the top, further
     * downward drags are reported live ([onOverdrag] with the cumulative px)
     * so the parent can morph toward fullscreen. [onOverdragEnd] receives
     * the final total (0f when the drag was cancelled or scrolled away).
     */
    onOverdragStart: () -> Unit = {},
    onOverdrag: (overdragPx: Float) -> Unit = {},
    onOverdragEnd: (overdragPx: Float) -> Unit = {},
) {
    val density = LocalDensity.current
    val systemBottomInset = with(density) { WindowInsets.systemBars.getBottom(density).toDp() }
    // Match the home feed's orientation-aware layout: single column in
    // portrait, grid in landscape. No bespoke recommended-only container.
    val isWide = rememberIsWide()
    LazyColumn(
        state = scrollState,
        // Bottom padding so the last row / button can scroll clear of the
        // screen edge (and any system bars on non-fullscreen-bleed devices).
        contentPadding = PaddingValues(bottom = maxOf(Tokens.SpaceXl, systemBottomInset)),
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Overdrag-to-fullscreen: when the list is already at the top
                // and the user keeps dragging DOWN, report the accumulated
                // overscroll live so the parent can morph toward fullscreen
                // with the finger. At the top the LazyColumn has nothing to
                // scroll backward, so consuming the frames is safe.
                .pointerInput(Unit) {
                    // Overdrag-to-fullscreen: OBSERVE the pointer directly
                    // instead of using detectDragGestures — the LazyColumn's
                    // own scroll handler is attached outside this modifier and
                    // claims vertical drags first (consuming the change before
                    // our slop is exceeded), so detectDragGestures cancelled
                    // almost every pure-vertical drag. Observing (never
                    // consuming) races nothing: the list keeps scrolling and
                    // edge-effecting as usual, and we mirror the amounts while
                    // it is pinned at the top.
                    var topOverscrollPx = 0f
                    var overscrollActive = false

                    fun endOverscroll(totalPx: Float) {
                        if (!overscrollActive) return
                        overscrollActive = false
                        onOverdragEnd(totalPx)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        topOverscrollPx = 0f
                        overscrollActive = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change =
                                    event.changes.firstOrNull { it.id == down.id }
                                        ?: continue
                                if (!change.pressed) {
                                    endOverscroll(topOverscrollPx)
                                    break
                                }
                                val amount = change.position - change.previousPosition
                                when {
                                    // List scrolled away from the top: abandon.
                                    scrollState.canScrollBackward -> {
                                        topOverscrollPx = 0f
                                        endOverscroll(0f)
                                    }

                                    // Dragging down while pinned at the top:
                                    // accumulate.
                                    amount.y > 0f -> {
                                        topOverscrollPx += amount.y
                                        if (!overscrollActive) {
                                            overscrollActive = true
                                            onOverdragStart()
                                        }
                                        onOverdrag(topOverscrollPx)
                                    }

                                    // Dragging up: the list will scroll away —
                                    // abandon.
                                    amount.y < 0f -> {
                                        topOverscrollPx = 0f
                                        endOverscroll(0f)
                                    }
                                }
                            }
                        } finally {
                            // This subtree can unmount MID-gesture: past the
                            // details fade threshold (fullscreenProgress ~0.9)
                            // detailsVisible flips and drops PlayerDetails
                            // while the finger is still down. Without this,
                            // onOverdragEnd never fires, isDraggingFullscreen
                            // stays true, and the axis is stranded mid-morph
                            // with no settle path (the stuck-mid-morph state).
                            endOverscroll(topOverscrollPx)
                        }
                    }
                },
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.currentVideo?.title ?: "Unknown",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                if (isLive) {
                    LiveElapsedPill(
                        startMs = state.currentVideo?.publishedAt?.takeIf { it > 0 },
                    )
                }
            }
        }
        item {
            val video = state.currentVideo
            ChannelRow(
                author = video?.author,
                viewCount = video?.viewCount,
                publishedAt = video?.publishedAt,
                likeCount = video?.likeCount,
                isLiked = state.isLiked,
                dislikeCount = video?.dislikeCount,
                isDisliked = state.isDisliked,
                isSubscribed = isSubscribedChannel,
                onSubscribe = onSubscribe,
                onLike = onLike,
                onDislike = onDislike,
                onMore = onMore,
                onChannelClick = onChannelClick,
                sourceIconUrl = video?.sourceIconUrl,
            )
        }
        item {
            DescriptionSection(
                description = state.currentVideo?.description ?: "",
                isExpanded = expandedDescription,
                onToggle = onToggleDescription,
                onTimestampClick = onTimestampClick,
                onLinkClick = onLinkClick,
            )
        }
        val visibleTabs =
            listOfNotNull(
                0.takeIf { isLive || state.showComments },
                1.takeIf { state.showRecommended },
            )
        if (visibleTabs.isEmpty()) {
            return@LazyColumn
        }
        val effectiveTab =
            if (selectedTab in visibleTabs) selectedTab else visibleTabs.first()
        item {
            TabsSection(
                showComments = isLive || state.showComments,
                showRecommended = state.showRecommended,
                isLive = isLive,
                selectedTab = effectiveTab,
                onTabSelected = onTabSelected,
            )
        }
        when (effectiveTab) {
            0 -> {
                if (isLive) {
                    item {
                        LiveChatPanel(
                            state = liveChat,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    return@LazyColumn
                }
                if (state.comments.isEmpty()) {
                    item(key = "no-comments") {
                        Text(
                            "No comments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 32.dp),
                        )
                    }
                }
                itemsIndexed(state.comments) { index, comment ->
                    CommentCard(
                        username = comment.author,
                        timeAgo = formatRelativeTime(comment.publishedAtMs),
                        text = comment.text,
                        likeCount = comment.likeCount.toInt(),
                        authorThumbnailUrl = comment.authorThumbnailUrl,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onTimestampClick = onTimestampClick,
                        onLinkClick = onLinkClick,
                    )
                    if (index < state.comments.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                // Only once comments have actually loaded — before that the
                // button is a no-op and looked like dead UI.
                if (state.comments.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = onLoadMoreComments) {
                                Text("Load more comments")
                            }
                        }
                    }
                }
            }

            1 -> {
                val recommendations = state.recommendations.filterIsInstance<VideoCard>()
                if (recommendations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    val recColumns = if (isWide) gridColumns else 1
                    items(
                        recommendations.chunked(recColumns),
                        key = { row -> row.first().id },
                    ) { rowCards ->
                        RecommendedGridRow(
                            cards = rowCards,
                            gridColumns = recColumns,
                            onClick = onRecommendedClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Red LIVE pill; shows the stream's elapsed time ("LIVE • 1:23:45") when the
 * source reports a start time (Twitch sets it), ticking every 30 s. Plain
 * "LIVE" when the start time is unknown.
 */
@Composable
private fun LiveElapsedPill(startMs: Long?) {
    var elapsedSecs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startMs) {
        if (startMs == null) return@LaunchedEffect
        while (true) {
            elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
            delay(30_000)
        }
    }
    val label =
        if (startMs != null && elapsedSecs > 0) {
            "LIVE • ${formatLiveElapsed(elapsedSecs)}"
        } else {
            "LIVE"
        }
    Box(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(BluejayTokens().radius.xs))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onError, fontSize = 12.sp)
    }
}

/** "H:MM:SS" from an hour up, "M:SS" below. */
private fun formatLiveElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}
