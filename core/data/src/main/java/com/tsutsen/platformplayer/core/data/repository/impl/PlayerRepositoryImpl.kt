package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlayerRepository implementation with actual ExoPlayer usage.
 * Resolves content URLs to streaming URLs via engine plugins.
 */
@Singleton
class PlayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerRepository {

    private val TAG = "PlayerRepositoryImpl"

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val exoPlayer: ExoPlayer? get() = _exoPlayer
    private var _exoPlayer: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playerState.update { it.copy(currentPositionMs = newPosition.positionMs) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playerState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }
    }

    override suspend fun play(videoId: String) {
        withContext(Dispatchers.Main) {
            try {
                Log.i(TAG, "Playing video: $videoId")

                if (_exoPlayer == null) {
                    _exoPlayer = ExoPlayer.Builder(context)
                        .setHandleAudioBecomingNoisy(true)
                        .build()
                    _exoPlayer?.addListener(playerListener)
                }

                // Create MediaSource based on URL type
                val mediaSource = createMediaSourceFromUrl(videoId)

                _exoPlayer?.setMediaSource(mediaSource)
                _exoPlayer?.prepare()
                _exoPlayer?.playWhenReady = true

                _playerState.update {
                    it.copy(
                        isPlaying = true,
                        currentVideo = ContentItem(
                            id = videoId,
                            url = videoId,
                            title = "Loading...",
                            author = null,
                            thumbnailUrl = null,
                            contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO
                        )
                    )
                }

                Log.i(TAG, "Video prepared successfully: $videoId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play video: $videoId", e)
                _playerState.update { it.copy(error = e.message ?: "Failed to play video") }
            }
        }
    }

    private fun isStreamingUrl(url: String): Boolean {
        return url.contains(".mpd") || url.contains(".m3u8") || url.contains("dash") || url.contains("hls")
    }

    private fun createMediaSourceFromUrl(url: String): MediaSource {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Bluejay/1.0")
            .setAllowCrossProtocolRedirects(true)

        return when {
            url.contains(".mpd") || url.contains("dash") -> {
                Log.i(TAG, "Creating DASH MediaSource")
                DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            url.contains(".m3u8") || url.contains("hls") -> {
                Log.i(TAG, "Creating HLS MediaSource")
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            else -> {
                Log.i(TAG, "Creating Progressive MediaSource")
                ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) {
            _exoPlayer?.playWhenReady = false
        }
        _playerState.update { it.copy(isPlaying = false) }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main) {
            _exoPlayer?.playWhenReady = true
        }
        _playerState.update { it.copy(isPlaying = true) }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.Main) {
            _exoPlayer?.seekTo(positionMs)
        }
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    override suspend fun setVolume(volume: Float) {
        withContext(Dispatchers.Main) {
            _exoPlayer?.volume = volume
        }
        _playerState.update { it.copy(volume = volume) }
    }

    override suspend fun setBrightness(brightness: Float) {
        // Brightness is controlled by the activity/window, not ExoPlayer directly
        _playerState.update { it.copy(brightness = brightness) }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        withContext(Dispatchers.Main) {
            _exoPlayer?.playbackParameters = _exoPlayer?.playbackParameters?.withSpeed(speed)
                ?: androidx.media3.common.PlaybackParameters(speed)
        }
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    override suspend fun setVideoQuality(quality: String) {
        // TODO: Implement quality selection based on available tracks
    }

    override suspend fun toggleFullscreen() {
        _playerState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    override suspend fun minimize() {
        _playerState.update { it.copy(isMinimized = true, isFullscreen = false) }
    }

    override suspend fun exitFullscreen() {
        _playerState.update { it.copy(isFullscreen = false) }
    }

    override suspend fun exitMiniPlayer() {
        _playerState.update { it.copy(isMinimized = false, isFullscreen = false) }
    }

    override suspend fun close() {
        _exoPlayer?.release()
        _exoPlayer = null
        _playerState.update {
            PlayerState(
                isPlaying = false,
                isMinimized = false,
                isFullscreen = false,
                currentVideo = null,
                currentPositionMs = 0L,
                durationMs = 0L,
                volume = 1f,
                brightness = 1f,
                playbackSpeed = 1f
            )
        }
    }
}
