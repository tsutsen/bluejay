package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.feature.player.impl.PlayerUiState
import com.tsutsen.platformplayer.feature.player.impl.formatTime
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared compact control row used by [PlayerControls] for the mini player overlay.
 * Renders play/pause + close at the top, title + author + fullscreen at the bottom,
 * and a progress bar along the bottom edge.
 */
@Composable
internal fun PlayerFloatingOverlay(
    state: PlayerUiState.Loaded,
    positionMs: StateFlow<Long>,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
) {
    // Collected here — at the leaf — so the 10 Hz position ticks recompose
    // only this overlay, not the whole player screen.
    val currentPositionMs by positionMs.collectAsState(initial = positionMs.value)
    Column(modifier = Modifier.fillMaxWidth()) {
        // Play/pause + Close row (top)
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Title + author + Fullscreen row (bottom)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentVideo?.title ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val authorName = state.currentVideo?.author?.name
                if (!authorName.isNullOrEmpty()) {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Progress bar
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            LinearProgressIndicator(
                progress = if (state.durationMs > 0) currentPositionMs.toFloat() / state.durationMs else 0f,
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}
