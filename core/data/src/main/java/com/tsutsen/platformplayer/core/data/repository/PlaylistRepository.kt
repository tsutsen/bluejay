package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.PlaylistInfo

/**
 * Playlist data: info + video pagination.
 *
 * Reuses [ChannelContentPage] — it is a generic "page of cards" result; no
 * second data class is needed for the same shape.
 */
interface PlaylistRepository {
    /** Resolves and returns playlist info. Throws if the url has no client. */
    suspend fun getPlaylist(url: String): PlaylistInfo

    /** Loads the first page of the playlist's videos. */
    suspend fun loadInitialVideos(url: String): ChannelContentPage

    /** Loads the next page; [ChannelContentPage.cards] is the full accumulated list. */
    suspend fun loadNextPage(url: String): ChannelContentPage
}
