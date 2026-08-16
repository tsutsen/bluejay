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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
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

    Box(modifier = modifier.fillMaxSize()) {
        if (sections.isEmpty()) {
            LibrarySkeleton()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(sections, key = { it.id }) { section ->
                    LibrarySectionRow(
                        section = section,
                        onSectionClick = { navigator.navigateToLibrarySectionDetail(section.id) },
                        onCardClick = { card ->
                            when (card) {
                                is CoreVideoCard -> playerViewModel.play(card.url)
                                is PlaylistCard -> navigator.navigateToPlaylist(card.url)
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionRow(
    section: LibrarySection,
    onSectionClick: () -> Unit,
    onCardClick: (Card) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            )
            Spacer(Modifier.weight(1f))
            if (section.totalCount > 0) {
                Text(
                    text = "${section.totalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
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
                modifier = Modifier.padding(vertical = 8.dp),
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
                    Box(modifier = Modifier.width(STRIP_CARD_WIDTH).padding(end = 12.dp)) {
                        LibraryCard(
                            card = card,
                            onClick = { onCardClick(card) },
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

@Composable
private fun LibraryCard(
    card: Card,
    onClick: () -> Unit,
) {
    when (card) {
        is CoreVideoCard -> {
            VideoCard(card = card, onClick = onClick)
        }

        is PlaylistCard -> {
            PlaylistCardView(card = card, onClick = onClick)
        }

        else -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/**
 * Playlist card: thumbnail + name + video count.
 */
@Composable
fun PlaylistCardView(
    card: PlaylistCard,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    url = card.thumbnailUrl,
                    contentDescription = card.title,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp)),
                )
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                if (card.videoCount != null) {
                    Text(
                        text = "${card.videoCount} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                .clip(RoundedCornerShape(8.dp))
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

@Composable
private fun LibrarySkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(4) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) {
                        Box(
                            modifier =
                                Modifier
                                    .width(STRIP_CARD_WIDTH)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

private val STRIP_CARD_WIDTH = 180.dp
