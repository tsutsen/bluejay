package com.tsutsen.platformplayer.compose.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.model.VideoCard

/**
 * Feed screen composable.
 * Displays a list of content items with load-more.
 */
@Composable
fun FeedScreen(
    state: FeedUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClicked: (String) -> Unit,
    onSortChanged: (String) -> Unit,
    onTagClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 56.dp
) {
    val listState = rememberLazyListState()

    // Detect scroll to bottom for load-more
    LaunchedEffect(listState.isScrollInProgress, listState.layoutInfo) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (lastVisible?.index == listState.layoutInfo.totalItemsCount - 1 &&
            listState.layoutInfo.totalItemsCount > 0 &&
            state.items.isNotEmpty()
        ) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomBarHeight)
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.items.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No content yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add sources to see content here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
            else -> {
                items(state.items.size) { index ->
                    FeedItemCard(
                        item = state.items[index],
                        onClick = { onItemClicked(state.items[index].id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * UI state for the feed screen.
 */
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

/**
 * A single feed item.
 */
data class FeedItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val timestamp: String? = null
) {
    /**
     * Convert FeedItem to VideoCard for use with VideoContainer.
     */
    fun toVideoCard(): VideoCard {
        return VideoCard(
            id = id,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = subtitle,
            url = ""
        )
    }

    /**
     * Convert FeedItem + IPlatformContent to VideoCard with full metadata.
     */
    fun toVideoCardWithMetadata(content: com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent): VideoCard {
        val video = content as? com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
        return VideoCard(
            id = id,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = subtitle,
            durationMs = video?.duration,
            viewCount = video?.viewCount,
            publishedAt = video?.playbackDate?.toEpochSecond(),
            url = ""
        )
    }
}
