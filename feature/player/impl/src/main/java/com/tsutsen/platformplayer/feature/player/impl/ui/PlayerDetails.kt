package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsutsen.platformplayer.core.model.VideoCard

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
) {
    LazyColumn(
        state = scrollState,
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        item {
            Text(
                text = state.currentVideo?.title ?: "Unknown",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            ChannelRow(
                author = state.currentVideo?.author,
                onSubscribe = { /* TODO */ },
                onWatchLater = { /* TODO */ },
                onShare = { /* TODO */ },
                onMore = { /* TODO */ },
                onChannelClick = onChannelClick,
            )
        }
        item {
            VideoStatsRow(
                viewCount = state.currentVideo?.viewCount ?: 0,
                publishedAt = state.currentVideo?.publishedAt,
                likeCount = state.currentVideo?.likeCount,
                dislikeCount = state.currentVideo?.dislikeCount,
            )
        }
        item {
            DescriptionSection(
                description = state.currentVideo?.description ?: "",
                isExpanded = expandedDescription,
                onToggle = onToggleDescription,
            )
        }
        val visibleTabs =
            listOfNotNull(
                0.takeIf { state.showComments },
                1.takeIf { state.showRecommended },
            )
        if (visibleTabs.isEmpty()) {
            return@LazyColumn
        }
        val effectiveTab =
            if (selectedTab in visibleTabs) selectedTab else visibleTabs.first()
        item {
            TabsSection(
                showComments = state.showComments,
                showRecommended = state.showRecommended,
                selectedTab = effectiveTab,
                onTabSelected = onTabSelected,
            )
        }
        when (effectiveTab) {
            0 -> {
                itemsIndexed(state.comments) { index, comment ->
                    CommentCard(
                        username = comment.author,
                        timeAgo = formatRelativeTime(comment.publishedAtMs),
                        text = comment.text,
                        likeCount = comment.likeCount.toInt(),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (index < state.comments.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
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
                    items(
                        recommendations.chunked(gridColumns),
                        key = { row -> row.first().id },
                    ) { rowCards ->
                        RecommendedGridRow(
                            cards = rowCards,
                            onClick = onRecommendedClick,
                        )
                    }
                }
            }
        }
    }
}
