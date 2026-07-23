package com.futo.platformplayer.di

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.core.data.repository.HomeRepository
import com.futo.platformplayer.core.model.Card
import com.futo.platformplayer.core.model.ChannelCard
import com.futo.platformplayer.core.model.FeedPage
import com.futo.platformplayer.core.model.PlaylistCard
import com.futo.platformplayer.core.model.PostCard
import com.futo.platformplayer.core.model.VideoCard
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HomeRepository implementation that bridges to the engine (StatePlatform).
 * This implementation is used in the app module where engine APIs are available.
 */
@Singleton
class EngineHomeRepositoryImpl @Inject constructor() : HomeRepository {

    private val _feed = MutableStateFlow(FeedPage())
    override val feed: StateFlow<FeedPage> = _feed.asStateFlow()

    private var _lastPager: IPager<IPlatformContent>? = null

    override suspend fun loadInitial() {
        Logger.i("EngineHomeRepository", "loadInitial")
        _feed.update { it.copy(isLoading = true, error = null) }

        try {
            val pager = StatePlatform.instance.getHomeRefresh(CoroutineScope(Dispatchers.IO))
            _lastPager = pager
            Logger.i("EngineHomeRepository", "Got pager with ${pager.getResults().size} results, hasMore=${pager.hasMorePages()}")
            val items = convertToCards(pager.getResults())
            Logger.i("EngineHomeRepository", "Converted to ${items.size} cards")
            _feed.update {
                it.copy(
                    isLoading = false,
                    items = items,
                    hasMorePages = pager.hasMorePages(),
                    error = null,
                    currentPage = 1
                )
            }
        } catch (e: Exception) {
            Logger.e("EngineHomeRepository", "loadInitial failed", e)
            _feed.update {
                it.copy(
                    isLoading = false,
                    items = emptyList(),
                    hasMorePages = false,
                    error = e.message ?: "Failed to load feed"
                )
            }
        }
    }

    override suspend fun loadNextPage() {
        Logger.i("EngineHomeRepository", "loadNextPage")
        _feed.update { it.copy(isLoading = true) }

        try {
            val pager = _lastPager ?: return
            if (!pager.hasMorePages()) {
                _feed.update { it.copy(isLoading = false) }
                return
            }

            pager.nextPage()
            val items = convertToCards(pager.getResults())
            _feed.update {
                it.copy(
                    isLoading = false,
                    items = it.items + items,
                    hasMorePages = pager.hasMorePages(),
                    error = null,
                    currentPage = it.currentPage + 1
                )
            }
        } catch (e: Exception) {
            Logger.e("EngineHomeRepository", "loadNextPage failed", e)
            _feed.update {
                it.copy(isLoading = false, error = e.message ?: "Failed to load more")
            }
        }
    }

    override suspend fun refresh() {
        Logger.i("EngineHomeRepository", "refresh")
        _lastPager = null
        loadInitial()
    }

    override suspend fun filterByTag(tag: String) {
        // TODO: Implement
    }

    override suspend fun filterByAuthor(authorId: String) {
        // TODO: Implement
    }

    private fun extractThumbnailUrl(content: IPlatformContent): String? {
        return when (content) {
            is IPlatformVideo -> content.thumbnails.getHQThumbnail() ?: content.thumbnails.getLQThumbnail()
            is IPlatformLockedContent -> content.contentThumbnails.getHQThumbnail() ?: content.contentThumbnails.getLQThumbnail()
            is IPlatformNestedContent -> content.contentThumbnails.getHQThumbnail() ?: content.contentThumbnails.getLQThumbnail()
            else -> null
        }
    }

    private fun convertToCards(items: List<IPlatformContent>): List<Card> {
        return items.mapNotNull { content ->
            try {
                val thumbnailUrl = extractThumbnailUrl(content)
                when (content.contentType) {
                    ContentType.MEDIA, ContentType.NESTED_VIDEO -> {
                        val video = content as? IPlatformVideo
                        VideoCard(
                            id = content.id.toString(),
                            title = content.name,
                            thumbnailUrl = thumbnailUrl,
                            author = content.author.name,
                            durationMs = (video?.duration ?: 0) * 1000,
                            viewCount = video?.viewCount,
                            publishedAt = video?.playbackDate?.toInstant()?.toEpochMilli(),
                            url = content.url
                        )
                    }
                    ContentType.PLAYLIST -> {
                        PlaylistCard(
                            id = content.id.toString(),
                            title = content.name,
                            thumbnailUrl = thumbnailUrl,
                            url = content.url
                        )
                    }
                    ContentType.CHANNEL -> {
                        ChannelCard(
                            id = content.id.toString(),
                            title = content.name,
                            thumbnailUrl = thumbnailUrl,
                            url = content.url
                        )
                    }
                    ContentType.POST -> {
                        PostCard(
                            id = content.id.toString(),
                            title = content.name,
                            thumbnailUrl = thumbnailUrl,
                            author = content.author.name,
                            publishedAt = content.datetime?.toInstant()?.toEpochMilli(),
                            url = content.url
                        )
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Logger.w("EngineHomeRepository", "Failed to convert content: ${content.id}", e)
                null
            }
        }
    }
}
