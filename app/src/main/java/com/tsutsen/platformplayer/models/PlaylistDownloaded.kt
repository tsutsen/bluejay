package com.tsutsen.platformplayer.models

import com.tsutsen.platformplayer.downloads.PlaylistDownloadDescriptor

data class PlaylistDownloaded(
    val downloadDescriptor: PlaylistDownloadDescriptor,
    val playlist: Playlist
);