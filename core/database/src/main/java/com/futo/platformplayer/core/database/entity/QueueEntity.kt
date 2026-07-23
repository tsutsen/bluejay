package com.futo.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val positionMs: Long = 0,
    val order: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
