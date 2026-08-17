package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.logging.Logger

/**
 * Accumulates an [IPager]'s delta results into a flat list.
 * Convention: [IPager.getResults] returns only the last loaded page
 * (MultiPager/Window semantics), so each page is appended to [items].
 *
 * @param map per-item converter; null results are dropped (e.g. [EngineCardMapper.toCard])
 */
class PagerFlow<T, R>(
    private val pager: IPager<T>,
    private val map: (T) -> R?,
) {
    private var _items: List<R> = emptyList()
    val items: List<R> get() = _items
    val hasMore: Boolean get() = pager.hasMorePages()
    var error: String? = null
        private set

    /** Loads the pager's first page. */
    fun loadInitial(): List<R> {
        error = null
        return try {
            _items = pager.getResults().mapNotNull(map)
            _items
        } catch (e: Exception) {
            Logger.w("PagerFlow", "loadInitial failed", e)
            error = e.message
            emptyList()
        }
    }

    /** Loads the next page and appends to [items]. Returns only the new items. */
    fun loadNextPage(): List<R> {
        if (!hasMore) return emptyList()
        return try {
            pager.nextPage()
            val delta = pager.getResults().mapNotNull(map)
            // Engine window pagers can re-return items from the previous page
            // (small playlists running out of content). Drop duplicates so
            // lazy grid keys (Card.id) stay unique.
            _items += delta.filter { it !in _items }
            error = null
            delta
        } catch (e: Exception) {
            Logger.w("PagerFlow", "loadNextPage failed", e)
            error = e.message
            emptyList()
        }
    }
}
