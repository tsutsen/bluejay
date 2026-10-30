package com.tsutsen.platformplayer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tsutsen.platformplayer.core.model.WatchState

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey
    val contentUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val lastPositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    /**
     * Actually watched time accumulated between position saves
     * (skip-aware: seeking contributes ~0). 0 on rows that predate it —
     * stats fall back to [lastPositionMs] for those.
     */
    val watchedMs: Long = 0,
    val watchedAt: Long = System.currentTimeMillis(),
    val viewedAt: Long = System.currentTimeMillis(),
    val authorUrl: String? = null,
    val viewCount: Long = 0,
    /** The video's real posted date, captured at watch time (0 = unknown). */
    val postedAt: Long = 0,
)

/**
 * True when the row counts as fully watched (position at/over
 * [WatchState.WATCHED_FRACTION] of the duration) — the options sheet's
 * "Watched" tile state. [HistoryDao.markWatched] sets lastPositionMs to
 * the full duration, so it always satisfies this.
 */
fun HistoryEntity.isFullyWatched(): Boolean =
    lastPositionMs > 0 &&
        totalDurationMs > 0 &&
        lastPositionMs >= totalDurationMs * WatchState.WATCHED_FRACTION
