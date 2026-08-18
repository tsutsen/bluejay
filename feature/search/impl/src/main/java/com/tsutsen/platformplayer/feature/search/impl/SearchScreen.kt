package com.tsutsen.platformplayer.feature.search.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.tsutsen.platformplayer.core.designsystem.layout.TabContentTopPadding
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.SearchSort
import com.tsutsen.platformplayer.core.model.SearchType
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
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
    val gridColumns by viewModel.gridColumns.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val sort by viewModel.sort.collectAsState()
    var optionsCard by remember { mutableStateOf<VideoCard?>(null) }
    val searchQuery by viewModel.query.collectAsState()
    val hasSearched = searchQuery.isNotBlank()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }
    // Screen-space bounds of the results area: only gestures that START
    // inside it clear the search field's focus — the field itself, the
    // filter chips and the history list never do.
    val resultsBounds =
        remember { mutableStateOf(Rect.Zero) }

    val refreshingState = rememberPullToRefreshState()

    // Focus the field on entry only when we arrived via a tab-bar press
    // (other entries, e.g. "Find channels", leave the keyboard down).
    LaunchedEffect(Unit) {
        if (navigator.searchAutoFocus.value) {
            focusRequester.requestFocus()
        }
    }

    val performSearch = {
        if (searchQuery.isNotBlank()) viewModel.search(searchQuery)
    }

    // Combine repository results with local search history
    val uiState by combine(viewModel.repositoryResults, viewModel.searchHistoryFlow) { results, history ->
        SearchUiState(
            items = results.items,
            isLoading = results.isLoading,
            hasMorePages = results.hasMorePages,
            error = results.error,
            searchHistory = history,
        )
    }.collectAsState(initial = SearchUiState())

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(focusManager, resultsBounds) {
                    // Initial pass: see every touch BEFORE children
                    // consume it, so both "tap a result card" and
                    // "scroll the results" release the search
                    // field's focus (and with it the keyboard).
                    // Gestures that start anywhere else (field, filter
                    // chips, history list) are left alone.
                    val slop = 8.dp.toPx()
                    awaitPointerEventScope {
                        var downPos: Offset? = null
                        var startedInResults = false
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
                                    startedInResults =
                                        pos != null &&
                                        resultsBounds.value.contains(pos)
                                }

                                PointerEventType.Move -> {
                                    val pos =
                                        event.changes
                                            .firstOrNull()
                                            ?.position
                                            ?: continue
                                    val down = downPos ?: continue
                                    if (startedInResults && !cleared &&
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
                                    if (startedInResults && !cleared &&
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
            // 1. Search field: one pill with the search button embedded in
            // the field (no leading icon, no button next to it).
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setQuery(it) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // First element of the tab: on the shared 42dp content line.
                        .padding(start = 16.dp, top = TabContentTopPadding, end = 16.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isSearchFocused = it.isFocused },
                placeholder = { Text("Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = { performSearch() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = { performSearch() },
                    ),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            // Which kind of content to search for.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchType.entries.forEach { type ->
                    FilterChip(
                        selected = type == searchType,
                        onClick = { viewModel.setSearchType(type) },
                        label = { Text(searchTypeLabel(type)) },
                    )
                }

                // Sorting is only supported for media search: a pill that
                // opens the sort menu from itself.
                if (searchType == SearchType.MEDIA) {
                    val sortMenuExpanded = remember { mutableStateOf(false) }
                    // Pushed to the right, separate from the type chips; the
                    // menu anchors to the pill itself.
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box {
                            FilterChip(
                                selected = sort != SearchSort.RELEVANCE,
                                onClick = { sortMenuExpanded.value = true },
                                label = { Text(sort.label) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                            DropdownMenu(
                                expanded = sortMenuExpanded.value,
                                onDismissRequest = { sortMenuExpanded.value = false },
                            ) {
                                SearchSort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            if (s == sort) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        text = { Text(s.label) },
                                        onClick = {
                                            sortMenuExpanded.value = false
                                            viewModel.setSort(s)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent searches — below the filters, only while the field is focused.
            AnimatedVisibility(
                visible = isSearchFocused && uiState.searchHistory.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
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
                        onItemClick = { viewModel.search(it) },
                        onDeleteItem = { viewModel.deleteFromHistory(it) },
                        onClearHistory = { viewModel.clearHistory() },
                    )
                }
            }

            // Results area (focus handling lives on the outer Box).
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .onGloballyPositioned {
                            resultsBounds.value = it.boundsInWindow()
                        },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search result grid (shown after search is executed)
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
                            val playlistResults = uiState.items.filterIsInstance<PlaylistCard>()
                            val videoResults = uiState.items.filterIsInstance<VideoCard>()
                            PullToRefreshBox(
                                // Reuse the pull-to-refresh spinner as the loading indicator.
                                isRefreshing = uiState.isLoading,
                                state = refreshingState,
                                onRefresh = {
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
                                                VideoContainer(
                                                    items = channelResults,
                                                    layout =
                                                        if (isWide) {
                                                            ContainerLayout.Grid(gridColumns)
                                                        } else {
                                                            ContainerLayout.List
                                                        },
                                                    isLoading = uiState.isLoading && channelResults.size < 6,
                                                    hasMorePages = uiState.hasMorePages,
                                                    onCardClick = { card ->
                                                        if (card is ChannelCard) navigator.navigateToChannel(card.url)
                                                    },
                                                    onLoadMore = { viewModel.nextPage() },
                                                    modifier = Modifier.weight(1f),
                                                ) { card ->
                                                    if (card is ChannelCard) {
                                                        ChannelCardView(
                                                            card = card,
                                                            onClick = {
                                                                navigator.navigateToChannel(card.url)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (playlistResults.isNotEmpty()) {
                                            VideoContainer(
                                                items = playlistResults,
                                                layout =
                                                    if (isWide) {
                                                        ContainerLayout.Grid(gridColumns)
                                                    } else {
                                                        ContainerLayout.List
                                                    },
                                                isLoading = uiState.isLoading && playlistResults.size < 6,
                                                hasMorePages = uiState.hasMorePages,
                                                onCardClick = { card ->
                                                    if (card is PlaylistCard) navigator.navigateToPlaylist(card.url)
                                                },
                                                onLoadMore = { viewModel.nextPage() },
                                                modifier = Modifier.fillMaxSize(),
                                            ) { card ->
                                                if (card is PlaylistCard) {
                                                    PlaylistCardView(
                                                        card = card,
                                                        onClick = { navigator.navigateToPlaylist(card.url) },
                                                    )
                                                }
                                            }
                                        }
                                        if (videoResults.isNotEmpty()) {
                                            VideoContainer(
                                                items = videoResults,
                                                layout =
                                                    if (isWide) {
                                                        ContainerLayout.Grid(gridColumns)
                                                    } else {
                                                        ContainerLayout.List
                                                    },
                                                isLoading = uiState.isLoading && videoResults.size < 6,
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

private fun searchTypeLabel(type: SearchType): String =
    when (type) {
        SearchType.MEDIA -> "Media"
        SearchType.CREATORS -> "Creators"
        SearchType.PLAYLISTS -> "Playlists"
    }
