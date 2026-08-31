package com.tsutsen.platformplayer.feature.channel.impl

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ShortText
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import androidx.compose.material3.CircularProgressIndicator
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.ScrollEndReached
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
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
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val gridColumns by viewModel.gridColumns.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isWide = rememberIsWide()
    var selectedTab by remember { mutableIntStateOf(TAB_VIDEOS) }
    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }

    LaunchedEffect(channelUrl) {
        viewModel.load(channelUrl)
    }

    val loaded = uiState as? ChannelViewModel.ChannelUiState.Loaded

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            loaded?.channel?.banner?.let { bannerUrl ->
                ChannelBannerCover(bannerUrl, modifier = Modifier.matchParentSize())
            }
            // Badge-row readability: opaque surface on the left, fading to
            // transparent toward the right (fade starts ~70%).
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to MaterialTheme.colorScheme.surface,
                                0.7f to MaterialTheme.colorScheme.surface,
                                1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            ),
                        ),
            )
            AppHeader(
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
                                        .padding(start = Tokens.SpaceMd)
                                        .weight(1f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = state.channel.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // Source badge — present only when the
                                    // app has >1 enabled source.
                                    state.channel.sourceIconUrl?.let { iconUrl ->
                                        AsyncImage(
                                            url = iconUrl,
                                            contentDescription = "Source",
                                            modifier =
                                                Modifier
                                                    .padding(start = Tokens.SpaceXs)
                                                    .size(16.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.FillBounds,
                                        )
                                    }
                                }
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
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    loaded?.let { state ->
                        // Bell only makes sense on a subscribed channel.
                        if (state.isSubscribed) {
                            IconButton(onClick = { viewModel.toggleNotify() }) {
                                Icon(
                                    imageVector =
                                        if (state.notifyEnabled) {
                                            Icons.Filled.Notifications
                                        } else {
                                            Icons.Filled.NotificationsOff
                                        },
                                    contentDescription =
                                        if (state.notifyEnabled) {
                                            "Notifications on"
                                        } else {
                                            "Notifications off"
                                        },
                                )
                            }
                        }
                        Button(onClick = { viewModel.toggleSubscription() }) {
                            Text(if (state.isSubscribed) "Subscribed" else "Subscribe")
                        }
                    }
                },
            )
        }

        when (val state = uiState) {
            is ChannelViewModel.ChannelUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
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
                        is CoreVideoCard -> playerViewModel.play(card)
                        is PlaylistCard -> navigator.navigateToPlaylist(card.url)
                        else -> Unit
                    }
                }
                // Shorts tab is capability-gated: tab indices shift when it
                // is present (Videos=0, Shorts=1?, Playlists, About).
                val tabs =
                    buildList {
                        add(ChannelTab("Videos", Icons.Filled.VideoCall))
                        if (state.hasShorts) add(ChannelTab("Shorts", Icons.Filled.ShortText))
                        add(ChannelTab("Playlists", Icons.Filled.PlaylistPlay))
                        add(ChannelTab("About", Icons.Filled.Description))
                    }
                val tabPlaylists = tabs.indexOfFirst { it.label == "Playlists" }
                val tabShorts = tabs.indexOfFirst { it.label == "Shorts" }
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize(),
                    ) {
                        PullToRefreshBox(
                            // Reuse the pull-to-refresh spinner as the loading indicator.
                            isRefreshing = state.isRefreshing,
                            state = rememberPullToRefreshState(),
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize(),
                            content = {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (isWide) {
                                        ChannelContent(
                                            state = state,
                                            selectedTab = selectedTab,
                                            tabShorts = tabShorts,
                                            isWide = true,
                                            gridColumns = gridColumns,
                                            onCardClick = onCardClick,
                                            onLoadMore = { viewModel.loadNextPage() },
                                            onShortsLoadMore = { viewModel.loadShortsNextPage() },
                                            onRetryContent = { viewModel.loadInitialContents() },
                                            onVideoLongClick = { optionsCard = it },
                                        )
                                    } else {
                                        TabRow(selectedTabIndex = selectedTab) {
                                            tabs.forEachIndexed { index, tab ->
                                                Tab(
                                                    selected = selectedTab == index,
                                                    onClick = {
                                                        selectedTab = index
                                                        when (index) {
                                                            tabPlaylists -> viewModel.loadPlaylists()
                                                            tabShorts -> viewModel.loadShortsInitial()
                                                        }
                                                    },
                                                    text = { Text(tab.label) },
                                                )
                                            }
                                        }
                                        ChannelContent(
                                            state = state,
                                            selectedTab = selectedTab,
                                            tabShorts = tabShorts,
                                            isWide = false,
                                            gridColumns = gridColumns,
                                            onCardClick = onCardClick,
                                            onLoadMore = { viewModel.loadNextPage() },
                                            onShortsLoadMore = { viewModel.loadShortsNextPage() },
                                            onRetryContent = { viewModel.loadInitialContents() },
                                            onVideoLongClick = { optionsCard = it },
                                        )
                                    }
                                }
                            },
                        )
                    }

                    if (isWide) {
                        ChannelIconRail(
                            tabs = tabs,
                            selectedTab = selectedTab,
                        ) { index ->
                            selectedTab = index
                            when (index) {
                                tabPlaylists -> viewModel.loadPlaylists()
                                tabShorts -> viewModel.loadShortsInitial()
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
            onPlay = { playerViewModel.play(card) },
            onGoToChannel = { navigator.navigateToChannel(it) },
        )
    }
}

