package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * "Homebrew recommendations" feed builder (Settings > Content > Recommendations).
 *
 * When the master toggle is on, the home feed is built from the enabled
 * sources instead of the plain platform home refresh:
 *  1. [sourceYoutubeFeed] — the current [StatePlatform.getHomeRefresh] pager.
 *  2. [sourceSubscriptions] — the first (recent) page of every subscribed
 *     channel via [StatePlatform.getChannelContent], unwatched only, shuffled.
 *  3. [sourceTopCreators] — creators with > 15 min watched in the last 28
 *     days (HistoryDao), their recent unwatched videos.
 *
 * The sources are interleaved round-robin and deduped by card id. Each source
 * is isolated: a failing source contributes nothing, and an empty result
 * means the caller falls back to the plain home refresh.
 *
 * [buildFeed] reports progress via [onPartial] (after each source completes)
 * and hands the per-source pagers to [onFlows] so the caller can keep
 * infinite scroll alive in homebrew mode.
 */
object HomebrewRecommendations {
    private const val TAG = "HomebrewRecommendations"

    /** Items each source contributes to the merged feed. */
    private const val SOURCE_ITEM_LIMIT = 10

    /** Random subscription channels sampled for source 2 (each is a plugin call). */
    private const val SUB_SAMPLE_SIZE = 6

    /** Concurrent channel fetches (plugin calls are the bottleneck). */
    private const val CHANNEL_CONCURRENCY = 4

    /** How many top creators (by recent watch time) source 3 considers. */
    private const val TOP_CREATOR_LIMIT = 5

    /** Source 3 window: the last 28 days. */
    private const val WATCH_WINDOW_MS = 28L * 24 * 60 * 60 * 1000

    /** Source 3 threshold: more than 15 minutes watched inside the window. */
    private const val MIN_WATCHED_MS = 15L * 60 * 1000

    /**
     * Builds the merged recommendation feed from the enabled sources.
     * [onPartial] fires after each source completes (progressive paint);
     * [onFlows] delivers the per-source pagers backing the feed.
     * Returns empty when every enabled source failed or came back empty.
     */
    suspend fun buildFeed(
        historyDao: HistoryDao,
        settings: Settings.RecommendationsSettings,
        onPartial: (List<Card>) -> Unit = {},
        onFlows: (List<PagerFlow<IPlatformContent, Card>>) -> Unit = {},
    ): List<Card> =
        withContext(Dispatchers.IO) {
            // Sources run concurrently — each is a network/plugin fetch and
            // the total is max(source), not the sum.
            coroutineScope {
                val futures = mutableListOf<Deferred<SourceResult>>()
                if (settings.sourceYoutubeFeed) {
                    futures += async { youtubeFeed() }
                }
                if (settings.sourceSubscriptions) {
                    futures += async { subscriptionVideos(historyDao, onPartial) }
                }
                if (settings.sourceTopCreators) {
                    futures += async { topCreatorVideos(historyDao) }
                }
                val allFlows = mutableListOf<PagerFlow<IPlatformContent, Card>>()
                val sources = mutableListOf<List<Card>>()
                // Await in submission order: onPartial fires as soon as each
                // source lands, not only when all of them are done.
                futures.forEachIndexed { i, future ->
                    val result = future.await()
                    if (result.cards.isEmpty()) Logger.i(TAG, "Source ${i + 1} empty/failed")
                    onPartial(result.cards)
                    sources += result.cards
                    allFlows += result.flows
                }
                onFlows(allFlows)
                interleave(sources)
            }
        }

    /** One source's contribution plus the pagers backing it (kept for loadNextPage). */
    private data class SourceResult(
        val cards: List<Card>,
        val flows: List<PagerFlow<IPlatformContent, Card>>,
    )

    private val FAILED_SOURCE = SourceResult(emptyList(), emptyList())

    /** Source 1: the logged-in platform home feed, exactly like the plain home refresh. */
    private suspend fun youtubeFeed(): SourceResult =
        runCatching {
            val pager = StatePlatform.instance.getHomeRefresh(CoroutineScope(Dispatchers.IO))
            val flow = PagerFlow(pager, EngineCardMapper::toCard, { it.id })
            flow.loadInitial()
            // Refresh pagers absorb late sub-sources after the first page.
            flow.mergeCurrentResults()
            SourceResult(flow.items, listOf(flow))
        }.onFailure { Logger.w(TAG, "YouTube feed source failed", it) }.getOrDefault(FAILED_SOURCE)

