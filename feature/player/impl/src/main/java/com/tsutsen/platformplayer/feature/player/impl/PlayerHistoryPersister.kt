package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How often playback history is persisted while a video plays (was: every 100ms tick). */
private const val HISTORY_SAVE_INTERVAL_MS = 15_000L

/**
 * Throttled playback-history persistence for the player.
 * Extracted from [PlayerViewModel]; the ViewModel's main state collector calls
 * [sample] on every repository tick and [persist] directly on video change,
 * pause and completion.
 */
internal class PlayerHistoryPersister(
    private val historyTracker: HistoryTracker,
    private val libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
) {
    /** Throttled history persistence (see [persist]). */
    @Volatile
    private var lastHistorySaveAtMs = 0L

    @Volatile
    private var lastHistorySavedUrl: String? = null
    private var lastSampledVideo: ContentItem? = null
    private var lastSampledPositionMs: Long = 0
    private var lastSampledDurationMs: Long = 0

    /**
     * Feed one repository tick into the sampler and persist on the throttle.
     * On a video switch, flushes the PREVIOUS video with its own last
     * position and skips sampling for this tick (the switch tick still
     * carries the old media's position/duration).
     */
    fun sample(
        video: ContentItem,
        positionMs: Long,
        durationMs: Long,
        force: Boolean,
    ) {
        // On a video switch, flush the PREVIOUS video with its own
        // last position — a tick landing after currentVideo flipped
        // but before the position reset would otherwise write the old
        // position under the new video's url (new video shows as
        // watched/progressed). The new video starts sampling next tick.
        if (lastSampledVideo != null && lastSampledVideo!!.url != video.url) {
            persist(
                video = lastSampledVideo!!,
                positionMs = lastSampledPositionMs,
                durationMs = lastSampledDurationMs,
                force = true,
            )
            // The tick that flipped currentVideo still
            // carries the OLD media's position/duration —
            // sampling the new video with them wrote the
            // previous video's progress onto the tapped
            // card. Skip this tick; the new video starts
            // sampling on the next one.
            lastSampledVideo = video
            lastSampledPositionMs = 0
            lastSampledDurationMs = 0
            return
        }
        lastSampledVideo = video
        lastSampledPositionMs = positionMs
        lastSampledDurationMs = durationMs
        persist(
            video = video,
            positionMs = positionMs,
            durationMs = durationMs,
            force = force,
        )
    }

    /**
     * Persist playback history on a throttle instead of on every state tick.
     * The old per-tick write (10×/s) hammered Room and — via the live query
     * behind the watch states — re-emitted to every screen that watches history.
     * Saves now happen every [HISTORY_SAVE_INTERVAL_MS] while playing, plus on
     * video change, pause and completion.
     */
    private fun persist(
        video: ContentItem,
        positionMs: Long,
        durationMs: Long,
        force: Boolean,
    ) {
        val now = System.currentTimeMillis()
        if (
            !force &&
            video.url == lastHistorySavedUrl &&
            now - lastHistorySaveAtMs < HISTORY_SAVE_INTERVAL_MS
        ) {
            return
        }
        lastHistorySaveAtMs = now
        lastHistorySavedUrl = video.url
        scope.launch {
            historyTracker.trackPlayback(
                contentUrl = video.url,
                title = video.title,
                author = video.author?.name,
                authorUrl = video.author?.url?.takeIf { it.isNotEmpty() },
                thumbnailUrl = video.thumbnailUrl,
                currentPositionMs = positionMs,
                totalDurationMs = durationMs,
                viewCount = video.viewCount,
                postedAt = video.publishedAt,
            )
            if (durationMs > 0) {
                libraryRepository.backfillDuration(video.url, durationMs)
            }
        }
    }
}
