package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.SearchResult
import com.tsutsen.platformplayer.core.model.SearchSort
import com.tsutsen.platformplayer.core.model.SearchType
import com.tsutsen.platformplayer.core.model.SourceInfo
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlugins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SearchRepository implementation that bridges to the engine (StatePlatform).
 * Uses StatePlatform.search() to query all enabled platforms in parallel.
 */
@Singleton
class EngineSearchRepositoryImpl
    @Inject
    constructor() : SearchRepository {
        private val _results = MutableStateFlow(SearchResult())
        override val results: StateFlow<SearchResult> = _results.asStateFlow()

        private val _enabledSources = MutableStateFlow(emptyList<SourceInfo>())
        override val enabledSources: StateFlow<List<SourceInfo>> = _enabledSources.asStateFlow()

        private var _pagerFlow: PagerFlow<IPlatformContent, Card>? = null
        private var _lastQuery: String = ""
        /** Non-empty when the user restricted the search to some sources. */
        private var restrictedSources: Set<String>? = null

        init {
            publishEnabledSources()
        }

        private fun publishEnabledSources() {
            _enabledSources.value =
                StatePlatform.instance
                    .getEnabledClients()
                    .map { SourceInfo(it.id, it.name, StatePlugins.instance.getPluginIconUriOrNull(it.id)) }
                    .sortedBy { it.name.lowercase() }
        }

        private fun visibleItems(flow: PagerFlow<IPlatformContent, Card>): List<Card> {
            val restricted = restrictedSources ?: return flow.items
            return flow.items.filter { it.sourceId == null || it.sourceId in restricted }
        }

        override suspend fun search(
            query: String,
            type: SearchType,
            sort: SearchSort,
            sources: Set<String>,
        ) {
            Logger.i("EngineSearchRepository", "search: $query ($type), sources: $sources")
            _lastQuery = query
            restrictedSources = sources.takeIf { it.isNotEmpty() }
            publishEnabledSources()
            _results.update { it.copy(query = query, isLoading = true, error = null, items = emptyList()) }

            try {
                // Run engine call on IO dispatcher to avoid main thread blocking
                withContext(Dispatchers.IO) {
                    val clientIds = restrictedSources?.toList()
                    val pager =
                        when (type) {
                            SearchType.MEDIA -> {
                                StatePlatform.instance.search(query, sort = sort.jsOrder, clientIds = clientIds)
                            }

                            SearchType.CREATORS -> {
                                // No clientIds support: filter the mapped cards
                                // by source below.
                                StatePlatform.instance.searchChannelsAsContent(query)
                            }

                            SearchType.PLAYLISTS -> {
                                StatePlatform.instance.searchPlaylist(query, clientIds = clientIds)
                            }
                        }
                    val flow = PagerFlow(pager, EngineCardMapper::toCard, { it.id })
                    _pagerFlow = flow
                    val items = flow.loadInitial()
                    Logger.i("EngineSearchRepository", "search: ${items.size} items, hasMore=${flow.hasMore}")
                    _results.update {
                        it.copy(
                            isLoading = false,
                            items = visibleItems(flow),
                            hasMorePages = flow.hasMore,
                            error = flow.error,
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e("EngineSearchRepository", "search failed", e)
                _results.update {
                    it.copy(
                        isLoading = false,
                        items = emptyList(),
                        hasMorePages = false,
                        error = e.message ?: "Search failed",
                    )
                }
            }
        }

        override suspend fun clearResults() {
            Logger.i("EngineSearchRepository", "clearResults")
            _results.update { SearchResult() }
            _pagerFlow = null
            _lastQuery = ""
        }

        override suspend fun nextPage() {
            Logger.i("EngineSearchRepository", "nextPage")
            _results.update { it.copy(isLoading = true) }

            try {
                val flow = _pagerFlow ?: return
                if (!flow.hasMore) {
                    _results.update { it.copy(isLoading = false) }
                    return
                }

                val newItems = withContext(Dispatchers.IO) { flow.loadNextPage() }
                Logger.i("EngineSearchRepository", "Got ${newItems.size} new items, ${flow.items.size} total")
                _results.update {
                    it.copy(
                        isLoading = false,
                        items = visibleItems(flow),
                        hasMorePages = flow.hasMore,
                        error = flow.error,
                    )
                }
            } catch (e: Exception) {
                Logger.e("EngineSearchRepository", "nextPage failed", e)
                _results.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load more")
                }
            }
        }
    }
