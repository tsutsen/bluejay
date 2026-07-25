package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long? = null,
    val subscribedAt: Long = System.currentTimeMillis()
)
