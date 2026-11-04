package com.tsutsen.platformplayer.models

import com.tsutsen.platformplayer.api.media.models.ResultCapabilities
import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.api.media.models.channels.SerializedChannel
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.getNowDiffDays
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.serializers.OffsetDateTimeSerializer
import com.tsutsen.platformplayer.states.EnabledClientResolver
import com.tsutsen.platformplayer.states.StateSubscriptions
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
class Subscription {
    var channel: SerializedChannel;

    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var creationTime: OffsetDateTime = OffsetDateTime.MIN;

    //Settings
    var doNotifications: Boolean = false;
    var doFetchLive: Boolean = false;
    var doFetchStreams: Boolean = true;
    var doFetchVideos: Boolean = true;
    var doFetchPosts: Boolean = false;

    //Last found content
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastVideo : OffsetDateTime = OffsetDateTime.MAX;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastLiveStream : OffsetDateTime = OffsetDateTime.MAX;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastPost : OffsetDateTime = OffsetDateTime.MAX;

    //Last update time
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastVideoUpdate : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastStreamUpdate : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastLiveStreamUpdate : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastPostUpdate : OffsetDateTime = OffsetDateTime.MIN;

    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastPeekVideo : OffsetDateTime = OffsetDateTime.MIN;

    //Last video interval
    var uploadInterval : Int = 0;
    var uploadStreamInterval : Int = 0;
    var uploadPostInterval : Int = 0;

    var playbackSeconds: Int = 0;
    var playbackViews: Int = 0;

    var isOther = false;

    constructor(channel : SerializedChannel) {
        this.channel = channel;
        if(this.creationTime == OffsetDateTime.MIN)
            this.creationTime = OffsetDateTime.now();
    }

    fun isChannel(url: String): Boolean {
        return channel.url == url || channel.urlAlternatives.contains(url);
    }

    fun shouldFetchVideos() = doFetchVideos &&
            (lastVideo.getNowDiffDays() < 30 || lastVideoUpdate.getNowDiffDays() >= 1) &&
            (lastVideo.getNowDiffDays() < 180 || lastVideoUpdate.getNowDiffDays() >= 3);
    fun shouldFetchStreams() = doFetchStreams && (lastLiveStream.getNowDiffDays() < 7);
    fun shouldFetchLiveStreams() = doFetchLive && (lastLiveStream.getNowDiffDays() < 14);
    fun shouldFetchPosts() = doFetchPosts && (lastPost.getNowDiffDays() < 5);

    fun getClient() = EnabledClientResolver.channelClientForUrl(channel.url);

    fun save() {
        if(isOther)
            StateSubscriptions.instance.saveSubscriptionOther(this);
        else
            StateSubscriptions.instance.saveSubscription(this);
    }
    fun saveAsync() {
        if(isOther)
            StateSubscriptions.instance.saveSubscriptionOtherAsync(this);
        else
            StateSubscriptions.instance.saveSubscriptionAsync(this);
    }

    fun updateChannel(channel: IPlatformChannel) {
        this.channel = SerializedChannel.fromChannel(channel);
    }

    fun updatePlayback(seconds: Int) {
        playbackSeconds += seconds;
    }
    fun addPlaybackView() {
        playbackViews += 1;
    }

    fun updateSubscriptionState(type: String, initialPage: List<IPlatformContent>) {
        // Plugins occasionally report absurd datetimes (OffsetDateTime.MIN /
        // MAX for unknown streams). Stored as-is they permanently disable
        // shouldFetch* (diff-days < 7/30 — MIN lands +365B days in the "past")
        // and starve the channel out of the "All subs" feed. Accept only
        // plausibly real dates: after 1971, not in the future.
        fun sane(dt: OffsetDateTime?): OffsetDateTime? {
            val d = dt ?: return null
            if (d.year < 1971) return null
            if (!d.isBefore(OffsetDateTime.now().plusDays(1))) return null
            return d
        }

        val interval: Int;
        val mostRecent: OffsetDateTime?;
        if(!initialPage.isEmpty()) {
            val newestVideoDays = initialPage[0].datetime?.getNowDiffDays()?.toInt() ?: 0;
            val diffs = mutableListOf<Int>()
            for(i in (initialPage.size - 1) downTo  1) {
                val currentVideoDays = initialPage[i].datetime?.getNowDiffDays();
                val nextVideoDays = initialPage[i - 1].datetime?.getNowDiffDays();

                if(currentVideoDays != null && nextVideoDays != null) {
                    val diff = nextVideoDays - currentVideoDays;
                    diffs.add(diff.toInt());
                }
            }
            val averageDiff = if(diffs.size > 0)
                newestVideoDays.coerceAtLeast(diffs.average().toInt())
            else
                newestVideoDays;
            interval = averageDiff.coerceAtLeast(1);
            mostRecent = sane(initialPage[0].datetime);
        }
        else {
            interval = 5;
            mostRecent = null;
            Logger.i("Subscription", "Subscription [${channel.name}]:${type} no results found");
        }
        when(type) {
            ResultCapabilities.TYPE_VIDEOS -> {
                uploadInterval = interval;
                if(mostRecent != null)
                    lastVideo = mostRecent;
                else if(lastVideo.getNowDiffDays() > 36500 || lastVideo.year > 3000)
                    lastVideo = OffsetDateTime.MAX;
                lastVideoUpdate = OffsetDateTime.now();
                lastPeekVideo = OffsetDateTime.MIN;
            }
            ResultCapabilities.TYPE_MIXED -> {
                uploadInterval = interval;
                if(mostRecent != null)
                    lastVideo = mostRecent;
                else if(lastVideo.getNowDiffDays() > 36500 || lastVideo.year > 3000)
                    lastVideo = OffsetDateTime.MAX;
                lastVideoUpdate = OffsetDateTime.now();
                lastPeekVideo = OffsetDateTime.MIN;
            }
            ResultCapabilities.TYPE_SUBSCRIPTIONS -> {
                uploadInterval = interval;
                if(mostRecent != null)
                    lastVideo = mostRecent;
                else if(lastVideo.getNowDiffDays() > 36500 || lastVideo.year > 3000)
                    lastVideo = OffsetDateTime.MAX;
                lastVideoUpdate = OffsetDateTime.now();
            }
            ResultCapabilities.TYPE_STREAMS -> {
                uploadStreamInterval = interval;
                if(mostRecent != null)
                    lastLiveStream = mostRecent;
                else if(lastLiveStream.getNowDiffDays() > 36500 || lastLiveStream.year > 3000)
                    lastLiveStream = OffsetDateTime.MAX;
                lastStreamUpdate = OffsetDateTime.now();
            }
            ResultCapabilities.TYPE_POSTS -> {
                uploadPostInterval = interval;
                if(mostRecent != null)
                    lastPost = mostRecent;
                else if(lastPost.getNowDiffDays() > 36500 || lastPost.year > 3000)
                    lastPost = OffsetDateTime.MAX;
                lastPostUpdate = OffsetDateTime.now();
            }
        }
    }
}