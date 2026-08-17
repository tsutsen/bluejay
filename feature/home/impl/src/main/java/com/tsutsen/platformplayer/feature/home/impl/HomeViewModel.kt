package com.tsutsen.platformplayer.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.FeedPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MVI state for the Home feed screen.
 */
sealed interface HomeUiState {
    data class Loaded(
        val items: List<Card>,
        val isLoading: Boolean,
        val hasMorePages: Boolean,
        val error: String?,
    ) : HomeUiState

    data object Initial : HomeUiState

    data object Loading : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}

/**
 * ViewModel for the Home feed.
 * Bridges between HomeRepository (data layer) and HomeScreen (UI).
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val homeRepository: HomeRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(viewModelScope, SharingStarted.Lazily, settingsRepository.preferences.value.gridColumns)

        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            // Observe repository feed and map to UiState
            viewModelScope.launch {
                homeRepository.feed
                    .collect { feedPage ->
                        _uiState.value =
                            HomeUiState.Loaded(
                                items = feedPage.items,
                                isLoading = feedPage.isLoading,
                                hasMorePages = feedPage.hasMorePages,
                                error = feedPage.error,
                            )
                    }
            }
            // Load initial feed
            viewModelScope.launch {
                loadInitial()
            }
        }

        fun loadInitial() {
            viewModelScope.launch {
                _uiState.value = HomeUiState.Loading
                homeRepository.loadInitial()
            }
        }

        fun loadNextPage() {
            viewModelScope.launch {
                homeRepository.loadNextPage()
            }
        }

        fun refresh() {
            viewModelScope.launch {
                homeRepository.refresh()
            }
        }

        fun retry() {
            viewModelScope.launch {
                homeRepository.refresh()
            }
        }

        fun filterByTag(tag: String) {
            viewModelScope.launch {
                homeRepository.filterByTag(tag)
            }
        }

        fun filterByAuthor(authorId: String) {
            viewModelScope.launch {
                homeRepository.filterByAuthor(authorId)
            }
        }
    }
