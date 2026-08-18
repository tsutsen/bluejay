package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.model.SubscriptionCreator
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
    constructor() : SubscriptionRepository {
        // Starts "loading" so the screen shows a spinner from the first
        // frame instead of a momentary empty state.
        private val _feed = MutableStateFlow(SubscriptionFeed(isLoading = true))
        override val feed: StateFlow<SubscriptionFeed> = _feed.asStateFlow()

        // The subscription-change listener lives outside any ViewModel.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private var flow: PagerFlow<IPlatformContent, IPlatformContent>? = null

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
                _feed.update { it.copy(creators = scanCreators()) }
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

        /** Rebuilds the pager for the current scope and applies filters. */
        private suspend fun reload(updated: Boolean) {
            _feed.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                try {
                    flow = newFlow(updated = updated)
                    flow!!.loadInitial()
                    Logger.i(TAG, "Loaded ${flow!!.items.size} items")
                } catch (e: Exception) {
                    Logger.e(TAG, "Error loading feed", e)
                    _feed.update { it.copy(isLoading = false, error = e.message) }
                    return@withContext
                }
                applyFilters()
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
                _feed.update { it.copy(creators = scanCreators()) }
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

        private fun scanCreators(): List<SubscriptionCreator> {
            val subs = StateSubscriptions.instance.getSubscriptions()
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
            val state = _feed.value
            val f = flow
            var filtered: List<IPlatformContent> = f?.items ?: emptyList()

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
            val cards = EngineCardMapper.toCards(filtered)

            _feed.update {
                it.copy(
                    items = cards,
                    hasMorePages = f?.hasMore ?: false,
                    isLoading = false,
                    error = f?.error,
                )
            }
        }

        private companion object {
            const val TAG = "EngineSubscriptionsRepository"
        }
    }
