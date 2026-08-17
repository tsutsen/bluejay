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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.ChannelCardView
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
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
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val isWide = rememberIsWide()
    var optionsCard by remember { mutableStateOf<VideoCard?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }
    // Screen-space bounds of the search field: gestures that START inside it
    // must not clear its focus.
    val fieldBounds =
        remember { mutableStateOf(Rect.Zero) }

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
            searchHistory = history,
        )
    }.collectAsState(initial = SearchUiState())

    // Reset refresh state when loading completes
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            isRefreshing = false
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(focusManager, fieldBounds) {
                    // Initial pass: see every touch BEFORE children
                    // consume it, so both "tap a result card" and
                    // "scroll the results" release the search
                    // field's focus (and with it the keyboard).
                    // Gestures that start inside the field itself
                    // are left alone.
                    val slop = 8.dp.toPx()
                    awaitPointerEventScope {
                        var downPos: Offset? = null
                        var startedInField = false
                        var cleared = false
                        while (true) {
                            val event =
                                awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Press -> {
                                    // Overwrite any stale state left by a
                                    // cancelled gesture (no cancel event is
                                    // delivered in this API).
                                    val pos =
                                        event.changes
                                            .firstOrNull { it.pressed }
                                            ?.position
                                    downPos = pos
                                    startedInField =
                                        pos != null &&
                                        fieldBounds.value.contains(pos)
                                }

                                PointerEventType.Move -> {
                                    val pos =
                                        event.changes
                                            .firstOrNull()
                                            ?.position
                                            ?: continue
                                    val down = downPos ?: continue
                                    if (!startedInField && !cleared &&
                                        (pos - down).getDistance() > slop
                                    ) {
                                        focusManager.clearFocus()
                                        cleared = true
                                    }
                                }

                                PointerEventType.Release -> {
                                    val pos =
                                        event.changes
                                            .firstOrNull()
                                            ?.position
                                    val down = downPos
                                    if (!startedInField && !cleared &&
                                        pos != null && down != null &&
                                        (pos - down).getDistance() <= slop
                                    ) {
                                        // A plain tap outside the
                                        // field.
                                        focusManager.clearFocus()
                                    }
                                    downPos = null
                                    cleared = false
                                }

                                else -> {
                                    downPos = null
                                    cleared = false
                                }
                            }
                        }
                    }
                },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Search field + search button (top row)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .onFocusChanged { isSearchFocused = it.isFocused }
                            .onGloballyPositioned {
                                fieldBounds.value = it.boundsInWindow()
                            },
                    placeholder = { Text("Search") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier =
                                    Modifier
                                        .padding(end = 4.dp)
                                        .size(24.dp)
                                        .clickable {
                                            searchQuery = ""
                                        },
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.search(searchQuery)
                                    hasSearched = true
                                }
                            },
                        ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Search button
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier =
                        Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.search(searchQuery)
                                    hasSearched = true
                                }
                            },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // 2+3. Results area (focus handling lives on the outer Box).
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Recent searches — only while the search field is focused.
                    if (isSearchFocused && uiState.searchHistory.isNotEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp),
                                    ).padding(12.dp),
                        ) {
                            RecentSearches(
                                history = uiState.searchHistory.take(5),
                                onItemClick = { query ->
                                    searchQuery = query
                                    viewModel.search(query)
                                    hasSearched = true
                                },
                                onDeleteItem = { query ->
                                    viewModel.deleteFromHistory(query)
                                },
                                onClearHistory = { viewModel.clearHistory() },
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
                                onRetry = { viewModel.search(searchQuery) },
                            )
                        }

                        uiState.items.isNotEmpty() -> {
                            val channelResults = uiState.items.filterIsInstance<ChannelCard>()
                            val videoResults = uiState.items.filterIsInstance<VideoCard>()
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                state = refreshingState,
                                onRefresh = {
                                    isRefreshing = true
                                    viewModel.search(searchQuery)
                                },
                                content = {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (channelResults.isNotEmpty()) {
                                            Column(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                            ) {
                                                Text(
                                                    text = "Channels",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.padding(bottom = 8.dp),
                                                )
                                                channelResults.forEach { channel ->
                                                    ChannelCardView(
                                                        card = channel,
                                                        onClick = {
                                                            navigator.navigateToChannel(channel.url)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        VideoContainer(
                                            items = videoResults,
                                            layout =
                                                if (isWide) ContainerLayout.Grid(3) else ContainerLayout.List,
                                            isLoading = uiState.isLoading && videoResults.size > 6,
                                            hasMorePages = uiState.hasMorePages,
                                            onCardClick = { card ->
                                                if (card is VideoCard) playerViewModel.play(card.url)
                                            },
                                            onLoadMore = { viewModel.nextPage() },
                                            modifier = Modifier.fillMaxSize(),
                                        ) { card ->
                                            if (card is VideoCard) {
                                                VideoCard(
                                                    card = card,
                                                    onClick = { playerViewModel.play(card.url) },
                                                    onLongClick = { optionsCard = card },
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        else -> {
                            // Show empty state only after a search was executed and returned no results
                            if (hasSearched && uiState.items.isEmpty() && !uiState.isLoading && searchQuery.isNotBlank()) {
                                EmptyState(
                                    message = "No results for \"$searchQuery\"",
                                    actionLabel = "Try a different search",
                                    onAction = {},
                                )
                            }
                        }
                    }
                }
            }

            optionsCard?.let { card ->
                VideoOptionsSheetHost(
                    video = card,
                    onDismiss = { optionsCard = null },
                    onPlay = { playerViewModel.play(card.url) },
                    onGoToChannel = { navigator.navigateToChannel(it) },
                )
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onClearHistory() },
                )
            }
        }

        items(history) { query ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onItemClick(query) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier =
                        Modifier
                            .size(20.dp)
                            .clickable { onDeleteItem(query) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
