package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.data.repository.ChannelContentPage
import com.tsutsen.platformplayer.core.data.repository.PlaylistRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.PlaylistInfo
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaylistRepository implementation that bridges to the engine
 * (StatePlatform). Reuses Stage 1's EngineCardMapper and PagerFlow —
 * no duplicate mapping/pagination logic.
 */
@Singleton
class EnginePlaylistRepositoryImpl
    @Inject
    constructor() : PlaylistRepository {
        private val videoFlows = mutableMapOf<String, PagerFlow<IPlatformVideo, Card>>()

        override suspend fun getPlaylist(url: String): PlaylistInfo =
            withContext(Dispatchers.IO) {
                val details = clientFor(url).getPlaylist(url)
                PlaylistInfo(
                    url = details.url,
                    name = details.name,
                    thumbnail = details.thumbnail,
                    videoCount = details.videoCount.takeIf { it > 0 },
                    author = details.author.name,
                )
            }

        // Pager construction and page loads execute source-plugin JS, which
        // the V8 engine refuses to run on the main thread — so every engine
        // call here is dispatched to IO ("Cannot run on main thread").
        override suspend fun loadInitialVideos(url: String): ChannelContentPage =
            withContext(Dispatchers.IO) {
                // Fresh flow per open: a cached flow keeps the previous
                // visit's pager position and would start mid-window.
                val flow = try {
                    newVideoFlow(url).also { videoFlows[url] = it }
                } catch (e: Exception) {
                    // Engine throws ScriptException when the source plugin's
                    // HTTP call fails (e.g. network stall -> 408).
                    Logger.w("PlaylistRepo", "loadInitialVideos failed for $url", e)
                    return@withContext ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load playlist")
                }
                flow.loadInitial()
                ChannelContentPage(flow.items, flow.hasMore, flow.error)
            }

        override suspend fun loadNextPage(url: String): ChannelContentPage =
            withContext(Dispatchers.IO) {
                val flow = videoFlowFor(url)
                flow.loadNextPage()
                ChannelContentPage(flow.items, flow.hasMore, flow.error)
            }

        private fun videoFlowFor(url: String): PagerFlow<IPlatformVideo, Card> = videoFlows.getOrPut(url) { newVideoFlow(url) }

        private fun newVideoFlow(url: String): PagerFlow<IPlatformVideo, Card> =
            PagerFlow(
                clientFor(url).getPlaylist(url).contents,
                { video -> EngineCardMapper.toCard(video) },
                { it.id },
            )

        private fun clientFor(url: String) =
            StatePlatform.instance.getClientOrNullByUrl(url)
                ?: throw IllegalStateException("No client found for playlist url: $url")
    }
