package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SearchRepository implementation.
 * Bridges to engine search in Phase 5, returns empty results until then.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor() : SearchRepository {

    private val _results = MutableStateFlow(SearchResult())
    override val results: StateFlow<SearchResult> = _results.asStateFlow()

    override suspend fun search(query: String, sources: Set<String>) {
        _results.update { it.copy(query = query, isLoading = true, error = null) }
        // Bridge to engine search — implemented in Phase 5
        _results.update { it.copy(isLoading = false) }
    }

    override suspend fun clearResults() {
        _results.update { SearchResult() }
    }

    override suspend fun nextPage() {
        _results.update { it.copy(isLoading = true) }
        // Bridge to engine next page — implemented in Phase 5
        _results.update { it.copy(isLoading = false) }
    }
}
