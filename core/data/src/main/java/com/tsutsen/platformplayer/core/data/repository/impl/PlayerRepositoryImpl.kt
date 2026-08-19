package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.SubtitleSource
import com.tsutsen.platformplayer.core.data.repository.VideoDetails
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.core.data.service.PlayerService
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    @ApplicationContext private val context: Context,
) : PlayerRepository {
    private var urlResolver: VideoUrlResolver? = null

    fun setUrlResolver(resolver: VideoUrlResolver?) {
        this.urlResolver = resolver
    }

    // Wired by the DI module after construction (same pattern as urlResolver).
    private var commentRepository: CommentRepository? = null
    private var contentExtrasRepository: ContentExtrasRepository? = null
    private var settingsRepository: SettingsRepository? = null

    fun setExtrasRepositories(
        commentRepository: CommentRepository,
        contentExtrasRepository: ContentExtrasRepository,
        settingsRepository: SettingsRepository,
    ) {
        this.commentRepository = commentRepository
        this.contentExtrasRepository = contentExtrasRepository
        this.settingsRepository = settingsRepository
    }

    // Generation counter guarding the extras fetches: each play() bumps it,
    // and a fetch only publishes if its generation is still current — so an
    // in-flight fetch for the previous video can never overwrite the new
    // video's extras (the "comments from the previous video" bug).
    private val playGeneration = java.util.concurrent.atomic.AtomicInteger(0)
    private val extrasScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val TAG = "PlayerRepositoryImpl"

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val exoPlayer: ExoPlayer? get() = _exoPlayer
    private var _exoPlayer: ExoPlayer? = null

    // Holds the connection to PlayerService open while playback is active.
    // The bind is what makes the service create its MediaSession — a bare
    // start never does (onGetSession is only called on connect).
    private var mediaController: MediaController? = null

    // User-selected track preferences (UI labels). Applied to the ExoPlayer
    // via [applyTrackSelectionParameters] and re-applied to each new player.
    private var selectedQuality: String = "Auto"
    private var selectedSubtitle: String = "Auto"

    // The primary source + engine-provided subtitle tracks for the video
    // currently loaded. Subtitles are applied as an extra media source
    // (SAB streams carry no text tracks of their own).
    private var currentPrimarySource: MediaSource? = null
    private var currentSubtitles: List<SubtitleSource> = emptyList()

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.i(TAG, "Playback state changed: $playbackState")
                val duration = _exoPlayer?.duration ?: 0L
                _playerState.update {
                    it.copy(
                        isLoading = playbackState == Player.STATE_BUFFERING,
                        isCompleted = playbackState == Player.STATE_ENDED,
                        durationMs = duration,
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "isPlaying changed: $isPlaying")
                _playerState.update { it.copy(isPlaying = isPlaying) }
            }

            // media3 1.x routes decoded cues here (the player's internal
            // TextOutput forwards to listeners) - no TextOutput plumbing needed.
            override fun onCues(cueGroup: CueGroup) {
                val text =
                    cueGroup.cues
                        .mapNotNull { cue -> cue.text?.toString() }
                        .joinToString("\n")
                _playerState.update {
                    if (it.subtitleText == text) it else it.copy(subtitleText = text)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Don't update position here - let the position ticker handle it
                // This avoids race conditions with seekTo() and scrubbing
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playerState.update { it.copy(playbackSpeed = playbackParameters.speed) }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val heights = mutableListOf<Int>()
                val languages = mutableListOf<String>()
                for (group in tracks.groups) {
                    when (group.mediaTrackGroup.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            for (i in 0 until group.length) {
                                val height = group.getTrackFormat(i).height
                                if (height > 0) heights.add(height)
                            }
                        }

                        C.TRACK_TYPE_TEXT -> {
                            for (i in 0 until group.length) {
                                val language = group.getTrackFormat(i).language
                                if (!language.isNullOrBlank()) languages.add(language)
                            }
                        }
                    }
                }
                val qualities = heights.distinct().sortedDescending()
                // Engine-provided subtitle tracks first (SAB streams carry
                // no text tracks), then player-reported text tracks (DASH).
                val subtitles = (currentSubtitles.map { it.name } + languages).distinct()
                _playerState.update {
                    if (it.videoQualities == qualities && it.subtitleLanguages == subtitles) {
                        it
                    } else {
                        it.copy(videoQualities = qualities, subtitleLanguages = subtitles)
                    }
                }
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

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                Log.i(TAG, "MediaItem transitioned: ${mediaItem?.mediaId}, reason: $reason")
            }

            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                Log.i(TAG, "Player events: $events")
            }
        }

    // Position ticker: single source of truth for currentPositionMs
    private val positionScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + Job())
    private var positionTickerJob: Job? = null

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob =
            positionScope.launch {
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

    private val analyticsListener =
        object : AnalyticsListener {
            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean,
            ) {
                val trackType =
                    when (mediaLoadData.trackType) {
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
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
            ) {
                Log.i(TAG, "LoadCompleted track=${mediaLoadData.trackType} bytes=${loadEventInfo.bytesLoaded}")
            }

            override fun onDroppedVideoFrames(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
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
                // A new video starts without the previous one's extras.
                comments = emptyList(),
                recommendations = emptyList(),
                chapters = emptyList(),
                currentVideo =
                    ContentItem(
                        id = videoId,
                        url = videoId,
                        title = "Loading...",
                        author = null,
                        thumbnailUrl = null,
                        contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
                    ),
            )
        }

        // Fetch the video's extras (comments / recommendations / chapters)
        // here — in the ONE place every play path goes through (main screen,
        // companion screen, notification taps, …) — instead of in
        // PlayerViewModel, which the companion's play path never touches.
        val generation = playGeneration.incrementAndGet()
        val prefs = settingsRepository?.preferences?.value
        if (prefs != null) {
            extrasScope.launch {
                if (prefs.showComments) {
                    launch(Dispatchers.IO) {
                        try {
                            val comments =
                                commentRepository?.getComments(videoId) ?: emptyList()
                            if (generation == playGeneration.get()) {
                                _playerState.update { it.copy(comments = comments) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch comments for $videoId", e)
                        }
                    }
                }
                if (prefs.showRecommendedVideos) {
                    launch(Dispatchers.IO) {
                        try {
                            val recs =
                                contentExtrasRepository?.getRecommendations(videoId)
                                    ?: emptyList()
                            if (generation == playGeneration.get()) {
                                _playerState.update { it.copy(recommendations = recs) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch recommendations for $videoId", e)
                        }
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        val chapters =
                            contentExtrasRepository?.getChapters(videoId) ?: emptyList()
                        if (generation == playGeneration.get() && chapters.isNotEmpty()) {
                            _playerState.update { it.copy(chapters = chapters) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch chapters for $videoId", e)
                    }
                }
            }
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
            val resolution =
                withContext(Dispatchers.IO) {
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
                    _exoPlayer =
                        ExoPlayer
                            .Builder(context)
                            .setHandleAudioBecomingNoisy(true)
                            .build()
                    _exoPlayer?.addListener(playerListener)
                    _exoPlayer?.addAnalyticsListener(analyticsListener)
                    // media3 1.x disables legacy text decoding by default, so
                    // the raw VTT subtitle sources we attach can't be decoded
                    // without opting back in.
                    _exoPlayer?.let { player ->
                        for (i in 0 until player.rendererCount) {
                            if (player.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                                (player.getRenderer(i) as? TextRenderer)
                                    ?.experimentalSetLegacyDecodingEnabled(true)
                            }
                        }
                    }
                    startPositionTicker()
                } else {
                    Log.i(TAG, "Using existing ExoPlayer instance")
                }

                if (resolution.mediaSource == null) {
                    Log.e(TAG, "Failed to create MediaSource, cannot play video")
                    _playerState.update { it.copy(isLoading = false, error = "Failed to resolve video source") }
                    return@withContext
                }

                // Attach title/artist/artwork so the system media notification
                // can show them (media3 builds the notification from the
                // player's MediaItem metadata).
                resolution.videoDetails?.let { details ->
                    val metadata =
                        MediaMetadata
                            .Builder()
                            .setTitle(details.title)
                            .apply { details.authorName?.let { setArtist(it) } }
                            .apply { details.thumbnailUrl?.let { setArtworkUri(Uri.parse(it)) } }
                            .build()
                    val updated =
                        resolution.mediaSource.mediaItem
                            .buildUpon()
                            .setMediaMetadata(metadata)
                            .build()
                    resolution.mediaSource.updateMediaItem(updated)
                }

                currentPrimarySource = resolution.mediaSource
                currentSubtitles = resolution.videoDetails?.subtitles ?: emptyList()
                _playerState.update {
                    it.copy(
                        subtitleLanguages = currentSubtitles.map { subtitle -> subtitle.name },
                        subtitleText = "",
                    )
                }

                val subtitleSource = buildSubtitleMediaSource()
                val mediaSource =
                    if (subtitleSource != null) {
                        MergingMediaSource(true, resolution.mediaSource, subtitleSource)
                    } else {
                        resolution.mediaSource
                    }

                Log.i(TAG, "Setting MediaSource on ExoPlayer (subtitle=$selectedSubtitle)...")
                _exoPlayer?.setMediaSource(mediaSource)
                Log.i(TAG, "Preparing ExoPlayer...")
                _exoPlayer?.prepare()
                Log.i(TAG, "Setting playWhenReady to true...")
                _exoPlayer?.playWhenReady = true
                // Re-apply the user's track preferences to a (possibly new)
                // player so quality/subtitle choices persist across videos.
                applyTrackSelectionParameters()

                // Register playback with the system: media notification,
                // lock-screen controls, media buttons (media3 session service).
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlayerService::class.java),
                )
                if (mediaController == null) {
                    val future =
                        MediaController
                            .Builder(
                                context,
                                SessionToken(
                                    context,
                                    ComponentName(context, PlayerService::class.java),
                                ),
                            ).buildAsync()
                    future.addListener(
                        {
                            try {
                                mediaController = future.get()
                            } catch (t: Throwable) {
                                Log.w(TAG, "MediaController connection failed", t)
                            }
                        },
                        ContextCompat.getMainExecutor(context),
                    )
                }

                Log.i(TAG, "Updating player state with video details...")
                Log.i(TAG, "Resolution has videoDetails: ${resolution.videoDetails != null}")
                if (resolution.videoDetails != null) {
                    Log.i(
                        TAG,
                        "VideoDetails from resolver: title=${resolution.videoDetails.title}, author=${resolution.videoDetails.authorName}",
                    )
                }
                val currentVideo =
                    resolution.videoDetails?.let { details ->
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
                            contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
                        )
                    }

                Log.i(TAG, "Current video title: ${currentVideo.title}")
                Log.i(TAG, "Current video author: ${currentVideo.author?.name}")
                Log.i(TAG, "Current video thumbnail: ${currentVideo.thumbnailUrl}")

                _playerState.update {
                    it.copy(
                        isPlaying = true,
                        currentVideo = currentVideo,
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

    private suspend fun resolveWithDetails(contentUrl: String): ResolutionResult =
        try {
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

    private fun isStreamingUrl(url: String): Boolean =
        url.contains(".mpd") || url.contains(".m3u8") || url.contains("dash") || url.contains("hls")

    /**
     * Map VideoDetails to ContentItem for display in the player UI.
     */
    private fun mapVideoDetailsToContentItem(details: VideoDetails): ContentItem =
        ContentItem(
            id = details.id,
            url = details.url,
            title = details.title,
            author =
                details.authorName?.let {
                    Author(
                        id = details.id,
                        name = it,
                        url = details.authorUrl,
                        thumbnailUrl = details.authorThumbnailUrl,
                    )
                },
            thumbnailUrl = details.thumbnailUrl,
            contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
            publishedAt = details.publishedAtMs,
            durationMs = details.durationMs,
            viewCount = details.viewCount,
            description = details.description,
            likeCount = details.likeCount,
            dislikeCount = details.dislikeCount,
        )

    private fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource
            .Factory()
            .setUserAgent("Bluejay/1.0")
            .setAllowCrossProtocolRedirects(true)

    private fun createMediaSourceFromUrl(url: String): MediaSource {
        Log.i(TAG, "createMediaSourceFromUrl() called with URL: $url")
        Log.i(TAG, "URL contains .mpd: ${url.contains(".mpd")}")
        Log.i(TAG, "URL contains .m3u8: ${url.contains(".m3u8")}")
        Log.i(TAG, "URL contains 'dash': ${url.contains("dash")}")
        Log.i(TAG, "URL contains 'hls': ${url.contains("hls")}")

        val httpDataSourceFactory = createHttpDataSourceFactory()

        return when {
            url.contains(".mpd") || url.contains("dash") -> {
                Log.i(TAG, "Creating DASH MediaSource for URL: $url")
                DashMediaSource
                    .Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }

            url.contains(".m3u8") || url.contains("hls") -> {
                Log.i(TAG, "Creating HLS MediaSource for URL: $url")
                HlsMediaSource
                    .Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }

            else -> {
                // Check if this is a progressive streaming URL that needs DASH conversion
                Log.i(TAG, "Checking if URL is progressive streaming...")
                // For now, try progressive media source
                Log.i(TAG, "Creating Progressive MediaSource for URL: $url")
                ProgressiveMediaSource
                    .Factory(httpDataSourceFactory)
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
        selectedQuality = quality
        applyTrackSelectionParameters()
        _playerState.update { it.copy(selectedQuality = quality) }
    }

    override suspend fun setSubtitle(selection: String) {
        selectedSubtitle = selection
        applyTrackSelectionParameters()
        _playerState.update { it.copy(selectedSubtitle = selection) }
        applySubtitleSource()
    }

    /**
     * Rebuilds the player's media source with (or without) the subtitle
     * source matching [selectedSubtitle], keeping the current position.
     */
    private suspend fun applySubtitleSource() {
        withContext(Dispatchers.Main) {
            val player = _exoPlayer ?: return@withContext
            val primary = currentPrimarySource ?: return@withContext
            val positionBefore = player.currentPosition
            val wasReady = player.playWhenReady
            val subtitleSource = buildSubtitleMediaSource()
            player.setMediaSource(
                if (subtitleSource != null) MergingMediaSource(true, primary, subtitleSource) else primary,
            )
            player.prepare()
            player.playWhenReady = wasReady
            if (positionBefore > 0) player.seekTo(positionBefore)
        }
    }

    /**
     * Builds a [SingleSampleMediaSource] for the currently selected
     * subtitle, or null when no subtitle is selected ("Auto"/"Off"),
     * the selection doesn't match a track of the current video, the
     * format is unsupported, or resolving the content fails.
     */
    private suspend fun buildSubtitleMediaSource(): MediaSource? {
        val subtitle = currentSubtitles.firstOrNull { it.name == selectedSubtitle } ?: return null
        val format = subtitle.format?.lowercase() ?: return null
        if (format !in SUPPORTED_SUBTITLE_FORMATS) return null
        val uri = runCatching { subtitle.contentUri() }.getOrNull() ?: return null
        return SingleSampleMediaSource
            .Factory(DefaultDataSource.Factory(context, createHttpDataSourceFactory()))
            .createMediaSource(
                MediaItem.SubtitleConfiguration
                    .Builder(uri)
                    .setMimeType(subtitle.format)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
                C.TIME_UNSET,
            )
    }

    /**
     * Rebuilds the player's track selection parameters from the user's
     * quality/subtitle preferences.
     *
     * Quality: "NNNp" caps the maximum video height (media3 picks the
     * highest track at or below it); "Auto" clears the cap.
     * Subtitles: "Off" ignores all text tracks, a language code prefers
     * that language, "Auto" leaves selection to the default heuristics.
     */
    private fun applyTrackSelectionParameters() {
        val player = _exoPlayer ?: return
        val builder = TrackSelectionParameters.Builder()
        val height = selectedQuality.removeSuffix("p").toIntOrNull()
        if (height != null && height > 0) {
            // Constrain a narrow band around the selected height so the exact
            // quality is forced. A max-only cap would leave lower tracks
            // "acceptable" and SABR's sticky ABR would keep the current (lower)
            // format instead of stepping up. "NNNp" => [NNN-10, NNN+10]; Auto => unconstrained.
            //
            // maxWidth MUST be a real large value (9999), not -1: media3's
            // isWithinMaxConstraints checks `format.width <= maxVideoWidth`, so -1 makes
            // EVERY video track fail the max constraint. Combined with the in-band high-res
            // track being ineligible, that sends media3 down the "outside min & max ->
            // prefer lower quality" path and it collapses to 144p. Grayjay uses 9999.
            builder.setMinVideoSize(0, height - 10)
            builder.setMaxVideoSize(9999, height + 10)
        }
        when (selectedSubtitle) {
            "Off" -> {
                builder.setIgnoredTextSelectionFlags(
                    C.SELECTION_FLAG_AUTOSELECT or C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT,
                )
            }

            "Auto" -> {
                Unit
            }

            else -> {
                builder.setPreferredTextLanguages(selectedSubtitle)
            }
        }
        player.setTrackSelectionParameters(builder.build())
    }

    override suspend fun toggleFullscreen() {
        _playerState.update { currentState ->
            if (currentState.isFullscreen) {
                // Exit fullscreen
                currentState.copy(isFullscreen = false, isMinimized = false)
            } else {
                // Enter fullscreen (exit mini mode if active)
                currentState.copy(isFullscreen = true, isMinimized = false)
            }
        }
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

    override fun setVideoExtras(comments: List<CommentItem>, recommendations: List<Card>) {
        _playerState.update { it.copy(comments = comments, recommendations = recommendations) }
    }

    override suspend fun close() {
        positionTickerJob?.cancel()
        positionTickerJob = null
        mediaController?.release()
        mediaController = null
        context.stopService(Intent(context, PlayerService::class.java))
        _exoPlayer?.release()
        _exoPlayer = null
        currentPrimarySource = null
        currentSubtitles = emptyList()
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
                playbackSpeed = 1f,
            )
        }
    }

    private companion object {
        // Formats ExoPlayer's text renderer can display (mirrors Grayjay).
        val SUPPORTED_SUBTITLE_FORMATS = setOf("text/vtt", "application/x-subrip")
    }
}
