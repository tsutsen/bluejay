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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.database.AppDatabaseProvider
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import kotlinx.coroutines.launch

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
    // The currently playing video's URL (null when unknown): its queue
    // tile shows "Now playing" and is disabled.
    currentVideoUrl: String? = null,
    // Non-null = the "Remove from history" tile appears (history contexts
    // only — the library's History strip and the history page).
    onRemoveFromHistory: (() -> Unit)? = null,
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
    val queue by viewModel.queue.collectAsState(initial = emptyList())
    val hiddenUrls by viewModel.hiddenVideoUrls.collectAsState(initial = emptyList())
    val hiddenCreators by viewModel.hiddenCreators.collectAsState(initial = emptyList())
    val downrankedCreators by viewModel.downrankedCreators.collectAsState(initial = emptyList())
    // Creator-level hide options are keyed by the video's authorUrl.
    val creatorUrl = video.authorUrl?.takeIf { it.isNotEmpty() }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    // One-shot feedback for download failures as a system toast (success
    // and stop are visible on the button itself).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
        onDownloadWithQuality = { quality -> viewModel.download(video, quality) },
        downloadState = downloadState,
        // "New playlist" row (the checkboxes use onTogglePlaylist).
        onAddToPlaylist = { playlistId ->
            if (playlistId == null) {
                showNewPlaylistDialog = true
            }
        },
        onRemoveFromHistory = onRemoveFromHistory,
        // "Watched" tile: upsert a history row at full duration, then a
        // toast (plain Toast here — the app module's UIDialogs.toast is the
        // same call but unreachable from this module).
        onMarkWatched = {
            scope.launch {
                AppDatabaseProvider.get(context).historyDao().markWatched(
                    url = video.url,
                    title = video.title,
                    author = video.author,
                    thumbnailUrl = video.thumbnailUrl,
                    durationMs = video.durationMs ?: 0L,
                )
                Toast.makeText(context, "Marked as watched", Toast.LENGTH_SHORT).show()
            }
        },
        isHidden = video.url in hiddenUrls,
        onHideVideo = { viewModel.toggleHiddenVideo(video.url) },
        onDownrankCreator = creatorUrl?.let { url -> { viewModel.toggleDownrankedCreator(url) } },
        onHideCreator = creatorUrl?.let { url -> { viewModel.toggleHiddenCreator(url) } },
        isCreatorDownranked = creatorUrl != null && creatorUrl in downrankedCreators,
        isCreatorHidden = creatorUrl != null && creatorUrl in hiddenCreators,
        onAddToQueue = {
            // No onDismiss: the sheet stays open so the user can keep
            // choosing actions.
            viewModel.addToQueue(video)
        },
        isInQueue = queue.any { it.url == video.url },
        isCurrentlyPlaying = currentVideoUrl != null && currentVideoUrl == video.url,
        onRemoveFromQueue = { viewModel.removeFromQueue(video.url) },
        onTogglePlaylist = { playlistId, checked ->
            viewModel.togglePlaylist(playlistId, checked, video)
        },
        containedPlaylistIds = containedPlaylists,
        isWatchLaterSaved = savedTypes.contains(SavedVideoType.WATCH_LATER),
        isLikedSaved = savedTypes.contains(SavedVideoType.LIKED),
        isFavouriteSaved = savedTypes.contains(SavedVideoType.FAVOURITE),
        playlists = playlists,
        authorUrl = creatorUrl,
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
