package com.tsutsen.platformplayer.feature.search.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import kotlinx.coroutines.flow.combine

/**
 * Search screen composable.
 * Layout:
 *  1. Search field + search button (top row)
 *  2. Search history list with background (shown on first open or when field is focused)
 *  3. Search result grid (shown after search is executed)
 */
@Composable
fun SearchScreen(
    navigator: Navigator,
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val isWide = rememberIsWide()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchBarFocused by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val refreshingState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Combine repository results with local search history
    val uiState by combine(viewModel.repositoryResults, viewModel.searchHistoryFlow) { results, history ->
        SearchUiState(
            query = searchQuery,
            items = results.items,
            isLoading = results.isLoading,
            hasMorePages = results.hasMorePages,
            error = results.error,
            searchHistory = history
        )
    }.collectAsState(initial = SearchUiState())

    // Reset refresh state when loading completes
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            isRefreshing = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Search field + search button (top row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(24.dp)
                                    .clickable {
                                        searchQuery = ""
                                        isSearchBarFocused = false
                                    }
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.search(searchQuery)
                                hasSearched = true
                                isSearchBarFocused = false
                            }
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Search button
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (searchQuery.isNotBlank()) {
                                viewModel.search(searchQuery)
                                hasSearched = true
                                isSearchBarFocused = false
                            }
                        },
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 2. Search history list with background (shown on first open or when field is focused)
            if ((!hasSearched || searchQuery.isBlank()) && uiState.searchHistory.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    RecentSearches(
                        history = uiState.searchHistory.take(5),
                        onItemClick = { query ->
                            searchQuery = query
                            viewModel.search(query)
                            hasSearched = true
                            isSearchBarFocused = false
                        },
                        onDeleteItem = { query ->
                            viewModel.deleteFromHistory(query)
                        },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
            }

            // 3. Search result grid (shown after search is executed)
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    VideoCardSkeleton(count = 6)
                }
                uiState.error != null && uiState.items.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: "Search failed",
                        onRetry = { viewModel.search(searchQuery) }
                    )
                }
                uiState.items.isNotEmpty() -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        state = refreshingState,
                        onRefresh = {
                            isRefreshing = true
                            viewModel.search(searchQuery)
                        },
                        content = {
                            VideoContainer(
                                items = uiState.items,
                                layout = if (isWide) ContainerLayout.Grid(3) else ContainerLayout.List,
                                isLoading = uiState.isLoading && uiState.items.size > 6,
                                hasMorePages = uiState.hasMorePages,
                                onCardClick = { card ->
                                    android.util.Log.i("SearchScreen", "Video clicked: ${card.title}")
                                },
                                onLoadMore = { viewModel.nextPage() },
                                modifier = Modifier.fillMaxSize()
                            ) { card ->
                                if (card is VideoCard) {
                                    VideoCard(
                                        card = card,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                    )
                }
                else -> {
                    // Show empty state only after a search was executed and returned no results
                    if (hasSearched && uiState.items.isEmpty() && !uiState.isLoading && searchQuery.isNotBlank()) {
                        EmptyState(
                            message = "No results for \"$searchQuery\"",
                            actionLabel = "Try a different search",
                            onAction = {}
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recent searches list.
 */
@Composable
private fun RecentSearches(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onClearHistory() }
                )
            }
        }

        items(history) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onItemClick(query) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDeleteItem(query) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
