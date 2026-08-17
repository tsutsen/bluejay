package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SubscriptionsRepository implementation that bridges to the engine (StateSubscriptions).
 * This implementation is used in the app module where engine APIs are available.
 *
 * Raw engine content is accumulated by a [PagerFlow] with an identity map (the
 * source-specific filters operate on raw [IPlatformContent]); [EngineCardMapper]
 * converts the filtered result to Cards at the end.
 */
@Singleton
class EngineSubscriptionsRepositoryImpl
    @Inject
    constructor() : SubscriptionRepository {
        private val _feed = MutableStateFlow(SubscriptionFeed())
        override val feed: StateFlow<SubscriptionFeed> = _feed.asStateFlow()

        private var pagerFlow: PagerFlow<IPlatformContent, IPlatformContent>? = null
        private var isLoadingMore = false

        override suspend fun loadCreators() {
            Logger.i("EngineSubscriptionsRepository", "Loading creators...")
            try {
                val subs = StateSubscriptions.instance.getSubscriptions()
                val creators =
                    subs.mapNotNull { sub ->
                        val channel = sub.channel
                        SubscriptionCreator(
                            id = channel.url,
                            name = channel.name,
                            thumbnailUrl = channel.thumbnail,
                            subscriberCount = channel.subscribers,
                            url = channel.url,
                            hasNewContent = false,
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
                val pager =
                    StateSubscriptions.instance.getGlobalSubscriptionFeed(
                        CoroutineScope(Dispatchers.IO),
                        updated = false,
                    )
                val flow = PagerFlow(pager, { it }, { it.id })
                pagerFlow = flow
                flow.loadInitial()
                Logger.i("EngineSubscriptionsRepository", "Loaded ${flow.items.size} subscription items")
                applyFilters()
            } catch (e: Exception) {
                Logger.e("EngineSubscriptionsRepository", "Error loading subscription feed", e)
            }
        }

        override suspend fun refresh() {
            Logger.i("EngineSubscriptionsRepository", "Refreshing subscription feed...")
            try {
                val pager =
                    StateSubscriptions.instance.getGlobalSubscriptionFeed(
                        CoroutineScope(Dispatchers.IO),
                        updated = true,
                    )
                val flow = PagerFlow(pager, { it }, { it.id })
                pagerFlow = flow
                flow.loadInitial()
                Logger.i("EngineSubscriptionsRepository", "Refreshed: ${flow.items.size} items")
                applyFilters()
            } catch (e: Exception) {
                Logger.e("EngineSubscriptionsRepository", "Error refreshing feed", e)
            }
        }

        override suspend fun loadMore() {
            Logger.i("EngineSubscriptionsRepository", "Loading more subscription items...")
            try {
                val flow = pagerFlow
                if (flow != null && flow.hasMore) {
                    val newItems = flow.loadNextPage()
                    Logger.i("EngineSubscriptionsRepository", "Loaded ${newItems.size} more items (total: ${flow.items.size})")
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

        // Each chip toggles exactly its own flag — no cross-coupling.
        override suspend fun toggleContinue() {
            _feed.update { it.copy(filterContinue = !it.filterContinue) }
            applyFilters()
        }

        override suspend fun toggleVideo() {
            _feed.update { it.copy(filterVideo = !it.filterVideo) }
            applyFilters()
        }

        override suspend fun toggleStreams() {
            _feed.update { it.copy(filterStreams = !it.filterStreams) }
            applyFilters()
        }

        private fun applyFilters() {
            val state = _feed.value
            val flow = pagerFlow
            var filtered: List<IPlatformContent> = flow?.items ?: emptyList()

            // Filter by selected creator
            val activeCreatorId = state.activeCreatorId
            if (activeCreatorId != null) {
                filtered =
                    filtered.filter { content ->
                        val channelUrl =
                            when (content) {
                                is IPlatformVideo -> content.author?.url
                                else -> null
                            }
                        channelUrl == activeCreatorId
                    }

                // If we have fewer than 20 items from this creator, load more
                val f = flow
                if (f != null && filtered.size < 20 && f.hasMore && !isLoadingMore) {
                    isLoadingMore = true
                    try {
                        f.loadNextPage()
                        applyFilters()
                    } finally {
                        isLoadingMore = false
                    }
                }
            }

            // Type filter: "Videos"/"Live" are OR within the category;
            // "Continue" narrows to partially watched videos (AND).
            val showVideos = state.filterVideo
            val showLive = state.filterStreams
            val onlyContinue = state.filterContinue
            filtered =
                filtered.filter { content ->
                    val video = content as? IPlatformVideo ?: return@filter true
                    if (video.isLive) {
                        if (!showLive) return@filter false
                    } else if (!showVideos) {
                        return@filter false
                    }
                    if (onlyContinue) {
                        val d = video.duration
                        val t = video.playbackTime
                        if (d <= 0 || t <= 0 || t >= 0.95 * d) return@filter false
                    }
                    true
                }

            // Convert to Cards
            val cards = EngineCardMapper.toCards(filtered)

            _feed.update {
                it.copy(
                    items = cards,
                    hasMorePages = flow?.hasMore ?: false,
                    isLoading = false,
                    error = flow?.error,
                )
            }
        }
    }
