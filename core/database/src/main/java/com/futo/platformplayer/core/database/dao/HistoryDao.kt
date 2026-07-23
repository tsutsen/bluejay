package com.futo.platformplayer.core.database.dao

import androidx.room.*
import com.futo.platformplayer.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE contentUrl = :url")
    suspend fun getByUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginated(limit: Int, offset: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE lastPositionMs > 0 ORDER BY watchedAt DESC")
    fun observeContinueWatching(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: HistoryEntity)

    @Update
    suspend fun update(history: HistoryEntity)

    @Delete
    suspend fun delete(history: HistoryEntity)

    @Query("DELETE FROM history WHERE contentUrl = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}
