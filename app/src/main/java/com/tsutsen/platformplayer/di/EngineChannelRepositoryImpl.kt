package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.core.data.repository.ChannelContentPage
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelInfo
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChannelRepository implementation that bridges to the engine
 * (StatePlatform / StateSubscriptions). Reuses Stage 1's EngineCardMapper
 * and PagerFlow — no duplicate mapping/pagination logic.
 */
@Singleton
class EngineChannelRepositoryImpl
    @Inject
    constructor() : ChannelRepository {
        private val contentFlows = mutableMapOf<String, PagerFlow<IPlatformContent, Card>>()
        private val playlistFlows = mutableMapOf<String, PagerFlow<IPlatformPlaylist, Card>>()

        override suspend fun getChannel(url: String): ChannelInfo {
            val channel = StatePlatform.instance.getChannel(url).await()
            return ChannelInfo(
                url = channel.url,
                name = channel.name,
                thumbnail = channel.thumbnail,
                banner = channel.banner,
                subscribers = channel.subscribers,
                description = channel.description,
                links = channel.links,
                isSubscribed = StateSubscriptions.instance.isSubscribed(channel.url),
            )
        }

        override fun isSubscribed(url: String): Boolean = StateSubscriptions.instance.isSubscribed(url)

        override suspend fun toggleSubscription(url: String): Boolean {
            if (StateSubscriptions.instance.isSubscribed(url)) {
                StateSubscriptions.instance.removeSubscription(url, isUserAction = true)
            } else {
                val channel = StatePlatform.instance.getChannel(url).await()
                StateSubscriptions.instance.addSubscription(channel)
            }
            return StateSubscriptions.instance.isSubscribed(url)
        }

        // Pager construction and page loads execute source-plugin JS, which
        // the V8 engine refuses to run on the main thread — so every engine
        // call here is dispatched to IO ("Cannot run on main thread").
        override suspend fun loadInitialContents(url: String): ChannelContentPage =
            withContext(Dispatchers.IO) {
                val flow = contentFlowFor(url)
                val cards = flow.loadInitial()
                ChannelContentPage(cards, flow.hasMore, flow.error)
            }

        override suspend fun loadNextPage(url: String): ChannelContentPage =
            withContext(Dispatchers.IO) {
                val flow = contentFlowFor(url)
                flow.loadNextPage()
                ChannelContentPage(flow.items, flow.hasMore, flow.error)
            }

        override suspend fun loadPlaylists(url: String): List<Card> =
            withContext(Dispatchers.IO) {
                val flow = playlistFlowFor(url)
                flow.loadInitial()
                flow.items
            }

        private fun contentFlowFor(url: String): PagerFlow<IPlatformContent, Card> =
            contentFlows.getOrPut(url) {
                val client =
                    StatePlatform.instance.getClientOrNullByUrl(url)
                        ?: throw IllegalStateException("No client found for channel url: $url")
                PagerFlow(
                    StatePlatform.instance.getChannelContent(client, url),
                ) { content -> EngineCardMapper.toCard(content) }
            }

        private fun playlistFlowFor(url: String): PagerFlow<IPlatformPlaylist, Card> =
            playlistFlows.getOrPut(url) {
                PagerFlow(
                    StatePlatform.instance.getChannelPlaylists(url),
                ) { content -> EngineCardMapper.toCard(content) }
            }
    }
