/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tsutsen.platformplayer.compose.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tsutsen.platformplayer.api.media.models.article.IPlatformArticle
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.models.post.IPlatformPost
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSWeb
import com.tsutsen.platformplayer.compose.feed.FeedItem
import com.tsutsen.platformplayer.compose.navigation.GrayjayNavigator
import com.tsutsen.platformplayer.compose.util.LoadingContent
import com.tsutsen.platformplayer.compose.util.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.LayoutMode
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.model.VideoCard

/**
 * Home screen composable using ViewModel + StateFlow pattern.
 * Demonstrates the recommended architecture for Compose screens.
 */
@Composable
fun HomeScreen(
    navigator: GrayjayNavigator,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    
    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                LoadingContent(
                    loading = true,
                    empty = false,
                    emptyContent = {},
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
            is HomeUiState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyState(
                        message = "Add sources to see content here",
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    FeedContent(
                        items = state.items,
                        contentList = state.contentList,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onItemClicked = { id ->
                            val content = state.contentList.find { it.id?.value == id }
                            when (content) {
                                is IPlatformVideo -> navigator.navigateToVideo(content.url)
                                is IPlatformPlaylist -> navigator.navigateToPlaylist(content.url)
                                is IPlatformPost -> navigator.navigateToPost(content.url)
                                is IPlatformArticle -> navigator.navigateToArticle(content.url)
                                is JSWeb -> navigator.navigateToWeb(content.url)
                                else -> {}
                            }
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
            is HomeUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = viewModel::loadFeed,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Error state composable with retry button.
 */
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error: $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Feed content using VideoContainer.
 */
@Composable
private fun FeedContent(
    items: List<FeedItem>,
    contentList: List<IPlatformContent>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cards: List<VideoCard> = items.map { item ->
        val content = contentList.find { it.id?.value == item.id }
        if (content != null) {
            item.toVideoCardWithMetadata(content)
        } else {
            item.toVideoCard()
        }
    }
    
    VideoContainer(
        items = cards,
        layoutMode = LayoutMode.List,
        onCardClick = { card ->
            val content = contentList.find { it.id?.value == card.id }
            if (content != null) {
                onItemClicked(card.id)
            }
        },
        onEndReached = onLoadMore,
        modifier = modifier,
        contentPadding = PaddingValues(8.dp)
    ) { card ->
        VideoCard(
            card = card as VideoCard,
            onClick = { onItemClicked(card.id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
