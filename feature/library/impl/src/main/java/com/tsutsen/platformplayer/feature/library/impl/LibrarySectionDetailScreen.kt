package com.tsutsen.platformplayer.feature.library.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard

/**
 * Detail screen for a library section.
 * List layout in portrait, 3-column grid on wide windows.
 * Video cards play on click; playlist cards open the (placeholder) playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySectionDetailScreen(
    sectionId: String,
    onBack: () -> Unit,
    navigator: Navigator,
    gridColumns: Int = 3,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: LibrarySectionDetailViewModel = hiltViewModel(),
) {
    val section by viewModel.section.collectAsState()
    val items by viewModel.items.collectAsState()
    val isWide = rememberIsWide()
    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }

    LaunchedEffect(sectionId) {
        viewModel.loadSection(sectionId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            TopAppBar(
                title = { Text(section?.title ?: "Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )

            when {
                section == null -> {
                    VideoCardSkeleton(count = 6)
                }

                items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nothing yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    VideoContainer(
                        items = items,
                        layout = if (isWide) ContainerLayout.Grid(gridColumns) else ContainerLayout.List,
                        isLoading = false,
                        hasMorePages = false,
                        onCardClick = { card ->
                            when (card) {
                                is CoreVideoCard -> playerViewModel.play(card.url)
                                is PlaylistCard -> navigator.navigateToPlaylist(card.url)
                                else -> Unit
                            }
                        },
                        onLoadMore = {},
                    ) { card ->
                        LibraryCard(
                            card = card,
                            onClick = { card ->
                                when (card) {
                                    is CoreVideoCard -> playerViewModel.play(card.url)
                                    is PlaylistCard -> navigator.navigateToPlaylist(card.url)
                                    else -> Unit
                                }
                            },
                            onVideoLongClick = { optionsCard = it },
                        )
                    }
                }
            }
        }
    }

    optionsCard?.let { card ->
        VideoOptionsSheetHost(
            video = card,
            onDismiss = { optionsCard = null },
            onPlay = { playerViewModel.play(card.url) },
            onGoToChannel = { navigator.navigateToChannel(it) },
        )
    }
}

/**
 * Card renderer for the detail grid/list.
 */
@Composable
private fun LibraryCard(
    card: Card,
    onClick: (Card) -> Unit,
    onVideoLongClick: (CoreVideoCard) -> Unit,
) {
    when (card) {
        is CoreVideoCard -> {
            VideoCard(
                card = card,
                onClick = { onClick(card) },
                onLongClick = { onVideoLongClick(card) },
            )
        }

        is PlaylistCard -> {
            PlaylistCardView(card = card, onClick = { onClick(card) })
        }

        else -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            )
        }
    }
}
