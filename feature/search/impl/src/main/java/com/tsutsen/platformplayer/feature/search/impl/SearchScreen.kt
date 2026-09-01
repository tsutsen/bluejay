package com.tsutsen.platformplayer.feature.search.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.collectAsActiveState
import com.tsutsen.platformplayer.core.designsystem.component.ChannelCardView
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.SegmentedGroup
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.designsystem.layout.TabContentTopPadding
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.SearchSort
import com.tsutsen.platformplayer.core.model.SearchType
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    // Paused while this keep-alive tab is hidden (LocalTabActive).
    val watchStates by playerViewModel.watchStates.collectAsActiveState(emptyMap())
    val searchType by viewModel.searchType.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val enabledSources by viewModel.enabledSources.collectAsState()
    val selectedSources by viewModel.selectedSources.collectAsState()
    var optionsCard by remember { mutableStateOf<VideoCard?>(null) }
    val searchQuery by viewModel.query.collectAsState()
    val hasSearched = searchQuery.isNotBlank()
    val motion = BluejayTokens().motion
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }

    // Tabs are keep-alive (the screen never leaves composition), so release
    // the field's focus explicitly whenever another tab becomes current.
    val currentRoute by navigator.currentRoute.collectAsState()
    LaunchedEffect(currentRoute) {
        if (currentRoute !is NavDestination.Search && isSearchFocused) {
            focusManager.clearFocus(force = true)
        }
    }

    // Back gesture: the system hands the first back to the IME, which just
    // hides the keyboard and leaves the field focused. Watch the IME's
    // bottom inset directly — when it collapses while the field holds
    // focus, release the focus too.
    val view = LocalView.current
    DisposableEffect(view) {
        val listener =
            OnApplyWindowInsetsListener { v, insets ->
                if (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom == 0 &&
                    isSearchFocused
                ) {
                    focusManager.clearFocus(force = true)
                }
                insets
            }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
        onDispose { ViewCompat.setOnApplyWindowInsetsListener(view, null) }
    }
    // Bounds of the results area, in window space (pointer positions are
    // local to the root Box, so the root Box's window rect is kept too and
    // the conversion happens at event time). Only gestures that START
    // inside the results area clear the field's focus — the field itself,
    // the filter chips and the history list never do.
    val resultsBounds =
        remember { mutableStateOf(Rect.Zero) }
    val rootBounds = remember { mutableStateOf(Rect.Zero) }

    val refreshingState = rememberPullToRefreshState()

    // Focus the field on entry only when we arrived via a tab-bar press
    // (other entries, e.g. "Find channels", leave the keyboard down).
    LaunchedEffect(Unit) {
        if (navigator.searchAutoFocus.value) {
            focusRequester.requestFocus()
        }
    }

    // Leaving the tab (or the screen) releases the field's focus and with
    // it the keyboard.
    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
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
                .onGloballyPositioned { rootBounds.value = it.boundsInWindow() }
                .pointerInput(focusManager, resultsBounds) {
                    // Initial pass: see every touch BEFORE children
                    // consume it, so both "tap a result card" and
                    // "scroll the results" release the search
                    // field's focus (and with it the keyboard).
                    // Gestures that start anywhere else (field, filter
                    // chips, history list) are left alone.
                    val slop = 8.dp.toPx()
                    awaitPointerEventScope {
                        // Local pointer position → window space, using the
                        // root Box's current window rect.
                        fun Offset.toWindow(): Offset {
                            val rb = rootBounds.value
                            return Offset(x + rb.left, y + rb.top)
                        }
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
                                            ?.toWindow()
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
                                            ?.toWindow()
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
                                            ?.toWindow()
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
                    // Search button pins to the field's right edge; the clear
                    // button slides in to its left when there's text. Buttons
                    // are fixed-size, so nothing squishes.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        // isNotBlank (not isNotEmpty): a whitespace-only
                        // field looks empty, so the clear button goes too.
                        AnimatedVisibility(
                            visible = searchQuery.isNotBlank(),
                            enter = fadeIn(motion.stateSpec<Float>()) + expandHorizontally(motion.stateSpec<IntSize>()),
                            exit = fadeOut(motion.stateSpec<Float>()) + shrinkHorizontally(motion.stateSpec<IntSize>())
                        ) {
                            Row {
                                SearchFieldButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    onClick = { viewModel.setQuery("") },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        SearchFieldButton(
                            icon = Icons.Default.Search,
                            contentDescription = "Search",
                            onClick = { performSearch() },
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(BluejayTokens().radius.md),
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
                        .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceXs),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedGroup(
                    options = SearchType.entries,
                    selectedIndex = SearchType.entries.indexOf(searchType),
                    onSelected = { i ->
                        // Switching the type releases the field's focus
                        // (and the keyboard).
                        focusManager.clearFocus()
                        viewModel.setSearchType(SearchType.entries[i])
                    },
                    label = { searchTypeLabel(it) },
                    modifier = Modifier.weight(1f),
                )

                // Source/sort menus pinned to the right; the menus anchor
                // to their pills.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                ) {
                    // Source filter — only when more than one source is
                    // enabled. Empty selection means "all sources".
                    if (enabledSources.size > 1) {
                        val sourceMenuExpanded = remember { mutableStateOf(false) }
                        val allSourceIds = enabledSources.map { it.id }.toSet()
                        Box {
                            FilterChip(
                                selected = selectedSources.isNotEmpty(),
                                onClick = { sourceMenuExpanded.value = true },
                                label = {
                                    Text(
                                        when {
                                            selectedSources.isEmpty() -> {
                                                "All sources"
                                            }

                                            selectedSources.size == 1 -> {
                                                enabledSources
                                                    .firstOrNull {
                                                        it.id in selectedSources
                                                    }?.name ?: "Sources"
                                            }

                                            else -> {
                                                "${selectedSources.size} sources"
                                            }
                                        },
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(Tokens.IconSm),
                                    )
                                },
                            )
                            DropdownMenu(
                                expanded = sourceMenuExpanded.value,
                                onDismissRequest = { sourceMenuExpanded.value = false },
                            ) {
                                // Checkbox variant: the menu stays open after a
                                // pick so several sources can be toggled.
                                SelectionDropdownItems(
                                    items = enabledSources,
                                    label = { it.name },
                                    isSelected = {
                                        selectedSources.isEmpty() || it.id in selectedSources
                                    },
                                    multiSelect = true,
                                    onPick = { source ->
                                        // Tap toggles ONLY this item (empty
                                        // selection = everything checked).
                                        val next =
                                            if (selectedSources.isEmpty()) {
                                                allSourceIds - source.id
                                            } else if (source.id in selectedSources) {
                                                selectedSources - source.id
                                            } else {
                                                selectedSources + source.id
                                            }
                                        // All checked collapses back to "all".
                                        viewModel.setSelectedSources(
                                            if (next == allSourceIds) emptySet() else next,
                                        )
                                    },
                                    onLongPick = { source ->
                                        // Long-press: select only this one.
                                        viewModel.setSelectedSources(setOf(source.id))
                                    },
                                )
                            }
                        }
                    }

                    // Sorting is only supported for media search: a pill that
                    // opens the sort menu from itself.
                    if (searchType == SearchType.MEDIA) {
                        val sortMenuExpanded = remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = sort != SearchSort.RELEVANCE,
                                onClick = { sortMenuExpanded.value = true },
                                label = { Text(sort.label) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(Tokens.IconSm),
                                    )
                                },
                            )
                            DropdownMenu(
                                expanded = sortMenuExpanded.value,
                                onDismissRequest = { sortMenuExpanded.value = false },
                            ) {
                                SelectionDropdownItems(
                                    items = SearchSort.entries,
                                    label = { it.label },
                                    isSelected = { it == sort },
                                    multiSelect = false,
                                    onPick = { s ->
                                        sortMenuExpanded.value = false
                                        viewModel.setSort(s)
                                    },
                                )
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
                            .padding(horizontal = Tokens.SpaceLg)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(BluejayTokens().radius.md),
                            ).padding(Tokens.SpaceMd),
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
                            Box(modifier = Modifier.fillMaxSize())
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
                                                        .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceSm),
                                            ) {
                                                Text(
                                                    text = "Channels",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.padding(bottom = Tokens.SpaceSm),
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
                                                    if (card is VideoCard) playerViewModel.play(card)
                                                },
                                                onLoadMore = { viewModel.nextPage() },
                                                modifier = Modifier.fillMaxSize(),
                                            ) { card ->
                                                if (card is VideoCard) {
                                                    VideoCard(
                                                        card = card,
                                                        onClick = { playerViewModel.play(card) },
                                                        onLongClick = { optionsCard = card },
                                                        watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                                                        isWatched = watchStates[card.url]?.isWatched ?: false,
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
                    onPlay = { playerViewModel.play(card) },
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
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
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

        items(history, key = { it }) { query ->
            // Swipe the row left or right to delete it from history.
            SwipeToDeleteRow(onSwipedAway = { onDeleteItem(query) }) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            ).clickable { onItemClick(query) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
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
}

/**
 * Small rounded-rectangle action button embedded in the search field:
 * primary fill, 26dp square, 16dp icon.
 */
@Composable
private fun SearchFieldButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(BluejayTokens().radius.xs))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Horizontal swipe-to-delete wrapper: dragging [content] left or right
 * follows the finger; releasing past the threshold animates it off-screen
 * in the drag direction and calls [onSwipedAway], otherwise it springs
 * back.
 */
@Composable
private fun SwipeToDeleteRow(
    onSwipedAway: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rowWidthPx = remember { mutableIntStateOf(0) }
    val motion = BluejayTokens().motion
    val offsetAnim = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        modifier =
            Modifier
                .onSizeChanged { rowWidthPx.value = it.width }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // Grabbing the row cancels any in-flight settle or
                            // fly-away, so the user can save a row mid-flight.
                            settleJob?.cancel()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val bound = rowWidthPx.value.toFloat().coerceAtLeast(1f)
                            // Main.immediate: runs inline on the UI thread.
                            scope.launch {
                                offsetAnim.snapTo(
                                    (offsetAnim.value + dragAmount).coerceIn(-bound, bound),
                                )
                            }
                        },
                        onDragEnd = {
                            settleJob =
                                scope.launch {
                                    if (abs(offsetAnim.value) > thresholdPx) {
                                        val target =
                                            (if (offsetAnim.value > 0f) 1f else -1f) *
                                                (rowWidthPx.value + 60f)
                                        offsetAnim.animateTo(target, motion.springSpec<Float>())
                                        onSwipedAway()
                                    } else {
                                        offsetAnim.animateTo(0f, motion.springSpec<Float>())
                                    }
                                }
                        },
                        onDragCancel = {
                            settleJob = scope.launch { offsetAnim.animateTo(0f, motion.springSpec<Float>()) }
                        },
                    )
                },
    ) {
        Box(modifier = Modifier.offset { IntOffset(offsetAnim.value.roundToInt(), 0) }) {
            content()
        }
    }
}

/**
 * Dropdown content with the selection element (radio / checkbox) to the
 * right of the label. Radio variant: the caller closes the menu on pick.
 * Checkbox variant ([multiSelect]): the menu stays open after a pick.
 *
 * [onLongPick] (optional): long-pressing an item calls this instead of
 * [onPick] — used for "select only this one" in multiselect menus.
 */
@Composable
private fun <T> SelectionDropdownItems(
    items: List<T>,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    multiSelect: Boolean,
    onPick: (T) -> Unit,
    onLongPick: ((T) -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        items.forEach { item ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(item) {
                            detectTapGestures(
                                onTap = { onPick(item) },
                                onLongPress = { onLongPick?.invoke(item) },
                            )
                        }.padding(horizontal = Tokens.SpaceMd, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Tokens.SpaceSm))
                if (multiSelect) {
                    Checkbox(checked = isSelected(item), onCheckedChange = { onPick(item) })
                } else {
                    RadioButton(selected = isSelected(item), onClick = { onPick(item) })
                }
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
