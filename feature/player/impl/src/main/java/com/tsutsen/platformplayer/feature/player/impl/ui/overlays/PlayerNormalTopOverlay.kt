package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerNormalTopOverlay(
    title: String,
    channelName: String,
    onMinimize: () -> Unit,
    // Loop mode: 0 = off, 1 = repeat once, 2 = repeat indefinitely.
    loopMode: Int = 0,
    onLoopMode: () -> Unit,
    onWatchLater: () -> Unit,
    isWatchLater: Boolean = false,
    onQueue: () -> Unit,
    onOptions: () -> Unit,
    onCast: (() -> Unit)? = null,
) {
    // In fullscreen landscape the bar rides the screen's top edge, under the
    // camera cutout (layoutInDisplayCutoutMode=shortEdges). displayCutout
    // insets are nonzero on an edge only when the cutout touches it, so this
    // shifts a corner-cutout bar clear of the hole and is a no-op elsewhere.
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = 16.dp,
                    start = 16.dp + cutout.calculateStartPadding(layoutDirection),
                    end = 16.dp + cutout.calculateEndPadding(layoutDirection),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize",
                tint = Color.White,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channelName,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onLoopMode) {
            val (icon, label) =
                when (loopMode) {
                    1 -> Icons.Filled.RepeatOne to "Loop once"
                    2 -> Icons.Filled.Repeat to "Loop indefinitely"
                    else -> Icons.Outlined.Repeat to "Do not loop"
                }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (loopMode == 0) Color.White.copy(alpha = 0.6f) else Color.White,
            )
        }
        IconButton(onClick = onWatchLater) {
            Icon(
                imageVector =
                    if (isWatchLater) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                contentDescription = "Watch Later",
                tint = if (isWatchLater) Color.White else Color.White.copy(alpha = 0.6f),
            )
        }
        IconButton(onClick = onQueue) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = "Queue",
                tint = Color.White,
            )
        }
        if (onCast != null) {
            IconButton(onClick = onCast) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = "Cast",
                    tint = Color.White,
                )
            }
        }
        IconButton(onClick = onOptions) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Options",
                tint = Color.White,
            )
        }
    }
}
