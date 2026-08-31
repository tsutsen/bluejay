package com.tsutsen.platformplayer.core.designsystem.component

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.LocalSemanticColors
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.DownloadQuality
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.ui.RelativeTime

/**
 * Long-press options for a video card.
 *
 * Leaf component: receives state (saved flags, playlists, video metadata)
 * and action lambdas; the screen wires them to PlayerViewModel / DAOs /
 * Navigator. "Play next" / "Add to queue" intentionally absent until the
 * player queue exists (PlayerViewModel still has TODOs for queue
 * navigation).
 *
 * Layout: title + metadata row (views • duration • posted), then a grid of
 * action tiles (3 columns) so all actions fit without scrolling. The
 * "Add to playlist" tile toggles the playlist picker section.
 *
 * Share (Intent.ACTION_SEND) is handled internally so every call site gets
 * identical behaviour.
 */
@Composable
fun VideoOptionsSheet(
    url: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onGoToChannel: (String) -> Unit,
    onToggleWatchLater: () -> Unit,
    onToggleLiked: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDownload: () -> Unit,
    // Long-press the download tile to pick a quality; null hides the
    // long-press (hosts that don't offer quality selection).
    onDownloadWithQuality: ((DownloadQuality) -> Unit)? = null,
    onAddToPlaylist: (Long?) -> Unit,
    onAddToQueue: () -> Unit = {},
    // Queue state: the tile becomes a highlighted "Remove from queue" when
    // the video is already queued, and a dimmed "Now playing" when it is
    // the current video.
    isInQueue: Boolean = false,
    isCurrentlyPlaying: Boolean = false,
    onRemoveFromQueue: () -> Unit = {},
    downloadState: DownloadButtonState = DownloadButtonState.Idle,
    isWatchLaterSaved: Boolean = false,
    isLikedSaved: Boolean = false,
    isFavouriteSaved: Boolean = false,
    playlists: List<PlaylistOption> = emptyList(),
    authorUrl: String? = null,
    title: String? = null,
    durationMs: Long? = null,
    viewCount: Long? = null,
    publishedAt: Long? = null,
    // Playlists already containing this video — their boxes start checked.
    containedPlaylistIds: Set<Long> = emptySet(),
    // Auto-commit: checked = add the video, unchecked = remove it.
    onTogglePlaylist: (Long, Boolean) -> Unit = { _, _ -> },
    // false (default): modal material3 ModalBottomSheet (main app).
    // true: render the body bare — the host wraps it in its own sheet chrome
    // (the second screen uses a material3 BottomSheetScaffold, which cannot
    // host a Popup-based modal sheet).
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    var showPlaylists by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    // Snapshot of the (recency-sorted) list taken when the section opens.
    // The live list re-sorts on every add/remove (updatedAt bump), which
    // made rows jump under the finger while a box was being checked. Pin
    // the order while the section is open; re-snapshot on each open.
    var pinnedPlaylists by remember { mutableStateOf<List<PlaylistOption>?>(null) }
    // Local mirror of the contained set, seeded on open and updated
    // optimistically on tap: the box flips in the same frame as the tap
    // (a live-DB-only bind lags a frame behind the re-flow, reading as
    // "nothing happens"), while the add/remove persists in the background.
    var checkedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val body: @Composable () -> Unit = {
        // Header: title, then the metadata row underneath (title and stats
        // never fit on one line at phone widths).
        val stats =
            buildList {
                viewCount?.takeIf { it > 0 }?.let { add("${formatViewCount(it)} views") }
                durationMs?.takeIf { it > 0 }?.let { add(formatDuration(it)) }
                publishedAt?.let { add(RelativeTime.format(it)) }
            }
        if (title != null || stats.isNotEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
        }

        // Action tiles: 3 columns, unrolled rows (max 7 tiles = 3 rows).
        val tiles =
            buildList {
                add(
                    OptionTile(
                        label = "Play",
                        icon = Icons.Filled.PlayArrow,
                        onClick = {
                            onPlay()
                            onDismiss()
                        },
                    ),
                )
                add(
                    OptionTile(
                        label = "Watch later",
                        icon = Icons.Filled.History,
                        selected = isWatchLaterSaved,
                        onClick = onToggleWatchLater,
                    ),
                )
                add(
                    OptionTile(
                        label = "Like",
                        icon = if (isLikedSaved) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        selected = isLikedSaved,
                        onClick = onToggleLiked,
                    ),
                )
                add(
                    OptionTile(
                        label = "Favourite",
                        icon = if (isFavouriteSaved) Icons.Filled.Star else Icons.Filled.StarBorder,
                        selected = isFavouriteSaved,
                        onClick = onToggleFavourite,
                    ),
                )
                add(
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
                        onLongClick =
                            if (downloadState is DownloadButtonState.Idle && onDownloadWithQuality != null) {
                                { showQualityDialog = true }
                            } else {
                                null
                            },
                    ),
                )
                add(
                    OptionTile(
                        label = "Add to playlist",
                        icon = Icons.Filled.HistoryEdu,
                        selected = showPlaylists,
                        onClick = {
                            if (showPlaylists) {
                                showPlaylists = false
                                pinnedPlaylists = null
                            } else {
                                pinnedPlaylists = playlists
                                checkedIds = containedPlaylistIds
                                showPlaylists = true
                            }
                        },
                    ),
                )
                add(
                    OptionTile(
                        label =
                            when {
                                isCurrentlyPlaying -> "Now playing"
                                isInQueue -> "Remove from queue"
                                else -> "Add to queue"
                            },
                        icon = Icons.Filled.QueueMusic,
                        tone =
                            if (isInQueue) TileTone.Danger else TileTone.Default,
                        disabled = isCurrentlyPlaying,
                        onClick = {
                            if (isInQueue) onRemoveFromQueue() else onAddToQueue()
                        },
                    ),
                )
                add(
                    OptionTile(
                        label = "Share",
                        icon = Icons.Filled.Share,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                            context.startActivity(
                                Intent.createChooser(intent, "Share video"),
                            )
                            onDismiss()
                        },
                    ),
                )
                authorUrl?.let {
                    add(
                        OptionTile(
                            label = "Go to channel",
                            icon = Icons.Filled.Public,
                            onClick = {
                                onGoToChannel(it)
                                onDismiss()
                            },
                        ),
                    )
                }
            }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            optionTileRow(tiles, 0)
            optionTileRow(tiles, 3)
            optionTileRow(tiles, 6)
            // ponytail: 3 unrolled rows cap the grid at 9 tiles; add a row
            // here if a 10th action is ever added.
        }

        // Playlist picker: revealed by the "Add to playlist" tile. Toggles
        // auto-commit — checking a box adds the video, unchecking removes
        // it (the contained set is live, so the boxes stay in sync).
        AnimatedVisibility(
            visible = showPlaylists,
            enter = expandVertically(clip = true) + fadeIn(),
            exit = shrinkVertically(clip = true) + fadeOut(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "Add this item",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                (pinnedPlaylists ?: playlists).forEach { playlist ->
                    playlistCheckRow(
                        label = playlist.name,
                        checked = playlist.id in checkedIds,
                        onCheckedChange = { checked ->
                            checkedIds =
                                if (checked) {
                                    checkedIds + playlist.id
                                } else {
                                    checkedIds - playlist.id
                                }
                            onTogglePlaylist(playlist.id, checked)
                        },
                    )
                }
                newPlaylistRow {
                    // No onDismiss here: the host shows a create dialog on
                    // top of this sheet. Dismissing first would unmount the
                    // dialog.
                    onAddToPlaylist(null)
                }
            }
        }
    }
    if (embedded) {
        body()
    } else {
        BluejayModalBottomSheet(onDismiss = onDismiss, content = body)
    }
    if (showQualityDialog) {
        DownloadQualityDialog(
            onDismiss = { showQualityDialog = false },
            onConfirm = { q ->
                showQualityDialog = false
                onDownloadWithQuality?.invoke(q)
            },
        )
    }
}

