package com.tsutsen.platformplayer.api.media.models.video

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.Serializer
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.serializers.OffsetDateTimeNullableSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class SerializedPlatformVideo(
    override val contentType: ContentType = ContentType.MEDIA,
    override val id: PlatformID,
    override val name: String,
    override val thumbnails: Thumbnails = Thumbnails(),
    override val author: PlatformAuthorLink,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    @JsonNames("datetime", "dateTime")
    override val datetime: OffsetDateTime? = null,
    override val url: String,
    override val shareUrl: String = "",

    override val duration: Long,
    override val viewCount: Long,
    override val isShort: Boolean = false
) : IPlatformVideo, SerializedPlatformContent {

    override val isLive: Boolean = false;

    override var playbackTime: Long = -1;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override var playbackDate: OffsetDateTime? = null;

    override fun toJson() : String {
        return Json.encodeToString(this);
    }
    override fun fromJson(str : String) : SerializedPlatformVideo {
        return Serializer.json.decodeFromString<SerializedPlatformVideo>(str);
    }
    override fun fromJsonArray(str : String) : Array<SerializedPlatformContent> {
        return Serializer.json.decodeFromString<Array<SerializedPlatformContent>>(str);
    }

    companion object {
        fun fromVideo(video: IPlatformVideo) : SerializedPlatformVideo {
            return SerializedPlatformVideo(
                ContentType.MEDIA,
                video.id,
                video.name,
                video.thumbnails,
                video.author,
                video.datetime,
                video.url,
                video.shareUrl,
                video.duration,
                video.viewCount
            );
        }
    }
}