/*
 * Subscriptions ViewModel
 *
 * Manages the Subscriptions tab: creator list, filtered video feed,
 * and filter state (creator, watched/continue, video/stream type, source).
 */

package com.tsutsen.platformplayer.compose.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.compose.feed.FeedItem
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateSubscriptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SubscriptionsViewModel"

/**
 * Creator (channel) entry for the avatar strip.
 */
data class SubscriptionCreator(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long?,
    val url: String,
    val hasNewContent: Boolean = false
)

/**
 * UI state for the Subscriptions screen.
 */
sealed interface SubscriptionsUiState {
    object Loading : SubscriptionsUiState
    data class Success(
        val items: List<FeedItem> = emptyList(),
        val contentList: List<IPlatformContent> = emptyList(),
        val creators: List<SubscriptionCreator> = emptyList(),
        val activeCreatorId: String? = null,
        val filterWatched: Boolean = true,
        val filterContinue: Boolean = false,
        val filterVideo: Boolean = true,
        val filterStreams: Boolean = false,
        val sourceFilters: Map<String, Boolean> = emptyMap(),
        val isLoading: Boolean = false,
        val error: String? = null
    ) : SubscriptionsUiState
    data class Error(val message: String) : SubscriptionsUiState
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<SubscriptionsUiState>(SubscriptionsUiState.Loading)
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private var feedPager: IPager<IPlatformContent>? = null
    private var allContent: List<IPlatformContent> = emptyList()

    init {
        loadCreators()
    }

    // ==================== Loading ====================

    /**
     * Load the list of subscribed creators from StateSubscriptions.
     */
    fun loadCreators() {
        viewModelScope.launch {
            try {
                Logger.i(TAG, "Loading creators...")
                val subs = StateSubscriptions.instance.getSubscriptions()
                val creators = subs.mapNotNull { sub ->
                    val channel = sub.channel
                    SubscriptionCreator(
                        id = channel.url,
                        name = channel.name,
                        thumbnailUrl = channel.thumbnail,
                        subscriberCount = channel.subscribers,
                        url = channel.url,
                        hasNewContent = false
                    )
                }
                
                // Transition to Success state with creators loaded
                val newState = SubscriptionsUiState.Success(
                    creators = creators,
                    isLoading = false
                )
                _uiState.value = newState
                
                // Now load the feed
                loadFeed()
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading creators", e)
                _uiState.value = SubscriptionsUiState.Error(e.message ?: "Failed to load creators")
            }
        }
    }

    /**
     * Load the subscription feed from all subscribed channels.
     */
    fun loadFeed() {
        viewModelScope.launch {
            try {
                Logger.i(TAG, "Loading subscription feed...")
                val pager = StateSubscriptions.instance.getGlobalSubscriptionFeed(
                    viewModelScope,
                    updated = false
                )
                feedPager = pager
                val loaded = pager.getResults()
                allContent = loaded
                Logger.i(TAG, "Loaded ${loaded.size} subscription items")
                applyFilters()
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading subscription feed", e)
                _uiState.value = SubscriptionsUiState.Error(e.message ?: "Failed to load subscriptions")
            }
        }
    }

    /**
     * Refresh the feed by re-fetching from all subscribed channels.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                Logger.i(TAG, "Refreshing subscription feed...")
                feedPager?.nextPage()
                val loaded = feedPager?.getResults() ?: emptyList()
                allContent = loaded
                applyFilters()
            } catch (e: Exception) {
                Logger.e(TAG, "Error refreshing feed", e)
            }
        }
    }

    // ==================== Filters ====================

    /**
     * Select a creator to filter by. null = show all.
     */
    fun selectCreator(creatorId: String?) {
        _uiState.update { state ->
            when (state) {
                is SubscriptionsUiState.Success -> state.copy(activeCreatorId = creatorId)
                else -> state
            }
        }
        applyFilters()
    }

