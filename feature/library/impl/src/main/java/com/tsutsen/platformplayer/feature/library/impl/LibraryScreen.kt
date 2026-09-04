package com.tsutsen.platformplayer.feature.library.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.PlaylistOptionsSheet
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.layout.TabContentTopPadding
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.PlaylistStats
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import kotlinx.coroutines.launch
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard

/**
 * Library screen: Watch Later, Liked, Favourites, Playlists.
 * Each section is a clickable title row over a horizontal strip of its
 * first items; an "All (n)" card at the strip end opens the same detail.
 */
@Composable
fun LibraryScreen(
    navigator: Navigator,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val sections by viewModel.sections.collectAsState()
    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }
    var optionsPlaylist by remember { mutableStateOf<PlaylistCard?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (sections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // First element of the tab: on the shared 42dp content line.
                contentPadding =
                    PaddingValues(
                        start = Tokens.SpaceLg,
                        top = TabContentTopPadding,
                        end = Tokens.SpaceLg,
                        bottom = Tokens.SpaceLg,
                    ),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXl),
            ) {
                items(sections, key = { it.id }) { section ->
                    LibrarySectionRow(
                        section = section,
                        onSectionClick = { navigator.navigateToLibrarySectionDetail(section.id) },
                        onCardClick = { card ->
                            when (card) {
                                is CoreVideoCard -> playerViewModel.play(card)
                                is PlaylistCard -> navigator.navigateToPlaylist(card.url)
                                else -> Unit
                            }
                        },
                        onVideoLongClick = { optionsCard = it },
                        onPlaylistLongClick = { optionsPlaylist = it },
                        // "playlists" is the LibraryRepositoryImpl.PLAYLISTS_ID
                        // section constant.
                        onNewPlaylist = { showNewPlaylistDialog = true },
                    )
                }
            }
        }
    }

    optionsCard?.let { card ->
        VideoOptionsSheetHost(
            video = card,
            onDismiss = { optionsCard = null },
            onPlay = { playerViewModel.play(card) },
            onGoToChannel = { navigator.navigateToChannel(it) },
        )
    }

    optionsPlaylist?.let { playlist ->
        PlaylistOptionsSheetHost(
            playlist = playlist,
            onDismiss = { optionsPlaylist = null },
        )
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onCreate = { viewModel.createPlaylist(it.trim()) },
            onDismiss = { showNewPlaylistDialog = false },
        )
    }
}

@Composable
private fun LibrarySectionRow(
    section: LibrarySection,
    onSectionClick: () -> Unit,
    onCardClick: (Card) -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
    onPlaylistLongClick: (PlaylistCard) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BluejayTokens().radius.card))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Tokens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSectionClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (section.totalCount > 0) {
                Text(
                    text = "${section.totalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Tokens.SpaceSm))
            }
            if (section.id == "playlists") {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New playlist",
                    modifier =
                        Modifier
                            .padding(horizontal = Tokens.SpaceSm)
                            .clickable(onClick = onNewPlaylist),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open ${section.title}",
            )
        }

        if (section.items.isEmpty()) {
            Text(
                text = "Nothing yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Tokens.SpaceSm),
            )
        } else {
            VideoContainer(
                items = section.items,
                layout = ContainerLayout.HorizontalStrip,
                isLoading = false,
                hasMorePages = false,
                onCardClick = onCardClick,
                onLoadMore = {},
                contentPadding = PaddingValues(0.dp),
                cardContent = { card ->
                    Box(modifier = Modifier.width(STRIP_CARD_WIDTH).padding(end = Tokens.SpaceMd)) {
                        LibraryCard(
                            card = card,
                            onClick = { onCardClick(card) },
                            onVideoLongClick = onVideoLongClick,
                            onPlaylistLongClick = onPlaylistLongClick,
                        )
                    }
                },
                trailingContent =
                    if (section.hasMore) {
                        { AllCard(count = section.totalCount, onClick = onSectionClick) }
                    } else {
                        null
                    },
            )
        }
    }
}


/**
 * Trailing "All (n)" card for strips longer than the section limit.
 */
@Composable
private fun AllCard(
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(STRIP_CARD_WIDTH)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "All ($count)",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val STRIP_CARD_WIDTH = 180.dp

/**
 * Long-press sheet for a local playlist card: stats (count + total
 * duration), Play (first video), Delete playlist.
 */
@Composable
internal fun PlaylistOptionsSheetHost(
    playlist: PlaylistCard,
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    // PlaylistCard.url is "playlist:<id>" for local playlists (built by
    // LibraryRepositoryImpl). Not a local id -> no sheet.
    val playlistId = playlist.url.substringAfter(":").toLongOrNull() ?: return
    val stats by viewModel.playlistStats(playlistId).collectAsState(
        initial = PlaylistStats(videoCount = 0, totalDurationMs = 0),
    )
    PlaylistOptionsSheet(
        title = playlist.title,
        videoCount = stats.videoCount,
        totalDurationMs = stats.totalDurationMs,
        onDismiss = onDismiss,
        onPlay = {
            scope.launch {
                viewModel.getFirstVideoUrl(playlistId)?.let { playerViewModel.play(it) }
            }
        },
        onDelete = { viewModel.deletePlaylist(playlistId) },
    )
}
