package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    ) : ViewModel() {
        private val savedTypesCache = mutableMapOf<String, StateFlow<Set<SavedVideoType>>>()

        private val _downloadMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        /** One-shot feedback for download actions (shown as a toast). */
        val downloadMessage: StateFlow<String?> = _downloadMessage

        fun consumeDownloadMessage() {
            _downloadMessage.value = null
        }

        fun download(video: VideoCard) {
            viewModelScope.launch {
                val error = downloadsRepository.startDownload(video.url)
                _downloadMessage.value =
                    if (error == null) "Download started" else error
            }
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
