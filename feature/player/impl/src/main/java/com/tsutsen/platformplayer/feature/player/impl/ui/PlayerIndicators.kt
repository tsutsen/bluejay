package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureAnimationConstants
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator
import kotlinx.coroutines.delay

private const val INDICATOR_ANIM_MS = GestureAnimationConstants.INDICATOR_ANIM_MS
private const val INDICATOR_HIDE_DELAY_MS = GestureAnimationConstants.INDICATOR_HIDE_DELAY_MS

/**
 * State for the centre text badge (seek, speed, etc).
 * [showAt] is a monotonically increasing token; changing it triggers a fresh fade cycle.
 */
data class GestureBadgeState(
    val label: String = "",
    val icon: ImageVector = Icons.Default.Replay10,
    val showAt: Long = 0L,
)

/**
 * Unified gesture indicator overlay.
 *
 * - Progress indicators (brightness/volume sliders) rendered from [activeProgressIndicator].
 * - Centre text badge rendered from [badgeState] — always composed, alpha-animated.
 */
@Composable
internal fun GestureIndicatorOverlay(
    activeProgressIndicator: GestureIndicator.Progress?,
    badgeState: GestureBadgeState,
) {
    val badgeAlpha = remember { Animatable(0f) }

    // Each new showAt token triggers a fresh fade-in → delay → fade-out cycle.
    LaunchedEffect(badgeState.showAt) {
        if (badgeState.showAt > 0L) {
            badgeAlpha.snapTo(0f)
            badgeAlpha.animateTo(1f, animationSpec = tween(INDICATOR_ANIM_MS))
            delay(INDICATOR_HIDE_DELAY_MS)
            badgeAlpha.animateTo(0f, animationSpec = tween(INDICATOR_ANIM_MS))
        }
    }

    // Progress indicator (brightness/volume — side bars)
    activeProgressIndicator?.let { indicator ->
        val alignment = if (indicator.key == "brightness") Alignment.CenterStart else Alignment.CenterEnd
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
            Box(modifier = Modifier.fillMaxHeight().width(120.dp)) {
                ProgressIndicator(
                    value = indicator.value,
                    icon = indicator.icon,
                    format = indicator.format,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // Centre text badge — always composed, alpha-driven
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = badgeAlpha.value },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = badgeState.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badgeState.label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    value: Float,
    icon: ImageVector,
    format: (Float) -> String = { "${(it * 100).toInt()}%" },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(100.dp)
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = format(value),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
