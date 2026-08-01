package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.LibrarySection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for a library section detail screen.
 * Loads and exposes a single section by id from the LibraryRepository.
 */
@HiltViewModel
class LibrarySectionDetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _section = MutableStateFlow<LibrarySection?>(null)
    val section: StateFlow<LibrarySection?> = _section.asStateFlow()

    fun loadSection(sectionId: String) {
        viewModelScope.launch {
            _section.update { current ->
                current?.copy(isLoading = true)
            }
            // Observe repository sections and find the matching one
            libraryRepository.sections.collect { sections ->
                val target = sections.find { it.id == sectionId }
                if (target != null) {
                    _section.value = target.copy(isLoading = false)
                }
            }
        }
    }
}
