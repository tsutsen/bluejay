package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.VideoChapter
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine-based content extras (chapters + recommendations).
 * Follows the same StatePlatform / PagerFlow / EngineCardMapper pattern
 * as the other Engine* repositories.
 */
@Singleton
class EngineContentExtrasRepository
    @Inject
    constructor() : ContentExtrasRepository {
        private val TAG = "EngineContentExtrasRepository"

        // The CURRENT video's pager only: a fresh flow per open (a cached
        // flow keeps the previous visit's pager position and would resume
        // mid-window), and no unbounded per-URL map.
        private var recommendationUrl: String? = null
        private var recommendationFlow: PagerFlow<IPlatformContent, Card>? = null

        override suspend fun getChapters(url: String): List<VideoChapter> =
            try {
                StatePlatform.instance
                    .getContentChapters(url)
                    ?.map { chapter ->
                        VideoChapter(
                            title = chapter.name,
                            startTimeMs = (chapter.timeStart * 1000).toLong(),
                            endTimeMs = (chapter.timeEnd * 1000).toLong(),
                        )
                    }
                    ?: emptyList()
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to fetch chapters for $url", e)
                emptyList()
            }

        override suspend fun getRecommendations(url: String): List<Card> =
            try {
                val pager = StatePlatform.instance.getContentRecommendations(url) ?: return emptyList()
                val flow =
                    PagerFlow(
                        pager,
                        { content -> EngineCardMapper.toCard(content) },
                        { it.id },
                    )
                recommendationFlow = flow
                recommendationUrl = url
                flow.loadInitial()
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to fetch recommendations for $url", e)
                emptyList()
            }

        override suspend fun loadMoreRecommendations(url: String): List<Card> {
            // A different video played since: this pager no longer applies.
            if (url != recommendationUrl) return emptyList()
            val flow = recommendationFlow ?: return emptyList()
            if (!flow.hasMore) return emptyList()
            return try {
                flow.loadNextPage()
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to load more recommendations for $url", e)
                emptyList()
            }
        }
    }
