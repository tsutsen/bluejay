package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Library screen.
 * Manages library sections (History, Watch Later, Playlists) and their state.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val sections: StateFlow<List<LibrarySection>> = libraryRepository.sections
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadAllSections()
    }

    private fun loadAllSections() {
        viewModelScope.launch {
            libraryRepository.loadHistory()
            libraryRepository.loadWatchLater()
            libraryRepository.loadPlaylists()
        }
    }
}
