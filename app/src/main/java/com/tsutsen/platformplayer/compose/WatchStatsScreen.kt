package com.tsutsen.platformplayer.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.tsutsen.platformplayer.core.designsystem.component.AvatarCircle
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.stats.CreatorWatch
import com.tsutsen.platformplayer.stats.DailyWatch
import com.tsutsen.platformplayer.stats.WatchStats
import com.tsutsen.platformplayer.stats.humanDuration
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Watch-time stats for the Dash tab: the compact card section
 * ([WatchStatsSummary], the Dash stats card) and the full detail screen
 * ([WatchStatsDetailScreen]). Health-app style: big numbers, bar charts,
 * ranked creators.
 */

/**
 * Bottom-aligned bar chart for watch time. [barWidth] null makes the bars
 * share the row evenly (weighted) for wide charts. [labels] draws one line
 * under each bar; [highlightIndex] marks one bar in full-strength primary
 * (e.g. today).
 */
@Composable
fun WatchTimeBars(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = Tokens.ChartSm,
    barWidth: Dp? = Tokens.SpaceXs,
    labels: List<String>? = null,
    highlightIndex: Int? = null,
    // When true the bars stretch to fill whatever height the caller gives
    // them (paired with a weight(1f) modifier) instead of using [height].
    fillHeight: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val width = barWidth?.let { Modifier.width(it) } ?: Modifier.weight(1f)
            Column(
                modifier = width,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = (value.toFloat() / maxValue).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(height)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    when {
                                        value == 0L -> Modifier.height(Tokens.SpaceXs)
                                        fillHeight ->
                                            Modifier.fillMaxHeight(fraction.coerceAtLeast(0.15f))
                                        else ->
                                            Modifier.height(
                                                height * fraction.coerceAtLeast(0.15f)
                                            )
                                    },
                                )
                                .clip(
                                    RoundedCornerShape(
                                        topStart = BluejayTokens().radius.xs,
                                        topEnd = BluejayTokens().radius.xs,
                                    )
                                )
                                .background(
                                    when {
                                        value == 0L -> scheme.surfaceVariant
                                        index == highlightIndex -> scheme.primary
                                        else -> scheme.primary.copy(alpha = 0.5f)
                                    }
                                ),
                    )
                }
                val label = labels?.getOrNull(index)
                if (label != null) {
                    Spacer(modifier = Modifier.height(Tokens.SpaceXxs))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The Dash stats card: the stat columns and the last-week bar chart below
 * them — tapping it opens the detail screen. The card owns its own
 * container styling; callers pass layout modifiers (weight, padding).
 * [fillHeight] lets the bar chart stretch to fill whatever height the
 * caller gives the card.
 */
@Composable
fun WatchStatsSummary(
    stats: WatchStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BluejayTokens().radius.card))
                .background(scheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(Tokens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        if (stats.isEmpty) {
            Text(
                text = "No watch history yet",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Stats", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "View watch stats",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Tokens.IconMd),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = humanDuration(stats.todayMs),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Avg this week",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = humanDuration(stats.weekAverageMs),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Top this week",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = stats.topCreatorsLastWeek.firstOrNull()?.author ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            WatchTimeBars(
                values = stats.lastWeekDaily.map { it.ms },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (fillHeight) Modifier.weight(1f) else Modifier),
                fillHeight = fillHeight,
                highlightIndex = stats.lastWeekDaily.lastIndex,
            )
        }
    }
}

/**
 * Full watch-time detail: all-time total, the last week broken down,
 * top creators (expanded) and the last 30 days.
 */
