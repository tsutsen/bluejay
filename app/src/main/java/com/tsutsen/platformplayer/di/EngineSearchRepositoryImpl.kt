package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.SearchResult
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
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

        private var _pagerFlow: PagerFlow<IPlatformContent, Card>? = null
        private var _lastQuery: String = ""

        override suspend fun search(
            query: String,
            sources: Set<String>,
        ) {
            Logger.i("EngineSearchRepository", "search: $query, sources: $sources")
            _lastQuery = query
            _results.update { it.copy(query = query, isLoading = true, error = null, items = emptyList()) }

            try {
                // Run engine call on IO dispatcher to avoid main thread blocking
                withContext(Dispatchers.IO) {
                    val pager = StatePlatform.instance.search(query)
                    val flow = PagerFlow(pager, EngineCardMapper::toCard)
                    _pagerFlow = flow
                    val items = flow.loadInitial()
                    _results.update {
                        it.copy(
                            isLoading = false,
                            items = items,
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

                val newItems = flow.loadNextPage()
                Logger.i("EngineSearchRepository", "Got ${newItems.size} new items, ${flow.items.size} total")
                _results.update {
                    it.copy(
                        isLoading = false,
                        items = flow.items,
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
