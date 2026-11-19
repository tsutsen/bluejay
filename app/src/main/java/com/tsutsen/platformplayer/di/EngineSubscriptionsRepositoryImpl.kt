package com.tsutsen.platformplayer.di
import com.tsutsen.platformplayer.di.SettingsRepositoryImpl

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.media.Serializer
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformContent
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.models.Subscription
import com.tsutsen.platformplayer.states.EnabledClientResolver
import com.tsutsen.platformplayer.states.*
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StateSubscriptions
import com.tsutsen.platformplayer.stores.FragmentedStorage
import com.tsutsen.platformplayer.stores.StringStorage
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/** On-disk payload for the Subs tab feed cache (file store: no Room bump). */
@Serializable
internal data class SubsFeedCache(
    val cachedAt: Long = 0L,
    val items: List<SerializedPlatformContent> = emptyList(),
)

/** File-store key of the subs feed cache (also read by HomebrewRecommendations source 2). */
internal const val SUBS_FEED_CACHE_KEY = "subscriptions_feed_cache"

/** Freshness hint (lastSyncedAt indicators); stale caches still paint. */
internal const val SUBS_FEED_CACHE_TTL_MS = 60L * 60L * 1000L

/**
 * The subs feed file cache when still fresh (< [SUBS_FEED_CACHE_TTL_MS]),
 * else null. Read by HomebrewRecommendations (source 2 seeds from it).
 * With [allowStale] the cache is returned regardless of age — the Subs
 * tab paints it stale-while-revalidate while the network verify runs.
 */
internal suspend fun readFreshSubsFeedCache(allowStale: Boolean = false): List<IPlatformContent>? =
    try {
        val raw = FragmentedStorage.get<StringStorage>(SUBS_FEED_CACHE_KEY).value
        if (raw.isBlank()) return null // StringStorage default is ""
        val payload = Serializer.json.decodeFromString<SubsFeedCache>(raw)
        if ((!allowStale &&
                System.currentTimeMillis() - payload.cachedAt > SUBS_FEED_CACHE_TTL_MS) ||
            payload.items.isEmpty()) {
            null
        } else {
            payload.items.toList()
        }
    } catch (e: Exception) {
        Logger.w("SubsFeedCache", "Failed to load cached feed", e)
        null
    }

/**
 * Bridges the engine subscription machinery (StateSubscriptions +
 * StatePlatform) into the [SubscriptionRepository] contract.
 *
 * Feed scope follows the selected creator: null = the global subscription
 * feed (all channels merged); a creator id = that channel's own video
 * pager (the same pager the channel screen uses). Switching scope builds
 * a fresh pager — filtering the accumulated global pages by one channel is
 * what used to leave the selected channel empty until a manual refresh.
 *
 * Raw engine content is accumulated by a [PagerFlow] (the filters operate
 * on raw [IPlatformContent] fields); [EngineCardMapper] converts to Cards
 * at the end. Pager construction and page loads run source-plugin JS,
 * which the V8 engine refuses to run on the main thread — every engine
 * call is dispatched to IO.
 */
