package com.tsutsen.platformplayer.feature.subscriptions.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.LayoutMode
import com.tsutsen.platformplayer.core.designsystem.component.LoadingContent
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.model.VideoCard as ModelVideoCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel

/**
 * Subscriptions screen composable.
 * Shows creator avatars, filter badges, and a filtered video feed.
 */
@Composable
fun SubscriptionsScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        when (val state = uiState) {
            is SubscriptionsUiState.Loading -> {
                LoadingContent(
                    loading = true,
                    empty = false,
                    emptyContent = {},
                    modifier = Modifier.padding(paddingValues)
                ) {}
            }
            is SubscriptionsUiState.Success -> {
                if (state.creators.isEmpty() && state.items.isEmpty()) {
                    EmptyState(
                        message = "No subscriptions yet.\nSubscribe to channels to see their content here.",
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    SubscriptionsContent(
                        state = state,
                        isLandscape = isLandscape,
                        onCreatorSelected = viewModel::selectCreator,
                        onWatchedToggle = viewModel::toggleWatched,
                        onContinueToggle = viewModel::toggleContinue,
                        onVideoToggle = viewModel::toggleVideo,
                        onStreamsToggle = viewModel::toggleStreams,
                        onSourceToggle = viewModel::toggleSourceFilter,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onItemClicked = { url ->
                            android.util.Log.i("SubscriptionsScreen", "Video clicked, URL: $url")
                            playerViewModel.play(url)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
            is SubscriptionsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = { /* retry */ }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main subscriptions content area.
 */
@Composable
private fun SubscriptionsContent(
    state: SubscriptionsUiState.Success,
    isLandscape: Boolean,
    onCreatorSelected: (String?) -> Unit,
    onWatchedToggle: () -> Unit,
    onContinueToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onStreamsToggle: () -> Unit,
    onSourceToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing = false
    val pullToRefreshState = rememberPullToRefreshState()

    if (isLandscape) {
        // Landscape: Filters + videos in center, creators on right side
        Row(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                // Filter badges
                SubscriptionFilterBadges(
                    filterWatched = state.filterWatched,
                    filterContinue = state.filterContinue,
                    filterVideo = state.filterVideo,
                    filterStreams = state.filterStreams,
                    sourceFilters = state.sourceFilters,
                    onWatchedToggle = onWatchedToggle,
                    onContinueToggle = onContinueToggle,
                    onVideoToggle = onVideoToggle,
                    onStreamsToggle = onStreamsToggle,
                    onSourceToggle = onSourceToggle,
                    modifier = Modifier.fillMaxWidth()
                )

                // Videos in grid
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                    onRefresh = {
                        isRefreshing = true
                        onRefresh()
                        isRefreshing = false
                    },
                    content = {
                        VideoContainer(
                            items = state.items,
                            layoutMode = LayoutMode.Grid,
                            columns = 3,
                            onCardClick = { card ->
                                if (card is ModelVideoCard) onItemClicked(card.url)
                            },
                            onEndReached = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp)
                        ) { card ->
                            VideoCard(
                                card = card as ModelVideoCard,
                                onClick = {
                                    if (card is ModelVideoCard) onItemClicked(card.url)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            }

            // Creators on right side
            LazyColumn(
                modifier = Modifier.width(80.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.creators) { creator ->
                    CreatorAvatar(
                        creator = creator,
                        isSelected = creator.id == state.activeCreatorId,
                        onClick = { onCreatorSelected(creator.id) }
                    )
                }
            }
        }
    } else {
        // Portrait: Creators on top, videos below
        Column(modifier = modifier.fillMaxSize()) {
            // Creator avatar strip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    CreatorAvatar(
                        creator = SubscriptionCreator(
                            id = "",
                            name = "All",
                            thumbnailUrl = null,
                            subscriberCount = null,
                            url = ""
                        ),
                        isSelected = state.activeCreatorId == null,
                        onClick = { onCreatorSelected(null) }
                    )
                }
                items(state.creators) { creator ->
                    CreatorAvatar(
                        creator = creator,
                        isSelected = creator.id == state.activeCreatorId,
                        onClick = { onCreatorSelected(creator.id) }
                    )
                }
            }

            // Filter badges
            SubscriptionFilterBadges(
                filterWatched = state.filterWatched,
                filterContinue = state.filterContinue,
                filterVideo = state.filterVideo,
                filterStreams = state.filterStreams,
                sourceFilters = state.sourceFilters,
                onWatchedToggle = onWatchedToggle,
                onContinueToggle = onContinueToggle,
                onVideoToggle = onVideoToggle,
                onStreamsToggle = onStreamsToggle,
                onSourceToggle = onSourceToggle,
                modifier = Modifier.fillMaxWidth()
            )

            // Videos in list
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                onRefresh = {
                    isRefreshing = true
                    onRefresh()
                    isRefreshing = false
                },
                content = {
                    VideoContainer(
                        items = state.items,
                        layoutMode = LayoutMode.List,
                        onCardClick = { card ->
                            if (card is ModelVideoCard) onItemClicked(card.url)
                        },
                        onEndReached = onLoadMore,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp)
                    ) { card ->
                        VideoCard(
                            card = card as ModelVideoCard,
                            onClick = {
                                if (card is ModelVideoCard) onItemClicked(card.url)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(if (isSelected) 52.dp else 48.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
    ) {
        if (creator.thumbnailUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(creator.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = creator.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Subscriptions,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Transparent)
            ) {
                androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Filter badges component.
 */
@Composable
private fun SubscriptionFilterBadges(
    filterWatched: Boolean,
    filterContinue: Boolean,
    filterVideo: Boolean,
    filterStreams: Boolean,
    sourceFilters: Map<String, Boolean>,
    onWatchedToggle: () -> Unit,
    onContinueToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onStreamsToggle: () -> Unit,
    onSourceToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            FilterChip(
                selected = filterWatched,
                onClick = onWatchedToggle,
                label = { Text("Watched") }
            )
        }
        item {
            FilterChip(
                selected = filterContinue,
                onClick = onContinueToggle,
                label = { Text("Continue") }
            )
        }
        item {
            FilterChip(
                selected = filterVideo,
                onClick = onVideoToggle,
                label = { Text("Video") }
            )
        }
        item {
            FilterChip(
                selected = filterStreams,
                onClick = onStreamsToggle,
                label = { Text("Streams") }
            )
        }
        items(sourceFilters.keys.toList()) { sourceId ->
            FilterChip(
                selected = sourceFilters[sourceId] ?: true,
                onClick = { onSourceToggle(sourceId) },
                label = { Text(sourceId) }
            )
        }
    }
}
