package com.tsutsen.platformplayer.feature.home.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.collectAsActiveState
import com.tsutsen.platformplayer.core.designsystem.component.ChannelCardView
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.designsystem.layout.TabContentTopPadding
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.model.WatchState
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel

/**
 * Home feed screen.
 * Displays a scrollable feed of recommended videos with infinite scroll.
 * Portrait: single-column list. Landscape: 3-column grid.
 */
@Composable
fun HomeScreen(
    navigator: Navigator,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val enabledSources by viewModel.enabledSources.collectAsState()
    // Paused while this keep-alive tab is hidden (LocalTabActive).
    val watchStates by playerViewModel.watchStates.collectAsActiveState(emptyMap())
    val isWide = rememberIsWide()
    var optionsCard by remember { mutableStateOf<VideoCard?>(null) }

    when (val state = uiState) {
        is HomeUiState.Initial,
        is HomeUiState.Loading,
        -> {
            Box(modifier = Modifier.fillMaxSize())
        }

        is HomeUiState.Loaded -> {
            // One chip per enabled source, shown only with >1 source. Each
            // chip is an independent toggle (all on by default, like the
            // Subs filters): cards from a hidden source drop out of the feed.
            var hiddenSources by remember { mutableStateOf(setOf<String>()) }
            val visibleItems =
                if (hiddenSources.isEmpty()) {
                    state.items
                } else {
                    state.items.filter { it.sourceId == null || it.sourceId !in hiddenSources }
                }
            Column(modifier = Modifier.fillMaxSize()) {
                if (enabledSources.size > 1) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(
                                    horizontal = Tokens.SpaceLg,
                                    vertical = Tokens.SpaceXs,
                                ),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                    ) {
                        enabledSources.forEach { source ->
                            FilterChip(
                                selected = source.id !in hiddenSources,
                                onClick = {
                                    hiddenSources =
                                        if (source.id in hiddenSources) {
                                            hiddenSources - source.id
                                        } else {
                                            hiddenSources + source.id
                                        }
                                },
                                label = { Text(source.name) },
                            )
                        }
                    }
                }
                val noSourcesSelected =
                    enabledSources.isNotEmpty() && enabledSources.all { it.id in hiddenSources }
                if (visibleItems.isEmpty() && !state.isLoading && state.error == null) {
                    if (noSourcesSelected) {
                        EmptyState(message = "No sources selected")
                    } else {
                        EmptyState(
                            message = "No content yet",
                            actionLabel = "Tap to refresh",
                            onAction = { viewModel.refresh() },
                        )
                    }
                } else if (state.error != null) {
                    ErrorState(
                        message = state.error,
                        onRetry = { viewModel.retry() },
                    )
                } else {
                    HomeFeedContent(
                        cards = visibleItems,
                        isLoading = state.isLoading,
                        hasMorePages = state.hasMorePages,
                        isWide = isWide,
                        gridColumns = gridColumns,
                        watchStates = watchStates,
                        isRefreshing = state.isRefreshing,
                        onCardClick = { card ->
                            when (card) {
                                is VideoCard -> playerViewModel.play(card)
                                is ChannelCard -> navigator.navigateToChannel(card.url)
                                else -> Unit
                            }
                        },
                        onVideoLongClick = { optionsCard = it },
                        onLoadMore = { viewModel.loadNextPage() },
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        is HomeUiState.Error -> {
            ErrorState(
                message = state.message,
                onRetry = { viewModel.retry() },
            )
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

/**
 * Home feed content with orientation-aware layout.
 * Portrait: single-column list. Landscape: 3-column grid.
 */
@Composable
private fun HomeFeedContent(
    cards: List<Card>,
    isLoading: Boolean,
    hasMorePages: Boolean,
    isWide: Boolean,
    isRefreshing: Boolean = false,
    gridColumns: Int,
    watchStates: Map<String, WatchState> = emptyMap(),
    onCardClick: (Card) -> Unit,
    onVideoLongClick: (VideoCard) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshingState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            // Spinner only for a deliberate refresh or the very first page.
            // "Load more" prefetches silently — no spinner, no footer.
            isRefreshing = isRefreshing || (isLoading && cards.isEmpty()),
            state = refreshingState,
            onRefresh = onRefresh,
            content = {
                VideoContainer(
                    items = cards,
                    layout = if (isWide) ContainerLayout.Grid(gridColumns) else ContainerLayout.List,
                    isLoading = isLoading,
                    hasMorePages = hasMorePages,
                    onCardClick = onCardClick,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.fillMaxSize(),
                    // First element of the tab: on the shared 42dp content line.
                    contentPadding =
                        PaddingValues(
                            start = Tokens.SpaceLg,
                            top = TabContentTopPadding,
                            end = Tokens.SpaceLg,
                            bottom = Tokens.SpaceLg,
                        ),
                ) { card ->
                    when (card) {
                        is VideoCard -> {
                            VideoCard(
                                card = card,
                                onClick = { onCardClick(card) },
                                onLongClick = { onVideoLongClick(card) },
                                watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                                isWatched = watchStates[card.url]?.isWatched ?: false,
                            )
                        }

                        is ChannelCard -> {
                            ChannelCardView(
                                card = card,
                                onClick = { onCardClick(card) },
                            )
                        }

                        else -> {
                            Box(Modifier.height(1.dp))
                        }
                    }
                }
            },
        )
    }
}
