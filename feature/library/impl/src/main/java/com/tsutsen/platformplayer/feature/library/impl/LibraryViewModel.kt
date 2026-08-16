package com.tsutsen.platformplayer.feature.library.impl

import androidx.lifecycle.ViewModel
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.LibrarySection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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
    }
