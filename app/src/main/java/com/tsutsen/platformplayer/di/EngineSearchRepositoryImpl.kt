package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.tsutsen.platformplayer.core.model.SearchResult
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SearchRepository implementation that bridges to the engine (StatePlatform).
 * Uses StatePlatform.search() to query all enabled platforms in parallel.
 */
@Singleton
class EngineSearchRepositoryImpl @Inject constructor() : SearchRepository {

    private val _results = MutableStateFlow(SearchResult())
    override val results: StateFlow<SearchResult> = _results.asStateFlow()

    private var _lastPager: IPager<IPlatformContent>? = null
    private var _lastQuery: String = ""

    override suspend fun search(query: String, sources: Set<String>) {
        Logger.i("EngineSearchRepository", "search: $query, sources: $sources")
        _lastQuery = query
        _results.update { it.copy(query = query, isLoading = true, error = null, items = emptyList()) }

        try {
            // Run engine call on IO dispatcher to avoid main thread blocking
            withContext(Dispatchers.IO) {
                val pager = StatePlatform.instance.search(query)
                _lastPager = pager
                val items = convertToCards(pager.getResults())
                _results.update {
                    it.copy(
                        isLoading = false,
                        items = items,
                        hasMorePages = pager.hasMorePages(),
                        error = null
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("EngineSearchRepository", "search failed", e)
            _results.update {
                it.copy(
                    isLoading = false,
                    items = emptyList(),
                    hasMorePages = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

    override suspend fun clearResults() {
        Logger.i("EngineSearchRepository", "clearResults")
        _results.update { SearchResult() }
        _lastPager = null
        _lastQuery = ""
    }

    override suspend fun nextPage() {
        Logger.i("EngineSearchRepository", "nextPage")
        _results.update { it.copy(isLoading = true) }

        try {
            val pager = _lastPager ?: return
            if (!pager.hasMorePages()) {
                _results.update { it.copy(isLoading = false) }
                return
            }

            pager.nextPage()
            val newItems = convertToCards(pager.getResults())
            val previousItems = _results.value.items
            val accumulatedItems = previousItems + newItems
            _results.update {
                it.copy(
                    isLoading = false,
                    items = accumulatedItems,
                    hasMorePages = pager.hasMorePages(),
                    error = null
                )
            }
        } catch (e: Exception) {
            Logger.e("EngineSearchRepository", "nextPage failed", e)
            _results.update {
                it.copy(isLoading = false, error = e.message ?: "Failed to load more")
            }
        }
    }

    private fun convertToCards(items: List<IPlatformContent>): List<Card> {
        return items.mapNotNull { content ->
            try {
                when (content) {
                    is com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo -> {
                        VideoCard(
                            id = content.id.platform + ":" + (content.id.value ?: ""),
                            title = content.name,
                            thumbnailUrl = content.thumbnails.getHQThumbnail(),
                            author = content.author.name,
                            url = content.url,
                            durationMs = if (content.duration > 0) content.duration else null,
                            viewCount = if (content.viewCount > 0) content.viewCount else null,
                            publishedAt = content.datetime?.toInstant()?.toEpochMilli()
                        )
                    }
                    is com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel -> {
                        ChannelCard(
                            id = content.id.platform + ":" + (content.id.value ?: ""),
                            title = content.name,
                            thumbnailUrl = content.thumbnail,
                            subscriberCount = if (content.subscribers > 0) content.subscribers else null,
                            url = content.url
                        )
                    }
                    else -> {
                        Logger.w("EngineSearchRepository", "Unknown content type: ${content::class.simpleName}")
                        null
                    }
                }
            } catch (e: Exception) {
                Logger.w("EngineSearchRepository", "Failed to convert content", e)
                null
            }
        }
    }
}
