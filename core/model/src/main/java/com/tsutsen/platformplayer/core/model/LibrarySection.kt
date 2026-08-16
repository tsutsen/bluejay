package com.tsutsen.platformplayer.core.model

data class LibrarySection(
    val id: String,
    val title: String,
    val items: List<Card> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
)
