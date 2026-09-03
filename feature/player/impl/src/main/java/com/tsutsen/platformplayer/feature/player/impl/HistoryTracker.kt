package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Max wall-clock gap between position samples that still counts as
 * continuous playback. The player saves every 5s while playing, plus on
 * pause/completion — a longer gap means pause, background or a session
 * break, so the position jump across it (resume, seek after a pause) is
 * not credited as watched time.
 */
private const val CONTINUOUS_PLAYBACK_GAP_MS = 30_000L

/**
 * Tracks video playback history and updates the database.
 */
@Singleton
class HistoryTracker
    @Inject
    constructor(
        private val historyDao: HistoryDao,
    ) {
        private data class PositionSample(val positionMs: Long, val atMs: Long)

        /**
         * Last position sample per video, used to accumulate actually
         * watched time between saves. In-memory only: after a process
         * restart the first sample simply re-anchors without crediting.
         */
        private val positionSamples = ConcurrentHashMap<String, PositionSample>()
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
            viewCount: Long? = null,
        ) {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                // Skip-aware watch time: credit the media advanced since
                // the last sample, capped by the wall-clock elapsed since
                // it. A skip/seek jumps the position in a single sample
                // with no time behind it, so it contributes only the (tiny)
                // elapsed time; rewinds and long pauses contribute nothing.
                var creditedMs = 0L
                if (currentPositionMs > 0L) {
                    positionSamples[contentUrl]?.let { prev ->
                        val elapsedMs = now - prev.atMs
                        if (elapsedMs in 1L..CONTINUOUS_PLAYBACK_GAP_MS) {
                            creditedMs =
                                minOf(currentPositionMs - prev.positionMs, elapsedMs)
                                    .coerceAtLeast(0L)
                        }
                    }
                    positionSamples[contentUrl] = PositionSample(currentPositionMs, now)
                }
                val existing = historyDao.getByUrl(contentUrl)
                if (existing != null) {
                    // A position-less call (play start) refreshes metadata only —
                    // never clobber the stored resume position, which the
                    // repository's resume lookup and the card progress bars read.
                    val hasPosition = currentPositionMs > 0L || totalDurationMs > 0L
                    // Update existing entry (don't clobber a stored channel URL
                    // with a null from a details-less call)
                    historyDao.update(
                        existing.copy(
                            title = title,
                            author = author,
                            authorUrl = authorUrl ?: existing.authorUrl,
                            thumbnailUrl = thumbnailUrl,
                            lastPositionMs = if (hasPosition) currentPositionMs else existing.lastPositionMs,
                            totalDurationMs = if (hasPosition) totalDurationMs else existing.totalDurationMs,
                            watchedMs = existing.watchedMs + creditedMs,
                            watchedAt = now,
                            viewedAt = now,
                            viewCount = viewCount ?: existing.viewCount,
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
                            watchedMs = creditedMs,
                            watchedAt = now,
                            viewedAt = now,
                            viewCount = viewCount ?: 0L,
                        )
                    historyDao.upsert(entity)
                }
            }
        }

        /**
         * Observe all history entries (live, updates as playback is tracked).
         */
        fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeAll()

        /**
         * Delete a video from history.
         */
        suspend fun deleteFromHistory(contentUrl: String) {
            withContext(Dispatchers.IO) {
                historyDao.deleteByUrl(contentUrl)
            }
        }
    }
