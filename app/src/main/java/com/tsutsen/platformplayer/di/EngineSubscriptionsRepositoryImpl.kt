package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SubscriptionsRepository implementation that bridges to the engine (StateSubscriptions).
 * This implementation is used in the app module where engine APIs are available.
 */
@Singleton
class EngineSubscriptionsRepositoryImpl @Inject constructor() : SubscriptionRepository {

    private val _feed = MutableStateFlow(SubscriptionFeed())
    override val feed: StateFlow<SubscriptionFeed> = _feed.asStateFlow()

    private var feedPager: com.tsutsen.platformplayer.api.media.structures.IPager<com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent>? = null
    private var allContent: List<com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent> = emptyList()
    private var isLoadingMore = false

    override suspend fun loadCreators() {
        Logger.i("EngineSubscriptionsRepository", "Loading creators...")
        try {
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
            _feed.update { it.copy(creators = creators) }
            loadFeed()
        } catch (e: Exception) {
            Logger.e("EngineSubscriptionsRepository", "Error loading creators", e)
        }
    }

    override suspend fun loadFeed() {
        Logger.i("EngineSubscriptionsRepository", "Loading subscription feed...")
        try {
            val pager = StateSubscriptions.instance.getGlobalSubscriptionFeed(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
                updated = false
            )
            feedPager = pager
            val loaded = pager.getResults()
            allContent = loaded
            Logger.i("EngineSubscriptionsRepository", "Loaded ${loaded.size} subscription items")
            applyFilters()
        } catch (e: Exception) {
            Logger.e("EngineSubscriptionsRepository", "Error loading subscription feed", e)
        }
    }

    override suspend fun refresh() {
        Logger.i("EngineSubscriptionsRepository", "Refreshing subscription feed...")
        try {
            val pager = StateSubscriptions.instance.getGlobalSubscriptionFeed(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
                updated = true
            )
            feedPager = pager
            val loaded = pager.getResults()
            allContent = loaded
            Logger.i("EngineSubscriptionsRepository", "Refreshed: ${loaded.size} items")
            applyFilters()
        } catch (e: Exception) {
            Logger.e("EngineSubscriptionsRepository", "Error refreshing feed", e)
        }
    }

    override suspend fun loadMore() {
        Logger.i("EngineSubscriptionsRepository", "Loading more subscription items...")
        try {
            val currentPager = feedPager
            if (currentPager != null && currentPager.hasMorePages()) {
                currentPager.nextPage()
                val newItems = currentPager.getResults()
                allContent = allContent + newItems
                Logger.i("EngineSubscriptionsRepository", "Loaded ${newItems.size} more items (total: ${allContent.size})")
                applyFilters()
            }
        } catch (e: Exception) {
            Logger.e("EngineSubscriptionsRepository", "Error loading more", e)
        }
    }

    override suspend fun selectCreator(creatorId: String?) {
        _feed.update { it.copy(activeCreatorId = creatorId) }
        applyFilters()
    }

    override suspend fun toggleWatched() {
        _feed.update { state ->
            if (state.filterWatched) {
                state.copy(
                    filterWatched = false,
                    filterContinue = !state.filterContinue
                )
            } else {
                state.copy(filterWatched = true)
            }
        }
        applyFilters()
    }

    override suspend fun toggleContinue() {
        _feed.update { state ->
            if (state.filterContinue) {
                state.copy(
                    filterContinue = false,
                    filterWatched = !state.filterWatched
                )
            } else {
                state.copy(filterContinue = true, filterWatched = false)
            }
        }
        applyFilters()
    }

    override suspend fun toggleVideo() {
        _feed.update { state ->
            if (state.filterVideo) {
                state.copy(
                    filterVideo = false,
                    filterStreams = !state.filterStreams
                )
            } else {
                state.copy(filterVideo = true)
            }
        }
        applyFilters()
    }

    override suspend fun toggleStreams() {
        _feed.update { state ->
            if (state.filterStreams) {
                state.copy(
                    filterStreams = false,
                    filterVideo = !state.filterVideo
                )
            } else {
                state.copy(filterStreams = true, filterVideo = false)
            }
        }
        applyFilters()
    }

    override suspend fun toggleSourceFilter(sourceId: String) {
        _feed.update { state ->
            val currentSources = state.sourceFilters.toMutableMap()
            val newValue = !(currentSources[sourceId] ?: true)
            currentSources[sourceId] = newValue
            state.copy(sourceFilters = currentSources)
        }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _feed.value
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

            // If we have fewer than 20 items from this creator, load more
            if (filtered.size < 20 && feedPager?.hasMorePages() == true && !isLoadingMore) {
                isLoadingMore = true
                try {
                    feedPager?.nextPage()
                    val newItems = feedPager?.getResults() ?: emptyList()
                    allContent = allContent + newItems
                    applyFilters()
                } catch (e: Exception) {
                    Logger.e("EngineSubscriptionsRepository", "Error loading more items for filter", e)
                } finally {
                    isLoadingMore = false
                }
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

        // Convert to VideoCards
        val videoCards = filtered.map { toVideoCard(it) }

        _feed.update {
            it.copy(
                items = videoCards,
                isLoading = false,
                error = null
            )
        }
    }

    private fun toVideoCard(content: com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent): VideoCard {
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
            url = content.url ?: ""
        )
    }
}
