package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistInfo
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Library = saved videos + playlists. Sections are reactive (DAO-backed);
 * no explicit load calls needed.
 */
interface LibraryRepository {
    /** Watch Later, Liked, Favourites, Playlists. Items capped at [SECTION_ITEM_LIMIT]. */
    val sections: StateFlow<List<LibrarySection>>

    /** All items for a section, newest first (used by the detail screen). */
    fun observeSectionItems(sectionId: String): Flow<List<Card>>

    /** Resolves a user-created local playlist (id from "playlist:<id>" urls). */
    suspend fun getLocalPlaylist(playlistId: Long): PlaylistInfo?

    /** All videos of a local playlist, ordered by videoOrder. */
    suspend fun getLocalPlaylistVideos(playlistId: Long): List<Card>

    /** User playlists, for the "Add to playlist" sheet action. */
    val playlists: StateFlow<List<PlaylistOption>>

    /** Which save types apply to a video (for the options sheet toggles). */
    fun observeSavedTypes(url: String): Flow<Set<SavedVideoType>>

    suspend fun saveVideo(
        type: SavedVideoType,
        video: VideoCard,
    )

    suspend fun removeSavedVideo(
        type: SavedVideoType,
        url: String,
    )

    suspend fun createPlaylist(
        name: String,
        description: String? = null,
    ): Long

    suspend fun addVideoToPlaylist(
        playlistId: Long,
        video: VideoCard,
    )

    suspend fun removeVideoFromPlaylist(
        playlistId: Long,
        url: String,
    )

    companion object {
        const val SECTION_ITEM_LIMIT = 20
    }
}
