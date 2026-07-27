package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
internal fun CompactControlsRow(
    isPlaying: Boolean,
    isLooping: Boolean,
    onMinimize: () -> Unit,
    onPlayPause: () -> Unit,
    onChapters: () -> Unit,
    onLoopToggle: () -> Unit,
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
        IconButton(onClick = onLoopToggle) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Loop",
                tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
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
