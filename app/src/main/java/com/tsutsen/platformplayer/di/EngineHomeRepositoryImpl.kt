package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.structures.IRefreshPager
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.dao.HomeFeedCacheDao
import com.tsutsen.platformplayer.core.model.ArticleCard
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.FeedPage
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.PostCard
import com.tsutsen.platformplayer.core.model.SourceInfo
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    constructor(
        private val homeFeedCacheDao: HomeFeedCacheDao,
        private val historyDao: HistoryDao,
    ) : HomeRepository {
        private val _feed = MutableStateFlow(FeedPage())
        override val feed: StateFlow<FeedPage> = _feed.asStateFlow()

        private val _enabledSources = MutableStateFlow(emptyList<SourceInfo>())
        override val enabledSources: StateFlow<List<SourceInfo>> = _enabledSources.asStateFlow()

        private var _pagerFlow: PagerFlow<IPlatformContent, Card>? = null

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Single-flight: loadInitial re-runs the full plugin init + client
        // enable + home fetch, so overlapping calls (e.g. refresh() racing a
        // fresh init) double the work. Concurrent callers bail out; the
        // in-flight call publishes the result.
        private val loadInitialInFlight =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        // Single-flight: auto-fill and scroll-prefetch can both ask for the
        // next page in the same frame; concurrent nextPage() calls on the
        // same pager corrupt its window. Concurrent callers bail out.
        private val loadNextPageInFlight =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        init {
            // Keep the source chips in sync with runtime plugin toggles.
            StatePlatform.instance.onEnabledClientsChanged.subscribe {
                publishEnabledSources()
            }
        }

        /** @return false when a load was already in flight (and skipped). */
        private suspend fun tryLoadInitial(): Boolean {
            if (!loadInitialInFlight.compareAndSet(false, true)) {
                Logger.i("EngineHomeRepository", "loadInitial already in flight, skipping")
                return false
            }
            try {
                doLoadInitial()
                return true
            } finally {
                loadInitialInFlight.set(false)
            }
        }

        override suspend fun loadInitial() {
            tryLoadInitial()
        }

        private suspend fun doLoadInitial() {
            Logger.i("EngineHomeRepository", "loadInitial")
            _feed.update { it.copy(isLoading = true, error = null) }

            // Engine calls below (plugin init, home pager nextPage) run V8
            // `busy`/runBlocking and must not execute on the main thread —
            // callers reach this via viewModelScope (Dispatchers.Main).
            withContext(Dispatchers.IO) {
            // Cache-first: paint the last successful feed immediately while
            // the refresh below runs. Only for a truly empty feed, so a
            // pull-to-refresh never collapses loaded pages back to page 1.
            if (_feed.value.items.isEmpty()) {
                loadCachedFeed()
            }

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

                    publishEnabledSources()
                }
            } catch (e: Exception) {
                Logger.w("EngineHomeRepository", "Plugin initialization failed", e)
            }

            // Homebrew recommendations (Settings > Content > Recommendations):
            // master toggle ON → build the feed from the enabled sources and
            // cache the merged result; OFF, or every source empty/failed →
            // fall through to the plain home refresh below (today's behavior).
            val recommendations = Settings.instance.recommendations
            if (recommendations.homebrewRecommendations) {
                val merged = HomebrewRecommendations.buildFeed(historyDao, recommendations)
                if (merged.isNotEmpty()) {
                    // No pager backs the merged feed: infinite scroll is off.
                    _pagerFlow = null
                    val visible = applyContentFilters(merged)
                    Logger.i("EngineHomeRepository", "Recommendations feed: ${visible.size} cards")
                    _feed.update {
                        it.copy(
                            isLoading = false,
                            items = visible,
                            hasMorePages = false,
                            error = null,
                            currentPage = 1,
                        )
                    }
                    cacheFeed(visible)
                    return@withContext
                }
                Logger.i("EngineHomeRepository", "Recommendations empty, falling back to home refresh")
            }

            try {
                val pager = StatePlatform.instance.getHomeRefresh(CoroutineScope(Dispatchers.IO))
                val refreshPager = pager as? IRefreshPager<IPlatformContent>
                val flow = PagerFlow(pager, EngineCardMapper::toCard, { it.id })
                _pagerFlow = flow
                // The engine resolves per-source pagers lazily: the first
                // client lands first, the rest join in via onPagerChanged.
                // Merge them into the feed as they arrive (grayjay's
                // FeedView subscribes the same way).
                refreshPager?.onPagerChanged?.subscribe(this@EngineHomeRepositoryImpl) {
                    // A refresh may have replaced _pagerFlow: a late sub-pager
                    // from the OLD load must not clobber the new feed.
                    if (flow !== _pagerFlow) return@subscribe
                    val merged = flow.mergeCurrentResults()
                    if (merged.isNotEmpty()) {
                        Logger.i("EngineHomeRepository", "Merged late source: +${merged.size}, ${flow.items.size} total")
                    }
                    scope.launch {
                        val visible = applyContentFilters(flow.items)
                        _feed.update {
                            it.copy(
                                items = visible,
                                hasMorePages = flow.hasMore,
                                error = flow.error,
                                isLoading = refreshPager.pendingPagers > 0,
                            )
                        }
                    }
                }
                val items = flow.loadInitial()
                // TEMP #33: how many video cards lack a thumbnail?
                val noThumb = items.filterIsInstance<com.tsutsen.platformplayer.core.model.VideoCard>().count { it.thumbnailUrl == null }
                Logger.i(
                    "EngineHomeRepository",
                    "Converted to ${items.size} cards (noThumb=$noThumb), hasMore=${flow.hasMore}, pending=${refreshPager?.pendingPagers ?: 0}",
                )
                // Content filters (hidden/watched/started) apply before emit
                // AND before the cache write, so the Room cache only ever
                // stores what the user actually sees.
                val visible = applyContentFilters(flow.items)
                _feed.update {
                    // flow.items (not the local): onPagerChanged may have
                    // merged a late source's cards in between loadInitial()
                    // and this update — the local would clobber them.
                    // isLoading stays up while late sources are still
                    // pending: the UI shows a spinner (filtered feeds)
                    // instead of flashing "No content yet" until the merge
                    // lands a couple of seconds later.
                    it.copy(
                        isLoading = refreshPager != null && refreshPager.pendingPagers > 0,
                        items = visible,
                        hasMorePages = flow.hasMore,
                        error = flow.error,
                        currentPage = 1,
                    )
                }
                cacheFeed(visible)
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
        }

        private suspend fun loadCachedFeed() {
            try {
                val entities = homeFeedCacheDao.observeValidCache().first()
                val cards = entities.mapNotNull { EngineCardMapper.toCardFromCache(it) }
                if (cards.isEmpty()) return
                val visible = applyContentFilters(cards)
                Logger.i("EngineHomeRepository", "Showing ${visible.size} cached cards")
                _feed.update {
                    it.copy(items = visible, isLoading = true, currentPage = 1)
                }
            } catch (e: Exception) {
                Logger.w("EngineHomeRepository", "Failed to load cached feed", e)
            }
        }

        private suspend fun cacheFeed(cards: List<Card>) {
            if (cards.isEmpty()) return
            try {
                val now = System.currentTimeMillis()
                // The DAO orders by cachedAt DESC and the entity has no
                // position column, so an offset restores the feed order.
                val entities =
                    cards.mapIndexed { index, card ->
                        EngineCardMapper.toCacheEntity(card).copy(cachedAt = now - index)
                    }
                homeFeedCacheDao.clearAll()
                homeFeedCacheDao.insertAll(entities)
                Logger.i("EngineHomeRepository", "Cached ${entities.size} feed cards")
            } catch (e: Exception) {
                Logger.w("EngineHomeRepository", "Failed to cache feed", e)
            }
        }

        /** Enabled clients → display info (id, name, icon url, login state). */
        private fun publishEnabledSources() {
            _enabledSources.value =
                StatePlatform.instance
                    .getEnabledClients()
                    .map { client ->
                        val js = client as? JSClient
                        SourceInfo(
                            id = client.id,
                            name = client.name,
                            iconUrl = StatePlugins.instance.getPluginIconUriOrNull(client.id),
                            supportsLogin = js?.config?.authentication != null,
                            loggedIn = js?.isLoggedIn ?: false,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
        }

        override suspend fun loadNextPage() {
            if (!loadNextPageInFlight.compareAndSet(false, true)) {
                Logger.i("EngineHomeRepository", "loadNextPage already in flight, skipping")
                return
            }
            Logger.i("EngineHomeRepository", "loadNextPage")
            _feed.update { it.copy(isLoading = true) }

            try {
                // Null pager = recommendations mode (merged list, no next page).
                val flow = _pagerFlow
                if (flow == null) {
                    _feed.update { it.copy(isLoading = false) }
                    return
                }
                Logger.i("EngineHomeRepository", "hasMore=${flow.hasMore}")
                if (!flow.hasMore) {
                    _feed.update { it.copy(isLoading = false) }
                    return
                }

                val newItems = withContext(Dispatchers.IO) { flow.loadNextPage() }
                Logger.i("EngineHomeRepository", "Got ${newItems.size} new items, ${flow.items.size} total, hasMore=${flow.hasMore}")
                val visible = applyContentFilters(flow.items)
                _feed.update {
                    it.copy(
                        isLoading = false,
                        items = visible,
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
            } finally {
                loadNextPageInFlight.set(false)
            }
        }

        override suspend fun refresh() {
            Logger.i("EngineHomeRepository", "refresh")
            // A skipped load (one already in flight) must not orphan the
            // current pager: the in-flight run publishes the fresh results.
            tryLoadInitial()
        }

        override suspend fun filterByTag(tag: String) {
            // TODO: Implement
        }

        override suspend fun filterByAuthor(authorId: String) {
            // TODO: Implement
        }

        /**
         * Applies the persisted content settings to a freshly loaded feed
         * page: hidden video urls (long-press "Hide"), plus the watched /
         * started toggles backed by the history table. Runs before emit and
         * before the Room cache write. History is batch-queried once per
         * page, and only when a toggle is actually off.
         */
        private suspend fun applyContentFilters(cards: List<Card>): List<Card> {
            val content = Settings.instance.content
            val hidden = content.hiddenVideoUrls
            val filterWatched = !content.showWatchedVideos
            val filterStarted = !content.showStartedVideos
            if (!filterWatched && !filterStarted && hidden.isEmpty()) return cards
            val history =
                if (filterWatched || filterStarted) {
                    val urls = cards.mapNotNull { cardUrl(it) }
                    if (urls.isEmpty()) {
                        emptyMap()
                    } else {
                        historyDao.getByUrls(urls).associateBy { it.contentUrl }
                    }
                } else {
                    emptyMap()
                }
            return cards.filter { card ->
                val url = cardUrl(card)
                if (url != null && url in hidden) return@filter false
                if (filterWatched || filterStarted) {
                    val h = url?.let { history[it] } ?: return@filter true
                    val duration = h.totalDurationMs
                    // Same watched/started split as the subs feed chips:
                    // watched = >= 95% of duration (position or watched time),
                    // started = in progress but not finished.
                    val watched =
                        duration > 0 &&
                            (h.lastPositionMs >= 0.95 * duration || h.watchedMs >= 0.95 * duration)
                    val started = h.lastPositionMs > 0 && !watched
                    if (filterWatched && watched) return@filter false
                    if (filterStarted && started) return@filter false
                }
                true
            }
        }

        private fun cardUrl(card: Card): String? =
            when (card) {
                is VideoCard -> card.url
                is PlaylistCard -> card.url
                is ChannelCard -> card.url
                is PostCard -> card.url
                is ArticleCard -> card.url
            }
    }
