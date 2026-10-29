package com.tsutsen.platformplayer.api.media.structures

import com.tsutsen.platformplayer.core.data.pager.PagedSource

/**
 * Base pager used for all paging in the app, often wrapped by various other pagers to modified behavior.
 * The paging contract lives in [PagedSource].
 */
interface IPager<T> : PagedSource<T>
