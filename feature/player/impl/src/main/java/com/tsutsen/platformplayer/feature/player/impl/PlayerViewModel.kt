package com.tsutsen.platformplayer.feature.player.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.ContentExtrasRepository
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.LiveChatRepository
import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.data.repository.PlaybackEngine
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.AudioTrackInfo
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.ContentType
import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.core.model.DownloadQuality
import com.tsutsen.platformplayer.core.model.PlayerState
import com.tsutsen.platformplayer.core.model.TrackSelectionValues
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.LiveChatUiState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.model.VideoChapter
import com.tsutsen.platformplayer.core.model.WatchState
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Minimum watch time before a video counts as "in progress" on cards. */
private const val MIN_WATCH_MS = 15_000L

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
        val selectedQuality: String = TrackSelectionValues.AUTO,
        val selectedSubtitle: String = TrackSelectionValues.OFF,
        val subtitleText: String = "",
        val chapters: List<VideoChapter> = emptyList(),
        val recommendations: List<Card> = emptyList(),
        val showComments: Boolean = true,
        val showRecommended: Boolean = true,
        val isLiked: Boolean = false,
        val isDisliked: Boolean = false,
        val isSubscribedChannel: Boolean = false,
        val isLive: Boolean = false,
    ) : PlayerUiState

    data object Initial : PlayerUiState

    data class Error(
        val message: String,
        val pluginId: String? = null,
    ) : PlayerUiState
}

