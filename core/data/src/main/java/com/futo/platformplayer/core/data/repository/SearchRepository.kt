package com.futo.platformplayer.core.data.repository

import com.futo.platformplayer.core.model.SearchResult
import kotlinx.coroutines.flow.StateFlow

interface SearchRepository {

    val results: StateFlow<SearchResult>

    suspend fun search(query: String, sources: Set<String> = emptySet())
    suspend fun clearResults()
    suspend fun nextPage()
}
