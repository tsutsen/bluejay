package com.tsutsen.platformplayer.core.data.repository

import androidx.media3.exoplayer.source.MediaSource

/**
 * Result of resolving a content URL — contains both the MediaSource for playback
 * and the VideoDetails for populating the video details page (title, author, etc.).
 */
data class ResolutionResult(
    val mediaSource: MediaSource?,
    val videoDetails: VideoDetails?,
)

/**
 * Interface for resolving content URLs to MediaSources.
 * Implementation depends on the engine plugin being available.
 * Returns both a MediaSource for playback and IPlatformVideoDetails for the UI.
 */
interface VideoUrlResolver {
    /**
     * Resolve [contentUrl] to a playable [ResolutionResult].
     *
     * [resumePositionMs] is the position this video was last watched at; it
     * is used as the playback start position.
     */
    suspend fun resolve(contentUrl: String, resumePositionMs: Long = 0): ResolutionResult
}
