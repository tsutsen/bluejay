package com.tsutsen.platformplayer.api.media.models.streams

import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource

interface IVideoSourceDescriptor {
    val isUnMuxed: Boolean;
    val videoSources: Array<IVideoSource>;
}