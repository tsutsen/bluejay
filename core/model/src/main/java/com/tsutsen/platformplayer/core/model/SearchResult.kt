package com.tsutsen.platformplayer.core.model

/** Which kind of content a search should return (one engine pager per type). */
enum class SearchType {
    MEDIA,
    CREATORS,
    PLAYLISTS,
}

/**
 * Search result sorting.
 * [jsOrder] is the engine (YouTube plugin) sort string; null means the
 * platform default (relevance). Only media search supports sorting.
 */
enum class SearchSort(
    val jsOrder: String?,
    val label: String,
) {
    RELEVANCE(null, "Relevance"),
    VIEWS("Views", "Views"),
    DATE("CHRONOLOGICAL", "Date"),
    RATING("Rating", "Rating"),
}

data class SearchResult(
    val query: String = "",
    val items: List<Card> = emptyList(),
    val creators: List<ChannelCard> = emptyList(),
    val playlists: List<PlaylistCard> = emptyList(),
    val isLoading: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
)
