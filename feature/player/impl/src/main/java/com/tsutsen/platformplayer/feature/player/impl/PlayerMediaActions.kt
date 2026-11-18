package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadQuality
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.model.toContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media-library actions for the player: like/dislike/watch-later/favourite
 * toggles, channel subscribe, downloads and playlist membership, plus the
 * per-video tracking of saved types, containing playlists and subscription
 * state. Extracted from [PlayerViewModel]; the ViewModel keeps thin
 * delegating methods.
 */
internal class PlayerMediaActions(
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val channelRepository: ChannelRepository,
    private val downloadsRepository: DownloadsRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
) {
    /** Live saved types for the current video (drives like/dislike buttons). */
    @Volatile
    internal var currentSavedTypes: Set<SavedVideoType> = emptySet()
    private var savedTypesUrl: String? = null
    private var savedTypesJob: Job? = null

    /** Live saved types as a flow — for the options sheet host. */
    private val _savedTypes = MutableStateFlow<Set<SavedVideoType>>(emptySet())
    val savedTypes: StateFlow<Set<SavedVideoType>> = _savedTypes.asStateFlow()

    /** Playlists containing the current video (options sheet checkboxes). */
    private val _containedPlaylists = MutableStateFlow<Set<Long>>(emptySet())
    val containedPlaylists: StateFlow<Set<Long>> = _containedPlaylists.asStateFlow()
    private var containedUrl: String? = null
    private var containedJob: Job? = null

    /** Channel subscription state for the current author (lazy-fetched). */
    @Volatile
    private var isSubscribedUrl: String? = null

    @Volatile
    private var isSubscribedCache: Boolean = false

    /**
     * Bumped on every optimistic subscribe/unsubscribe so an in-flight
     * lazy-fetch (or an earlier toggle) can't clobber the UI with a
     * pre-toggle value.
     */
    @Volatile
    private var subscribeGeneration: Int = 0

    /** Synchronously readable subscription cache for the UI rebuild. */
    val isSubscribedCached: Boolean
        get() = isSubscribedUrl?.let { isSubscribedCache } ?: false

    /**
     * Retarget the per-video observers (saved types, containing playlists,
     * subscription state) when the current video changes. Called from the
     * ViewModel's main state collector on every repository tick, in the same
     * order the logic originally ran there.
     */
    fun onVideoChanged(video: ContentItem?) {
        // Track playback history. The player's real duration
        // backfills history + library rows that stored none,
        // so library cards show a duration after one play.
        if (video != null && video.url != savedTypesUrl) {
            savedTypesUrl = video.url
            savedTypesJob?.cancel()
            savedTypesJob =
                scope.launch {
                    libraryRepository
                        .observeSavedTypes(video.url)
                        .collect { types ->
                            currentSavedTypes = types
                            _savedTypes.value = types
                            uiState.update {
                                (it as? PlayerUiState.Loaded)?.let { s ->
                                    s.copy(
                                        isLiked = SavedVideoType.LIKED in types,
                                        isDisliked =
                                            SavedVideoType.DISLIKED in types,
                                    )
                                }
                                    ?: it
                            }
                        }
                }
        }
        // Playlists containing this video (options sheet).
        if (video != null && video.url != containedUrl) {
            containedUrl = video.url
            containedJob?.cancel()
            containedJob =
                scope.launch {
                    libraryRepository
                        .observePlaylistsContaining(video.url)
                        .collect { ids ->
                            _containedPlaylists.value = ids
                        }
                }
        }
        // Channel subscription state for the current author.
        val channelUrl = video?.author?.url?.takeIf { it.isNotEmpty() }
        if (channelUrl != null && channelUrl != isSubscribedUrl) {
            isSubscribedUrl = channelUrl
            isSubscribedCache = false
            val gen = subscribeGeneration
            scope.launch {
                val subscribed =
                    withContext(Dispatchers.IO) {
                        channelRepository.isSubscribed(channelUrl)
                    }
                if (isSubscribedUrl == channelUrl && gen == subscribeGeneration) {
                    isSubscribedCache = subscribed
                    uiState.update {
                        (it as? PlayerUiState.Loaded)?.copy(
                            isSubscribedChannel = subscribed,
                        )
                            ?: it
                    }
                }
            }
        }
    }

    /** Toggle the current video in/out of the library's Liked list. */
    fun toggleLike(liked: Boolean) {
        toggleSaveType(SavedVideoType.LIKED, liked)
    }

    /** Toggle the current video in/out of the library's Disliked list. */
    fun toggleDislike(disliked: Boolean) {
        toggleSaveType(SavedVideoType.DISLIKED, disliked)
    }

    /** Toggle the current video in/out of the library's Watch Later list. */
    fun toggleWatchLater(saved: Boolean) {
        toggleSaveType(SavedVideoType.WATCH_LATER, saved)
    }

    /** Toggle the current video in/out of the library's Favourites list. */
    fun toggleFavourite(saved: Boolean) {
        toggleSaveType(SavedVideoType.FAVOURITE, saved)
    }

    /**
     * Toggle the current video in/out of a saved list. Like and dislike are
     * mutually exclusive: adding one removes the other.
     */
    private fun toggleSaveType(
        type: SavedVideoType,
        isSaved: Boolean,
    ) {
        scope.launch {
            val video = playerRepository.playerState.value.currentVideo ?: return@launch
            if (isSaved) {
                libraryRepository.removeSavedVideo(type, video.url)
            } else {
                when (type) {
                    SavedVideoType.LIKED -> {
                        libraryRepository.removeSavedVideo(
                            SavedVideoType.DISLIKED,
                            video.url,
                        )
                    }

                    SavedVideoType.DISLIKED -> {
                        libraryRepository.removeSavedVideo(SavedVideoType.LIKED, video.url)
                    }

                    else -> {
                        Unit
                    }
                }
                libraryRepository.saveVideo(type, video.toVideoCard())
            }
        }
    }

    /**
     * Subscribe/unsubscribe the current video's channel.
     *
     * Optimistic: the button flips immediately from the synchronous
     * local store state; the backend transaction runs behind it and its
     * result (the source of truth) is written back — a failed
     * subscription no-ops in the repository, which rolls the UI back.
     */
    fun subscribeChannel() {
        val url =
            playerRepository.playerState.value.currentVideo
                ?.author
                ?.url
                ?.takeIf { it.isNotEmpty() } ?: return
        // Local read — synchronous, no IO.
        val optimistic = !channelRepository.isSubscribed(url)
        isSubscribedUrl = url
        isSubscribedCache = optimistic
        subscribeGeneration++
        uiState.update {
            (it as? PlayerUiState.Loaded)?.copy(isSubscribedChannel = optimistic) ?: it
        }
        val gen = subscribeGeneration
        scope.launch {
            val subscribed =
                withContext(Dispatchers.IO) {
                    channelRepository.toggleSubscription(url)
                }
            if (isSubscribedUrl == url && gen == subscribeGeneration) {
                isSubscribedCache = subscribed
                uiState.update {
                    (it as? PlayerUiState.Loaded)?.copy(isSubscribedChannel = subscribed) ?: it
                }
            }
        }
    }

    /** Start downloading the current video (null quality = engine default). */
    fun startDownload(quality: DownloadQuality? = null) {
        val url =
            playerRepository.playerState.value.currentVideo
                ?.url ?: return
        scope.launch { downloadsRepository.startDownload(url, quality) }
    }

    /** Cancel the current video's download. */
    fun cancelDownload() {
        val url =
            playerRepository.playerState.value.currentVideo
                ?.url ?: return
        scope.launch { downloadsRepository.cancelDownload(url) }
    }

    /** Delete the current video's downloaded copy. */
    fun deleteDownload() {
        val url =
            playerRepository.playerState.value.currentVideo
                ?.url ?: return
        scope.launch { downloadsRepository.deleteDownload(url) }
    }

    /** Add [video] to a local playlist (options-sheet picker). */
    fun addToPlaylist(
        video: ContentItem,
        playlistId: Long,
    ) {
        scope.launch {
            libraryRepository.addVideoToPlaylist(
                playlistId,
                VideoCard(
                    id = video.id,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    author = video.author?.name,
                    authorUrl = video.author?.url,
                    durationMs = video.durationMs,
                    viewCount = video.viewCount,
                    publishedAt = video.publishedAt,
                    url = video.url,
                ),
            )
        }
    }

    /** Add/remove the current video in/out of a local playlist (sheet checkbox). */
    fun togglePlaylistMembership(
        playlistId: Long,
        add: Boolean,
    ) {
        scope.launch {
            val video = playerRepository.playerState.value.currentVideo ?: return@launch
            if (add) {
                libraryRepository.addVideoToPlaylist(playlistId, video.toVideoCard())
            } else {
                libraryRepository.removeVideoFromPlaylist(playlistId, video.url)
            }
        }
    }

    /** Create a new playlist and add the current video to it. */
    fun createPlaylistAndAdd(name: String) {
        scope.launch {
            val video = playerRepository.playerState.value.currentVideo ?: return@launch
            val id = libraryRepository.createPlaylist(name)
            libraryRepository.addVideoToPlaylist(id, video.toVideoCard())
        }
    }

    private fun ContentItem.toVideoCard() =
        VideoCard(
            id = id,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = author?.name,
            authorUrl = author?.url?.takeIf { it.isNotEmpty() },
            durationMs = durationMs,
            viewCount = viewCount,
            publishedAt = publishedAt,
            url = url,
        )
}
