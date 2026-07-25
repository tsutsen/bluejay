package com.tsutsen.platformplayer.stores

import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class WatchLaterStorage : FragmentedStorageFileJson() {

    var playlist = listOf<SerializedPlatformVideo>();

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}