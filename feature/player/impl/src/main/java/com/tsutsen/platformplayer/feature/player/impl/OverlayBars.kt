package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Composable
internal fun TopOverlay(
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
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channelName,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
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
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onWatchLater) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Watch Later",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onOptions) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Options",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

@Composable
internal fun BottomOverlay(
    player: Player?,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreen: () -> Unit,
    onSeek: (Long) -> Unit = {},
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = currentPositionMs
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Controls row first
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Time display before chapters - wrapped to match IconButton height for alignment
            Box(
                modifier = Modifier.height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${formatTime(if (isScrubbing) scrubPositionMs else currentPositionMs)} / ${formatTime(durationMs)}",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Timeline below controls
        var isDragging by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }

        androidx.compose.material3.Slider(
            value = if (isDragging) seekPosition else (if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f),
            onValueChange = {
                isDragging = true
                seekPosition = it
                // Update scrub position for display
                val seekToMs = (it * durationMs).toLong()
                onSeek(seekToMs)
            },
            onValueChangeFinished = {
                // Commit the seek
                val seekToMs = (seekPosition * durationMs).toLong()
                onSeek(seekToMs)
                isDragging = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = durationMs > 0,
            colors = androidx.compose.material3.SliderDefaults.colors(),
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        )
    }
}

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onChapters) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = "Chapters",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onLoopToggle) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Loop",
                tint = if (isLooping) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onWatchLater) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Watch Later",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onOptions) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Options",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        IconButton(onClick = onFullscreen) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}
