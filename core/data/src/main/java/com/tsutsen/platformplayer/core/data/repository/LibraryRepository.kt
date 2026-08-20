package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistInfo
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.PlaylistStats
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

    /**
     * Reactive (info, videos) for a user-created local playlist (id from
     * "playlist:<id>" urls), so adds/removes from the options sheet land in
     * an open detail screen without a reload.
     */
    fun observeLocalPlaylist(playlistId: Long): Flow<Pair<PlaylistInfo, List<Card>>?>

    /** User playlists, for the "Add to playlist" sheet action. */
    val playlists: StateFlow<List<PlaylistOption>>

    /** Which save types apply to a video (for the options sheet toggles). */
    fun observeSavedTypes(url: String): Flow<Set<SavedVideoType>>

    /** Playlists already containing a video (pre-checks the options sheet). */
    fun observePlaylistsContaining(url: String): Flow<Set<Long>>

    suspend fun saveVideo(
        type: SavedVideoType,
        video: VideoCard,
    )

    /**
     * Backfills the real duration into saved-video and playlist rows that
     * stored none, called from the player once a video's duration is known.
     */
    suspend fun backfillDuration(
        contentUrl: String,
        durationMs: Long,
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

    /** Reactive stats for a local playlist (count + total duration). */
    fun observePlaylistStats(playlistId: Long): Flow<PlaylistStats>

    /** First video url in a local playlist (play target), null if empty. */
    suspend fun getFirstVideoUrl(playlistId: Long): String?

    /** Deletes a playlist and its videos (FK cascade). */
    suspend fun deletePlaylist(playlistId: Long)

    companion object {
        const val SECTION_ITEM_LIMIT = 20
    }
}
