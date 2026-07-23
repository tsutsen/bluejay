package com.futo.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.core.model.Card
import com.futo.platformplayer.core.model.VideoCard

/**
 * Type-agnostic container for video cards with layout mode support.
 */
enum class LayoutMode { LIST, GRID }

@Composable
fun VideoContainer(
    cards: List<VideoCard>,
    layoutMode: LayoutMode = LayoutMode.LIST,
    columns: Int = 3,
    onCardClick: (VideoCard) -> Unit,
    modifier: Modifier = Modifier
) {
    when (layoutMode) {
        LayoutMode.LIST -> {
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(cards) { card ->
                    VideoCard(
                        card = card,
                        onClick = { onCardClick(card) }
                    )
                }
            }
        }
        LayoutMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(cards) { card ->
                    VideoCard(
                        card = card,
                        onClick = { onCardClick(card) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
