package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.Author

/**
 * Channel badge + info strip.
 *
 * Landscape (wide window): everything on a single row — avatar, name,
 * subscribe, like/dislike, stats, more.
 *
 * Portrait (narrow window): split into two rows so nothing gets squeezed:
 *   row 1 — avatar, name, subscribe, more
 *   row 2 — like/dislike pill, view count + published time
 */
@Composable
fun ChannelRow(
    author: Author?,
    viewCount: Long?,
    publishedAt: Long?,
    likeCount: Long?,
    isLiked: Boolean,
    dislikeCount: Long?,
    isDisliked: Boolean,
    isSubscribed: Boolean = false,
    onSubscribe: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMore: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    sourceIconUrl: String? = null,
    /** Icon-only subscribe (second screen) instead of the labelled button. */
    subscribeIconOnly: Boolean = false,
    /** Leading padding (0 on hosts that already inset the row). */
    startPadding: Dp = 16.dp,
) {
    val isWide = rememberIsWide()
    // Tapping the avatar or name opens the channel page.
    val channelUrl = author?.url
    val channelClick: Modifier =
        if (channelUrl != null && onChannelClick != null) {
            Modifier.clickable { onChannelClick(channelUrl) }
        } else {
            Modifier
        }

    val stats =
        buildList {
            if (viewCount != null) add("${formatViewCount(viewCount)} views")
            val ago = formatRelativeTime(publishedAt)
            if (ago.isNotEmpty()) add(ago)
        }.joinToString(" • ")

    if (subscribeIconOnly) {
        // Second screen: one row — avatar, name, subscribe, like/dislike,
        // more. Stats live in the description card, not here.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(author, channelClick)
            Spacer(modifier = Modifier.width(12.dp))
            ChannelName(author, channelClick, sourceIconUrl)
            Spacer(modifier = Modifier.width(8.dp))
            LikeDislikePill(
                likeCount = likeCount,
                isLiked = isLiked,
                onLike = onLike,
                dislikeCount = dislikeCount,
                isDisliked = isDisliked,
                onDislike = onDislike,
            )
            MoreButton(onMore)
        }
        return
    }

    if (isWide) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(author, channelClick)
            Spacer(modifier = Modifier.width(12.dp))
            ChannelName(author, channelClick, sourceIconUrl)
            Spacer(modifier = Modifier.width(8.dp))
            SubscribeButton(isSubscribed, onSubscribe, iconOnly = subscribeIconOnly)
            Spacer(modifier = Modifier.width(12.dp))
            LikeDislikePill(
                likeCount = likeCount,
                isLiked = isLiked,
                onLike = onLike,
                dislikeCount = dislikeCount,
                isDisliked = isDisliked,
                onDislike = onDislike,
            )
            if (stats.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            MoreButton(onMore)
        }
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, top = 4.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(author, channelClick)
                Spacer(modifier = Modifier.width(12.dp))
                ChannelName(author, channelClick, sourceIconUrl)
                Spacer(modifier = Modifier.width(8.dp))
                SubscribeButton(isSubscribed, onSubscribe, iconOnly = subscribeIconOnly)
                MoreButton(onMore)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeDislikePill(
                    likeCount = likeCount,
                    isLiked = isLiked,
                    onLike = onLike,
                    dislikeCount = dislikeCount,
                    isDisliked = isDisliked,
                    onDislike = onDislike,
                )
                if (stats.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    author: Author?,
    channelClick: Modifier,
) {
    if (author?.thumbnailUrl != null) {
        AsyncImage(
            model = author.thumbnailUrl,
            contentDescription = author.name,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(channelClick),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .then(channelClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (author?.name?.firstOrNull()?.toString() ?: "?").uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RowScope.ChannelName(
    author: Author?,
    channelClick: Modifier,
    sourceIconUrl: String? = null,
) {
    Column(
        modifier = Modifier.weight(1f).then(channelClick),
    ) {
        // Name + optional source icon on one line; the name keeps its
        // ellipsis by taking the remaining width.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = author?.name ?: "Unknown Channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (sourceIconUrl != null) {
                AsyncImage(
                    model = sourceIconUrl,
                    contentDescription = "Source",
                    modifier =
                        Modifier
                            .padding(start = 4.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        author?.subscriberCount?.takeIf { it > 0 }?.let { count ->
            Text(
                text = "${formatCompactCount(count)} subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatCompactCount(count: Long): String =
    when {
        count >= 1_000_000_000 ->
            java.lang.String.format(java.util.Locale.US, "%.1f", count / 1_000_000_000.0)
                .trimEnd('0', '.') +
                "B"

        count >= 1_000_000 ->
            java.lang.String.format(java.util.Locale.US, "%.1f", count / 1_000_000.0)
                .trimEnd('0', '.') +
                "M"

        count >= 1_000 ->
            java.lang.String.format(java.util.Locale.US, "%.1f", count / 1_000.0)
                .trimEnd('0', '.') +
                "K"

        else -> count.toString()
    }

@Composable
private fun SubscribeButton(
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    iconOnly: Boolean = false,
) {
    if (iconOnly) {
        IconButton(onClick = onSubscribe) {
            Icon(
                imageVector =
                    if (isSubscribed) Icons.Filled.Check else Icons.Filled.ThumbUp,
                contentDescription = if (isSubscribed) "Subscribed" else "Subscribe",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }
    // Subscribed state is visibly different: tonal outline vs filled.
    if (isSubscribed) {
        OutlinedButton(
            onClick = onSubscribe,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text(text = "Subscribed", style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Button(
            onClick = onSubscribe,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text(text = "Subscribe", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MoreButton(onMore: () -> Unit) {
    IconButton(onClick = onMore) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
