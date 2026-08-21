package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.CastDevice
import com.tsutsen.platformplayer.core.model.CastState
import kotlinx.coroutines.flow.StateFlow

/**
 * Control surface for mirroring playback to a cast receiver (fcast / chromecast).
 *
 * The implementation lives in the app module and wraps the fcast sender SDK
 * state. [PlayerRepository] consults it while [PlayerState.isCasting] is set
 * and delegates transport controls here instead of ExoPlayer.
 */
interface CastingRepository {

    /** Connection / discovery state for the casting sheet UI. */
    val state: StateFlow<CastState>

    /** Position of the cast receiver in milliseconds (0 when not casting). */
    val currentTimeMs: StateFlow<Long>

    /** Duration reported by the cast receiver in milliseconds. */
    val durationMs: StateFlow<Long>

    /** Connect to a known device (discovered or remembered). */
    fun connect(device: CastDevice)

    /** Connect to a receiver at an explicit http://ip:port address. */
    fun connectByUrl(url: String)

    /** Disconnect the active receiver (casting stops). */
    fun disconnect()

    fun pause()

    fun resume()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float): Boolean

    /** Set to receive a one-shot signal when the casted media item ends. */
    fun setMediaEndedListener(listener: (() -> Unit)?)
}
