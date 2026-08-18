package com.tsutsen.platformplayer.core.designsystem.component

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
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
    data class Grid(
        val columns: Int,
    ) : ContainerLayout

    /**
     * Fixed-height vertical paginated grid.
     * Shows columns × rowsPerPage items in a grid.
     * Container has fixed height, not fillMaxSize.
     */
    data class PaginatedVertical(
        val columns: Int,
        val rowsPerPage: Int,
    ) : ContainerLayout

    /**
     * Fixed-width horizontal paginated grid.
     * Shows columns × rowsPerPage items in a grid.
     * Container has fixed width, not fillMaxSize.
     */
    data class PaginatedHorizontal(
        val columns: Int,
        val rowsPerPage: Int,
    ) : ContainerLayout
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
 * @param trailingContent Optional extra item rendered after all cards (e.g. an "All (n)" card)
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
    contentPadding: PaddingValues = PaddingValues(Tokens.SpaceLg),
    trailingContent: (@Composable () -> Unit)? = null,
    topContent: (@Composable () -> Unit)? = null,
    cardContent: @Composable (Card) -> Unit,
) {
    when (layout) {
        is ContainerLayout.List -> {
            val state = rememberLazyListState()
            ScrollEndReached(
                listState = state,
                gridState = null,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = 3,
                onEndReached = onLoadMore,
            )
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg),
            ) {
                if (topContent != null) {
                    item(key = "__top__") { topContent() }
                }
                renderCards(items, cardContent, trailingContent)
            }
        }

        is ContainerLayout.HorizontalStrip -> {
            val state = rememberLazyListState()
            ScrollEndReached(
                listState = state,
                gridState = null,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = 3,
                onEndReached = onLoadMore,
            )
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                state = state,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            ) {
                renderCards(items, cardContent, trailingContent)
            }
        }

        is ContainerLayout.Grid -> {
            val state = rememberLazyGridState()
            ScrollEndReached(
                listState = null,
                gridState = state,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = 3,
                onEndReached = onLoadMore,
            )
            LazyVerticalGrid(
                modifier = modifier.fillMaxSize(),
                state = state,
                columns = GridCells.Fixed(layout.columns),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            ) {
                if (topContent != null) {
                    item(key = "__top__") { topContent() }
                }
                renderCards(items, cardContent, trailingContent)
            }
        }

        is ContainerLayout.PaginatedVertical -> {
            val state = rememberLazyGridState()
            // Threshold = page size (columns × rowsPerPage) so load triggers one page before end
            val pageSize = layout.columns * layout.rowsPerPage
            ScrollEndReached(
                listState = null,
                gridState = state,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = pageSize,
                onEndReached = onLoadMore,
            )
            LazyVerticalGrid(
                modifier = modifier.fillMaxWidth(),
                state = state,
                columns = GridCells.Fixed(layout.columns),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }

        is ContainerLayout.PaginatedHorizontal -> {
            val state = rememberLazyGridState()
            // Threshold = page size (columns × rowsPerPage) so load triggers one page before end
            val pageSize = layout.columns * layout.rowsPerPage
            ScrollEndReached(
                listState = null,
                gridState = state,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = pageSize,
                onEndReached = onLoadMore,
            )
            LazyHorizontalGrid(
                modifier = modifier.fillMaxHeight(),
                state = state,
                rows = GridCells.Fixed(layout.rowsPerPage),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }
    }
}

/**
 * Detects when the user scrolls near the end of a lazy list/grid and triggers onLoadMore.
 * Fires when the last visible item is within `threshold` items of the end.
 *
 * @param itemCount Number of items (used as LaunchedEffect key to re-evaluate when items are added)
 * @param threshold Number of items before the end to trigger loading (default: 1)
 */
@Composable
fun ScrollEndReached(
    listState: LazyListState?,
    gridState: LazyGridState?,
    itemCount: Int,
    isLoading: Boolean,
    hasMorePages: Boolean,
    threshold: Int = 1,
    onEndReached: () -> Unit,
) {
    // Handle lazy list (List, HorizontalStrip)
    if (listState != null) {
        LaunchedEffect(listState.isScrollInProgress, itemCount) {
            if (listState.isScrollInProgress && !isLoading && hasMorePages) {
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: -1
                val totalItems = listState.layoutInfo.totalItemsCount
                if (lastVisibleIndex >= totalItems - threshold && totalItems > 0) {
                    onEndReached()
                }
            }
        }
    }

    // Handle lazy grid (Grid)
    if (gridState != null) {
        LaunchedEffect(gridState.isScrollInProgress, itemCount) {
            if (gridState.isScrollInProgress && !isLoading && hasMorePages) {
                val lastVisibleIndex =
                    gridState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: -1
                val totalItems = gridState.layoutInfo.totalItemsCount
                if (lastVisibleIndex >= totalItems - threshold && totalItems > 0) {
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
    cardContent: @Composable (Card) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    items(items, key = { it.id }) { card -> cardContent(card) }
    if (trailingContent != null) {
        item(key = "__trailing__") { trailingContent() }
    }
}

/**
 * Renders a list of cards in a lazy grid scope.
 */
private fun LazyGridScope.renderCards(
    items: List<Card>,
    cardContent: @Composable (Card) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    items(items, key = { it.id }) { card -> cardContent(card) }
    if (trailingContent != null) {
        item(key = "__trailing__") { trailingContent() }
    }
}
