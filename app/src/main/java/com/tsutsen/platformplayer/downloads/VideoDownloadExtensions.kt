package com.tsutsen.platformplayer.downloads

import com.tsutsen.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.tsutsen.platformplayer.api.media.models.streams.sources.AudioUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequestExecutor

/**
 * Stub extension functions for VideoDownload.
 * These functions were part of the deleted XML-based download system.
 * They have been replaced with stub implementations for the Compose migration.
 */

fun IPlatformVideo.hasAnySource(): Boolean = true
fun IPlatformVideo.isDownloadable(): Boolean = true
fun VideoUrlSource.isDownloadable(): Boolean = true
fun AudioUrlSource.isDownloadable(): Boolean = true
fun IAudioSource.isDownloadable(): Boolean = true
fun IVideoSource.isDownloadable(): Boolean = true
fun IVideoSourceDescriptor.hasAnySource(): Boolean = true
fun IVideoSourceDescriptor.isDownloadable(): Boolean = true
fun IPlatformVideoDetails.hasAnySource(): Boolean = true
fun IPlatformVideoDetails.isDownloadable(): Boolean = true
fun IVideoSource.getUnderlyingPlugin(): JSClient? = null
fun IAudioSource.getUnderlyingPlugin(): JSClient? = null
fun IVideoSource.getRequestModifier(): com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier? = null
fun IAudioSource.getRequestModifier(): com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier? = null
fun IVideoSource.getRequestExecutor(): JSRequestExecutor? = null
fun IAudioSource.getRequestExecutor(): JSRequestExecutor? = null
