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
    indices =
        [
            Index(value = ["playlistId"]),
            // A video appears at most once per playlist. Enforced in the
            // DB, not just in repository code: the UI used to allow adding
            // the same video twice, and duplicate rows crashed the
            // playlist grid (duplicate lazy keys).
            Index(value = ["playlistId", "contentUrl"], unique = true),
        ]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val videoOrder: Int,
    val contentUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
)
