package com.tsutsen.platformplayer.core.designsystem.component

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsutsen.platformplayer.core.model.PlaylistOption

/**
 * Long-press options for a video card.
 *
 * Leaf component: receives state (saved flags, playlists, authorUrl) and
 * action lambdas; the screen wires them to PlayerViewModel / DAOs / Navigator.
 * "Play next" / "Add to queue" intentionally absent until the player queue
 * exists (PlayerViewModel still has TODOs for queue navigation).
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
    onAddToPlaylist: (Long?) -> Unit,
    isWatchLaterSaved: Boolean = false,
    isLikedSaved: Boolean = false,
    isFavouriteSaved: Boolean = false,
    playlists: List<PlaylistOption> = emptyList(),
    authorUrl: String? = null,
    title: String? = null,
) {
    val context = LocalContext.current

    GrayjayModalBottomSheet(
        onDismiss = onDismiss,
        title = title,
    ) {
        OptionRow(
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp)) },
            label = "Play",
            onClick = {
                onPlay()
                onDismiss()
            },
        )

        OptionRow(
            icon = {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                        if (isWatchLaterSaved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            label = "Watch later",
            onClick = onToggleWatchLater,
        )

        OptionRow(
            icon = {
                Icon(
                    if (isLikedSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                        if (isLikedSaved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            label = "Like",
            onClick = onToggleLiked,
        )

        OptionRow(
            icon = {
                Icon(
                    if (isFavouriteSaved) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                        if (isFavouriteSaved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            label = "Favourite",
            onClick = onToggleFavourite,
        )

        OptionRow(
            icon = { Icon(Icons.Filled.HistoryEdu, contentDescription = null, modifier = Modifier.size(24.dp)) },
            label = "Add to playlist",
            onClick = null,
        )
        playlists.forEach { playlist ->
            IndentedRow(
                label = playlist.name,
                onClick = {
                    onAddToPlaylist(playlist.id)
                    onDismiss()
                },
            )
        }
        IndentedRow(
            label = "New playlist",
            onClick = {
                onAddToPlaylist(null)
                onDismiss()
            },
        )

        OptionRow(
            icon = { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(24.dp)) },
            label = "Share",
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
        )

        if (authorUrl != null) {
            OptionRow(
                icon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = "Go to channel",
                onClick = {
                    onGoToChannel(authorUrl)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun OptionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun IndentedRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 60.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
