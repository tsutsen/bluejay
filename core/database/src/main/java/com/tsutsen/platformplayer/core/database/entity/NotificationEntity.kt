package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An in-app notification: a new video on a subscribed channel that has
 * notifications enabled. Populated by the background subscription worker;
 * surfaced in the Notifications tab.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["subscriptionUrl", "contentUrl"], unique = true),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionUrl: String,
    val subscriptionName: String,
    val contentUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val timestamp: Long,
    val isRead: Boolean = false,
)
