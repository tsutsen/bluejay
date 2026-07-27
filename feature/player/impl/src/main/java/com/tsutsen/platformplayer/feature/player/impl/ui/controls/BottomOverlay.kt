package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

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
                    tint = Color.White
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
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
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
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
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
        Spacer(modifier = Modifier.height(4.dp))
        // Timeline below controls
        var isDragging by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }

        Slider(
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
            colors = SliderDefaults.colors(),
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        )
    }
}
