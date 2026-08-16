package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.tsutsen.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.models.post.IPlatformPost
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelCard
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.PostCard
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.logging.Logger
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

            is IPlatformChannel -> {
                ChannelCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = content.thumbnail,
                    subscriberCount = content.subscribers.takeIf { it > 0 },
                    url = content.url,
                )
            }

            is IPlatformPlaylist -> {
                PlaylistCard(
                    id = contentId(content.id),
                    title = content.name,
                    thumbnailUrl = content.thumbnail,
                    videoCount = content.videoCount.takeIf { it > 0 },
                    author = content.author.name,
                    url = content.url,
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
                )
            }

            else -> {
                null
            }
        }

    fun contentId(id: PlatformID): String = "${id.platform}:${id.value ?: ""}"

    private fun videoCard(video: IPlatformVideo): VideoCard =
        VideoCard(
            id = contentId(video.id),
            title = video.name,
            thumbnailUrl = video.thumbnails.getHQThumbnail() ?: video.thumbnails.getLQThumbnail(),
            author = video.author.name,
            durationMs = if (video.duration > 0) video.duration * 1000 else null,
            viewCount = video.viewCount.takeIf { it > 0 },
            publishedAt = epochMs(video.playbackDate ?: video.datetime),
            url = video.url,
            authorUrl = video.author.url,
        )

    private fun contentThumbnails(content: IPlatformContent): Thumbnails? =
        when (content) {
            is IPlatformNestedContent -> content.contentThumbnails
            is IPlatformLockedContent -> content.contentThumbnails
            else -> null
        }

    private fun epochMs(datetime: OffsetDateTime?): Long? = datetime?.toInstant()?.toEpochMilli()
}
