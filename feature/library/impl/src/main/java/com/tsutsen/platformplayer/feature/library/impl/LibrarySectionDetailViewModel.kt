package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : ViewModel() {
        private val _section = MutableStateFlow<LibrarySection?>(null)
        val section: StateFlow<LibrarySection?> = _section.asStateFlow()

        private val _items = MutableStateFlow<List<Card>>(emptyList())
        val items: StateFlow<List<Card>> = _items.asStateFlow()

        fun loadSection(sectionId: String) {
            viewModelScope.launch {
                libraryRepository.sections.collect { sections ->
                    _section.value = sections.find { it.id == sectionId }
                }
            }
            viewModelScope.launch {
                libraryRepository.observeSectionItems(sectionId).collect { list ->
                    _items.value = list
                }
            }
        }
    }