@Composable
private fun DownloadQualityDialog(
    onDismiss: () -> Unit,
    onConfirm: (DownloadQuality) -> Unit,
) {
    var selected by remember { mutableStateOf(DownloadQuality.Default) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download quality") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DownloadQuality.Options.forEach { q ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Tokens.RadiusMd))
                                .clickable { selected = q }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == q,
                            onClick = { selected = q },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = q.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun optionTileRow(
    tiles: List<OptionTile>,
    start: Int,
) {
    if (start >= tiles.size) return
    Row(modifier = Modifier.fillMaxWidth()) {
        OptionTileView(
            tile = tiles[start],
            modifier = Modifier.weight(1f),
        )
        if (start + 1 < tiles.size) {
            OptionTileView(
                tile = tiles[start + 1],
                modifier = Modifier.weight(1f),
            )
        }
        if (start + 2 < tiles.size) {
            OptionTileView(
                tile = tiles[start + 2],
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Public: PlaylistOptionsSheet and the companion (second screen) activity
// reuse the same tile.
@Composable
fun OptionTileView(
    tile: OptionTile,
    modifier: Modifier = Modifier,
    // Null = default tint (selected-aware). Non-null overrides the icon
    // tint only (e.g. error color for a destructive action).
    iconTint: Color? = null,
    // false = icon only (e.g. the second screen's playback controls, where
    // the icon alone is self-evident).
    showLabel: Boolean = true,
    outerPadding: Dp = Tokens.SpaceXs,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val active = tile.progress != null || tile.indeterminate
    // `selected` (saved toggles) shares the highlight tone. Tone drives
    // the background/content: danger = M3 error*, warning = semantic
    // yellow, highlight = M3 primaryContainer.
    val highlighted = tile.selected || tile.tone == TileTone.Highlight
    val (bg, content, iconColor) =
        when {
            tile.disabled -> {
                Triple(
                    scheme.surfaceContainer,
                    scheme.onSurface.copy(alpha = 0.4f),
                    scheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            highlighted -> {
                Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.onPrimaryContainer)
            }

            tile.tone == TileTone.Danger -> {
                Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.onErrorContainer)
            }

            tile.tone == TileTone.Warning -> {
                Triple(semantic.warning, semantic.onWarning, semantic.onWarning)
            }

            else -> {
                Triple(scheme.surfaceContainer, scheme.onSurface, scheme.onSurfaceVariant)
            }
        }
    val clickModifier =
        if (tile.onLongClick != null) {
            Modifier.combinedClickable(
                enabled = !tile.disabled,
                onClick = tile.onClick,
                onLongClick = {
                    tile.onLongClick?.invoke()
                    true
                },
            )
        } else {
            Modifier.clickable(enabled = !tile.disabled, onClick = tile.onClick)
        }
    Column(
        modifier =
            modifier
                .padding(outerPadding)
                .clip(
                    RoundedCornerShape(Tokens.RadiusMd),
                ).background(
                    bg,
                ).then(clickModifier)
                .padding(vertical = Tokens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconMd),
            tint = iconTint ?: iconColor,
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (active) {
            val barModifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp)
                    .height(3.dp)
            val progress = tile.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = content,
                    modifier = barModifier,
                )
            } else {
                // no progress lambda = indeterminate bar ("Starting...")
                LinearProgressIndicator(color = content, modifier = barModifier)
            }
        }
    }
}

@Composable
private fun playlistCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    playlistRow(label, onClick = { onCheckedChange(!checked) }) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun newPlaylistRow(onClick: () -> Unit) {
    // Same row shape as the checkbox rows; the + icon sits in the same
    // 48dp slot a checkbox occupies, so heights line up.
    playlistRow("New playlist", onClick = onClick) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New playlist",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun playlistRow(
    label: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(Tokens.RadiusMd))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

// Public: PlaylistOptionsSheet and the companion (second screen) activity
// reuse the same tile.
data class OptionTile(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
    val tone: TileTone = TileTone.Default,
    /** Determinate progress 0..1; null = no bar (or indeterminate). */
    val progress: Float? = null,
    /** Show an indeterminate bar (e.g. "Starting..."). */
    val indeterminate: Boolean = false,
    /** Dimmed, clicks ignored. */
    val disabled: Boolean = false,
    /** Optional long-press action (e.g. pick a download quality). */
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Semantic emphasis for a tile. Danger maps to the M3 error* roles,
 * highlight to primaryContainer, warning to [LocalSemanticColors]
 * (the only semantic role M3 does not provide).
 */
enum class TileTone {
    Default,
    Highlight,
    Warning,
    Danger,
}
