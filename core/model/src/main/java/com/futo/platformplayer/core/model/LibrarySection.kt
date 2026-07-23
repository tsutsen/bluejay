package com.futo.platformplayer.core.model

data class LibrarySection(
    val id: String,
    val title: String,
    val items: List<Card> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false
)
