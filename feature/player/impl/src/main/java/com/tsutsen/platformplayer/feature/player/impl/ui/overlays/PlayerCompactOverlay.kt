package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
internal fun PlayerCompactOverlay(
    isPlaying: Boolean,
    subtitlesOn: Boolean,
    onMinimize: () -> Unit,
    onPlayPause: () -> Unit,
    onChapters: () -> Unit,
    onSubtitleToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onOptions: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.0f),
                        Color.Black.copy(alpha = 0.8f),
                        Color.Black.copy(alpha = 0.0f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = Color.White
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
                    tint = Color.White
                )
            }
            IconButton(onClick = onSubtitleToggle) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = "Subtitles",
                    tint = if (subtitlesOn) MaterialTheme.colorScheme.primary else Color.White
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
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}
