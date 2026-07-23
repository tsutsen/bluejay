package com.futo.platformplayer.core.data.repository

import com.futo.platformplayer.core.model.SubscriptionFeed
import kotlinx.coroutines.flow.StateFlow

interface SubscriptionRepository {

    val feed: StateFlow<SubscriptionFeed>
    val creators: StateFlow<List<com.futo.platformplayer.core.model.Creator>>

    suspend fun loadFeed()
    suspend fun loadCreators()
    suspend fun filterByCreator(creatorId: String)
    suspend fun filterByType(type: String)
    suspend fun markAsWatched(videoId: String)
}
