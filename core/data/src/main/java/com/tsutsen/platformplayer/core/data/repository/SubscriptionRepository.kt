package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import kotlinx.coroutines.flow.StateFlow

interface SubscriptionRepository {

    val feed: StateFlow<SubscriptionFeed>
    val creators: StateFlow<List<com.tsutsen.platformplayer.core.model.Creator>>

    suspend fun loadFeed()
    suspend fun loadCreators()
    suspend fun filterByCreator(creatorId: String)
    suspend fun filterByType(type: String)
    suspend fun markAsWatched(videoId: String)
}
