package com.futo.platformplayer.core.data.repository

import com.futo.platformplayer.core.model.ContentItem
import com.futo.platformplayer.core.model.FeedPage
import kotlinx.coroutines.flow.StateFlow

interface HomeRepository {

    val feed: StateFlow<FeedPage>

    suspend fun loadInitial()
    suspend fun loadNextPage()
    suspend fun refresh()
    suspend fun filterByTag(tag: String)
    suspend fun filterByAuthor(authorId: String)
}
