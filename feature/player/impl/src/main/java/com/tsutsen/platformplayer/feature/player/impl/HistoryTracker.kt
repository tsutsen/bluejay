package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks video playback history and updates the database.
 */
@Singleton
class HistoryTracker
    @Inject
    constructor(
        private val historyDao: HistoryDao,
    ) {
        /**
         * Record or update video playback.
         */
        suspend fun trackPlayback(
            contentUrl: String,
            title: String,
            author: String? = null,
            authorUrl: String? = null,
            thumbnailUrl: String? = null,
            currentPositionMs: Long = 0,
            totalDurationMs: Long = 0,
        ) {
            withContext(Dispatchers.IO) {
                val existing = historyDao.getByUrl(contentUrl)
                if (existing != null) {
                    // Update existing entry (don't clobber a stored channel URL
                    // with a null from a details-less call)
                    historyDao.update(
                        existing.copy(
                            title = title,
                            author = author,
                            authorUrl = authorUrl ?: existing.authorUrl,
                            thumbnailUrl = thumbnailUrl,
                            lastPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            watchedAt = System.currentTimeMillis(),
                            viewedAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    // Create new entry
                    val entity =
                        HistoryEntity(
                            contentUrl = contentUrl,
                            title = title,
                            author = author,
                            authorUrl = authorUrl,
                            thumbnailUrl = thumbnailUrl,
                            lastPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            watchedAt = System.currentTimeMillis(),
                            viewedAt = System.currentTimeMillis(),
                        )
                    historyDao.upsert(entity)
                }
            }
        }

        /**
         * Delete a video from history.
         */
        suspend fun deleteFromHistory(contentUrl: String) {
            withContext(Dispatchers.IO) {
                historyDao.deleteByUrl(contentUrl)
            }
        }
    }
