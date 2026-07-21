package com.futo.platformplayer.compose.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxSize()
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
                        Text(
                            text = "No content yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
)
