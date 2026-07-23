package com.futo.platformplayer.core.data.repository

import com.futo.platformplayer.core.model.LibrarySection
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {

    val sections: StateFlow<List<LibrarySection>>

    suspend fun loadHistory()
    suspend fun loadWatchLater()
    suspend fun loadPlaylists()
    suspend fun addToWatchLater(videoId: String)
    suspend fun removeFromWatchLater(videoId: String)
    suspend fun createPlaylist(name: String, description: String? = null): Long
    suspend fun addVideoToPlaylist(playlistId: Long, videoId: String)
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String)
}
