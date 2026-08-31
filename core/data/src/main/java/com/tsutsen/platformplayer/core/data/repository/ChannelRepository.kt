package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.ChannelInfo

/**
 * Result of a channel content page load. [cards] is the accumulated list.
 */
data class ChannelContentPage(
    val cards: List<Card> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
)

/**
 * Channel data: info, subscription state, and content/playlists pagination.
 * Implementations bridge to the engine (app module) and reuse the shared
 * card mapper + pager flow.
 */
/**
 * Channel content types for type-specific tabs. Values mirror the engine's
 * ResultCapabilities type constants (kept as strings so core/data doesn't
 * depend on the app module).
 */
const val CHANNEL_TYPE_SHORTS = "SHORTS"

interface ChannelRepository {
    /** Resolves and returns channel info. Throws if the url has no client. */
    suspend fun getChannel(url: String): ChannelInfo

    fun isSubscribed(url: String): Boolean

    /** Subscribes or unsubscribes. Returns the new state. */
    suspend fun toggleSubscription(url: String): Boolean

    /** Whether new-video notifications are enabled for this channel. */
    fun isNotificationsEnabled(url: String): Boolean

    /** Toggles new-video notifications. Returns the new state. */
    suspend fun toggleNotifications(url: String): Boolean

    /**
     * Loads the first page of the channel's contents (newest first).
     * [type] selects a content type tab (e.g. [CHANNEL_TYPE_SHORTS]);
     * null = the channel's default/mixed contents.
     */
    suspend fun loadInitialContents(url: String, type: String? = null): ChannelContentPage

    /** Loads the next page; [ChannelContentPage.cards] is the full accumulated list. */
    suspend fun loadNextPage(url: String, type: String? = null): ChannelContentPage

    /** Loads the channel's playlists. */
    suspend fun loadPlaylists(url: String): List<Card>
}
