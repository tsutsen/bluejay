package com.futo.platformplayer.core.database.dao

import androidx.room.*
import com.futo.platformplayer.core.database.entity.HomeFeedCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeFeedCacheDao {

    @Query("SELECT * FROM home_feed_cache WHERE expiresAt > :currentTime ORDER BY cachedAt DESC")
    fun observeValidCache(currentTime: Long = System.currentTimeMillis()): Flow<List<HomeFeedCacheEntity>>

    @Query("SELECT * FROM home_feed_cache WHERE cacheKey = :key AND expiresAt > :currentTime")
    suspend fun getCached(key: String, currentTime: Long = System.currentTimeMillis()): HomeFeedCacheEntity?

    @Query("SELECT * FROM home_feed_cache WHERE contentType = :type AND expiresAt > :currentTime ORDER BY cachedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginatedByType(type: String, limit: Int, offset: Int, currentTime: Long = System.currentTimeMillis()): List<HomeFeedCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: HomeFeedCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(caches: List<HomeFeedCacheEntity>)

    @Delete
    suspend fun delete(cache: HomeFeedCacheEntity)

    @Query("DELETE FROM home_feed_cache WHERE expiresAt <= :currentTime")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM home_feed_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM home_feed_cache")
    suspend fun count(): Int
}