/**
 * ViewModel for the Video Player.
 * Bridges between PlayerRepository (data layer) and PlayerScreen (UI).
 *
 * The concerns it used to inline live in collaborators constructed here:
 * [PlayerPlaybackController] (transport/queue/controller keys),
 * [PlayerMediaActions] (likes/subscribe/downloads/playlists),
 * [PlayerHistoryPersister] (throttled history) and
 * [PlayerSideContentLoader] (comments/recommendations pagination). The
 * ViewModel keeps the ui-state assembly, the main collector and thin
 * delegating methods.
 */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val playerRepository: PlayerRepository,
        private val commentRepository: CommentRepository,
        private val settingsRepository: SettingsRepository,
        historyTracker: HistoryTracker,
        private val libraryRepository: LibraryRepository,
        private val channelRepository: ChannelRepository,
        private val downloadsRepository: DownloadsRepository,
        private val playbackQueueRepository: PlaybackQueueRepository,
        private val liveChatRepository: LiveChatRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Initial)

        /** High-frequency playback position (updated 10×/s by the repository ticker).
         * Kept OUT of [uiState] on purpose: a position-only tick must not rebuild the
         * whole [PlayerUiState] or recompose the player screen — only the timeline
         * leaves collect this flow. */
        private val _positionMs = MutableStateFlow(0L)
        val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

        /** Storyboard scrub previews for the current video (null when the
         *  plugin offers none). Straight from the repository — low-frequency. */
        val seekThumbnails: StateFlow<com.tsutsen.platformplayer.core.data.repository.SeekThumbnailProvider?> =
            playerRepository.seekThumbnails

        /** Last value emitted to [positionMs]. Emissions are quantized to 500 ms so the
         *  timeline leaves recompose at 2 Hz instead of the ticker's 10 Hz (every 10th
         *  recompose was otherwise a no-op re-layout). A seek shows on the first tick
         *  crossing a 500 ms boundary (<=100 ms). */
        @Volatile
        private var lastEmittedPositionMs = 0L

        /** Last repository state, used to detect position-only ticks. */
        @Volatile
        private var lastPlayerState: PlayerState? = null

        /** Job running live chat start for the current video. */
        @Volatile
        private var liveChatJob: Job? = null

        /** Per-slot gesture assignments (Settings > Gestures); live flow.
         *  The player surface builds its gesture configs from this on (re)composition. */
        val gesturePrefs: StateFlow<com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences> =
            settingsRepository.preferences.map { it.playerGestures }.stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences(),
            )

        /** Controller (gamepad/remote) settings (Settings > Controller); live flow. */
        val controllerPrefs: StateFlow<ControllerPreferences> =
            settingsRepository.preferences.map { it.controller }.stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                ControllerPreferences(),
            )

        private val historyPersister =
            PlayerHistoryPersister(historyTracker, libraryRepository, viewModelScope)

        private val mediaActions =
            PlayerMediaActions(
                playerRepository = playerRepository,
                libraryRepository = libraryRepository,
                channelRepository = channelRepository,
                downloadsRepository = downloadsRepository,
                scope = viewModelScope,
                uiState = _uiState,
            )

        private val sideContent =
            PlayerSideContentLoader(playerRepository, commentRepository, viewModelScope)

        private val playback =
            PlayerPlaybackController(
                playerRepository = playerRepository,
                playbackQueueRepository = playbackQueueRepository,
                settingsRepository = settingsRepository,
                historyTracker = historyTracker,
                scope = viewModelScope,
                uiState = _uiState,
                controllerPrefs = controllerPrefs,
            )

        init {
            // Subtitle appearance follows the settings live (font, size,
            // bottom padding).
            viewModelScope.launch {
                settingsRepository.preferences.collect { prefs ->
                    SubtitleStyle.applyPreferences(
                        font = prefs.subtitle.font,
                        size = prefs.subtitle.size,
                        padding = prefs.subtitle.bottomPadding,
                        outline = prefs.subtitle.outline,
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

        /** Jump step (seconds) for the back/forward seek gestures. */
        val jumpStepSeconds: Int
            get() = settingsRepository.preferences.value.jumpStepSeconds

        /** Live saved types as a flow — for the options sheet host. */
        val savedTypes: StateFlow<Set<SavedVideoType>> get() = mediaActions.savedTypes

        /** Playlists containing the current video (options sheet checkboxes). */
        val containedPlaylists: StateFlow<Set<Long>> get() = mediaActions.containedPlaylists

        /** Loop mode: OFF → ONCE → INFINITE, cycled by the loop button.
         * Backed by the repository so it survives configuration changes. */
        val loopMode: StateFlow<Int> = playerRepository.loopMode

        /** Queue items, including the currently playing video (shown as the
         * now-playing card in the queue UI). */
        val queue: StateFlow<List<com.tsutsen.platformplayer.core.model.ContentItem>> =
            playbackQueueRepository.queue

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
                                    isWatched = WatchState.finished(
                                        it.lastPositionMs,
                                        it.totalDurationMs,
                                    ),
                                )
                        }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                        if (playerState.currentPositionMs / 500L != lastEmittedPositionMs / 500L) {
                            lastEmittedPositionMs = playerState.currentPositionMs
                            _positionMs.value = playerState.currentPositionMs
                        }
                        // History sampling runs on every tick — the early
                        // return below skips the UI rebuild for position-only
                        // ticks, but those ticks are exactly what the
                        // 5s-throttled sampler feeds on. Skipping them made
                        // saves fire only on state changes (pause/seek), and
                        // the tracker's gap cap then refused to credit the
                        // long gaps between them, so watchedMs tracked only
                        // a few percent of real playback.
                        playerState.currentVideo?.let { video ->
                            historyPersister.sample(
                                video = video,
                                positionMs = playerState.currentPositionMs,
                                durationMs = playerState.durationMs,
                                force = !playerState.isPlaying || playerState.isCompleted,
                            )
                        }
                        val prev = lastPlayerState
                        lastPlayerState = playerState
                        if (
                            prev != null &&
                            playerState.copy(currentPositionMs = prev.currentPositionMs) == prev
                        ) {
                            return@collect
                        }
                        _uiState.value =
                            if (playerState.error != null)
                                PlayerUiState.Error(playerState.error!!, playerState.errorPluginId)
                            else
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
                                isLiked = SavedVideoType.LIKED in mediaActions.currentSavedTypes,
                                isDisliked = SavedVideoType.DISLIKED in mediaActions.currentSavedTypes,
                                isSubscribedChannel = mediaActions.isSubscribedCached,
                                showRecommended =
                                    settingsRepository.preferences.value
                                        .showRecommendedVideos,
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

                        // Saved types, containing playlists and channel
                        // subscription state follow the current video.
                        mediaActions.onVideoChanged(playerState.currentVideo)
                    }
            }
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

        /** Toggle the current video in/out of the library's Liked list. */
        fun toggleLike(liked: Boolean) = mediaActions.toggleLike(liked)

        /** Toggle the current video in/out of the library's Disliked list. */
        fun toggleDislike(disliked: Boolean) = mediaActions.toggleDislike(disliked)

        /** Toggle the current video in/out of the library's Watch Later list. */
        fun toggleWatchLater(saved: Boolean) = mediaActions.toggleWatchLater(saved)

        /** Toggle the current video in/out of the library's Favourites list. */
        fun toggleFavourite(saved: Boolean) = mediaActions.toggleFavourite(saved)

        /** Subscribe/unsubscribe the current video's channel. */
        fun subscribeChannel() = mediaActions.subscribeChannel()

        /** Start downloading the current video (null quality = engine default). */
        fun startDownload(quality: DownloadQuality? = null) = mediaActions.startDownload(quality)

        /** Cancel the current video's download. */
        fun cancelDownload() = mediaActions.cancelDownload()

        /** Delete the current video's downloaded copy. */
        fun deleteDownload() = mediaActions.deleteDownload()

        /** Add [video] to a local playlist (options-sheet picker). */
        fun addToPlaylist(
            video: ContentItem,
            playlistId: Long,
        ) = mediaActions.addToPlaylist(video, playlistId)

        /** Add/remove the current video in/out of a local playlist (sheet checkbox). */
        fun togglePlaylistMembership(
            playlistId: Long,
            add: Boolean,
        ) = mediaActions.togglePlaylistMembership(playlistId, add)

        /** Create a new playlist and add the current video to it. */
        fun createPlaylistAndAdd(name: String) = mediaActions.createPlaylistAndAdd(name)

        fun play(
            videoId: String,
            initial: com.tsutsen.platformplayer.core.model.ContentItem? = null,
        ) = playback.play(videoId, initial)

        /** Play a card whose details are already known (instant title/thumb). */
        fun play(card: com.tsutsen.platformplayer.core.model.VideoCard) = playback.play(card)

        fun loadMoreComments(contentUrl: String) = sideContent.loadMoreComments(contentUrl)

        /** Recommendations tab: fetch the next page and append it to the shared list. */
        fun loadMoreRecommendations(contentUrl: String) =
            sideContent.loadMoreRecommendations(contentUrl)

        fun getPlayer() = playerRepository

        fun playbackEngine(): PlaybackEngine = playerRepository.engine

        fun pause() = playback.pause()

        fun resume() = playback.resume()

        fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

        /** Seek to [positionMs], clamped to the current video's duration. */
        fun seekToClamped(positionMs: Long) = playback.seekToClamped(positionMs)

        /** Seek relative to the engine's live position (avoids stale UI state). */
        fun seekBy(deltaMs: Long) = playback.seekBy(deltaMs)

        /** Current engine position in ms (0 when no player). */
        fun currentPositionMs(): Long = playback.currentPositionMs()

        fun setVolume(volume: Float) = playback.setVolume(volume)

        fun setBrightness(brightness: Float) = playback.setBrightness(brightness)

        fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

        fun setVideoQuality(quality: String) = playback.setVideoQuality(quality)

        fun setSubtitle(selection: String) = playback.setSubtitle(selection)

        fun setAudioTrack(selection: String) = playback.setAudioTrack(selection)

        fun toggleSubtitles() = playback.toggleSubtitles()

        /**
         * Handle a gamepad/remote press or release edge; see
         * [PlayerPlaybackController.handleControllerKey].
         */
        fun handleControllerKey(event: GamepadKeyBus.GamepadEvent): Boolean =
            playback.handleControllerKey(event)

        fun toggleFullscreen() = playback.toggleFullscreen()

        fun minimize() = playback.minimize()

        fun exitFullscreen() = playback.exitFullscreen()

        fun exitMiniPlayer() = playback.exitMiniPlayer()

        fun close() = playback.close()

        fun skipNext() = playback.skipNext()

        fun skipPrevious() = playback.skipPrevious()

        fun cycleLoopMode() = playback.cycleLoopMode()
    }
