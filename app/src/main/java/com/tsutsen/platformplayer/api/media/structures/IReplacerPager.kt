package com.tsutsen.platformplayer.api.media.structures

import com.tsutsen.platformplayer.constructs.Event2

interface IReplacerPager<T> {
    val onReplaced: Event2<T, T>;
}