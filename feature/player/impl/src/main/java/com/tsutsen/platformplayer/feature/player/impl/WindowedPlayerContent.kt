package com.tsutsen.platformplayer.feature.player.impl

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

/**
 * Windowed mode content: just the scrollable details LazyColumn.
 *
 * The video box, controls, and gesture layer are all handled by PlayerMorphBox
 * and the gesture layer in PlayerScreen.kt. This composable only renders the
 * details below the video box (title, channel, stats, description, tabs, etc.).
 *
 * In NORMAL mode, this is visible below the video box.
 * In COMPACT mode, this is partially visible (video box is scrolled up).
 * In FLOATING mode, this fades out during morph (handled by PlayerMorphBox alpha).
 */
@Composable
fun WindowedPlayerContent(
    player: ExoPlayer?,
    state: PlayerUiState.Loaded,
    scrollState: LazyListState,
    expandedDescription: Boolean,
    onToggleDescription: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLoadMoreComments: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ==================== Scrollable video details ====================
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
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
    }
}
