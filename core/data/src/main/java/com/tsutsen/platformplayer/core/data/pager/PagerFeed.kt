package com.tsutsen.platformplayer.core.data.pager

import android.util.Log

/**
 * Accumulates a [PagedSource]'s delta results into a flat list.
 * Convention: [PagedSource.getResults] returns only the last loaded page
 * (MultiPager/Window semantics), so each page is appended to [items].
 *
 * Engine window pagers can re-emit items that were already delivered (small
 * playlists running out of content, overlapping windows), so appended items
 * are filtered by [key] — the same identity lazy layouts key their items by
 * (Card.id) — keeping keys unique.
 *
 * @param map per-item converter; null results are dropped
 * @param key per-item identity for deduplication (e.g. Card.id); defaults to
 *   the item itself (equality)
 */
class PagerFeed<T, R>(
    private val source: PagedSource<T>,
    private val map: (T) -> R?,
    private val key: (R) -> Any? = { it },
) {
    private var _items: List<R> = emptyList()
    private val seenKeys = mutableSetOf<Any?>()
    val items: List<R> get() = _items
    val hasMore: Boolean get() = source.hasMorePages()
    var error: String? = null
        private set

    /** Loads the source's first page, dropping intra-page duplicates. */
    fun loadInitial(): List<R> {
        error = null
        return try {
            _items = emptyList()
            seenKeys.clear()
            append(source.getResults().mapNotNull(map))
            _items
        } catch (e: Exception) {
            Log.w(TAG, "loadInitial failed", e)
            error = e.message
            emptyList()
        }
    }

    /** Loads the next page and appends unseen items to [items]. Returns the appended items. */
    fun loadNextPage(): List<R> {
        if (!hasMore) return emptyList()
        return try {
            source.nextPage()
            val delta = source.getResults().mapNotNull(map)
            error = null
            append(delta)
        } catch (e: Exception) {
            Log.w(TAG, "loadNextPage failed", e)
            error = e.message
            emptyList()
        }
    }

    /**
     * Merges the source's current window into [items] without advancing the
     * page. Used by refresh pagers that absorb late sub-pagers (other
     * sources) after the first results were already delivered.
     */
    fun mergeCurrentResults(): List<R> {
        return try {
            error = null
            append(source.getResults().mapNotNull(map))
        } catch (e: Exception) {
            Log.w(TAG, "mergeCurrentResults failed", e)
            error = e.message
            emptyList()
        }
    }

    private fun append(delta: List<R>): List<R> {
        val fresh = delta.filter { seenKeys.add(key(it)) }
        if (fresh.isNotEmpty()) _items += fresh
        return fresh
    }

    private companion object {
        const val TAG = "PagerFlow"
    }
}
