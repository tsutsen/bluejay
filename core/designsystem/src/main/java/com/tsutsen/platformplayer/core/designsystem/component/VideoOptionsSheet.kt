package com.tsutsen.platformplayer.core.designsystem.component

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.LocalSemanticColors
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.spatialSpec
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
    // Starts a download at the picked quality (the segmented control in
    // the [DownloadSection]); null = segments fall back to [onDownload].
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
            // Last row: the remaining tiles, then the download group takes
            // the rest of the row and matches the tiles' height, so it reads
            // as part of the grid instead of a lone row underneath.
            // ponytail: 3 unrolled rows cap the grid at 9 tiles; add a row
            // here if a 10th action is ever added.
            val remainingTiles = (tiles.size - 6).coerceIn(0, 2)
            Row(modifier = Modifier.fillMaxWidth()) {
                if (tiles.size > 6) {
                    OptionTileView(
                        tile = tiles[6],
                        modifier = Modifier.weight(1f),
                    )
                }
                if (tiles.size > 7) {
                    OptionTileView(
                        tile = tiles[7],
                        modifier = Modifier.weight(1f),
                    )
                }
                // The group sits in the same slot a tile occupies: the
                // tile's outer gap around it, and its natural height (the
                // segments mirror the tile's content stack) makes it
                // exactly one tile tall — no measurement needed.
                Box(
                    modifier =
                        Modifier
                            .weight(3f - remainingTiles)
                            .padding(Tokens.SpaceXs)
                            .height(IntrinsicSize.Min),
                ) {
                    DownloadSection(
                        state = downloadState,
                        onDownload = onDownload,
                        onDownloadWithQuality = onDownloadWithQuality,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
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

/**
 * Corner treatment for tiles joined into a connected group (the M3 split
 * button): [ConnectedLeading] squares the END (inner) corners, [ConnectedTrailing]
 * squares the START (inner) corners, so two tiles side by side read as one
 * component with a seam. Outer corners keep the full animated rounding.
 */
enum class TileConnection { Standalone, ConnectedLeading, ConnectedTrailing }

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
    outerHPadding: Dp = Tokens.SpaceXs,
    outerVPadding: Dp = Tokens.SpaceXs,
    connection: TileConnection = TileConnection.Standalone,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val radius = BluejayTokens().radius
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val enabled = !tile.disabled
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
    // Expressive press, the M3 button rules: the container darkens by
    // overlaying 12% of its own content color (flips direction in dark
    // theme), the corners squish inward, and the whole tile scales — each
    // on its own animated track so state changes and presses compose.
    // Toggled (selected) tiles take the big corner rounding, so the state
    // reads in the shape as well as the color.
    val pressBg = lerp(bg, content, 0.12f)
    val scale by
        animateFloatAsState(
            targetValue = if (pressed && enabled) 1.04f else 1f,
            animationSpec = spatialSpec<Float>(),
            label = "tile-press-scale",
        )
    val cornerPx by
        animateFloatAsState(
            targetValue =
                when {
                    highlighted -> radius.lg.value
                    pressed && enabled -> radius.sm.value
                    else -> radius.md.value
                },
            animationSpec = spatialSpec<Float>(),
            label = "tile-corner",
        )
    // Clamped: the spring overshoots below 0 when the radius scale
    // collapses (rounding 0), and a negative corner is fatal.
    val cornerDp = cornerPx.coerceAtLeast(0f).dp
    val corner =
        when (connection) {
            TileConnection.Standalone -> RoundedCornerShape(cornerDp)

            // Inner (facing) corners stay square — they form the seam.
            TileConnection.ConnectedLeading ->
                RoundedCornerShape(
                    topStart = cornerDp,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp,
                    bottomStart = cornerDp,
                )

            TileConnection.ConnectedTrailing ->
                RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = cornerDp,
                    bottomEnd = cornerDp,
                    bottomStart = 0.dp,
                )
        }
    val bgAnim by
        animateColorAsState(
            targetValue = if (pressed && enabled) pressBg else bg,
            animationSpec = tileColorSpec,
            label = "tile-bg",
        )
    val contentAnim by
        animateColorAsState(content, animationSpec = tileColorSpec, label = "tile-content")
    val iconAnim by
        animateColorAsState(iconTint ?: iconColor, animationSpec = tileColorSpec, label = "tile-icon")
    val click =
        if (tile.onLongClick != null) {
            Modifier.combinedClickable(
                enabled = enabled,
                onClick = tile.onClick,
                interactionSource = interactionSource,
                onLongClick = {
                    tile.onLongClick?.invoke()
                    true
                },
            )
        } else {
            Modifier.clickable(
                enabled = enabled,
                onClick = tile.onClick,
                interactionSource = interactionSource,
            )
        }
    Column(
        modifier =
            modifier
                .padding(vertical = outerVPadding, horizontal = outerHPadding)
                .scale(scale)
                .clip(corner)
                .background(bgAnim)
                .then(click)
                .padding(vertical = Tokens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconMd),
            tint = iconAnim,
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(Tokens.SpaceSm))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentAnim,
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
                    color = contentAnim,
                    modifier = barModifier,
                )
            } else {
                // no progress lambda = indeterminate bar ("Starting...")
                LinearProgressIndicator(color = contentAnim, modifier = barModifier)
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
                .padding(vertical = Tokens.SpaceXxs)
                .clip(RoundedCornerShape(BluejayTokens().radius.md))
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

/** Cross-fade for tile state colors (M3's own color-change duration). */
private val tileColorSpec = tween<Color>(200, easing = FastOutSlowInEasing)

/**
 * Registers the leading download action in the download group. A plain
 * [Button] (it's an action, not a state) with [ButtonGroupScope.weight] 1f:
 * it takes all the horizontal space left after the unweighted chevron
 * toggle is measured, so the two segments are deliberately unequal while
 * the group still spans its slot. The content stacks icon over a label —
 * or a progress bar, they share one fixed-height slot — with the sheet's
 * own tile spacing, so the segment reads as one of the grid and keeps its
 * height across states.
 *
 * Non-composable on purpose: the ButtonGroup scope itself is not a
 * composable context, only the item's content lambda is.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun ButtonGroupScope.downloadLeadingItem(
    icon: ImageVector,
    label: String?,
    progress: Float?,
    indeterminate: Boolean,
    containerColor: Color,
    contentColor: Color,
    shapes: GroupCornerShapes,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val showBar = progress != null || indeterminate
    customItem(
        buttonGroupContent = {
            Button(
                onClick = onClick,
                shapes = ButtonShapes(shapes.shape, shapes.pressedShape),
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .animateWidth(interactionSource),
                // Same vertical padding as the sheet's tiles (their inner
                // SpaceMd), so the segment's natural height equals a tile's.
                contentPadding = PaddingValues(all = Tokens.SpaceMd),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                interactionSource = interactionSource,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
                    Spacer(modifier = Modifier.height(Tokens.SpaceSm))
                    // Label and progress bar cross-fade inside one slot the
                    // height of a labelSmall line: swapping them changes
                    // nothing about the segment's height.
                    Box(
                        modifier = Modifier.fillMaxWidth().height(Tokens.SpaceLg),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Fully qualified: inside the Column the scope-bound
                        // AnimatedVisibility overloads would win resolution.
                        androidx.compose.animation.AnimatedVisibility(visible = showBar) {
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = !showBar) {
                            Text(
                                text = label.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        menuContent = {},
    )
}

/**
 * Registers the trailing icon-only quality toggle in the download group.
 * Unweighted: it shrinks to its content, so the group stays unequal. Its
 * [width] is animated by the caller (to 0 while a download is active, so
 * the progress bar owns the whole segment); [clip]ping to the largest
 * (checked) shape keeps the icon inside while the segment collapses. The
 * toggle anchors an expressive [DropdownMenuPopup] (a sub-window, so it is
 * allowed even from the companion's [android.app.Presentation] context, where
 * a [Dialog] would crash with a window-type mismatch).
 * Non-composable on purpose, see [downloadLeadingItem].
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun ButtonGroupScope.downloadQualityItem(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    width: Dp,
    selectedQuality: DownloadQuality?,
    onQualitySelected: (DownloadQuality) -> Unit,
    shapes: GroupCornerShapes,
    interactionSource: MutableInteractionSource,
) {
    customItem(
        buttonGroupContent = {
            val scheme = MaterialTheme.colorScheme
            ToggleButton(
                checked = expanded,
                enabled = enabled,
                onCheckedChange = { if (it) onExpandedChange(true) },
                shapes = ToggleButtonShapes(shapes.shape, shapes.pressedShape, shapes.checkedShape),
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(width)
                        .clip(shapes.checkedShape)
                        .animateWidth(interactionSource),
                // Same vertical padding as the sheet's tiles, see the
                // leading item.
                contentPadding = PaddingValues(all = Tokens.SpaceMd),
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = scheme.surfaceContainer,
                        contentColor = scheme.onSurfaceVariant,
                        checkedContainerColor = scheme.primaryContainer,
                        checkedContentColor = scheme.onPrimaryContainer,
                    ),
                interactionSource = interactionSource,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Download quality",
                    modifier = Modifier.size(Tokens.IconMd),
                )
            }
            DropdownMenuPopup(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                    MenuDefaults.DropdownMenuGroupLabel { Text("Download quality") }
                    val count = DownloadQuality.Options.size
                    DownloadQuality.Options.forEachIndexed { index, quality ->
                        CheckableDropdownMenuItem(
                            checked = quality == selectedQuality,
                            onCheckedChange = {
                                onQualitySelected(quality)
                                onExpandedChange(false)
                            },
                            text = { Text(quality.label) },
                            shapes = MenuDefaults.itemShape(index, count),
                            checkedLeadingIcon = {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            },
                        )
                    }
                }
            }
        },
        menuContent = {},
    )
}

/**
 * Download control for the options sheets. One native M3 [ButtonGroup] in
 * every state — the grid-tile corner recipe, the tiles' colors, and the
 * expressive width animation — with deliberately unequal segments: the
 * leading action takes the remaining width, the trailing icon-only quality
 * toggle shrinks to its content. While a download is active the chevron
 * animates away (width → 0, faded) and the leading segment shows the
 * progress bar in place of its label; when done it becomes the delete
 * action. Because the segment keeps the tile's content stack (and thus
 * height) in every state, switching states never jumps the layout.
 *
 * [onDownload] is state-aware (the host switches on the current state),
 * so it doubles as cancel (while downloading) and delete (when done).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DownloadSection(
    state: DownloadButtonState,
    onDownload: () -> Unit,
    onDownloadWithQuality: ((DownloadQuality) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showQualityMenu by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf<DownloadQuality?>(null) }
    val downloadSource = remember { MutableInteractionSource() }
    val qualitySource = remember { MutableInteractionSource() }
    val radius = BluejayTokens().radius
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val showQuality = onDownloadWithQuality != null && state is DownloadButtonState.Idle
    // The chevron's fixed width when visible: its icon plus the item's
    // horizontal content padding, both tokens.
    val chevronWidth by
        animateDpAsState(
            targetValue = if (showQuality) Tokens.IconMd + Tokens.SpaceMd * 2 else 0.dp,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "download-chevron-width",
        )
    // The tiles' default color at rest, the tiles' state tones otherwise.
    val container by
        animateColorAsState(
            targetValue =
                when (state) {
                    is DownloadButtonState.Downloaded -> scheme.errorContainer
                    is DownloadButtonState.Downloading -> semantic.warning
                    is DownloadButtonState.Starting -> scheme.primaryContainer
                    is DownloadButtonState.Idle -> scheme.surfaceContainer
                },
            animationSpec = tileColorSpec,
            label = "download-container",
        )
    val content by
        animateColorAsState(
            targetValue =
                when (state) {
                    is DownloadButtonState.Downloaded -> scheme.onErrorContainer
                    is DownloadButtonState.Downloading -> semantic.onWarning
                    is DownloadButtonState.Starting -> scheme.onPrimaryContainer
                    is DownloadButtonState.Idle -> scheme.onSurfaceVariant
                },
            animationSpec = tileColorSpec,
            label = "download-content",
        )
    ButtonGroup(
        overflowIndicator = { _ -> },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXxs),
    ) {
        downloadLeadingItem(
            icon =
                when (state) {
                    is DownloadButtonState.Downloaded -> Icons.Filled.Delete
                    // A running download offers itself as the cancel action.
                    is DownloadButtonState.Starting, is DownloadButtonState.Downloading ->
                        Icons.Filled.Stop
                    is DownloadButtonState.Idle -> Icons.Filled.Download
                },
            label =
                when (state) {
                    is DownloadButtonState.Idle -> "Download"
                    is DownloadButtonState.Downloaded -> "Delete"
                    else -> null
                },
            progress = (state as? DownloadButtonState.Downloading)?.progress,
            indeterminate = state is DownloadButtonState.Starting,
            containerColor = container,
            contentColor = content,
            shapes = tileFlowGroupShapes(GroupPosition.First, radius),
            interactionSource = downloadSource,
            onClick = onDownload,
        )
        if (onDownloadWithQuality != null) {
            downloadQualityItem(
                expanded = showQualityMenu,
                onExpandedChange = { showQualityMenu = it },
                enabled = showQuality,
                width = chevronWidth,
                selectedQuality = selectedQuality,
                onQualitySelected = { quality ->
                    selectedQuality = quality
                    onDownloadWithQuality?.invoke(quality)
                },
                shapes = tileFlowGroupShapes(GroupPosition.Last, radius),
                interactionSource = qualitySource,
            )
        }
    }
}

