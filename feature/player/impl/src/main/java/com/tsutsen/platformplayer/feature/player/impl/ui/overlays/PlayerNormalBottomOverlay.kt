package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tsutsen.platformplayer.core.model.VideoChapter
import com.tsutsen.platformplayer.feature.player.impl.formatTime

@Composable
internal fun PlayerNormalBottomOverlay(
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
    scrubPositionMs: Long = currentPositionMs,
    chapters: List<VideoChapter> = emptyList(),
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        // Controls row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${formatTime(if (isScrubbing) scrubPositionMs else currentPositionMs)} / ${formatTime(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Timeline
        var isDragging by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }

        Box {
            // Chapter boundary ticks above the track (skipping the leading edge)
            if (chapters.isNotEmpty() && durationMs > 0) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .drawBehind {
                                val tickWidth = 2.dp.toPx()
                                val tickHeight = 8.dp.toPx()
                                val cy = size.height / 2f
                                chapters.forEach { chapter ->
                                    val fraction =
                                        (chapter.startTimeMs.toFloat() / durationMs)
                                            .coerceIn(0f, 1f)
                                    if (fraction <= 0.001f) return@forEach
                                    val x = fraction * size.width - tickWidth / 2f
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.8f),
                                        topLeft = Offset(x, cy - tickHeight / 2f),
                                        size = Size(tickWidth, tickHeight),
                                    )
                                }
                            },
                )
            }
            Slider(
                value = if (isDragging) seekPosition else (if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f),
                onValueChange = {
                    isDragging = true
                    seekPosition = it
                    val seekToMs = (it * durationMs).toLong()
                    onSeek(seekToMs)
                },
                onValueChangeFinished = {
                    val seekToMs = (seekPosition * durationMs).toLong()
                    onSeek(seekToMs)
                    isDragging = false
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(),
                interactionSource = remember { MutableInteractionSource() },
            )
        }
    }
}
