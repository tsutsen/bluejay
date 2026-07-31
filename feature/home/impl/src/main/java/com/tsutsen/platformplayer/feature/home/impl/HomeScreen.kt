package com.tsutsen.platformplayer.feature.home.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel

/**
 * Home feed screen.
 * Displays a scrollable feed of recommended videos with infinite scroll.
 * Portrait: single-column list. Landscape: 3-column grid.
 */
@Composable
fun HomeScreen(
    navigator: Navigator,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
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
                        if (card is VideoCard) {
                            android.util.Log.i("HomeScreen", "Video clicked: ${card.title}, URL: ${card.url}")
                            playerViewModel.play(card.url)
                        }
                    },
                    onLoadMore = { viewModel.loadNextPage() },
                    onRefresh = { viewModel.refresh() }
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
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshingState = rememberPullToRefreshState()

    // Reset refresh state when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = refreshingState,
            onRefresh = {
                isRefreshing = true
                onRefresh()
            },
            content = {
                VideoContainer(
                    items = cards,
                    layout = if (isWide) ContainerLayout.Grid(3) else ContainerLayout.List,
                    isLoading = isLoading,
                    hasMorePages = hasMorePages,
                    onCardClick = onCardClick,
                    onLoadMore = onLoadMore,
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
        )
    }
}
