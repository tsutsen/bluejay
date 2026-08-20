package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.FeedPage
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HomeRepository implementation that bridges to the engine (StatePlatform).
 * This implementation is used in the app module where engine APIs are available.
 */
@Singleton
class EngineHomeRepositoryImpl
    @Inject
    constructor() : HomeRepository {
        private val _feed = MutableStateFlow(FeedPage())
        override val feed: StateFlow<FeedPage> = _feed.asStateFlow()

        private var _pagerFlow: PagerFlow<IPlatformContent, Card>? = null

        // Single-flight: loadInitial re-runs the full plugin init + client
        // enable + home fetch, so overlapping calls (e.g. refresh() racing a
        // fresh init) double the work. Concurrent callers bail out; the
        // in-flight call publishes the result.
        private val loadInitialInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

        override suspend fun loadInitial() {
            if (!loadInitialInFlight.compareAndSet(false, true)) {
                Logger.i("EngineHomeRepository", "loadInitial already in flight, skipping")
                return
            }
            try {
                doLoadInitial()
            } finally {
                loadInitialInFlight.set(false)
            }
        }

        private suspend fun doLoadInitial() {
            Logger.i("EngineHomeRepository", "loadInitial")
            _feed.update { it.copy(isLoading = true, error = null) }

            // Ensure plugins are initialized before loading feed
            try {
                val context = StateApp.instance.contextOrNull
                if (context != null) {
                    StatePlugins.instance.updateEmbeddedPlugins(context)
                    StatePlugins.instance.installMissingEmbeddedPlugins(context)
                    StatePlatform.instance.updateAvailableClients(context)

                    // Ensure YouTube is enabled by default
                    val youtubeClient =
                        StatePlatform.instance.getAvailableClients().find {
                            it.name.contains("Youtube", ignoreCase = true)
                        }
                    if (youtubeClient != null && !StatePlatform.instance.isClientEnabled(youtubeClient)) {
                        Logger.i("EngineHomeRepository", "Enabling YouTube by default")
                        StatePlatform.instance.enableClient(listOf(youtubeClient.id))
                    }
                }
            } catch (e: Exception) {
                Logger.w("EngineHomeRepository", "Plugin initialization failed", e)
            }

            try {
                val pager = StatePlatform.instance.getHomeRefresh(CoroutineScope(Dispatchers.IO))
                val flow = PagerFlow(pager, EngineCardMapper::toCard, { it.id })
                _pagerFlow = flow
                val items = flow.loadInitial()
                Logger.i("EngineHomeRepository", "Converted to ${items.size} cards, hasMore=${flow.hasMore}")
                _feed.update {
                    it.copy(
                        isLoading = false,
                        items = items,
                        hasMorePages = flow.hasMore,
                        error = flow.error,
                        currentPage = 1,
                    )
                }
            } catch (e: Exception) {
                Logger.e("EngineHomeRepository", "loadInitial failed", e)
                _feed.update {
                    it.copy(
                        isLoading = false,
                        items = emptyList(),
                        hasMorePages = false,
                        error = e.message ?: "Failed to load feed",
                    )
                }
            }
        }

        override suspend fun loadNextPage() {
            Logger.i("EngineHomeRepository", "loadNextPage")
            _feed.update { it.copy(isLoading = true) }

            try {
                val flow = _pagerFlow ?: return
                Logger.i("EngineHomeRepository", "hasMore=${flow.hasMore}")
                if (!flow.hasMore) {
                    _feed.update { it.copy(isLoading = false) }
                    return
                }

                val newItems = withContext(Dispatchers.IO) { flow.loadNextPage() }
                Logger.i("EngineHomeRepository", "Got ${newItems.size} new items, ${flow.items.size} total, hasMore=${flow.hasMore}")
                _feed.update {
                    it.copy(
                        isLoading = false,
                        items = flow.items,
                        hasMorePages = flow.hasMore,
                        error = flow.error,
                        currentPage = it.currentPage + 1,
                    )
                }
            } catch (e: Exception) {
                Logger.e("EngineHomeRepository", "loadNextPage failed", e)
                _feed.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load more")
                }
            }
        }

        override suspend fun refresh() {
            Logger.i("EngineHomeRepository", "refresh")
            _pagerFlow = null
            loadInitial()
        }

        override suspend fun filterByTag(tag: String) {
            // TODO: Implement
        }

        override suspend fun filterByAuthor(authorId: String) {
            // TODO: Implement
        }
    }
