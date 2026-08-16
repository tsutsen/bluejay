package com.tsutsen.platformplayer.feature.channel.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Channel screen ViewModel.
 *
 * [ChannelUiState] mirrors the load sequence: channel info first, then
 * content pages (infinite scroll) and playlists.
 */
@HiltViewModel
class ChannelViewModel
    @Inject
    constructor(
        private val channelRepository: ChannelRepository,
    ) : ViewModel() {
        sealed interface ChannelUiState {
            data object Loading : ChannelUiState

            data class Error(
                val message: String,
            ) : ChannelUiState

            data class Loaded(
                val channel: ChannelInfo,
                val cards: List<Card> = emptyList(),
                val hasMore: Boolean = false,
                val isLoadingMore: Boolean = false,
                val contentError: String? = null,
                val playlists: List<Card> = emptyList(),
                val isSubscribed: Boolean = false,
            ) : ChannelUiState
        }

        private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
        val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

        private var loadedChannelUrl: String? = null

        fun load(channelUrl: String) {
            if (loadedChannelUrl == channelUrl) return
            loadedChannelUrl = channelUrl
            _uiState.value = ChannelUiState.Loading
            viewModelScope.launch {
                runCatching { channelRepository.getChannel(channelUrl) }
                    .onSuccess { info ->
                        _uiState.value =
                            ChannelUiState.Loaded(
                                channel = info,
                                isSubscribed = info.isSubscribed,
                            )
                        loadInitialContents()
                    }.onFailure { e ->
                        _uiState.value = ChannelUiState.Error(e.message ?: "Failed to load channel")
                    }
            }
        }

        fun retry() {
            val url = (uiState.value as? ChannelUiState.Loaded)?.channel?.url
            if (url != null) {
                loadedChannelUrl = null
                load(url)
            }
        }

        fun loadInitialContents() {
            val url = (uiState.value as? ChannelUiState.Loaded)?.channel?.url ?: return
            viewModelScope.launch {
                val page = channelRepository.loadInitialContents(url)
                _uiState.update { state ->
                    if (state is ChannelUiState.Loaded) {
                        state.copy(
                            cards = page.cards,
                            hasMore = page.hasMore,
                            contentError = page.error,
                        )
                    } else {
                        state
                    }
                }
            }
        }

        fun loadNextPage() {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            if (state.isLoadingMore || !state.hasMore) return
            val url = state.channel.url
            viewModelScope.launch {
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(isLoadingMore = true) else it
                }
                val page = channelRepository.loadNextPage(url)
                _uiState.update {
                    if (it is ChannelUiState.Loaded) {
                        it.copy(
                            cards = page.cards,
                            hasMore = page.hasMore,
                            isLoadingMore = false,
                            contentError = page.error,
                        )
                    } else {
                        it
                    }
                }
            }
        }

        fun loadPlaylists() {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            if (state.playlists.isNotEmpty()) return
            val url = state.channel.url
            viewModelScope.launch {
                val playlists = channelRepository.loadPlaylists(url)
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(playlists = playlists) else it
                }
            }
        }

        fun toggleSubscription() {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            val url = state.channel.url
            viewModelScope.launch {
                val subscribed = channelRepository.toggleSubscription(url)
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(isSubscribed = subscribed) else it
                }
            }
        }
    }
