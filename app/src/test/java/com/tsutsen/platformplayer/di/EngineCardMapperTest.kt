package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.post.IPlatformPost
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.model.PostCard
import com.tsutsen.platformplayer.core.model.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class EngineCardMapperTest {
    private val authorLink =
        PlatformAuthorLink(
            PlatformID("youtube", "ch1"),
            "Author Name",
            "https://yt.example/ch1",
            "https://img.example/author.png",
        )

    private fun video(
        id: PlatformID = PlatformID("youtube", "v1"),
        duration: Long = 90,
        viewCount: Long = 1234,
        playbackDate: OffsetDateTime? = OffsetDateTime.parse("2024-05-04T12:00:00Z"),
        datetime: OffsetDateTime? = null,
        thumbnails: Thumbnails =
            Thumbnails(
                arrayOf(
                    Thumbnail("https://i.example/lq.jpg", 120),
                    Thumbnail("https://i.example/hq.jpg", 480),
                ),
            ),
    ) = object : IPlatformVideo {
        override val contentType = ContentType.MEDIA
        override val id = id
        override val name = "Video Title"
        override val url = "https://yt.example/v1"
        override val shareUrl = "https://yt.example/v1"
        override val datetime = datetime
        override val author = authorLink
        override val thumbnails = thumbnails
        override val duration = duration
        override val viewCount = viewCount
        override val playbackTime = 0L
        override val playbackDate = playbackDate
        override val isLive = false
        override val isShort = false
    }

    @Test
    fun videoCard_usesUnifiedConventions() {
        val card = EngineCardMapper.toCard(video()) as VideoCard

        assertEquals("youtube:v1", card.id)
        assertEquals(90_000L, card.durationMs) // seconds -> milliseconds
        assertEquals(
            OffsetDateTime.parse("2024-05-04T12:00:00Z").toInstant().toEpochMilli(),
            card.publishedAt,
        )
        assertEquals("https://i.example/hq.jpg", card.thumbnailUrl) // HQ preferred
        assertEquals("Author Name", card.author)
        assertEquals("https://yt.example/ch1", card.authorUrl)
        assertEquals(1234L, card.viewCount)
    }

    @Test
    fun videoCard_zeroDurationAndCount_becomeNull_fallsBackToDatetime() {
        val card =
            EngineCardMapper.toCard(
                video(
                    duration = 0,
                    viewCount = 0,
                    playbackDate = null,
                    datetime = OffsetDateTime.parse("2024-01-02T03:04:05Z"),
                    thumbnails = Thumbnails(arrayOf(Thumbnail("https://i.example/only.jpg", 240))),
                ),
            ) as VideoCard

        assertNull(card.durationMs)
        assertNull(card.viewCount)
        assertEquals(
            OffsetDateTime.parse("2024-01-02T03:04:05Z").toInstant().toEpochMilli(),
            card.publishedAt,
        )
        assertEquals("https://i.example/only.jpg", card.thumbnailUrl)
    }

    @Test
    fun videoCard_nullIdValue_keepsPlatformPrefix() {
        val card = EngineCardMapper.toCard(video(id = PlatformID("youtube", null))) as VideoCard
        assertEquals("youtube:", card.id)
    }

    @Test
    fun postCard_mapsFields() {
        val post =
            object : IPlatformPost {
                override val contentType = ContentType.POST
                override val id = PlatformID("postplatform", "p1")
                override val name = "Post Title"
                override val url = "https://p.example/p1"
                override val shareUrl = "https://p.example/p1"
                override val datetime = OffsetDateTime.parse("2024-06-01T00:00:00Z")
                override val author = authorLink
                override val description = "body"
                override val thumbnails = listOf(Thumbnails(arrayOf(Thumbnail("https://i.example/post.jpg", 320))))
                override val images = emptyList<String>()
            }

        val card = EngineCardMapper.toCard(post) as PostCard

        assertEquals("postplatform:p1", card.id)
        assertEquals("Post Title", card.title)
        assertEquals("https://i.example/post.jpg", card.thumbnailUrl)
        assertEquals("Author Name", card.author)
        assertEquals(
            OffsetDateTime.parse("2024-06-01T00:00:00Z").toInstant().toEpochMilli(),
            card.publishedAt,
        )
    }

    @Test
    fun unknownContent_returnsNull_andToCardsDropsIt() {
        val unknown =
            object : IPlatformContent {
                override val contentType = ContentType.UNKNOWN
                override val id = PlatformID("unknown", "x")
                override val name = "Unknown"
                override val url = ""
                override val shareUrl = ""
                override val datetime = null
                override val author = PlatformAuthorLink.UNKNOWN
            }

        assertNull(EngineCardMapper.toCard(unknown))

        val cards = EngineCardMapper.toCards(listOf(unknown, video(), unknown))
        assertEquals(1, cards.size)
        assertTrue(cards[0] is VideoCard)
    }
}