private data class ChannelTab(
    val label: String,
    val icon: ImageVector,
)

private const val TAB_VIDEOS = 0

@Composable
private fun ChannelContent(
    state: ChannelViewModel.ChannelUiState.Loaded,
    selectedTab: Int,
    tabShorts: Int,
    isWide: Boolean,
    gridColumns: Int,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    onShortsLoadMore: () -> Unit,
    onRetryContent: () -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
) {
    val watchStates by hiltViewModel<PlayerViewModel>().watchStates.collectAsState()
    // Indices shift when the Shorts tab is present:
    // Videos=0, [Shorts=1], Playlists, About.
    val tabPlaylists = if (state.hasShorts) 2 else 1
    val tabAbout = tabPlaylists + 1
    when (selectedTab) {
        tabAbout -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Tokens.SpaceLg),
            ) {
                state.channel.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(Tokens.SpaceLg))
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
                    Spacer(Modifier.height(Tokens.SpaceSm))
                }
            }
        }

        tabShorts -> {
            if (state.shortsCards.isEmpty()) {
                val shortsError = state.shortsError
                if (state.shortsLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (shortsError != null) {
                    ErrorState(
                        message = shortsError,
                        onRetry = { onShortsLoadMore() },
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            } else if (isWide) {
                WideVideoGrid(
                    items = state.shortsCards,
                    hasMore = state.shortsHasMore,
                    columns = gridColumns,
                    onCardClick = onCardClick,
                    onLoadMore = onShortsLoadMore,
                    onVideoLongClick = onVideoLongClick,
                )
            } else {
                VideoContainer(
                    items = state.shortsCards,
                    layout = ContainerLayout.List,
                    isLoading = false,
                    hasMorePages = state.shortsHasMore,
                    onCardClick = onCardClick,
                    onLoadMore = onShortsLoadMore,
                ) { card ->
                    if (card is CoreVideoCard) {
                        VideoCard(
                            card = card,
                            onClick = { onCardClick(card) },
                            onLongClick = { onVideoLongClick(card) },
                            watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                            isWatched = watchStates[card.url]?.isWatched ?: false,
                        )
                    } else {
                        Box(Modifier.height(1.dp))
                    }
                }
            }
        }

        tabPlaylists -> {
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
                if (state.contentLoading) {
                    //Initial load or retry in flight — spinner takes priority
                    //over a stale error so the retry press has feedback.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (contentError != null) {
                    ErrorState(
                        message = contentError,
                        onRetry = { onRetryContent() },
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            } else if (isWide) {
                WideVideoGrid(
                    items = state.cards,
                    hasMore = state.hasMore,
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
                ) { card ->
                    if (card is CoreVideoCard) {
                        VideoCard(
                            card = card,
                            onClick = { onCardClick(card) },
                            onLongClick = { onVideoLongClick(card) },
                            watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                            isWatched = watchStates[card.url]?.isWatched ?: false,
                        )
                    } else {
                        Box(Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}

/** Wide-mode videos: card rows in a single LazyColumn. */
@Composable
private fun WideVideoGrid(
    items: List<Card>,
    hasMore: Boolean,
    columns: Int,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
) {
    val listState = rememberLazyListState()
    val rows = remember(items, columns) { items.chunked(columns) }
    ScrollEndReached(
        listState = listState,
        gridState = null,
        itemCount = items.size,
        isLoading = false,
        hasMorePages = hasMore,
        threshold = 3,
        onEndReached = onLoadMore,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(Tokens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        items(
            rows,
            key = { row -> row.joinToString("|") { it.id } },
        ) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
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
    val watchStates by androidx.hilt.navigation.compose.hiltViewModel<PlayerViewModel>().watchStates.collectAsState()
    Box(modifier) {
        if (card is CoreVideoCard) {
            VideoCard(
                card = card,
                onClick = { onCardClick(card) },
                onLongClick = { onVideoLongClick(card) },
                watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                isWatched = watchStates[card.url]?.isWatched ?: false,
            )
        }
    }
}

@Composable
private fun ChannelIconRail(
    tabs: List<ChannelTab>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(80.dp)
                .fillMaxSize()
                .padding(vertical = Tokens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tabs.forEachIndexed { index, tab ->
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

/**
 * The channel cover shown as the top-bar background. The image is fit (never
 * cropped) and centered; left/right gradients fade the image's real edges into
 * the page background. The fitted bounds are computed from the image's
 * intrinsic size (Coil reports it on success) so the gradients sit on the
 * image's edges, not the box's.
 */
@Composable
private fun ChannelBannerCover(bannerUrl: String, modifier: Modifier = Modifier) {
    val edge = MaterialTheme.colorScheme.surface
    var imgW by remember { mutableStateOf(0) }
    var imgH by remember { mutableStateOf(0) }
    val iw = imgW
    val ih = imgH

    Box(
        modifier =
            modifier.drawWithContent {
                drawContent()
                if (iw > 0 && ih > 0) {
                    val scale = minOf(size.width / iw.toFloat(), size.height / ih.toFloat())
                    val rw = (iw * scale).toInt()
                    val rh = (ih * scale).toInt()
                    val rx = (size.width - rw) / 2
                    val ry = (size.height - rh) / 2
                    val fadeW = (rw * 0.18f).toInt().coerceAtLeast(2)
                    if (rw > fadeW * 2) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(edge, edge.copy(alpha = 0f)),
                                    startX = rx.toFloat(),
                                    endX = (rx + fadeW).toFloat(),
                                ),
                            topLeft = Offset(rx.toFloat(), ry.toFloat()),
                            size = Size(fadeW.toFloat(), rh.toFloat()),
                        )
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(edge.copy(alpha = 0f), edge),
                                    startX = (rx + rw - fadeW).toFloat(),
                                    endX = (rx + rw).toFloat(),
                                ),
                            topLeft = Offset((rx + rw - fadeW).toFloat(), ry.toFloat()),
                            size = Size(fadeW.toFloat(), rh.toFloat()),
                        )
                    }
                }
            },
    ) {
        AsyncImage(
            url = bannerUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onIntrinsicSize = { w, h ->
                imgW = w
                imgH = h
            },
        )
    }
}

private fun formatSubscribers(count: Long): String = NumberFormat.getInstance().format(count) + " subscribers"