    /**
     * Toggle "Watched" filter (videos watched ≥95%).
     */
    fun toggleWatched() {
        _uiState.update { state ->
            when (state) {
                is SubscriptionsUiState.Success -> {
                    if (state.filterWatched) {
                        state.copy(
                            filterWatched = false,
                            filterContinue = !state.filterContinue
                        )
                    } else {
                        state.copy(filterWatched = true)
                    }
                }
                else -> state
            }
        }
        applyFilters()
    }

    /**
     * Toggle "Continue" filter (videos with 1s < watchtime < 95%).
     */
    fun toggleContinue() {
        _uiState.update { state ->
            when (state) {
                is SubscriptionsUiState.Success -> {
                    if (state.filterContinue) {
                        state.copy(
                            filterContinue = false,
                            filterWatched = !state.filterWatched
                        )
                    } else {
                        state.copy(filterContinue = true, filterWatched = false)
                    }
                }
                else -> state
            }
        }
        applyFilters()
    }

    /**
     * Toggle "Video" filter (regular videos).
     */
    fun toggleVideo() {
        _uiState.update { state ->
            when (state) {
                is SubscriptionsUiState.Success -> {
                    if (state.filterVideo) {
                        state.copy(
                            filterVideo = false,
                            filterStreams = !state.filterStreams
                        )
                    } else {
                        state.copy(filterVideo = true)
                    }
                }
                else -> state
            }
        }
        applyFilters()
    }

    /**
     * Toggle "Streams" filter (live/recent streams).
     */
    fun toggleStreams() {
        _uiState.update { state ->
            when (state) {
                is SubscriptionsUiState.Success -> {
                    if (state.filterStreams) {
                        state.copy(
                            filterStreams = false,
                            filterVideo = !state.filterVideo
                        )
                    } else {
                        state.copy(filterStreams = true, filterVideo = false)
                    }
                }
                else -> state
            }
        }
        applyFilters()
    }

    /**
     * Toggle a source filter (e.g., YouTube, SoundCloud).
     */
    fun toggleSourceFilter(sourceId: String) {
        _uiState.update { state ->
            when (state) {
                is SubscriptionsUiState.Success -> {
                    val currentSources = state.sourceFilters.toMutableMap()
                    val newValue = !(currentSources[sourceId] ?: true)
                    currentSources[sourceId] = newValue
                    state.copy(sourceFilters = currentSources)
                }
                else -> state
            }
        }
        applyFilters()
    }

    // ==================== Internal ====================

    /**
     * Apply current filters to allContent and update the displayed items.
     */
    private fun applyFilters() {
        val state = _uiState.value
        if (state !is SubscriptionsUiState.Success) return

        var filtered = allContent

        // Filter by selected creator
        val activeCreatorId = state.activeCreatorId
        if (activeCreatorId != null) {
            filtered = filtered.filter { content ->
                val channelUrl = when (content) {
                    is IPlatformVideo -> content.author?.url
                    else -> null
                }
                channelUrl == activeCreatorId
            }
        }

        // Filter by type (video vs streams)
        if (state.filterVideo && !state.filterStreams) {
            filtered = filtered.filter { it is IPlatformVideo && !it.isLive }
        } else if (state.filterStreams && !state.filterVideo) {
            filtered = filtered.filter {
                (it as? IPlatformVideo)?.isLive == true ||
                (it as? IPlatformVideo)?.isShort == true
            }
        }

        // Convert to FeedItems
        val feedItems = filtered.map { toFeedItem(it) }

        _uiState.value = state.copy(
            items = feedItems,
            contentList = filtered,
            isLoading = false,
            error = null
        )
    }

    /**
     * Convert IPlatformContent to FeedItem for display.
     */
    private fun toFeedItem(content: IPlatformContent): FeedItem {
        val thumbnailUrl = when (content) {
            is IPlatformVideo -> content.thumbnails.getHQThumbnail()
            else -> null
        }
        return FeedItem(
            id = content.id?.value ?: "",
            title = content.name ?: "",
            subtitle = content.author?.name,
            thumbnailUrl = thumbnailUrl,
            timestamp = null
        )
    }
}
