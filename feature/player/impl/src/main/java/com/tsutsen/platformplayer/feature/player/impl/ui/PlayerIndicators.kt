package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator

@Composable
internal fun BrightnessIndicator(
    brightness: Float,
    modifier: Modifier = Modifier
) {
    ProgressIndicator(
        value = brightness,
        icon = Icons.Default.BrightnessHigh,
        modifier = modifier
    )
}

@Composable
internal fun VolumeIndicator(
    volume: Float,
    modifier: Modifier = Modifier
) {
    ProgressIndicator(
        value = volume,
        icon = Icons.Default.VolumeUp,
        modifier = modifier
    )
}

@Composable
internal fun SeekIndicators(
    showSeekBack: Boolean,
    showSeekForward: Boolean
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        if (showSeekBack) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Seek back 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        if (showSeekForward) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Seek forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private const val INDICATOR_ANIM_MS = 200

/**
 * Unified gesture indicator overlay with fade-in / fade-out animations.
 *
 * [AnimatedVisibility.updateTo] cross-fades when the indicator reference changes,
 * so rapid updates (e.g. consecutive swipe frames or double-tap seeks) produce
 * a smooth visual transition instead of a hard cut.
 * Handles [GestureIndicator.None] and null by rendering nothing.
 */
@Composable
internal fun GestureIndicatorOverlay(
    targetIndicator: GestureIndicator?,
) {
    AnimatedVisibility(
        visible = targetIndicator != null && targetIndicator != GestureIndicator.None,
        enter = fadeIn(animationSpec = tween(INDICATOR_ANIM_MS)),
        exit = fadeOut(animationSpec = tween(INDICATOR_ANIM_MS)),
    ) {
        val indicator = targetIndicator!! // safe: guarded by visible
        when (indicator) {
            is GestureIndicator.Progress -> {
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
            is GestureIndicator.Badge -> {
                BadgeIndicatorOverlay(label = indicator.format(indicator.value), icon = indicator.icon)
            }
            is GestureIndicator.TextBadge -> {
                BadgeIndicatorOverlay(label = indicator.label, icon = indicator.icon)
            }
            else -> Unit
        }
    }
}

@Composable
private fun BadgeIndicatorOverlay(label: String, icon: ImageVector) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
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
