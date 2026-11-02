package com.tsutsen.platformplayer.states

import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.PlatformClientCapabilities
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.ResultCapabilities
import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.api.media.models.chapters.IChapter
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.tsutsen.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.tsutsen.platformplayer.api.media.models.playback.IPlaybackTracker
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.models.ImageVariable
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

/**
 * Pure policy layer of [EnabledClientResolver] — the live functions read
 * StatePlatform (Android), the pure functions take a client list, tested here
 * with fakes. The resolver only touches id/name, everything else throws.
 */
class EnabledClientResolverTests {
    private class FakeClient(override val id: String, override val name: String = id) : IPlatformClient {
        override val icon: ImageVariable? = null;
        override val capabilities: PlatformClientCapabilities get() = throw NotImplementedError();
        override fun initialize() = throw NotImplementedError();
        override fun disable() = throw NotImplementedError();
        override fun getHome(): IPager<IPlatformContent> = throw NotImplementedError();
        override fun getShorts(): IPager<IPlatformVideo> = throw NotImplementedError();
        override fun searchSuggestions(query: String): Array<String> = throw NotImplementedError();
        override fun getSearchCapabilities(): ResultCapabilities = throw NotImplementedError();
        override fun search(query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> = throw NotImplementedError();
        override fun getSearchChannelContentsCapabilities(): ResultCapabilities = throw NotImplementedError();
        override fun searchChannelContents(channelUrl: String, query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> = throw NotImplementedError();
        override fun searchChannels(query: String): IPager<PlatformAuthorLink> = throw NotImplementedError();
        override fun searchChannelsAsContent(query: String): IPager<IPlatformContent> = throw NotImplementedError();
        override fun isChannelUrl(url: String): Boolean = throw NotImplementedError();
        override fun getChannel(channelUrl: String): IPlatformChannel = throw NotImplementedError();
        override fun getChannelCapabilities(): ResultCapabilities = throw NotImplementedError();
        override fun getChannelContents(channelUrl: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> = throw NotImplementedError();
        override fun getPeekChannelTypes(): List<String> = throw NotImplementedError();
        override fun peekChannelContents(channelUrl: String, type: String?): List<IPlatformContent> = throw NotImplementedError();
        override fun getChannelPlaylists(channelUrl: String): IPager<IPlatformPlaylist> = throw NotImplementedError();
        override fun getChannelUrlByClaim(claimType: Int, claimValues: Map<Int, String>): String? = throw NotImplementedError();
        override fun isContentDetailsUrl(url: String): Boolean = throw NotImplementedError();
        override fun getContentDetails(url: String): IPlatformContentDetails = throw NotImplementedError();
        override fun getContentChapters(url: String): List<IChapter> = throw NotImplementedError();
        override fun getPlaybackTracker(url: String): IPlaybackTracker? = throw NotImplementedError();
        override fun getContentRecommendations(url: String): IPager<IPlatformContent>? = throw NotImplementedError();
        override fun getComments(url: String): IPager<IPlatformComment> = throw NotImplementedError();
        override fun getSubComments(comment: IPlatformComment): IPager<IPlatformComment> = throw NotImplementedError();
        override fun getLiveChatWindow(url: String): ILiveChatWindowDescriptor? = throw NotImplementedError();
        override fun getLiveEvents(url: String): IPager<IPlatformLiveEvent>? = throw NotImplementedError();
        override fun searchPlaylists(query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> = throw NotImplementedError();
        override fun isPlaylistUrl(url: String): Boolean = throw NotImplementedError();
        override fun getPlaylist(url: String): IPlatformPlaylistDetails = throw NotImplementedError();
        override fun getUserPlaylists(): Array<String> = throw NotImplementedError();
        override fun getUserSubscriptions(): Array<String> = throw NotImplementedError();
        override fun getUserHistory(): IPager<IPlatformContent> = throw NotImplementedError();
        override fun isClaimTypeSupported(claimType: Int): Boolean = throw NotImplementedError();
    }

    private fun client(id: String, name: String = id) = FakeClient(id, name);

    // ---- orderedClients (the getSortedEnabledClient policy) ----

    @Test
    fun `orderedClients puts platformOrder names first and appends the rest in enabled order`() {
        val clients = listOf(client("yt", "YouTube"), client("nv", "Nebula"), client("pt", "PeerTube"));
        val result = EnabledClientResolver.orderedClients(clients, listOf("PeerTube", "YouTube"));
        assertEquals(listOf("pt", "yt", "nv"), result.map { it.id });
    }

    @Test
    fun `orderedClients ignores order names with no match and keeps enabled order when order is empty`() {
        val clients = listOf(client("a", "Alpha"), client("b", "Beta"));
        assertEquals(listOf("a", "b"), EnabledClientResolver.orderedClients(clients, listOf("Gamma")).map { it.id });
        assertEquals(listOf("a", "b"), EnabledClientResolver.orderedClients(clients, emptyList()).map { it.id });
    }

    @Test
    fun `orderedClients matches by name, keeps only the first name match in the ordered slot`() {
        // Pins the original semantics: later same-name clients stay in the remainder.
        val result = EnabledClientResolver.orderedClients(listOf(client("x2", "Dup"), client("x1", "Dup")), listOf("Dup"));
        assertEquals(listOf("x2", "x1"), result.map { it.id });
    }

    // ---- ids / id sets ----

    @Test
    fun `clientIds keeps enabled order, clientIdSet dedupes`() {
        val clients = listOf(client("a"), client("b"), client("a"));
        assertEquals(listOf("a", "b", "a"), EnabledClientResolver.clientIds(clients));
        assertEquals(setOf("a", "b"), EnabledClientResolver.clientIdSet(clients));
    }

    // ---- enabled checks ----

    @Test
    fun `isClientEnabled by id matches id equality`() {
        val clients = listOf(client("a", "Alpha"));
        assertTrue(EnabledClientResolver.isClientEnabled(clients, "a"));
        assertFalse(EnabledClientResolver.isClientEnabled(clients, "b"));
    }

    @Test
    fun `isClientEnabled by client uses membership, not id equality`() {
        val enabled = listOf(client("a", "Alpha"));
        assertTrue(EnabledClientResolver.isClientEnabled(enabled, enabled[0]));
        assertFalse(EnabledClientResolver.isClientEnabled(enabled, client("a", "Alpha")));
    }

    // ---- multi-source ----

    @Test
    fun `isMultiSource is only true with more than one client`() {
        assertFalse(EnabledClientResolver.isMultiSource(emptyList()));
        assertFalse(EnabledClientResolver.isMultiSource(listOf(client("a"))));
        assertTrue(EnabledClientResolver.isMultiSource(listOf(client("a"), client("b"))));
    }
}
