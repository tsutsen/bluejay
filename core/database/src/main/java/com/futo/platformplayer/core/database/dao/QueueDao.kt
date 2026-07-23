package com.futo.platformplayer.core.database.dao

import androidx.room.*
import com.futo.platformplayer.core.database.entity.QueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue ORDER BY `order` ASC")
    fun observeAll(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM queue WHERE id = :id")
    suspend fun getById(id: Long): QueueEntity?

    @Query("SELECT * FROM queue ORDER BY `order` ASC LIMIT :limit OFFSET :offset")
    suspend fun getPaginated(limit: Int, offset: Int): List<QueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(queue: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(queues: List<QueueEntity>)

    @Update
    suspend fun update(queue: QueueEntity)

    @Delete
    suspend fun delete(queue: QueueEntity)

    @Query("DELETE FROM queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM queue")
    suspend fun count(): Int

    @Query("UPDATE queue SET `order` = :newOrder WHERE id = :id")
    suspend fun updateOrder(id: Long, newOrder: Int)
}
