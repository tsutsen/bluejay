package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.tsutsen.platformplayer.core.designsystem.component.GroupCornerShapes
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.connectedGroupShapes
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens

/**
 * Like/dislike vote group, built on the native M3 [ButtonGroup] so it gets the
 * expressive connected-group behaviour for free: on press the pressed button
 * expands while the neighbour compresses ([ToggleButton] inside a
 * `customItem` + [androidx.compose.material3.ButtonGroupScope.animateWidth]).
 *
 * Container colors follow the M3 connected-button semantics: the active
 * button is filled (primaryContainer), the inactive one a neutral tonal
 * container — so the state change is visible on the button, not just the
 * icon. The icon itself inherits the content color (primary / onSurface
 * tints), never a fixed color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikeDislikePill(
    likeCount: Long?,
    isLiked: Boolean,
    onLike: () -> Unit,
    dislikeCount: Long?,
    isDisliked: Boolean,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radius = BluejayTokens().radius
    // The ButtonGroup scope is not a composable context, so everything a
    // composable call needs is computed here and passed into the item.
    val likeSource = remember { MutableInteractionSource() }
    val dislikeSource = remember { MutableInteractionSource() }

    ButtonGroup(
        overflowIndicator = { _: ButtonGroupMenuState -> },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXxs),
    ) {
        VoteItem(
            checked = isLiked,
            count = likeCount,
            onCheckedChange = onLike,
            activeIcon = Icons.Filled.ThumbUp,
            inactiveIcon = Icons.Outlined.ThumbUp,
            description = if (isLiked) "Unlike" else "Like",
            shapes = connectedGroupShapes(GroupPosition.First, radius),
            interactionSource = likeSource,
        )
        VoteItem(
            checked = isDisliked,
            count = dislikeCount,
            onCheckedChange = onDislike,
            activeIcon = Icons.Filled.ThumbDown,
            inactiveIcon = Icons.Outlined.ThumbDown,
            description = if (isDisliked) "Remove dislike" else "Dislike",
            shapes = connectedGroupShapes(GroupPosition.Last, radius),
            interactionSource = dislikeSource,
        )
    }
}

/**
 * Registers one connected toggle button in the group. Non-composable on
 * purpose: the ButtonGroup scope itself is not a composable context, only
 * the item's content lambda is.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun ButtonGroupScope.VoteItem(
    checked: Boolean,
    count: Long?,
    onCheckedChange: () -> Unit,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    description: String,
    shapes: GroupCornerShapes,
    interactionSource: MutableInteractionSource,
) {
    customItem(
        buttonGroupContent = {
            val scheme = MaterialTheme.colorScheme
            ToggleButton(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                shapes = ToggleButtonShapes(shapes.shape, shapes.pressedShape, shapes.checkedShape),
                modifier =
                    Modifier
                        .height(Tokens.ButtonSm)
                        .animateWidth(interactionSource),
                contentPadding =
                    PaddingValues(horizontal = Tokens.SpaceLg),
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurfaceVariant,
                        checkedContainerColor = scheme.primaryContainer,
                        checkedContentColor = scheme.onPrimaryContainer,
                    ),
                interactionSource = interactionSource,
            ) {
                Icon(
                    imageVector = if (checked) activeIcon else inactiveIcon,
                    contentDescription = description,
                    modifier = Modifier.size(Tokens.IconXs),
                )
                val countText = count?.let(::formatCount)
                if (countText != null) {
                    Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        },
        menuContent = {},
    )
}

private fun formatCount(count: Long): String =
    when {
        count >= 1_000_000 -> "${(count / 1_000_000.0).let { String.format("%.1f", it).trimEnd('0').trimEnd('.') }}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
