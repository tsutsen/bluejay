package com.futo.platformplayer.core.database.entity

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
    val cachedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 3600000 // 1 hour
)
