package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.data.repository.PlaybackEngine
import com.tsutsen.platformplayer.core.data.repository.PlaybackVideoSize
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.SubtitleSource
import com.tsutsen.platformplayer.core.data.repository.VideoAudioTrack
import com.tsutsen.platformplayer.core.data.repository.VideoStreamOption
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.core.data.service.PlayerService
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import com.tsutsen.platformplayer.core.model.VideoChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * PlayerRepository implementation with actual ExoPlayer usage.
 * Resolves content URLs to streaming URLs via engine plugins.
 *
 * Orchestrator over focused collaborators: [PlaybackSourceResolver]
 * (source construction + resolve), [SideContentCache] (per-video extras
 * caches), [PlaybackPositionTracker] (position ticker),
 * [SourceRecoveryManager] (SABR source-error recovery) and
 * [PlayerEventListener]/[LoadAnalyticsListener] (player event mirroring).
 */
@Singleton
class PlayerRepositoryImpl(
    @ApplicationContext private val context: Context,
    appScope: CoroutineScope,
) : PlayerRepository {
    private var urlResolver: VideoUrlResolver? = null

    fun setUrlResolver(resolver: VideoUrlResolver?) {
        this.urlResolver = resolver
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
    private val playGeneration =
        java.util.concurrent.atomic
            .AtomicInteger(0)
    // Extras fetches run on the injected app-lifetime scope instead of a
    // detached module-level scope: same lifetime in practice (the app scope is
    // never cancelled), but the work is structured under one supervisor.
    // close() must not cancel this shared scope — it only invalidates
    // in-flight fetches via playGeneration.
    private val extrasScope = appScope

    private val TAG = "PlayerRepositoryImpl"

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // User-selected track preferences (UI labels) and their application to
    // the ExoPlayer; re-applied to each new player.
    private val trackSelection = TrackSelectionController(_playerState)

    // Per-video extras caches for the session: replaying a video (tab
    // re-entry, re-tap, notification tap, next→previous) applies the cached
    // extras instantly instead of refetching comments/recommendations/
    // chapters over the network. Bounded LRU, ~50 videos each.
    private val commentsCache =
        SideContentCache<List<CommentItem>>(
            logTag = TAG,
            what = "comments",
            playGeneration = playGeneration,
            state = _playerState,
        )
    private val recommendationsCache =
        SideContentCache<List<Card>>(
            logTag = TAG,
            what = "recommendations",
            playGeneration = playGeneration,
            state = _playerState,
        )
    private val chaptersCache =
        SideContentCache<List<VideoChapter>>(
            logTag = TAG,
            what = "chapters",
            playGeneration = playGeneration,
            state = _playerState,
        )

    /** Media3 handle for same-module consumers (PlayerService); use [engine] elsewhere. */
    internal val exoPlayer: ExoPlayer? get() = _exoPlayer
    // The live player instance is owned by [sessionManager]; this getter
    // keeps the call sites unchanged.
    private val _exoPlayer: ExoPlayer? get() = sessionManager.player
    override val positionMs: Long get() = _exoPlayer?.currentPosition ?: 0L
    override val durationMs: Long get() = _exoPlayer?.duration ?: 0L
    override val videoSize: PlaybackVideoSize?
        get() = _exoPlayer?.videoSize?.let { PlaybackVideoSize(it.width, it.height) }
    override val engine: PlaybackEngine = ExoPlayerEngine(this)
    private val _loopMode = MutableStateFlow(PlayerRepository.LOOP_OFF)
    override val loopMode: kotlinx.coroutines.flow.StateFlow<Int> = _loopMode.asStateFlow()
    private var loopOnceArmed = false

    private var pendingResumePosition: Long = 0

    // The primary source + engine-provided subtitle tracks for the video
    // currently loaded. Subtitles are applied as an extra media source
    // (SAB streams carry no text tracks of their own).
    private var currentPrimarySource: MediaSource? = null
    private var currentSubtitles: List<SubtitleSource> = emptyList()

    // Unmuxed (separate video + audio URL) streams: the selectable audio
    // tracks come from the resolver, not ExoPlayer's track groups (the
    // merged progressive source exposes only one group). Swapping a track
    // rebuilds the merged media source around the chosen audio URL.
    private var currentAudioTracks: List<VideoAudioTrack> = emptyList()
    private var currentVideoStreams: List<VideoStreamOption> = emptyList()
    private var currentVideoStreamUrl: String? = null

    /** Pre-merged subtitle sources of the current video (built by [sourceResolver]). */
    private var currentSubtitleSource: MediaSource? = null

    // The URL of the video currently loaded (set by play(), the one place
    // every play path goes through). Recovery re-resolves this.
    private var currentPlayUrl: String? = null

    private val _seekThumbnails =
        MutableStateFlow<com.tsutsen.platformplayer.core.data.repository.SeekThumbnailProvider?>(null)
    override val seekThumbnails: kotlinx.coroutines.flow.StateFlow<com.tsutsen.platformplayer.core.data.repository.SeekThumbnailProvider?> =
        _seekThumbnails.asStateFlow()

    // Media source construction / content-URL resolution (extracted).
    private val sourceResolver =
        PlaybackSourceResolver(context, urlResolverProvider = { urlResolver }, logTag = TAG)

    // Position ticker (extracted): see [PlaybackPositionTracker].
    private val positionTracker =
        PlaybackPositionTracker(
            appScope = appScope,
            playerProvider = { _exoPlayer },
            onPosition = { position -> _playerState.update { it.copy(currentPositionMs = position) } },
        )

    // Transparent source-error recovery (SABR po-token rejection / server
    // reload) — budgeted per play() (extracted).
    private val sourceRecoveryManager =
        SourceRecoveryManager(
            logTag = TAG,
            scope = extrasScope,
            state = _playerState,
            playerProvider = { _exoPlayer },
            currentPlayUrl = { currentPlayUrl },
            resolve = { url, positionMs -> sourceResolver.resolveWithDetails(url, positionMs) },
            invalidateSourceCache = { url -> urlResolver?.invalidateSourceCache(url) },
            onSourceRebuilt = { mediaSource -> currentPrimarySource = mediaSource },
        )

    private val playerListener =
        PlayerEventListener(
            logTag = TAG,
            state = _playerState,
            playerProvider = { _exoPlayer },
            currentTracks = {
                if (currentAudioTracks.isEmpty() && currentSubtitles.isEmpty() && currentVideoStreams.isEmpty()) {
                    null
                } else {
                    CurrentTrackInfo(currentAudioTracks, currentSubtitles, currentVideoStreams)
                }
            },
            replayOnceIfArmed = {
                if (loopOnceArmed) {
                    loopOnceArmed = false
                    _exoPlayer?.let {
                        it.seekTo(0)
                        it.playWhenReady = true
                    }
                }
            },
            sourceRecovery = { error -> sourceRecoveryManager.attempt(error) },
        )
    private val analyticsListener = LoadAnalyticsListener(TAG)

    // Owns the ExoPlayer instance and its PlayerService/MediaController
    // connection (see [ExoPlayerConnectionManager]).
    private val sessionManager =
        ExoPlayerConnectionManager(
            context = context,
            logTag = TAG,
            playerListener = playerListener,
            analyticsListener = analyticsListener,
            onPlayerCreated = {
                positionTracker.start()
                engine.onPlayerReplaced()
            },
        )

    override suspend fun play(
        videoId: String,
        initial: com.tsutsen.platformplayer.core.model.ContentItem?,
    ) {
        // Stop the current video IMMEDIATELY — otherwise it keeps playing
        // (audio included) until the new MediaSource is resolved and prepared,
        // which can take seconds of network I/O. prepare() below starts the
        // new one. Harmless on an idle player (e.g. cast-only sessions).
        withContext(Dispatchers.Main) { _exoPlayer?.stop() }

        // A fresh play arms transparent source-error recovery (SABR po-token
        // rejection etc.): remember what to re-resolve and reset the budget.
        currentPlayUrl = videoId
        sourceRecoveryManager.arm()

        // Resume: look up where this video was last watched. The position is
        // applied before prepare() below so the first READY is already there.
        // The last 5% means the video was finished — start over instead.
        pendingResumePosition =
            withContext(Dispatchers.IO) {
                val row = historyDao?.getByUrl(videoId)
                val position = row?.lastPositionMs ?: 0L
                val total = row?.totalDurationMs ?: 0L
                if (total > 0 && position >= total * 0.95) 0L else position
            }

        // Publish loading state IMMEDIATELY, before any network/resolve/ExoPlayer work.
        // This is what lets the UI show a spinner right away instead of a blank gap.
        // Also set currentVideo to a placeholder so PlayerScreen gets composed immediately.
        _seekThumbnails.value = null
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
        // is a persistent preference, so reset it here (the one place every
        // play path goes through) before the track selection is applied below.
        prefs?.defaultVideoResolution?.let { res ->
            trackSelection.selectedQuality = res
            _playerState.update { it.copy(selectedQuality = res) }
        }

        if (prefs != null) {
            if (prefs.showComments) {
                commentsCache.fetchOrReplay(
                    extrasScope,
                    videoId,
                    generation,
                    fetch = { commentRepository?.getComments(videoId) ?: emptyList() },
                    update = { comments -> copy(comments = comments) },
                )
            }
            if (prefs.showRecommendedVideos) {
                recommendationsCache.fetchOrReplay(
                    extrasScope,
                    videoId,
                    generation,
                    fetch = { contentExtrasRepository?.getRecommendations(videoId) ?: emptyList() },
                    update = { recs -> copy(recommendations = recs) },
                )
            }
            chaptersCache.fetchOrReplay(
                extrasScope,
                videoId,
                generation,
                fetch = { contentExtrasRepository?.getChapters(videoId) ?: emptyList() },
                update = { chapters -> if (chapters.isNotEmpty()) copy(chapters = chapters) else this },
            )
        }

        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "play() called with videoId: $videoId")
            Log.i(TAG, "videoId length: ${videoId.length}")
            Log.i(TAG, "Is streaming URL: ${sourceResolver.isStreamingUrl(videoId)}")
            Log.i(TAG, "Is empty: ${videoId.isEmpty()}")
            Log.i(TAG, "========================================")

            // Resolve content URL to MediaSource + video details OFF the main thread.
            // resolveWithDetails() ultimately calls into VideoUrlResolver, which may do
            // network I/O — running that under Dispatchers.Main would freeze the whole
            // UI thread (no frames, no spinner) for the entire resolve, which is exactly
            // what was causing the "tap -> nothing -> screen already loaded" gap.
            val resolution =
                withContext(Dispatchers.IO) {
                    if (!sourceResolver.isStreamingUrl(videoId)) {
                        Log.i(TAG, "Content URL detected, resolving to MediaSource + details...")
                        sourceResolver.resolveWithDetails(videoId, pendingResumePosition)
                    } else {
                        Log.i(TAG, "Streaming URL detected, creating MediaSource from URL...")
                        com.tsutsen.platformplayer.core.data.repository.ResolutionResult(
                            sourceResolver.createMediaSourceFromUrl(videoId),
                            null,
                        )
                    }
                }

            // The player was closed (or superseded) while resolution was in
            // flight — abort before touching ExoPlayer, or this play would
            // resurrect the released player (recreate + prepare + start).
            if (generation != playGeneration.get()) {
                Log.i(TAG, "play() aborted: superseded during resolution ($videoId)")
                return
            }

            Log.i(TAG, "MediaSource to use: ${resolution.mediaSource?.javaClass?.simpleName}")

            // Only the ExoPlayer instance itself must be touched from the main thread.
            withContext(Dispatchers.Main) {
                // Creates the player on first use; the created-instance hook
                // starts the position ticker and pushes the instance to any
                // host that attached while it was still null (surface update
                // ran once at first composition).
                sessionManager.ensurePlayer()

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
                currentAudioTracks = resolution.videoDetails?.audioTracks ?: emptyList()
                currentVideoStreams = resolution.videoDetails?.videoStreams ?: emptyList()
                currentVideoStreamUrl = resolution.videoDetails?.videoStreamUrl
                _seekThumbnails.value = resolution.seekThumbnails
                // Unmuxed streams: the loaded file IS the quality — seed the
                // options and highlight the actually-loaded resolution
                // (overrides the prefs default, which ABR-only streams use).
                val activeHeight =
                    currentVideoStreams.firstOrNull { it.url == currentVideoStreamUrl }?.height
                        ?: currentVideoStreams.firstOrNull()?.height
                val unmuxedQuality = activeHeight?.let { "${it}p" }
                if (unmuxedQuality != null) trackSelection.selectedQuality = unmuxedQuality
                _playerState.update {
                    it.copy(
                        subtitleLanguages = currentSubtitles.map { subtitle -> subtitle.name },
                        subtitleText = "",
                        audioTracks = currentAudioTracks.map { track ->
                            com.tsutsen.platformplayer.core.model.AudioTrackInfo(track.id, track.label, track.language)
                        },
                        selectedAudioTrack =
                            resolution.videoDetails?.activeAudioTrack.orEmpty(),
                        videoQualities =
                            if (currentVideoStreams.isEmpty()) {
                                it.videoQualities
                            } else {
                                currentVideoStreams.map { option -> option.height }
                                    .distinct()
                                    .sortedDescending()
                            },
                        selectedQuality = unmuxedQuality ?: it.selectedQuality,
                    )
                }

                currentSubtitleSource = sourceResolver.buildAllSubtitleMediaSource(currentSubtitles)
                val subtitleSource = currentSubtitleSource
                val mediaSource =
                    if (subtitleSource != null) {
                        MergingMediaSource(true, resolution.mediaSource, subtitleSource)
                    } else {
                        resolution.mediaSource
                    }

                Log.i(TAG, "Setting MediaSource on ExoPlayer (subtitle=${trackSelection.selectedSubtitle})...")
                _exoPlayer?.setMediaSource(mediaSource)
                // Seeking before prepare() makes this the player's initial
                // position, so ExoPlayer starts its first load at the resume
                // point instead of seeking (and re-buffering) after READY.
                if (pendingResumePosition > 0) {
                    Log.i(TAG, "Seeking to resume position before prepare: $pendingResumePosition")
                    _exoPlayer?.seekTo(pendingResumePosition)
                    pendingResumePosition = 0
                }
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
                trackSelection.applyTrackSelectionParameters(_exoPlayer)

                // Register playback with the system: media notification,
                // lock-screen controls, media buttons (media3 session service).
                sessionManager.startServiceAndConnectController()

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
                        sourceResolver.mapVideoDetailsToContentItem(details)
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
            val pluginId = (e as? com.tsutsen.platformplayer.core.model.PluginScriptException)?.pluginId
            _playerState.update { it.copy(isLoading = false, error = e.message ?: "Failed to play video", errorPluginId = pluginId) }
        } finally {
            // Never let a resume position leak into the next play() call — but
            // don't clobber one a newer play() has already loaded in its place.
            if (generation == playGeneration.get()) pendingResumePosition = 0
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
        withContext(Dispatchers.Main) {
            _exoPlayer?.playbackParameters = _exoPlayer?.playbackParameters?.withSpeed(speed)
                ?: androidx.media3.common.PlaybackParameters(speed)
        }
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    override suspend fun setVideoQuality(quality: String) {
        // Unmuxed streams: a resolution is a URL swap, not an ABR band.
        if (currentVideoStreams.isNotEmpty()) {
            val height = quality.removeSuffix("p").toIntOrNull()
            val option =
                if (height == null) currentVideoStreams.firstOrNull() // "Auto" = best
                else currentVideoStreams.firstOrNull { it.height == height }
            trackSelection.selectedQuality = quality
            _playerState.update { it.copy(selectedQuality = quality) }
            if (option != null && option.url != currentVideoStreamUrl) {
                rebuildUnmuxedSource(videoUrl = option.url)
            }
            return
        }
        trackSelection.selectedQuality = quality
        trackSelection.applyTrackSelectionParameters(_exoPlayer)
        _playerState.update { it.copy(selectedQuality = quality) }
    }

    override suspend fun setSubtitle(selection: String) {
        if (selection != "Off" && selection != "Auto") {
            trackSelection.lastExplicitSubtitle = selection
        }
        trackSelection.selectedSubtitle = selection
        trackSelection.applyTrackSelectionParameters(_exoPlayer)
        _playerState.update { it.copy(selectedSubtitle = selection) }
        trackSelection.applySubtitleSelection(_exoPlayer)
    }

    override suspend fun setAudioTrack(selection: String) {
        // Unmuxed streams: the track list came from the resolver, so a
        // selection swaps the audio URL and rebuilds the merged source.
        val track = currentAudioTracks.firstOrNull { it.label == selection }
        if (track != null) {
            if (selection != _playerState.value.selectedAudioTrack) {
                trackSelection.preferredAudioTrack = selection
                rebuildUnmuxedSource(audioLabel = selection)
            }
            return
        }
        trackSelection.preferredAudioTrack = selection
        trackSelection.applyTrackSelectionParameters(_exoPlayer)
    }

    /**
     * Rebuilds the player's merged media source around the given video
     * URL / audio track (defaults: whatever is currently loaded), keeping
     * the current position. Unmuxed streams only — progressive files have
     * no in-stream track selection.
     */
    private suspend fun rebuildUnmuxedSource(
        videoUrl: String? = null,
        audioLabel: String? = null,
    ) {
        val swapped =
            withContext(Dispatchers.Main) {
                val player = _exoPlayer ?: return@withContext null
                val vUrl = videoUrl ?: currentVideoStreamUrl ?: return@withContext null
                val audio =
                    audioLabel?.let { label -> currentAudioTracks.firstOrNull { it.label == label } }
                        ?: currentAudioTracks.firstOrNull() ?: return@withContext null
                val positionBefore = player.currentPosition
                val wasReady = player.playWhenReady
                val videoSource =
                    ProgressiveMediaSource
                        .Factory(sourceResolver.createHttpDataSourceFactory())
                        .createMediaSource(MediaItem.fromUri(Uri.parse(vUrl)))
                val audioSource =
                    ProgressiveMediaSource
                        .Factory(sourceResolver.createHttpDataSourceFactory())
                        .createMediaSource(MediaItem.fromUri(Uri.parse(audio.url)))
                val base = MergingMediaSource(true, videoSource, audioSource)
                // Subtitle toggles are track-selection changes over the
                // pre-merged sources — keep the merge alive across the swap.
                currentPrimarySource = base
                val subtitleSource = currentSubtitleSource
                player.setMediaSource(
                    if (subtitleSource != null) MergingMediaSource(true, base, subtitleSource)
                    else base,
                )
                player.prepare()
                player.playWhenReady = wasReady
                if (positionBefore > 0) player.seekTo(positionBefore)
                vUrl to audio.label
            }
        if (swapped != null) {
            currentVideoStreamUrl = swapped.first
            _playerState.update { it.copy(selectedAudioTrack = swapped.second) }
        }
    }

    override suspend fun toggleSubtitles() {
        val current = trackSelection.selectedSubtitle
        if (current != "Off" && current != "Auto") {
            setSubtitle("Off")
            return
        }
        val target =
            trackSelection.lastExplicitSubtitle ?: currentSubtitles.firstOrNull()?.name
        if (target != null) setSubtitle(target)
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

    override fun setVideoExtras(
        comments: List<CommentItem>,
        recommendations: List<Card>,
    ) {
        _playerState.update { it.copy(comments = comments, recommendations = recommendations) }
    }

    override suspend fun loadMoreRecommendations(url: String): List<Card> {
        val more =
            contentExtrasRepository?.loadMoreRecommendations(url) ?: return emptyList()
        if (more.isNotEmpty()) {
            // Append into the shared state; the extras repo itself no-ops
            // for stale URLs, and the video guard keeps a fast switch from
            // appending onto the wrong list.
            val current = _playerState.value
            if (current.currentVideo?.url == url) {
                _playerState.update { it.copy(recommendations = current.recommendations + more) }
            }
        }
        return more
    }

    override suspend fun close() {
        // Invalidate any in-flight play() so it cannot resurrect the player
        // after its resolution completes (close-during-load).
        playGeneration.incrementAndGet()
        positionTracker.stop()
        sessionManager.disconnectMediaController()
        context.stopService(Intent(context, PlayerService::class.java))
        sessionManager.releasePlayer()
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
}
