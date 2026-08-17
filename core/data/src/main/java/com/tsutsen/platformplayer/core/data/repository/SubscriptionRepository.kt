package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for subscriptions data.
 * Abstracts away engine-specific types so the feature module stays engine-agnostic.
 */
interface SubscriptionRepository {

    /**
     * Observable subscription feed with filter state.
     */
    val feed: StateFlow<SubscriptionFeed>

    /**
     * Load the list of subscribed creators.
     */
    suspend fun loadCreators()

    /**
     * Load the subscription feed from all subscribed channels.
     */
    suspend fun loadFeed()

    /**
     * Refresh the feed by reloading from the beginning.
     */
    suspend fun refresh()

    /**
     * Load more items (pagination).
     */
    suspend fun loadMore()

    /**
     * Select a creator to filter by. null = show all.
     */
    suspend fun selectCreator(creatorId: String?)

    /**
     * Toggle "Continue" filter: only partially watched videos
     * (0 < playbackTime < 95% of duration).
     */
    suspend fun toggleContinue()

    /**
     * Toggle "Videos" filter: only regular (non-live) videos.
     */
    suspend fun toggleVideo()

    /**
     * Toggle "Live" filter: only live streams.
     */
    suspend fun toggleStreams()
}
