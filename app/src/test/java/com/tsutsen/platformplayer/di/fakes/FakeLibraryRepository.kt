package com.tsutsen.platformplayer.di.fakes

import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistInfo
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.PlaylistStats
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory [LibraryRepository]: saved-type sets and playlists are real state,
 * everything else is an explicit unsupported stub so tests fail loudly if a
 * consumer starts relying on an unimplemented member.
 */
class FakeLibraryRepository : LibraryRepository {
    private val savedTypes = MutableStateFlow<Map<String, Set<SavedVideoType>>>(emptyMap())
    private val playlistsState = MutableStateFlow<Map<Long, Pair<PlaylistInfo, List<Card>>>>(emptyMap())
    private var nextPlaylistId = 1L

    override val sections: MutableStateFlow<List<LibrarySection>> = MutableStateFlow(emptyList())
    override val playlists: MutableStateFlow<List<PlaylistOption>> = MutableStateFlow(emptyList())

    override fun observeSectionItems(sectionId: String): Flow<List<Card>> = flowOf(emptyList())
    override fun observeLocalPlaylist(playlistId: Long): Flow<Pair<PlaylistInfo, List<Card>>?> =
        playlistsState.map { it[playlistId] }
    override fun observeSavedTypes(url: String): Flow<Set<SavedVideoType>> =
        savedTypes.map { it[url] ?: emptySet() }
    override fun observePlaylistsContaining(url: String): Flow<Set<Long>> = flowOf(emptySet())

    override suspend fun saveVideo(type: SavedVideoType, video: VideoCard) {
        savedTypes.value = savedTypes.value + (video.url to (savedTypes.value[video.url] ?: emptySet()) + type)
    }

    override suspend fun backfillDuration(contentUrl: String, durationMs: Long) {}
    override suspend fun removeWatched(type: SavedVideoType): Int = 0
    override suspend fun removeSavedVideo(type: SavedVideoType, url: String) {
        savedTypes.value = savedTypes.value + (url to (savedTypes.value[url] ?: emptySet()) - type)
    }

    override suspend fun deleteFromHistory(url: String) {}

    override suspend fun createPlaylist(name: String, description: String?): Long {
        val id = nextPlaylistId++
        playlistsState.value = playlistsState.value + (id to (PlaylistInfo("playlist:$id", name, videoCount = 0) to emptyList()))
        return id
    }

    override suspend fun addVideoToPlaylist(playlistId: Long, video: VideoCard) {}
    override suspend fun removeVideoFromPlaylist(playlistId: Long, url: String) {}
    override fun observePlaylistStats(playlistId: Long): Flow<PlaylistStats> = flowOf(PlaylistStats(0, 0))
    override suspend fun getFirstVideoUrl(playlistId: Long): String? = playlistsState.value[playlistId]?.second?.firstOrNull()?.let { (it as? VideoCard)?.url }
    override suspend fun deletePlaylist(playlistId: Long) {
        playlistsState.value = playlistsState.value - playlistId
    }
}
