package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.structures.IRefreshPager
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.FeedPage
import com.tsutsen.platformplayer.core.model.SourceInfo
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

        private val _enabledSources = MutableStateFlow(emptyList<SourceInfo>())
        override val enabledSources: StateFlow<List<SourceInfo>> = _enabledSources.asStateFlow()

        private var _pagerFlow: PagerFlow<IPlatformContent, Card>? = null

        // Survives across loadInitial calls; auto-update installs can take
        // longer than one launch's work and must not be cancelled.
        private val pluginUpdateScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

                    // Auto-update plugins in the background: check every
                    // enabled source, install anything newer when the user
                    // allows it (Settings > Content > Auto-update plugins).
                    pluginUpdateScope.launch {
                        val updates = StatePlugins.instance.checkForUpdates()
                        if (updates.isEmpty()) return@launch
                        if (!Settings.instance.plugins.autoUpdatePlugins) {
                            Logger.i("EngineHomeRepository", "${updates.size} plugin update(s) available, auto-update off")
                            return@launch
                        }
                        Logger.i("EngineHomeRepository", "Auto-installing ${updates.size} plugin update(s)")
                        StatePlugins.instance.installPlugins(
                            context,
                            this,
                            updates.mapNotNull { it.second.sourceUrl },
                            assumeReinstall = true,
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.w("EngineHomeRepository", "Plugin initialization failed", e)
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
                refreshPager?.onPagerChanged?.subscribe(this) {
                    // A refresh may have replaced _pagerFlow: a late sub-pager
                    // from the OLD load must not clobber the new feed.
                    if (flow !== _pagerFlow) return@subscribe
                    val merged = flow.mergeCurrentResults()
                    if (merged.isNotEmpty()) {
                        Logger.i("EngineHomeRepository", "Merged late source: +${merged.size}, ${flow.items.size} total")
                    }
                    _feed.update {
                        it.copy(
                            items = flow.items,
                            hasMorePages = flow.hasMore,
                            error = flow.error,
                            isLoading = refreshPager.pendingPagers > 0,
                        )
                    }
                }
                val items = flow.loadInitial()
                // TEMP #33: how many video cards lack a thumbnail?
                val noThumb = items.filterIsInstance<com.tsutsen.platformplayer.core.model.VideoCard>().count { it.thumbnailUrl == null }
                Logger.i(
                    "EngineHomeRepository",
                    "Converted to ${items.size} cards (noThumb=$noThumb), hasMore=${flow.hasMore}, pending=${refreshPager?.pendingPagers ?: 0}",
                )
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
                        items = flow.items,
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

        /** Enabled clients → display info (id, name, icon url). */
        private fun publishEnabledSources() {
            _enabledSources.value =
                StatePlatform.instance
                    .getEnabledClients()
                    .map { SourceInfo(it.id, it.name, StatePlugins.instance.getPluginIconUriOrNull(it.id)) }
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
    }
