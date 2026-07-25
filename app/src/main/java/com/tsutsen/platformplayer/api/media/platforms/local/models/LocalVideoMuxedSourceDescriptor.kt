package com.tsutsen.platformplayer.api.media.platforms.local.models

import com.tsutsen.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.tsutsen.platformplayer.api.media.platforms.local.models.sources.LocalVideoContentSource
import com.tsutsen.platformplayer.api.media.platforms.local.models.sources.LocalVideoFileSource
import com.tsutsen.platformplayer.downloads.VideoLocal

class LocalVideoMuxedSourceDescriptor: VideoMuxedSourceDescriptor {
    override val videoSources: Array<IVideoSource>;

    constructor(video: LocalVideoFileSource) {
        videoSources = arrayOf(video);
    }
    constructor(video: LocalVideoContentSource) {
        videoSources = arrayOf(video);
    }
    constructor(videoSources: Array<IVideoSource>) {
        this.videoSources = videoSources;
    }
}