package com.tsutsen.platformplayer.core.designsystem.component

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Long-press options for a playlist card (local playlists).
 *
 * Same leaf pattern as [VideoOptionsSheet]: title on top, a metadata row
 * below (video count + total duration), then a row of action tiles —
 * Play (first video) and Delete playlist.
 */
@Composable
fun PlaylistOptionsSheet(
    title: String,
    videoCount: Int? = null,
    totalDurationMs: Long? = null,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    GrayjayModalBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceSm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val stats =
                buildList {
                    videoCount?.let { add("$it ${if (it == 1) "video" else "videos"}") }
                    totalDurationMs?.takeIf { it > 0 }?.let { add(formatTotalDuration(it)) }
                }
            if (stats.isNotEmpty()) {
                Text(
                    text = stats.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.SpaceMd, vertical = Tokens.SpaceXs),
        ) {
            OptionTileView(
                tile =
                    OptionTile(
                        label = "Play",
                        icon = Icons.Filled.PlayArrow,
                        onClick = {
                            onPlay()
                            onDismiss()
                        },
                    ),
                modifier = Modifier.weight(1f),
            )
            OptionTileView(
                tile =
                    OptionTile(
                        label = "Delete playlist",
                        icon = Icons.Filled.Delete,
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                    ),
                modifier = Modifier.weight(1f),
                iconTint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** "3725s -> 1h 2m", "745s -> 12m 25s" (totals, unlike formatDuration). */
private fun formatTotalDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
}
