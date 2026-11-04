package com.tsutsen.platformplayer.core.data.repository

import androidx.media3.exoplayer.source.MediaSource

/**
 * Result of resolving a content URL — contains both the MediaSource for playback
 * and the VideoDetails for populating the video details page (title, author, etc.).
 */
data class ResolutionResult(
    val mediaSource: MediaSource?,
    val videoDetails: VideoDetails?,
    val seekThumbnails: SeekThumbnailProvider? = null,
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

    /**
     * Ask the source layer to drop cached per-video/session state so the next
     * [resolve] produces a genuinely fresh source (new attestation token).
     * Called by the player's source-error recovery before re-resolving — a
     * plain re-resolve can return the same rejected token from a cache.
     * Default: no-op (resolvers without caches need nothing).
     */
    suspend fun invalidateSourceCache(contentUrl: String) {}
}
