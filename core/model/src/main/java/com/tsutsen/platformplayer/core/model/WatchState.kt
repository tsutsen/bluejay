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
         * The single watched/started split for feed filters (home + subs used
         * to hand-roll the 0.95 formula twice). watched = >= 95% of duration
         * by position or accumulated watched time; started = begun, not
         * finished. Null = fresh (no meaningful progress).
         */
        fun of(
            positionMs: Long,
            watchedMs: Long,
            durationMs: Long,
        ): WatchState? {
            if (durationMs <= 0) return null
            val watched =
                positionMs >= 0.95 * durationMs || watchedMs >= 0.95 * durationMs
            return when {
                watched -> WatchState(1f, isWatched = true)
                positionMs > 0 -> WatchState((positionMs.toFloat() / durationMs).coerceIn(0f, 1f), isWatched = false)
                else -> null
            }
        }

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
