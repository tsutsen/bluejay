package com.tsutsen.platformplayer.core.database.dao

import androidx.room.*
import com.tsutsen.platformplayer.core.database.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions ORDER BY channelName ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE channelId = :channelId")
    suspend fun getByChannelId(channelId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions ORDER BY channelName ASC LIMIT :limit OFFSET :offset")
    suspend fun getPaginated(limit: Int, offset: Int): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE channelName LIKE '%' || :query || '%' ORDER BY channelName ASC")
    fun search(query: String): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: String)

    @Query("DELETE FROM subscriptions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun count(): Int
}
