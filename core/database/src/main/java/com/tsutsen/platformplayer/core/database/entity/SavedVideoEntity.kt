package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import com.tsutsen.platformplayer.core.model.SavedVideoType

@Entity(
    tableName = "saved_video",
    primaryKeys = ["contentUrl", "type"],
)
data class SavedVideoEntity(
    val contentUrl: String,
    val type: SavedVideoType,
    val title: String,
    val author: String?,
    val authorUrl: String? = null,
    val thumbnailUrl: String?,
    val durationMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
