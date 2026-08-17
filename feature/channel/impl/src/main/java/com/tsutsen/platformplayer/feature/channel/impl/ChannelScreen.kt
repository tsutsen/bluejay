package com.tsutsen.platformplayer.feature.channel.impl

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.ScrollEndReached
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import java.text.NumberFormat
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard

/**
 * Channel detail screen: hero header, Videos / Playlists / About tabs.
 * Portrait: top TabRow. Wide: 80 dp vertical icon rail on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    onBack: () -> Unit,
    navigator: Navigator,
    gridColumns: Int = 3,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isWide = rememberIsWide()
    var selectedTab by remember { mutableIntStateOf(TAB_VIDEOS) }
    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }

    LaunchedEffect(channelUrl) {
        viewModel.load(channelUrl)
    }

    val loaded = uiState as? ChannelViewModel.ChannelUiState.Loaded

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                loaded?.let { state ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            url = state.channel.thumbnail,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Column(
                            modifier =
                                Modifier
                                    .padding(start = 12.dp)
                                    .weight(1f),
                        ) {
                            Text(
                                text = state.channel.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatSubscribers(state.channel.subscribers),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                } ?: Text("Channel")
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            actions = {
                loaded?.let { state ->
                    Button(onClick = { viewModel.toggleSubscription() }) {
                        Text(if (state.isSubscribed) "Subscribed" else "Subscribe")
                    }
                }
            },
        )

        when (val state = uiState) {
            is ChannelViewModel.ChannelUiState.Loading -> {
                VideoCardSkeleton(count = 6)
            }

            is ChannelViewModel.ChannelUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                )
            }

            is ChannelViewModel.ChannelUiState.Loaded -> {
                val onCardClick: (Card) -> Unit = { card ->
                    when (card) {
                        is CoreVideoCard -> playerViewModel.play(card.url)
                        is PlaylistCard -> navigator.navigateToPlaylist(card.url)
                        else -> Unit
                    }
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize(),
                    ) {
                        if (isWide) {
                            ChannelContent(
                                state = state,
                                selectedTab = selectedTab,
                                isWide = true,
                                gridColumns = gridColumns,
                                onCardClick = onCardClick,
                                onLoadMore = { viewModel.loadNextPage() },
                                onRetryContent = { viewModel.loadInitialContents() },
                                onVideoLongClick = { optionsCard = it },
                            )
                        } else {
                            TabRow(selectedTabIndex = selectedTab) {
                                TABS.forEachIndexed { index, tab ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = {
                                            selectedTab = index
                                            if (index == TAB_PLAYLISTS) {
                                                viewModel.loadPlaylists()
                                            }
                                        },
                                        text = { Text(tab.label) },
                                    )
                                }
                            }
                            ChannelContent(
                                state = state,
                                selectedTab = selectedTab,
                                isWide = false,
                                gridColumns = gridColumns,
                                onCardClick = onCardClick,
                                onLoadMore = { viewModel.loadNextPage() },
                                onRetryContent = { viewModel.loadInitialContents() },
                                onVideoLongClick = { optionsCard = it },
                            )
                        }
                    }

                    if (isWide) {
                        ChannelIconRail(selectedTab = selectedTab) { index ->
                            selectedTab = index
                            if (index == TAB_PLAYLISTS) {
                                viewModel.loadPlaylists()
                            }
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
            onPlay = { playerViewModel.play(card.url) },
            onGoToChannel = { navigator.navigateToChannel(it) },
        )
    }
}

@Composable
private fun ChannelBanner(bannerUrl: String?) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(160.dp),
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                url = bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private data class ChannelTab(
    val label: String,
    val icon: ImageVector,
)

private val TABS =
    listOf(
        ChannelTab("Videos", Icons.Filled.VideoCall),
        ChannelTab("Playlists", Icons.Filled.PlaylistPlay),
        ChannelTab("About", Icons.Filled.Description),
    )

private const val TAB_VIDEOS = 0
private const val TAB_PLAYLISTS = 1
private const val TAB_ABOUT = 2

@Composable
private fun ChannelContent(
    state: ChannelViewModel.ChannelUiState.Loaded,
    selectedTab: Int,
    isWide: Boolean,
    gridColumns: Int,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    onRetryContent: () -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
) {
    when (selectedTab) {
        TAB_ABOUT -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                ChannelBanner(bannerUrl = state.channel.banner)
                Spacer(Modifier.height(16.dp))
                state.channel.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                state.channel.links.forEach { (label, link) ->
                    Text(
                        text = "$label: $link",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // ponytail: display-only links; no in-app browser seam
                                },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        TAB_PLAYLISTS -> {
            if (state.playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No playlists",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                VideoContainer(
                    items = state.playlists,
                    layout = if (isWide) ContainerLayout.Grid(gridColumns) else ContainerLayout.List,
                    isLoading = false,
                    hasMorePages = false,
                    onCardClick = onCardClick,
                    onLoadMore = {},
                    topContent = if (isWide) null else channelBannerContent(state.channel.banner),
                ) { card ->
                    if (card is PlaylistCard) {
                        PlaylistCardView(
                            card = card,
                            onClick = { onCardClick(card) },
                        )
                    } else {
                        Box(Modifier.height(1.dp))
                    }
                }
            }
        }

        else -> {
            if (state.cards.isEmpty()) {
                val contentError = state.contentError
                if (contentError != null) {
                    ErrorState(
                        message = contentError,
                        onRetry = { onRetryContent() },
                    )
                } else {
                    VideoCardSkeleton(count = 4)
                }
            } else if (isWide) {
                WideVideoGrid(
                    state = state,
                    columns = gridColumns,
                    onCardClick = onCardClick,
                    onLoadMore = onLoadMore,
                    onVideoLongClick = onVideoLongClick,
                )
            } else {
                VideoContainer(
                    items = state.cards,
                    layout = ContainerLayout.List,
                    isLoading = false,
                    hasMorePages = state.hasMore,
                    onCardClick = onCardClick,
                    onLoadMore = onLoadMore,
                    topContent = { ChannelBanner(bannerUrl = state.channel.banner) },
                ) { card ->
                    if (card is CoreVideoCard) {
                        VideoCard(
                            card = card,
                            onClick = { onCardClick(card) },
                            onLongClick = { onVideoLongClick(card) },
                        )
                    } else {
                        Box(Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}

/**
 * Wide-mode videos: scrollable banner followed by 2-column card rows.
 * A single LazyColumn (not a LazyVerticalGrid) so the banner scrolls with
 * the content — grid items cannot span columns.
 */
