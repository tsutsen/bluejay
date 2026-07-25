package com.futo.platformplayer.downloads

import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.AudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor

/**
 * Stub extension functions for VideoDownload.
 * These functions were part of the deleted XML-based download system.
 * They have been replaced with stub implementations for the Compose migration.
 */

fun IPlatformVideo.hasAnySource(): Boolean = true

fun IPlatformVideo.isDownloadable(): Boolean = true

fun VideoUrlSource.isDownloadable(): Boolean = true

fun AudioUrlSource.isDownloadable(): Boolean = true

fun IVideoSource.getUnderlyingPlugin(): JSClient? = null

fun IAudioSource.getUnderlyingPlugin(): JSClient? = null

fun IVideoSource.getRequestModifier(): IRequestModifier? = null

fun IAudioSource.getRequestModifier(): IRequestModifier? = null

fun IVideoSource.getRequestExecutor(): JSRequestExecutor? = null

fun IAudioSource.getRequestExecutor(): JSRequestExecutor? = null

fun IPlatformVideoDetails.hasAnySource(): Boolean = true

fun IPlatformVideoDetails.isDownloadable(): Boolean = true

fun IVideoSourceDescriptor.hasAnySource(): Boolean = true

fun IVideoSourceDescriptor.isDownloadable(): Boolean = true

import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource

fun IVideoSource.isDownloadable(): Boolean = true

fun IAudioSource.isDownloadable(): Boolean = true
