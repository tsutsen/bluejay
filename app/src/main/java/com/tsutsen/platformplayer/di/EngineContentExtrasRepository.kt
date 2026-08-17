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
        private val recommendationFlows = mutableMapOf<String, PagerFlow<IPlatformContent, Card>>()

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
                    recommendationFlows.getOrPut(url) {
                        PagerFlow(
                            pager,
                            { content -> EngineCardMapper.toCard(content) },
                            { it.id },
                        )
                    }
                flow.loadInitial()
                flow.items
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to fetch recommendations for $url", e)
                emptyList()
            }
    }
