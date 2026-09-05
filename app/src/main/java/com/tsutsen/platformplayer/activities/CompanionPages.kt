package com.tsutsen.platformplayer.activities

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Window
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.compose.WatchStatsSummary
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import com.tsutsen.platformplayer.core.designsystem.component.CommentCardView
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.PillTabs
import com.tsutsen.platformplayer.core.designsystem.component.QueueStripCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardFull
import com.tsutsen.platformplayer.core.designsystem.component.expressiveClickable
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.designsystem.component.connectedGroupShapes
import com.tsutsen.platformplayer.core.designsystem.component.formatDuration
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.ContentType
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.WatchState
import com.tsutsen.platformplayer.core.model.toContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.stats.CreatorWatch
import com.tsutsen.platformplayer.stats.WatchStats
import com.tsutsen.platformplayer.stats.humanDuration
import kotlinx.coroutines.flow.distinctUntilChanged
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun CompanionQueueTabContent(
    queue: List<ContentItem>,
    current: ContentItem?,
    isPlaying: Boolean,
    onPlayItem: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayPause: () -> Unit,
    onLongClick: (ContentItem) -> Unit,
) {
    // Same horizontal component as the Feed queue card on the main screen.
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        QueueStripCard(
            queue = queue,
            current = current,
            isPlaying = isPlaying,
            onPlay = onPlayItem,
            onRemove = onRemove,
            onMove = onMove,
            onPlayPause = onPlayPause,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun CompanionVideoPage(
    playerState: com.tsutsen.platformplayer.core.model.PlayerState,
    video: ContentItem?,
    comments: List<CommentItem>,
    isLive: Boolean = false,
    liveChat: com.tsutsen.platformplayer.core.model.LiveChatUiState? = null,
    recommendations: List<CoreVideoCard>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onPlayItem: (ContentItem) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    queue: List<ContentItem>,
    onQueuePlay: (Int) -> Unit,
    onQueueRemove: (String) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    videoTabKeys: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue"),
    pageOrder: List<String> = listOf("controls", "video", "tabs"),
    savedTypes: Set<SavedVideoType> = emptySet(),
    isSubscribed: Boolean = false,
    onSubscribe: () -> Unit = {},
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onMore: () -> Unit = {},
    companionWindow: Window?,
    onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (element in pageOrder) {
            when (element) {
                "controls" -> {
                    CompanionControlRow(
                        isPlaying = playerState.isPlaying,
                        isLoading = playerState.isLoading,
                        onPlayPause = onPlayPause,
                        onSeekBy = onSeekBy,
                        onPrevious = onPrevious,
                        onNext = onNext,
                    )
                }

                "video" -> {
                    video?.let {
                        CompanionVideoHeader(
                            video = it,
                            positionMs = playerState.currentPositionMs,
                            durationMs = playerState.durationMs,
                        )
                    }
                }

                "tabs" -> {
                    if (video != null) {
                        VideoPageTabs(
                            video = video,
                            videoTabKeys = videoTabKeys,
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected,
                            comments = comments,
                            isLive = isLive,
                            liveChat = liveChat,
                            onPlayItem = onPlayItem,
                            recommendations = recommendations,
                            queue = queue,
                            positionMs = playerState.currentPositionMs,
                            durationMs = playerState.durationMs,
                            chapters = playerState.chapters,
                            savedTypes = savedTypes,
                            isSubscribed = isSubscribed,
                            onSubscribe = onSubscribe,
                            onLike = onLike,
                            onDislike = onDislike,
                            onMore = onMore,
                            onSeekTo = onSeekTo,
                            onPlay = onPlay,
                            onLongClick = onLongClick,
                            onQueuePlay = onQueuePlay,
                            onQueueRemove = onQueueRemove,
                            onQueueMove = onQueueMove,
                            context = context,
                            currentVideo = video,
                            isPlaying = playerState.isPlaying,
                            onPlayPause = onPlayPause,
                            companionWindow = companionWindow,
                            // Tabs take the leftover height so any element
                            // order works — fillMaxSize here would swallow
                            // everything below when tabs isn't last.
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onChannelClick = onChannelClick,
                        )
                    }
                }
            }
        }
        if (video == null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The tab strip + its content area (the "tabs" element of the video page).
 * One scroll state per (enabled) tab — switching tabs never shares or
 * resets a strip's position.
 */
@Composable
internal fun VideoPageTabs(
    video: ContentItem,
    videoTabKeys: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    comments: List<CommentItem>,
    isLive: Boolean,
    liveChat: com.tsutsen.platformplayer.core.model.LiveChatUiState?,
    onPlayItem: (ContentItem) -> Unit,
    recommendations: List<CoreVideoCard>,
    queue: List<ContentItem>,
    positionMs: Long,
    durationMs: Long,
    chapters: List<com.tsutsen.platformplayer.core.model.VideoChapter>,
    savedTypes: Set<SavedVideoType>,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    onQueuePlay: (Int) -> Unit,
    onQueueRemove: (String) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    context: android.content.Context,
    currentVideo: ContentItem?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    companionWindow: Window?,
    modifier: Modifier = Modifier.fillMaxSize(),
    onChannelClick: (String) -> Unit,
) {
    val tabStates = remember(videoTabKeys) { videoTabKeys.map { LazyListState() } }
    val activeTab = videoTabKeys.getOrNull(selectedTab)
    val currentChapterIndex =
        chapters.indexOfLast { it.startTimeMs <= positionMs }
    // Follow the playhead: while on the chapters tab, a chapter
    // change scrolls the strip to the newly active chapter.
    LaunchedEffect(currentChapterIndex) {
        if (activeTab == "chapters" && currentChapterIndex >= 0) {
            tabStates.getOrNull(selectedTab)?.animateScrollToItem(currentChapterIndex)
        }
    }
    Column(
        modifier = modifier,
        // The old layout spaced these 12dp apart; keep the gap below the
        // tab strip so the content doesn't hug it.
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillTabs(
            labels =
                videoTabKeys.map {
                    if (isLive && it == "comments") "Live chat" else it.companionTabLabel()
                },
            selected = selectedTab,
            onSelect = onTabSelected,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            key(selectedTab) {
                if (activeTab == null) {
                    // No tabs enabled — nothing to show in the strip.
                    Unit
                } else if (activeTab == "info") {
                    // Info tab: the main player's channel row (icon-only
                    // subscribe) + a scrollable description card.
                    CompanionInfoTab(
                        video = video,
                        savedTypes = savedTypes,
                        isSubscribed = isSubscribed,
                        onSubscribe = onSubscribe,
                        onLike = onLike,
                        onDislike = onDislike,
                        onMore = onMore,
                        durationMs = durationMs,
                        onSeekTo = onSeekTo,
                        onChannelClick = onChannelClick,
                    )
                } else if (activeTab == "controls") {
                    // Controls tab: playback, volume and brightness
                    // sliders.
                    CompanionControlsTab(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isLive = isLive,
                        onSeekTo = onSeekTo,
                        companionWindow = companionWindow,
                    )
                } else if (activeTab == "queue") {
                    // Queue tab: the playing video pinned on top, then
                    // the pending queue (tap = play, drag = reorder,
                    // X = remove). The same horizontal strip as the
                    // tabs' horizontal strips.
                    CompanionQueueTabContent(
                        queue = queue,
                        current = currentVideo,
                        isPlaying = isPlaying,
                        onPlayItem = onQueuePlay,
                        onRemove = onQueueRemove,
                        onMove = onQueueMove,
                        onPlayPause = onPlayPause,
                        onLongClick = { item ->
                            onLongClick(
                                CoreVideoCard(
                                    id = item.id,
                                    title = item.title,
                                    thumbnailUrl = item.thumbnailUrl,
                                    author = item.author?.name,
                                    authorUrl = item.author?.url,
                                    durationMs = item.durationMs,
                                    viewCount = item.viewCount,
                                    publishedAt = item.publishedAt,
                                    url = item.url,
                                ),
                            )
                        },
                    )
                } else if (activeTab == "dot") {
                    // The distraction-free tab: just the video.
                    Unit
                } else if (activeTab == "comments" && isLive) {
                    // Live stream: the comments slot becomes the live
                    // chat, same as the main player. Vertical, fills the
                    // tab area.
                    com.tsutsen.platformplayer.feature.player.impl.ui.components
                        .LiveChatPanel(
                            state = liveChat,
                            modifier = Modifier.fillMaxSize(),
                            listHeight = null,
                            // No horizontal inset on the companion
                            // (bottom screen) chat.
                            horizontalPadding = 0.dp,
                        )
                } else if (activeTab == "comments" && comments.isEmpty()) {
                    // Centre the empty state in the whole tab area — a
                    // LazyRow item can't fill the (unbounded) row width.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No comments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else if (activeTab == "chapters" && chapters.isEmpty()) {
                    // Centre the empty state in the whole tab area — a
                    // LazyRow item can't fill the (unbounded) row width.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No chapters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyRow(
                        state = tabStates[selectedTab],
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        if (activeTab == "comments") {
                            items(comments, key = { it.id }) { comment ->
                                CommentCardView(
                                    comment = comment,
                                    onTimestampClick = { ms ->
                                        val dur = durationMs
                                        val target =
                                            if (dur > 0) ms.coerceIn(0, dur - 500) else ms.coerceAtLeast(0)
                                        onSeekTo(target)
                                    },
                                    onLinkClick = { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                            )
                                        }
                                    },
                                )
                            }
                        } else if (activeTab == "chapters") {
                            if (chapters.isEmpty()) {
                                item(key = "no-chapters") {
                                    Text(
                                        "No chapters",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp),
                                    )
                                }
                            }
                            itemsIndexed(chapters, key = { _, c -> c.startTimeMs }) { index, chapter ->
                                // Same card chrome as the comment cards; the chapter
                                // containing the current playhead is highlighted.
                                val scheme = MaterialTheme.colorScheme
                                val isCurrent = index == currentChapterIndex
                                // Animated highlight: the active chapter eases in.
                                val containerColor by animateColorAsState(
                                    targetValue =
                                        if (isCurrent) {
                                            scheme.primaryContainer
                                        } else {
                                            scheme.surfaceContainer
                                        },
                                    animationSpec = tween(300),
                                    label = "chapterBg",
                                )
                                val cardFg by animateColorAsState(
                                    targetValue =
                                        if (isCurrent) {
                                            scheme.onPrimaryContainer
                                        } else {
                                            scheme.onSurface
                                        },
                                    animationSpec = tween(300),
                                    label = "chapterFg",
                                )
                                Card(
                                    modifier =
                                        Modifier
                                            .width(240.dp)
                                            .fillMaxHeight()
                                            .combinedClickable(onClick = { onSeekTo(chapter.startTimeMs) }),
                                    shape = RoundedCornerShape(BluejayTokens().radius.md),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = containerColor,
                                        ),
                                ) {
                                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = cardFg,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = formatDuration(chapter.startTimeMs),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = cardFg,
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cardFg,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        } else {
                            if (recommendations.isEmpty()) {
                                item(key = "no-recs") {
                                    Text(
                                        "No recommendations",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp),
                                    )
                                }
                            }
                            items(recommendations, key = { it.url }) { card ->
                                VideoCardFull(
                                    card = card,
                                    onClick = { onPlayItem(card.toContentItem()) },
                                    onLongClick = { onLongClick(card) },
                                    modifier =
                                        Modifier
                                            .width(240.dp)
                                            .padding(horizontal = 2.dp),
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
 * Page 1: the library as four corner slots (2x2).
 * Each slot is a section
 * header + a horizontal pager of that section's cards — swipe left/right
 * inside a slot to page through its list.
 */
@Composable
internal fun CompanionLibraryPage(
    sections: List<LibrarySection>,
    slots: List<String> =
        listOf("watch_later", "liked", "favourite", "history"),
    libraryRepository: LibraryRepository,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    onPlaylistClick: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        // Four 2x2 slots (order matters). Each is a section id or a
        // "playlist:<id>" reference (Settings > Dual screen > Library slots).
        val slotValues = (0 until 4).map { slots.getOrNull(it) }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotCell(
                value = slotValues.getOrNull(0),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                onPlaylistClick = onPlaylistClick,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotCell(
                value = slotValues.getOrNull(1),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                onPlaylistClick = onPlaylistClick,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotCell(
                value = slotValues.getOrNull(2),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                onPlaylistClick = onPlaylistClick,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotCell(
                value = slotValues.getOrNull(3),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                onPlaylistClick = onPlaylistClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One 2x2 corner: a section or playlist slot, blank when unset. */
@Composable
internal fun LibrarySlotCell(
    value: String?,
    sections: List<LibrarySection>,
    libraryRepository: LibraryRepository,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (value == null) {
        Box(modifier = modifier)
        return
    }
    if (value.startsWith("playlist:")) {
        PlaylistSlotPager(
            playlistId = value.substringAfter(":"),
            libraryRepository = libraryRepository,
            onPlay = onPlay,
            onLongClick = onLongClick,
            onPlaylistClick = onPlaylistClick,
            modifier = modifier,
        )
        return
    }
    val section = sections.firstOrNull { it.id == value }
    LibrarySlotPager(
        title = section?.title ?: "Empty",
        cards = section?.items.orEmpty(),
        totalCount = section?.totalCount ?: 0,
        onPlay = onPlay,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

/** A playlist-backed 2x2 slot. Collects the live playlist cards. */
@Composable
internal fun PlaylistSlotPager(
    playlistId: String,
    libraryRepository: LibraryRepository,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = playlistId.toLongOrNull()
    if (id == null) {
        Box(modifier = modifier)
        return
    }
    val pair by libraryRepository
        .observeLocalPlaylist(id)
        .collectAsState(initial = null)
    LibrarySlotPager(
        title = pair?.first?.name ?: "Playlist",
        cards = pair?.second.orEmpty(),
        totalCount = pair?.second?.size ?: 0,
        onPlay = onPlay,
        onLongClick = onLongClick,
        // Same URL shape the main screen's library playlist cards use.
        onSlotClick = { onPlaylistClick("playlist:$playlistId") },
        modifier = modifier,
    )
}

/**
 * Dash page: the stats card (the same widget as the main screen's Dash
 * tab), the top creators with their watch time (this week or all time,
 * Settings > Dual screen > Dash page), and the last unfinished video to
 * continue. The widgets render in [pageOrder].
 */
@Composable
internal fun CompanionDashPage(
    stats: WatchStats,
    history: List<HistoryEntity>,
    currentVideoUrl: String?,
    isPlaying: Boolean = false,
    pageOrder: List<String> = listOf("stats", "top_creators", "continue"),
    topCreatorsScope: String = "week",
    onPlay: (String) -> Unit,
    onPlayItem: (ContentItem) -> Unit,
    discarded: Set<String>,
    onDiscard: (String) -> Unit,
    onStatsClick: () -> Unit,
    onChannelClick: (String) -> Unit,
) {
    // Staggered entrance: the sections fade + slide in as the page appears.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // Top creators scope: this week (default) or all time.
    val topCreators =
        if (topCreatorsScope == "overall") stats.topCreators else stats.topCreatorsLastWeek
    val topCreatorsTitle =
        if (topCreatorsScope == "overall") "Top creators" else "Top creators this week"
    // Saved order wins; unknown keys are dropped, missing widgets appended.
    val widgetKeys =
        (pageOrder + listOf("stats", "top_creators", "continue")).distinct()
    // Not scrollable on purpose: a scrollable page inside the VerticalPager
    // eats the page-swipe gestures until it reaches its edge.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.SpaceSm, vertical = Tokens.SpaceXs),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        widgetKeys.forEach { key ->
            when (key) {
                "stats" ->
                    WatchStatsSummary(
                        stats = stats,
                        onClick = onStatsClick,
                        // Take whatever vertical space the stat columns +
                        // sections leave over, and stretch the bar chart
                        // into it.
                        modifier =
                            Modifier
                                .weight(1f)
                                .sectionEntrance(entered, 0),
                        fillHeight = true,
                        wideChart = true,
                    )

                "top_creators" -> {
                    // A card holding the title + the creator badges.
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .sectionEntrance(entered, 80)
                                .clip(RoundedCornerShape(BluejayTokens().radius.card))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(Tokens.SpaceMd),
                    ) {
                        DashSectionTitle(topCreatorsTitle)
                        if (topCreators.isEmpty()) {
                            DashEmpty("No watch history yet")
                        } else {
                            Spacer(Modifier.height(Tokens.SpaceSm))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                            ) {
                                topCreators.forEach { creator ->
                                    CreatorBadge(
                                        creator = creator,
                                        onClick = { creator.authorUrl?.let(onChannelClick) },
                                    )
                                }
                            }
                        }
                    }
                }

                "continue" -> {
                    // A card holding the title + the entry row (or the
                    // empty-state label).
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .sectionEntrance(entered, 160)
                                .clip(RoundedCornerShape(BluejayTokens().radius.card))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(Tokens.SpaceMd),
                    ) {
                        DashSectionTitle("Continue")
                        val entry =
                            continueEntry(history, currentVideoUrl, isPlaying, discarded)
                        ContinueCard(
                            entry = entry,
                            onPlay = { entry?.let { onPlayItem(it.toContentItem()) } },
                            onDiscard = { entry?.let { onDiscard(it.contentUrl) } },
                        )
                    }
                }
            }
        }
    }
}

/** Fade + slide-up entrance for a dash-page section (staggered by [delayMs]). */
@Composable
internal fun Modifier.sectionEntrance(entered: Boolean, delayMs: Int): Modifier =
    composed {
        val spec = tween<Float>(
            durationMillis = 350,
            delayMillis = delayMs,
            easing = FastOutSlowInEasing,
        )
        val entranceAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = spec,
            label = "dashEntranceAlpha",
        )
        val entranceOffset by animateFloatAsState(
            targetValue = if (entered) 0f else 16f,
            animationSpec = spec,
            label = "dashEntranceOffset",
        )
        graphicsLayer {
            alpha = entranceAlpha
            translationY = entranceOffset
        }
    }

@Composable
internal fun DashSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun DashEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Tokens.SpaceSm),
    )
}

/** A horizontally scrolling badge: avatar, channel name, total watch time. */
@Composable
internal fun CreatorBadge(
    creator: CreatorWatch,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .expressiveClickable(onClick = onClick)
                .clip(RoundedCornerShape(BluejayTokens().radius.md))
                // Main background color, like the video cards on the
                // surfaceContainer panel.
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Tokens.SpaceMd, vertical = Tokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        AsyncImage(
            url = creator.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(Tokens.AvatarSm).clip(CircleShape),
        )
        Column {
            Text(
                text = creator.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = humanDuration(creator.ms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The last unfinished video: thumbnail, title, channel, play + discard.
 * With a null [entry] the card just shows a centered empty-state label.
 * The enclosing dash card is drawn by the caller.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ContinueCard(
    entry: HistoryEntity?,
    onPlay: () -> Unit,
    onDiscard: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val radius = BluejayTokens().radius
    val playSource = remember { MutableInteractionSource() }
    val discardSource = remember { MutableInteractionSource() }
    if (entry == null) {
        Text(
            text = "Watched it all and left no crumbs!",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Tokens.SpaceLg),
        )
        return
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Tokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        Box(
            modifier =
                Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                    .background(scheme.surfaceVariant),
        ) {
            AsyncImage(
                url = entry.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // Play + discard in the companion's connected-group language:
        // on press the group compresses, the seam corners squish, and
        // both colors shift (play keeps the accent the control row uses
        // while stopped; discard shifts up one surface step).
        val playPressed by playSource.collectIsPressedAsState()
        val discardPressed by discardSource.collectIsPressedAsState()
        val playContainer by
            animateColorAsState(
                targetValue = if (playPressed) scheme.primary else scheme.surface,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "continue-play-container",
            )
        val playContent by
            animateColorAsState(
                targetValue = if (playPressed) scheme.onPrimary else scheme.onPrimaryContainer,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "continue-play-content",
            )
        val discardContainer by
            animateColorAsState(
                targetValue = if (discardPressed) scheme.surfaceContainerHigh else scheme.surface,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "continue-discard-container",
            )
        val discardContent by
            animateColorAsState(
                targetValue = if (discardPressed) scheme.onSurface else scheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "continue-discard-content",
            )
        ButtonGroup(
            overflowIndicator = { _ -> },
            modifier =
                Modifier
                    .width(Tokens.ControlLg * 2 + Tokens.SpaceXs)
                    .height(Tokens.ControlLg),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
        ) {
            controlItem(
                icon = Icons.Filled.PlayArrow,
                description = "Play",
                containerColor = playContainer,
                contentColor = playContent,
                shapes = connectedGroupShapes(GroupPosition.First, radius),
                interactionSource = playSource,
                onClick = onPlay,
            )
            controlItem(
                icon = Icons.Filled.Close,
                description = "Discard",
                containerColor = discardContainer,
                contentColor = discardContent,
                shapes = connectedGroupShapes(GroupPosition.Last, radius),
                interactionSource = discardSource,
                onClick = onDiscard,
            )
        }
    }
}

/**
 * The last unfinished video to continue: the newest of the five most
 * recent not-fully-watched history entries, excluding the video actively
 * playing right now and any the user discarded in the widget. The current
 * video is only excluded while playing: playerState.currentVideo survives
 * completion (it clears on close() only), so a finished or paused video
 * must still surface here as its own continue entry.
 */
internal fun continueEntry(
    history: List<HistoryEntity>,
    currentVideoUrl: String?,
    isPlaying: Boolean,
    discarded: Set<String>,
): HistoryEntity? =
    // observeAll() is already ordered watchedAt DESC (newest first).
    // Scan only the five most recent entries so a half-watched video from
    // weeks ago can't zombie-resurface behind a run of finished ones; the
    // active video is excluded from the scan (not just the result) so it
    // doesn't burn a pool slot, and a discarded video the user has since
    // re-opened (it IS the current video) is honoured instead of hidden.
    history
        .filter { !(isPlaying && it.contentUrl == currentVideoUrl) }
        .filter { it.contentUrl !in discarded || it.contentUrl == currentVideoUrl }
        .take(5)
        .filter { it.lastPositionMs > 0L && it.totalDurationMs > 0L }
        .filter {
            it.lastPositionMs.toFloat() <
                it.totalDurationMs * WatchState.WATCHED_FRACTION
        }
        .firstOrNull()

/** Pre-filled details for playback (title/channel/thumb are known). */
internal fun HistoryEntity.toContentItem(): ContentItem =
    ContentItem(
        id = contentUrl,
        url = contentUrl,
        title = title,
        author =
            author?.let {
                Author(
                    id = authorUrl.orEmpty(),
                    name = it,
                    url = authorUrl,
                    thumbnailUrl = null,
                )
            },
        thumbnailUrl = thumbnailUrl,
        contentType = ContentType.VIDEO,
        durationMs = totalDurationMs.takeIf { it > 0L },
    )

internal fun String.companionTabLabel(): String =
    when (this) {
        "info" -> "Info"
        "controls" -> "Controls"
        "comments" -> "Comments"
        "chapters" -> "Chapters"
        "recommended" -> "Recommended"
        "dot" -> "Blank"
        else -> "Queue"
    }

/**
 * One corner slot: section header (with current/total page position) + a
 * horizontal pager. Pages are slightly narrower than the slot so the next
 * item peeks in, and edge fades let cards dissolve at the slot's edges.
 */
@Composable
internal fun LibrarySlotPager(
    title: String,
    cards: List<com.tsutsen.platformplayer.core.model.Card>,
    totalCount: Int,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    // Playlist slots only: tapping the slot opens the playlist on the
    // main screen (same affordance as the main screen's library rows).
    onSlotClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    // 1-based index of the page currently in the centre.
    val currentPage = remember(title) { mutableIntStateOf(1) }
    Card(
        modifier =
            modifier
                .then(
                    if (onSlotClick != null) {
                        Modifier.clickable(onClick = onSlotClick)
                    } else {
                        Modifier
                    },
                )
                .padding(4.dp),
        shape = RoundedCornerShape(BluejayTokens().radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val total = totalCount
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${currentPage.value.coerceIn(1, total)}/$total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The arrow tells the slot apart from a section slot —
                // same affordance as the main screen's library section rows.
                if (onSlotClick != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Open $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (cards.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(BluejayTokens().radius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing here yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // key() recreates the pager (and its state) when the list
                    // identity changes (e.g. empty -> loaded).
                    key(cards) {
                        val state = rememberPagerState(pageCount = { cards.size })
                        LaunchedEffect(state) {
                            snapshotFlow { state.currentPage }
                                .distinctUntilChanged()
                                .collect { currentPage.value = it + 1 }
                        }
                        HorizontalPager(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 8.dp,
                            // Fixed page width narrower than the viewport:
                            // pages centre themselves, so ~20dp of each
                            // neighbour peeks in at the edges.
                            pageSize = PageSize.Fixed(maxWidth - 40.dp),
                        ) { index ->
                            PagerCard(
                                card = cards[index],
                                onClick = onPlay,
                                onLongClick = onLongClick,
                            )
                        }
                    }
                    // Edge fades: the slot's card colour → transparent, so
                    // peeked neighbours dissolve into the tile's edges.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .align(Alignment.CenterStart)
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                cardColor,
                                                cardColor.copy(alpha = 0f),
                                            ),
                                    ),
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .align(Alignment.CenterEnd)
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                cardColor.copy(alpha = 0f),
                                                cardColor,
                                            ),
                                    ),
                                ),
                    )
                }
            }
        }
    }
}

/**
 * Page 2: the home feed as a horizontal pager of grids — each page is a
 * 2x2 grid of feed cards, swipe left/right to move between pages. Reaching
 * the last page fetches the next feed page.
 */
@Composable
internal fun CompanionHomePage(
    items: List<com.tsutsen.platformplayer.core.model.Card>,
    onLoadNextPage: () -> Unit,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (items.isEmpty()) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // The pager state is recreated only when the page count changes
            // (feed grew), keeping the user's current page.
            val feedItems = remember { mutableStateOf(items) }
            LaunchedEffect(items) { feedItems.value = items }
            val pageCount = (feedItems.value.size + 3) / 4
            val lastPage = remember { mutableIntStateOf(0) }
            key(pageCount) {
                val state =
                    rememberPagerState(
                        initialPage = lastPage.value.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                        pageCount = { pageCount },
                    )
                // Reach the last page -> fetch more. The repository no-ops
                // once the feed is exhausted, so extra triggers are harmless.
                LaunchedEffect(state) {
                    snapshotFlow { state.currentPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            lastPage.value = page
                            if (page == pageCount - 1) {
                                onLoadNextPage()
                            }
                        }
                }
                HorizontalPager(
                    state = state,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    pageSpacing = 8.dp,
                ) { pageIndex ->
                    HomeGridPage(
                        pageItems =
                            feedItems.value
                                .chunked(4)
                                .getOrNull(pageIndex)
                                .orEmpty(),
                        onPlay = onPlay,
                        onLongClick = onLongClick,
                    )
                }
            }
        }
    }
}

/** One pager page: a 2x2 grid of feed cards. */
@Composable
internal fun HomeGridPage(
    pageItems: List<com.tsutsen.platformplayer.core.model.Card>,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
) {
    // The whole 2x2 page lives in one card so it reads as a single element,
    // not four separate cells.
    Card(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        shape = RoundedCornerShape(BluejayTokens().radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                HomeGridCell(pageItems.getOrNull(0), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
                HomeGridCell(pageItems.getOrNull(1), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                HomeGridCell(pageItems.getOrNull(2), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
                HomeGridCell(pageItems.getOrNull(3), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun HomeGridCell(
    card: com.tsutsen.platformplayer.core.model.Card?,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        if (card != null) {
            PagerCard(card = card, onClick = onPlay, onLongClick = onLongClick)
        }
    }
}

/**
 * Long-press video options for the second screen. Mirrors the main app's
 * VideoOptionsSheet but is wired directly against the shared repositories —
 * no Hilt ViewModel, since a Presentation has no ViewModelStoreOwner.
 */

