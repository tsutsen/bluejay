package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.model.ArticleCard
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.PostCard
import com.tsutsen.platformplayer.core.model.VideoCard
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
import kotlin.random.Random

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
 * The sources are merged by weighted spawn (see [interleave]): the stored
 * spawn-probability cut points split 0..100 into three segments, one per
 * source (see [spawnWeights]), and every emitted item comes from an enabled
 * source picked with probability proportional to its segment, renormalized
 * so the enabled weights sum to 1. Cards are deduped by card id.
 * Each source is isolated: a failing source contributes nothing, and an
 * empty result means the caller falls back to the plain home refresh.
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
                // Fixed-order source index (0=youtube, 1=subs, 2=topCreators)
                // per future, so the spawn weights stay aligned with
                // [sources] below.
                val sourceIndices = mutableListOf<Int>()
                if (settings.sourceYoutubeFeed) {
                    futures += async { youtubeFeed() }
                    sourceIndices += 0
                }
                if (settings.sourceSubscriptions) {
                    futures += async { subscriptionVideos(historyDao, onPartial) }
                    sourceIndices += 1
                }
                if (settings.sourceTopCreators) {
                    futures += async { topCreatorVideos(historyDao) }
                    sourceIndices += 2
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
                val weights = spawnWeights(settings)
                interleave(sources, sourceIndices.map { weights[it] })
            }
        }

    /**
     * Spawn weights (0..1, summing to 1 among the enabled sources) for the
     * three sources in fixed order (youtube, subs, topCreators). The cut
     * points split 0..100 into three segments — [0, cut1], [cut1, cut2],
     * [cut2, 100] — so the raw weights sum to 1 by construction; disabled
     * sources are zeroed and the remaining weights renormalized to sum to 1.
     * A segment of 0 means that source never spawns (empty feed -> the caller
     * falls back to the plain home refresh).
     */
    internal fun spawnWeights(settings: Settings.RecommendationsSettings): List<Float> {
        // Cuts split 0..100 across the ENABLED sources only (fixed order:
        // youtube, subs, topCreators), so enabled weights always sum to 1.
        val cuts = listOf(settings.probCut1.coerceIn(0, 100), settings.probCut2.coerceIn(0, 100)).sorted()
        val enabled =
            listOf(
                settings.sourceYoutubeFeed,
                settings.sourceSubscriptions,
                settings.sourceTopCreators,
            )
        val enabledIdx = enabled.indices.filter { enabled[it] }
        val w = MutableList(3) { 0f }
        if (enabledIdx.isEmpty()) return w
        val bounds = listOf(0) + cuts.take(enabledIdx.size - 1) + listOf(100)
        enabledIdx.forEachIndexed { k, idx -> w[idx] = (bounds[k + 1] - bounds[k]) / 100f }
        return w
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

    /**
     * Weighted-spawn interleave of the sources, deduped by card id (first
     * source to yield a card wins). [weights] aligns with [sources]; each
     * entry is the source's spawn weight from [spawnWeights]. Every emitted item
     * comes from a source picked with probability proportional to its weight
     * among the sources that still have items; a zero-weight source
     * contributes nothing.
     *
     * Anti-repeat: the creator key ([creatorKey]) of the last emitted card is
     * tracked. When the roulette pick's next card would repeat that creator,
     * the other candidate sources are tried in weight order for a different
     * creator; if every remaining candidate is the same creator, the pick is
     * accepted as-is (a creator dominating the feed must not deadlock).
     */
    internal fun interleave(sources: List<List<Card>>, weights: List<Float>): List<Card> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Card>()
        // Index cursors (not iterators) so a source's remaining items can be
        // inspected without consuming them.
        val cursors = IntArray(sources.size)
        val w = weights.map { it.coerceIn(0f, 1f) }
        var lastCreator: String? = null
        while (true) {
            // Candidates = sources with weight and at least one non-duplicate
            // item left (duplicates are drained as they are met).
            val candidates = mutableListOf<Int>()
            for (i in sources.indices) {
                if (w[i] <= 0f) continue
                while (cursors[i] < sources[i].size && sources[i][cursors[i]].id in seen) {
                    cursors[i]++
                }
                if (cursors[i] < sources[i].size) candidates += i
            }
            if (candidates.isEmpty()) return out
            // Roulette-wheel pick over the sources that still have items.
            val total = candidates.fold(0f) { acc, i -> acc + w[i] }
            var roll = Random.nextDouble() * total
            var chosen = candidates.last()
            for (i in candidates) {
                roll -= w[i]
                if (roll < 0.0) {
                    chosen = i
                    break
                }
            }
            // Try the picked source first, then the other candidates in weight
            // order: the first whose next card has a different creator wins.
            val order = listOf(chosen) + candidates.filter { it != chosen }.sortedByDescending { w[it] }
            var picked = -1
            for (i in order) {
                val creator = creatorKey(sources[i][cursors[i]])
                if (creator == null || creator != lastCreator) {
                    picked = i
                    break
                }
            }
            // All remaining candidates repeat the last creator: accept the
            // roulette pick (no deadlock).
            if (picked < 0) picked = chosen
            val card = sources[picked][cursors[picked]]
            seen.add(card.id)
            out += card
            lastCreator = creatorKey(card)
            cursors[picked]++
        }
    }

    /**
     * Creator key for anti-repeat: the card's authorUrl when present, else the
     * author name. Null (e.g. channel cards) never counts as a repeat.
     */
    private fun creatorKey(card: Card): String? =
        when (card) {
            is VideoCard -> card.authorUrl ?: card.author
            is PlaylistCard -> card.author
            is PostCard -> card.author
            is ArticleCard -> card.author
            is ChannelCard -> null
        }
}
