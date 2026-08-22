package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.core.ui.RelativeTime
import kotlin.math.roundToLong

// Shared thumbnail-pill geometry: the duration pill, the watched-progress
// pill and the completed badge all use these so they line up on the
// thumbnail corners.
private val PILL_HEIGHT = 18.dp

/**
 * Standard video card with fixed height layout.
 * Thumbnail: 60% of height (16:9 aspect ratio)
 * Text area: 40% of height
 */
@Composable
fun VideoCard(
    card: VideoCard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 0..1 watch progress from local history; null = not started. Hidden while downloading. */
    watchProgress: Float? = null,
    /** Shows the watched checkmark badge. */
    isWatched: Boolean = false,
) {
    val title = card.title
    val author = card.author
    val viewCount = card.viewCount
    val durationMs = card.durationMs
    val publishedAt = card.publishedAt
    val thumbnailUrl = card.thumbnailUrl
    val downloadProgress = card.downloadProgress

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Tokens.RadiusSm),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Thumbnail area (60% of height)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    url = thumbnailUrl,
                    contentDescription = title,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Tokens.RadiusSm)),
                    contentScale = ContentScale.Crop,
                )

                // In-progress download: percentage + bar across the bottom
                if (downloadProgress != null) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.55f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Downloading ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                        )
                    }
                }

                // Watch progress: a thin bar hugging the thumbnail's bottom
                // edge (full width, 4dp). The track's bottom corners follow the
                // thumbnail radius so it sits flush on the edge. Hidden while
                // downloading.
                if (downloadProgress == null && watchProgress != null) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = Tokens.RadiusSm,
                                        bottomEnd = Tokens.RadiusSm,
                                    ),
                                ).background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(watchProgress.coerceIn(0f, 1f))
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                // Completed badge (bottom-LEFT, never drawn together with the
                // progress pill — callers hide progress when watched).
                if (isWatched) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(Tokens.SpaceSm)
                                .height(26.dp)
                                .clip(RoundedCornerShape(Tokens.RadiusXs))
                                .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Watched",
                            tint = Color.White,
                            modifier =
                                Modifier
                                    .size(22.dp)
                                    .padding(horizontal = 8.dp),
                        )
                    }
                }

                // Duration pill (bottom-right)
                if (durationMs != null && durationMs > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Tokens.SpaceSm)
                                .height(PILL_HEIGHT)
                                .clip(RoundedCornerShape(Tokens.RadiusXs))
                                .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Text area (40% of height)
            Column(
                modifier =
                    Modifier
                        .padding(Tokens.SpaceMd)
                        .fillMaxWidth(),
            ) {
                // Title (2 lines max)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Meta line: Author • Views • Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (author != null) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(Tokens.SpaceXs))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(Tokens.SpaceXs))
                    }

                    if (viewCount != null) {
                        Text(
                            text = formatViewCount(viewCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (publishedAt != null) {
                            Spacer(modifier = Modifier.width(Tokens.SpaceXs))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(Tokens.SpaceXs))
                        }
                    }

                    if (publishedAt != null) {
                        Text(
                            text = RelativeTime.format(publishedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact video card for small surfaces (e.g. the second display).
 * All meta info — channel, views, posted time, duration — lives in pills on
 * the thumbnail corners; only the title sits below. No meta row, so the card
 * stays short and nothing gets clipped in tight slots.
 */
@Composable
fun VideoCardPills(
    card: VideoCard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title = card.title
    val author = card.author
    val viewCount = card.viewCount
    val publishedAt = card.publishedAt
    val durationMs = card.durationMs
    val thumbnailUrl = card.thumbnailUrl

    // The thumbnail keeps its fixed 16:9 aspect ratio, always. Callers that
    // need the card to fit a short slot constrain the WIDTH (the card height
    // follows from 16:9 + the title), never the thumbnail's ratio.
    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Tokens.RadiusSm),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    url = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Tokens.RadiusSm)),
                    contentScale = ContentScale.Crop,
                )
                if (author != null) {
                    ThumbnailPill(text = author, modifier = Modifier.align(Alignment.TopStart).padding(Tokens.SpaceSm))
                }
                if (viewCount != null) {
                    ThumbnailPill(text = formatViewCount(viewCount), modifier = Modifier.align(Alignment.TopEnd).padding(Tokens.SpaceSm))
                }
                if (publishedAt != null) {
                    ThumbnailPill(
                        text = RelativeTime.format(publishedAt),
                        modifier = Modifier.align(Alignment.BottomStart).padding(Tokens.SpaceSm),
                    )
                }
                if (durationMs != null && durationMs > 0) {
                    ThumbnailPill(text = formatDuration(durationMs), modifier = Modifier.align(Alignment.BottomEnd).padding(Tokens.SpaceSm))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(Tokens.SpaceMd),
            )
        }
    }
}

/**
 * Full-size video card for list/strip contexts with vertical room (e.g. the
 * second screen's recommended strip): fixed 16:9 thumbnail on top, title
 * below it, channel + posted time pinned to the bottom edge. No pills, no
 * duration, no view count — the card fills the height the strip gives it,
 * like the comment cards.
 */
@Composable
fun VideoCardFull(
    card: VideoCard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title = card.title
    val author = card.author
    val publishedAt = card.publishedAt
    val thumbnailUrl = card.thumbnailUrl

    Card(
        modifier =
            modifier
                .fillMaxHeight()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Tokens.RadiusMd),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Tokens.SpaceMd),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    url = thumbnailUrl,
                    contentDescription = title,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Tokens.RadiusSm)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.height(Tokens.SpaceSm))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Push the meta row to the bottom edge.
            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = author ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (publishedAt != null) {
                    Text(
                        text = RelativeTime.format(publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(Tokens.RadiusXs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .widthIn(max = 120.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/**
 * Compact landscape video card (96×54 thumbnail ratio).
 */
@Composable
fun CompactVideoCard(
    card: VideoCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = card.title
    val author = card.author
    val thumbnailUrl = card.thumbnailUrl

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            url = thumbnailUrl,
            contentDescription = title,
            modifier =
                Modifier
                    .size(128.dp, 72.dp)
                    .clip(RoundedCornerShape(Tokens.RadiusSm)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier =
                Modifier
                    .padding(start = Tokens.SpaceMd)
                    .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (author != null) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * "1.2M" / "340K" view counts.
 * Hand-rolled 1-decimal formatting: String.format pulls in locale machinery
 * and this runs on every recomposition of every visible card.
 */
fun formatViewCount(count: Long): String =
    when {
        count >= 1_000_000 -> "${oneDecimal(count / 1_000_000.0)}M"
        count >= 1_000 -> "${oneDecimal(count / 1_000.0)}K"
        else -> count.toString()
    }

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
}

/** "M:SS" under an hour, "H:MM:SS" from an hour up (e.g. 1:06:00). */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
