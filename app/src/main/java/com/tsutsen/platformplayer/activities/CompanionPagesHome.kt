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
