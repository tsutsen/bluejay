package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Library screen ViewModel.
 *
 * Section data is reactive (DAO-backed) — no explicit load calls needed.
 */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
    ) : ViewModel() {
        val sections: StateFlow<List<LibrarySection>> = libraryRepository.sections

        private val playlistStatsCache = mutableMapOf<Long, StateFlow<PlaylistStats>>()

        fun createPlaylist(name: String) {
            if (name.isBlank()) return
            viewModelScope.launch {
                libraryRepository.createPlaylist(name)
            }
        }

        /** Reactive stats (count + total duration) for a local playlist. */
        fun playlistStats(playlistId: Long): StateFlow<PlaylistStats> =
            playlistStatsCache.getOrPut(playlistId) {
                libraryRepository
                    .observePlaylistStats(playlistId)
                    .stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        PlaylistStats(videoCount = 0, totalDurationMs = 0),
                    )
            }

        /** First video url in a local playlist (play target), null if empty. */
        suspend fun getFirstVideoUrl(playlistId: Long): String? =
            libraryRepository.getFirstVideoUrl(playlistId)

        fun deletePlaylist(playlistId: Long) {
            viewModelScope.launch {
                libraryRepository.deletePlaylist(playlistId)
            }
        }
    }
