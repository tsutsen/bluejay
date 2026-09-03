package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
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
 * Bold hand-drawn check (the icon-font check is a thin stroke): ~18% of
 * its width as a round-capped stroke.
 */
@Composable
private fun WatchedCheck(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = this.size.width
        val h = this.size.height
        val path =
            Path().apply {
                moveTo(w * 0.10f, h * 0.54f)
                lineTo(w * 0.40f, h * 0.84f)
                lineTo(w * 0.92f, h * 0.20f)
            }
        drawPath(
            path = path,
            color = Color.White,
            style =
                Stroke(
                    width = w * 0.18f,
                    cap = StrokeCap.Round,
                ),
        )
    }
}

/**
 * Watched badge: the duration pill's shape (same height, corner radius and
 * background) with a thick hand-drawn check instead of text.
 */
@Composable
private fun WatchedBadge(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(size)
                .height(PILL_HEIGHT)
                .clip(RoundedCornerShape(BluejayTokens().radius.xs))
                .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        WatchedCheck(Modifier.size(size * 0.4f))
    }
}

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
        shape = RoundedCornerShape(BluejayTokens().radius.sm),
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
                            .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
                    contentScale = ContentScale.Crop,
                )

                // LIVE badge (top-left) — same red pill as the player header.
                if (card.isLive) {
                    LiveBadge(modifier = Modifier.align(Alignment.TopStart).padding(Tokens.SpaceSm))
                } else if (card.isClip) {
                    ClipBadge(modifier = Modifier.align(Alignment.TopStart).padding(Tokens.SpaceSm))
                }

                // View-count pill (top-right).
                if (viewCount != null) {
                    ThumbnailPill(
                        text = formatViewCount(viewCount),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(Tokens.SpaceSm),
                    )
                }

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
                                        bottomStart = BluejayTokens().radius.sm,
                                        bottomEnd = BluejayTokens().radius.sm,
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

                // Posted-time pill (bottom-left).
                if (publishedAt != null) {
                    ThumbnailPill(
                        text = RelativeTime.format(publishedAt),
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(Tokens.SpaceSm),
                    )
                }

                // Duration pill (bottom-right); watched videos carry the
                // check inside it. Callers hide the progress bar when
                // watched, so the check never doubles as a "progress done".
                if ((durationMs != null && durationMs > 0) || isWatched) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Tokens.SpaceSm),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(BluejayTokens().radius.xs),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isWatched) {
                                WatchedCheck(Modifier.size(10.dp))
                                if (durationMs != null && durationMs > 0) {
                                    Spacer(Modifier.width(2.dp))
                                }
                            }
                            if (durationMs != null && durationMs > 0) {
                                Text(
                                    text = formatDuration(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
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
                // Title: always reserves two lines, so every card in a
                // grid row is the same height and the author line sits at
                // the same spot (the bottom of the text block) regardless
                // of title length.
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Meta line: author only — views and posted time live in the
                // thumbnail pills now.
                if (author != null) {
                    Spacer(modifier = Modifier.height(6.dp))
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
        shape = RoundedCornerShape(BluejayTokens().radius.sm),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    url = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(BluejayTokens().radius.sm)),
                    contentScale = ContentScale.Crop,
                )
                if (card.isLive) {
                    LiveBadge(modifier = Modifier.align(Alignment.TopStart).padding(Tokens.SpaceSm))
                } else if (card.isClip) {
                    ClipBadge(modifier = Modifier.align(Alignment.TopStart).padding(Tokens.SpaceSm))
                }
                if (author != null && !card.isLive && !card.isClip) {
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
        shape = RoundedCornerShape(BluejayTokens().radius.md),
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
                            .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
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

/** Red LIVE pill, top-left of live-stream thumbnails (matches the player). */
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(BluejayTokens().radius.xs))
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onError),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Grey CLIP pill, top-left of clip thumbnails (Twitch clips are ~30 s). */
@Composable
private fun ClipBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(BluejayTokens().radius.xs))
                .background(Color(0xCC202124))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CLIP",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
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
        shape = RoundedCornerShape(BluejayTokens().radius.xs),
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
 * Shorts card: vertical 9:16 thumbnail on the left, title top-aligned to
 * its right and the channel name pinned to the bottom edge.
 */
@Composable
fun VideoCardShorts(
    card: VideoCard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 0..1 watch progress from local history; null = not started. */
    watchProgress: Float? = null,
    /** Shows the watched checkmark badge. */
    isWatched: Boolean = false,
) {
    val title = card.title
    val thumbnailUrl = card.thumbnailUrl
    val durationMs = card.durationMs

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(BluejayTokens().radius.sm),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Tokens.SpaceSm),
        ) {
            Box(
                modifier =
                    Modifier
                        .height(120.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
            ) {
                AsyncImage(
                    url = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Completed badge (bottom-left).
                if (isWatched) {
                    WatchedBadge(
                        size = 22.dp,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(Tokens.SpaceXs),
                    )
                }

                // Duration pill (bottom-right) — shorts usually report no
                // duration, so this stays hidden in practice.
                if (durationMs != null && durationMs > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Tokens.SpaceXs)
                                .height(PILL_HEIGHT)
                                .clip(RoundedCornerShape(BluejayTokens().radius.xs))
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

                // Watch progress: thin bar along the bottom edge.
                if (watchProgress != null) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = BluejayTokens().radius.sm,
                                        bottomEnd = BluejayTokens().radius.sm,
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
            }

            Column(
                modifier =
                    Modifier
                        .padding(start = Tokens.SpaceMd)
                        .fillMaxHeight()
                        .weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = card.author ?: "",
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
