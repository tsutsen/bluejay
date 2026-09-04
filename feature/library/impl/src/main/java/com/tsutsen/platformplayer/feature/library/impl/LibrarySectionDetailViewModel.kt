package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.impl.LibraryRepositoryImpl
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.SavedVideoType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for a library section detail screen.
 * Section title comes from the sections flow; the full item list (newest
 * first) comes from the section items flow.
 */
@HiltViewModel
class LibrarySectionDetailViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(viewModelScope, SharingStarted.Lazily, settingsRepository.preferences.value.gridColumns)

        private val _section = MutableStateFlow<LibrarySection?>(null)
        val section: StateFlow<LibrarySection?> = _section.asStateFlow()

        private val _items = MutableStateFlow<List<Card>>(emptyList())
        val items: StateFlow<List<Card>> = _items.asStateFlow()

        // The VM is activity-scoped, so without a guard every entry into the
        // screen (LaunchedEffect re-fires on recomposition) stacks two more
        // live collectors on top of the previous ones.
        private var loadedSectionId: String? = null
        private var sectionJob: Job? = null
        private var itemsJob: Job? = null

        private var removingWatched = false

        /**
         * Watch Later cleanup: removes every entry that counts as watched.
         * The items flow refreshes the list reactively once the deletes land.
         */
        fun removeWatched() {
            if (loadedSectionId != LibraryRepositoryImpl.WATCH_LATER_ID || removingWatched) return
            removingWatched = true
            viewModelScope.launch {
                libraryRepository.removeWatched(SavedVideoType.WATCH_LATER)
                removingWatched = false
            }
        }

        fun loadSection(sectionId: String) {
            if (loadedSectionId == sectionId) return
            loadedSectionId = sectionId
            sectionJob?.cancel()
            itemsJob?.cancel()
            sectionJob =
                viewModelScope.launch {
                    libraryRepository.sections.collect { sections ->
                        _section.value = sections.find { it.id == sectionId }
                    }
                }
            itemsJob =
                viewModelScope.launch {
                    libraryRepository.observeSectionItems(sectionId).collect { list ->
                        _items.value = list
                    }
                }
        }
    }
