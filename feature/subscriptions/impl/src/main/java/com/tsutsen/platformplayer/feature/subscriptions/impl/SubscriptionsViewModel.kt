package com.tsutsen.platformplayer.feature.subscriptions.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.model.SubscriptionFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Subscriptions screen.
 */
sealed interface SubscriptionsUiState {
    object Loading : SubscriptionsUiState

    data class Success(
        val items: List<com.tsutsen.platformplayer.core.model.Card> = emptyList(),
        val creators: List<com.tsutsen.platformplayer.core.model.SubscriptionCreator> = emptyList(),
        val activeCreatorId: String? = null,
        val filterWatched: Boolean = true,
        val filterContinue: Boolean = false,
        val filterVideo: Boolean = true,
        val filterStreams: Boolean = false,
        val sourceFilters: Map<String, Boolean> = emptyMap(),
        val isLoading: Boolean = false,
        val hasMorePages: Boolean = false,
        val error: String? = null,
    ) : SubscriptionsUiState

    data class Error(
        val message: String,
    ) : SubscriptionsUiState
}

@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val subscriptionRepository: SubscriptionRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(viewModelScope, SharingStarted.Lazily, settingsRepository.preferences.value.gridColumns)

        private val _uiState = MutableStateFlow<SubscriptionsUiState>(SubscriptionsUiState.Loading)
        val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                subscriptionRepository.feed.collect { feed ->
                    _uiState.value =
                        SubscriptionsUiState.Success(
                            items = feed.items,
                            creators = feed.creators,
                            activeCreatorId = feed.activeCreatorId,
                            filterWatched = feed.filterWatched,
                            filterContinue = feed.filterContinue,
                            filterVideo = feed.filterVideo,
                            filterStreams = feed.filterStreams,
                            sourceFilters = feed.sourceFilters,
                            isLoading = feed.isLoading,
                            hasMorePages = feed.hasMorePages,
                            error = feed.error,
                        )
                }
            }
            viewModelScope.launch {
                subscriptionRepository.loadCreators()
            }
        }

        fun refresh() {
            viewModelScope.launch { subscriptionRepository.refresh() }
        }

        fun loadMore() {
            viewModelScope.launch { subscriptionRepository.loadMore() }
        }

        fun selectCreator(creatorId: String?) {
            viewModelScope.launch { subscriptionRepository.selectCreator(creatorId) }
        }

        fun toggleWatched() {
            viewModelScope.launch { subscriptionRepository.toggleWatched() }
        }

        fun toggleContinue() {
            viewModelScope.launch { subscriptionRepository.toggleContinue() }
        }

        fun toggleVideo() {
            viewModelScope.launch { subscriptionRepository.toggleVideo() }
        }

        fun toggleStreams() {
            viewModelScope.launch { subscriptionRepository.toggleStreams() }
        }

        fun toggleSourceFilter(sourceId: String) {
            viewModelScope.launch { subscriptionRepository.toggleSourceFilter(sourceId) }
        }
    }
