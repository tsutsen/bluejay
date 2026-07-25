package com.tsutsen.platformplayer.sync.models

import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.tsutsen.platformplayer.models.Playlist
import com.tsutsen.platformplayer.models.Subscription
import com.tsutsen.platformplayer.models.SubscriptionGroup
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Dictionary

@Serializable
class SyncWatchLaterPackage(
    var videos: List<SerializedPlatformVideo>,
    var videoAdds: Map<String, Long>,
    var videoRemovals: Map<String, Long>,
    var reorderTime: Long = 0,
    var ordering: List<String>? = null
)