package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.component.OptionTile
import com.tsutsen.platformplayer.core.designsystem.component.OptionTileView
import com.tsutsen.platformplayer.core.designsystem.component.TileTone
import com.tsutsen.platformplayer.core.model.AudioTrackInfo
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.VideoChapter
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsModal(
    playbackSpeed: Float,
    quality: String,
    qualities: List<Int>,
    subtitle: String,
    subtitles: List<String>,
    audioTracks: List<AudioTrackInfo> = emptyList(),
    selectedAudioTrack: String = "",
    loopMode: Int,
    isWatchLater: Boolean,
    downloadState: DownloadButtonState,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onAudioChange: (String) -> Unit,
    onLoopClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OptionCard(title = "Speed") {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            OptionCard(title = "Quality") {
                (listOf("Auto") + qualities.map { "${it}p" }).forEach { q ->
                    FilterChip(
                        selected = quality == q,
                        onClick = { onQualityChange(q) },
                        label = { Text(q) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            // A single track is the default — nothing to select.
            if (audioTracks.size > 1) {
                OptionCard(title = "Audio") {
                    audioTracks.forEach { track ->
                        FilterChip(
                            selected = track.label == selectedAudioTrack,
                            onClick = { onAudioChange(track.label) },
                            label = { Text(track.label) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            OptionCard(title = "Subtitles") {
                val subtitleOptions = if (subtitles.isEmpty()) listOf("Auto", "Off") else listOf("Off") + subtitles
                subtitleOptions.forEach { s ->
                    FilterChip(
                        selected = subtitle == s,
                        onClick = { onSubtitleChange(s) },
                        label = { Text(s) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            // Same tiles as the video card's options sheet — no card
            // wrapper, the tiles carry their own surface.
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionTileView(
                    tile =
                        OptionTile(
                            label = "Loop",
                            icon =
                                when (loopMode) {
                                    1 -> Icons.Filled.RepeatOne
                                    2 -> Icons.Filled.Repeat
                                    else -> Icons.Outlined.Repeat
                                },
                            selected = loopMode != 0,
                            onClick = onLoopClick,
                        ),
                    modifier = Modifier.weight(1f),
                )
                OptionTileView(
                    tile =
                        OptionTile(
                            label = "Watch later",
                            icon = Icons.Filled.History,
                            selected = isWatchLater,
                            onClick = onWatchLaterClick,
                        ),
                    modifier = Modifier.weight(1f),
                )
                OptionTileView(
                    tile =
                        OptionTile(
                            label =
                                when (downloadState) {
                                    is DownloadButtonState.Downloading -> "Stop download"
                                    is DownloadButtonState.Downloaded -> "Delete"
                                    is DownloadButtonState.Starting -> "Starting..."
                                    is DownloadButtonState.Idle -> "Download"
                                },
                            icon =
                                when (downloadState) {
                                    is DownloadButtonState.Downloading -> Icons.Filled.Stop
                                    is DownloadButtonState.Downloaded -> Icons.Filled.Delete
                                    else -> Icons.Filled.Download
                                },
                            tone =
                                when (downloadState) {
                                    is DownloadButtonState.Downloading -> TileTone.Warning
                                    is DownloadButtonState.Downloaded -> TileTone.Danger
                                    is DownloadButtonState.Starting -> TileTone.Highlight
                                    is DownloadButtonState.Idle -> TileTone.Default
                                },
                            progress = (downloadState as? DownloadButtonState.Downloading)?.progress,
                            indeterminate = downloadState is DownloadButtonState.Starting,
                            onClick = onDownload,
                        ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One options-sheet section: card with a fixed-width label on the left
 * (so the chip rows of every section align) and horizontally scrolling
 * controls on the right.
 */
@Composable
private fun OptionCard(
    title: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(76.dp),
        )
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersPanel(
    chapters: List<VideoChapter>,
    positionMs: StateFlow<Long>,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // Collected here — the panel is only composed while open.
    val currentPositionMs by positionMs.collectAsState(initial = positionMs.value)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn {
            itemsIndexed(chapters) { _, chapter ->
                val isSelected = currentPositionMs in chapter.startTimeMs..chapter.endTimeMs
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { onChapterClick(chapter.startTimeMs) }
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                MaterialTheme.shapes.medium,
                            ).padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(chapter.startTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
