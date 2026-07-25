package com.tsutsen.platformplayer.subsexchange

import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformContent
import com.tsutsen.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.tsutsen.platformplayer.serializers.OffsetDateTimeSerializer
import com.tsutsen.platformplayer.serializers.OffsetDateTimeStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class ChannelResult(
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    @SerialName("dateTime")
    var dateTime: OffsetDateTime,
    @SerialName("channelUrl")
    var channelUrl: String,
    @SerialName("content")
    var content: List<SerializedPlatformContent>,
    @SerialName("channel")
    var channel: IPlatformChannel? = null
)