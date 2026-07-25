package com.tsutsen.platformplayer.core.model

data class SubscriptionFeed(
    val items: List<Card> = emptyList(),
    val isLoading: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val activeCreatorFilter: String? = null,
    val activeTypeFilter: String? = null
)
