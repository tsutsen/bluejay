package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.logging.Logger

/**
 * Accumulates an [IPager]'s delta results into a flat list.
 * Convention: [IPager.getResults] returns only the last loaded page
 * (MultiPager/Window semantics), so each page is appended to [items].
 *
 * Engine window pagers can re-emit items that were already delivered (small
 * playlists running out of content, overlapping windows), so appended items
 * are filtered by [key] — the same identity lazy layouts key their items by
 * (Card.id) — keeping keys unique.
 *
 * @param map per-item converter; null results are dropped (e.g. [EngineCardMapper.toCard])
 * @param key per-item identity for deduplication (e.g. Card.id); defaults to
 *   the item itself (equality)
 */
class PagerFlow<T, R>(
    private val pager: IPager<T>,
    private val map: (T) -> R?,
    private val key: (R) -> Any? = { it },
) {
    private var _items: List<R> = emptyList()
    private val seenKeys = mutableSetOf<Any?>()
    val items: List<R> get() = _items
    val hasMore: Boolean get() = pager.hasMorePages()
    var error: String? = null
        private set

    /** Loads the pager's first page, dropping intra-page duplicates. */
    fun loadInitial(): List<R> {
        error = null
        return try {
            _items = emptyList()
            seenKeys.clear()
            append(pager.getResults().mapNotNull(map))
            _items
        } catch (e: Exception) {
            Logger.w("PagerFlow", "loadInitial failed", e)
            error = e.message
            emptyList()
        }
    }

    /** Loads the next page and appends unseen items to [items]. Returns the appended items. */
    fun loadNextPage(): List<R> {
        if (!hasMore) return emptyList()
        return try {
            pager.nextPage()
            val delta = pager.getResults().mapNotNull(map)
            error = null
            append(delta)
        } catch (e: Exception) {
            Logger.w("PagerFlow", "loadNextPage failed", e)
            error = e.message
            emptyList()
        }
    }

    /**
     * Merges the pager's current window into [items] without advancing the
     * page. Used by refresh pagers that absorb late sub-pagers (other
     * sources) after the first results were already delivered.
     */
    fun mergeCurrentResults(): List<R> {
        return try {
            error = null
            append(pager.getResults().mapNotNull(map))
        } catch (e: Exception) {
            Logger.w("PagerFlow", "mergeCurrentResults failed", e)
            error = e.message
            emptyList()
        }
    }

    private fun append(delta: List<R>): List<R> {
        val fresh = delta.filter { seenKeys.add(key(it)) }
        if (fresh.isNotEmpty()) _items += fresh
        return fresh
    }
}
