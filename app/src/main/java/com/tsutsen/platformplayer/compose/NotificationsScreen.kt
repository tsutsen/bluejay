package com.tsutsen.platformplayer.compose

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.designsystem.component.QueueStripCard
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.database.dao.NotificationDao
import com.tsutsen.platformplayer.core.database.entity.NotificationEntity
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.core.ui.RelativeTime
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
    ) : ViewModel() {
        val notifications: StateFlow<List<NotificationEntity>> =
            notificationDao
                .observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun markRead(id: Long) {
            viewModelScope.launch { notificationDao.markRead(id) }
        }

        fun markAllRead() {
            viewModelScope.launch { notificationDao.markAllRead() }
        }

        fun clearAll() {
            viewModelScope.launch { notificationDao.clear() }
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

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = { Text("Feed", style = MaterialTheme.typography.titleLarge) },
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

        // Persistent queue card at the top of the Feed.
        QueueStripCard(
            current = playerLoaded?.currentVideo,
            isPlaying = playerLoaded?.isPlaying ?: false,
            queue = queue,
            onPlayPause = {
                if (playerLoaded?.isPlaying == true) playerViewModel.pause()
                else playerViewModel.resume()
            },
            onPlay = { index -> playerViewModel.playQueueItem(index) },
            onRemove = { index -> playerViewModel.removeQueueItem(index) },
            onMove = { from, to -> playerViewModel.moveQueueItem(from, to) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No notifications yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap the bell on a subscribed channel to get notified about new videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn {
                items(
                    items = notifications,
                    key = { it.id },
                ) { notification ->
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

@Composable
private fun NotificationRow(
    notification: NotificationEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceSm)
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