@Singleton
class EngineSubscriptionsRepositoryImpl
    @Inject
    constructor(
        private val historyDao: HistoryDao,
    ) : SubscriptionRepository {
        // Starts "loading" so the screen shows a spinner from the first
        // frame instead of a momentary empty state. hasMorePages=true from
        // the start: the overdrag gesture must be armed even before the
        // cache-first paint lands (a load-more arriving this early is
        // queued by loadMore() and replayed when the fresh pager is ready).
        private val _feed = MutableStateFlow(SubscriptionFeed(isLoading = true, hasMorePages = true))
        override val feed: StateFlow<SubscriptionFeed> = _feed.asStateFlow()

        // The subscription-change listener lives outside any ViewModel.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private var flow: PagerFlow<IPlatformContent, IPlatformContent>? = null

        // Demo mode (Settings > About): last generated demo page for
        // loadMore(); demo content never touches the engine or the cache.
        private var demoPage = 0

        // File-backed cache of the last loaded global feed; painted on launch
        // while a fresh fetch runs (same UX as the home feed's Room cache).
        private val feedCache by lazy { FragmentedStorage.get<StringStorage>(SUBS_FEED_CACHE_KEY) }

        // Cached items currently backing the UI (initial load in flight or a
        // failed refresh); cleared once fresh data lands.
        private var cachedItems: List<IPlatformContent>? = null

        init {
            // Subscribing/unsubscribing from anywhere (channel screen,
            // search) must update the sidebar immediately, not on relaunch.
            StateSubscriptions.instance.onSubscriptionsChanged.subscribe { _, _ ->
                scope.launch { resyncSubscriptions() }
            }
        }

        override suspend fun loadCreators() {
            Logger.i(TAG, "Loading creators...")
            // Demo mode: simulated creators + demo feed, no engine calls.
            if (com.tsutsen.platformplayer.demo.DemoContent.demoModeEnabled()) {
                loadDemoFeed(reset = true)
                return
            }
            try {
                val creators = scanCreators()
                _feed.update { it.copy(creators = creators) }
                loadFeed()
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading creators", e)
            }
        }

        override suspend fun loadFeed() {
            reload(updated = false)
        }

        override suspend fun refresh() {
            reload(updated = true)
        }

        // A load-more that arrived while the fresh fetch was still building
        // its pager (cache-first paint, no flow yet): replayed once the
        // fresh pager is ready.
        @Volatile private var pendingLoadMore = false

        override suspend fun loadMore() {
            // Demo mode: append the next deterministic demo page, no engine
            // calls, no cache write.
            if (com.tsutsen.platformplayer.demo.DemoContent.demoModeEnabled()) {
                loadDemoFeed(reset = false)
                return
            }
            withContext(Dispatchers.IO) {
                val f = flow
                if (f != null && f.hasMore) {
                    // Drive the load-more footer indicator.
                    _feed.update { it.copy(isLoading = true) }
                    f.loadNextPage()
                    Logger.i(TAG, "Loaded more (total: ${f.items.size})")
                    applyFilters()
                    // Only the global feed pages belong in the feed cache —
                    // a per-channel pager's pages must not overwrite it.
                    if (_feed.value.activeCreatorId == null) saveFeedCache(f.items)
                } else if (_feed.value.isLoading) {
                    // The fresh fetch is still initializing — queue the
                    // overdrag instead of dropping it on the floor.
                    Logger.i(TAG, "loadMore during initial fetch: queued")
                    pendingLoadMore = true
                }
            }
        }

        /** Replays a load-more queued while the fresh pager was building. */
        private suspend fun runPendingLoadMore() {
            if (!pendingLoadMore) return
            pendingLoadMore = false
            val f = flow ?: return
            if (f.hasMore) {
                _feed.update { it.copy(isLoading = true) }
                f.loadNextPage()
                Logger.i(TAG, "Loaded queued more (total: ${f.items.size})")
                applyFilters()
                if (_feed.value.activeCreatorId == null) saveFeedCache(f.items)
            }
        }

        override suspend fun selectCreator(creatorId: String?) {
            if (creatorId == _feed.value.activeCreatorId) return
            _feed.update {
                it.copy(activeCreatorId = creatorId, items = emptyList(), isLoading = true)
            }
            // Demo mode: re-generate the demo cards scoped to the demo
            // channel (demo://channel/<n>), no engine calls.
            if (com.tsutsen.platformplayer.demo.DemoContent.demoModeEnabled()) {
                loadDemoFeed(reset = true)
                return
            }
            withContext(Dispatchers.IO) {
                try {
                    // A fresh pager per scope: the previous scope's pages
                    // don't apply to this one.
                    flow = newFlow(updated = false)
                    flow!!.loadInitial()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error loading creator feed", e)
                    _feed.update { it.copy(isLoading = false, error = e.message) }
                    return@withContext
                }
                applyFilters()
            }
        }

        // Independent toggles, both ON by default (no filtering).
        override suspend fun toggleStarted() {
            _feed.update { it.copy(filterStarted = !it.filterStarted) }
            applyFilters()
        }

        override suspend fun toggleWatched() {
            _feed.update { it.copy(filterWatched = !it.filterWatched) }
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

        override suspend fun toggleShorts() {
            _feed.update { it.copy(filterShorts = !it.filterShorts) }
            applyFilters()
        }

        /** Rebuilds the pager for the current scope and applies filters. */
        // History snapshot for the started/watched filters — refreshed on
        // every reload; plugin items carry playbackTime = -1, so watch state
        // must come from the history table.
        @Volatile private var historyByUrl: Map<String, HistoryEntity> = emptyMap()

        private suspend fun reload(updated: Boolean) {
            // Demo mode: simulated feed, no engine calls, no cache write.
            if (com.tsutsen.platformplayer.demo.DemoContent.demoModeEnabled()) {
                loadDemoFeed(reset = true)
                return
            }
            // Revisit (tab re-entry, not pull-to-refresh) with the engine's
            // global feed still in memory: the pager window is emitted
            // directly once loadInitial resumes — no spinner flash, no cache
            // repaint.
            val preloaded =
                !updated &&
                    _feed.value.activeCreatorId == null &&
                    StateSubscriptions.instance.isGlobalFeedPreloaded()
            if (!preloaded) {
                _feed.update { it.copy(isLoading = true) }
            }
            withContext(Dispatchers.IO) {
                historyByUrl = historyDao.observeAll().first().associateBy { it.contentUrl }
                // Cache-first on the initial load (not pull-to-refresh): paint
                // the last feed immediately (stale included), refresh in
                // background, replace when fresh data lands.
                if (!updated && !preloaded) {
                    cachedItems = loadCachedFeed()
                    emitCachedFeed()
                }
                try {
                    flow = newFlow(updated = updated)
                    flow!!.loadInitial()
                    Logger.i(TAG, "Loaded ${flow!!.items.size} items")
                    saveFeedCache(flow!!.items)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error loading feed", e)
                    pendingLoadMore = false // its page never materialized
                    // A failed refresh must not wipe a cache that's on screen.
                    if (cachedItems != null) {
                        _feed.update {
                            it.copy(
                                items = filteredCards(cachedItems!!),
                                isLoading = false,
                                error = e.message,
                            )
                        }
                    } else {
                        _feed.update { it.copy(isLoading = false, error = e.message) }
                    }
                    return@withContext
                }
                cachedItems = null
                applyFilters()
                runPendingLoadMore()
            }
        }

        /** Emits the cached feed (filtered) immediately; loading stays up. */
        private fun emitCachedFeed() {
            val cached = cachedItems ?: return
            Logger.i(TAG, "Showing ${cached.size} cached feed items")
            // hasMorePages=true while painting: the cached page IS page 1 of
            // a feed that had more — without it the overdrag gesture stays
            // inert for the whole initial fetch.
            _feed.update { it.copy(items = filteredCards(cached), isLoading = true, hasMorePages = true) }
        }

        /** @return the cached feed, stale included (stale-while-revalidate). */
        private suspend fun loadCachedFeed(): List<IPlatformContent>? = readFreshSubsFeedCache(allowStale = true)

        private suspend fun saveFeedCache(items: List<IPlatformContent>) {
            if (items.isEmpty()) return
            try {
                val payload =
                    SubsFeedCache(
                        System.currentTimeMillis(),
                        items.map { SerializedPlatformContent.fromContent(it) },
                    )
                feedCache.setAndSave(Serializer.json.encodeToString(payload))
                Logger.i(TAG, "Cached ${payload.items.size} feed items")
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to cache feed", e)
            }
        }

        private suspend fun newFlow(updated: Boolean): PagerFlow<IPlatformContent, IPlatformContent> {
            val creatorId = _feed.value.activeCreatorId
            val pager =
                if (creatorId == null) {
                    // Global feed = the engine's merged pager over ALL
                    // subscribed channels (a per-channel batch would leave
                    // most channels' videos out of the list entirely).
                    StateSubscriptions.instance.getGlobalSubscriptionFeed(
                        CoroutineScope(Dispatchers.IO),
                        updated = updated,
                    )
                } else {
                    val client =
                        EnabledClientResolver.clientForUrl(creatorId)
                            ?: throw IllegalStateException("No client found for channel $creatorId")
                    StatePlatform.instance.getChannelContent(client, creatorId)
                }
            return PagerFlow(pager, { it }, { it.id })
        }

        /**
         * A subscription was added or removed somewhere. Rescan the
         * sidebar, drop a selection that no longer exists, and (global
         * scope only) pick up the changed channel content.
         */
        private suspend fun resyncSubscriptions() {
            withContext(Dispatchers.IO) {
                // Demo mode: keep the simulated creator list, never scan
                // real subscriptions.
                if (com.tsutsen.platformplayer.demo.DemoContent.demoModeEnabled()) {
                    _feed.update { it.copy(creators = demoCreators()) }
                    return@withContext
                }
                val creators = scanCreators()
                _feed.update { it.copy(creators = creators) }
                val active = _feed.value.activeCreatorId
                when {
                    // A still-subscribed channel: its own pager is
                    // unaffected by subscription changes.
                    active != null && _feed.value.creators.any { it.id == active } -> {
                        Unit
                    }

                    // The selected channel was unsubscribed: fall back to
                    // the global feed.
                    active != null -> {
                        _feed.update {
                            it.copy(activeCreatorId = null, items = emptyList(), isLoading = true)
                        }
                        reloadGlobal()
                    }

                    // Global scope: refresh so new channels appear.
                    else -> {
                        reloadGlobal()
                    }
                }
            }
        }

        private suspend fun reloadGlobal() {
            withContext(Dispatchers.IO) {
                try {
                    flow = newFlow(updated = false)
                    flow!!.loadInitial()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error resyncing feed", e)
                    _feed.update { it.copy(isLoading = false, error = e.message) }
                    return@withContext
                }
                applyFilters()
            }
        }

        // Stable per app session: the "random" order reshuffles only when the
        // subscription count changes, not on every fetch.
        private var randomSubOrder: List<Subscription>? = null

        /** Orders the subs list for the Subs tab per Settings > subscriptionsOrder. */
        private suspend fun orderedSubscriptions(subs: List<Subscription>): List<Subscription> =
            when (SettingsRepositoryImpl.instance.preferences.value.subscriptionsOrder) {
                "random" -> {
                    val cached = randomSubOrder
                    if (cached != null && cached.size == subs.size) {
                        cached
                    } else {
                        subs.shuffled().also { randomSubOrder = it }
                    }
                }

                "alphabetical" -> subs.sortedBy { it.channel.name.lowercase() }

                // "watchtime" (default): total watched time from history per
                // channel, alphabetical fallback for channels without history.
                else -> {
                    val times =
                        historyDao.watchedTimeByAuthor().associate { it.authorUrl to it.watchedMs }
                    subs.sortedWith(
                        compareByDescending<Subscription> { times[it.channel.url] ?: 0L }
                            .thenBy { it.channel.name.lowercase() },
                    )
                }
            }

        private suspend fun scanCreators(): List<SubscriptionCreator> {
            val subs = orderedSubscriptions(StateSubscriptions.instance.getSubscriptions())
            return subs.mapNotNull { sub ->
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
        }

        private fun applyFilters() {
            // Demo mode: the filter chips don't apply to the simulated feed
            // (and flow/cachedItems are null here — a pass through would
            // wipe the demo items).
            if (com.tsutsen.platformplayer.demo.DemoContent.demoModeEnabled()) return
            val f = flow
            // While a fresh load is in flight the new pager's first page hasn't
            // landed yet: keep the cached items on screen so the chips keep
            // filtering them.
            val contents = (f?.items ?: emptyList()).ifEmpty { cachedItems ?: emptyList() }
            _feed.update {
                it.copy(
                    items = filteredCards(contents),
                    hasMorePages = f?.hasMore ?: false,
                    isLoading = false,
                    error = f?.error,
                )
            }
        }

        /** Applies the scope/type/watch filters and maps raw content to Cards. */
        private fun filteredCards(contents: List<IPlatformContent>): List<Card> {
            val state = _feed.value
            var filtered: List<IPlatformContent> = contents

            // Scope filter (no-op for the per-channel pager, which is
            // already scoped — kept for a single code path).
            val activeCreatorId = state.activeCreatorId
            if (activeCreatorId != null) {
                filtered =
                    filtered.filter { content ->
                        (content as? IPlatformVideo)?.author?.url == activeCreatorId
                    }
            }

            // Type filter: "Videos"/"Live" are OR within the category.
            // Watch filter: fresh (never played) videos always show;
            // the chips gate the started and watched categories
            // (both on by default = no filtering).
            val showVideos = state.filterVideo
            val showLive = state.filterStreams
            val showShorts = state.filterShorts
            val showStarted = state.filterStarted
            val showWatched = state.filterWatched
            filtered =
                filtered.filter { content ->
                    val video = content as? IPlatformVideo ?: return@filter true
                    // Type filter: Videos/Live/Shorts are OR categories — a video
                    // shows if its own category's chip is on. isShort comes from
                    // the plugin; fall back to the /shorts/ URL segment for
                    // plugins that don't set the flag (and for cached rows
                    // serialized before the flag existed).
                    val isShort = video.isShort || content.url.contains("/shorts/")
                    val categoryOn =
                        when {
                            video.isLive -> showLive
                            isShort -> showShorts
                            else -> showVideos
                        }
                    if (!categoryOn) return@filter false
                    // Plugin items report playbackTime = -1; watch progress
                    // lives in history (refreshed per reload). Single
                    // watched/started rule (core:model WatchState).
                    val watchedMs = historyByUrl[content.url]?.watchedMs ?: 0
                    val t = historyByUrl[content.url]?.lastPositionMs
                        ?: video.playbackTime.coerceAtLeast(0)
                    val ws = com.tsutsen.platformplayer.core.model.WatchState.of(
                        t, watchedMs, video.duration,
                    )
                    when {
                        ws?.isWatched == true -> if (!showWatched) return@filter false
                        ws != null -> if (!showStarted) return@filter false
                        else -> Unit // never played: always shown
                    }
                    true
                }

            // Convert to Cards
            return EngineCardMapper.toCards(filtered)
        }

        /** Demo mode: publish the deterministic demo page for the current scope. */
        private suspend fun loadDemoFeed(reset: Boolean) {
            val creatorId = _feed.value.activeCreatorId
            val page = if (reset) 1 else demoPage + 1
            val existing = if (reset) emptyList() else _feed.value.items
            val fresh =
                com.tsutsen.platformplayer.demo.DemoContent.cards(page).filter { card ->
                    creatorId == null || card.authorUrl == creatorId
                }
            demoPage = page
            _feed.update {
                it.copy(
                    items = existing + fresh,
                    creators = if (it.creators.isEmpty()) demoCreators() else it.creators,
                    isLoading = false,
                    hasMorePages = true,
                    error = null,
                    currentPage = page,
                )
            }
            Logger.i(TAG, "Demo mode: feed page $page (+${fresh.size} cards)")
        }

        /** Demo mode: the four simulated channels (bundled thumbnails). */
        private fun demoCreators(): List<SubscriptionCreator> {
            val thumbnails = com.tsutsen.platformplayer.demo.DemoContent.imageUris()
            return (1..4).map { n ->
                SubscriptionCreator(
                    id = "demo://channel/$n",
                    name = "Demo Channel $n",
                    thumbnailUrl = thumbnails.getOrNull(n - 1),
                    subscriberCount = null,
                    url = "demo://channel/$n",
                    hasNewContent = false,
                )
            }
        }

        private companion object {
            const val TAG = "EngineSubscriptionsRepository"
        }
    }
