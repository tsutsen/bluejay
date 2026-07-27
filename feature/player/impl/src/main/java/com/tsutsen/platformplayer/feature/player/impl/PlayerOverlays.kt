package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

@Composable
internal fun BottomOverlay(
    player: androidx.media3.common.Player?,
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
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

@Composable
internal fun BrightnessIndicator(
    brightness: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BrightnessHigh,
            contentDescription = "Brightness",
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
                    .fillMaxHeight(brightness)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(brightness * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun VolumeIndicator(
    volume: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Volume",
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
                    .fillMaxHeight(volume)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsModal(
    playbackSpeed: Float,
    quality: String,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed}x") }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Quality",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            listOf("Auto", "1080p", "720p", "480p", "360p").forEach { q ->
                FilterChip(
                    selected = quality == q,
                    onClick = { onQualityChange(q) },
                    label = { Text(q) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersPanel(
    chapters: List<Chapter>,
    currentPositionMs: Long,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            itemsIndexed(chapters) { _, chapter ->
                val isSelected = currentPositionMs in chapter.startTimeMs..chapter.endTimeMs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            MaterialTheme.shapes.medium
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(chapter.startTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
