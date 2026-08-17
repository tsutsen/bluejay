package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.database.dao.PlaylistDao
import com.tsutsen.platformplayer.core.database.dao.SavedVideoDao
import com.tsutsen.platformplayer.core.database.entity.PlaylistEntity
import com.tsutsen.platformplayer.core.database.entity.PlaylistVideoEntity
import com.tsutsen.platformplayer.core.database.entity.SavedVideoEntity
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.PlaylistInfo
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibraryRepository implementation.
 * Sections are live views over SavedVideoDao + PlaylistDao (reactive, no loads).
 */
@Singleton
class LibraryRepositoryImpl
    @Inject
    constructor(
        private val savedVideoDao: SavedVideoDao,
        private val playlistDao: PlaylistDao,
    ) : LibraryRepository {
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _sections = MutableStateFlow<List<LibrarySection>>(emptyList())
        override val sections: StateFlow<List<LibrarySection>> = _sections.asStateFlow()

        private val _playlists = MutableStateFlow<List<PlaylistOption>>(emptyList())
        override val playlists: StateFlow<List<PlaylistOption>> = _playlists.asStateFlow()

        init {
            val combinedFlow =
                combine(
                    savedVideoDao.observeByType(SavedVideoType.WATCH_LATER),
                    savedVideoDao.observeByType(SavedVideoType.LIKED),
                    savedVideoDao.observeByType(SavedVideoType.FAVOURITE),
                    playlistDao.observeAll(),
                ) { watchLater, liked, favourite, playlists ->
                    listOf(
                        buildSection(WATCH_LATER_ID, "Watch Later", watchLater.map { it.toVideoCard() }),
                        buildSection(LIKED_ID, "Liked", liked.map { it.toVideoCard() }),
                        buildSection(FAVOURITE_ID, "Favourites", favourite.map { it.toVideoCard() }),
                        buildSection(PLAYLISTS_ID, "Playlists", playlists.map { it.toPlaylistCard() }),
                    )
                }
            repositoryScope.launch {
                combinedFlow.collect {
                    _sections.value = it
                    _playlists.value =
                        it
                            .firstOrNull { section -> section.id == PLAYLISTS_ID }
                            ?.items
                            ?.filterIsInstance<PlaylistCard>()
                            ?.map { card ->
                                PlaylistOption(
                                    id = card.id.toLongOrNull() ?: return@map null,
                                    name = card.title,
                                )
                            }?.filterNotNull()
                            ?: emptyList()
                }
            }
        }

        override fun observeSavedTypes(url: String): Flow<Set<SavedVideoType>> =
            savedVideoDao.observeTypes(url).map { types -> types.toSet() }

        override fun observeSectionItems(sectionId: String): Flow<List<Card>> =
            when (sectionId) {
                WATCH_LATER_ID -> savedVideoDao.observeByType(SavedVideoType.WATCH_LATER).map { list -> list.map { it.toVideoCard() } }
                LIKED_ID -> savedVideoDao.observeByType(SavedVideoType.LIKED).map { list -> list.map { it.toVideoCard() } }
                FAVOURITE_ID -> savedVideoDao.observeByType(SavedVideoType.FAVOURITE).map { list -> list.map { it.toVideoCard() } }
                PLAYLISTS_ID -> playlistDao.observeAll().map { list -> list.map { it.toPlaylistCard() } }
                else -> MutableStateFlow<List<Card>>(emptyList())
            }

        override suspend fun getLocalPlaylist(playlistId: Long): PlaylistInfo? =
            withContext(Dispatchers.IO) {
                playlistDao.getById(playlistId)?.let {
                    PlaylistInfo(
                        url = "playlist:$playlistId",
                        name = it.name,
                        thumbnail = it.thumbnailUrl,
                        videoCount = it.videoCount,
                    )
                }
            }

        override suspend fun getLocalPlaylistVideos(playlistId: Long): List<Card> =
            withContext(Dispatchers.IO) {
                playlistDao
                    .getVideosPaginated(playlistId, Int.MAX_VALUE, 0)
                    .map { it.toVideoCard() }
            }

        private fun PlaylistVideoEntity.toVideoCard(): VideoCard =
            VideoCard(
                id = contentUrl,
                title = title,
                thumbnailUrl = thumbnailUrl,
                author = author,
                durationMs = null,
                viewCount = null,
                publishedAt = addedAt,
                url = contentUrl,
            )

        override suspend fun saveVideo(
            type: SavedVideoType,
            video: VideoCard,
        ) {
            savedVideoDao.upsert(
                SavedVideoEntity(
                    contentUrl = video.url,
                    type = type,
                    title = video.title,
                    author = video.author,
                    thumbnailUrl = video.thumbnailUrl,
                ),
            )
        }

        override suspend fun removeSavedVideo(
            type: SavedVideoType,
            url: String,
        ) {
            savedVideoDao.deleteByType(url, type)
        }

        override suspend fun createPlaylist(
            name: String,
            description: String?,
        ): Long {
            val entity =
                PlaylistEntity(
                    name = name,
                    description = description,
                    videoCount = 0,
                )
            return playlistDao.insert(entity)
        }

        override suspend fun addVideoToPlaylist(
            playlistId: Long,
            video: VideoCard,
        ) {
            playlistDao.insertVideo(
                PlaylistVideoEntity(
                    playlistId = playlistId,
                    videoOrder = playlistDao.countVideos(playlistId),
                    contentUrl = video.url,
                    title = video.title,
                    author = video.author,
                    thumbnailUrl = video.thumbnailUrl,
                ),
            )
            val playlist = playlistDao.getById(playlistId)
            if (playlist != null) {
                playlistDao.update(playlist.copy(videoCount = playlist.videoCount + 1))
            }
        }

        override suspend fun removeVideoFromPlaylist(
            playlistId: Long,
            url: String,
        ) {
            val video = playlistDao.getVideoInPlaylist(playlistId, url)
            if (video != null) {
                playlistDao.deleteVideo(video)
                val playlist = playlistDao.getById(playlistId)
                if (playlist != null) {
                    playlistDao.update(playlist.copy(videoCount = (playlist.videoCount - 1).coerceAtLeast(0)))
                }
            }
        }

        private fun buildSection(
            id: String,
            title: String,
            items: List<Card>,
        ): LibrarySection =
            LibrarySection(
                id = id,
                title = title,
                items = items.take(LibraryRepository.SECTION_ITEM_LIMIT),
                totalCount = items.size,
                hasMore = items.size > LibraryRepository.SECTION_ITEM_LIMIT,
            )

        private fun SavedVideoEntity.toVideoCard(): VideoCard =
            VideoCard(
                id = contentUrl,
                title = title,
                thumbnailUrl = thumbnailUrl,
                author = author,
                durationMs = null,
                viewCount = null,
                publishedAt = addedAt,
                url = contentUrl,
            )

        private fun PlaylistEntity.toPlaylistCard(): PlaylistCard =
            PlaylistCard(
                id = id.toString(),
                title = name,
                thumbnailUrl = thumbnailUrl,
                videoCount = videoCount,
                author = null,
                url = "playlist:$id",
            )

        companion object {
            const val WATCH_LATER_ID = "watch_later"
            const val LIKED_ID = "liked"
            const val FAVOURITE_ID = "favourite"
            const val PLAYLISTS_ID = "playlists"
        }
    }
