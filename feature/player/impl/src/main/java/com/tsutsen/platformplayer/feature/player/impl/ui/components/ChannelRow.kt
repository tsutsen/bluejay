package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tsutsen.platformplayer.core.model.Author

/**
 * Channel badge row: avatar, name, subscribe, like/dislike pill, view count
 * + published time, and the three-dot button that opens the video options
 * sheet. Tapping the avatar or name opens the channel page.
 */
@Composable
internal fun ChannelRow(
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
) {
    // Tapping the avatar or name opens the channel page.
    val channelUrl = author?.url
    val channelClick: Modifier =
        if (channelUrl != null && onChannelClick != null) {
            Modifier.clickable { onChannelClick(channelUrl) }
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
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

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f).then(channelClick),
        ) {
            Text(
                text = author?.name ?: "Unknown Channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "125K subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Subscribed state is visibly different: tonal outline vs filled.
        if (isSubscribed) {
            OutlinedButton(
                onClick = onSubscribe,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = "Subscribed",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Button(
                onClick = onSubscribe,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = "Subscribe",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        LikeDislikePill(
            likeCount = likeCount,
            isLiked = isLiked,
            onLike = onLike,
            dislikeCount = dislikeCount,
            isDisliked = isDisliked,
            onDislike = onDislike,
        )

        Spacer(modifier = Modifier.width(12.dp))

        val stats =
            buildList {
                if (viewCount != null) add("${formatViewCount(viewCount)} views")
                val ago = formatRelativeTime(publishedAt)
                if (ago.isNotEmpty()) add(ago)
            }.joinToString(" • ")
        if (stats.isNotEmpty()) {
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        // More — opens the video options sheet.
        IconButton(onClick = onMore) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
