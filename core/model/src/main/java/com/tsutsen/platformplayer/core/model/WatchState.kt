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

        /** Recent history entries scanned for a continue-watching pick. */
        const val CONTINUE_POOL_SIZE = 5

        /**
         * True once playback is at/over [WATCHED_FRACTION] of [totalDurationMs].
         * Entries with no meaningful progress or an unknown duration never
         * count as finished.
         */
        fun finished(lastPositionMs: Long, totalDurationMs: Long): Boolean =
            lastPositionMs > 0 && totalDurationMs > 0 &&
                lastPositionMs >= totalDurationMs * WATCHED_FRACTION
    }
}
