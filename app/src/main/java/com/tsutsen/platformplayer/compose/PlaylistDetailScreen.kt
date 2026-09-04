package com.tsutsen.platformplayer.compose

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlaylistRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.designsystem.component.ContentCard
import com.tsutsen.platformplayer.core.designsystem.component.ContainerLayout
import com.tsutsen.platformplayer.core.designsystem.component.ErrorState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoContainer
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.PlaylistInfo
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.library.impl.VideoOptionsSheetHost
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard

/**
 * Playlist detail screen: playlist header + video list with pagination.
 * Mirrors the channel screen's load pattern (info first, then pages).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistUrl: String,
    onBack: () -> Unit,
    navigator: Navigator,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val gridColumns by viewModel.gridColumns.collectAsState()
    val watchStates by playerViewModel.watchStates.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isWide = rememberIsWide()
    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }

    LaunchedEffect(playlistUrl) {
        viewModel.load(playlistUrl)
    }

    val loaded = uiState as? PlaylistDetailViewModel.UiState.Loaded

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = {
                loaded?.info?.let { info ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // AsyncImage spins forever on a null url — local
                        // playlists have no thumbnail, so fall back to an
                        // icon (same pattern as PlaylistCardView).
                        if (!info.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                url = info.thumbnail,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(
                            modifier =
                                Modifier
                                    .padding(start = Tokens.SpaceMd)
                                    .weight(1f),
                        ) {
                            Text(
                                text = info.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle =
                                buildList {
                                    info.videoCount?.let { add("$it videos") }
                                    info.author?.let { add(it) }
                                }.joinToString(" • ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } ?: Text("Playlist")
            },
            leading = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
        )

        when (val state = uiState) {
            is PlaylistDetailViewModel.UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize())
            }

            is PlaylistDetailViewModel.UiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                )
            }

            is PlaylistDetailViewModel.UiState.Loaded -> {
                if (state.cards.isEmpty()) {
                    val contentError = state.contentError
                    if (contentError != null) {
                        ErrorState(
                            message = contentError,
                            onRetry = { viewModel.loadInitialVideos() },
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                } else {
                    ContentCard(modifier = Modifier.fillMaxSize()) {
                        VideoContainer(
                        items = state.cards,
                        layout = if (isWide) ContainerLayout.Grid(gridColumns) else ContainerLayout.List,
                        isLoading = false,
                        hasMorePages = state.hasMore,
                        onCardClick = { card ->
                            if (card is CoreVideoCard) {
                                playerViewModel.play(card)
                            }
                        },
                        onLoadMore = { viewModel.loadNextPage() },
                    ) { card ->
                        if (card is CoreVideoCard) {
                            VideoCard(
                                card = card,
                                onClick = { playerViewModel.play(card) },
                                onLongClick = { optionsCard = card },
                                watchProgress = watchStates[card.url]?.takeIf { !it.isWatched }?.progress,
                                isWatched = watchStates[card.url]?.isWatched ?: false,
                            )
                        } else {
                            Box(Modifier.height(1.dp))
                        }
                    }
                    }
                }
            }
        }
    }

    optionsCard?.let { card ->
        VideoOptionsSheetHost(
            video = card,
            onDismiss = { optionsCard = null },
            onPlay = { playerViewModel.play(card) },
            onGoToChannel = { navigator.navigateToChannel(it) },
        )
    }
}

private const val LOCAL_PLAYLIST_PREFIX = "playlist:"

/**
 * Playlist detail ViewModel.
 */
@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Live grid columns from the single config — grids reflow when it changes. */
        val gridColumns: StateFlow<Int> =
            settingsRepository.preferences
                .map { it.gridColumns }
                .stateIn(viewModelScope, SharingStarted.Lazily, settingsRepository.preferences.value.gridColumns)

        sealed interface UiState {
            data object Loading : UiState

            data class Error(
                val message: String,
            ) : UiState

            data class Loaded(
                val info: PlaylistInfo,
                val cards: List<Card> = emptyList(),
                val hasMore: Boolean = false,
                val isLoadingMore: Boolean = false,
                val contentError: String? = null,
            ) : UiState
        }

        private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private var loadedPlaylistUrl: String? = null

        fun load(playlistUrl: String) {
            if (loadedPlaylistUrl == playlistUrl) return
            loadedPlaylistUrl = playlistUrl
            _uiState.value = UiState.Loading
            if (playlistUrl.startsWith(LOCAL_PLAYLIST_PREFIX)) {
                val id =
                    playlistUrl.removePrefix(LOCAL_PLAYLIST_PREFIX).toLongOrNull()
                        ?: error("Invalid local playlist url: $playlistUrl")
                // Reactive: adds/removes from the options sheet land while
                // the screen is open (a one-shot load went stale whenever
                // the screen survived in the back stack).
                viewModelScope.launch {
                    libraryRepository
                        .observeLocalPlaylist(id)
                        .filterNotNull()
                        .collect { (info, cards) ->
                            _uiState.value = UiState.Loaded(info = info, cards = cards, hasMore = false)
                        }
                }
                return
            }
            viewModelScope.launch {
                runCatching { playlistRepository.getPlaylist(playlistUrl) }
                    .onSuccess { info ->
                        _uiState.value = UiState.Loaded(info = info)
                        loadInitialVideos()
                    }.onFailure { e ->
                        _uiState.value = UiState.Error(e.message ?: "Failed to load playlist")
                    }
            }
        }

        fun retry() {
            val url = (uiState.value as? UiState.Loaded)?.info?.url
            if (url != null) {
                loadedPlaylistUrl = null
                load(url)
            } else {
                _uiState.value = UiState.Loading
                loadedPlaylistUrl?.let { load(it) }
            }
        }

        fun loadInitialVideos() {
            val url = (uiState.value as? UiState.Loaded)?.info?.url ?: return
            viewModelScope.launch {
                val page = playlistRepository.loadInitialVideos(url)
                _uiState.update { state ->
                    if (state is UiState.Loaded) {
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
            val state = uiState.value as? UiState.Loaded ?: return
            if (state.isLoadingMore || !state.hasMore) return
            val url = state.info.url
            viewModelScope.launch {
                _uiState.update {
                    if (it is UiState.Loaded) it.copy(isLoadingMore = true) else it
                }
                val page = playlistRepository.loadNextPage(url)
                _uiState.update {
                    if (it is UiState.Loaded) {
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
    }
