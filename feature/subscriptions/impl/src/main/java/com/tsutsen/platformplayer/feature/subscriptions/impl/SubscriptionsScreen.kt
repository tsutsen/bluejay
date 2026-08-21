package com.tsutsen.platformplayer.feature.subscriptions.impl

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.layout.TabContentTopPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import kotlinx.coroutines.launch
import com.tsutsen.platformplayer.core.model.VideoCard as ModelVideoCard

/**
 * Subscriptions screen composable.
 * Shows creator avatars, filter badges, and a filtered video feed.
 */
@Composable
fun SubscriptionsScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val gridColumns by viewModel.gridColumns.collectAsState()
    val watchStates by playerViewModel.watchStates.collectAsState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWide = rememberIsWide()
    val coroutineScope = rememberCoroutineScope()
    var optionsCard by remember { mutableStateOf<ModelVideoCard?>(null) }

    // No Scaffold: M3 Scaffold pads its content with the system-bar insets
    // (~58dp on this device), which pushed the first element far below the
    // other tabs'. AppLayout owns the top offset.
    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SubscriptionsUiState.Success -> {
                if (state.creators.isEmpty() && state.items.isEmpty() && !state.isLoading) {
                    EmptyState(
                        message = "No subscriptions yet.\nSubscribe to channels to see their content here.",
                        actionLabel = "Find channels",
                        onAction = { navigator.navigateSearch(autoFocus = false) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SubscriptionsContent(
                        state = state,
                        isWide = isWide,
                        gridColumns = gridColumns,
                        watchStates = watchStates,
                        onCreatorSelected = viewModel::selectCreator,
                        onGoToChannel = { navigator.navigateToChannel(it) },
                        onStartedToggle = viewModel::toggleStarted,
                        onWatchedToggle = viewModel::toggleWatched,
                        onVideoToggle = viewModel::toggleVideo,
                        onStreamsToggle = viewModel::toggleStreams,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onVideoLongClick = { optionsCard = it },
                        onItemClicked = { url -> playerViewModel.play(url) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            is SubscriptionsUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(Tokens.SpaceLg))
                        androidx.compose.material3.Button(
                            onClick = { viewModel.refresh() },
                        ) {
                            Text("Retry")
                        }
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

/**
 * Main subscriptions content area.
 */
@Composable
private fun SubscriptionsContent(
    state: SubscriptionsUiState.Success,
    isWide: Boolean,
    gridColumns: Int,
    watchStates: Map<String, com.tsutsen.platformplayer.core.model.WatchState> = emptyMap(),
    onCreatorSelected: (String?) -> Unit,
    onGoToChannel: (String) -> Unit,
    onStartedToggle: () -> Unit,
    onWatchedToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onStreamsToggle: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClicked: (String) -> Unit,
    onVideoLongClick: (ModelVideoCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()

    if (isWide) {
        // Wide: Filters + videos in center, creators on right side
        Row(modifier = modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        // First element of the tab: on the shared 42dp content line.
                        .padding(top = TabContentTopPadding),
            ) {
                // Filter chips: independent toggles, no cross-coupling
                SubscriptionFilterBadges(
                    filterStarted = state.filterStarted,
                    filterWatched = state.filterWatched,
                    filterVideo = state.filterVideo,
                    filterStreams = state.filterStreams,
                    onStartedToggle = onStartedToggle,
                    onWatchedToggle = onWatchedToggle,
                    onVideoToggle = onVideoToggle,
                    onStreamsToggle = onStreamsToggle,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Videos in grid
                PullToRefreshBox(
                    // Spinner only for a deliberate refresh or first page —
                    // "load more" prefetches silently.
                    isRefreshing = state.isRefreshing || (state.isLoading && state.items.isEmpty()),
                    state = pullToRefreshState,
                    onRefresh = onRefresh,
                    content = {
                        VideoContainer(
                            items = state.items,
                            layout = ContainerLayout.Grid(gridColumns),
                            isLoading = false,
                            hasMorePages = state.hasMorePages,
                            onCardClick = { card -> onItemClicked((card as ModelVideoCard).url) },
                            onLoadMore = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(Tokens.SpaceSm),
                        ) { card ->
                            VideoCard(
                                card = card as ModelVideoCard,
                                onClick = { onItemClicked((card as ModelVideoCard).url) },
                                onLongClick = { onVideoLongClick(card as ModelVideoCard) },
                                modifier = Modifier.fillMaxWidth(),
                                watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                                isWatched = watchStates[card.url]?.isWatched ?: false,
                            )
                        }
                    },
                )
            }

            // Creators on right side ("All" first — the same escape hatch
            // the portrait strip has)
            LazyColumn(
                modifier = Modifier.width(80.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                contentPadding = PaddingValues(top = TabContentTopPadding),
            ) {
                item {
                    CreatorAvatar(
                        creator =
                            SubscriptionCreator(
                                id = "",
                                name = "All",
                                thumbnailUrl = null,
                                subscriberCount = null,
                                url = "",
                            ),
                        isSelected = state.activeCreatorId == null,
                        onClick = { onCreatorSelected(null) },
                    )
                }
                items(state.creators) { creator ->
                    CreatorAvatar(
                        creator = creator,
                        isSelected = creator.id == state.activeCreatorId,
                        onClick = { onCreatorSelected(creator.id) },
                        onLongClick = { onGoToChannel(creator.url) },
                    )
                }
            }
        }
    } else {
        // Portrait: Creators on top, videos below
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // First element of the tab: on the shared 42dp content line.
                    .padding(top = TabContentTopPadding),
        ) {
            // Creator avatar strip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                contentPadding = PaddingValues(horizontal = Tokens.SpaceLg),
            ) {
                item {
                    CreatorAvatar(
                        creator =
                            SubscriptionCreator(
                                id = "",
                                name = "All",
                                thumbnailUrl = null,
                                subscriberCount = null,
                                url = "",
                            ),
                        isSelected = state.activeCreatorId == null,
                        onClick = { onCreatorSelected(null) },
                    )
                }
                items(state.creators) { creator ->
                    CreatorAvatar(
                        creator = creator,
                        isSelected = creator.id == state.activeCreatorId,
                        onClick = { onCreatorSelected(creator.id) },
                        onLongClick = { onGoToChannel(creator.url) },
                    )
                }
            }

            // Filter chips: independent toggles, no cross-coupling
            SubscriptionFilterBadges(
                filterStarted = state.filterStarted,
                filterWatched = state.filterWatched,
                filterVideo = state.filterVideo,
                filterStreams = state.filterStreams,
                onStartedToggle = onStartedToggle,
                onWatchedToggle = onWatchedToggle,
                onVideoToggle = onVideoToggle,
                onStreamsToggle = onStreamsToggle,
                modifier = Modifier.fillMaxWidth(),
            )

            // Videos in list
            PullToRefreshBox(
                // Spinner only for a deliberate refresh or first page —
                // "load more" prefetches silently.
                isRefreshing = state.isRefreshing || (state.isLoading && state.items.isEmpty()),
                state = pullToRefreshState,
                onRefresh = onRefresh,
                content = {
                    VideoContainer(
                        items = state.items,
                        layout = ContainerLayout.List,
                        isLoading = false,
                        hasMorePages = state.hasMorePages,
                        onCardClick = { card -> onItemClicked((card as ModelVideoCard).url) },
                        onLoadMore = onLoadMore,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(Tokens.SpaceSm),
                    ) { card ->
                        VideoCard(
                            card = card as ModelVideoCard,
                            onClick = { onItemClicked((card as ModelVideoCard).url) },
                            onLongClick = { onVideoLongClick(card as ModelVideoCard) },
                            modifier = Modifier.fillMaxWidth(),
                            watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                            isWatched = watchStates[card.url]?.isWatched ?: false,
                        )
                    }
                },
            )
        }
    }
}

/**
 * Creator avatar component.
 */
@Composable
private fun CreatorAvatar(
    creator: SubscriptionCreator,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(if (isSelected) 52.dp else 48.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ).combinedClickable(onClick = onClick, onLongClick = onLongClick ?: {}),
    ) {
        if (creator.thumbnailUrl != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(creator.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = creator.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Subscriptions,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Tokens.SpaceMd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Transparent),
            ) {
                androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Filter chips. Each chip is an independent toggle:
 *  - Videos / Live are OR within the type category
 *  - Started / Watched gate the watch state (both on by default = all
 *    videos; fresh videos always show, AND with the type)
 */
@Composable
private fun SubscriptionFilterBadges(
    filterStarted: Boolean,
    filterWatched: Boolean,
    filterVideo: Boolean,
    filterStreams: Boolean,
    onStartedToggle: () -> Unit,
    onWatchedToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onStreamsToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        contentPadding = PaddingValues(horizontal = Tokens.SpaceLg),
    ) {
        item {
            FilterChip(
                selected = filterVideo,
                onClick = onVideoToggle,
                label = { Text("Videos") },
            )
        }
        item {
            FilterChip(
                selected = filterStreams,
                onClick = onStreamsToggle,
                label = { Text("Live") },
            )
        }
        item {
            FilterChip(
                selected = filterStarted,
                onClick = onStartedToggle,
                label = { Text("Started") },
            )
        }
        item {
            FilterChip(
                selected = filterWatched,
                onClick = onWatchedToggle,
                label = { Text("Watched") },
            )
        }
    }
}
