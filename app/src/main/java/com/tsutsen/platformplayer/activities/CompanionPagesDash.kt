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
        .take(WatchState.CONTINUE_POOL_SIZE)
        .filter { !WatchState.finished(it.lastPositionMs, it.totalDurationMs) }
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
