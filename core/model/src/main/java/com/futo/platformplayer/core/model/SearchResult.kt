package com.futo.platformplayer.core.model

data class SearchResult(
    val query: String = "",
    val items: List<Card> = emptyList(),
    val creators: List<ChannelCard> = emptyList(),
    val playlists: List<PlaylistCard> = emptyList(),
    val isLoading: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0
)
