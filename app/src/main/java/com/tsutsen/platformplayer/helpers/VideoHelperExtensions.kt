package com.tsutsen.platformplayer.helpers

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.logging.Logger

/**
 * Stub extension functions for VideoHelper.
 * These functions were part of the deleted XML-based video playback system.
 * They have been replaced with stub implementations for the Compose migration.
 */

fun IVideoSource.getHttpDataSourceFactory(): DataSource.Factory {
    return DefaultHttpDataSource.Factory()
}

fun IAudioSource.getHttpDataSourceFactory(): DataSource.Factory = getHttpDataSourceFactory()

fun IPlatformVideoDetails.hasAnySource(): Boolean = true

fun IPlatformVideoDetails.isDownloadable(): Boolean = true

fun IPlatformVideoDetails.playback(): Any? = null

fun IVideoSource.getUnderlyingPlugin(): Any? = null

fun IAudioSource.getUnderlyingPlugin(): Any? = null
