package com.tsutsen.platformplayer.stores

import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.models.Subscription
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable()
class SubscriptionStorage : FragmentedStorageFileJson() {
    var version = StateSubscriptions.VERSION;
    var subscriptions = arrayListOf<Subscription>();

    fun addSubscription(channel: Subscription) : Subscription {
        subscriptions.add(channel);
        return channel;
    }

    fun removeSubscription(url : String) {
        subscriptions.removeIf { it.channel.url == url };
    }

    fun isSubscribedTo(channel: IPlatformChannel): Boolean = isSubscribedTo(channel.url);
    fun isSubscribedTo(channel: PlatformAuthorLink): Boolean = isSubscribedTo(channel.url);
    fun isSubscribedTo(url: String) : Boolean = subscriptions.any { u -> u.channel.url == url };

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}