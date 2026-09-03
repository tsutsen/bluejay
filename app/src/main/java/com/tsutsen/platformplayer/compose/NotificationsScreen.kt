package com.tsutsen.platformplayer.compose

import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.stats.WatchStats
import com.tsutsen.platformplayer.stats.WatchStatsBuilder
import com.tsutsen.platformplayer.core.designsystem.component.DownloadStripCard
import com.tsutsen.platformplayer.core.designsystem.component.QueueStripCard
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.database.dao.NotificationDao
import com.tsutsen.platformplayer.core.database.entity.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.core.ui.RelativeTime
import com.tsutsen.platformplayer.feature.player.impl.HistoryTracker
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Notifications tab: new videos on subscribed channels with the
 * bell enabled. Rows are backed by Room (populated by the background
 * subscription worker).
 */
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val notificationDao: NotificationDao,
        private val downloadsRepository: DownloadsRepository,
        private val historyTracker: HistoryTracker,
    ) : ViewModel() {
        val notifications: StateFlow<List<NotificationEntity>> =
            notificationDao
                .observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** In-flight downloads (not complete) for the Feed's progress widget. */
        val activeDownloads: StateFlow<List<DownloadInfo>> =
            downloadsRepository.downloads
                .map { list -> list.filter { !it.done } }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        /** Aggregated watch stats for the Dash stats card / detail screen. */
        private val _watchStats = MutableStateFlow(WatchStats.Empty)
        val watchStats: StateFlow<WatchStats> = _watchStats

        init {
            // Live history from the Room store the player's HistoryTracker
            // writes to: the flow emits on every playback write (and
            // immediately with the current rows), so the stats recompute
            // reactively without polling.
            viewModelScope.launch {
                historyTracker.observeHistory().collect { history ->
                    _watchStats.value =
                        withContext(Dispatchers.IO) { WatchStatsBuilder.build(history) }
                }
            }
        }

        fun markRead(id: Long) {
            viewModelScope.launch { notificationDao.markRead(id) }
        }

        fun markAllRead() {
            viewModelScope.launch { notificationDao.markAllRead() }
        }

        fun clearAll() {
            viewModelScope.launch { notificationDao.clear() }
        }

        fun cancelDownload(url: String) {
            viewModelScope.launch { downloadsRepository.cancelDownload(url) }
        }
    }

@Composable
fun NotificationsScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    val queue by playerViewModel.queue.collectAsState(initial = emptyList())
    val playerUi by playerViewModel.uiState.collectAsState()
    val playerLoaded = playerUi as? com.tsutsen.platformplayer.feature.player.impl.PlayerUiState.Loaded
    val activeDownloads by viewModel.activeDownloads.collectAsState(initial = emptyList())
    val watchStats by viewModel.watchStats.collectAsState()
    // Long-press on a queue card → the shared video options sheet.
    var sheetVideo by remember { mutableStateOf<ContentItem?>(null) }
    var showStatsDetail by remember { mutableStateOf(false) }

    if (showStatsDetail) {
        WatchStatsDetailScreen(stats = watchStats, onBack = { showStatsDetail = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = { Text("Dash", style = MaterialTheme.typography.titleLarge) },
            actions = {
                TextButton(onClick = { viewModel.markAllRead() }) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = "Mark all as read",
                    )
                }
                TextButton(onClick = { viewModel.clearAll() }) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear all",
                    )
                }
            },
        )

        // The cards + notifications scroll together as one column.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Persistent queue card at the top of the Dash.
            QueueStripCard(
                queue = queue,
                current = playerLoaded?.currentVideo,
                isPlaying = playerLoaded?.isPlaying ?: false,
                onPlay = { index -> playerViewModel.playQueueItem(index) },
                onRemove = { url -> playerViewModel.removeQueueItemUrl(url) },
                onMove = { from, to -> playerViewModel.moveQueueItem(from, to) },
                onPlayPause = {
                    if (playerLoaded?.isPlaying == true) {
                        playerViewModel.pause()
                    } else {
                        playerViewModel.resume()
                    }
                },
                onLongClick = { video -> sheetVideo = video },
                modifier = Modifier.padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceSm),
            )

            // Stats card (health-app style): tapping it opens the detail screen.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceXs)
                        .clip(RoundedCornerShape(BluejayTokens().radius.card))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(Tokens.SpaceLg),
            ) {
                WatchStatsSummary(stats = watchStats, onClick = { showStatsDetail = true })
            }

            // Persistent download-progress card (active downloads only).
            DownloadStripCard(
                downloads = activeDownloads,
                onRemove = { url -> viewModel.cancelDownload(url) },
                modifier = Modifier.padding(horizontal = Tokens.SpaceLg).padding(top = Tokens.SpaceXs),
            )

        // Long-press sheet for the queue cards (same host as every other
        // video card in the app). "Go to channel" is hidden: this screen
        // has no navigation target.
        sheetVideo?.let { video ->
            com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost(
                video = video.toVideoCard(authorUrl = null),
                onDismiss = { sheetVideo = null },
                onPlay = {
                    playerViewModel.play(video.url)
                    sheetVideo = null
                },
                onGoToChannel = {},
                currentVideoUrl = playerLoaded?.currentVideo?.url,
            )
        }

        // Notifications section: same card panel as the queue/stats cards
        // above, with a full bottom gap for the last row.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Tokens.SpaceLg,
                        top = Tokens.SpaceXs,
                        end = Tokens.SpaceLg,
                        bottom = Tokens.SpaceLg,
                    )
                    .clip(RoundedCornerShape(BluejayTokens().radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(Tokens.SpaceLg),
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(Tokens.SpaceSm))
            if (notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No notifications yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tap the bell on a subscribed channel to get notified about new videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            notifications.forEach { notification ->
                NotificationRow(
                    notification = notification,
                    onClick = {
                        viewModel.markRead(notification.id)
                        playerViewModel.play(notification.contentUrl)
                    },
                )
            }
        }
            }
        }
    }
}

private fun ContentItem.toVideoCard(authorUrl: String? = this.author?.url): VideoCard =
    VideoCard(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author?.name,
        authorUrl = authorUrl,
        durationMs = durationMs,
        viewCount = viewCount,
        publishedAt = publishedAt,
        url = url,
    )

@Composable
private fun NotificationRow(
    notification: NotificationEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // The section card owns the horizontal padding now.
                .padding(vertical = Tokens.SpaceSm)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        notification.thumbnailUrl?.let { url ->
            AsyncImage(
                url = url,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = Tokens.SpaceMd),
        ) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Text(
                text = "${notification.subscriptionName} • ${RelativeTime.format(notification.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (!notification.isRead) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
