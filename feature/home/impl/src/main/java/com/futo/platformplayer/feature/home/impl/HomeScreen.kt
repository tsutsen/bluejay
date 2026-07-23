package com.futo.platformplayer.feature.home.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.futo.platformplayer.core.designsystem.component.ErrorState
import com.futo.platformplayer.core.designsystem.component.EmptyState
import com.futo.platformplayer.core.designsystem.component.LayoutMode
import com.futo.platformplayer.core.designsystem.component.VideoCard
import com.futo.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.futo.platformplayer.core.designsystem.component.VideoContainer
import com.futo.platformplayer.core.model.Card
import com.futo.platformplayer.core.model.VideoCard
import com.futo.platformplayer.core.navigation.Navigator

/**
 * Home feed screen.
 * Displays a scrollable feed of recommended videos with infinite scroll.
 * Portrait: single-column list. Landscape: 3-column grid.
 */
@Composable
fun HomeScreen(
    navigator: Navigator,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isWide = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
            adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    when (val state = uiState) {
        is HomeUiState.Initial -> {
            VideoCardSkeleton(count = 6)
        }
        is HomeUiState.Loading -> {
            VideoCardSkeleton(count = 6)
        }
        is HomeUiState.Loaded -> {
            if (state.items.isEmpty() && !state.isLoading && state.error == null) {
                EmptyState(
                    message = "No content yet",
                    actionLabel = "Tap to refresh",
                    onAction = { viewModel.refresh() }
                )
            } else if (state.error != null) {
                ErrorState(
                    message = state.error,
                    onRetry = { viewModel.retry() }
                )
            } else {
                HomeFeedContent(
                    cards = state.items,
                    isLoading = state.isLoading,
                    hasMorePages = state.hasMorePages,
                    isWide = isWide,
                    onCardClick = { card ->
                        if (card is VideoCard) navigator.navigateToVideo(card.url)
                    },
                    onLoadMore = { viewModel.loadNextPage() }
                )
            }
        }
        is HomeUiState.Error -> {
            ErrorState(
                message = state.message,
                onRetry = { viewModel.retry() }
            )
        }
    }
}

/**
 * Home feed content with orientation-aware layout.
 * Portrait: single-column list. Landscape: 3-column grid.
 */
@Composable
private fun HomeFeedContent(
    cards: List<Card>,
    isLoading: Boolean,
    hasMorePages: Boolean,
    isWide: Boolean,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutMode = if (isWide) LayoutMode.Grid else LayoutMode.List
    val columns = if (isWide) 3 else 1

    Box(modifier = modifier.fillMaxSize()) {
        VideoContainer(
            items = cards,
            layoutMode = layoutMode,
            columns = columns,
            onCardClick = onCardClick,
            onEndReached = {
                if (isLoading && hasMorePages) {
                    onLoadMore()
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { card ->
            VideoCard(
                card = card as VideoCard,
                onClick = { onCardClick(card) }
            )
        }

        // Show loading indicator at bottom when loading more pages
        if (isLoading && hasMorePages && cards.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Could add a circular progress indicator here
            }
        }
    }
}
