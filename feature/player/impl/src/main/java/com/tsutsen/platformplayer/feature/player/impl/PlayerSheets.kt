package com.tsutsen.platformplayer.feature.player.impl

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tsutsen.platformplayer.core.database.AppDatabaseProvider
import com.tsutsen.platformplayer.core.database.dao.toggleWatched
import com.tsutsen.platformplayer.core.database.entity.isFullyWatched
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.ui.R
import kotlinx.coroutines.launch

/**
 * Long-press-style options sheet for the currently playing video. Same
 * [VideoOptionsSheet] the library uses for card long-presses, with live
 * saved/download state from [PlayerViewModel].
 */
@Composable
internal fun CurrentVideoOptionsSheet(
    video: ContentItem,
    viewModel: PlayerViewModel,
    isCurrentlyPlaying: Boolean,
    onDismiss: () -> Unit,
    onGoToChannel: (String) -> Unit,
) {
    val savedTypes by viewModel.savedTypes.collectAsState(initial = emptySet())
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val contained by viewModel.containedPlaylists.collectAsState(initial = emptySet())
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val queue by viewModel.queue.collectAsState(initial = emptyList())
    val downloadInfo = downloads.find { it.url == video.url }
    val downloadState =
        when {
            downloadInfo == null -> DownloadButtonState.Idle
            downloadInfo.done -> DownloadButtonState.Downloaded
            else -> DownloadButtonState.Downloading(downloadInfo.progress)
        }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    // Creator-level hide options are keyed by the video's authorUrl.
    val creatorUrl = video.author?.url?.takeIf { it.isNotEmpty() }
    // "Watched" tile: the player module owns its DB access directly (same
    // as HistoryTracker); the state is read from history when the sheet
    // opens.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWatched by remember { mutableStateOf(false) }
    LaunchedEffect(video.url) {
        isWatched =
            AppDatabaseProvider.get(context).historyDao().getByUrl(video.url)?.isFullyWatched() == true
    }

    VideoOptionsSheet(
        url = video.url,
        onDismiss = onDismiss,
        onPlay = onDismiss,
        onGoToChannel = onGoToChannel,
        onToggleWatchLater = {
            viewModel.toggleWatchLater(savedTypes.contains(SavedVideoType.WATCH_LATER))
        },
        onToggleLiked = {
            viewModel.toggleLike(savedTypes.contains(SavedVideoType.LIKED))
        },
        onToggleFavourite = {
            viewModel.toggleFavourite(savedTypes.contains(SavedVideoType.FAVOURITE))
        },
        onDownload = {
            when (downloadState) {
                is DownloadButtonState.Idle -> viewModel.startDownload()
                is DownloadButtonState.Downloading -> viewModel.cancelDownload()
                is DownloadButtonState.Downloaded -> viewModel.deleteDownload()
                is DownloadButtonState.Starting -> Unit
            }
        },
        onDownloadWithQuality = { quality -> viewModel.startDownload(quality) },
        onAddToPlaylist = { playlistId ->
            if (playlistId == null) {
                showNewPlaylistDialog = true
            } else {
                viewModel.addToPlaylist(video, playlistId)
            }
        },
        onAddToQueue = { viewModel.addToQueue(video) },
        // "Watched" tile: toggle fully-watched in the history (mark, or
        // clear the progress when already watched), then a toast (plain
        // Toast here — the app module's UIDialogs.toast is the same call but
        // unreachable from this module).
        onToggleWatched = {
            scope.launch {
                isWatched =
                    AppDatabaseProvider.get(context).historyDao().toggleWatched(
                        url = video.url,
                        title = video.title,
                        author = video.author?.name,
                        thumbnailUrl = video.thumbnailUrl,
                        durationMs = video.durationMs ?: 0L,
                    )
                // No toast: the tile's state change is the feedback.
            }
        },
        isWatched = isWatched,
        // The Hide tile is home-feed-only; the player sheet omits it.
        isHidden = false,
        onHideVideo = null,
        onDownrankCreator = null,
        onHideCreator = null,
        isCreatorDownranked = false,
        isCreatorHidden = false,
        isInQueue = queue.any { it.url == video.url },
        isCurrentlyPlaying = isCurrentlyPlaying,
        onRemoveFromQueue = { viewModel.removeQueueItemUrl(video.url) },
        downloadState = downloadState,
        isWatchLaterSaved = savedTypes.contains(SavedVideoType.WATCH_LATER),
        isLikedSaved = savedTypes.contains(SavedVideoType.LIKED),
        isFavouriteSaved = savedTypes.contains(SavedVideoType.FAVOURITE),
        playlists = playlists,
        authorUrl = creatorUrl,
        title = video.title,
        durationMs = video.durationMs,
        viewCount = video.viewCount,
        publishedAt = video.publishedAt,
        containedPlaylistIds = contained,
        onTogglePlaylist = { id, checked ->
            viewModel.togglePlaylistMembership(id, checked)
        },
    )

    if (showNewPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text(stringResource(R.string.library_new_playlist_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.library_playlist_name_label)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Close only the modal; the sheet stays open and the
                        // video is added to the new playlist.
                        viewModel.createPlaylistAndAdd(name)
                        showNewPlaylistDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.common_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
