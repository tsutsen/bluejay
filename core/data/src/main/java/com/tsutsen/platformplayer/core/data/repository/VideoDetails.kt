package com.tsutsen.platformplayer.core.data.repository

import android.net.Uri

/**
 * Lightweight video details for populating the player UI.
 * Decoupled from IPlatformVideoDetails to keep core:data free of app-module dependencies.
 */
data class VideoDetails(
    val id: String,
    val url: String,
    val title: String,
    val authorName: String?,
    val authorUrl: String?,
    val authorThumbnailUrl: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val durationMs: Long?,
    val viewCount: Long?,
    val publishedAtMs: Long?,
    val likeCount: Long?,
    val dislikeCount: Long?,
    val isLive: Boolean = false,
    val subtitles: List<SubtitleSource> = emptyList()
)

/**
 * A subtitle track offered by the engine for a video.
 *
 * [contentUri] resolves the placeable subtitle location: either the
 * subtitle's direct URL or a local file the engine fetched on demand.
 */
data class SubtitleSource(
    val name: String,
    val format: String?,
    val contentUri: suspend () -> Uri?
)
