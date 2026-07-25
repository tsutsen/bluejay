package com.tsutsen.platformplayer.api.media.models.streams

import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource

@kotlinx.serialization.Serializable
abstract class VideoMuxedSourceDescriptor : IVideoSourceDescriptor {
    override val isUnMuxed : Boolean = false;

    abstract override val videoSources : Array<IVideoSource>;
}