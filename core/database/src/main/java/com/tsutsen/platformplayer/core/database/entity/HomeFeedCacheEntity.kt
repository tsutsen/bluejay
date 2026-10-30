package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_feed_cache")
data class HomeFeedCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val contentUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val contentType: String,
    /** Video stats (null for non-video cards / pre-v11 rows) so cached cards paint duration/views/posted date. */
    val durationMs: Long? = null,
    val viewCount: Long? = null,
    val publishedAt: Long? = null,
    val cachedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 3600000 // 1 hour
)
