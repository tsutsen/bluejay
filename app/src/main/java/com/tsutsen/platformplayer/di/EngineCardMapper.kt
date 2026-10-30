package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.IPlatformChannelContent
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.tsutsen.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.models.article.IPlatformArticle
import com.tsutsen.platformplayer.api.media.models.post.IPlatformPost
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSWeb
import com.tsutsen.platformplayer.core.database.entity.HomeFeedCacheEntity
import com.tsutsen.platformplayer.core.model.ArticleCard
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.PostCard
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlugins
import java.time.OffsetDateTime

/**
 * Single engine content → Card mapper. One convention for every feed:
 * - id: "platform:value"
 * - thumbnail: HQ with LQ fallback
 * - duration: always milliseconds
 * - publishedAt: always epoch milliseconds
 * - authorUrl: always set where an author exists
 */
object EngineCardMapper {
    private const val CACHE_TYPE_VIDEO = "video"
    private const val CACHE_TYPE_PLAYLIST = "playlist"
    private const val CACHE_TYPE_CHANNEL = "channel"
    private const val CACHE_TYPE_POST = "post"
    private const val CACHE_TYPE_ARTICLE = "article"

    fun toCards(contents: List<IPlatformContent>): List<Card> =
        contents.mapNotNull { content ->
            try {
                toCard(content)
            } catch (e: Exception) {
                Logger.w("EngineCardMapper", "Failed to convert ${content::class.simpleName}: ${content.id}", e)
                null
            }
        }

    fun toCard(content: IPlatformContent): Card? =
        when (content) {
            is IPlatformVideo -> {
                videoCard(content)
            }

            is IPlatformChannelContent -> {
                ChannelCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = content.thumbnail,
                    subscriberCount = content.subscribers?.takeIf { it > 0 },
                    url = content.url,
                    sourceId = content.id.pluginId,
                    sourceIconUrl = sourceIcon(content.id.pluginId),
                )
            }

            is IPlatformPlaylist -> {
                PlaylistCard(
                    id = playlistId(content),
                    title = content.name,
                    thumbnailUrl = content.thumbnail,
                    videoCount = content.videoCount.takeIf { it > 0 },
                    author = content.author.name,
                    url = content.url,
                    sourceId = content.id.pluginId,
                )
            }

