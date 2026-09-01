package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import java.util.Locale

/**
 * Like | dislike action group — a native M3 Expressive [ButtonGroup]
 * (standard style: separated, fully-rounded buttons) with the icon and
 * count on each button. Active votes are tinted with the primary color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        clickableItem(
            onClick = onLike,
            label = likeCount?.let(::formatCount) ?: "",
            icon = {
                ThumbIcon(
                    icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    active = isLiked,
                    activeDescription = "Unlike",
                    idleDescription = "Like",
                    tint = if (isLiked) scheme.primary else scheme.onSurfaceVariant,
                )
            },
        )
        clickableItem(
            onClick = onDislike,
            label = dislikeCount?.let(::formatCount) ?: "",
            icon = {
                ThumbIcon(
                    icon = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    active = isDisliked,
                    activeDescription = "Remove dislike",
                    idleDescription = "Dislike",
                    tint = if (isDisliked) scheme.primary else scheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun ThumbIcon(
    icon: ImageVector,
    active: Boolean,
    activeDescription: String,
    idleDescription: String,
    tint: Color,
) {
    Icon(
        imageVector = icon,
        contentDescription = if (active) activeDescription else idleDescription,
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
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
