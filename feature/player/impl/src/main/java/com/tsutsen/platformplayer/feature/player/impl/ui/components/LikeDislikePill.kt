package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.GroupedButton
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Two-part button group: like | dislike. Two [GroupedButton]s (asymmetric
 * outer rounding) meeting on a flat thin divider, so the junction is a
 * straight seam rather than two overlapping pills.
 */
@Composable
internal fun LikeDislikePill(
    likeCount: Long?,
    isLiked: Boolean,
    onLike: () -> Unit,
    dislikeCount: Long?,
    isDisliked: Boolean,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        GroupedButton(
            position = GroupPosition.First,
            onClick = onLike,
        ) {
            PillHalf(
                icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                count = likeCount,
                active = isLiked,
                contentDescription = if (isLiked) "Unlike" else "Like",
            )
        }
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.25f)),
        )
        GroupedButton(
            position = GroupPosition.Last,
            onClick = onDislike,
        ) {
            PillHalf(
                icon = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                count = dislikeCount,
                active = isDisliked,
                contentDescription = if (isDisliked) "Remove dislike" else "Dislike",
            )
        }
    }
}

@Composable
private fun PillHalf(
    icon: ImageVector,
    count: Long?,
    active: Boolean,
    contentDescription: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .height(36.dp)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.bodySmall,
                color = if (active) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 ->
            String.format(Locale.getDefault(), "%.1fM", count / 1_000_000.0)
        count >= 1_000 ->
            String.format(Locale.getDefault(), "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
