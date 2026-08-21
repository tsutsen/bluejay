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
    data class Success(
        val items: List<com.tsutsen.platformplayer.core.model.Card> = emptyList(),
        val creators: List<com.tsutsen.platformplayer.core.model.SubscriptionCreator> = emptyList(),
        val activeCreatorId: String? = null,
        val filterStarted: Boolean = false,
        val filterWatched: Boolean = false,
        val filterVideo: Boolean = true,
        val filterStreams: Boolean = false,
        val isLoading: Boolean = false,
        val hasMorePages: Boolean = false,
        val error: String? = null,
        // True only for a deliberate refresh (pull / retry) — the spinner
        // must NOT spin while "load more" prefetches the next page.
        val isRefreshing: Boolean = false,
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

        // First frame: the feed flow is already loading, so show the
        // content layout (with its pull-to-refresh spinner) from the start
        // instead of a blank screen.
        private val _uiState =
            MutableStateFlow<SubscriptionsUiState>(
                SubscriptionsUiState.Success(isLoading = true),
            )
        val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)

        init {
            viewModelScope.launch {
                subscriptionRepository.feed.collect { feed ->
                    if (!feed.isLoading) _isRefreshing.value = false
                    _uiState.value =
                        SubscriptionsUiState.Success(
                            items = feed.items,
                            creators = feed.creators,
                            activeCreatorId = feed.activeCreatorId,
                            filterStarted = feed.filterStarted,
                            filterWatched = feed.filterWatched,
                            filterVideo = feed.filterVideo,
                            filterStreams = feed.filterStreams,
                            isLoading = feed.isLoading,
                            hasMorePages = feed.hasMorePages,
                            error = feed.error,
                            isRefreshing = _isRefreshing.value,
                        )
                }
            }
            viewModelScope.launch {
                subscriptionRepository.loadCreators()
            }
        }

        fun refresh() {
            _isRefreshing.value = true
            viewModelScope.launch { subscriptionRepository.refresh() }
        }

        fun loadMore() {
            viewModelScope.launch { subscriptionRepository.loadMore() }
        }

        fun selectCreator(creatorId: String?) {
            viewModelScope.launch { subscriptionRepository.selectCreator(creatorId) }
        }

        fun toggleStarted() {
            viewModelScope.launch { subscriptionRepository.toggleStarted() }
        }

        fun toggleWatched() {
            viewModelScope.launch { subscriptionRepository.toggleWatched() }
        }

        fun toggleVideo() {
            viewModelScope.launch { subscriptionRepository.toggleVideo() }
        }

        fun toggleStreams() {
            viewModelScope.launch { subscriptionRepository.toggleStreams() }
        }
    }