@Composable
private fun WideVideoGrid(
    state: ChannelViewModel.ChannelUiState.Loaded,
    columns: Int,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
) {
    val listState = rememberLazyListState()
    val rows = remember(state.cards, columns) { state.cards.chunked(columns) }
    ScrollEndReached(
        listState = listState,
        gridState = null,
        itemCount = state.cards.size,
        isLoading = false,
        hasMorePages = state.hasMore,
        threshold = 3,
        onEndReached = onLoadMore,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__top__") {
            ChannelBanner(bannerUrl = state.channel.banner)
        }
        items(
            rows,
            key = { row -> row.joinToString("|") { it.id } },
        ) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WideVideoCell(
                    card = row[0],
                    modifier = Modifier.weight(1f),
                    onCardClick = onCardClick,
                    onVideoLongClick = onVideoLongClick,
                )
                if (row.size > 1) {
                    WideVideoCell(
                        card = row[1],
                        modifier = Modifier.weight(1f),
                        onCardClick = onCardClick,
                        onVideoLongClick = onVideoLongClick,
                    )
                }
                // ponytail: unrolled for the 2-4 setting values; composable calls
                // can't run in a loop lambda, so no per-column helper
                if (row.size > 2) {
                    WideVideoCell(
                        card = row[2],
                        modifier = Modifier.weight(1f),
                        onCardClick = onCardClick,
                        onVideoLongClick = onVideoLongClick,
                    )
                }
                if (row.size > 3) {
                    WideVideoCell(
                        card = row[3],
                        modifier = Modifier.weight(1f),
                        onCardClick = onCardClick,
                        onVideoLongClick = onVideoLongClick,
                    )
                }
                if (row.size == 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WideVideoCell(
    card: Card,
    modifier: Modifier = Modifier,
    onCardClick: (Card) -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
) {
    Box(modifier) {
        if (card is CoreVideoCard) {
            VideoCard(
                card = card,
                onClick = { onCardClick(card) },
                onLongClick = { onVideoLongClick(card) },
            )
        }
    }
}

@Composable
private fun channelBannerContent(bannerUrl: String?): (@Composable () -> Unit)? {
    if (bannerUrl == null) return null
    return { ChannelBanner(bannerUrl = bannerUrl) }
}

@Composable
private fun ChannelIconRail(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(80.dp)
                .fillMaxSize()
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TABS.forEachIndexed { index, tab ->
            IconButton(
                onClick = { onSelect(index) },
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint =
                        if (index == selectedTab) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

private fun formatSubscribers(count: Long): String = NumberFormat.getInstance().format(count) + " subscribers"
