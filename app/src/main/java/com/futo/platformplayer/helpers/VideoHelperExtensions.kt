package com.futo.platformplayer.helpers

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.logging.Logger

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
