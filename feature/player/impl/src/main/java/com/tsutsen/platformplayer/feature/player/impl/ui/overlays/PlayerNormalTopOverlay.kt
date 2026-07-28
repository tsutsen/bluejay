package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerNormalTopOverlay(
    title: String,
    channelName: String,
    onMinimize: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize",
                tint = Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channelName,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onReplayToggle) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Replay",
                tint = Color.White
            )
        }
        IconButton(onClick = onWatchLater) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Watch Later",
                tint = Color.White
            )
        }
        IconButton(onClick = onOptions) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Options",
                tint = Color.White
            )
        }
    }
}
