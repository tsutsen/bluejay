package com.tsutsen.platformplayer.feature.player.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.CastingRepository
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.LiveChatRepository
import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.AudioTrackInfo
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CastDevice
import com.tsutsen.platformplayer.core.model.CastState
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.ContentType
import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.core.model.DownloadQuality
import com.tsutsen.platformplayer.core.model.PlayerState
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.LiveChatUiState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.model.VideoChapter
import com.tsutsen.platformplayer.core.model.WatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Minimum watch time before a video counts as "in progress" on cards. */
private const val MIN_WATCH_MS = 15_000L

/** Position/duration fraction at or above which a video is "watched". */
private const val WATCHED_FRACTION = 0.95f

/** How often playback history is persisted while a video plays (was: every 100ms tick). */
private const val HISTORY_SAVE_INTERVAL_MS = 5_000L

/**
 * MVI state for the Video Player screen.
 */
sealed interface PlayerUiState {
    data class Loaded(
        val isPlaying: Boolean,
        val durationMs: Long,
        val volume: Float,
        val brightness: Float,
        val playbackSpeed: Float,
        val isFullscreen: Boolean,
        val isMinimized: Boolean,
        val currentVideo: ContentItem?,
        val queue: List<ContentItem>,
        val selectedIndex: Int,
        val error: String?,
        val isLoading: Boolean = false,
        val isCompleted: Boolean = false,
        val comments: List<CommentItem> = emptyList(),
        val videoQualities: List<Int> = emptyList(),
        val subtitleLanguages: List<String> = emptyList(),
        val audioTracks: List<AudioTrackInfo> = emptyList(),
        val selectedAudioTrack: String = "",
        val selectedQuality: String = "Auto",
        val selectedSubtitle: String = "Off",
        val subtitleText: String = "",
        val chapters: List<VideoChapter> = emptyList(),
        val recommendations: List<Card> = emptyList(),
        val showComments: Boolean = true,
        val showRecommended: Boolean = true,
        val isLiked: Boolean = false,
        val isDisliked: Boolean = false,
        val isSubscribedChannel: Boolean = false,
        val isCasting: Boolean = false,
        val castDeviceName: String? = null,
        val isLive: Boolean = false,
    ) : PlayerUiState

    data object Initial : PlayerUiState

    data class Error(
        val message: String,
    ) : PlayerUiState
}

