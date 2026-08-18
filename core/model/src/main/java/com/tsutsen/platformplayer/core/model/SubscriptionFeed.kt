package com.tsutsen.platformplayer.core.model

data class SubscriptionCreator(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long?,
    val url: String,
    val hasNewContent: Boolean = false,
)

data class SubscriptionFeed(
    val items: List<Card> = emptyList(),
    val creators: List<SubscriptionCreator> = emptyList(),
    val isLoading: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val activeCreatorId: String? = null,
    val filterStarted: Boolean = false,
    val filterWatched: Boolean = false,
    val filterVideo: Boolean = true,
    val filterStreams: Boolean = false,
)
