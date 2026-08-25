package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.core.data.repository.ChannelContentPage
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelInfo
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlugins
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
            // Source badge only earns its place with >1 enabled source.
            val client = StatePlatform.instance.getClientOrNullByUrl(url)
            val multiSource = StatePlatform.instance.getEnabledClients().size > 1
            return ChannelInfo(
                url = channel.url,
                name = channel.name,
                thumbnail = channel.thumbnail,
                banner = channel.banner,
                subscribers = channel.subscribers,
                description = channel.description,
                links = channel.links,
                isSubscribed = StateSubscriptions.instance.isSubscribed(channel.url),
                notifyEnabled = StateSubscriptions.instance.getSubscription(channel.url)?.doNotifications == true,
                sourceIconUrl =
                    if (multiSource && client != null) StatePlugins.instance.getPluginIconUriOrNull(client.id) else null,
            )
        }

        override fun isSubscribed(url: String): Boolean = StateSubscriptions.instance.isSubscribed(url)

        override fun isNotificationsEnabled(url: String): Boolean =
            StateSubscriptions.instance.getSubscription(url)?.doNotifications == true

        override suspend fun toggleNotifications(url: String): Boolean {
            val sub = StateSubscriptions.instance.getSubscription(url)
            if (sub == null) return false
            sub.doNotifications = !sub.doNotifications
            sub.saveAsync()
            return sub.doNotifications
        }

        override suspend fun toggleSubscription(url: String): Boolean {
            if (StateSubscriptions.instance.isSubscribed(url)) {
                StateSubscriptions.instance.removeSubscription(url, isUserAction = true)
            } else {
                try {
                    val channel = StatePlatform.instance.getChannel(url).await()
                    StateSubscriptions.instance.addSubscription(channel)
                } catch (e: Exception) {
                    // Unsubscribing never fails; subscribing needs a live
                    // channel fetch — surface it as a no-op, not a crash.
                    Logger.w("ChannelRepo", "subscribe failed for $url", e)
                }
            }
            return StateSubscriptions.instance.isSubscribed(url)
        }

        // Pager construction and page loads execute source-plugin JS, which
        // the V8 engine refuses to run on the main thread — so every engine
        // call here is dispatched to IO ("Cannot run on main thread").
        override suspend fun loadInitialContents(url: String): ChannelContentPage =
            withContext(Dispatchers.IO) {
                // Fresh flow per open: a cached flow keeps the previous
                // visit's pager position and would start mid-window.
                val flow = try {
                    newContentFlow(url).also { contentFlows[url] = it }
                } catch (e: Exception) {
                    // The engine throws ScriptException when the source
                    // plugin's HTTP call fails (e.g. network stall -> 408).
                    // Surface it as a page error, not a crash.
                    Logger.w("ChannelRepo", "loadInitialContents failed for $url", e)
                    return@withContext ChannelContentPage(emptyList(), hasMore = false, error = e.message ?: "Failed to load channel contents")
                }
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
                val flow = try {
                    newPlaylistFlow(url).also { playlistFlows[url] = it }
                } catch (e: Exception) {
                    Logger.w("ChannelRepo", "loadPlaylists failed for $url", e)
                    return@withContext emptyList()
                }
                flow.loadInitial()
                flow.items
            }

        private fun contentFlowFor(url: String): PagerFlow<IPlatformContent, Card> = contentFlows.getOrPut(url) { newContentFlow(url) }

        private fun newContentFlow(url: String): PagerFlow<IPlatformContent, Card> {
            val client =
                StatePlatform.instance.getClientOrNullByUrl(url)
                    ?: throw IllegalStateException("No client found for channel url: $url")
            return PagerFlow(
                StatePlatform.instance.getChannelContent(client, url),
                { content -> EngineCardMapper.toCard(content) },
                { it.id },
            )
        }

        private fun playlistFlowFor(url: String): PagerFlow<IPlatformPlaylist, Card> = playlistFlows.getOrPut(url) { newPlaylistFlow(url) }

        private fun newPlaylistFlow(url: String): PagerFlow<IPlatformPlaylist, Card> =
            PagerFlow(
                StatePlatform.instance.getChannelPlaylists(url),
                { content -> EngineCardMapper.toCard(content) },
                { it.id },
            )
    }
