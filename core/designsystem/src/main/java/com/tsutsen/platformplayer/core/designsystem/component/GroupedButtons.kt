package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens

/**
 * One clickable segment of an adjacent button group. Corner rounding
 * depends on [position] so a Row of them reads as a single control —
 * the same asymmetric-rounding pattern as the settings card groups.
 */
@Composable
fun GroupedButton(
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit,
) {
    val r = BluejayTokens().radius
    val shape =
        when (position) {
            GroupPosition.Single -> RoundedCornerShape(r.md)
            GroupPosition.First -> RoundedCornerShape(topStart = r.md, bottomStart = r.md)
            GroupPosition.Middle -> RoundedCornerShape(0.dp)
            GroupPosition.Last -> RoundedCornerShape(topEnd = r.md, bottomEnd = r.md)
        }
    Row(
        modifier =
            modifier
                .background(containerColor, shape)
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        content()
    }
}
