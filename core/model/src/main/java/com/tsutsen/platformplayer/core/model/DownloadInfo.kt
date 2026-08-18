package com.tsutsen.platformplayer.core.model

/**
 * A video download: in progress (0 < progress < 1, done = false)
 * or complete (done = true). [url] is the original video URL, which is
 * also the playback target — the player resolver serves local files
 * for downloaded videos.
 */
data class DownloadInfo(
    val url: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    val progress: Float = 0f,
    val done: Boolean = false,
)

/** Download state of one video, as seen by the UI (options sheet button). */
sealed interface DownloadButtonState {
    data object Idle : DownloadButtonState

    /** Requested, engine not yet queued (resolving video details). */
    data object Starting : DownloadButtonState

    data class Downloading(
        val progress: Float,
    ) : DownloadButtonState

    data object Downloaded : DownloadButtonState
}
