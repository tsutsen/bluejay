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
