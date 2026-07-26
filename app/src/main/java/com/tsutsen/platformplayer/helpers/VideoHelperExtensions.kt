package com.tsutsen.platformplayer.helpers

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.logging.Logger

fun IVideoSource.getHttpDataSourceFactory(): DataSource.Factory {
    return DefaultHttpDataSource.Factory()
        .setUserAgent("Bluejay/1.0")
        .setAllowCrossProtocolRedirects(true)
}

fun IAudioSource.getHttpDataSourceFactory(): DataSource.Factory = getHttpDataSourceFactory()

fun IPlatformVideoDetails.hasAnySource(): Boolean {
    return video.videoSources.any { it is IVideoUrlSource || it is IHLSManifestSource }
        || video.isUnMuxed
}

fun IPlatformVideoDetails.isDownloadable(): Boolean {
    val desc = video
    return desc.videoSources.any { it is IVideoUrlSource || it is IHLSManifestSource }
        || (desc is com.tsutsen.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
            && desc.audioSources.any { it is IAudioUrlSource || it is IHLSManifestAudioSource })
}

fun IPlatformVideoDetails.playback(): Any? = null

fun IVideoSource.getUnderlyingPlugin(): Any? = null

fun IAudioSource.getUnderlyingPlugin(): Any? = null
