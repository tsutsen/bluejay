package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

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
                            else androidx.compose.ui.graphics.Color.Transparent,
                            MaterialTheme.shapes.medium
                        )
                        .padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
