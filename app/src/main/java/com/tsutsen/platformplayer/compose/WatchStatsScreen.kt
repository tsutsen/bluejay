package com.tsutsen.platformplayer.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.stats.CreatorWatch
import com.tsutsen.platformplayer.stats.WatchStats
import com.tsutsen.platformplayer.stats.humanDuration
import java.time.format.TextStyle
import java.util.Locale

/**
 * Watch-time stats for the Dash tab: the compact card section
 * ([WatchStatsSummary], rendered inside the queue card) and the full
 * detail screen ([WatchStatsDetailScreen]). Health-app style: big
 * numbers, bar charts, ranked creators.
 */

/**
 * Bottom-aligned bar chart for watch time. [barWidth] null makes the bars
 * share the row evenly (weighted) for wide charts like the monthly trend.
 * [labels] draws one line under each bar; [highlightIndex] marks one bar
 * in full-strength primary (e.g. today).
 */
@Composable
fun WatchTimeBars(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = Tokens.ChartSm,
    barWidth: Dp? = Tokens.SpaceXs,
    labels: List<String>? = null,
    highlightIndex: Int? = null,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            if (value > 0) height * fraction.coerceAtLeast(0.15f) else 2.dp,
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
 * The stats section of the Dash queue card: today's total, the week
 * average, the last-week bar chart and the top creators — tapping it
 * opens the detail screen.
 */
@Composable
fun WatchStatsSummary(
    stats: WatchStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    if (stats.isEmpty) {
        Text(
            text = "No watch history yet",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
        Spacer(modifier = Modifier.height(Tokens.SpaceSm))
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
            Spacer(modifier = Modifier.width(Tokens.SpaceMd))
            WatchTimeBars(
                values = stats.lastWeekDaily.map { it.ms },
                height = Tokens.ChartSm,
                highlightIndex = stats.lastWeekDaily.lastIndex,
            )
        }
        if (stats.topCreatorsLastWeek.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Tokens.SpaceSm))
            Text(
                text = "Top this week: " +
                    stats.topCreatorsLastWeek.joinToString(" · ") { it.author },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Full watch-time detail: all-time total, the last week broken down,
 * top creators (expanded) and the monthly trend.
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
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.SpaceLg),
        ) {
            // Hero: total watched over the whole history.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.SpaceXs)
                    .clip(RoundedCornerShape(BluejayTokens().radius.lg))
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
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = "across ${stats.videoCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
            SectionTitle("This week")
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                MiniStat("Today", humanDuration(stats.todayMs), Modifier.weight(1f))
                MiniStat(
                    "Daily average",
                    humanDuration(stats.weekAverageMs),
                    Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(Tokens.SpaceMd))
            WatchTimeBars(
                values = stats.lastWeekDaily.map { it.ms },
                height = Tokens.ChartLg,
                labels = stats.lastWeekDaily.map { it.day.label },
                highlightIndex = stats.lastWeekDaily.lastIndex,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
            SectionTitle("Top creators")
            Column {
                val topMs = stats.topCreators.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L
                stats.topCreators.forEachIndexed { index, creator ->
                    CreatorRow(
                        rank = index + 1,
                        creator = creator,
                        fraction = (creator.ms.toFloat() / topMs).coerceIn(0f, 1f),
                    )
                    if (index < stats.topCreators.lastIndex) {
                        Spacer(modifier = Modifier.height(Tokens.SpaceMd))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
            SectionTitle("Monthly trend")
            WatchTimeBars(
                values = stats.monthlyTrend.map { it.ms },
                height = Tokens.ChartLg,
                labels = stats.monthlyTrend.map { it.month.label },
                barWidth = null, // weighted: 12 bars share the width
                highlightIndex = stats.monthlyTrend.lastIndex,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Tokens.SpaceLg))
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = Tokens.SpaceXs),
    )
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(BluejayTokens().radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Tokens.SpaceMd),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Tokens.SpaceXxs))
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CreatorRow(rank: Int, creator: CreatorWatch, fraction: Float) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(Tokens.IconMd),
        )
        Text(
            text = creator.author,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = humanDuration(creator.ms),
            style = MaterialTheme.typography.titleSmall,
        )
    }
    Spacer(modifier = Modifier.height(Tokens.SpaceXxs))
    // Proportional bar: the widest creator fills it, the rest scale.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.SpaceXs)
            .clip(RoundedCornerShape(BluejayTokens().radius.xs))
            .background(scheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(BluejayTokens().radius.xs))
                .background(scheme.primary),
        )
    }
}

private val java.time.LocalDate.label: String
    get() = dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

private val java.time.YearMonth.label: String
    get() = month.getDisplayName(TextStyle.NARROW, Locale.getDefault())
