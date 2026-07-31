package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.model.Card

/**
 * Layout mode for the video container.
 */
sealed interface ContainerLayout {
    /** Single-column vertical scroll (portrait). */
    object List : ContainerLayout

    /** Horizontal scrollable row (for sections). */
    object HorizontalStrip : ContainerLayout

    /** Multi-column grid (landscape). */
    data class Grid(val columns: Int) : ContainerLayout
}

/**
 * Type-agnostic video container composable.
 * Supports List, HorizontalStrip, and Grid layout modes.
 * Handles infinite scroll pagination internally.
 *
 * @param items List of cards to display
 * @param layout Layout mode (List, HorizontalStrip, or Grid with column count)
 * @param isLoading Whether data is currently being loaded (prevents duplicate requests)
 * @param hasMorePages Whether there are more pages to load
 * @param onCardClick Called when a card is tapped
 * @param onLoadMore Called when the user scrolls to the end (if !isLoading && hasMorePages)
 * @param modifier Modifier for the container
 * @param contentPadding Padding around the content
 * @param cardContent Composable that renders a single card
 */
@Composable
fun VideoContainer(
    items: List<Card>,
    layout: ContainerLayout = ContainerLayout.List,
    isLoading: Boolean,
    hasMorePages: Boolean,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    cardContent: @Composable (Card) -> Unit
) {
    when (layout) {
        is ContainerLayout.List -> {
            val state = rememberLazyListState()
            ScrollEndReached(listState = state, gridState = null, isLoading = isLoading, hasMorePages = hasMorePages, onEndReached = onLoadMore)
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                renderCards(items, cardContent)
            }
        }
        is ContainerLayout.HorizontalStrip -> {
            val state = rememberLazyListState()
            ScrollEndReached(listState = state, gridState = null, isLoading = isLoading, hasMorePages = hasMorePages, onEndReached = onLoadMore)
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                state = state,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                renderCards(items, cardContent)
            }
        }
        is ContainerLayout.Grid -> {
            val state = rememberLazyGridState()
            ScrollEndReached(listState = null, gridState = state, isLoading = isLoading, hasMorePages = hasMorePages, onEndReached = onLoadMore)
            LazyVerticalGrid(
                modifier = modifier.fillMaxSize(),
                state = state,
                columns = GridCells.Fixed(layout.columns),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                renderCards(items, cardContent)
            }
        }
    }
}

/**
 * Detects when the user scrolls to the end of a lazy list/grid and triggers onLoadMore.
 * Only fires when the last visible item is the actual last item in the list.
 */
@Composable
private fun ScrollEndReached(
    listState: LazyListState?,
    gridState: LazyGridState?,
    isLoading: Boolean,
    hasMorePages: Boolean,
    onEndReached: () -> Unit
) {
    // Handle lazy list (List, HorizontalStrip)
    if (listState != null) {
        LaunchedEffect(listState.isScrollInProgress, listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, listState.layoutInfo.totalItemsCount) {
            if (listState.isScrollInProgress && !isLoading && hasMorePages) {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val totalItems = listState.layoutInfo.totalItemsCount
                if (lastVisibleIndex == totalItems - 1 && totalItems > 0) {
                    onEndReached()
                }
            }
        }
    }

    // Handle lazy grid (Grid)
    if (gridState != null) {
        LaunchedEffect(gridState.isScrollInProgress, gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, gridState.layoutInfo.totalItemsCount) {
            if (gridState.isScrollInProgress && !isLoading && hasMorePages) {
                val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val totalItems = gridState.layoutInfo.totalItemsCount
                if (lastVisibleIndex == totalItems - 1 && totalItems > 0) {
                    onEndReached()
                }
            }
        }
    }
}

/**
 * Renders a list of cards in a lazy list scope.
 */
private fun LazyListScope.renderCards(
    items: List<Card>,
    cardContent: @Composable (Card) -> Unit
) {
    items(items, key = { it.id }) { card -> cardContent(card) }
}

/**
 * Renders a list of cards in a lazy grid scope.
 */
private fun LazyGridScope.renderCards(
    items: List<Card>,
    cardContent: @Composable (Card) -> Unit
) {
    items(items, key = { it.id }) { card -> cardContent(card) }
}
