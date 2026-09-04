package com.tsutsen.platformplayer.core.model

/**
 * Aggregated watch state for a single video, derived from local history.
 *
 * @param progress Playback position as a 0..1 fraction of the total duration.
 * @param isWatched True once [progress] passes the watched threshold.
 */
data class WatchState(
    val progress: Float,
    val isWatched: Boolean,
) {
    companion object {
        /**
         * A video counts as watched once playback passes this fraction of
         * its duration. The single definition of the rule: drives the
         * checkmark on video cards and playlist cleanups like
         * "remove watched".
         */
        const val WATCHED_FRACTION = 0.95f
    }
}
