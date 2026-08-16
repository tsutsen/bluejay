package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity

/**
 * Save destinations for the "Save" sheet actions. A video can hold several
 * types at once (e.g. Watch Later + Favourite), hence the composite PK.
 */
enum class SavedVideoType {
    WATCH_LATER,
    LIKED,
    FAVOURITE,
}

@Entity(
    tableName = "saved_video",
    primaryKeys = ["contentUrl", "type"],
)
data class SavedVideoEntity(
    val contentUrl: String,
    val type: SavedVideoType,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
)
