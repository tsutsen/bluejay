package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.data.repository.VideoDetails
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * PlayerRepository implementation with actual ExoPlayer usage.
 * Resolves content URLs to streaming URLs via engine plugins.
 */
@Singleton
class PlayerRepositoryImpl(
    @ApplicationContext private val context: Context
) : PlayerRepository {

    private var urlResolver: VideoUrlResolver? = null

    fun setUrlResolver(resolver: VideoUrlResolver?) {
        this.urlResolver = resolver
    }

    private val TAG = "PlayerRepositoryImpl"

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val exoPlayer: ExoPlayer? get() = _exoPlayer
    private var _exoPlayer: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "Playback state changed: $playbackState")
            val duration = _exoPlayer?.duration ?: 0L
            _playerState.update { it.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isCompleted = playbackState == Player.STATE_ENDED,
                durationMs = duration
            ) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "isPlaying changed: $isPlaying")
            _playerState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Don't update position here - let the position ticker handle it
            // This avoids race conditions with seekTo() and scrubbing
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playerState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player error: ${error.errorCodeName}, message: ${error.message}", error)
            _playerState.update { it.copy(error = error.message ?: "Unknown error") }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            Log.i(TAG, "Loading changed: $isLoading")
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            Log.i(TAG, "Playback suppression reason changed: $playbackSuppressionReason")
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            Log.i(TAG, "isLoading changed: $isLoading")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.i(TAG, "MediaItem transitioned: ${mediaItem?.mediaId}, reason: $reason")
        }

        override fun onEvents(player: Player, events: Player.Events) {
            Log.i(TAG, "Player events: $events")
        }
    }

    // Position ticker: single source of truth for currentPositionMs
    private val positionScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + Job())
    private var positionTickerJob: Job? = null

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = positionScope.launch {
            while (true) {
                val player = _exoPlayer
                if (player != null) {
                    val position = player.currentPosition
                    _playerState.update { it.copy(currentPositionMs = position) }
                }
                kotlinx.coroutines.delay(100) // 10fps for smooth UI
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
            error: java.io.IOException,
            wasCanceled: Boolean
        ) {
            val trackType = when (mediaLoadData.trackType) {
                C.TRACK_TYPE_VIDEO -> "VIDEO"
                C.TRACK_TYPE_AUDIO -> "AUDIO"
                C.TRACK_TYPE_TEXT -> "TEXT"
                C.TRACK_TYPE_METADATA -> "METADATA"
                C.TRACK_TYPE_UNKNOWN -> "UNKNOWN"
                else -> "OTHER"
            }
            Log.e(TAG, "LoadError track=$trackType uri=${loadEventInfo.uri} wasCanceled=$wasCanceled", error)
        }

        override fun onLoadCompleted(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
        ) {
            Log.i(TAG, "LoadCompleted track=${mediaLoadData.trackType} bytes=${loadEventInfo.bytesLoaded}")
        }

        override fun onDroppedVideoFrames(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            Log.w(TAG, "Dropped $droppedFrames video frames")
        }
    }

    override suspend fun play(videoId: String) {
        // Publish loading state IMMEDIATELY, before any network/resolve/ExoPlayer work.
        // This is what lets the UI show a spinner right away instead of a blank gap.
        // Also set currentVideo to a placeholder so PlayerScreen gets composed immediately.
        _playerState.update {
            it.copy(
                isLoading = true,
                error = null,
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

        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "play() called with videoId: $videoId")
            Log.i(TAG, "videoId length: ${videoId.length}")
            Log.i(TAG, "Is streaming URL: ${isStreamingUrl(videoId)}")
            Log.i(TAG, "Is empty: ${videoId.isEmpty()}")
            Log.i(TAG, "========================================")

            // Resolve content URL to MediaSource + video details OFF the main thread.
            // resolveWithDetails() ultimately calls into VideoUrlResolver, which may do
            // network I/O — running that under Dispatchers.Main would freeze the whole
            // UI thread (no frames, no spinner) for the entire resolve, which is exactly
            // what was causing the "tap -> nothing -> screen already loaded" gap.
            val resolution = withContext(Dispatchers.IO) {
                if (!isStreamingUrl(videoId)) {
                    Log.i(TAG, "Content URL detected, resolving to MediaSource + details...")
                    resolveWithDetails(videoId)
                } else {
                    Log.i(TAG, "Streaming URL detected, creating MediaSource from URL...")
                    ResolutionResult(createMediaSourceFromUrl(videoId), null)
                }
            }

            Log.i(TAG, "MediaSource to use: ${resolution.mediaSource?.javaClass?.simpleName}")

            // Only the ExoPlayer instance itself must be touched from the main thread.
            withContext(Dispatchers.Main) {
                if (_exoPlayer == null) {
                    Log.i(TAG, "Creating new ExoPlayer instance")
                    _exoPlayer = ExoPlayer.Builder(context)
                        .setHandleAudioBecomingNoisy(true)
                        .build()
                    _exoPlayer?.addListener(playerListener)
                    _exoPlayer?.addAnalyticsListener(analyticsListener)
                    startPositionTicker()
                } else {
                    Log.i(TAG, "Using existing ExoPlayer instance")
                }

                if (resolution.mediaSource == null) {
                    Log.e(TAG, "Failed to create MediaSource, cannot play video")
                    _playerState.update { it.copy(isLoading = false, error = "Failed to resolve video source") }
                    return@withContext
                }

                Log.i(TAG, "Setting MediaSource on ExoPlayer...")
                _exoPlayer?.setMediaSource(resolution.mediaSource)
                Log.i(TAG, "Preparing ExoPlayer...")
                _exoPlayer?.prepare()
                Log.i(TAG, "Setting playWhenReady to true...")
                _exoPlayer?.playWhenReady = true

                Log.i(TAG, "Updating player state with video details...")
                Log.i(TAG, "Resolution has videoDetails: ${resolution.videoDetails != null}")
                if (resolution.videoDetails != null) {
                    Log.i(TAG, "VideoDetails from resolver: title=${resolution.videoDetails.title}, author=${resolution.videoDetails.authorName}")
                }
                val currentVideo = resolution.videoDetails?.let { details ->
                    Log.i(TAG, "Mapping video details to ContentItem...")
                    mapVideoDetailsToContentItem(details)
                } ?: run {
                    Log.w(TAG, "No video details, using stub ContentItem")
                    ContentItem(
                        id = videoId,
                        url = videoId,
                        title = "Loading...",
                        author = null,
                        thumbnailUrl = null,
                        contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO
                    )
                }

                Log.i(TAG, "Current video title: ${currentVideo.title}")
                Log.i(TAG, "Current video author: ${currentVideo.author?.name}")
                Log.i(TAG, "Current video thumbnail: ${currentVideo.thumbnailUrl}")

                _playerState.update {
                    it.copy(
                        isPlaying = true,
                        currentVideo = currentVideo
                    )
                }

                Log.i(TAG, "========================================")
                Log.i(TAG, "play() completed successfully")
                Log.i(TAG, "========================================")
            }
        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "Failed to play video: $videoId", e)
            Log.e(TAG, "========================================")
            _playerState.update { it.copy(isLoading = false, error = e.message ?: "Failed to play video") }
        }
    }

    private suspend fun resolveWithDetails(contentUrl: String): ResolutionResult {
        return try {
            Log.i(TAG, "Resolving MediaSource + details for content URL: $contentUrl")
            Log.i(TAG, "urlResolver is null: ${urlResolver == null}")
            val resolver = urlResolver
            if (resolver != null) {
                Log.i(TAG, "Calling resolver.resolve()...")
                val resolution = resolver.resolve(contentUrl)
                Log.i(TAG, "Resolved MediaSource: ${resolution.mediaSource?.javaClass?.simpleName}")
                Log.i(TAG, "Video details available: ${resolution.videoDetails != null}")
                if (resolution.videoDetails != null) {
                    Log.i(TAG, "VideoDetails title: ${resolution.videoDetails.title}")
                }
                resolution
            } else {
                Log.w(TAG, "WARNING: VideoUrlResolver not provided, creating MediaSource from URL")
                ResolutionResult(createMediaSourceFromUrl(contentUrl), null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve, creating from URL: $contentUrl", e)
            ResolutionResult(createMediaSourceFromUrl(contentUrl), null)
        }
    }

    private fun isStreamingUrl(url: String): Boolean {
        return url.contains(".mpd") || url.contains(".m3u8") || url.contains("dash") || url.contains("hls")
    }

    /**
     * Map VideoDetails to ContentItem for display in the player UI.
     */
    private fun mapVideoDetailsToContentItem(details: VideoDetails): ContentItem {
        return ContentItem(
            id = details.id,
            url = details.url,
            title = details.title,
            author = details.authorName?.let {
                Author(
                    id = details.id,
                    name = it,
                    url = details.authorUrl,
                    thumbnailUrl = details.authorThumbnailUrl
                )
            },
            thumbnailUrl = details.thumbnailUrl,
            contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
            publishedAt = details.publishedAtMs,
            durationMs = details.durationMs,
            viewCount = details.viewCount,
            description = details.description,
            likeCount = details.likeCount,
            dislikeCount = details.dislikeCount
        )
    }

    private fun createMediaSourceFromUrl(url: String): MediaSource {
        Log.i(TAG, "createMediaSourceFromUrl() called with URL: $url")
        Log.i(TAG, "URL contains .mpd: ${url.contains(".mpd")}")
        Log.i(TAG, "URL contains .m3u8: ${url.contains(".m3u8")}")
        Log.i(TAG, "URL contains 'dash': ${url.contains("dash")}")
        Log.i(TAG, "URL contains 'hls': ${url.contains("hls")}")

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Bluejay/1.0")
            .setAllowCrossProtocolRedirects(true)

        return when {
            url.contains(".mpd") || url.contains("dash") -> {
                Log.i(TAG, "Creating DASH MediaSource for URL: $url")
                DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            url.contains(".m3u8") || url.contains("hls") -> {
                Log.i(TAG, "Creating HLS MediaSource for URL: $url")
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            else -> {
                // Check if this is a progressive streaming URL that needs DASH conversion
                Log.i(TAG, "Checking if URL is progressive streaming...")
                // For now, try progressive media source
                Log.i(TAG, "Creating Progressive MediaSource for URL: $url")
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
        // Don't update currentPositionMs here - let the position ticker handle it
        // This ensures the UI always shows the actual player position
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
        positionTickerJob?.cancel()
        positionTickerJob = null
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
