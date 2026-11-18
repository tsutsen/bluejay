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
 * Long-press video options for the second screen. Mirrors the main app's
 * VideoOptionsSheet but is wired directly against the shared repositories —
 * no Hilt ViewModel, since a Presentation has no ViewModelStoreOwner.
 */

