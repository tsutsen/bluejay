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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.api.media.structures.IRefreshPager
import com.tsutsen.platformplayer.api.media.structures.ReusableRefreshPager
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.core.model.VideoCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HomeViewModel"

/**
 * UI state for the home/feed screen.
 * Uses a sealed interface to represent different loading states.
 */
sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val items: List<VideoCard> = emptyList(),
        val contentList: List<IPlatformContent> = emptyList(),
        val error: String? = null
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/**
 * ViewModel for the home/feed screen.
 * Manages feed loading, refresh, and load-more functionality.
 */
@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState
    
    private var pager: ReusableRefreshPager<IPlatformContent>? = null
    private var contentList: List<IPlatformContent> = emptyList()
    
    init {
        loadFeed()
    }
    
    /**
     * Load the home feed from plugins.
     */
    fun loadFeed() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                Logger.i(TAG, "Loading feed...")
                val p = StatePlatform.instance.getHomeRefresh(viewModelScope)
                Logger.i(TAG, "Got pager: $p")
                
                if (p is IRefreshPager<*>) {
                    val refreshPager = ReusableRefreshPager(p as IRefreshPager<IPlatformContent>)
                    pager = refreshPager
                    refreshPager.nextPage()
                    val loaded = refreshPager.getResults()
                    Logger.i(TAG, "Loaded ${loaded.size} items")
                    
                    contentList = loaded
                    val videoCards = loaded.map { toVideoCard(it) }
                    _uiState.value = HomeUiState.Success(
                        items = videoCards,
                        contentList = loaded
                    )
                } else {
                    Logger.w(TAG, "No refreshable pager: ${p?.javaClass}")
                    _uiState.value = HomeUiState.Success(
                        items = emptyList(),
                        contentList = emptyList()
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading feed", e)
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Refresh the feed by loading the next page.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val currentPager = pager
                if (currentPager != null) {
                    currentPager.nextPage()
                    val loaded = currentPager.getResults()
                    contentList = loaded
                    val videoCards = loaded.map { toVideoCard(it) }
                    _uiState.value = HomeUiState.Success(
                        items = videoCards,
                        contentList = loaded
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error refreshing feed", e)
            }
        }
    }
    
    /**
     * Load more items (pagination).
     */
    fun loadMore() {
        viewModelScope.launch {
            try {
                val currentPager = pager
                if (currentPager != null && currentPager.hasMorePages()) {
                    currentPager.nextPage()
                    val loaded = currentPager.getResults()
                    contentList = loaded
                    val videoCards = loaded.map { toVideoCard(it) }
                    _uiState.value = HomeUiState.Success(
                        items = videoCards,
                        contentList = loaded
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading more", e)
            }
        }
    }
    
    /**
     * Convert IPlatformContent to VideoCard.
     */
    private fun toVideoCard(content: IPlatformContent): VideoCard {
        val video = content as? IPlatformVideo
        val thumbnailUrl = video?.thumbnails?.getHQThumbnail()
        return VideoCard(
            id = content.id?.value ?: "",
            title = content.name ?: "",
            thumbnailUrl = thumbnailUrl,
            author = video?.author?.name,
            durationMs = video?.duration,
            viewCount = video?.viewCount,
            publishedAt = video?.playbackDate?.toEpochSecond(),
            url = ""
        )
    }
}
