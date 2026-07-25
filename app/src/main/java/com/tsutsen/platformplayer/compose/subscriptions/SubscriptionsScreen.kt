/*
 * Subscriptions Screen
 *
 * Displays subscribed creators' latest content with:
 * - Creator avatar strip (horizontal portrait / vertical landscape)
 * - Filter badges (Watched, Continue, Video, Streams, source filters)
 * - Video feed (single column portrait / grid landscape)
 */

package com.tsutsen.platformplayer.compose.subscriptions

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.compose.feed.FeedItemCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.compose.util.LoadingContent
import com.tsutsen.platformplayer.compose.util.EmptyState
import com.tsutsen.platformplayer.core.ui.RelativeTime

/**
 * Subscriptions screen composable.
 * Shows creator avatars, filter badges, and a filtered video feed.
 */
@Composable
fun SubscriptionsScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
                        onItemClicked = { content ->
                            when (content) {
                                is IPlatformVideo -> navigator.navigateToVideo(content.url)
                                else -> {}
                            }
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.loadFeed() }
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
    onItemClicked: (com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent) -> Unit,
    modifier: Modifier = Modifier
) {
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

                // Video grid
                SubscriptionsVideoGrid(
                    items = state.items,
                    contentList = state.contentList,
                    onItemClicked = onItemClicked,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Creator avatar strip (vertical, right side)
            CreatorAvatarStripVertical(
                creators = state.creators,
                activeCreatorId = state.activeCreatorId,
                onCreatorSelected = onCreatorSelected,
                modifier = Modifier.width(72.dp)
            )
        }
    } else {
        // Portrait: Creators on top, filters below, videos below
        Column(modifier = modifier.fillMaxSize()) {
            // Creator avatar strip (horizontal scroll)
            CreatorAvatarStripHorizontal(
                creators = state.creators,
                activeCreatorId = state.activeCreatorId,
                onCreatorSelected = onCreatorSelected,
                modifier = Modifier.fillMaxWidth()
            )

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

            // Video feed
            SubscriptionsVideoFeed(
                items = state.items,
                contentList = state.contentList,
                onItemClicked = onItemClicked,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== Creator Avatar Strips ====================

/**
 * Horizontal creator avatar strip for portrait layout.
 */
@Composable
private fun CreatorAvatarStripHorizontal(
    creators: List<SubscriptionCreator>,
    activeCreatorId: String?,
    onCreatorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
    ) {
        // "All" avatar at start
        item {
            CreatorAvatar(
                name = "All",
                thumbnailUrl = null,
                isSelected = activeCreatorId == null,
                onClick = { onCreatorSelected(null) },
                modifier = Modifier.size(48.dp)
            )
        }

        items(creators) { creator ->
            CreatorAvatar(
                name = creator.name,
                thumbnailUrl = creator.thumbnailUrl,
                isSelected = activeCreatorId == creator.id,
                hasNewContent = creator.hasNewContent,
                onClick = { onCreatorSelected(creator.id) },
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/**
 * Vertical creator avatar strip for landscape layout (right side).
 */
@Composable
private fun CreatorAvatarStripVertical(
    creators: List<SubscriptionCreator>,
    activeCreatorId: String?,
    onCreatorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 8.dp)
    ) {
        // "All" button at top
        CreatorAvatar(
            name = "All",
            thumbnailUrl = null,
            isSelected = activeCreatorId == null,
            onClick = { onCreatorSelected(null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .height(40.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Creator list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
        ) {
            items(creators) { creator ->
                CreatorAvatar(
                    name = creator.name,
                    thumbnailUrl = creator.thumbnailUrl,
                    isSelected = activeCreatorId == creator.id,
                    hasNewContent = creator.hasNewContent,
                    onClick = { onCreatorSelected(creator.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }
        }
    }
}

/**
 * Single creator avatar component.
 */
@Composable
private fun CreatorAvatar(
    name: String,
    thumbnailUrl: String?,
    isSelected: Boolean,
    hasNewContent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size = if (isSelected) 52.dp else 48.dp
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Subscriptions,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Selection ring
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(
                        Modifier.background(
                            borderColor.copy(alpha = 0.3f),
                            CircleShape
                        )
                    )
            )
        }

        // New content indicator
        if (hasNewContent) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// ==================== Filter Badges ====================

/**
 * Filter badge row for the Subscriptions screen.
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
    ) {
        item {
            FilterChip(
                selected = filterWatched,
                onClick = onWatchedToggle,
                label = { Text("Watched", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (filterWatched) {
                    { Text("●", style = MaterialTheme.typography.labelSmall) }
                } else null,
                modifier = Modifier.height(28.dp)
            )
        }

        item {
            FilterChip(
                selected = filterContinue,
                onClick = onContinueToggle,
                label = { Text("Continue", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (filterContinue) {
                    { Text("●", style = MaterialTheme.typography.labelSmall) }
                } else null,
                modifier = Modifier.height(28.dp)
            )
        }

        item {
            FilterChip(
                selected = filterVideo,
                onClick = onVideoToggle,
                label = { Text("Video", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (filterVideo) {
                    { Text("●", style = MaterialTheme.typography.labelSmall) }
                } else null,
                modifier = Modifier.height(28.dp)
            )
        }

        item {
            FilterChip(
                selected = filterStreams,
                onClick = onStreamsToggle,
                label = { Text("Streams", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (filterStreams) {
                    { Text("●", style = MaterialTheme.typography.labelSmall) }
                } else null,
                modifier = Modifier.height(28.dp)
            )
        }

        // Source filters (YouTube, SoundCloud, Twitch, etc.)
        // These would be populated from active plugins
        // For now, show a placeholder
        if (sourceFilters.isNotEmpty()) {
            items(sourceFilters.keys.toList()) { sourceId ->
                FilterChip(
                    selected = sourceFilters[sourceId] == true,
                    onClick = { onSourceToggle(sourceId) },
                    label = { Text(sourceId, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (sourceFilters[sourceId] == true) {
                        { Text("●", style = MaterialTheme.typography.labelSmall) }
                    } else null,
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

// ==================== Video Feed / Grid ====================

/**
 * Video feed for portrait layout (single column LazyColumn).
 */
@Composable
private fun SubscriptionsVideoFeed(
    items: List<com.tsutsen.platformplayer.compose.feed.FeedItem>,
    contentList: List<com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent>,
    onItemClicked: (com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Detect scroll to bottom for load-more
    LaunchedEffect(listState.isScrollInProgress, listState.layoutInfo) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (lastVisible?.index == listState.layoutInfo.totalItemsCount - 1 &&
            listState.layoutInfo.totalItemsCount > 0
        ) {
            onRefresh()
        }
    }

    if (items.isEmpty()) {
        EmptyState(
            message = "No videos from selected creator",
            modifier = modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth()
        ) {
            items(items.size) { index ->
                FeedItemCard(
                    item = items[index],
                    onClick = {
                        val content = contentList.find { it.id?.value == items[index].id }
                        if (content != null) onItemClicked(content)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Video grid for landscape layout (3-column LazyVerticalGrid).
 */
@Composable
private fun SubscriptionsVideoGrid(
    items: List<com.tsutsen.platformplayer.compose.feed.FeedItem>,
    contentList: List<com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent>,
    onItemClicked: (com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    if (items.isEmpty()) {
        EmptyState(
            message = "No videos from selected creator",
            modifier = modifier.fillMaxSize()
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
        ) {
            items(items.size) { index ->
                FeedItemCard(
                    item = items[index],
                    onClick = {
                        val content = contentList.find { it.id?.value == items[index].id }
                        if (content != null) onItemClicked(content)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