/**
 * ViewModel for the Video Player.
 * Bridges between PlayerRepository (data layer) and PlayerScreen (UI).
 */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val playerRepository: PlayerRepository,
        private val commentRepository: CommentRepository,
        private val settingsRepository: SettingsRepository,
        private val historyTracker: HistoryTracker,
        private val libraryRepository: LibraryRepository,
        private val channelRepository: ChannelRepository,
        private val downloadsRepository: DownloadsRepository,
        private val playbackQueueRepository: PlaybackQueueRepository,
        private val castingRepository: CastingRepository,
        private val liveChatRepository: LiveChatRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Initial)

        /** High-frequency playback position (updated 10×/s by the repository ticker).
         * Kept OUT of [uiState] on purpose: a position-only tick must not rebuild the
         * whole [PlayerUiState] or recompose the player screen — only the timeline
         * leaves collect this flow. */
        private val _positionMs = MutableStateFlow(0L)
        val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

        /** Last repository state, used to detect position-only ticks. */
        @Volatile
        private var lastPlayerState: PlayerState? = null

        /** Throttled history persistence (see [persistPlaybackHistory]). */
        @Volatile
        private var lastHistorySaveAtMs = 0L

        @Volatile
        private var lastHistorySavedUrl: String? = null

        /** Job running live chat start for the current video. */
        @Volatile
        private var liveChatJob: Job? = null

        init {
            // Subtitle appearance follows the settings live (font, size,
            // bottom padding).
            viewModelScope.launch {
                settingsRepository.preferences.collect { prefs ->
                    SubtitleStyle.applyPreferences(
                        font = prefs.subtitle.font,
                        size = prefs.subtitle.size,
                        padding = prefs.subtitle.bottomPadding,
                    )
                }
            }
        }

        val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

        /** Live chat session for the current video (null when not live).
         * Kept OUT of [uiState] on purpose: chat bursts must not rebuild
         * the whole player state on every tick. */
        val liveChat: StateFlow<LiveChatUiState?> = liveChatRepository.state

        /** Speed multiplier reached after holding the speed-up side. */
        val defaultSpeedup: Float
            get() = settingsRepository.preferences.value.defaultSpeedup

        /** Horizontal-swipe sensitivity of the speed-up/slow-down hold. */
        val speedupSensitivity: Float
            get() = settingsRepository.preferences.value.speedupSensitivity

        /** Live saved types for the current video (drives like/dislike buttons). */
        @Volatile
        private var currentSavedTypes: Set<SavedVideoType> = emptySet()
        private var savedTypesUrl: String? = null
        private var savedTypesJob: Job? = null

        /** Live saved types as a flow — for the options sheet host. */
        private val _savedTypes = MutableStateFlow<Set<SavedVideoType>>(emptySet())
        val savedTypes: StateFlow<Set<SavedVideoType>> = _savedTypes.asStateFlow()

        /** Loop mode: OFF → ONCE → INFINITE, cycled by the loop button.
         * Backed by the repository so it survives configuration changes. */
        val loopMode: StateFlow<Int> = playerRepository.loopMode

        /** Queue items, including the currently playing video (shown as the
         * now-playing card in the queue UI). */
        val queue: StateFlow<List<com.tsutsen.platformplayer.core.model.ContentItem>> =
            playbackQueueRepository.queue

        /** Cast connection / discovery state for the casting sheet. */
        val castState: StateFlow<CastState> = castingRepository.state

        /** Watch state per video url, live from local history. Empty entry =
         * not (meaningfully) watched. Feeds use this for progress bars and
         * the watched checkmark on video cards. */
        val watchStates: StateFlow<Map<String, WatchState>> =
            historyTracker
                .observeHistory()
                .map { entries ->
                    entries
                        .filter { it.totalDurationMs > 0 && it.lastPositionMs >= MIN_WATCH_MS }
                        .associate {
                            it.contentUrl to
                                WatchState(
                                    progress =
                                        (it.lastPositionMs.toFloat() / it.totalDurationMs)
                                            .coerceIn(0f, 1f),
                                    isWatched =
                                        it.lastPositionMs >=
                                            it.totalDurationMs * WATCHED_FRACTION,
                                )
                        }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        /**
         * Persist playback history on a throttle instead of on every state tick.
         * The old per-tick write (10×/s) hammered Room and — via the live query
         * behind [watchStates] — re-emitted to every screen that watches history.
         * Saves now happen every [HISTORY_SAVE_INTERVAL_MS] while playing, plus on
         * video change, pause and completion.
         */
        private fun persistPlaybackHistory(
            video: ContentItem,
            positionMs: Long,
            durationMs: Long,
            force: Boolean,
        ) {
            val now = System.currentTimeMillis()
            if (
                !force &&
                video.url == lastHistorySavedUrl &&
                now - lastHistorySaveAtMs < HISTORY_SAVE_INTERVAL_MS
            ) {
                return
            }
            lastHistorySaveAtMs = now
            lastHistorySavedUrl = video.url
            viewModelScope.launch {
                historyTracker.trackPlayback(
                    contentUrl = video.url,
                    title = video.title,
                    author = video.author?.name,
                    authorUrl = video.author?.url?.takeIf { it.isNotEmpty() },
                    thumbnailUrl = video.thumbnailUrl,
                    currentPositionMs = positionMs,
                    totalDurationMs = durationMs,
                    viewCount = video.viewCount,
                )
                if (durationMs > 0) {
                    libraryRepository.backfillDuration(video.url, durationMs)
                }
            }
        }

        fun castConnect(device: CastDevice) {
            castingRepository.connect(device)
        }

        fun castConnectByUrl(url: String) {
            castingRepository.connectByUrl(url)
        }

        fun castDisconnect() {
            castingRepository.disconnect()
        }

        /** Enqueue the given video (starts playing if nothing is). */
        fun addToQueue(item: com.tsutsen.platformplayer.core.model.ContentItem) {
            playbackQueueRepository.add(item)
        }

        fun playQueueItem(index: Int) {
            playbackQueueRepository.playAt(index)
        }

        /** Remove [url] from the queue (URL-based so in-flight delete
         * animations never act on a stale index). */
        fun removeQueueItemUrl(url: String) {
            playbackQueueRepository.remove(url)
        }

        fun moveQueueItem(
            from: Int,
            to: Int,
        ) {
            playbackQueueRepository.move(from, to)
        }

        /** Playlists containing the current video (options sheet checkboxes). */
        private val _containedPlaylists = MutableStateFlow<Set<Long>>(emptySet())
        val containedPlaylists: StateFlow<Set<Long>> = _containedPlaylists.asStateFlow()
        private var containedUrl: String? = null
        private var containedJob: Job? = null

        /** Channel subscription state for the current author (lazy-fetched). */
        @Volatile
        private var isSubscribedUrl: String? = null

        @Volatile
        private var isSubscribedCache: Boolean = false

        val playlists: StateFlow<List<PlaylistOption>> = libraryRepository.playlists
        val downloads: StateFlow<List<DownloadInfo>> = downloadsRepository.downloads

        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Lazily,
                    settingsRepository.preferences.value.gridColumns,
                )

        init {
            // Observe repository player state and map to UiState
            // Position updates are now handled by the repository's position ticker
            viewModelScope.launch {
                playerRepository.playerState
                    .collect { playerState ->
                        // The ticker fires 10×/s with only the position changed. Route
                        // that into a dedicated flow and skip the rebuild below unless
                        // something else changed — otherwise every tick would re-emit a
                        // new 28-field state and recompose the whole player subtree.
                        _positionMs.value = playerState.currentPositionMs
                        val prev = lastPlayerState
                        lastPlayerState = playerState
                        if (
                            prev != null &&
                            playerState.copy(currentPositionMs = prev.currentPositionMs) == prev
                        ) {
                            return@collect
                        }
                        _uiState.value =
                            PlayerUiState.Loaded(
                                isPlaying = playerState.isPlaying,
                                durationMs = playerState.durationMs,
                                volume = playerState.volume,
                                brightness = playerState.brightness,
                                playbackSpeed = playerState.playbackSpeed,
                                isFullscreen = playerState.isFullscreen,
                                isMinimized = playerState.isMinimized,
                                currentVideo = playerState.currentVideo,
                                queue = playerState.queue,
                                selectedIndex = playerState.selectedIndex,
                                error = playerState.error,
                                isLoading = playerState.isLoading,
                                isCompleted = playerState.isCompleted,
                                // Comments/recommendations/chapters live in the shared
                                // PlayerState — PlayerRepository.play() fetches them for
                                // every play path (main screen AND companion screen),
                                // so both displays always show the same data.
                                comments = playerState.comments,
                                videoQualities = playerState.videoQualities,
                                subtitleLanguages = playerState.subtitleLanguages,
                                audioTracks = playerState.audioTracks,
                                selectedAudioTrack = playerState.selectedAudioTrack,
                                selectedQuality = playerState.selectedQuality,
                                selectedSubtitle = playerState.selectedSubtitle,
                                subtitleText = playerState.subtitleText,
                                chapters = playerState.chapters,
                                recommendations = playerState.recommendations,
                                showComments = settingsRepository.preferences.value.showComments,
                                isLiked = SavedVideoType.LIKED in currentSavedTypes,
                                isDisliked = SavedVideoType.DISLIKED in currentSavedTypes,
                                isSubscribedChannel =
                                    isSubscribedUrl?.let {
                                        isSubscribedCache
                                    } ?: false,
                                showRecommended =
                                    settingsRepository.preferences.value
                                        .showRecommendedVideos,
                                isCasting = playerState.isCasting,
                                castDeviceName = playerState.castDeviceName,
                                isLive =
                                    playerState.currentVideo?.contentType ==
                                    ContentType.LIVE,
                            )

                        // Live chat follows the current video: start for live
                        // streams, stop for everything else.
                        val liveVideo =
                            playerState.currentVideo
                                ?.takeIf { it.contentType == ContentType.LIVE }
                        if (liveVideo != null) {
                            liveChatJob?.cancel()
                            liveChatJob =
                                viewModelScope.launch {
                                    liveChatRepository.start(liveVideo.url)
                                }
                        } else {
                            liveChatJob?.cancel()
                            liveChatRepository.stop()
                        }

                        // Track playback history. The player's real duration
                        // backfills history + library rows that stored none,
                        // so library cards show a duration after one play.
                        val video = playerState.currentVideo
                        if (video != null && video.url != savedTypesUrl) {
                            savedTypesUrl = video.url
                            savedTypesJob?.cancel()
                            savedTypesJob =
                                viewModelScope.launch {
                                    libraryRepository
                                        .observeSavedTypes(video.url)
                                        .collect { types ->
                                            currentSavedTypes = types
                                            _savedTypes.value = types
                                            _uiState.update {
                                                (it as? PlayerUiState.Loaded)?.let { s ->
                                                    s.copy(
                                                        isLiked = SavedVideoType.LIKED in types,
                                                        isDisliked =
                                                            SavedVideoType.DISLIKED in types,
                                                    )
                                                }
                                                    ?: it
                                            }
                                        }
                                }
                        }
                        // Playlists containing this video (options sheet).
                        if (video != null && video.url != containedUrl) {
                            containedUrl = video.url
                            containedJob?.cancel()
                            containedJob =
                                viewModelScope.launch {
                                    libraryRepository
                                        .observePlaylistsContaining(video.url)
                                        .collect { ids ->
                                            _containedPlaylists.value = ids
                                        }
                                }
                        }
                        // Channel subscription state for the current author.
                        val channelUrl = video?.author?.url?.takeIf { it.isNotEmpty() }
                        if (channelUrl != null && channelUrl != isSubscribedUrl) {
                            isSubscribedUrl = channelUrl
                            isSubscribedCache = false
                            viewModelScope.launch {
                                val subscribed =
                                    withContext(Dispatchers.IO) {
                                        channelRepository.isSubscribed(channelUrl)
                                    }
                                if (isSubscribedUrl == channelUrl) {
                                    isSubscribedCache = subscribed
                                    _uiState.update {
                                        (it as? PlayerUiState.Loaded)?.copy(
                                            isSubscribedChannel = subscribed,
                                        )
                                            ?: it
                                    }
                                }
                            }
                        }
                        if (video != null) {
                            persistPlaybackHistory(
                                video = video,
                                positionMs = playerState.currentPositionMs,
                                durationMs = playerState.durationMs,
                                force = !playerState.isPlaying || playerState.isCompleted,
                            )
                        }
                    }
            }
        }

        /** Toggle the current video in/out of the library's Liked list. */
        fun toggleLike(liked: Boolean) {
            toggleSaveType(SavedVideoType.LIKED, liked)
        }

        /** Toggle the current video in/out of the library's Disliked list. */
        fun toggleDislike(disliked: Boolean) {
            toggleSaveType(SavedVideoType.DISLIKED, disliked)
        }

        /** Toggle the current video in/out of the library's Watch Later list. */
        fun toggleWatchLater(saved: Boolean) {
            toggleSaveType(SavedVideoType.WATCH_LATER, saved)
        }

        /** Toggle the current video in/out of the library's Favourites list. */
        fun toggleFavourite(saved: Boolean) {
            toggleSaveType(SavedVideoType.FAVOURITE, saved)
        }

        /**
         * Toggle the current video in/out of a saved list. Like and dislike are
         * mutually exclusive: adding one removes the other.
         */
        private fun toggleSaveType(
            type: SavedVideoType,
            isSaved: Boolean,
        ) {
            viewModelScope.launch {
                val video = playerRepository.playerState.value.currentVideo ?: return@launch
                if (isSaved) {
                    libraryRepository.removeSavedVideo(type, video.url)
                } else {
                    when (type) {
                        SavedVideoType.LIKED -> {
                            libraryRepository.removeSavedVideo(
                                SavedVideoType.DISLIKED,
                                video.url,
                            )
                        }

                        SavedVideoType.DISLIKED -> {
                            libraryRepository.removeSavedVideo(SavedVideoType.LIKED, video.url)
                        }

                        else -> {
                            Unit
                        }
                    }
                    libraryRepository.saveVideo(type, video.toVideoCard())
                }
            }
        }

        /** Subscribe/unsubscribe the current video's channel. */
        fun subscribeChannel() {
            viewModelScope.launch {
                val url =
                    playerRepository.playerState.value.currentVideo
                        ?.author
                        ?.url
                if (url.isNullOrEmpty()) return@launch
                val subscribed =
                    withContext(Dispatchers.IO) {
                        channelRepository.toggleSubscription(url)
                    }
                isSubscribedCache = subscribed
                _uiState.update {
                    (it as? PlayerUiState.Loaded)?.copy(isSubscribedChannel = subscribed) ?: it
                }
            }
        }

        /** Start downloading the current video (null quality = engine default). */
        fun startDownload(quality: DownloadQuality? = null) {
            val url =
                playerRepository.playerState.value.currentVideo
                    ?.url ?: return
            viewModelScope.launch { downloadsRepository.startDownload(url, quality) }
        }

        /** Cancel the current video's download. */
        fun cancelDownload() {
            val url =
                playerRepository.playerState.value.currentVideo
                    ?.url ?: return
            viewModelScope.launch { downloadsRepository.cancelDownload(url) }
        }

        /** Delete the current video's downloaded copy. */
        fun deleteDownload() {
            val url =
                playerRepository.playerState.value.currentVideo
                    ?.url ?: return
            viewModelScope.launch { downloadsRepository.deleteDownload(url) }
        }

        /** Add [video] to a local playlist (options-sheet picker). */
        fun addToPlaylist(
            video: ContentItem,
            playlistId: Long,
        ) {
            viewModelScope.launch {
                libraryRepository.addVideoToPlaylist(
                    playlistId,
                    VideoCard(
                        id = video.id,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        author = video.author?.name,
                        authorUrl = video.author?.url,
                        durationMs = video.durationMs,
                        viewCount = video.viewCount,
                        publishedAt = video.publishedAt,
                        url = video.url,
                    ),
                )
            }
        }

        /** Seek to [positionMs], clamped to the current video's duration. */
        fun seekToClamped(positionMs: Long) {
            val duration = playerRepository.playerState.value.durationMs
            val target = if (duration > 0) positionMs.coerceIn(0, duration - 500) else positionMs.coerceAtLeast(0)
            seekTo(target)
        }

        /** Add/remove the current video in/out of a local playlist (sheet checkbox). */
        fun togglePlaylistMembership(
            playlistId: Long,
            add: Boolean,
        ) {
            viewModelScope.launch {
                val video = playerRepository.playerState.value.currentVideo ?: return@launch
                if (add) {
                    libraryRepository.addVideoToPlaylist(playlistId, video.toVideoCard())
                } else {
                    libraryRepository.removeVideoFromPlaylist(playlistId, video.url)
                }
            }
        }

        /** Create a new playlist and add the current video to it. */
        fun createPlaylistAndAdd(name: String) {
            viewModelScope.launch {
                val video = playerRepository.playerState.value.currentVideo ?: return@launch
                val id = libraryRepository.createPlaylist(name)
                libraryRepository.addVideoToPlaylist(id, video.toVideoCard())
            }
        }

        private fun ContentItem.toVideoCard() =
            VideoCard(
                id = id,
                title = title,
                thumbnailUrl = thumbnailUrl,
                author = author?.name,
                authorUrl = author?.url?.takeIf { it.isNotEmpty() },
                durationMs = durationMs,
                viewCount = viewCount,
                publishedAt = publishedAt,
                url = url,
            )

        fun play(
            videoId: String,
            initial: com.tsutsen.platformplayer.core.model.ContentItem? = null,
        ) {
            viewModelScope.launch {
                // PlayerRepository.play() clears the previous video's extras and
                // fetches the new one's (comments/recs/chapters) — the single
                // orchestration point shared with the companion screen.
                playerRepository.play(videoId, initial)
                // A new video starts at the user's default speed (manual
                // changes during playback still override it).
                playerRepository.setPlaybackSpeed(
                    settingsRepository.preferences.value.defaultPlaybackSpeed,
                )
                // Track in history
                historyTracker.trackPlayback(
                    contentUrl = videoId,
                    title = initial?.title ?: videoId,
                    author = initial?.author?.name,
                    thumbnailUrl = initial?.thumbnailUrl,
                )
            }
        }

        /** Play a card whose details are already known (instant title/thumb). */
        fun play(card: com.tsutsen.platformplayer.core.model.VideoCard) {
            play(
                card.url,
                com.tsutsen.platformplayer.core.model.ContentItem(
                    id = card.id,
                    url = card.url,
                    title = card.title,
                    author =
                        card.author?.let {
                            com.tsutsen.platformplayer.core.model.Author(
                                id = card.authorUrl.orEmpty(),
                                name = it,
                                url = card.authorUrl,
                                thumbnailUrl = null,
                            )
                        },
                    thumbnailUrl = card.thumbnailUrl,
                    contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
                    publishedAt = card.publishedAt,
                    durationMs = card.durationMs,
                    viewCount = card.viewCount,
                ),
            )
        }

        fun loadMoreComments(contentUrl: String) {
            viewModelScope.launch {
                // Guard: a faster switch to a new video supersedes this request.
                if (playerRepository.playerState.value.currentVideo
                        ?.url != contentUrl
                ) {
                    return@launch
                }
                try {
                    val moreComments =
                        withContext(Dispatchers.IO) {
                            commentRepository.loadMoreComments(contentUrl)
                        }

                    if (moreComments.isNotEmpty()) {
                        val current = playerRepository.playerState.value
                        playerRepository.setVideoExtras(
                            current.comments + moreComments,
                            current.recommendations,
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Failed to load more comments", e)
                }
            }
        }

        fun getPlayer() = playerRepository

        fun pause() {
            viewModelScope.launch {
                playerRepository.pause()
            }
        }

        fun resume() {
            viewModelScope.launch {
                playerRepository.resume()
            }
        }

        fun seekTo(positionMs: Long) {
            viewModelScope.launch {
                playerRepository.seekTo(positionMs)
            }
        }

        /** Seek relative to current ExoPlayer position (avoids stale UI state). */
        fun seekBy(deltaMs: Long) {
            val current = playerRepository.exoPlayer?.currentPosition ?: return
            viewModelScope.launch {
                playerRepository.seekTo((current + deltaMs).coerceIn(0, playerRepository.exoPlayer?.duration ?: Long.MAX_VALUE))
            }
        }

        fun setVolume(volume: Float) {
            viewModelScope.launch {
                playerRepository.setVolume(volume)
            }
        }

        fun setBrightness(brightness: Float) {
            viewModelScope.launch {
                playerRepository.setBrightness(brightness)
            }
        }

        fun setPlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                playerRepository.setPlaybackSpeed(speed)
            }
        }

        fun setVideoQuality(quality: String) {
            viewModelScope.launch {
                playerRepository.setVideoQuality(quality)
            }
        }

        fun setSubtitle(selection: String) {
            viewModelScope.launch {
                playerRepository.setSubtitle(selection)
            }
        }

        fun setAudioTrack(selection: String) {
            viewModelScope.launch {
                playerRepository.setAudioTrack(selection)
            }
        }

        fun toggleSubtitles() {
            viewModelScope.launch {
                playerRepository.toggleSubtitles()
            }
        }

        fun toggleFullscreen() {
            viewModelScope.launch {
                playerRepository.toggleFullscreen()
            }
        }

        fun minimize() {
            viewModelScope.launch {
                playerRepository.minimize()
            }
        }

        fun exitFullscreen() {
            viewModelScope.launch {
                playerRepository.exitFullscreen()
            }
        }

        fun exitMiniPlayer() {
            viewModelScope.launch {
                playerRepository.exitMiniPlayer()
            }
        }

        fun close() {
            viewModelScope.launch {
                playerRepository.close()
            }
        }

        fun skipNext() {
            playbackQueueRepository.playNext()
        }

        fun skipPrevious() {
            viewModelScope.launch {
                // TODO: Implement queue navigation
            }
        }

        fun cycleLoopMode() {
            val next = (playerRepository.loopMode.value + 1) % 3
            playerRepository.setLoopMode(next)
        }
    }
