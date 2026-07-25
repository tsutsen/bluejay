package com.tsutsen.platformplayer.models

import com.tsutsen.platformplayer.api.media.models.channels.SerializedChannel
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import kotlinx.serialization.Serializable

@Serializable
class ImportCache(
    var videos: List<SerializedPlatformVideo>? = null,
    var channels: List<SerializedChannel>? = null
);