package com.tsutsen.platformplayer.api.media.models.playlists

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent

interface IPlatformPlaylist : IPlatformContent {
    val thumbnail: String?;
    val videoCount: Int;
}