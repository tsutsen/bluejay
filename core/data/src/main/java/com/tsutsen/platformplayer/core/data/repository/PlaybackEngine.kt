package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

/** Engine-neutral pixel size of the current video frame. */
data class PlaybackVideoSize(
    val width: Int,
    val height: Int,
)

/**
 * Sink for the engine's video renderer. The core port stays free of Media3
 * types; the surface host in the player feature casts [renderer] to the
 * engine type it renders (a Media3 ExoPlayer today).
 */
interface PlaybackViewHost {
    fun attachToHost(renderer: Any?)
}

/**
 * Engine-neutral playback port: gives the UI its state, controls and a way
 * to bind a video surface without depending on Media3.
 */
interface PlaybackEngine {
    val playerState: StateFlow<PlayerState>

    /** Hands the current renderer (or null after release) to [host]. */
    fun attach(host: PlaybackViewHost)

    /** Called when the underlying player instance is created/replaced; hosts should re-attach. */
    fun onPlayerReplaced() {}

    suspend fun play()

    suspend fun pause()

    suspend fun seekTo(positionMs: Long)

    suspend fun setPlaybackSpeed(speed: Float)

    fun positionMs(): Long

    fun durationMs(): Long

    fun videoSize(): PlaybackVideoSize?
}
