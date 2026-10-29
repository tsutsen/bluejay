package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.media.Serializer
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformContent
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.models.Subscription
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
private data class SubsFeedCache(
    val cachedAt: Long = 0L,
    val items: List<SerializedPlatformContent> = emptyList(),
)

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
        // frame instead of a momentary empty state.
        private val _feed = MutableStateFlow(SubscriptionFeed(isLoading = true))
        override val feed: StateFlow<SubscriptionFeed> = _feed.asStateFlow()

        // The subscription-change listener lives outside any ViewModel.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private var flow: PagerFlow<IPlatformContent, IPlatformContent>? = null

        // File-backed cache of the last loaded global feed; painted on launch
        // while a fresh fetch runs (same UX as the home feed's Room cache).
        private val feedCache by lazy { FragmentedStorage.get<StringStorage>("subscriptions_feed_cache") }

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

        override suspend fun loadMore() {
            withContext(Dispatchers.IO) {
                val f = flow
                if (f != null && f.hasMore) {
                    f.loadNextPage()
                    Logger.i(TAG, "Loaded more (total: ${f.items.size})")
                    applyFilters()
                }
            }
        }

        override suspend fun selectCreator(creatorId: String?) {
            if (creatorId == _feed.value.activeCreatorId) return
            _feed.update {
                it.copy(activeCreatorId = creatorId, items = emptyList(), isLoading = true)
            }
            withContext(Dispatchers.IO) {
                try {
                    // A fresh pager per scope: the previous scope's pages
                    // don't apply to this one.
                    flow = newFlow(updated = false)
                    flow!!.loadInitial()
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
        private suspend fun reload(updated: Boolean) {
            _feed.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                // Cache-first on the initial load (not pull-to-refresh): paint
                // the last feed immediately, refresh in background, replace
                // when fresh data lands.
                if (!updated) {
                    cachedItems = loadCachedFeed()
                    emitCachedFeed()
                }
                try {
                    flow = newFlow(updated = updated)
                    flow!!.loadInitial()
                    Logger.i(TAG, "Loaded ${flow!!.items.size} items")
                    saveFeedCache(flow!!.items)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error loading feed", e)
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
            }
        }

        /** Emits the cached feed (filtered) immediately; loading stays up. */
        private fun emitCachedFeed() {
            val cached = cachedItems ?: return
            Logger.i(TAG, "Showing ${cached.size} cached feed items")
            _feed.update { it.copy(items = filteredCards(cached), isLoading = true) }
        }

        /** @return the cached feed when fresh enough (< [FEED_CACHE_TTL_MS]), else null. */
        private suspend fun loadCachedFeed(): List<IPlatformContent>? {
            return try {
                val payload = Serializer.json.decodeFromString<SubsFeedCache>(feedCache.value)
                if (System.currentTimeMillis() - payload.cachedAt > FEED_CACHE_TTL_MS || payload.items.isEmpty()) {
                    null
                } else {
                    payload.items.toList()
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to load cached feed", e)
                null
            }
        }

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
                    StateSubscriptions.instance.getGlobalSubscriptionFeed(
                        CoroutineScope(Dispatchers.IO),
                        updated = updated,
                    )
                } else {
                    val client =
                        StatePlatform.instance.getClientOrNullByUrl(creatorId)
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
            when (Settings.instance.subscriptions.subscriptionsOrder) {
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
            val showStarted = state.filterStarted
            val showWatched = state.filterWatched
            filtered =
                filtered.filter { content ->
                    // Shorts filter: on = only short videos (hides non-videos too).
                    if (state.filterShorts && (content as? IPlatformVideo)?.isShort != true) {
                        return@filter false
                    }
                    val video = content as? IPlatformVideo ?: return@filter true
                    if (video.isLive) {
                        if (!showLive) return@filter false
                    } else if (!showVideos) {
                        return@filter false
                    }
                    val d = video.duration
                    val t = video.playbackTime
                    when {
                        d > 0 && t >= 0.95 * d -> {
                            if (!showWatched) return@filter false
                        }

                        t > 0 -> {
                            if (!showStarted) return@filter false
                        }

                        else -> {
                            Unit
                        } // never played: always shown
                    }
                    true
                }

            // Convert to Cards
            return EngineCardMapper.toCards(filtered)
        }

        private companion object {
            const val TAG = "EngineSubscriptionsRepository"
            // Cached feed is a launch shortcut only; after this, wait for fresh data.
            const val FEED_CACHE_TTL_MS = 60L * 60L * 1000L
        }
    }
