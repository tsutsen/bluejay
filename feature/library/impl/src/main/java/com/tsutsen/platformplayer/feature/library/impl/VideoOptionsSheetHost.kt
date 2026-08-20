package com.tsutsen.platformplayer.feature.library.impl

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard

/**
 * Shared long-press options host: renders [VideoOptionsSheet] with live
 * saved-state toggles and playlist actions, backed by
 * [VideoOptionsViewModel]. Every screen with video cards uses this one
 * composable, so the sheet behaviour stays identical everywhere.
 *
 * Screens pass [video] + lambdas for the actions they already own
 * (play, channel navigation); saving and playlists are handled internally.
 */
@Composable
fun VideoOptionsSheetHost(
    video: VideoCard,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onGoToChannel: (String) -> Unit,
) {
    val viewModel: VideoOptionsViewModel = hiltViewModel()
    val savedTypes by viewModel.savedTypes(video.url).collectAsState(initial = emptySet())
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val downloadState by viewModel
        .downloadState(video.url)
        .collectAsState(initial = DownloadButtonState.Idle)
    val downloadMessage by viewModel.downloadMessage.collectAsState()
    val containedPlaylists by viewModel
        .playlistsContaining(video.url)
        .collectAsState(initial = emptySet())
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    // One-shot feedback for download failures as a system toast (success
    // and stop are visible on the button itself).
    val context = LocalContext.current
    LaunchedEffect(downloadMessage) {
        downloadMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeDownloadMessage()
        }
    }

    VideoOptionsSheet(
        url = video.url,
        onDismiss = onDismiss,
        onPlay = onPlay,
        onGoToChannel = onGoToChannel,
        onToggleWatchLater = {
            viewModel.toggle(SavedVideoType.WATCH_LATER, video)
        },
        onToggleLiked = {
            viewModel.toggle(SavedVideoType.LIKED, video)
        },
        onToggleFavourite = {
            viewModel.toggle(SavedVideoType.FAVOURITE, video)
        },
        onDownload = {
            when (downloadState) {
                is DownloadButtonState.Idle -> viewModel.download(video)

                is DownloadButtonState.Downloading -> viewModel.stopDownload(video)

                is DownloadButtonState.Downloaded -> viewModel.deleteDownload(video)

                // Starting: no-op, the button shows it.
                is DownloadButtonState.Starting -> Unit
            }
        },
        downloadState = downloadState,
        // "New playlist" row (the checkboxes use onTogglePlaylist).
        onAddToPlaylist = { playlistId ->
            if (playlistId == null) {
                showNewPlaylistDialog = true
            }
        },
        onTogglePlaylist = { playlistId, checked ->
            viewModel.togglePlaylist(playlistId, checked, video)
        },
        containedPlaylistIds = containedPlaylists,
        isWatchLaterSaved = savedTypes.contains(SavedVideoType.WATCH_LATER),
        isLikedSaved = savedTypes.contains(SavedVideoType.LIKED),
        isFavouriteSaved = savedTypes.contains(SavedVideoType.FAVOURITE),
        playlists = playlists,
        authorUrl = video.authorUrl,
        title = video.title,
        durationMs = video.durationMs,
        viewCount = video.viewCount,
        publishedAt = video.publishedAt,
    )

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onCreate = { name ->
                viewModel.createPlaylistAndAdd(name, video)
                showNewPlaylistDialog = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
