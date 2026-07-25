package com.tsutsen.platformplayer.api.media.models.playlists

import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.models.Playlist

interface IPlatformPlaylistDetails: IPlatformPlaylist {
    //TODO: Determine if this should be IPlatformContent (probably not?)
    val contents: IPager<IPlatformVideo>;

    fun toPlaylist(onProgress: ((progress: Int) -> Unit)? = null): Playlist;
}