package com.tsutsen.platformplayer.core.designsystem.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.core.ui.AsyncImage

/**
 * Persistent "Downloading" widget for the top of the Feed: active (not
 * complete) downloads with a live progress bar each. Empty list → nothing.
 * [onRemove] cancels a download (by url); pass null to hide the cancel.
 */
@Composable
fun DownloadStripCard(
    downloads: List<DownloadInfo>,
    onRemove: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (downloads.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        shape = RoundedCornerShape(BluejayTokens().radius.md),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Downloading",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    downloads.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            val shown = downloads.take(5)
            shown.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .width(68.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(BluejayTokens().radius.xs)),
                    ) {
                        AsyncImage(
                            url = d.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { d.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = scheme.primary,
                            trackColor = scheme.surfaceContainerHighest,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (d.progress <= 0f) "Preparing…" else "${(d.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (onRemove != null) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { onRemove(d.url) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                "Cancel download",
                                modifier = Modifier.size(18.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (i < shown.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}