@Composable
fun WatchStatsDetailScreen(
    stats: WatchStats,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            leading = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            title = { Text(text = "Watch stats", style = MaterialTheme.typography.titleLarge) },
        )
        if (stats.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No watch history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            return
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Tokens.SpaceLg),
        ) {
            // Hero: total watched over the whole history.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Tokens.SpaceXs)
                        .clip(RoundedCornerShape(BluejayTokens().radius.md))
                        .background(scheme.surfaceContainer)
                        .padding(Tokens.SpaceLg),
            ) {
                Text(
                    text = "Total watched",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = humanDuration(stats.allTimeMs),
                    // Same weight as the MiniStat values below — display
                    // styles default to Normal, which looked thinner next
                    // to the Medium titleLarge numbers.
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = videosLabel(stats.videoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
            SectionTitle("This week")
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                MiniStat(
                    "Today",
                    humanDuration(stats.todayMs),
                    subtext = videosLabel(stats.todayVideoCount),
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    "Daily average",
                    humanDuration(stats.weekAverageMs),
                    subtext = videosLabel(stats.weekVideoCount),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(Tokens.SpaceMd))
            StatCard {
                TippedBars(
                    data = stats.lastWeekDaily,
                    binCount = 7,
                    labeler = { it.label },
                    highlightIndex = 6, // today
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
            SectionTitle("Top creators")
            // Two cards side by side: this week's favourites and the
            // all-time overall list. IntrinsicSize.Min makes the row as
            // tall as the taller card so both cards fill the same height.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                StatCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text = "This week",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Tokens.SpaceSm))
                    if (stats.topCreatorsLastWeek.isEmpty()) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        stats.topCreatorsLastWeek.forEachIndexed { index, creator ->
                            CreatorRow(rank = index + 1, creator = creator)
                            if (index < stats.topCreatorsLastWeek.lastIndex) {
                                Spacer(modifier = Modifier.height(Tokens.SpaceSm))
                            }
                        }
                    }
                }
                StatCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text = "Overall",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Tokens.SpaceSm))
                    stats.topCreators.forEachIndexed { index, creator ->
                        CreatorRow(rank = index + 1, creator = creator)
                        if (index < stats.topCreators.lastIndex) {
                            Spacer(modifier = Modifier.height(Tokens.SpaceSm))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
            SectionTitle("Last 30 days")
            StatCard {
                TippedBars(
                    data = stats.last30Days,
                    binCount = 30,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = Tokens.SpaceXs, bottom = Tokens.SpaceMd),
    )
}

/** The detail screen's content card (same surface as the Dash cards). */
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BluejayTokens().radius.md))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Tokens.SpaceLg),
        content = content,
    )
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    subtext: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(BluejayTokens().radius.md))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Tokens.SpaceMd),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Tokens.SpaceXxs))
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        subtext?.let {
            Spacer(modifier = Modifier.height(Tokens.SpaceXxs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatorRow(rank: Int, creator: CreatorWatch) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(Tokens.IconSm),
        )
        Spacer(modifier = Modifier.width(Tokens.SpaceXs))
        AvatarCircle(
            thumbnailUrl = creator.avatarUrl,
            name = creator.author,
            modifier = Modifier.size(Tokens.AvatarSm),
        )
        Spacer(modifier = Modifier.width(Tokens.SpaceSm))
        Text(
            text = creator.author,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(Tokens.SpaceXs))
        Text(
            text = humanDuration(creator.ms),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Bar chart where every bar carries a native [TooltipBox] that is pinned
 * on tap: the tooltip (a real [androidx.compose.ui.window.Popup], placed
 * above the bar with a caret) shows the day, the watch duration and the
 * top creator for that day. Tapping the same bar, or anywhere outside the
 * tooltip, dismisses it — handled by the popup.
 *
 * Renders a fixed [binCount]-day window ending at [windowEnd] (today): the
 * full window is always shown — days missing from [data] (or beyond its
 * range) fill with 0, as small visible stubs. [labeler] (optional) draws
 * one line under each bar; [highlightIndex] marks one bar in full-strength
 * primary (e.g. today).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TippedBars(
    data: List<DailyWatch>,
    binCount: Int,
    modifier: Modifier = Modifier,
    chartHeight: Dp = Tokens.ChartLg,
    labeler: ((LocalDate) -> String?)? = null,
    highlightIndex: Int? = null,
    windowEnd: LocalDate = LocalDate.now(),
) {
    val scheme = MaterialTheme.colorScheme
    if (binCount <= 0) return
    val byDay = data.associate { it.day to it }
    val days = List(binCount) { i ->
        val day = windowEnd.minusDays((binCount - 1 - i).toLong())
        byDay[day] ?: DailyWatch(day, 0L)
    }
    val maxValue = days.maxOf { it.ms }.coerceAtLeast(1L)
    Row(modifier = modifier) {
        days.forEachIndexed { index, day ->
            val tooltipState = rememberTooltipState(isPersistent = true)
            val scope = rememberCoroutineScope()
            // The wrapper owns the row's equal share: TooltipBox's outer layout
            // is an unconstrained Box (its modifier hits the inner anchor), so
            // a weight passed to it would be ignored by this Row.
            Column(modifier = Modifier.weight(1f)) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = {
                        PlainTooltip {
                            Column {
                                Text(
                                    text = day.day.labelFull,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = humanDuration(day.ms),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.primary,
                                )
                                day.topCreator?.let { creator ->
                                    Text(
                                        text = "Top: $creator",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    },
                    state = tooltipState,
                    // Gestures are handled per bar below, not by the framework.
                    enableUserInput = false,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (tooltipState.isVisible) {
                                        tooltipState.dismiss()
                                    } else {
                                        scope.launch { tooltipState.show() }
                                    }
                                }
                                // Visual gap between bars; the full slot is the hit target.
                                .padding(horizontal = Tokens.SpaceXxs / 2),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(chartHeight),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val fraction = (day.ms.toFloat() / maxValue).coerceIn(0f, 1f)
                            // M3's own color-change timing: 200ms FastOutSlowIn.
                            val barColor by animateColorAsState(
                                targetValue =
                                    when {
                                        day.ms == 0L -> scheme.surfaceVariant
                                        tooltipState.isVisible || index == highlightIndex ->
                                            scheme.primary
                                        else -> scheme.primary.copy(alpha = 0.5f)
                                    },
                                animationSpec =
                                    tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "barColor",
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            if (day.ms > 0)
                                                chartHeight * fraction.coerceAtLeast(0.15f)
                                            // Visible stub so the whole window stays shown
                                            else Tokens.SpaceXs
                                        )
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = BluejayTokens().radius.xs,
                                                topEnd = BluejayTokens().radius.xs,
                                            )
                                        )
                                        .background(barColor),
                            )
                        }
                        labeler?.invoke(day.day)?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Tokens.SpaceXs),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun videosLabel(count: Int): String =
    if (count == 1) "across 1 video" else "across $count videos"

private val FULL_DATE_FORMAT =
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

private val java.time.LocalDate.label: String
    get() = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private val java.time.LocalDate.labelFull: String
    get() = format(FULL_DATE_FORMAT)
