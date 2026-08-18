package com.tsutsen.platformplayer.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Search screen.
 * Merges repository results with local search history.
 */
data class SearchUiState(
    val query: String = "",
    val items: List<com.tsutsen.platformplayer.core.model.Card> = emptyList(),
    val isLoading: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    val searchHistory: List<String> = emptyList(),
)

/**
 * Search screen ViewModel.
 * Manages search queries, results, and search history.
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(viewModelScope, SharingStarted.Lazily, settingsRepository.preferences.value.gridColumns)

        val repositoryResults: StateFlow<SearchResult> =
            searchRepository.results.stateIn(
                scope = viewModelScope,
                started =
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5000),
                initialValue = SearchResult(),
            )

        // History lives in persisted settings (survives tab switches and app
        // restarts); this is the live view over it.
        val searchHistoryFlow: StateFlow<List<String>> =
            settingsRepository.preferences
                .map { it.searchHistory }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        /**
         * Perform a search with the given query.
         */
        fun search(query: String) {
            if (query.isBlank()) return
            viewModelScope.launch {
                searchRepository.search(query)
                addToHistory(query)
            }
        }

        /**
         * Load the next page of results.
         */
        fun nextPage() {
            viewModelScope.launch {
                searchRepository.nextPage()
            }
        }

        /**
         * Clear current search results.
         */
        fun clearResults() {
            viewModelScope.launch {
                searchRepository.clearResults()
            }
        }

        /**
         * Add a query to search history (deduplicated, max 10, persisted).
         */
        private suspend fun addToHistory(query: String) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return
            val current =
                settingsRepository.preferences.value.searchHistory
                    .toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            while (current.size > 10) {
                current.removeAt(current.size - 1)
            }
            settingsRepository.updateGeneral("searchHistory", current)
        }

        /**
         * Clear all search history.
         */
        fun clearHistory() {
            viewModelScope.launch {
                settingsRepository.updateGeneral("searchHistory", emptyList<String>())
            }
        }

        /**
         * Delete a single query from search history.
         */
        fun deleteFromHistory(query: String) {
            viewModelScope.launch {
                val current =
                    settingsRepository.preferences.value.searchHistory
                        .toMutableList()
                        .apply { remove(query) }
                settingsRepository.updateGeneral("searchHistory", current)
            }
        }
    }
