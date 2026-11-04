package com.tsutsen.platformplayer.core.data.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * One frame of a storyboard sprite sheet: crop [x, y, w, h] out of the image
 * at [sheetUrl]; the frame represents [timeMs].
 */
data class SeekThumbnailFrame(
    val sheetUrl: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val timeMs: Long,
)

/**
 * Storyboard-backed frame previews for timeline scrubbing, resolved from the
 * source plugin for the currently loaded video. null = the plugin offers no
 * previews (unsupported platform or no storyboard spec in the player response).
 */
interface SeekThumbnailProvider {
    /** Interval between storyboard frames, in ms. */
    val intervalMs: Long

    suspend fun thumbnailFor(timeMs: Long): SeekThumbnailFrame?
}

/**
 * Bridge seam for exposing the currently loaded video's [SeekThumbnailProvider]
 * to the player UI. Set by the player repository on resolve, cleared on the
 * next play() call.
 */
interface SeekThumbnailsStore {
    val seekThumbnails: StateFlow<SeekThumbnailProvider?>
}
