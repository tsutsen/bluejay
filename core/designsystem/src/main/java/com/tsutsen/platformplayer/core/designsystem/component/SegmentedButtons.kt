package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens

/**
 * A row of equal segments where the selected one is a tonal pill — the
 * search tab's media/creators/playlists switcher.
 *
 * Deliberately a colour-only morph: the pill clip is static, so no shape
 * lerp can overshoot into negative corners (the reason M3's own
 * spring-driven shape morphs needed clamping). The colour animates with
 * M3's own colour duration.
 */
@Composable
fun SegmentedButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(CornerSize(100))
    Row(modifier) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "segment-$index",
            )
            val text by animateColorAsState(
                targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "segment-text-$index",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(Tokens.ButtonMd)
                        .background(background, pill)
                        .clickable { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    color = text,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
