package com.tsutsen.platformplayer.core.database.dao

import androidx.room.*
import com.tsutsen.platformplayer.core.database.entity.PlaylistEntity
import com.tsutsen.platformplayer.core.database.entity.PlaylistVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // Playlist operations
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    // Like observeAll, but videoCount is the REAL row count, not the stored
    // counter (which drifted: the old count-based videoOrder collided after
    // removals left gaps, INSERT IGNORE dropped the row, and the counter
    // still incremented). Rows are the source of truth.
    @Query(
        "SELECT p.id, p.name, p.description, p.thumbnailUrl, " +
            "(SELECT COUNT(*) FROM playlist_videos pv WHERE pv.playlistId = p.id) AS videoCount, " +
            "p.createdAt, p.updatedAt " +
            "FROM playlists p ORDER BY p.updatedAt DESC",
    )
    fun observeAllWithCounts(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginated(
        limit: Int,
        offset: Int,
    ): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Playlist video operations
    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY videoOrder ASC")
    fun observeVideos(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY videoOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getVideosPaginated(
        playlistId: Long,
        limit: Int,
        offset: Int,
    ): List<PlaylistVideoEntity>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId AND contentUrl = :contentUrl")
    suspend fun getVideoInPlaylist(
        playlistId: Long,
        contentUrl: String,
    ): PlaylistVideoEntity?

    // IGNORE (not REPLACE): a duplicate (playlistId, contentUrl) is a
    // no-op, so re-adding a video that's already in the playlist can
    // neither create a duplicate row nor reset its position.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideo(video: PlaylistVideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<PlaylistVideoEntity>)

    @Delete
    suspend fun deleteVideo(video: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND contentUrl = :contentUrl")
    suspend fun deleteVideoFromPlaylist(
        playlistId: Long,
        contentUrl: String,
    )

    @Query("UPDATE playlist_videos SET videoOrder = :newOrder WHERE playlistId = :playlistId AND videoOrder = :oldOrder")
    suspend fun reorderVideo(
        playlistId: Long,
        oldOrder: Int,
        newOrder: Int,
    )

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun countVideos(playlistId: Long): Int

    // Append position: max(existing) + 1. countVideos() collides with an
    // existing videoOrder whenever a removal left a gap, and the IGNORE
    // conflict strategy then drops the new row silently.
    @Query("SELECT COALESCE(MAX(videoOrder), -1) + 1 FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun nextVideoOrder(playlistId: Long): Int

    // Reverse lookup for the options sheet: which playlists already contain
    // a video, so its checkboxes can be pre-checked. Reactive so a
    // just-added video flips its box on without a refresh.
    @Query("SELECT playlistId FROM playlist_videos WHERE contentUrl = :contentUrl")
    fun observePlaylistIdsForVideo(contentUrl: String): Flow<List<Long>>

    @Query(
        "SELECT (SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId) AS videoCount, " +
            "(SELECT COALESCE(SUM(durationMs), 0) FROM playlist_videos WHERE playlistId = :playlistId) AS totalDurationMs",
    )
    fun observePlaylistStats(playlistId: Long): Flow<PlaylistStatsEntity>

    @Query("SELECT contentUrl FROM playlist_videos WHERE playlistId = :playlistId ORDER BY videoOrder ASC LIMIT 1")
    suspend fun firstVideoUrl(playlistId: Long): String?

    /** Fills in the duration on rows that stored none (played-video backfill). */
    @Query("UPDATE playlist_videos SET durationMs = :durationMs WHERE contentUrl = :url AND durationMs = 0")
    suspend fun backfillDuration(
        url: String,
        durationMs: Long,
    )
}

/** Row mapping for [PlaylistDao.observePlaylistStats]. */
data class PlaylistStatsEntity(
    val videoCount: Int,
    val totalDurationMs: Long,
)
