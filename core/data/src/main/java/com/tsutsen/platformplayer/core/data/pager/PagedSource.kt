package com.tsutsen.platformplayer.core.data.pager

/**
 * Engine-neutral pager contract. Implementations return only the last loaded
 * page from [getResults] (the convention of the plugin pager API).
 */
interface PagedSource<T> {
    fun getResults(): List<T>

    fun hasMorePages(): Boolean

    fun nextPage()
}
