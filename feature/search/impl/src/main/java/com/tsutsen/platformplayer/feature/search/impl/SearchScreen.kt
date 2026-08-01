package com.tsutsen.platformplayer.feature.search.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * Shows a search bar at the top with recent search history suggestions.
 * When a search is executed, displays results in a video grid (same layout as Home).
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
            // Search bar
            SearchBar(
                query = searchQuery,
                isFocused = isSearchBarFocused,
                onQueryChange = { searchQuery = it },
                onSearch = { query ->
                    viewModel.search(query)
                    isSearchBarFocused = false
                },
                onFocusedChange = { isSearchBarFocused = it },
                onHistoryItemClick = { query ->
                    searchQuery = query
                    viewModel.search(query)
                    isSearchBarFocused = false
                },
                onDeleteItem = { query ->
                    viewModel.deleteFromHistory(query)
                },
                onClearHistory = { viewModel.clearHistory() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Show history popup when focused and query is empty
            if (isSearchBarFocused && searchQuery.isBlank() && uiState.searchHistory.isNotEmpty()) {
                RecentSearches(
                    history = uiState.searchHistory.take(5),
                    onItemClick = { query ->
                        searchQuery = query
                        viewModel.search(query)
                        isSearchBarFocused = false
                    },
                    onDeleteItem = { query ->
                        viewModel.deleteFromHistory(query)
                    },
                    onClearHistory = { viewModel.clearHistory() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // Content area
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
                    // Show empty state or history suggestions
                    if (uiState.searchHistory.isNotEmpty() && searchQuery.isBlank() && !isSearchBarFocused) {
                        RecentSearches(
                            history = uiState.searchHistory.take(5),
                            onItemClick = { query ->
                                searchQuery = query
                                viewModel.search(query)
                            },
                            onDeleteItem = { query ->
                                viewModel.deleteFromHistory(query)
                            },
                            onClearHistory = { viewModel.clearHistory() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.items.isEmpty() && !uiState.isLoading && searchQuery.isNotBlank()) {
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
 * Search bar with history suggestions dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    isFocused: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onFocusedChange: (Boolean) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(24.dp)
                            .clickable {
                                onQueryChange("")
                                onFocusedChange(false)
                            }
                    )
                }
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                        .clickable {
                            onSearch(query)
                        }
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch(query) }
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
