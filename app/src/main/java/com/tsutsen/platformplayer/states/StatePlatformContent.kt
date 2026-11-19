package com.tsutsen.platformplayer.states

import com.tsutsen.platformplayer.di.SettingsRepositoryImpl
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.exceptions.NoPlatformClientException
import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.api.media.models.chapters.IChapter
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.ResultCapabilities
import com.tsutsen.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.tsutsen.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.tsutsen.platformplayer.api.media.models.playback.IPlaybackTracker
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.structures.EmptyPager
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.api.media.structures.MultiChronoContentPager
import com.tsutsen.platformplayer.fromPool
import com.tsutsen.platformplayer.getNowDiffDays
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.Deferred
import kotlin.streams.asSequence
import kotlinx.coroutines.async
import java.time.OffsetDateTime

// Content / channel / playlist / comments / live-event / account concerns
// extracted verbatim from StatePlatform.kt as extension functions. Bodies are
// unchanged; only the receiver qualification changed.
// Depends on StatePlatform internal members: TAG, _scope and the client pools.

//Video
    fun StatePlatform.getContentClient(url: String) : IPlatformClient = getContentClientOrNull(url)
        ?: throw NoPlatformClientException("No client enabled that supports this content url (${url})");
    fun StatePlatform.getContentClientOrNull(url: String) : IPlatformClient? = getEnabledClients().find { _instantClientPool.getClientPooled(it).isContentDetailsUrl(url) };

    fun StatePlatform.getContentChapters(url: String): List<IChapter>? {
        val baseClient = getContentClientOrNull(url) ?: return null;
        if (baseClient !is JSClient) {
            return baseClient.getContentChapters(url);
        }
        val client = _trackerClientPool.getClientPooled(baseClient, 1);
        return client.getContentChapters(url);
    }
    fun StatePlatform.getPlaybackTracker(url: String): IPlaybackTracker? {
        val baseClient = getContentClientOrNull(url) ?: return null;
        if (baseClient !is JSClient) {
            return baseClient.getPlaybackTracker(url);
        }
        val client = _trackerClientPool.getClientPooled(baseClient, 1);
        return client.getPlaybackTracker(url);
    }

    fun StatePlatform.getContentRecommendations(url: String): IPager<IPlatformContent>? {
        val baseClient = getContentClientOrNull(url) ?: return null;
        if (baseClient !is JSClient) {
            return baseClient.getContentRecommendations(url);
        }
        val client = _mainClientPool.getClientPooled(baseClient);
        return client.getContentRecommendations(url);
    }

    fun StatePlatform.getChannelClient(url : String, exclude: List<String>? = null) : IPlatformClient = getChannelClientOrNull(url, exclude)
        ?: throw NoPlatformClientException("No client enabled that supports this channel url (${url})");
    fun StatePlatform.getChannelClientOrNull(url : String, exclude: List<String>? = null) : IPlatformClient? =
        if(exclude == null)
            getEnabledClients().find { _instantClientPool.getClientPooled(it).isChannelUrl(url) }
        else
            getEnabledClients().find { !exclude.contains(it.id) && _instantClientPool.getClientPooled(it).isChannelUrl(url) };

    fun StatePlatform.getChannel(url: String, updateSubscriptions: Boolean = true): Deferred<IPlatformChannel>  {
        Logger.i(TAG, "Platform - getChannel");
        val channel = StateSubscriptions.instance.getSubscription(url);
        return if(channel != null) {
            _scope.async { getChannelLive(url, updateSubscriptions) }; //_batchTaskGetChannel.execute(channel);
        } else {
            _scope.async { getChannelLive(url, updateSubscriptions) };
        }
    }

    fun StatePlatform.getChannelContent(baseClient: IPlatformClient, channelUrl: String, isSubscriptionOptimized: Boolean = false, usePooledClients: Int = 0, type: String? = null): IPager<IPlatformContent> {
        val clientCapabilities = baseClient.getChannelCapabilities();
        val client = if(usePooledClients > 1)
            _channelClientPool.getClientPooled(baseClient, usePooledClients);
        else baseClient;

        var lastStream: OffsetDateTime? = null;

        val pagerResult: IPager<IPlatformContent>;
        if (type == null) {
            if(!clientCapabilities.hasType(ResultCapabilities.TYPE_MIXED) &&
                    (   clientCapabilities.hasType(ResultCapabilities.TYPE_VIDEOS) ||
                        clientCapabilities.hasType(ResultCapabilities.TYPE_STREAMS) ||
                        clientCapabilities.hasType(ResultCapabilities.TYPE_LIVE) ||
                        clientCapabilities.hasType(ResultCapabilities.TYPE_POSTS)
                    )) {
                val toQuery = mutableListOf<String>();
                if(clientCapabilities.hasType(ResultCapabilities.TYPE_VIDEOS))
                    toQuery.add(ResultCapabilities.TYPE_VIDEOS);
                if(clientCapabilities.hasType(ResultCapabilities.TYPE_STREAMS))
                    toQuery.add(ResultCapabilities.TYPE_STREAMS);
                if(clientCapabilities.hasType(ResultCapabilities.TYPE_LIVE))
                    toQuery.add(ResultCapabilities.TYPE_LIVE);
                if(clientCapabilities.hasType(ResultCapabilities.TYPE_POSTS))
                    toQuery.add(ResultCapabilities.TYPE_POSTS);

                if(isSubscriptionOptimized) {
                    val sub = StateSubscriptions.instance.getSubscription(channelUrl);
                    if(sub != null) {
                        if(!sub.shouldFetchStreams()) {
                            Logger.i(TAG, "Subscription [${sub.channel.name}:${channelUrl}] Last livestream > 7 days, skipping live streams [${sub.lastLiveStream.getNowDiffDays()} days ago]");
                            toQuery.remove(ResultCapabilities.TYPE_LIVE);
                        }
                        if(!sub.shouldFetchLiveStreams()) {
                            Logger.i(TAG, "Subscription [${sub.channel.name}:${channelUrl}] Last livestream > 15 days, skipping streams [${sub.lastLiveStream.getNowDiffDays()} days ago]");
                            toQuery.remove(ResultCapabilities.TYPE_STREAMS);
                        }
                        if(!sub.shouldFetchPosts()) {
                            Logger.i(TAG, "Subscription [${sub.channel.name}:${channelUrl}] Last livestream > 5 days, skipping posts [${sub.lastPost.getNowDiffDays()} days ago]");
                            toQuery.remove(ResultCapabilities.TYPE_POSTS);
                        }
                    }
                }

                //Merged pager
                val pagers: List<IPager<IPlatformContent>> = toQuery
                    .parallelStream()
                    .map {
                        val results = client.getChannelContents(channelUrl, it, ResultCapabilities.ORDER_CHONOLOGICAL) ;

                        when(it) {
                            ResultCapabilities.TYPE_STREAMS -> {
                                val streamResults = results.getResults();
                                if(streamResults.size == 0)
                                    lastStream = OffsetDateTime.MIN;
                                else
                                    lastStream = results.getResults().firstOrNull()?.datetime;
                            }
                        }
                        return@map results;
                    }
                    .asSequence()
                    .toList();

                val pager = MultiChronoContentPager(pagers.toTypedArray());
                pager.initialize();
                pagerResult = pager;
            }
            else {
                pagerResult = client.getChannelContents(channelUrl, ResultCapabilities.TYPE_MIXED, ResultCapabilities.ORDER_CHONOLOGICAL);
            }
        } else {
            pagerResult = if (type == ResultCapabilities.TYPE_SHORTS && clientCapabilities.hasType(ResultCapabilities.TYPE_SHORTS)) {
                client.getChannelContents(channelUrl, ResultCapabilities.TYPE_SHORTS, ResultCapabilities.ORDER_CHONOLOGICAL);
            } else {
                EmptyPager()
            }
        }

        //Subscription optimization
        val sub = StateSubscriptions.instance.getSubscription(channelUrl);
        if(sub != null) {
            var hasChanges = false;
            val lastVideo = pagerResult.getResults().firstOrNull();

            fun saneDate(dt: OffsetDateTime?): OffsetDateTime? {
                if (dt == null || dt.year < 1971 || !dt.isBefore(OffsetDateTime.now().plusDays(1))) return null
                return dt
            }
            val saneVideoDate = saneDate(lastVideo?.datetime)
            if(saneVideoDate != null && sub.lastVideo.getNowDiffDays() != saneVideoDate.getNowDiffDays()) {
                Logger.i(TAG, "Subscription [${channelUrl}] has new last video date [${saneVideoDate.getNowDiffDays()} Days]");
                sub.lastVideo = saneVideoDate;
                hasChanges = true;
            }

            val saneStreamDate = saneDate(lastStream)
            if(saneStreamDate != null && sub.lastLiveStream.getNowDiffDays() != saneStreamDate.getNowDiffDays()) {
                Logger.i(TAG, "Subscription [${channelUrl}] has new last stream date [${saneStreamDate.getNowDiffDays()} Days]");
                sub.lastLiveStream = saneStreamDate;
                hasChanges = true;
            }

            val now = OffsetDateTime.now();
            val firstPage = pagerResult.getResults().filter { it.datetime != null && it.datetime!! < now }
            if(firstPage.size > 0) {
                val newestVideoDays = firstPage[0].datetime?.getNowDiffDays()?.toInt() ?: 0;
                val diffs = mutableListOf<Int>()
                for(i in (firstPage.size - 1) downTo  1) {
                    val currentVideoDays = firstPage[i].datetime?.getNowDiffDays();
                    val nextVideoDays = firstPage[i - 1].datetime?.getNowDiffDays();

                    if(currentVideoDays != null && nextVideoDays != null) {
                        val diff = nextVideoDays - currentVideoDays;
                        diffs.add(diff.toInt());
                    }
                }
                val averageDiff = if(diffs.size > 0)
                    newestVideoDays.coerceAtLeast(diffs.average().toInt())
                else
                    newestVideoDays;

                if(sub.uploadInterval != averageDiff) {
                    Logger.i(TAG, "Subscription [${channelUrl}] has new upload interval [${averageDiff} Days]");
                    sub.uploadInterval = averageDiff;
                    hasChanges = true;
                }
            }

            if(hasChanges)
                sub.save();
        }

        return pagerResult;
    }
    fun StatePlatform.getChannelContent(channelUrl: String, isSubscriptionOptimized: Boolean = false, usePooledClients: Int = 0, ignorePlugins: List<String>? = null, type: String? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - getChannelVideos");
        val baseClient = getChannelClient(channelUrl, ignorePlugins);
        return getChannelContent(baseClient, channelUrl, isSubscriptionOptimized, usePooledClients, type);
    }
    fun StatePlatform.getChannelContent(channelUrl: String, type: String?, ordering: String = ResultCapabilities.ORDER_CHONOLOGICAL): IPager<IPlatformContent> {
        val client = getChannelClient(channelUrl);
        return getChannelContent(client, channelUrl, type, ordering);
    }
    fun StatePlatform.getChannelContent(baseClient: IPlatformClient, channelUrl: String, type: String?, ordering: String = ResultCapabilities.ORDER_CHONOLOGICAL): IPager<IPlatformContent> {
        val client = _channelClientPool.getClientPooled(baseClient, SettingsRepositoryImpl.instance.preferences.value.subscriptions.concurrency);
        return client.getChannelContents(channelUrl, type, ordering) ;
    }

    fun StatePlatform.getChannelPlaylists(channelUrl: String): IPager<IPlatformPlaylist> {
        val client = getChannelClient(channelUrl);
        return client.getChannelPlaylists(channelUrl);
    }

    fun StatePlatform.peekChannelContents(baseClient: IPlatformClient, channelUrl: String, type: String?): List<IPlatformContent> {
        val client = _channelClientPool.getClientPooled(baseClient, SettingsRepositoryImpl.instance.preferences.value.subscriptions.concurrency);
        return client.peekChannelContents(channelUrl, type) ;
    }

    fun StatePlatform.getChannelLive(url: String, updateSubscriptions: Boolean = true): IPlatformChannel {
        val channel = getChannelClient(url).getChannel(url);

        if(updateSubscriptions && StateSubscriptions.instance.isSubscribed(channel))
            StateSubscriptions.instance.updateSubscriptionChannel(channel);

        return channel
    }

    fun StatePlatform.getChannelUrlByClaim(claimType: Int, claimValues: Map<Int, String>): String? {
        val client = getClientByClaimTypeOrNull(claimType) ?: return null;
        return client.getChannelUrlByClaim(claimType, claimValues);
    }
    fun StatePlatform.resolveChannelUrlByClaimTemplates(claimType: Int, claimValues: Map<Int, String>): String? {
        for(client in getClientsByClaimType(claimType).filter { it is JSClient }) {
            val url = (client as JSClient).resolveChannelUrlByClaimTemplates(claimType, claimValues);
            if(!url.isNullOrEmpty())
                return url;
        }
        return null;
    }

    fun StatePlatform.resolveChannelUrlsByClaimTemplates(claimType: Int, claimValues: Map<Int, String>): List<String> {
        val urls = arrayListOf<String>();
        for(client in getClientsByClaimType(claimType).filter { it is JSClient }) {
            val res = (client as JSClient).resolveChannelUrlsByClaimTemplates(claimType, claimValues);
            urls.addAll(res);
        }
        return urls;
    }

    fun StatePlatform.getPlaylistClientOrNull(url: String): IPlatformClient? = getEnabledClients().find { _instantClientPool.getClientPooled(it).isPlaylistUrl(url) }
    fun StatePlatform.getPlaylistClient(url: String): IPlatformClient = getEnabledClients().find { _instantClientPool.getClientPooled(it).isPlaylistUrl(url) }
        ?: throw NoPlatformClientException("No client enabled that supports this playlist url (${url})");
    fun StatePlatform.getPlaylist(url: String): IPlatformPlaylistDetails {
        return getPlaylistClient(url).getPlaylist(url);
    }

    //Comments
    fun StatePlatform.getComments(content: IPlatformContentDetails): IPager<IPlatformComment> {
        val client = getContentClient(content.url);
        val pager = content.getComments(client);

        return pager ?: getComments(content.url);
    }
    fun StatePlatform.getComments(url: String): IPager<IPlatformComment> {
        Logger.i(TAG, "Platform - getComments");
        val client = getContentClient(url);
        if(!client.capabilities.hasGetComments)
            return EmptyPager();

        if(!StateApp.instance.privateMode)
            return client.fromPool(_pagerClientPool).getComments(url);
        else
            return client.fromPool(_privateClientPool).getComments(url);
    }
    fun StatePlatform.getSubComments(comment: IPlatformComment): IPager<IPlatformComment> {
        Logger.i(TAG, "Platform - getSubComments");
        val client = getContentClient(comment.contextUrl);
        return client.getSubComments(comment);
    }

    fun StatePlatform.getLiveEvents(url: String): IPager<IPlatformLiveEvent>? {
        Logger.i(TAG, "Platform - getLiveChat");
        var client = getContentClient(url);

        if(!StateApp.instance.privateMode)
            return client.fromPool(_liveEventClientPool).getLiveEvents(url);
        else
            return client.fromPool(_privateClientPool).getLiveEvents(url);
    }
    fun StatePlatform.getLiveChatWindow(url: String): ILiveChatWindowDescriptor? {
        Logger.i(TAG, "Platform - getLiveChat");
        var client = getContentClient(url);
        return client.getLiveChatWindow(url);
    }

    //Account
    fun StatePlatform.getUserHistory(id: String): IPager<IPlatformContent> {
        val client = getClient(id);
        if(client is JSClient && client.isLoggedIn) {
            return client.fromPool(_pagerClientPool).getUserHistory()
        }
        return EmptyPager<IPlatformContent>();
    }
