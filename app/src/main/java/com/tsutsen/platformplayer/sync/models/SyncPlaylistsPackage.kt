package com.tsutsen.platformplayer.sync.models

import com.tsutsen.platformplayer.models.Playlist
import com.tsutsen.platformplayer.models.Subscription
import com.tsutsen.platformplayer.models.SubscriptionGroup
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Dictionary

@Serializable
class SyncPlaylistsPackage(
    var playlists: List<Playlist>,
    var playlistRemovals: Map<String, Long>
)