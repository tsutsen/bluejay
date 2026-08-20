package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.ContentType
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel behind [VideoOptionsSheetHost]. One instance per screen
 * (hiltViewModel), so Home/Search/Subscriptions/Library/Channel all get the
 * same save-toggle + playlist behaviour without duplicating logic.
 */
@HiltViewModel
class VideoOptionsViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val downloadsRepository: DownloadsRepository,
        private val playbackQueueRepository: PlaybackQueueRepository,
    ) : ViewModel() {
        private val savedTypesCache = mutableMapOf<String, StateFlow<Set<SavedVideoType>>>()

        private val _downloadMessage = MutableStateFlow<String?>(null)

        /** One-shot feedback for download failures (shown as a toast). */
        val downloadMessage: StateFlow<String?> = _downloadMessage

        /** Live playback queue (drives the sheet's queue tile). */
        val queue: StateFlow<List<ContentItem>>
            get() = playbackQueueRepository.queue

        fun removeFromQueue(url: String) {
            playbackQueueRepository.remove(url)
        }

        fun consumeDownloadMessage() {
            _downloadMessage.value = null
        }

        /** Enqueue [video] (starts playing if nothing is). */
        fun addToQueue(video: VideoCard) {
            playbackQueueRepository.add(
                ContentItem(
                    id = video.id,
                    url = video.url,
                    title = video.title,
                    author = video.author?.let { name ->
                        Author(
                            id = video.id,
                            name = name,
                            url = video.authorUrl,
                            thumbnailUrl = null,
                        )
                    },
                    thumbnailUrl = video.thumbnailUrl,
                    contentType = ContentType.VIDEO,
                    durationMs = video.durationMs,
                    viewCount = video.viewCount,
                    publishedAt = video.publishedAt,
                )
            )
        }

        /**
         * URLs with a download request in flight. Resolving the video
         * details takes seconds on slow connections — without this the
         * button looks dead and users double-tap (which double-queues).
         */
        private val _startingUrls = MutableStateFlow<Set<String>>(emptySet())

        fun download(video: VideoCard) {
            // Check + add on the main thread with no suspension between
            // them, so rapid double/triple taps can't both slip through
            // (each would otherwise queue a separate full download).
            if (video.url in _startingUrls.value) return
            _startingUrls.value += video.url
            viewModelScope.launch {
                try {
                    // The engine toasts on success and dialogs on its own
                    // failures; only surface pre-resolution errors here.
                    downloadsRepository.startDownload(video.url)?.let { _downloadMessage.value = it }
                } finally {
                    _startingUrls.value -= video.url
                }
            }
        }

        fun stopDownload(video: VideoCard) {
            viewModelScope.launch {
                downloadsRepository.cancelDownload(video.url)?.let { _downloadMessage.value = it }
            }
        }

        fun deleteDownload(video: VideoCard) {
            viewModelScope.launch {
                val error = downloadsRepository.deleteDownload(video.url)
                _downloadMessage.value =
                    if (error == null) "Download deleted" else error
            }
        }

        private val downloadStateCache = mutableMapOf<String, StateFlow<DownloadButtonState>>()

        /** Live download state for [url] — drives the options sheet button. */
        fun downloadState(url: String): StateFlow<DownloadButtonState> =
            downloadStateCache.getOrPut(url) {
                downloadsRepository.downloads
                    .map { list ->
                        val d = list.firstOrNull { it.url == url }
                        when {
                            d == null -> DownloadButtonState.Idle
                            d.done -> DownloadButtonState.Downloaded
                            else -> DownloadButtonState.Downloading(d.progress)
                        }
                    }.combine(_startingUrls) { state, starting ->
                        if (state == DownloadButtonState.Idle && url in starting) {
                            DownloadButtonState.Starting
                        } else {
                            state
                        }
                    }.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        DownloadButtonState.Idle,
                    )
            }

        val playlists: StateFlow<List<PlaylistOption>>
            get() = libraryRepository.playlists

        /** Stable StateFlow of the saved types for [url] (cached per url). */
        fun savedTypes(url: String): StateFlow<Set<SavedVideoType>> =
            savedTypesCache.getOrPut(url) {
                libraryRepository
                    .observeSavedTypes(url)
                    .stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        emptySet(),
                    )
            }

        /** Adds the type if absent, removes it if present. */
        fun toggle(
            type: SavedVideoType,
            video: VideoCard,
        ) {
            viewModelScope.launch {
                val saved = libraryRepository.observeSavedTypes(video.url).first()
                if (saved.contains(type)) {
                    libraryRepository.removeSavedVideo(type, video.url)
                } else {
                    libraryRepository.saveVideo(type, video)
                }
            }
        }

        fun addToPlaylist(
            playlistId: Long,
            video: VideoCard,
        ) {
            viewModelScope.launch {
                libraryRepository.addVideoToPlaylist(playlistId, video)
            }
        }

        private val containedPlaylistsCache = mutableMapOf<String, StateFlow<Set<Long>>>()

        /** Playlists already containing [url] — pre-checks the sheet boxes. */
        fun playlistsContaining(url: String): StateFlow<Set<Long>> =
            containedPlaylistsCache.getOrPut(url) {
                libraryRepository
                    .observePlaylistsContaining(url)
                    .stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        emptySet(),
                    )
            }

        /** Auto-commit: check adds the video, uncheck removes it. */
        fun togglePlaylist(
            playlistId: Long,
            add: Boolean,
            video: VideoCard,
        ) {
            viewModelScope.launch {
                if (add) {
                    libraryRepository.addVideoToPlaylist(playlistId, video)
                } else {
                    libraryRepository.removeVideoFromPlaylist(playlistId, video.url)
                }
            }
        }

        fun createPlaylistAndAdd(
            name: String,
            video: VideoCard,
        ) {
            viewModelScope.launch {
                val id = libraryRepository.createPlaylist(name, null)
                libraryRepository.addVideoToPlaylist(id, video)
            }
        }
    }
