package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoOrder"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val videoOrder: Int,
    val contentUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
)
