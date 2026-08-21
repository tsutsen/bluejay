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
)
