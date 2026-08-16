package com.tsutsen.platformplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tsutsen.platformplayer.core.database.entity.SavedVideoEntity
import com.tsutsen.platformplayer.core.model.SavedVideoType
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedVideoDao {
    @Query("SELECT * FROM saved_video WHERE type = :type ORDER BY addedAt DESC")
    fun observeByType(type: SavedVideoType): Flow<List<SavedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: SavedVideoEntity)

    @Delete
    suspend fun delete(video: SavedVideoEntity)

    @Query("DELETE FROM saved_video WHERE contentUrl = :url AND type = :type")
    suspend fun deleteByType(
        url: String,
        type: SavedVideoType,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM saved_video WHERE contentUrl = :url AND type = :type)")
    suspend fun containsUrl(
        url: String,
        type: SavedVideoType,
    ): Boolean
}
