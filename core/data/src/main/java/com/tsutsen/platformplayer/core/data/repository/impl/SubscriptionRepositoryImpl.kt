package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stub implementation of SubscriptionRepository.
 * The actual implementation is EngineSubscriptionsRepositoryImpl in the app module.
 */
class SubscriptionRepositoryImpl : SubscriptionRepository {

    private val _feed = MutableStateFlow(SubscriptionFeed())
    override val feed: StateFlow<SubscriptionFeed> = _feed.asStateFlow()

    override suspend fun loadCreators() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun loadFeed() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun refresh() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun loadMore() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun selectCreator(creatorId: String?) {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun toggleWatched() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun toggleContinue() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun toggleVideo() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun toggleStreams() {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }

    override suspend fun toggleSourceFilter(sourceId: String) {
        // Stub - implementation in EngineSubscriptionsRepositoryImpl
    }
}
