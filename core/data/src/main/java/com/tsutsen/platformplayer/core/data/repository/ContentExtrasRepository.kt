package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.VideoChapter

/**
 * Engine-provided extras for a piece of content: chapters and
 * recommended (related) videos.
 */
interface ContentExtrasRepository {
    /** Chapters for the given content URL, in playback order (empty when the plugin has none). */
    suspend fun getChapters(url: String): List<VideoChapter>

    /** First page of recommended videos for the given content URL. */
    suspend fun getRecommendations(url: String): List<Card>

    /**
     * Next page of recommended videos for the given content URL (when the
     * list end is reached). Empty = no more pages.
     */
    suspend fun loadMoreRecommendations(url: String): List<Card>
}
