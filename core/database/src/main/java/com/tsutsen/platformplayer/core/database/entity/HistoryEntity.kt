package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey
    val contentUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val lastPositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    val watchedAt: Long = System.currentTimeMillis(),
    val viewedAt: Long = System.currentTimeMillis(),
    val authorUrl: String? = null,
)
