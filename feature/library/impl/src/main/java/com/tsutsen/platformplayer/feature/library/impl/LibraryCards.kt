package com.tsutsen.platformplayer.feature.library.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import com.tsutsen.platformplayer.core.ui.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.collectAsActiveState
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Card dispatcher shared by the library tab and the section detail
 * screen: video cards show watch progress, playlists render as
 * [PlaylistCardView], unknown kinds get a neutral 16:9 placeholder.
 * Watch-state collection pauses while a keep-alive tab hosting this
 * composable is hidden ([collectAsActiveState] is a plain collect
 * elsewhere).
 */
@Composable
fun LibraryCard(
    card: Card,
    onClick: (Card) -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
    onPlaylistLongClick: (PlaylistCard) -> Unit,
) {
    val watchStates by hiltViewModel<PlayerViewModel>().watchStates.collectAsActiveState(emptyMap())
    when (card) {
        is CoreVideoCard -> {
            VideoCard(
                card = card,
                onClick = { onClick(card) },
                onLongClick = { onVideoLongClick(card) },
                watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                isWatched = watchStates[card.url]?.isWatched ?: false,
            )
        }

        is PlaylistCard -> {
            PlaylistCardView(
                card = card,
                onClick = { onClick(card) },
                onLongClick = { onPlaylistLongClick(card) },
            )
        }

        else -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/**
 * "New playlist" name dialog; owns its own text state.
 */
@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_new_playlist_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.library_playlist_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/**
 * Playlist card: thumbnail + name + video count.
 */
@Composable
fun PlaylistCardView(
    card: PlaylistCard,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    // Video-card shape: 16:9 cover on top, title + count below. A missing
    // cover shows a placeholder icon (AsyncImage would spin forever on a
    // null url).
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = RoundedCornerShape(BluejayTokens().radius.sm),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (card.thumbnailUrl != null) {
                    AsyncImage(
                        url = card.thumbnailUrl,
                        contentDescription = card.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
            ) {
                // minLines=2: every card reserves two title lines, so the
                // text area (and thus the card) has one height regardless of
                // how long the title is.
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The count line is ALWAYS rendered (line space reserved via
                // the empty text) so no card is shorter than another. The
                // count itself only shows when the source reports one — a
                // null count means "unknown", not zero, so nothing is
                // fabricated.
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        card.videoCount?.let { count ->
                            if (count == 1) {
                                stringResource(R.string.library_one_video)
                            } else {
                                stringResource(R.string.library_videos_count, count)
                            }
                        }
                        ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
