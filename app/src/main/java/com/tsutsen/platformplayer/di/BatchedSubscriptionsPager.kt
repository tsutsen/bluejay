package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.ResultCapabilities
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.api.media.structures.SingleItemPager
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.models.Subscription
import com.tsutsen.platformplayer.states.EnabledClientResolver
import com.tsutsen.platformplayer.states.StateCache
import com.tsutsen.platformplayer.states.StatePlatform
import java.time.OffsetDateTime

/**
 * The global Subs tab feed, fetched in batches.
 *
 * The engine's subscription fetch (SmartSubscriptionAlgorithm) fires one
 * request per subscribed channel up front — with hundreds of subscriptions
 * the first paint waits on every channel (live, peek or cache) at once.
 * This pager instead loads [batchSize] channels at a time, chrono-merged
 * [pageSize] items per page; each scroll to the list end (PagerFeed
 * load-more) consumes further windows, and only when every loaded channel
 * is exhausted is the next batch of channels fetched.
 *
 * Channels are processed in the order given (the Subs tab passes its
 * watch-time ordering, so the first batch is the channels actually
 * watched). Per-channel bookkeeping and the cache fallback mirror the
 * fetch algorithm's task handling so subscription dates stay healthy.
 */
class BatchedSubscriptionsPager(
    subs: List<Subscription>,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) : IPager<IPlatformContent> {
    private class Channel(val sub: Subscription) {
        var single: SingleItemPager<IPlatformContent>? = null
        var failed: Boolean = false
    }

    private val channels = subs.map { Channel(it) }
    private var nextChannel = 0
    private var initialized = false
    private var currentResults: List<IPlatformContent> = emptyList()

    /** Everything ever served — returned whole when a surviving pager is re-attached. */
    private val served = mutableListOf<IPlatformContent>()

    /** Set by [reopen]: the next [getResults] returns [served] instead of the last window. */
    private var emitAll = false

    override fun hasMorePages(): Boolean =
        synchronized(this) {
            channels.any { it.available } || nextChannel < channels.size
        }

    override fun getResults(): List<IPlatformContent> =
        synchronized(this) {
            if (!initialized) {
                currentResults = loadNextWindow()
                initialized = true
            }
            if (emitAll) {
                emitAll = false
                served.toList()
            } else {
                currentResults
            }
        }

    override fun nextPage() {
        synchronized(this) {
            currentResults = loadNextWindow()
        }
    }

    /**
     * Re-attach this pager to a fresh feed after tab re-entry: the next
     * initial load replays everything accumulated so far (no refetch, no
     * shrink to one window).
     */
    fun reopen() {
        synchronized(this) { emitAll = true }
    }

    private val Channel.available: Boolean
        get() =
            !failed &&
                (single?.hasMoreItems() == true || single?.getPager()?.hasMorePages() == true)

    /** Chrono-merges the next [pageSize] items, fetching channel batches as they run dry. */
    private fun loadNextWindow(): List<IPlatformContent> {
        val results = mutableListOf<IPlatformContent>()
        while (results.size < pageSize) {
            val item = nextChronoItem() ?: break
            results.add(item)
        }
        if (results.isNotEmpty()) served.addAll(results)
        return results
    }

    /**
     * The newest unconsumed item across the loaded channels; advances
     * channel pagers as their windows run out (same semantics as
     * MultiPager/SingleItemPager) and loads the next channel batch when all
     * of them are exhausted.
     */
    private fun nextChronoItem(): IPlatformContent? {
        while (true) {
            var best: Channel? = null
            var bestDate: OffsetDateTime? = null
            for (channel in channels) {
                if (!channel.available) continue
                val single = channel.single!!
                val item =
                    try {
                        single.getCurrentItem()
                    } catch (e: Throwable) {
                        channel.failed = true
                        null
                    }
                if (item == null) {
                    channel.failed = true
                    continue
                }
                val date = item.datetime
                if (best == null || (date != null && (bestDate == null || date > bestDate))) {
                    best = channel
                    bestDate = date
                }
            }
            if (best != null) return best.single!!.consumeItem()
            if (nextChannel < channels.size) {
                loadBatch()
                continue
            }
            return null
        }
    }

    /** Live-fetches the next [batchSize] channels; failures fall back to the channel cache. */
    private fun loadBatch() {
        val end = minOf(nextChannel + batchSize, channels.size)
        Logger.i(TAG, "Fetching channels $nextChannel..${end - 1} of ${channels.size}")
        while (nextChannel < end) {
            val channel = channels[nextChannel++]
            val pager = fetchChannelPager(channel.sub)
            channel.single = pager?.let { SingleItemPager(it) }
            if (pager == null) channel.failed = true
        }
    }

    private fun fetchChannelPager(sub: Subscription): IPager<IPlatformContent>? {
        val client =
            EnabledClientResolver.channelClientForUrl(sub.channel.url) as? JSClient
                ?: return null
        val capabilities = client.getChannelCapabilities()
        val type =
            when {
                capabilities.hasType(ResultCapabilities.TYPE_MIXED) || capabilities.types.isEmpty() ->
                    ResultCapabilities.TYPE_MIXED
                capabilities.hasType(ResultCapabilities.TYPE_SUBSCRIPTIONS) ->
                    ResultCapabilities.TYPE_SUBSCRIPTIONS
                else -> ResultCapabilities.TYPE_VIDEOS
            }
        return try {
            val pager =
                StatePlatform.instance.getChannelContent(
                    client,
                    sub.channel.url,
                    type,
                    ResultCapabilities.ORDER_CHONOLOGICAL,
                )
            // Mirror the fetch algorithm's bookkeeping (urgency/dates for
            // future update scheduling).
            sub.updateSubscriptionState(type, pager.getResults())
            sub.save()
            pager
        } catch (e: Throwable) {
            Logger.w(TAG, "Channel ${sub.channel.name} failed, substituting with cache", e)
            try {
                StateCache.instance.getChannelCachePager(sub.channel.url)
            } catch (e2: Throwable) {
                Logger.w(TAG, "Channel ${sub.channel.name} cache fallback failed too", e2)
                null
            }
        }
    }

    companion object {
        private const val TAG = "BatchedSubsPager"

        /** Window size — matches the engine subs pager's page size. */
        const val DEFAULT_PAGE_SIZE = 15

        /** Channels fetched per batch. */
        const val DEFAULT_BATCH_SIZE = 10
    }
}
