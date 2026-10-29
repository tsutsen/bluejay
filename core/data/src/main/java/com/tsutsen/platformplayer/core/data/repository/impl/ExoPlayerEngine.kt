package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.repository.PlaybackEngine
import com.tsutsen.platformplayer.core.data.repository.PlaybackVideoSize
import com.tsutsen.platformplayer.core.data.repository.PlaybackViewHost
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

/** [PlaybackEngine] adapter over [PlayerRepositoryImpl] (Media3 stays in core/data). */
internal class ExoPlayerEngine(
    private val repository: PlayerRepositoryImpl,
) : PlaybackEngine {
    override val playerState: StateFlow<PlayerState> get() = repository.playerState

    private var host: PlaybackViewHost? = null

    override fun attach(host: PlaybackViewHost) {
        this.host = host
        host.attachToHost(repository.exoPlayer)
    }

    override fun onPlayerReplaced() {
        host?.attachToHost(repository.exoPlayer)
    }

    override suspend fun play() = repository.resume()

    override suspend fun pause() = repository.pause()

    override suspend fun seekTo(positionMs: Long) = repository.seekTo(positionMs)

    override suspend fun setPlaybackSpeed(speed: Float) = repository.setPlaybackSpeed(speed)

    override fun positionMs(): Long = repository.positionMs

    override fun durationMs(): Long = repository.durationMs

    override fun videoSize(): PlaybackVideoSize? = repository.videoSize
}
