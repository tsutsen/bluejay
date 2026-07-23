package com.futo.platformplayer.core.database.dao

import androidx.room.*
import com.futo.platformplayer.core.database.entity.PlaylistEntity
import com.futo.platformplayer.core.database.entity.PlaylistVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // Playlist operations
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginated(limit: Int, offset: Int): List<PlaylistEntity>

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
    suspend fun getVideosPaginated(playlistId: Long, limit: Int, offset: Int): List<PlaylistVideoEntity>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId AND contentUrl = :contentUrl")
    suspend fun getVideoInPlaylist(playlistId: Long, contentUrl: String): PlaylistVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: PlaylistVideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<PlaylistVideoEntity>)

    @Delete
    suspend fun deleteVideo(video: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND contentUrl = :contentUrl")
    suspend fun deleteVideoFromPlaylist(playlistId: Long, contentUrl: String)

    @Query("UPDATE playlist_videos SET videoOrder = :newOrder WHERE playlistId = :playlistId AND videoOrder = :oldOrder")
    suspend fun reorderVideo(playlistId: Long, oldOrder: Int, newOrder: Int)

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun countVideos(playlistId: Long): Int
}
