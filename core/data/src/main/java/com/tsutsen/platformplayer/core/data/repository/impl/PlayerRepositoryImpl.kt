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
import com.tsutsen.platformplayer.core.data.repository.CastingRepository
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.data.repository.SubtitleSource
import com.tsutsen.platformplayer.core.data.repository.VideoDetails
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.core.data.service.PlayerService
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import com.tsutsen.platformplayer.core.model.VideoChapter
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
    // Present on every build that has a cast subsystem (fcast sender SDK).
    private var castingRepository: CastingRepository? = null

    fun setCastingRepository(repository: CastingRepository?) {
        this.castingRepository = repository
    }

    // Wired by the DI module after construction (same pattern as urlResolver).
    private var commentRepository: CommentRepository? = null
    private var contentExtrasRepository: ContentExtrasRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var historyDao: HistoryDao? = null

    fun setHistoryDao(dao: HistoryDao?) {
        this.historyDao = dao
    }

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

    // Per-video extras caches for the session: replaying a video (tab
    // re-entry, re-tap, notification tap, next→previous) applies the cached
    // extras instantly instead of refetching comments/recommendations/
    // chapters over the network. Bounded LRU, ~50 videos each.
    private val commentsCache = ExtrasCache<List<CommentItem>>()
    private val recommendationsCache = ExtrasCache<List<Card>>()
    private val chaptersCache = ExtrasCache<List<VideoChapter>>()

    private val TAG = "PlayerRepositoryImpl"

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val exoPlayer: ExoPlayer? get() = _exoPlayer
    private var _exoPlayer: ExoPlayer? = null
    private val _loopMode = MutableStateFlow(PlayerRepository.LOOP_OFF)
    override val loopMode: kotlinx.coroutines.flow.StateFlow<Int> = _loopMode.asStateFlow()
    private var loopOnceArmed = false

    // Holds the connection to PlayerService open while playback is active.
    // The bind is what makes the service create its MediaSession — a bare
    // start never does (onGetSession is only called on connect).
    private var mediaController: MediaController? = null

    // User-selected track preferences (UI labels). Applied to the ExoPlayer
    // via [applyTrackSelectionParameters] and re-applied to each new player.
    private var selectedQuality: String = "Auto"
    private var selectedSubtitle: String = "Off"
    /** Last concretely selected track — used by [toggleSubtitles] when re-enabling. */
    private var lastExplicitSubtitle: String? = null
    private var pendingResumePosition: Long = 0

    // Collects the cast receiver's position/duration while [PlayerState.isCasting].
    private var castTrackingJob: kotlinx.coroutines.Job? = null

    private fun startCastTracking() {
        castTrackingJob?.cancel()
        val repo = castingRepository
        if (repo == null) return
        repo.setMediaEndedListener {
            _playerState.update { it.copy(isPlaying = false, isCompleted = true) }
        }
        castTrackingJob = extrasScope.launch {
            launch {
                repo.currentTimeMs.collect { pos ->
                    if (_playerState.value.isCasting) {
                        _playerState.update { it.copy(currentPositionMs = pos) }
                    }
                }
            }
            launch {
                repo.durationMs.collect { dur ->
                    if (_playerState.value.isCasting && dur > 0) {
                        _playerState.update { it.copy(durationMs = dur) }
                    }
                }
            }
        }
    }

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
                // Apply a pending resume position once playback is ready, unless
                // it would land in the last 5% (i.e. the video was finished).
                if (playbackState == Player.STATE_READY && pendingResumePosition > 0) {
                    val pos = pendingResumePosition
                    pendingResumePosition = 0
                    if (duration <= 0 || pos < duration * 0.95) {
                        _exoPlayer?.seekTo(pos)
                    }
                }
                // LOOP_ONCE: replay the video exactly one more time, then stop.
                if (playbackState == Player.STATE_ENDED && loopOnceArmed) {
                    loopOnceArmed = false
                    _exoPlayer?.let {
                        it.seekTo(0)
                        it.playWhenReady = true
                    }
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

    override suspend fun play(videoId: String, initial: com.tsutsen.platformplayer.core.model.ContentItem?) {
        // Stop the current video IMMEDIATELY — otherwise it keeps playing
        // (audio included) until the new MediaSource is resolved and prepared,
        // which can take seconds of network I/O. prepare() below starts the
        // new one. Harmless on an idle player (e.g. cast-only sessions).
        withContext(Dispatchers.Main) { _exoPlayer?.stop() }

        // Resume: look up where this video was last watched (applied once the
        // player is READY, see onPlaybackStateChanged).
        pendingResumePosition =
            withContext(Dispatchers.IO) {
                historyDao?.getByUrl(videoId)?.lastPositionMs ?: 0L
            }

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
                    initial
                        ?: ContentItem(
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

        // A new video starts at the user's default resolution. selectedQuality
        // is a persistent field, so reset it here (the one place every play
        // path goes through) before the track selection is applied below.
        prefs?.defaultVideoResolution?.let { res ->
            selectedQuality = res
            _playerState.update { it.copy(selectedQuality = res) }
        }

        if (prefs != null) {
            extrasScope.launch {
                if (prefs.showComments) {
                    launch(Dispatchers.IO) {
                        try {
                            val comments =
                                commentsCache.get(videoId)
                                    ?: commentRepository?.getComments(videoId)
                                        ?: emptyList()
                            commentsCache.put(videoId, comments)
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
                                recommendationsCache.get(videoId)
                                    ?: contentExtrasRepository?.getRecommendations(videoId)
                                        ?: emptyList()
                            recommendationsCache.put(videoId, recs)
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
                            chaptersCache.get(videoId)
                                ?: contentExtrasRepository?.getChapters(videoId)
                                    ?: emptyList()
                        chaptersCache.put(videoId, chapters)
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
                        resolveWithDetails(videoId, pendingResumePosition)
                    } else {
                        Log.i(TAG, "Streaming URL detected, creating MediaSource from URL...")
                        ResolutionResult(createMediaSourceFromUrl(videoId), null)
                    }
                }

            // The player was closed (or superseded) while resolution was in
            // flight — abort before touching ExoPlayer, or this play would
            // resurrect the released player (recreate + prepare + start).
            if (generation != playGeneration.get()) {
                Log.i(TAG, "play() aborted: superseded during resolution ($videoId)")
                return
            }

            // Cast path: the resolver already handed this video to a receiver.
            // No local ExoPlayer work is needed — position and duration arrive
            // from the cast state flows instead of the position ticker.
            if (resolution.casted) {
                Log.i(TAG, "Video handed to the cast receiver; skipping local playback")
                _playerState.update {
                    it.copy(
                        isCasting = true,
                        castDeviceName = castingRepository?.state?.value?.activeDevice?.name,
                        isLoading = false,
                        isPlaying = true,
                        isCompleted = false,
                        error = null,
                    )
                }
                startCastTracking()
                return
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
                // Re-arm the once-replay for the new video and re-apply the
                // repeat mode (repeatMode normally persists across prepares).
                loopOnceArmed = _loopMode.value == PlayerRepository.LOOP_ONCE
                _exoPlayer?.repeatMode =
                    if (_loopMode.value == PlayerRepository.LOOP_INFINITE) {
                        Player.REPEAT_MODE_ONE
                    } else {
                        Player.REPEAT_MODE_OFF
                    }
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

    private suspend fun resolveWithDetails(contentUrl: String, resumePositionMs: Long = 0): ResolutionResult =
        try {
            Log.i(TAG, "Resolving MediaSource + details for content URL: $contentUrl")
            Log.i(TAG, "urlResolver is null: ${urlResolver == null}")
            val resolver = urlResolver
            if (resolver != null) {
                Log.i(TAG, "Calling resolver.resolve()...")
                val resolution = resolver.resolve(contentUrl, resumePositionMs)
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
        if (_playerState.value.isCasting) {
            castingRepository?.pause()
            _playerState.update { it.copy(isPlaying = false) }
            return
        }
        withContext(Dispatchers.Main) {
            _exoPlayer?.playWhenReady = false
        }
        _playerState.update { it.copy(isPlaying = false) }
    }

    override suspend fun resume() {
        if (_playerState.value.isCasting) {
            castingRepository?.resume()
            _playerState.update { it.copy(isPlaying = true) }
            return
        }
        withContext(Dispatchers.Main) {
            _exoPlayer?.playWhenReady = true
        }
        _playerState.update { it.copy(isPlaying = true) }
    }

    override suspend fun seekTo(positionMs: Long) {
        if (_playerState.value.isCasting) {
            castingRepository?.seekTo(positionMs)
            return
        }
        withContext(Dispatchers.Main) {
            _exoPlayer?.seekTo(positionMs)
        }
        // Don't update currentPositionMs here - let the position ticker handle it
        // This ensures the UI always shows the actual player position
    }

    override fun setLoopMode(mode: Int) {
        _loopMode.value = mode
        // ExoPlayer's API is thread-safe; no dispatch needed.
        _exoPlayer?.let { player ->
            if (mode == PlayerRepository.LOOP_INFINITE) {
                loopOnceArmed = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                loopOnceArmed = mode == PlayerRepository.LOOP_ONCE
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
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
        if (_playerState.value.isCasting) {
            val supported = castingRepository?.setSpeed(speed) ?: true
            if (supported) {
                _playerState.update { it.copy(playbackSpeed = speed) }
            }
            return
        }
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
        if (selection != "Off" && selection != "Auto") {
            lastExplicitSubtitle = selection
        }
        selectedSubtitle = selection
        applyTrackSelectionParameters()
        _playerState.update { it.copy(selectedSubtitle = selection) }
        applySubtitleSource()
    }

    override suspend fun toggleSubtitles() {
        val current = selectedSubtitle
        if (current != "Off" && current != "Auto") {
            setSubtitle("Off")
            return
        }
        val target = lastExplicitSubtitle ?: currentSubtitles.firstOrNull()?.name
        if (target != null) setSubtitle(target)
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
        // Invalidate any in-flight play() so it cannot resurrect the player
        // after its resolution completes (close-during-load).
        playGeneration.incrementAndGet()
        // Leaving the player stops the cast session — the receiver keeps
        // playing only while the player is on screen.
        if (_playerState.value.isCasting) {
            castingRepository?.disconnect()
        }
        castTrackingJob?.cancel()
        castTrackingJob = null
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

/**
 * Small thread-safe LRU cache keyed by video URL.
 */
private class ExtrasCache<V>(private val capacity: Int = 50) {
    private val map = LinkedHashMap<String, V>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): V? = map[key]

    @Synchronized
    fun put(key: String, value: V) {
        map[key] = value
        while (map.size > capacity) {
            val eldest = map.keys.first()
            map.remove(eldest)
        }
    }
}
