package com.tsutsen.platformplayer.feature.subscriptions.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tsutsen.platformplayer.core.designsystem.collectAsActiveState
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.designsystem.layout.TabContentTopPadding
import com.tsutsen.platformplayer.core.designsystem.component.expressiveClickable
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.spatialSpec
import androidx.compose.ui.unit.Dp
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
    // Paused while this keep-alive tab is hidden (LocalTabActive).
    val watchStates by playerViewModel.watchStates.collectAsActiveState(emptyMap())
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
    val density = LocalDensity.current

    // Channel peek: long-press a creator bubble and a pill with the
    // channel name slides out from it (the strip shows no names).
    var peek by remember { mutableStateOf<SubscriptionCreator?>(null) }
    val bubbles = remember { mutableMapOf<String, Rect>() }
    val rootOrigin = remember { mutableStateOf(Offset.Zero) }
    val pillSize = remember { mutableStateOf(IntSize.Zero) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(peek) {
        if (peek != null) {
            slide.snapTo(0f)
            slide.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        } else {
            slide.snapTo(0f)
        }
    }
    // Selecting a creator (or "All") closes the peek.
    LaunchedEffect(state.activeCreatorId) { peek = null }

    val headerTitle =
        state.creators
            .firstOrNull { it.id == state.activeCreatorId }
            ?.let { "by ${it.name}" }
            ?: "All subs"

    // One creator item: the avatar plus its window-relative bounds, so the
    // peek pill can anchor to it. (Plain map — the bounds are read by the
    // overlay, never by the lazy items themselves.)
    val creatorItemContent: @Composable (SubscriptionCreator) -> Unit = { creator ->
        Box(
            modifier =
                Modifier.onGloballyPositioned {
                    val r = it.boundsInWindow()
                    val o = rootOrigin.value
                    bubbles[creator.id] =
                        Rect(
                            r.left - o.x,
                            r.top - o.y,
                            r.right - o.x,
                            r.bottom - o.y,
                        )
                },
        ) {
            CreatorAvatar(
                creator = creator,
                isSelected = creator.id == state.activeCreatorId,
                onClick = { onCreatorSelected(creator.id) },
                onLongClick = { onGoToChannel(creator.url) },
            )
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOrigin.value = it.boundsInWindow().topLeft },
    ) {
        if (isWide) {
            // Wide: header + videos in center, creators on right side
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            // First element of the tab: on the shared 42dp
                            // content line.
                            .padding(top = TabContentTopPadding),
                ) {
                    // Title + filter chips share the header row.
                    SubsHeader(
                        title = headerTitle,
                        filterStarted = state.filterStarted,
                        filterWatched = state.filterWatched,
                        filterVideo = state.filterVideo,
                        filterStreams = state.filterStreams,
                        onStartedToggle = onStartedToggle,
                        onWatchedToggle = onWatchedToggle,
                        onVideoToggle = onVideoToggle,
                        onStreamsToggle = onStreamsToggle,
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
                        creatorItemContent(creator)
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
                // Title + filter chips share the header row.
                SubsHeader(
                    title = headerTitle,
                    filterStarted = state.filterStarted,
                    filterWatched = state.filterWatched,
                    filterVideo = state.filterVideo,
                    filterStreams = state.filterStreams,
                    onStartedToggle = onStartedToggle,
                    onWatchedToggle = onWatchedToggle,
                    onVideoToggle = onVideoToggle,
                    onStreamsToggle = onStreamsToggle,
                )

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
                        creatorItemContent(creator)
                    }
                }

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

        // Channel peek overlay: a pill sliding out of the long-pressed
        // bubble; tapping anywhere else dismisses.
        peek?.let { creator ->
            Box(modifier = Modifier.fillMaxSize().clickable { peek = null })
            val bubble = bubbles[creator.id]
            if (bubble != null && pillSize.value != IntSize.Zero) {
                val gapPx = with(density) { 8.dp.toPx() }
                val pw = pillSize.value.width.toFloat()
                val ph = pillSize.value.height.toFloat()
                val x =
                    if (isWide) {
                        lerp(bubble.right - pw, bubble.left - gapPx - pw, slide.value)
                    } else {
                        bubble.left
                    }
                val y =
                    if (isWide) {
                        bubble.center.y - ph / 2f
                    } else {
                        lerp(bubble.top, bubble.bottom + gapPx, slide.value)
                    }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .graphicsLayer { alpha = slide.value }
                            .onSizeChanged { pillSize.value = it },
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                    ) {
                        Row(modifier = Modifier.padding(5.dp)) {
                            Button(
                                onClick = {
                                    onGoToChannel(creator.url)
                                    peek = null
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = creator.name,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
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
    val avatarSize by animateDpAsState(
        targetValue = if (isSelected) Tokens.SwatchLg + Tokens.SpaceXs else Tokens.SwatchLg,
        animationSpec = spatialSpec<Dp>(),
        label = "creator-avatar-size",
    )
    Box(
        modifier =
            modifier
                .size(avatarSize)
                .expressiveClickable(onClick = onClick, onLongClick = onLongClick ?: {})
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
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
 * Tab header row: the title ("All subs" or "by <channel>") on the left,
 * the filter chips inline on the right. Each chip is an independent
 * toggle:
 *  - Videos / Live are OR within the type category
 *  - Started / Watched gate the watch state (both on by default = all
 *    videos; fresh videos always show, AND with the type)
 */
@Composable
private fun SubsHeader(
    title: String,
    filterStarted: Boolean,
    filterWatched: Boolean,
    filterVideo: Boolean,
    filterStreams: Boolean,
    onStartedToggle: () -> Unit,
    onWatchedToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onStreamsToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = filterVideo,
            onClick = onVideoToggle,
            label = { Text("Videos") },
        )
        FilterChip(
            selected = filterStreams,
            onClick = onStreamsToggle,
            label = { Text("Live") },
        )
        FilterChip(
            selected = filterStarted,
            onClick = onStartedToggle,
            label = { Text("Started") },
        )
        FilterChip(
            selected = filterWatched,
            onClick = onWatchedToggle,
            label = { Text("Watched") },
        )
    }
}
