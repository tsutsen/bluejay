package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.FeedPage
import com.tsutsen.platformplayer.core.model.SourceInfo
import kotlinx.coroutines.flow.StateFlow

interface HomeRepository {

    val feed: StateFlow<FeedPage>

    /** Enabled sources (id, display name, icon) for feed source filtering. */
    val enabledSources: StateFlow<List<SourceInfo>>

    suspend fun loadInitial()
    suspend fun loadNextPage()
    suspend fun refresh()
    suspend fun filterByTag(tag: String)
    suspend fun filterByAuthor(authorId: String)
}
