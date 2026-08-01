package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.dao.PlaylistDao
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import com.tsutsen.platformplayer.core.database.entity.PlaylistEntity
import com.tsutsen.platformplayer.core.database.entity.PlaylistVideoEntity
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.VideoCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibraryRepository implementation.
 * Bridges to database for history, watch later, and playlists.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao
) : LibraryRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _sections = MutableStateFlow<List<LibrarySection>>(emptyList())
    override val sections: StateFlow<List<LibrarySection>> = _sections.asStateFlow()

    init {
        // Observe all data sources and combine into sections
        val combinedFlow = combine(
            historyDao.observeAll(),
            playlistDao.observeAll()
        ) { history, playlists ->
            buildSections(history, playlists)
        }
        // Collect in repository scope (singleton lives for app lifetime)
        repositoryScope.launch {
            combinedFlow.collect { sections ->
                _sections.value = sections
            }
        }
    }

    private fun buildSections(
        history: List<HistoryEntity>,
        playlists: List<PlaylistEntity>
    ): List<LibrarySection> {
        val historySection = LibrarySection(
            id = "history",
            title = "History",
            items = history.map { it.toVideoCard() },
            isLoading = false
        )

        val watchLaterSection = LibrarySection(
            id = "watch_later",
            title = "Watch Later",
            items = history.filter { it.lastPositionMs == 0L }.map { it.toVideoCard() },
            isLoading = false
        )

        val playlistsSection = LibrarySection(
            id = "playlists",
            title = "Playlists",
            items = playlists.map { it.toPlaylistCard() },
            isLoading = false
        )

        return listOf(historySection, watchLaterSection, playlistsSection)
    }

    override suspend fun loadHistory() {
        // Data is observed via init block, no explicit load needed
    }

    override suspend fun loadWatchLater() {
        // Data is observed via init block, no explicit load needed
    }

    override suspend fun loadPlaylists() {
        // Data is observed via init block, no explicit load needed
    }

    override suspend fun addToWatchLater(videoId: String) {
        // Find the history entry and set lastPositionMs to 0
        val existing = historyDao.getByUrl(videoId)
        if (existing != null) {
            historyDao.update(existing.copy(lastPositionMs = 0L))
        } else {
            // Create a new history entry for watch later
            val entity = HistoryEntity(
                contentUrl = videoId,
                title = "Watch Later",
                author = null,
                thumbnailUrl = null,
                lastPositionMs = 0L,
                totalDurationMs = 0L,
                watchedAt = System.currentTimeMillis(),
                viewedAt = System.currentTimeMillis()
            )
            historyDao.upsert(entity)
        }
    }

    override suspend fun removeFromWatchLater(videoId: String) {
        // Remove from history
        historyDao.deleteByUrl(videoId)
    }

    override suspend fun createPlaylist(name: String, description: String?): Long {
        val entity = PlaylistEntity(
            name = name,
            description = description,
            videoCount = 0
        )
        return playlistDao.insert(entity)
    }

    override suspend fun addVideoToPlaylist(playlistId: Long, videoId: String) {
        // Get the video details from history if available
        val history = historyDao.getByUrl(videoId)
        val video = PlaylistVideoEntity(
            playlistId = playlistId,
            videoOrder = playlistDao.countVideos(playlistId),
            contentUrl = videoId,
            title = history?.title ?: "Unknown",
            author = history?.author,
            thumbnailUrl = history?.thumbnailUrl
        )
        playlistDao.insertVideo(video)
        
        // Update playlist video count
        val playlist = playlistDao.getById(playlistId)
        if (playlist != null) {
            playlistDao.update(playlist.copy(videoCount = playlist.videoCount + 1))
        }
    }

    override suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String) {
        val video = playlistDao.getVideoInPlaylist(playlistId, videoId)
        if (video != null) {
            playlistDao.deleteVideo(video)
            
            // Update playlist video count
            val playlist = playlistDao.getById(playlistId)
            if (playlist != null) {
                playlistDao.update(playlist.copy(videoCount = playlist.videoCount - 1))
            }
        }
    }

    private fun HistoryEntity.toVideoCard(): VideoCard {
        return VideoCard(
            id = contentUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = author,
            durationMs = totalDurationMs.takeIf { it > 0 },
            viewCount = null,
            publishedAt = viewedAt,
            url = contentUrl
        )
    }

    private fun PlaylistEntity.toPlaylistCard(): PlaylistCard {
        return PlaylistCard(
            id = id.toString(),
            title = name,
            thumbnailUrl = thumbnailUrl,
            videoCount = videoCount,
            author = null,
            url = "playlist:$id"
        )
    }
}