            is IPlatformPost -> {
                PostCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = content.thumbnails.firstOrNull()?.getHQThumbnail(),
                    author = content.author.name,
                    publishedAt = epochMs(content.datetime),
                    url = content.url,
                    sourceId = content.id.pluginId,
                )
            }

            is IPlatformArticle -> {
                ArticleCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = content.thumbnails?.getHQThumbnail(),
                    author = content.author.name,
                    publishedAt = epochMs(content.datetime),
                    url = content.url,
                    sourceId = content.id.pluginId,
                )
            }

            is JSWeb -> {
                ArticleCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = null,
                    author = content.author.name,
                    publishedAt = epochMs(content.datetime),
                    url = content.url,
                    sourceId = content.id.pluginId,
                )
            }

            is IPlatformNestedContent, is IPlatformLockedContent -> {
                VideoCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = contentThumbnails(content).let { it?.getHQThumbnail() ?: it?.getLQThumbnail() },
                    author = content.author.name,
                    durationMs = null,
                    publishedAt = epochMs(content.datetime),
                    url = content.url,
                    authorUrl = content.author.url,
                    sourceId = content.id.pluginId,
                )
            }

            else -> {
                null
            }
        }

    fun contentId(id: PlatformID): String = "${id.platform}:${id.value ?: ""}"

    /**
     * Engine playlists often arrive without an id ("YouTube:"), and
     * VideoContainer keys items by card id — colliding ids crash the
     * LazyColumn. Fall back to url, then name, which are unique.
     */
    private fun playlistId(playlist: IPlatformPlaylist): String =
        playlist.id.value
            ?.takeIf { it.isNotBlank() }
            ?.let { contentId(playlist.id) }
            ?: playlist.url.ifBlank { playlist.name }

    private fun videoCard(video: IPlatformVideo): VideoCard =
        VideoCard(
            id = contentId(video.id),
            title = video.name,
            thumbnailUrl = video.thumbnails.getHQThumbnail() ?: video.thumbnails.getLQThumbnail(),
            author = video.author.name,
            durationMs = if (video.duration > 0) video.duration * 1000 else null,
            isLive = video.isLive,
            // Twitch clips live under https://www.twitch.tv/<login>/clip/<slug>
            isClip = video.url.contains("/clip/"),
            viewCount = video.viewCount.takeIf { it > 0 },
            publishedAt = epochMs(video.playbackDate ?: video.datetime),
            url = video.url,
            authorUrl = video.author.url,
            sourceId = video.id.pluginId,
        )

    private fun contentThumbnails(content: IPlatformContent): Thumbnails? =
        when (content) {
            is IPlatformNestedContent -> content.contentThumbnails
            is IPlatformLockedContent -> content.contentThumbnails
            else -> null
        }

    private fun epochMs(datetime: OffsetDateTime?): Long? = datetime?.toInstant()?.toEpochMilli()

    /**
     * Display-only snapshot for the home-feed cache. Video stats
     * (durationMs/viewCount/publishedAt) ride along so cold-start cached
     * cards paint the same pills as live cards; non-video cards have none.
     */
    fun toCacheEntity(card: Card): HomeFeedCacheEntity {
        val video = card as? VideoCard
        val (contentUrl, author) =
            when (card) {
                is VideoCard -> card.url to card.author
                is PlaylistCard -> card.url to card.author
                is ChannelCard -> card.url to null
                is PostCard -> card.url to card.author
                is ArticleCard -> card.url to card.author
            }
        val contentType =
            when (card) {
                is VideoCard -> CACHE_TYPE_VIDEO
                is PlaylistCard -> CACHE_TYPE_PLAYLIST
                is ChannelCard -> CACHE_TYPE_CHANNEL
                is PostCard -> CACHE_TYPE_POST
                is ArticleCard -> CACHE_TYPE_ARTICLE
            }
        return HomeFeedCacheEntity(
            cacheKey = card.id,
            contentUrl = contentUrl,
            title = card.title,
            author = author,
            thumbnailUrl = card.thumbnailUrl,
            contentType = contentType,
            durationMs = video?.durationMs,
            viewCount = video?.viewCount,
            publishedAt = video?.publishedAt,
        )
    }

    fun toCardFromCache(entity: HomeFeedCacheEntity): Card? {
        val id = entity.cacheKey
        val sourceId = cacheSourceId(id)
        return when (entity.contentType) {
            CACHE_TYPE_VIDEO ->
                VideoCard(
                    id = id,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    author = entity.author,
                    durationMs = entity.durationMs,
                    viewCount = entity.viewCount,
                    publishedAt = entity.publishedAt,
                    url = entity.contentUrl,
                    sourceId = sourceId,
                )

            CACHE_TYPE_PLAYLIST ->
                PlaylistCard(
                    id = id,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    author = entity.author,
                    url = entity.contentUrl,
                    sourceId = sourceId,
                )

            CACHE_TYPE_CHANNEL ->
                ChannelCard(
                    id = id,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    url = entity.contentUrl,
                    sourceId = sourceId,
                )

            CACHE_TYPE_POST ->
                PostCard(
                    id = id,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    author = entity.author,
                    url = entity.contentUrl,
                    sourceId = sourceId,
                )

            CACHE_TYPE_ARTICLE ->
                ArticleCard(
                    id = id,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    author = entity.author,
                    url = entity.contentUrl,
                    sourceId = sourceId,
                )

            else -> null
        }
    }

    /** Card ids are "pluginId:value"; URL/name playlist fallbacks have none. */
    private fun cacheSourceId(id: String): String? {
        if (id.contains("://")) return null
        val separator = id.indexOf(':')
        return if (separator > 0) id.substring(0, separator) else null
    }

    /**
     * Source badge for channel cards: the plugin's stored icon, only when
     * more than one source is enabled (single-source makes it pointless).
     */
    private fun sourceIcon(pluginId: String?): String? {
        if (pluginId.isNullOrEmpty()) return null
        if (StatePlatform.instance.getEnabledClients().size <= 1) return null
        return StatePlugins.instance.getPluginIconUriOrNull(pluginId)
    }
}
