package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.SearchResult
import com.tsutsen.platformplayer.core.model.SearchSort
import com.tsutsen.platformplayer.core.model.SearchType
import com.tsutsen.platformplayer.core.model.SourceInfo
import kotlinx.coroutines.flow.StateFlow

interface SearchRepository {
    val results: StateFlow<SearchResult>

    /** Enabled sources (id, display name, icon) for the source selector. */
    val enabledSources: StateFlow<List<SourceInfo>>

    suspend fun search(
        query: String,
        type: SearchType = SearchType.MEDIA,
        sort: SearchSort = SearchSort.RELEVANCE,
        sources: Set<String> = emptySet(),
    )

    suspend fun clearResults()

    suspend fun nextPage()
}
