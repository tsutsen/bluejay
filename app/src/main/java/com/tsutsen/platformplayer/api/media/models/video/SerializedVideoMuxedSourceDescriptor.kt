package com.tsutsen.platformplayer.api.media.models.video

import com.tsutsen.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.VideoUrlSource

@kotlinx.serialization.Serializable
class SerializedVideoMuxedSourceDescriptor(
    val _videoSources: Array<VideoUrlSource>
): VideoMuxedSourceDescriptor(), ISerializedVideoSourceDescriptor {
    override val videoSources: Array<IVideoSource> get() = _videoSources.map { it }.toTypedArray();
};