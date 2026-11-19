package com.tsutsen.platformplayer.feature.channel.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.data.repository.CHANNEL_TYPE_SHORTS
import com.tsutsen.platformplayer.core.data.repository.ChannelContentPage
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(viewModelScope, SharingStarted.Lazily, settingsRepository.preferences.value.gridColumns)

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
                val contentLoading: Boolean = false,
                val contentError: String? = null,
                val playlists: List<Card> = emptyList(),
                val playlistsLoading: Boolean = false,
                val playlistsError: String? = null,
                val isSubscribed: Boolean = false,
                val notifyEnabled: Boolean = false,
                val isRefreshing: Boolean = false,
                /** Shorts tab (capability-gated by the source plugin). */
                val shortsCards: List<Card> = emptyList(),
                val shortsHasMore: Boolean = false,
                val shortsLoading: Boolean = false,
                val shortsError: String? = null,
                val hasShorts: Boolean = false,
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
                        //Header is up, content is still fetching — show the
                        //spinner in the content area instead of a blank gap.
                        _uiState.value =
                            ChannelUiState.Loaded(
                                channel = info,
                                isSubscribed = info.isSubscribed,
                                notifyEnabled = info.notifyEnabled,
                                contentLoading = true,
                                hasShorts = info.hasShorts,
                            )
                        loadInitialContents()
                    }.onFailure { e ->
                        _uiState.value = ChannelUiState.Error(e.message ?: "Failed to load channel")
                    }
            }
        }

        fun retry() {
            val current = uiState.value
            //Retry from the full-page error — the Loading state (spinner)
            //takes over until the reload resolves.
            if (current is ChannelUiState.Error) {
                val url = loadedChannelUrl
                if (url != null) {
                    loadedChannelUrl = null
                    _uiState.value = ChannelUiState.Loading
                    load(url)
                }
                return
            }
            val url = (current as? ChannelUiState.Loaded)?.channel?.url
            if (url != null) {
                loadedChannelUrl = null
                load(url)
            }
        }

        fun loadInitialContents() {
            val url = (uiState.value as? ChannelUiState.Loaded)?.channel?.url ?: return
            viewModelScope.launch {
                //Retry after a content error re-shows the spinner so the
                //press has visible feedback.
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(contentLoading = true) else it
                }
                val page = runCatching { channelRepository.loadInitialContents(url) }
                    .getOrElse { e ->
                        ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load contents")
                    }
                _uiState.update { state ->
                    if (state is ChannelUiState.Loaded) {
                        state.copy(
                            cards = page.cards,
                            hasMore = page.hasMore,
                            contentLoading = false,
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
                val page = runCatching { channelRepository.loadNextPage(url) }
                    .getOrElse { e ->
                        ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load more")
                    }
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

        /** Shorts tab first load — called when the tab is selected. */
    fun loadShortsInitial() {
        val state = uiState.value as? ChannelUiState.Loaded ?: return
        if (state.shortsCards.isNotEmpty() || state.shortsLoading) return
        val url = state.channel.url
        viewModelScope.launch {
            _uiState.update { if (it is ChannelUiState.Loaded) it.copy(shortsLoading = true) else it }
            val page = runCatching { channelRepository.loadInitialContents(url, CHANNEL_TYPE_SHORTS) }
                .getOrElse { e ->
                    ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load shorts")
                }
            _uiState.update {
                if (it is ChannelUiState.Loaded) {
                    it.copy(
                        shortsCards = page.cards,
                        shortsHasMore = page.hasMore,
                        shortsLoading = false,
                        shortsError = page.error,
                    )
                } else it
            }
        }
    }

    fun loadShortsNextPage() {
        val state = uiState.value as? ChannelUiState.Loaded ?: return
        if (state.shortsLoading || !state.shortsHasMore) return
        val url = state.channel.url
        viewModelScope.launch {
            _uiState.update { if (it is ChannelUiState.Loaded) it.copy(shortsLoading = true) else it }
            val page = runCatching { channelRepository.loadNextPage(url, CHANNEL_TYPE_SHORTS) }
                .getOrElse { e ->
                    ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load shorts")
                }
            _uiState.update {
                if (it is ChannelUiState.Loaded) {
                    it.copy(
                        shortsCards = page.cards,
                        shortsHasMore = page.hasMore,
                        shortsLoading = false,
                        shortsError = page.error,
                    )
                } else it
            }
        }
    }

    fun loadPlaylists(force: Boolean = false) {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            if (state.playlists.isNotEmpty() && !force) return
            val url = state.channel.url
            viewModelScope.launch {
                // Loading flag gates the empty state: without it the tab would
                // flash a "No playlists" placeholder for the whole load.
                _uiState.update {
                    if (it is ChannelUiState.Loaded) {
                        it.copy(playlistsLoading = true, playlistsError = null)
                    } else it
                }
                val loaded = runCatching { channelRepository.loadPlaylists(url) }
                _uiState.update {
                    if (it is ChannelUiState.Loaded) {
                        it.copy(
                            playlists = loaded.getOrElse { e -> emptyList() },
                            playlistsLoading = false,
                            playlistsError =
                                loaded.exceptionOrNull()?.let { e ->
                                    e.message ?: "Failed to load playlists"
                                },
                        )
                    } else it
                }
            }
        }

        /** Pull-to-refresh: re-fetch channel info, video feed and playlists. */
        fun refresh() {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            if (state.isRefreshing) return
            val url = state.channel.url
            viewModelScope.launch {
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(isRefreshing = true) else it
                }
                runCatching { channelRepository.getChannel(url) }
                    .onSuccess { info ->
                        _uiState.update {
                            if (it is ChannelUiState.Loaded) {
                                it.copy(
                                    channel = info,
                                    isSubscribed = info.isSubscribed,
                                    notifyEnabled = info.notifyEnabled,
                                    hasShorts = info.hasShorts,
                                )
                            } else {
                                it
                            }
                        }
                    }
                    .onFailure { e ->
                        //A failed refresh must not leave the spinner up.
                        _uiState.update {
                            if (it is ChannelUiState.Loaded) {
                                it.copy(
                                    isRefreshing = false,
                                    contentError = e.message ?: "Failed to refresh channel",
                                )
                            } else {
                                it
                            }
                        }
                        return@launch
                    }
                val page = runCatching { channelRepository.loadInitialContents(url) }
                    .getOrElse { e ->
                        ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load contents")
                    }
                _uiState.update {
                    if (it is ChannelUiState.Loaded) {
                        it.copy(
                            cards = page.cards,
                            hasMore = page.hasMore,
                            contentError = page.error,
                            isRefreshing = false,
                        )
                    } else {
                        it
                    }
                }
                loadPlaylists(force = true)
            }
        }

        fun toggleSubscription() {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            val url = state.channel.url
            val previous = state.isSubscribed
            // Optimistic flip for responsiveness; the network result reconciles
            // (a failed toggle reverts to the previous state).
            _uiState.update {
                if (it is ChannelUiState.Loaded) it.copy(isSubscribed = !previous) else it
            }
            viewModelScope.launch {
                val subscribed = runCatching { channelRepository.toggleSubscription(url) }
                    .getOrNull()
                    ?: previous
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(isSubscribed = subscribed) else it
                }
            }
        }

        fun toggleNotify() {
            val state = uiState.value as? ChannelUiState.Loaded ?: return
            val url = state.channel.url
            viewModelScope.launch {
                val enabled = channelRepository.toggleNotifications(url)
                _uiState.update {
                    if (it is ChannelUiState.Loaded) it.copy(notifyEnabled = enabled) else it
                }
            }
        }
    }
