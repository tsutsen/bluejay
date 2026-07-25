package com.tsutsen.platformplayer.subscription

import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.structures.DedupContentPager
import com.tsutsen.platformplayer.models.Subscription
import com.tsutsen.platformplayer.states.StateCache
import com.tsutsen.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ForkJoinPool

class CachedSubscriptionAlgorithm(scope: CoroutineScope, allowFailure: Boolean = false, withCacheFallback: Boolean = true, threadPool: ForkJoinPool? = null, pageSize: Int = 50)
        : SubscriptionFetchAlgorithm(scope, allowFailure, withCacheFallback, threadPool) {

    private val _pageSize: Int = pageSize;

    override fun countRequests(subs: Map<Subscription, List<String>>): Map<JSClient, Int> {
        return mapOf();
    }

    override fun getSubscriptions(subs: Map<Subscription, List<String>>): Result {
        return Result(DedupContentPager(StateCache.instance.getChannelCachePager(subs.flatMap { it.value }.distinct(), _pageSize), StatePlatform.instance.getEnabledClients().map { it.id }), listOf());
    }
}