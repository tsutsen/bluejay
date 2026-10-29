package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.core.data.pager.PagerFeed

/** App-side name for the core pager accumulator; existing call sites use this alias. */
typealias PagerFlow<T, R> = PagerFeed<T, R>
