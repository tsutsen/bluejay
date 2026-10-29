package com.tsutsen.platformplayer.core.database.dao

import androidx.room.*
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE contentUrl = :url")
    suspend fun getByUrl(url: String): HistoryEntity?

    /** Batch lookup for feed filtering (one query for a whole page of urls). */
    @Query("SELECT * FROM history WHERE contentUrl IN (:urls)")
    suspend fun getByUrls(urls: List<String>): List<HistoryEntity>

    /** Total watched time per channel url (subscriptions "watch time" ordering). */
    @Query("SELECT authorUrl AS authorUrl, SUM(watchedMs) AS watchedMs FROM history WHERE authorUrl IS NOT NULL GROUP BY authorUrl")
    suspend fun watchedTimeByAuthor(): List<AuthorWatchTime>

    /**
     * Top creators by watched time within a recent window (homebrew
     * recommendations source 3): authors with more than [minWatchedMs]
     * accumulated since [sinceMs], most watched first.
     */
    @Query("SELECT authorUrl AS authorUrl, SUM(watchedMs) AS watchedMs FROM history WHERE authorUrl IS NOT NULL AND watchedAt > :sinceMs GROUP BY authorUrl HAVING SUM(watchedMs) > :minWatchedMs ORDER BY watchedMs DESC LIMIT :limit")
    suspend fun topCreatorsByWatchTimeSince(sinceMs: Long, minWatchedMs: Long, limit: Int): List<AuthorWatchTime>

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginated(limit: Int, offset: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE watchedAt > :millis ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getSince(millis: Long, limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE lastPositionMs > 0 ORDER BY watchedAt DESC")
    fun observeContinueWatching(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: HistoryEntity)

    @Update
    suspend fun update(history: HistoryEntity)

    @Delete
    suspend fun delete(history: HistoryEntity)

    /**
     * Mark [url] as fully watched (long-press sheet "Watched" tile):
     * lastPositionMs and watchedMs become [durationMs]. Existing rows keep
     * their other fields (viewedAt, authorUrl, viewCount, postedAt) — a
     * plain REPLACE upsert would reset them.
     */
    // viewedAt/viewCount/postedAt have no table defaults (NOT NULL, set by
    // the entity constructor), so a raw INSERT must name them; on conflict
    // only the watched fields change.
    @Query(
        "INSERT INTO history (contentUrl, title, author, thumbnailUrl, lastPositionMs, totalDurationMs, watchedMs, watchedAt, viewedAt, viewCount, postedAt) " +
            "VALUES (:url, :title, :author, :thumbnailUrl, :durationMs, :durationMs, :durationMs, :watchedAt, :watchedAt, 0, 0) " +
            "ON CONFLICT (contentUrl) DO UPDATE SET lastPositionMs = excluded.lastPositionMs, " +
            "totalDurationMs = excluded.totalDurationMs, watchedMs = excluded.watchedMs, watchedAt = excluded.watchedAt",
    )
    suspend fun markWatched(
        url: String,
        title: String,
        author: String? = null,
        thumbnailUrl: String? = null,
        durationMs: Long,
        watchedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM history WHERE contentUrl = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

/** Room POJO for [HistoryDao.watchedTimeByAuthor]. */
data class AuthorWatchTime(
    val authorUrl: String,
    val watchedMs: Long,
)
