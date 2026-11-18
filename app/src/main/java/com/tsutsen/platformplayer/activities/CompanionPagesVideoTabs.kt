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
