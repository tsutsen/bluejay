package com.futo.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.core.model.Card
import com.futo.platformplayer.core.model.VideoCard

/**
 * Layout mode for the video container.
 */
enum class LayoutMode {
    /** Single-column vertical scroll (portrait). */
    List,
    /** Horizontal scrollable row (for sections). */
    HorizontalStrip,
    /** Multi-column grid (landscape). */
    Grid
}

/**
 * Type-agnostic video container composable.
 * Supports List, HorizontalStrip, and Grid layout modes.
 * Handles infinite scroll pagination via onEndReached callback.
 */
@Composable
fun VideoContainer(
    items: kotlin.collections.List<Card>,
    layoutMode: LayoutMode = LayoutMode.List,
    columns: Int = 3,
    onCardClick: (Card) -> Unit,
    onEndReached: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    cardContent: @Composable (Card) -> Unit
) {
    when (layoutMode) {
        LayoutMode.List -> {
            val listState = rememberLazyListState()
            var isLoading by remember { mutableStateOf(false) }

            LaunchedEffect(listState.isScrollInProgress, items.size) {
                if (listState.isScrollInProgress) {
                    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val totalItems = listState.layoutInfo.totalItemsCount
                    if (lastVisibleIndex == totalItems - 1 && totalItems > 0 && !isLoading) {
                        isLoading = true
                        onEndReached()
                        isLoading = false
                    }
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }
        LayoutMode.HorizontalStrip -> {
            val listState = rememberLazyListState()

            LazyRow(
                modifier = modifier.fillMaxWidth(),
                state = listState,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }
        LayoutMode.Grid -> {
            val gridState = rememberLazyGridState()
            var isLoading by remember { mutableStateOf(false) }

            LaunchedEffect(gridState.isScrollInProgress, items.size) {
                if (gridState.isScrollInProgress) {
                    val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val totalItems = gridState.layoutInfo.totalItemsCount
                    if (lastVisibleIndex == totalItems - 1 && totalItems > 0 && !isLoading) {
                        isLoading = true
                        onEndReached()
                        isLoading = false
                    }
                }
            }

            LazyVerticalGrid(
                modifier = modifier.fillMaxSize(),
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }
    }
}

/**
 * Convenience composable for rendering a list of VideoCards.
 */
@Composable
fun VideoCardList(
    cards: kotlin.collections.List<VideoCard>,
    layoutMode: LayoutMode = LayoutMode.List,
    columns: Int = 3,
    onCardClick: (VideoCard) -> Unit,
    onEndReached: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    VideoContainer(
        items = cards,
        layoutMode = layoutMode,
        columns = columns,
        onCardClick = { card ->
            if (card is VideoCard) onCardClick(card)
        },
        onEndReached = onEndReached,
        modifier = modifier,
        contentPadding = contentPadding
    ) { card ->
        VideoCard(
            card = card as VideoCard,
            onClick = { onCardClick(card as VideoCard) }
        )
    }
}
