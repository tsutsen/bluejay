package com.tsutsen.platformplayer.core.datastore.model

data class SubscriptionsPreferences(
    /** Fetch the subscription feed on app boot. */
    val fetchOnAppBoot: Boolean = true,
    /** Route subscription updates through the exchange server. */
    val useSubscriptionExchange: Boolean = false,
    /** Peek channel contents when a fetch would exceed rate limits. */
    val peekChannelContents: Boolean = true,
    /** Minutes between background subscription refreshes; 0 = off. Legacy stub getter returned a constant 60. */
    val backgroundIntervalMinutes: Int = 60,
    /** Parallel channel fetches. Legacy stub getter returned a constant 4. */
    val concurrency: Int = 4,
)
