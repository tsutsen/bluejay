package com.tsutsen.platformplayer.api.media.models.video

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.Serializer
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.tsutsen.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.tsutsen.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.tsutsen.platformplayer.states.StatePlatform
import com.futo.polycentric.core.combineHashCodes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class SerializedPlatformLockedContent(
    override val id: PlatformID,
    override val name: String,
    override val author: PlatformAuthorLink,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val datetime: OffsetDateTime?,
    override val url: String,
    override val shareUrl: String,
    override val lockContentType: ContentType,
    override val contentName: String?,
    override val lockDescription: String? = null,
    override val unlockUrl: String? = null,
    override val contentThumbnails: Thumbnails
) : IPlatformLockedContent, SerializedPlatformContent {
    override val contentType: ContentType = ContentType.LOCKED;

    override fun toJson() : String {
        return Json.encodeToString(this);
    }
    override fun fromJson(str : String) : SerializedPlatformLockedContent {
        return Serializer.json.decodeFromString<SerializedPlatformLockedContent>(str);
    }
    override fun fromJsonArray(str : String) : Array<SerializedPlatformContent> {
        return Serializer.json.decodeFromString<Array<SerializedPlatformContent>>(str);
    }

    companion object {
        fun fromLocked(content: IPlatformLockedContent) : SerializedPlatformLockedContent {
            return SerializedPlatformLockedContent(
                content.id,
                content.name,
                content.author,
                content.datetime,
                content.url,
                content.shareUrl,
                content.lockContentType,
                content.contentName,
                content.lockDescription,
                content.unlockUrl,
                content.contentThumbnails
            );
        }
    }
}