    /**
     * Source 2: recent videos from sampled subscribed channels, unwatched
     * only, shuffled. A fresh (< 1h) subs feed file cache seeds the
     * contribution immediately (emitted via [onPartial]); the live fetch
     * over the sampled channels then merges in.
     */
    private suspend fun subscriptionVideos(
        historyDao: HistoryDao,
        onPartial: (List<Card>) -> Unit,
    ): SourceResult =
        runCatching {
            val cached =
                readFreshSubsFeedCache()
                    ?.let {
                        unwatched(it, historyDao)
                            .shuffled()
                            .take(SOURCE_ITEM_LIMIT)
                            .let(EngineCardMapper::toCards)
                    }
                    ?: emptyList()
            if (cached.isNotEmpty()) onPartial(cached)
            val subs = StateSubscriptions.instance.getSubscriptions().shuffled().take(SUB_SAMPLE_SIZE)
            val permits = Semaphore(CHANNEL_CONCURRENCY)
            val perChannel =
                coroutineScope {
                    subs.map { sub ->
                        async {
                            permits.withPermit {
                                try {
                                    // A fresh pager has no results until a page is loaded.
                                    val pager = StatePlatform.instance.getChannelContent(sub.channel.url)
                                    val flow = PagerFlow(pager, EngineCardMapper::toCard, { it.id })
                                    flow.loadInitial()
                                    // Raw first-page contents for the unwatched filter.
                                    flow to pager.getResults()
                                } catch (e: Exception) {
                                    Logger.w(TAG, "Channel contents failed for ${sub.channel.url}", e)
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            val flows = perChannel.map { it.first }
            val contents = perChannel.flatMap { it.second }
            val live =
                unwatched(contents, historyDao)
                    .shuffled()
                    .take(SOURCE_ITEM_LIMIT)
                    .let(EngineCardMapper::toCards)
            SourceResult((cached + live).distinctBy { it.id }, flows)
        }.onFailure { Logger.w(TAG, "Subscriptions source failed", it) }.getOrDefault(FAILED_SOURCE)

    /** Source 3: recent unwatched videos from the most-watched creators of the last 4 weeks. */
    private suspend fun topCreatorVideos(historyDao: HistoryDao): SourceResult =
        runCatching {
            val sinceMs = System.currentTimeMillis() - WATCH_WINDOW_MS
            val creators = historyDao.topCreatorsByWatchTimeSince(sinceMs, MIN_WATCHED_MS, TOP_CREATOR_LIMIT)
            val permits = Semaphore(CHANNEL_CONCURRENCY)
            val perCreator =
                coroutineScope {
                    creators.map { creator ->
                        async {
                            permits.withPermit {
                                try {
                                    val pager = StatePlatform.instance.getChannelContent(creator.authorUrl)
                                    val flow = PagerFlow(pager, EngineCardMapper::toCard, { it.id })
                                    flow.loadInitial()
                                    flow to pager.getResults()
                                } catch (e: Exception) {
                                    Logger.w(TAG, "Channel contents failed for ${creator.authorUrl}", e)
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            val flows = perCreator.map { it.first }
            val contents = perCreator.flatMap { it.second }
            SourceResult(
                unwatched(contents, historyDao)
                    .take(SOURCE_ITEM_LIMIT)
                    .let(EngineCardMapper::toCards),
                flows,
            )
        }.onFailure { Logger.w(TAG, "Top creators source failed", it) }.getOrDefault(FAILED_SOURCE)

    /**
     * Drops contents that have a history row with playback progress
     * (lastPositionMs > 0) — already started/watched. One batch query.
     */
    private suspend fun unwatched(
        contents: List<IPlatformContent>,
        historyDao: HistoryDao,
    ): List<IPlatformContent> {
        val urls = contents.map { it.url }.distinct()
        if (urls.isEmpty()) return emptyList()
        val history = historyDao.getByUrls(urls).associateBy { it.contentUrl }
        return contents.filter { content ->
            val h = history[content.url] ?: return@filter true
            h.lastPositionMs <= 0
        }
    }

    /** Round-robin interleave of the sources, deduped by card id (first source wins). */
    internal fun interleave(sources: List<List<Card>>): List<Card> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Card>()
        val iterators = sources.map { it.iterator() }
        var progressed = true
        while (progressed) {
            progressed = false
            for (it in iterators) {
                while (it.hasNext()) {
                    val card = it.next()
                    if (seen.add(card.id)) {
                        out += card
                        progressed = true
                        break
                    }
                }
            }
        }
        return out
    }
}
